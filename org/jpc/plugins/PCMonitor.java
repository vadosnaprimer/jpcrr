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

package org.jpc.plugins;

import java.awt.*;
import javax.swing.*;
import java.io.*;

import org.jpc.j2se.*;
import org.jpc.support.PNGSaver;
import org.jpc.support.Plugins;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import org.jpc.support.VGADigitalOut;
import org.jpc.emulator.*;

/**
 *
 * @author Rhys Newman
 */
public class PCMonitor implements org.jpc.Plugin
{
    private static final long serialVersionUID = 6;
    private VGADigitalOut vgaOutput;
    private BufferedImage buffer;
    private int[] rawImageData;
    private int screenWidth, screenHeight;
    private MonitorPanel monitorPanel;
    private JFrame monitorWindow;

    private volatile boolean clearBackground;

    public PCMonitor(Plugins manager)
    {
        clearBackground = true;
        monitorPanel = new MonitorPanel();
        monitorPanel.setDoubleBuffered(false);
        monitorPanel.requestFocusInWindow();

        vgaOutput = null;
        monitorPanel.setInputMap(JPanel.WHEN_FOCUSED, null);

        monitorWindow = new JFrame("VGA Monitor");
        monitorWindow.getContentPane().add("Center", monitorPanel);

        resizeDisplay(720, 400);
        monitorWindow.setSize(new Dimension(730, 440));
        monitorWindow.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        monitorWindow.setVisible(true);
    }

    public void systemShutdown()
    {
        //Not interesting.
    }

    public void pcStopping()
    {
        //Not interesting.
    }

    public void pcStarting()
    {
        //Not interesting.
    }

    public synchronized void reconnect(PC pc)
    {
        if(vgaOutput != null)
            vgaOutput.unsubscribeOutput(this);

        if(pc != null) {
            vgaOutput = pc.getVideoOutput();
            int width = vgaOutput.getWidth();
            int height = vgaOutput.getHeight();
            if(width > 0 && height > 0) {
                resizeDisplay(width, height);
                monitorWindow.setSize(new Dimension(width + 10, height + 40));
            }
            monitorPanel.repaint(0, 0, width, height);
        } else {
            vgaOutput = null;
        }
        if(vgaOutput != null)
            vgaOutput.subscribeOutput(this);

        monitorPanel.validate();
        monitorPanel.requestFocus();
        synchronized(this) {
            notifyAll();   //Connection might have gotten established.
        }
    }


    public void main()
    {
        while (true)  //JVM will kill us.
        {
            synchronized(this) {
                while(vgaOutput == null)
                    try {
                        wait();
                    } catch(Exception e) {
                    }

                if(vgaOutput.waitOutput(this)) {
                    int w = vgaOutput.getWidth();
                    int h = vgaOutput.getHeight();
                    int[] buffer = vgaOutput.getBuffer();
                    if(w > 0 && h > 0 && (w != screenWidth || h != screenHeight)) {
                        resizeDisplay(w, h);
                        monitorWindow.setSize(new Dimension(w + 10, h + 40));
                    }
                    int xmin = vgaOutput.getDirtyXMin();
                    int xmax = vgaOutput.getDirtyXMax();
                    int ymin = vgaOutput.getDirtyYMin();
                    int ymax = vgaOutput.getDirtyYMax();

                    for(int y = ymin; y < ymax; y++) {
                        int offset = y * w + xmin;
                        if(xmax >= xmin)
                            System.arraycopy(buffer, offset, rawImageData, offset, xmax - xmin);
                    }

                    vgaOutput.releaseOutput(this);
                    monitorPanel.repaint(xmin, ymin, xmax - xmin + 1, ymax - ymin + 1);
                }
            }
        }
    }

    public void resizeDisplay(int width, int height)
    {
        monitorPanel.setPreferredSize(new Dimension(width, height));
        monitorPanel.setMaximumSize(new Dimension(width, height));
        monitorPanel.setMinimumSize(new Dimension(width, height));

        if(width > 0 && height > 0) {
            buffer = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            buffer.setAccelerationPriority(1);
            DataBufferInt buf = (DataBufferInt) buffer.getRaster().getDataBuffer();
            rawImageData = buf.getData();
            int[] outputBuffer = null;
            if(vgaOutput != null)
                outputBuffer = vgaOutput.getDisplayBuffer();
            if(outputBuffer != null && outputBuffer.length > 0 && rawImageData.length > 0) {
                System.arraycopy(outputBuffer, 0, rawImageData, 0, rawImageData.length);
            }
        }
        screenWidth = width;
        screenHeight = height;
        clearBackground = true;
        monitorPanel.revalidate();
        monitorPanel.repaint(0, 0, screenWidth, screenHeight);
    }

    public class MonitorPanel extends JPanel
    {
        public void update(Graphics g)
        {
            paint(g);
        }

        public void paint(Graphics g)
        {
            int s2w, s2h;
            if(vgaOutput != null) {
                s2w = vgaOutput.getWidth();
                s2h = vgaOutput.getHeight();
            } else {
                Dimension s1 = getSize();
                s2w = s1.width;
                s2h = s1.height;
            }
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
}
