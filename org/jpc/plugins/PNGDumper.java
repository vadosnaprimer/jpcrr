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
import org.jpc.emulator.*;
import org.jpc.pluginsaux.PNGSaver;
import org.jpc.pluginsbase.Plugins;
import org.jpc.pluginsbase.Plugin;

public class PNGDumper implements Plugin
{
    private PNGSaver saver;
    private volatile VGADigitalOut videoOut;
    private volatile boolean signalCheck;
    private volatile boolean shuttingDown;
    private volatile boolean shutDown;
    private Thread worker;
    private volatile boolean pcRunStatus;

    public PNGDumper(Plugins pluginManager, String prefix)
    {
        saver = new PNGSaver(prefix);
        shuttingDown = false;
        shutDown = false;
        pcRunStatus = false;
    }

    public boolean systemShutdown()
    {
        if(pcRunStatus)
            return false;  //Don't shut down until after PC.

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
        return true;
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
        pcRunStatus = true;
    }

    public void pcStopping()
    {
        pcRunStatus = false;
    }

    public void main()
    {
        int frame = 0;
	int[] saveBuffer = null;
	int saveBufferSize = 0;
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
                        if(saveBuffer == null || w * h > saveBufferSize) {
                            saveBuffer = new int[w * h + 1];
                            saveBufferSize = w * h;
                        }
                        frame++;
                        if(w * h > 0)
                            System.arraycopy(videoOut.getDisplayBuffer(), 0, saveBuffer, 0, w * h);
                        videoOut.releaseOutput(this);
                        saver.savePNG(saveBuffer, w, h);
                        System.err.println("Informational: Saved frame #" + frame + ": " + w + "x" + h + ".");
                    } catch(IOException e) {
                        System.err.println("Warning: Failed to save screenshot image!");
                        e.printStackTrace();
                    }

                }
            }
        }

        synchronized(this) {
            shutDown = true;
            notifyAll();
        }
    }
}
