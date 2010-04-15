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

import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import javax.swing.*;
import org.jpc.pluginsbase.Plugins;
import org.jpc.pluginsbase.Plugin;
import org.jpc.pluginsaux.HUDRenderer;
import java.io.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import org.jpc.emulator.VGADigitalOut;
import org.jpc.emulator.*;
import static org.jpc.Misc.moveWindow;
import static org.jpc.Misc.errorDialog;
import org.jpc.pluginsaux.PNGSaver;

/**
 *
 * @author Rhys Newman
 */
public class PCMonitor implements Plugin, ActionListener
{
    private static final long serialVersionUID = 6;
    private volatile VGADigitalOut vgaOutput;
    private volatile boolean signalCheck;
    private BufferedImage buffer;
    private int ssSeq;
    private int[] renderBuffer;
    private int renderBufferW;
    private int renderBufferH;
    private int[] rawImageData;
    private int screenWidth, screenHeight;
    private MonitorPanel monitorPanel;
    private JFrame monitorWindow;
    private Thread monitorThread;
    private HUDRenderer renderer;

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

        renderer = new HUDRenderer();

        JMenuBar bar = new JMenuBar();
        JMenu lamp = new JMenu("Gain");
        bar.add(lamp);
        JMenuItem lampx;

        lampx = new JMenuItem("1x");
        lampx.setActionCommand("LAMP1X");
        lampx.addActionListener(this);
        lamp.add(lampx);

        lampx = new JMenuItem("2x");
        lampx.setActionCommand("LAMP2X");
        lampx.addActionListener(this);
        lamp.add(lampx);

        lampx = new JMenuItem("4x");
        lampx.setActionCommand("LAMP4X");
        lampx.addActionListener(this);
        lamp.add(lampx);

        lampx = new JMenuItem("8x");
        lampx.setActionCommand("LAMP8X");
        lampx.addActionListener(this);
        lamp.add(lampx);

        lampx = new JMenuItem("16x");
        lampx.setActionCommand("LAMP16X");
        lampx.addActionListener(this);
        lamp.add(lampx);

        lampx = new JMenuItem("32x");
        lampx.setActionCommand("LAMP32X");
        lampx.addActionListener(this);
        lamp.add(lampx);

        lamp = new JMenu("Screenshot");
        bar.add(lamp);

        lampx = new JMenuItem("VGA output buffer");
        lampx.setActionCommand("SSVGAOUT");
        lampx.addActionListener(this);
        lamp.add(lampx);

        lampx = new JMenuItem("render buffer");
        lampx.setActionCommand("SSRENDER");
        lampx.addActionListener(this);
        lamp.add(lampx);

        monitorWindow.setJMenuBar(bar);

