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

import java.io.*;
import java.util.*;
import java.util.zip.*;
import org.jpc.emulator.*;
import org.jpc.pluginsaux.HUDRenderer;
import org.jpc.pluginsbase.Plugins;
import org.jpc.pluginsbase.Plugin;
import static org.jpc.Misc.errorDialog;
import static org.jpc.Misc.parseStringToComponents;

public class RAWVideoDumper implements Plugin
{
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
    private OutputStream rawOutputStream;
    private HUDRenderer renderer;

    public RAWVideoDumper(Plugins pluginManager, String args) throws IOException
    {
        Map<String, String> params = parseStringToComponents(args);
        String rawOutput = params.get("rawoutput");
        if(rawOutput == null)
            throw new IOException("Raw output setting (rawoutput) required for PNGDumper");
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
        renderer = new HUDRenderer();
    }


    public void eci_hud_left_gap(Integer flags, Integer gap)
    {
        if((flags.intValue() & 2) != 0)
            renderer.setLeftGap(gap);
    }

    public void eci_hud_top_gap(Integer flags, Integer gap)
    {
        if((flags.intValue() & 2) != 0)
            renderer.setTopGap(gap);
    }

    public void eci_hud_right_gap(Integer flags, Integer gap)
    {
        if((flags.intValue() & 2) != 0)
            renderer.setRightGap(gap);
    }

    public void eci_hud_bottom_gap(Integer flags, Integer gap)
    {
        if((flags.intValue() & 2) != 0)
            renderer.setBottomGap(gap);
    }

    public void eci_hud_white_solid_box(Integer flags, Integer x, Integer y, Integer w, Integer h)
    {
        if((flags.intValue() & 2) != 0)
            renderer.whiteSolidBox(x.intValue(), y.intValue(), w.intValue(), h.intValue());
    }

    public void eci_hud_box(Integer flags, Integer x, Integer y, Integer w, Integer h, Integer thick, Integer lr,
        Integer lg, Integer lb, Integer la, Integer fr, Integer fg, Integer fb, Integer fa)
    {
        if((flags.intValue() & 2) != 0)
            renderer.box(x, y, w, h, thick, lr, lg, lb, la, fr, fg, fb, fa);
    }

    public void eci_hud_circle(Integer flags, Integer x, Integer y, Integer r, Integer thick, Integer lr,
        Integer lg, Integer lb, Integer la, Integer fr, Integer fg, Integer fb, Integer fa)
    {
        if((flags.intValue() & 2) != 0)
            renderer.circle(x, y, r, thick, lr, lg, lb, la, fr, fg, fb, fa);
    }

    public void eci_hud_bitmap(Integer flags, Integer x, Integer y, String bmap, Integer lr,
        Integer lg, Integer lb, Integer la, Integer fr, Integer fg, Integer fb, Integer fa)
    {
        if((flags.intValue() & 2) != 0)
            renderer.bitmap(x, y, bmap, lr, lg, lb, la, fr, fg, fb, fa);
    }

    public void eci_hud_bitmap_binary(Integer flags, Integer x, Integer y, String bmap, Integer lr,
        Integer lg, Integer lb, Integer la, Integer fr, Integer fg, Integer fb, Integer fa)
    {
        if((flags.intValue() & 2) != 0)
            renderer.bitmapBinary(x, y, bmap, lr, lg, lb, la, fr, fg, fb, fa);
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
        while(worker == null);
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
                        renderer.setBackground(videoOut.getDisplayBuffer(), w, h);
                        videoOut.releaseOutputWaitAll(this);
                        w = renderer.getRenderWidth();
                        h = renderer.getRenderHeight();
                        saveBuffer = renderer.getFinishedAndReset();
                        if(saveBuffer == null || w == 0 || h == 0) {
                            continue; //Image not usable.
                        }
                        frame++;
                        frameTime = (clock.getTime() - lastInternalTimeUpdate) + internalTime;

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

        if(videoOut != null)
            videoOut.unsubscribeOutput(this);

       if(rawOutputStream != null) {
           try {
               rawOutputStream.flush();
               rawOutputStream.close();
           } catch(IOException e) {
               System.err.println("Warning: Failed to close video output stream!");
               errorDialog(e, "Failed to close video output", null, "Dismiss");
           }
       }

        synchronized(this) {
            shutDown = true;
            notifyAll();
        }
    }
}
