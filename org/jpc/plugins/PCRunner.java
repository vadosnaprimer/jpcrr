/*
    JPC: A x86 PC Hardware Emulator for a pure Java Virtual Machine
    Release Version 2.0

    A project from the Physics Dept, The University of Oxford

    Copyright (C) 2007-2009 Isis Innovation Limited

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

    Details (including contact information) can be found at:

    www-jpc.physics.ox.ac.uk
*/

package org.jpc.plugins;

import java.io.*;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;
import java.util.jar.*;
import java.util.zip.*;
import java.security.AccessControlException;
import java.lang.reflect.*;

import org.jpc.*;
import org.jpc.emulator.PC;
import org.jpc.emulator.TraceTrap;
import org.jpc.emulator.pci.peripheral.VGACard;
import org.jpc.emulator.peripheral.FloppyController;
import org.jpc.emulator.peripheral.Keyboard;
import org.jpc.emulator.memory.PhysicalAddressSpace;
import org.jpc.emulator.SRLoader;
import org.jpc.emulator.SRDumper;
import org.jpc.emulator.StatusDumper;
import org.jpc.emulator.Clock;
import org.jpc.diskimages.BlockDevice;
import org.jpc.diskimages.GenericBlockDevice;
import org.jpc.diskimages.ImageLibrary;
import org.jpc.diskimages.DiskImage;
import org.jpc.support.*;

public class PCRunner implements org.jpc.Plugin
{
    private static final long serialVersionUID = 8;
    private Plugins vPluginManager;
    private String fileName;
    protected String[] arguments;

    protected PC pc;

    public void systemShutdown()
    {
        //Not interested.
    }

    public void reconnect(PC pc)
    {
        //Not interested.
    }

    public void pcStarting()
    {
        //Not interested.
    }

    public void pcStopping()
    {
        //Not interested.
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
            ZipFile zip2 = new ZipFile(fileName);

            ZipEntry entry = zip2.getEntry("constructors.manifest");
            if(entry == null)
                throw new IOException("Not a savestate file.");
            DataInput manifest = new DataInputStream(zip2.getInputStream(entry));
            if(!SRLoader.checkConstructorManifest(manifest))
                throw new IOException("Wrong savestate version");

            entry = zip2.getEntry("savestate.SR");
            if(entry == null)
                throw new IOException("Not a savestate file.");
            DataInput zip = new DataInputStream(zip2.getInputStream(entry));
            SRLoader loader = new SRLoader(zip);
            pc = (PC)(loader.loadObject());
            zip2.close();
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
            caught.printStackTrace();
            vPluginManager.shutdownEmulator();
            return;
        }

        vPluginManager.pcStarted();
        pc.start();

        while(true) {   //We will be killed by JVM.
            try {
                pc.execute();
                if(pc.getHitTraceTrap()) {
                    if(pc.getAndClearTripleFaulted())
                        System.err.println("Warning: CPU shut itself down due to triple fault. Rebooting the system.");
                }
            } catch (Exception e) {
                System.err.println("Critical: Hardware emulator internal error");
                e.printStackTrace();
                break;
            }
        }
    }

    public synchronized void connectPC(PC pc)
    {
        vPluginManager.reconnect(pc);
        this.pc = pc;
        notifyAll();
    }

    public void notifyArguments(String[] args)
    {
        this.arguments = args;
    }

    public PCRunner(Plugins manager, String saveName) throws Exception
    {
        this.pc = null;
        this.vPluginManager = manager;
        this.fileName = saveName;
    }
}
