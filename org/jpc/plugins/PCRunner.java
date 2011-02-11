/*
    JPC-RR: A x86 PC Hardware Emulator
    Release 1

    Copyright (C) 2007-2009 Isis Innovation Limited
    Copyright (C) 2009-2010 H. Ilari Liusvaara

    This program is free software; you can redistribute it and/or modify
    it under the terms of the GNU General Public License version 2 as published by
    the Free Software Foundation.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License along
    with this program; if not, write to the Free Software Foundation, Inc.,
    51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.

    Based on JPC x86 PC Hardware emulator,
    A project from the Physics Dept, The University of Oxford

    Details about original JPC can be found at:

    www-jpc.physics.ox.ac.uk

*/

package org.jpc.plugins;

import java.util.*;
import org.jpc.emulator.PC;
import org.jpc.emulator.memory.PhysicalAddressSpace;
import org.jpc.bus.Bus;
import org.jpc.jrsr.*;
import org.jpc.diskimages.DiskImage;
import static org.jpc.Misc.errorDialog;
import static org.jpc.Misc.parseStringsToComponents;

public class PCRunner
{
    private static final long serialVersionUID = 8;
    private Bus bus;
    private String fileName;
    private String submovie;
    private boolean shutDown;
    private boolean shutDownRequest;
    private boolean fpuHack;
    private boolean vgaDrawHack;
    private boolean vgaScroll2Hack;
    private long imminentTrapTime;

    protected PC pc;

    public boolean systemShutdown()
    {
        shutDownRequest = true;
        synchronized(this) {
            while(!shutDown)
                try {
                    wait();
                } catch(Exception e) {
                }
        }
        return true;
    }

    private void main()
    {
        Exception caught = null;

        if(fileName == null) {
            System.err.println("Critical: No savestate to load.");
            return;
        }

        try {
            System.err.println("Informational: Loading a snapshot of JPC-RR");
            JRSRArchiveReader reader = new JRSRArchiveReader(fileName);
            PC.PCFullStatus fullStatus = PC.loadSavestate(reader, false, false, null, submovie);
            pc = fullStatus.pc;
            reader.close();
            fullStatus.events.setPCRunStatus(true);
        } catch(Exception e) {
            caught = e;
        }

        if(caught == null) {
            try {
                connectPC(pc);
                if(fpuHack)
                    pc.setFPUHack();
                if(vgaDrawHack)
                    pc.setVGADrawHack();
                if(vgaScroll2Hack)
                    pc.setVGAScroll2Hack();
                System.err.println("Informational: Loadstate done");
            } catch(Exception e) {
                caught = e;
            }
        }

        if(caught != null) {
            System.err.println("Critical: Savestate load failed.");
            errorDialog(caught, "Failed to load savestate", null, "Quit");
            shutDown = true;
            bus.shutdownEmulator();
            return;
        }

        bus.invokeEvent("pc-start", null);
        pc.start();

        if(imminentTrapTime > 0) {
            pc.getTraceTrap().setTrapTime(imminentTrapTime);
        }

        while(!shutDownRequest) {   //We will be killed by JVM.
            try {
                pc.execute();
                if(pc.getHitTraceTrap()) {
                    if(pc.getAndClearTripleFaulted())
                        System.err.println("Warning: CPU shut itself down due to triple fault. Rebooting the system.");
                    break;
                }
            } catch (Exception e) {
                System.err.println("Critical: Hardware emulator internal error");
                errorDialog(e, "Emulator internal error", null, "Quit");
                break;
            }
        }

        System.err.println("Informational: Emulation stopped. Exiting.");
        pc.stop();
        bus.invokeEvent("pc-stop", null);
        synchronized(this) {
            shutDown = true;
            notifyAll();
        }
        bus.shutdownEmulator();
    }

    public synchronized void connectPC(PC pc)
    {
        bus.invokeEvent("pc-change", new Object[]{pc});
        this.pc = pc;
        notifyAll();
    }

    public PCRunner(Bus _bus, String[] args) throws Exception
    {
        Map<String, String> params = parseStringsToComponents(args);

        bus = _bus;
        bus.setShutdownHandler(this, "systemShutdown");
        bus.setEventHandler(this, "reconnect", "pc-change");

        if(DiskImage.getLibrary() == null)
            throw new Exception("PCRunner plugin requires disk library");

        this.pc = null;
        this.fileName = params.get("movie");
        String stopAt = params.get("stoptime");
        submovie = params.get("initialstate");
        if(this.fileName == null) {
            throw new Exception("No movie to load");
        }
        if(stopAt != null) {
            this.imminentTrapTime = Long.parseLong(stopAt);
        }
        if(submovie != null)
            submovie = "initialization-" + submovie;
        if(params.get("fpuhack") != null)
            this.fpuHack = true;
        if(params.get("vgadrawhack") != null)
            this.vgaDrawHack = true;
        if(params.get("vgascroll2hack") != null)
            this.vgaScroll2Hack = true;
        (new Thread(new Runnable(){ public void run() { main(); }}, "PCRunner thread")).start();

    }
}
