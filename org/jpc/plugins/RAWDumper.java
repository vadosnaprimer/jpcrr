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
import org.jpc.emulator.pci.peripheral.VGACard;
import org.jpc.output.*;
import org.jpc.pluginsaux.HUDRenderer;
import org.jpc.pluginsbase.Plugins;
import org.jpc.pluginsbase.Plugin;
import static org.jpc.Misc.errorDialog;
import static org.jpc.Misc.parseStringToComponents;

public class RAWDumper implements Plugin
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
    private PC pc;
    private Thread worker;
    private OutputStream rawOutputStream;
    private DumpFrameFilter filter;
    private HUDRenderer renderer;
	
	//PrintWriter writerVtotal, writerVrstart, writerVrend;

    public RAWDumper(Plugins pluginManager, String args) throws IOException
    {
		/*
		writerVtotal = new PrintWriter("vtotal.txt", "UTF-8");
		writerVrstart = new PrintWriter("vrstart.txt", "UTF-8");
		writerVrend = new PrintWriter("vrend.txt", "UTF-8");
		*/
		
        Map<String, String> params = parseStringToComponents(args);
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
        connector = pluginManager.getOutputConnector();
        videoOut = new OutputClient(connector);
        filter = new DumpFrameFilter();
        renderer = new HUDRenderer(2);
        pluginManager.addRenderer(renderer);
    }

    public boolean systemShutdown()
    {
        if(pcRunStatus) {
            return false;  //Don't shut down until after PC.
        }
		
		/*
		writerVtotal.close();
		writerVrstart.close();
		writerVrend.close();
		*/
		
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
        return true;
    }

    public void reconnect(PC _pc)
    {
        pcRunStatus = false;
        pc = _pc;
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
						if(w==0 || h==0) {
                            videoOut.releaseWaitAll();
                            continue;
						}						
                        renderer.setBackground(lastFrame.getImageData(), w, h);
                        videoOut.releaseWaitAll();
                        w = renderer.getRenderWidth();
                        h = renderer.getRenderHeight();
                        int[] saveBuffer = renderer.getFinishedAndReset();
                        frame++;
                        long time = filter.lastTimestamp;
                        if(base > time)
                            time = base;
						VGACard card = (VGACard)pc.getComponent(VGACard.class);
						int num = card.GetMasterFreq();
						int denom = card.GetVtotal();
                        lastFrame = new OutputFrameImage(time, (short)w, (short)h, num, denom, saveBuffer);
                        rawOutputStream.write(lastFrame.dump(filter.videoChannel, base));
						// logging
						/*
						VGACard card = (VGACard)pc.getComponent(VGACard.class);
						writerVtotal.println(card.draw_vtotal);
						writerVrstart.println(card.draw_vrstart);
						writerVrend.println(card.draw_vrend);
						*/
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
