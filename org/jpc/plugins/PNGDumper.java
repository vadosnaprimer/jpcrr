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
import java.util.*;
import java.util.zip.*;
import org.jpc.emulator.*;
import org.jpc.pluginsaux.PNGSaver;
import org.jpc.pluginsbase.Plugins;
import org.jpc.pluginsbase.Plugin;
import static org.jpc.Misc.errorDialog;
import static org.jpc.Misc.parseStringToComponents;

public class PNGDumper implements Plugin
{
    private PNGSaver saver;
    private volatile VGADigitalOut videoOut;
    private volatile Clock clock;
    private volatile boolean signalCheck;
    private volatile boolean shuttingDown;
    private volatile boolean shutDown;
    private Thread worker;
    private volatile boolean pcRunStatus;
    private volatile long internalTime;
    private volatile long lastSaveTime;
    private volatile long lastInternalTimeUpdate;
    private PrintStream timingFile;
    private OutputStream rawOutputStream;

    public PNGDumper(Plugins pluginManager, String args) throws IOException
    {
        Map<String, String> params = parseStringToComponents(args);
        String prefix = params.get("prefix");
        String rawOutput = params.get("rawoutput");
        if(prefix == null && rawOutput == null)
            throw new IOException("Prefix setting (prefix) or Raw output setting (rawoutput) required for PNGDumper");
        if(prefix != null) {
            saver = new PNGSaver(prefix);
            try {
                timingFile = new PrintStream(prefix + ".timing", "UTF-8");
            } catch(Exception e) {
                System.err.println("Error: Failed to open timing information file.");
            }
        }
        if(rawOutput != null) {
            try {
                rawOutputStream = new DeflaterOutputStream(new FileOutputStream(rawOutput));
            } catch(Exception e) {
                System.err.println("Error: Failed to open raw output file.");
            }
        }
        shuttingDown = false;
        shutDown = false;
        pcRunStatus = false;
        internalTime = 0;
        lastInternalTimeUpdate = 0;
        lastSaveTime = 0;
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
                clock = (Clock)pc.getComponent(Clock.class);
            } else {
                videoOut = null;
                clock = null;
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
        lastInternalTimeUpdate = clock.getTime();
    }

    public void pcStopping()
    {
        pcRunStatus = false;
        long newUpdate = clock.getTime();
        internalTime += (newUpdate - lastInternalTimeUpdate);
        lastInternalTimeUpdate = newUpdate;
    }

    public void main()
    {
        int frame = 0;
        long frameTime;
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
                        if(w == 0 || h == 0) {
                            videoOut.releaseOutput(this);
                            continue; //Image not usable.
                        }
                        if(saveBuffer == null || w * h > saveBufferSize) {
                            saveBuffer = new int[w * h];
                            saveBufferSize = w * h;
                        }
                        frame++;
                        System.arraycopy(videoOut.getDisplayBuffer(), 0, saveBuffer, 0, w * h);
                        frameTime = (clock.getTime() - lastInternalTimeUpdate) + internalTime;
                        videoOut.releaseOutput(this);
                        if(saver != null)
                            saver.savePNG(saveBuffer, w, h);
                        if(timingFile != null)
                            timingFile.println(frameTime + " " + saver.lastPNGName());
                        if(rawOutputStream != null) {
                            long offset = frameTime - lastSaveTime;
                            int offsetWords = (int)(offset / 0xFFFFFFFFL + 1);
                            byte[] frameHeader = new byte[4 * offsetWords + 4];
                            frameHeader[4 * offsetWords + 0] = (byte)(w >>> 8);
                            frameHeader[4 * offsetWords + 1] = (byte)w;
                            frameHeader[4 * offsetWords + 2] = (byte)(h >>> 8);
                            frameHeader[4 * offsetWords + 3] = (byte)h;
                            for(int i = 0; i < 4 * offsetWords - 4; i++)
                                frameHeader[i] = (byte)-1;
                            offset = offset % 0xFFFFFFFFL;
                            frameHeader[4 * offsetWords - 4] = (byte)(offset >>> 24);
                            frameHeader[4 * offsetWords - 3] = (byte)(offset >>> 16);
                            frameHeader[4 * offsetWords - 2] = (byte)(offset >>> 8);
                            frameHeader[4 * offsetWords - 1] = (byte)offset;
                            rawOutputStream.write(frameHeader);
                            byte[] frameLine = new byte[4 * w];
                            for(int y = 0; y < h; y++) {
                                for(int x = 0; x < w; x++) {
                                    int px = saveBuffer[y * w + x];
                                    frameLine[4 * x + 0] = 0;
                                    frameLine[4 * x + 1] = (byte)(px >>> 16);
                                    frameLine[4 * x + 2] = (byte)(px >>> 8);
                                    frameLine[4 * x + 3] = (byte)px;
                                }
                                rawOutputStream.write(frameLine);
                            }
                        }
                        System.err.println("Informational: Saved frame #" + frame + ": " + w + "x" + h + " <" +
                            frameTime + ">.");
                        lastSaveTime = frameTime;
                    } catch(IOException e) {
                        System.err.println("Warning: Failed to save screenshot image!");
                        errorDialog(e, "Failed to save screenshot", null, "Dismiss");
                    }
                }
            }
        }

       if(rawOutputStream != null) {
           try {
               rawOutputStream.flush();
               rawOutputStream.close();
           } catch(IOException e) {
               System.err.println("Warning: Failed to close video output stream!");
               errorDialog(e, "Failed to close video output", null, "Dismiss");
           }
       }

       if(timingFile != null)
           timingFile.close();

        synchronized(this) {
            shutDown = true;
            notifyAll();
        }
    }
}
