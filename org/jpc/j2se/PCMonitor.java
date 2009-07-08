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
import javax.swing.JScrollPane;
import org.jpc.emulator.*;
import org.jpc.emulator.pci.peripheral.*;
import org.jpc.emulator.peripheral.*;

/**
 *
 * @author Rhys Newman
 */
public class PCMonitor extends KeyHandlingPanel
{
    private DefaultVGACard vgaCard;
    private Updater updater;
    private Component frame = null;

    private volatile boolean clearBackground;
    private PNGSaver dumpPics;

    public void reconnect(PC pc)
    {
        vgaCard = (DefaultVGACard) pc.getComponent(VGACard.class);
        vgaCard.setMonitor(this);
        Dimension size = vgaCard.getDisplaySize();
        vgaCard.resizeDisplay(size.width, size.height);
        repaint(0, 0, size.width, size.height);
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

        vgaCard = null;
        setInputMap(WHEN_FOCUSED, null);
    }

    public void setPNGSave(PNGSaver save)
    {
        dumpPics = save;
    }

    public void setFrame(Component f) {
        this.frame = f;
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
                if(vgaCard.startReadable()) {
                    vgaCard.prepareUpdate();
                    vgaCard.updateDisplay();

                    if(dumpPics != null) {
                        Dimension size = vgaCard.getDisplaySize();
                        try {
                            dumpPics.savePNG(vgaCard.getDisplayBuffer(), size.width, size.height);
                        } catch(IOException e) {
                            System.err.println("WARNING: Failed to save screenshot image!");
                            e.printStackTrace();
                        }
                    }

                    int xmin = vgaCard.getXMin();
                    int xmax = vgaCard.getXMax();
                    int ymin = vgaCard.getYMin();
                    int ymax = vgaCard.getYMax();
                    vgaCard.endReadable();

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

        clearBackground = true;
        revalidate();
        repaint();
    }

    public void update(Graphics g)
    {
        paint(g);
    }

    public void paint(Graphics g)
    {
        if (clearBackground)
        {
            g.setColor(Color.white);
            Dimension s1 = getSize();
            Dimension s2 = vgaCard.getDisplaySize();

            if (s1.width > s2.width)
                g.fillRect(s2.width, 0, s1.width - s2.width, s1.height);
            if (s1.height > s2.height)
                g.fillRect(0, s2.height, s1.width, s1.height - s2.height);
            clearBackground = false;
        }
        vgaCard.paintPCMonitor(g, this);
    }
}
