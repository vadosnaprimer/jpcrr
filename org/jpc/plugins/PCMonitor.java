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

import java.awt.*;
import javax.swing.*;
import org.jpc.pluginsbase.Plugins;
import org.jpc.pluginsbase.Plugin;
import org.jpc.pluginsbase.ExternalCommandInterface;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import org.jpc.emulator.VGADigitalOut;
import org.jpc.emulator.*;

/**
 *
 * @author Rhys Newman
 */
public class PCMonitor implements Plugin, ExternalCommandInterface
{
    private static final long serialVersionUID = 6;
    private volatile VGADigitalOut vgaOutput;
    private volatile boolean signalCheck;
    private BufferedImage buffer;
    private int[] rawImageData;
    private int screenWidth, screenHeight;
    private MonitorPanel monitorPanel;
    private JFrame monitorWindow;
    private Thread monitorThread;
    private Plugins vPluginManager;

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
        vPluginManager = manager;
    }

    public boolean invokeCommand(String cmd, String[] args)
    {
        if("pcmonitor-setwinpos".equals(cmd) && args.length == 2) {
            int x2, y2;
            try {
                x2 = Integer.parseInt(args[0]);
                y2 = Integer.parseInt(args[1]);
            } catch(Exception e) {
                vPluginManager.signalCommandCompletion();
                return true;
            }
            final int x = x2;
            final int y = y2;
            final int w = screenWidth + 10;
            final int h = screenHeight + 40;

            if(!SwingUtilities.isEventDispatchThread())
                try {
                    SwingUtilities.invokeAndWait(new Thread() { public void run() {
                        PCMonitor.this.monitorWindow.setBounds(x, y, w, h); }});
                } catch(Exception e) {
                }
            else
                monitorWindow.setBounds(x, y, w, h);
            vPluginManager.signalCommandCompletion();
            return true;
        }
        return false;
    }

    public boolean systemShutdown()
    {
        //JVM will kill us.
        return true;
    }

    public void pcStopping()
    {
        //Not interesting.
    }

    public void pcStarting()
    {
        //Not interesting.
    }

    public void reconnect(PC pc)
    {
        VGADigitalOut previousOutput = vgaOutput;

        //Bump it a little.
        vgaOutput = null;
        monitorThread.interrupt();
        while(!signalCheck)
            ;

        synchronized(this) {
            if(previousOutput != null)
                previousOutput.unsubscribeOutput(this);

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

            notifyAll();   //Connection might have gotten established.
        }
    }

    public void main()
    {
        monitorThread = Thread.currentThread();
        while (true)  //JVM will kill us.
        {
            synchronized(this) {
                while(vgaOutput == null)
                    try {
                        signalCheck = true;
                        wait();
                        signalCheck = false;
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
        private static final long serialVersionUID = 9;
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
