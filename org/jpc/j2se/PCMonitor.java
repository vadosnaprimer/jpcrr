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

package org.jpc.j2se;

import java.awt.*;
import java.io.*;

import org.jpc.support.PNGSaver;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import org.jpc.support.VGADigitalOut;
import org.jpc.emulator.*;

/**
 *
 * @author Rhys Newman
 */
public class PCMonitor extends KeyHandlingPanel
{
    private static final long serialVersionUID = 6;
    private VGADigitalOut vgaOutput;
    private Updater updater;
    private BufferedImage buffer;
    private int[] rawImageData;
    private int screenWidth, screenHeight;

    private volatile boolean clearBackground;
    private PNGSaver dumpPics;

    public void reconnect(PC pc)
    {
        vgaOutput = pc.getVideoOutput();
        int width = vgaOutput.getWidth();
        int height = vgaOutput.getHeight();
        if(width > 0 && height > 0) {
            resizeDisplay(width, height);
        }
        repaint(0, 0, width, height);
    }



    public PCMonitor()
    {
        this(null);
    }

    public PCMonitor(LayoutManager mgr)
    {
        super(mgr);

        clearBackground = true;
        setDoubleBuffered(false);
        requestFocusInWindow();

        vgaOutput = null;
        setInputMap(WHEN_FOCUSED, null);
    }

    public void setPNGSave(PNGSaver save)
    {
        dumpPics = save;
    }

    public void setFrame(Component f) {
    }

    public synchronized void startUpdateThread()
    {
        stopUpdateThread();
        updater = new Updater();
        updater.start();
    }

    public synchronized void stopUpdateThread()
    {
        if (updater != null)
            updater.halt();
    }

    public synchronized boolean isRunning()
    {
        if (updater == null)
            return false;
        return updater.running;
    }

    class Updater extends Thread
    {
        private volatile boolean running = true;

        public Updater()
        {
            super("PC Monitor Updater Task");
        }

        public void run()
        {
            while (running)
            {
                if(vgaOutput.waitReadable()) {

                    int w = vgaOutput.getWidth();
                    int h = vgaOutput.getHeight();
                    int[] buffer = vgaOutput.getBuffer();
                    if(w > 0 && h > 0 && (w != screenWidth || h != screenHeight))
                            resizeDisplay(w, h);
                    int xmin = vgaOutput.getDirtyXMin();
                    int xmax = vgaOutput.getDirtyXMax();
                    int ymin = vgaOutput.getDirtyYMin();
                    int ymax = vgaOutput.getDirtyYMax();

                    for(int y = ymin; y < ymax; y++) {
                        int offset = y * w + xmin;
                        if(xmax >= xmin)
                            System.arraycopy(buffer, offset, rawImageData, offset, xmax - xmin);
                    }

                    if(dumpPics != null) {
                        try {
                            dumpPics.savePNG(vgaOutput.getDisplayBuffer(), vgaOutput.getWidth(),
                                vgaOutput.getHeight());
                        } catch(IOException e) {
                            System.err.println("WARNING: Failed to save screenshot image!");
                            e.printStackTrace();
                        }
                    }

                    vgaOutput.endReadable();
                    System.err.println("Region painted: (" + xmin + "," + ymin + ");(" + xmax + "," + ymax + ").");
                    repaint(xmin, ymin, xmax - xmin + 1, ymax - ymin + 1);
                }
            }
        }

        public void halt()
        {
            try
            {
                running = false;
                interrupt();
            }
            catch (SecurityException e) {}
        }
    }

    public void resizeDisplay(int width, int height)
    {
        setPreferredSize(new Dimension(width, height));
        setMaximumSize(new Dimension(width, height));
        setMinimumSize(new Dimension(width, height));

        if(width > 0 && height > 0) {
            buffer = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            buffer.setAccelerationPriority(1);
            DataBufferInt buf = (DataBufferInt) buffer.getRaster().getDataBuffer();
            rawImageData = buf.getData();
            int[] outputBuffer = vgaOutput.getDisplayBuffer();
            if(outputBuffer != null && outputBuffer.length > 0 && rawImageData.length > 0) {
                System.arraycopy(outputBuffer, 0, rawImageData, 0, rawImageData.length);
            }
        }
        screenWidth = width;
        screenHeight = height;
        clearBackground = true;
        revalidate();
        repaint(0, 0, screenWidth, screenHeight);
    }

    public void update(Graphics g)
    {
        paint(g);
    }

    public void paint(Graphics g)
    {
        int s2w = vgaOutput.getWidth();
        int s2h = vgaOutput.getHeight();
        if (clearBackground)
        {
            g.setColor(Color.white);
            Dimension s1 = getSize();

            if (s1.width > s2w)
                g.fillRect(s2w, 0, s1.width - s2w, s1.height);
            if (s1.height > s2h)
                g.fillRect(0, s2h, s1.width, s1.height - s2h);
            clearBackground = false;
        }
        g.drawImage(buffer, 0, 0, null);
        Dimension s = getSize();
        g.setColor(getBackground());
        g.fillRect(s2w, 0, s.width - s2w, s2h);
        g.fillRect(0, s2h, s.width, s.height - s2h);
    }
}
