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
import org.jpc.output.*;
import org.jpc.hud.HUDRenderer;
import org.jpc.bus.Bus;
import static org.jpc.Misc.errorDialog;
import static org.jpc.Misc.parseStringsToComponents;

public class RAWDumper
{
    class DumpFrameFilter implements OutputStatic.FrameFilter
    {
        OutputFrameImage lastVideoFrame;
        short videoChannel;
        boolean gotFrame;
        long lastTimestamp;

        public DumpFrameFilter()
        {
        }

        public OutputFrame doFilter(OutputFrame f, short channel)
        {
            gotFrame = true;
            lastTimestamp = f.getTime();
            if(!(f instanceof OutputFrameImage))
                return f;
            //This is video frame. Save and cancel it.
            lastVideoFrame = (OutputFrameImage)f;
            videoChannel = channel;
            return null;
        }
    }


    private volatile OutputClient videoOut;
    private volatile OutputStatic connector;
    private volatile boolean shuttingDown;
    private volatile boolean shutDown;
    private volatile boolean pcRunStatus;
    private Thread worker;
    private OutputStream rawOutputStream;
    private DumpFrameFilter filter;
    private HUDRenderer renderer;
    private Bus bus;

    public RAWDumper(Bus _bus, String[] args) throws IOException
    {
        bus = _bus;
        bus.setShutdownHandler(this, "systemShutdown");
        bus.setEventHandler(this, "pcStarting", "pc-start");
        bus.setEventHandler(this, "pcStopping", "pc-stop");

        Map<String, String> params = parseStringsToComponents(args);
        String rawOutput = params.get("rawoutput");
        if(rawOutput == null)
            throw new IOException("Raw output setting (rawoutput) required for PNGDumper");
        if(rawOutput != null) {
            try {
                rawOutputStream = new FileOutputStream(rawOutput);
            } catch(Exception e) {
                System.err.println("Error: Failed to open raw output file.");
                throw new IOException("Can't open dumpfile '" + rawOutput + "':" + e.getMessage());
            }
        }
        shuttingDown = false;
        shutDown = false;
        pcRunStatus = false;
        connector = (OutputStatic)((bus.executeCommandNoFault("get-pc-output", null))[0]);
        videoOut = new OutputClient(connector);
        filter = new DumpFrameFilter();
        renderer = new HUDRenderer(2);
        bus.executeCommandNoFault("add-renderer", new Object[]{renderer});
        (new Thread(new Runnable(){ public void run() { main(); }}, "Dumper thread")).start();
    }

    public boolean systemShutdown()
    {
        if(pcRunStatus) {
            return false;  //Don't shut down until after PC.
        }

        shuttingDown = true;
        if(worker != null) {
            synchronized(this) {
                worker.interrupt();
                while(!shutDown)
                    try {
                        wait();
                    } catch(Exception e) {
                    }
            }
        }
        bus.executeCommandNoFault("remove-renderer", new Object[]{renderer});
        return true;
    }

    public void pcStarting(String cmd, Object[] args)
    {
        pcRunStatus = true;
    }

    public void pcStopping(String cmd, Object[] args)
    {
        pcRunStatus = false;
    }

    private void main()
    {
        int frame = 0;
        worker = Thread.currentThread();
        boolean first = true;
        while(!shuttingDown) {
            if(shuttingDown)
                break;

            if(videoOut.aquire()) {
                synchronized(this) {
                    try {
                        long base;
                        if(first)
                            rawOutputStream.write(connector.makeChannelTable());
                        first = false;
                        base = connector.writeFrames(rawOutputStream, filter);
                        OutputFrameImage lastFrame = filter.lastVideoFrame;
                        if(lastFrame == null) {
                            videoOut.releaseWaitAll();
                            continue;
                        }
                        int w = lastFrame.getWidth();
                        int h = lastFrame.getHeight();
                        renderer.setBackground(lastFrame.getImageData(), w, h);
                        videoOut.releaseWaitAll();
                        w = renderer.getRenderWidth();
                        h = renderer.getRenderHeight();
                        int[] saveBuffer = renderer.getFinishedAndReset();
                        frame++;
                        long time = filter.lastTimestamp;
                        if(base > time)
                            time = base;
                        lastFrame = new OutputFrameImage(time, (short)w, (short)h, saveBuffer);
                        rawOutputStream.write(lastFrame.dump(filter.videoChannel, base));
                        System.err.println("Informational: Saved frame #" + frame + ": " + w + "x" + h + " <" +
                            time + ">.");
                    } catch(IOException e) {
                        System.err.println("Warning: Failed to save screenshot image!");
                        errorDialog(e, "Failed to save screenshot", null, "Dismiss");
                    }
                }
            }
       }


       try {
           if(filter.gotFrame)
               connector.writeFrames(rawOutputStream, null);
       } catch(IOException e) {
           System.err.println("Warning: Failed to close video output stream!");
           errorDialog(e, "Failed to close video output", null, "Dismiss");
       }

       if(videoOut != null)
            videoOut.detach();

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
