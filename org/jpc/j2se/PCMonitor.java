/*
    JPC: A x86 PC Hardware Emulator for a pure Java Virtual Machine
    Release Version 2.0

    A project from the Physics Dept, The University of Oxford

    Copyright (C) 2007 Isis Innovation Limited

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


package org.jpc.j2se;

import java.util.*;
import java.util.zip.*;
import java.io.*;
import java.awt.*;
import java.awt.color.*;
import java.awt.image.*;
import java.awt.event.*;

import javax.swing.*;
import javax.swing.event.*;

import org.jpc.emulator.processor.*;
import org.jpc.emulator.*;
import org.jpc.support.*;
import org.jpc.emulator.motherboard.*;
import org.jpc.emulator.memory.*;
import org.jpc.emulator.memory.codeblock.*;
import org.jpc.emulator.peripheral.*;
import org.jpc.emulator.pci.peripheral.*;

public class PCMonitor extends KeyHandlingPanel implements GraphicsDisplay
{
    public static final int WIDTH = 720;
    public static final int HEIGHT = 400;

    private PC pc;
    private Keyboard keyboard;
    private VGACard vgaCard;
    private VGADigitalOut digitalOut;

    private BufferedImage buffer;
    private int[] rawImageData;
    private PNGSaver dumpPics;

    private Updater updater;
    private int xmin, xmax, ymin, ymax, width, height, mouseX, mouseY;
    private boolean resized, doubleSize;

    public PCMonitor(PC pc)
    {
        this(null, pc);
    }

    public PCMonitor(LayoutManager mgr, PC pc)
    {
        super(mgr);

        this.pc = pc;
        setDoubleBuffered(false);
        requestFocusInWindow();
        doubleSize = false;
        mouseX = mouseY = 0;

        vgaCard = pc.getGraphicsCard();
        keyboard = pc.getKeyboard();
        digitalOut = vgaCard.getDigitalOut();
        resizeDisplay(WIDTH, HEIGHT);
        setInputMap(WHEN_FOCUSED, null);
    }

    public void setPNGSave(PNGSaver save) 
    {
        dumpPics = save;
    }

    public void reconnect(PC pc)
    {
        this.pc = pc;
        vgaCard = pc.getGraphicsCard();
        keyboard = pc.getKeyboard();
        digitalOut = vgaCard.getDigitalOut();
        //vgaCard.resizeDisplay(this);
        int w = digitalOut.getWidth();
        int h = digitalOut.getHeight();
        resizeDisplay(w, h);
        if(w > 0 && h > 0)
            System.arraycopy(digitalOut.getBuffer(), 0, rawImageData, 0, w * h);
        if (doubleSize)
            repaint(0, 0, 2 * w, 2 * h);
        else
            repaint(0, 0, w, h);
    }

    public void saveState(ZipOutputStream zip) throws IOException
    {
        ZipEntry entry = new ZipEntry("ScreenData");
        zip.putNextEntry(entry);
        byte[] dummy = new byte[rawImageData.length*4];
        for (int i=0, j=0; i<rawImageData.length; i++)
        {
            int val = rawImageData[i];
            dummy[j++] = (byte) (val >> 24);
            dummy[j++] = (byte) (val >> 16);
            dummy[j++] = (byte) (val >> 8);
            dummy[j++] = (byte) (val);
        }

        DataOutputStream output = new DataOutputStream(zip);
        output.writeInt(rawImageData.length);
        zip.write(dummy);
        zip.closeEntry();
    }

    public void loadState(File f) throws IOException
    {
        ZipFile zip = new ZipFile(f);
        ZipEntry entry = new ZipEntry("ScreenData");
        DataInputStream input = new DataInputStream(zip.getInputStream(entry));
        int len = input.readInt();

        if (len != rawImageData.length)
            throw new IOException("Image size not comsistent with saved image state");

        byte[] dummy = new byte[len*4];
        input.readFully(dummy);
        for (int i=0, j=0; i<len; i++)
        {
            int val = 0;
            val |= (0xff & dummy[j++]) << 24;
            val |= (0xff & dummy[j++]) << 16;
            val |= (0xff & dummy[j++]) << 8;
            val |= 0xff & dummy[j++];

            rawImageData[i] = val;
        }
    }

    public void repeatedKeyPress(int keyCode)
    {
        keyboard.keyPressed(KeyMapping.getScancode(new Integer(keyCode)));
    }

    public void keyPressed(int keyCode)
    {
        keyboard.keyPressed(KeyMapping.getScancode(new Integer(keyCode)));
    }

    public void keyReleased(int keyCode)
    {
        keyboard.keyReleased(KeyMapping.getScancode(new Integer(keyCode)));
    }

    public void mouseEventReceived(int dx, int dy, int dz, int buttons)
    {
        if (doubleSize)
            keyboard.putMouseEvent(dx/2, dy/2, dz, buttons);
        else
            keyboard.putMouseEvent(dx, dy, dz, buttons);
    }

    public void startUpdateThread()
    {
        startUpdateThread(Thread.currentThread().getPriority());
    }

    public void startUpdateThread(int vgaUpdateThreadPriority)
    {
        updater = new Updater();
        updater.setPriority(vgaUpdateThreadPriority);
        updater.start();
    }

    public void stopUpdateThread()
    {
        try
        {
            updater.running = false;
            updater.interrupt();
        }
        catch (Throwable t) {}
    }

    public void setDoubleSize(boolean value)
    {
        if (doubleSize == value)
            return;

        Dimension d = getPreferredSize();
        if (value)
            setPreferredSize(new Dimension(d.width*2, d.height*2));
        else
            setPreferredSize(new Dimension(d.width/2, d.height/2));
        doubleSize = value;

        revalidate();
        repaint();
    }

    class Updater extends Thread
    {
        boolean running = true;

         public Updater()
        {
            super("PC Monitor Updater Task");
        }

        public void run()
        {
            while (running)
            {
                try
                {
                    synchronized(vgaCard) {
                        vgaCard.wait();
                    }

                    int w = digitalOut.getWidth();
                    int h = digitalOut.getHeight();
                    int[] buffer = digitalOut.getBuffer();
                    if(w > 0 && h > 0 && (w != width || h != height))
                            resizeDisplay(w, h);
                    xmin = digitalOut.getDirtyXMin();
                    xmax = digitalOut.getDirtyXMax();
                    ymin = digitalOut.getDirtyYMin();
                    ymax = digitalOut.getDirtyYMax();

                    for(int y = ymin; y < ymax; y++) {
                        int offset = y * width + xmin;
                        if(xmax >= xmin)
                            System.arraycopy(buffer, offset, rawImageData, offset, xmax - xmin);
                    }

                    if(dumpPics != null) {
                        dumpPics.savePNG(digitalOut.getBuffer(), width, height);
                        System.err.println("Saved frame at " + (double)pc.getSystemClock().getTime() /
                            pc.getSystemClock().getTickRate() + "s.");
                    }

                    synchronized(vgaCard) {
                        vgaCard.notifyAll();
                    }

                    if (doubleSize)
                        repaint(2*xmin, 2*ymin, 2*(xmax - xmin + 1), 2*(ymax - ymin + 1));
                    else
                        repaint(xmin, ymin, xmax - xmin + 1, ymax - ymin + 1);
                }
                catch (InterruptedException e)
                {
                }
                catch (ThreadDeath d)
                {
                    running = false;
                }
                catch (Throwable t)
                {
                    System.err.println("Warning: error in video display update " + t);
                    t.printStackTrace();
                }
            }
        }
    }

    public int rgbToPixel(int red, int green, int blue)
    {
        return 0xFF000000 | ((0xFF & red) << 16) | ((0xFF & green) << 8) | (0xFF & blue);
    }

    private final void prepareUpdate()
    {
        xmin = width;
        xmax = 0;
        ymin = height;
        ymax = 0;
    }

    public void resizeDisplay(int width, int height)
    {
        resized = true;
        this.width = width;
        this.height = height;

        buffer = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        WritableRaster raster = buffer.getRaster();
        DataBufferInt buf = (DataBufferInt) raster.getDataBuffer();
        rawImageData = buf.getData();

        int pink = Color.pink.getRGB();
        for (int i=0; i<rawImageData.length; i++)
            rawImageData[i] = pink;

        setPreferredSize(new Dimension(width, height));
        revalidate();
        repaint();
    }

    public int[] getDisplayBuffer()
    {
        return rawImageData;
    }

    public final void dirtyDisplayRegion(int x, int y, int w, int h)
    {
        xmin = Math.min(x, xmin);
        xmax = Math.max(x+w, xmax);
        ymin = Math.min(y, ymin);
        ymax = Math.max(y+h, ymax);
    }

    public void resetDirtyRegion()
    {
        xmin = width;
        ymin = height;
        xmax = 0;
        ymax = 0;
    }


    public void update(Graphics g)
    {
        paint(g);
    }

    protected void paintPCMonitor(Graphics g)
    {
        int w = width;
        int h = height;

        if (doubleSize)
        {
            w *= 2;
            h *= 2;
            g.drawImage(buffer, 0, 0, w, h, null);
        }
        else
            g.drawImage(buffer, 0, 0, null);

        Dimension s = getSize();
        g.setColor(getBackground());
        g.fillRect(w, 0, s.width - w, h);
        g.fillRect(0, h, s.width, s.height - h);
    }

    protected void defaultPaint(Graphics g)
    {
        super.paint(g);
    }

    public void paint(Graphics g)
    {
        paintPCMonitor(g);
    }

    public static void main(String[] args) throws Exception
    {
        try
        {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        }
        catch (Exception e) {}

        if (args.length == 0)
            args = new String[] { "-fda", "mem:floppy.img", "-hda", "mem:dosgames.img", "-boot", "fda" };

        PC pc = PC.createPC(args, new VirtualClock());
        PCMonitorFrame frame = PCMonitorFrame.createMonitor("JPC Monitor", pc, args);
    }
}
