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

public class RAWAudioDumper implements Plugin
{
    private OutputStream stream;
    private String soundName;
    private volatile SoundDigitalOut soundOut;
    private volatile boolean signalCheck;
    private volatile boolean shuttingDown;
    private volatile boolean shutDown;
    private Thread worker;

    public RAWAudioDumper(Plugins pluginManager, String args)
    {
        int split = args.indexOf(',');
        String fileName = args.substring(split + 1);
        soundName = args.substring(0, split);
        System.err.println("Notice: Filename: " + fileName + " soundtrack: " + soundName + ".");
        try {
            stream = new FileOutputStream(fileName);
        } catch(IOException e) {
            e.printStackTrace();
        }
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
        SoundDigitalOut previousOutput = soundOut;

        //Bump it a little.
        soundOut = null;
        worker.interrupt();
        while(!signalCheck)
            ;

        if(previousOutput != null)
             previousOutput.unsubscribeOutput(this);

        synchronized(this) {
            if(pc != null) {
                soundOut = pc.getSoundOut(soundName);
                if(soundOut == null)
                    System.err.println("Warning: No such audio output \"" + soundName + "\".");
            } else {
                soundOut = null;
            }

            if(soundOut != null)
                soundOut.subscribeOutput(this);

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
        long frame = 0;
        worker = Thread.currentThread();
        while(!shuttingDown) {
            synchronized(this) {
                while(soundOut == null && !shuttingDown)
                    try {
                        signalCheck = true;
                        wait();
                        signalCheck = false;
                    } catch(Exception e) {
                    }
                if(shuttingDown)
                    break;

                if(soundOut.waitOutput(this)) {
                    try {
                        SoundDigitalOut.Block frameData = new SoundDigitalOut.Block();
                        soundOut.readBlock(frameData);
                        frame = frameData.blockNo;
                        stream.write(frameData.sampleData, 0, 8 * frameData.samples);
                    } catch(IOException e) {
                        System.err.println("Warning: Failed to save audio frame!");
                        e.printStackTrace();
                    }

                    soundOut.releaseOutput(this);
                }
            }
        }

        SoundDigitalOut.Block frameData = new SoundDigitalOut.Block();
        if(soundOut != null) {
            soundOut.readBlock(frameData);
        }
        //One final frame to dump.
        try {
            if(frame != frameData.blockNo)
                stream.write(frameData.sampleData, 0, 8 * frameData.samples);
            stream.flush();
            stream.close();
        } catch(IOException e) {
            System.err.println("Warning: Failed to save audio frame!");
            e.printStackTrace();
        }

        synchronized(this) {
            shutDown = true;
            notifyAll();
        }
    }
}
