/*
    JPC-RR: A x86 PC Hardware Emulator
    Release 1

    Copyright (C) 2007-2009 Isis Innovation Limited
    Copyright (C) 2009 H. Ilari Liusvaara

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

import java.io.*;
import java.util.zip.*;
import java.util.*;
import org.jpc.emulator.PC;
import org.jpc.emulator.memory.PhysicalAddressSpace;
import org.jpc.emulator.SRLoader;
import org.jpc.pluginsbase.*;
import org.jpc.jrsr.*;
import static org.jpc.Misc.errorDialog;
import static org.jpc.Misc.parseStringToComponents;

public class PCRunner implements Plugin
{
    private static final long serialVersionUID = 8;
    private Plugins vPluginManager;
    private String fileName;
    private boolean shutDown;
    private boolean shutDownRequest;
    private boolean fpuHack;
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

    public void reconnect(PC pc)
    {
        //Not interested.
        if(fpuHack)
            pc.setFPUHack();
    }

    public void pcStarting()
    {
        //Not interested.
    }

    public void pcStopping()
    {
        //Not interested.
    }


    public void eci_memory_read(Long address, Integer size)
    {
        if(pc != null) {
            long addr = address.longValue();
            long _size = size.intValue();
            long ret = 0;
            PhysicalAddressSpace addrSpace;
            if(addr < 0 || addr > 0xFFFFFFFFL || (_size != 1 && _size != 2 && _size != 4))
                return;

            addrSpace = (PhysicalAddressSpace)pc.getComponent(PhysicalAddressSpace.class);
            if(_size == 1)
                ret = (long)addrSpace.getByte((int)addr) & 0xFF;
            else if(_size == 2)
                ret = (long)addrSpace.getWord((int)addr) & 0xFFFF;
            else if(_size == 4)
                ret = (long)addrSpace.getDoubleWord((int)addr) & 0xFFFFFFFFL;

            vPluginManager.returnValue(ret);
        }
    }

    public void main()
    {
        Exception caught = null;

        if(fileName == null) {
            System.err.println("Critical: No savestate to load.");
            return;
        }

        try {
            System.err.println("Informational: Loading a snapshot of JPC-RR");
            JRSRArchiveReader reader = new JRSRArchiveReader(fileName);
            PC.PCFullStatus fullStatus = PC.loadSavestate(reader, null, false);
            pc = fullStatus.pc;
            reader.close();
            fullStatus.events.setPCRunStatus(true);
        } catch(Exception e) {
            caught = e;
        }

        if(caught == null) {
            try {
                connectPC(pc);
                System.err.println("Informational: Loadstate done");
            } catch(Exception e) {
                caught = e;
            }
        }

        if(caught != null) {
            System.err.println("Critical: Savestate load failed.");
            errorDialog(caught, "Failed to load savestate", null, "Quit");
            shutDown = true;
            vPluginManager.shutdownEmulator();
            return;
        }

        vPluginManager.pcStarted();
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
        vPluginManager.pcStopped();
        synchronized(this) {
            shutDown = true;
            notifyAll();
        }
        vPluginManager.shutdownEmulator();
    }

    public synchronized void connectPC(PC pc)
    {
        vPluginManager.reconnect(pc);
        this.pc = pc;
        notifyAll();
    }

    public PCRunner(Plugins manager, String args) throws Exception
    {
        Map<String, String> params = parseStringToComponents(args);
        this.pc = null;
        this.vPluginManager = manager;
        this.fileName = params.get("movie");
        String stopAt = params.get("stoptime");
        if(this.fileName == null) {
            throw new Exception("No movie to load");
        }
        if(stopAt != null) {
            this.imminentTrapTime = Long.parseLong(stopAt);
        }
        if(params.get("fpuhack") != null)
            this.fpuHack = true;
    }
}
