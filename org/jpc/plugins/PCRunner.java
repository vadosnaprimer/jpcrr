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
import java.util.zip.*;
import org.jpc.emulator.PC;
import org.jpc.emulator.SRLoader;
import org.jpc.support.*;
import org.jpc.jrsr.*;

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
            JRSRArchiveReader reader = new JRSRArchiveReader(fileName);

            InputStream entry = reader.readMember("manifest");
            if(!SRLoader.checkConstructorManifest(entry))
                throw new IOException("Wrong savestate version");
            entry.close();

            entry = new FourToFiveDecoder(reader.readMember("savestate"));
            DataInput save = new DataInputStream(new InflaterInputStream(entry));
            SRLoader loader = new SRLoader(save);
            pc = (PC)(loader.loadObject());
            entry.close();
            reader.close();
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