        resizeDisplay(480, 360, true);
        monitorWindow.setSize(new Dimension(490, 420));
        monitorWindow.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        monitorWindow.setVisible(true);
    }

    public void eci_pcmonitor_setwinpos(Integer x, Integer y)
    {
        moveWindow(monitorWindow, x.intValue(), y.intValue(), screenWidth + 10, screenHeight + 60);
    }

    public void eci_hud_left_gap(Integer flags, Integer gap)
    {
        if((flags.intValue() & 1) != 0)
            renderer.setLeftGap(gap);
    }

    public void eci_hud_top_gap(Integer flags, Integer gap)
    {
        if((flags.intValue() & 1) != 0)
            renderer.setTopGap(gap);
    }

    public void eci_hud_right_gap(Integer flags, Integer gap)
    {
        if((flags.intValue() & 1) != 0)
            renderer.setRightGap(gap);
    }

    public void eci_hud_bottom_gap(Integer flags, Integer gap)
    {
        if((flags.intValue() & 1) != 0)
            renderer.setBottomGap(gap);
    }

    public void eci_hud_white_solid_box(Integer flags, Integer x, Integer y, Integer w, Integer h)
    {
        if((flags.intValue() & 1) != 0)
            renderer.whiteSolidBox(x, y, w, h);
    }

    public void eci_hud_box(Integer flags, Integer x, Integer y, Integer w, Integer h, Integer thick, Integer lr,
        Integer lg, Integer lb, Integer la, Integer fr, Integer fg, Integer fb, Integer fa)
    {
        if((flags.intValue() & 1) != 0)
            renderer.box(x, y, w, h, thick, lr, lg, lb, la, fr, fg, fb, fa);
    }

    public void eci_hud_circle(Integer flags, Integer x, Integer y, Integer r, Integer thick, Integer lr,
        Integer lg, Integer lb, Integer la, Integer fr, Integer fg, Integer fb, Integer fa)
    {
        if((flags.intValue() & 1) != 0)
            renderer.circle(x, y, r, thick, lr, lg, lb, la, fr, fg, fb, fa);
    }

    public void eci_hud_bitmap(Integer flags, Integer x, Integer y, String bmap, Integer lr,
        Integer lg, Integer lb, Integer la, Integer fr, Integer fg, Integer fb, Integer fa)
    {
        if((flags.intValue() & 1) != 0)
            renderer.bitmap(x, y, bmap, lr, lg, lb, la, fr, fg, fb, fa);
    }

    public void eci_hud_bitmap_binary(Integer flags, Integer x, Integer y, String bmap, Integer lr,
        Integer lg, Integer lb, Integer la, Integer fr, Integer fg, Integer fb, Integer fa)
    {
        if((flags.intValue() & 1) != 0)
            renderer.bitmapBinary(x, y, bmap, lr, lg, lb, la, fr, fg, fb, fa);
    }

    public void eci_screenshot_vgabuffer()
    {
        screenShot(false);
    }

    public void eci_screenshot_renderbuffer()
    {
        screenShot(true);
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
                    resizeDisplay(width, height, true);
                    monitorWindow.setSize(new Dimension(width + 10, height + 60));
                }
                monitorPanel.repaint(0, 0, width, height);
            } else {
                vgaOutput = null;
            }
            if(vgaOutput != null)
                vgaOutput.subscribeOutput(this);

            monitorPanel.validate();
            //monitorPanel.requestFocus();

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
                    renderer.setBackground(buffer, w, h);
                    vgaOutput.releaseOutputWaitAll(this);
                    w = renderBufferW = renderer.getRenderWidth();
                    h = renderBufferH = renderer.getRenderHeight();
                    renderBuffer = renderer.getFinishedAndReset();
                    if(w > 0 && h > 0 && (w != screenWidth || h != screenHeight)) {
                        resizeDisplay(w, h, false);
                        monitorWindow.setSize(new Dimension(w + 10, h + 60));
                    }
                    if(renderBuffer == null)
                        continue;

                    for(int y = 0; y < h; y++) {
                        int offset = y * w;
                        if(w > 0)
                            System.arraycopy(renderBuffer, offset, rawImageData, offset, w);
                    }

                    monitorPanel.repaint(0, 0, w, h);
                }
            }
        }
    }

    public void resizeDisplay(int width, int height, boolean repaint)
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
            if(repaint && outputBuffer != null && outputBuffer.length > 0 && rawImageData.length > 0) {
                System.arraycopy(outputBuffer, 0, rawImageData, 0, rawImageData.length);
            }
        }
        screenWidth = width;
        screenHeight = height;
        clearBackground = true;
        monitorPanel.revalidate();
        if(repaint)
            monitorPanel.repaint(0, 0, screenWidth, screenHeight);
    }

    public void actionPerformed(ActionEvent e)
    {
        String command = e.getActionCommand();
        if(command == null)
            return;
        if(command.equals("LAMP1X"))
            renderer.setLightAmplification(1);
        if(command.equals("LAMP2X"))
            renderer.setLightAmplification(2);
        if(command.equals("LAMP4X"))
            renderer.setLightAmplification(4);
        if(command.equals("LAMP8X"))
            renderer.setLightAmplification(8);
        if(command.equals("LAMP16X"))
            renderer.setLightAmplification(16);
        if(command.equals("LAMP32X"))
            renderer.setLightAmplification(32);
        if(command.equals("SSVGAOUT"))
            screenShot(false);
        if(command.equals("SSRENDER"))
            screenShot(true);
    }

    private void screenShot(boolean asRendered)
    {
        int w = 0;
        int h = 0;
        int[] buffer = null;

        if(asRendered) {
            w = renderBufferW;
            h = renderBufferH;
            buffer = renderBuffer;
        } else {
            if(vgaOutput != null) {
                w = vgaOutput.getWidth();
                h = vgaOutput.getHeight();
                buffer = vgaOutput.getBuffer();
                if(w * h > buffer.length) {
                    System.err.println("Error: Can't get stable VGA output buffer.");
                    return;
                }
            }
        }
        if(buffer == null || w == 0 || h == 0) {
            System.err.println("Error: No image to screenshot.");
            return;
        }


        String name = "Screenshot-" + System.currentTimeMillis() + "-" + (ssSeq++) + ".png";
        try {
            DataOutputStream out = new DataOutputStream(new FileOutputStream(name));
            PNGSaver.savePNG(out, buffer, w, h);
            out.close();
        } catch(Exception e) {
            errorDialog(e, "Can't save screenshot", null, "Dismiss");
        }
        System.err.println("Informative: Screenshot '" + name + "' saved.");
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
                s2w = screenWidth;
                s2h = screenHeight;
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
