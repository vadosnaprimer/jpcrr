/*
    JPC: A x86 PC Hardware Emulator for a pure Java Virtual Machine
    Release Version 2.0

    A project from the Physics Dept, The University of Oxford

    Copyright (C) 2007 Isis Innovation Limited
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

    Details (including contact information) can be found at:

    www.physics.ox.ac.uk/jpc
*/

package org.jpc.plugins;

import java.io.*;
import org.jpc.emulator.*;
import org.jpc.pluginsaux.PNGSaver;
import org.jpc.support.Plugins;

public class PNGDumper implements org.jpc.Plugin
{
    private PNGSaver saver;
    private volatile VGADigitalOut videoOut;
    private volatile boolean signalCheck;
    private volatile boolean shuttingDown;
    private volatile boolean shutDown;
    private Thread worker;

    public PNGDumper(Plugins pluginManager, String prefix)
    {
        saver = new PNGSaver(prefix);
        shuttingDown = false;
        shutDown = false;
    }

    public void systemShutdown()
    {
        shuttingDown = true;
        if(worker != null) {
            worker.interrupt();
            synchronized(this) {
                notifyAll();
                while(!shutDown)
                    try {
                        wait();
                    } catch(Exception e) {
                    }
            }
        }
    }

    public void reconnect(PC pc)
    {
        VGADigitalOut previousOutput = videoOut;

        //Bump it a little.
        videoOut = null;
        worker.interrupt();
        while(!signalCheck)
            ;

        if(previousOutput != null)
             previousOutput.unsubscribeOutput(this);

        synchronized(this) {
            if(pc != null) {
                videoOut = pc.getVideoOutput();
            } else {
                videoOut = null;
            }

            if(videoOut != null)
                videoOut.subscribeOutput(this);

            notifyAll();
        }
    }

    public void notifyArguments(String[] args)
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
        int frame = 0;
        worker = Thread.currentThread();
        while(!shuttingDown) {
            synchronized(this) {
                while(videoOut == null && !shuttingDown)
                    try {
                        signalCheck = true;
                        wait();
                        signalCheck = false;
                    } catch(Exception e) {
                    }
                if(shuttingDown)
                    break;

                if(videoOut.waitOutput(this)) {
                    try {
                        int w = videoOut.getWidth();
                        int h = videoOut.getHeight();
                        frame++;
                        saver.savePNG(videoOut.getDisplayBuffer(), w, h);
                        System.err.println("Informational: Saved frame #" + frame + ": " + w + "x" + h + ".");
                    } catch(IOException e) {
                        System.err.println("Warning: Failed to save screenshot image!");
                        e.printStackTrace();
                    }

                    videoOut.releaseOutput(this);
                }
            }
        }

        synchronized(this) {
            shutDown = true;
            notifyAll();
        }
    }
}
