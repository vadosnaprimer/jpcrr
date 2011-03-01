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

package org.jpc.pluginsaux;

import java.awt.*;
import java.util.*;
import java.awt.event.ActionListener;
import java.awt.event.MouseListener;
import java.awt.event.ActionEvent;
import java.awt.event.MouseEvent;
import javax.swing.*;
import org.jpc.hud.HUDRenderer;
import java.io.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import org.jpc.emulator.VGADigitalOut;
import org.jpc.emulator.*;
import org.jpc.output.*;
import org.jpc.bus.*;
import static org.jpc.Misc.errorDialog;
import static org.jpc.Misc.castToString;
import org.jpc.pluginsaux.PNGSaver;

/**
 *
 * @author Rhys Newman
 */
public class PCMonitorPanel implements ActionListener, MouseListener
{
    private static final String SCREENSHOT_VGA = "screenshot-vga-buffer";
    private static final String SCREENSHOT_RENDER = "screenshot-render-buffer";
    private static final long serialVersionUID = 6;
    private boolean exitThread;
    private OutputStatic outputServer;
    private OutputClient outputClient;
    private volatile boolean signalCheck;
    private BufferedImage buffer;
    private int ssSeq;
    private int[] renderBuffer;
    private int renderBufferW;
    private int renderBufferH;
    private int[] rawImageData;
    private int screenWidth, screenHeight;
    private MonitorPanel monitorPanel;
    private Thread monitorThread;
    private HUDRenderer renderer;
    private PCMonitorPanelEmbedder embedder;
    private java.util.List<JMenu> menusNeeded;
    private OutputFrameImage lastFrame;
    private Bus bus;

    private volatile boolean clearBackground;

    public HUDRenderer getRenderer()
    {
        return renderer;
    }

    public PCMonitorPanel(PCMonitorPanelEmbedder embedWhere, Bus _bus)
    {
        bus = _bus;
        bus.setCommandHandler(this, "busScreenshot", SCREENSHOT_VGA);
        bus.setCommandHandler(this, "busScreenshot", SCREENSHOT_RENDER);
        OutputStatic serv = null;
        serv = (OutputStatic)((bus.executeCommandNoFault("get-pc-output", null))[0]);

        clearBackground = true;
        monitorPanel = new MonitorPanel();
        monitorPanel.setDoubleBuffered(false);
        monitorPanel.requestFocusInWindow();
        outputServer = serv;
        outputClient = new OutputClient(serv);

        monitorPanel.setInputMap(JPanel.WHEN_FOCUSED, null);

        embedder = embedWhere;
        renderer = new HUDRenderer(1);
        embedder.notifyRenderer(renderer);
        menusNeeded = new ArrayList<JMenu>();
        JMenu lamp;
        JMenuItem lampx;

        menusNeeded.add(lamp = new JMenu("Gain"));

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

        menusNeeded.add(lamp = new JMenu("Screenshot"));

        lampx = new JMenuItem("VGA output buffer");
        lampx.setActionCommand("SSVGAOUT");
        lampx.addActionListener(this);
        lamp.add(lampx);

        lampx = new JMenuItem("render buffer");
        lampx.setActionCommand("SSRENDER");
        lampx.addActionListener(this);
        lamp.add(lampx);

        monitorPanel.addMouseListener(this);
        resizeDisplay(480, 360, true);
    }

    public String busScreenshot_help(String cmd, boolean brief)
    {
        if(brief && SCREENSHOT_VGA.equals(cmd))
            return "Screenshot the VGA output buffer";
        if(brief && SCREENSHOT_RENDER.equals(cmd))
            return "Screenshot the Render buffer";
        System.err.println("Synopsis: " + cmd + " [<filename>]");
        if(SCREENSHOT_VGA.equals(cmd)) {
            System.err.println("Writes VGA output buffer into specified file.");
        } else if(SCREENSHOT_RENDER.equals(cmd)) {
            System.err.println("Writes render buffer into specified file.");
        }
        System.err.println("If filename is not specified, a name is automatically picked.");
        return null;
    }

    public void busScreenshot(BusRequest req, String cmd, Object[] args) throws IllegalArgumentException
    {
        if(args != null && args.length > 1)
            throw new IllegalArgumentException("Command takes an optonal argument");
        String name = null;
        if(args != null && args.length == 1)
            name = castToString(args[0]);

        if(SCREENSHOT_VGA.equals(cmd))
            screenShot(false, name);
        else if(SCREENSHOT_RENDER.equals(cmd))
            screenShot(true, name);
        req.doReturn();
    }

    public java.util.List<JMenu> getMenusNeeded()
    {
        return menusNeeded;
    }

    public JPanel getMonitorPanel()
    {
        return monitorPanel;
    }

    public void startThread()
    {
        (new Thread(new Runnable() { public void run() { main(); }}, "Monitor Panel Thread")).start();
    }

    public void exitMontorPanelThread()
    {
        bus.detachObject(this);
        exitThread = true;
    }

    public void main()
    {
        monitorThread = Thread.currentThread();
        while(!exitThread) {  //JVM will kill us.
            synchronized(this) {
                if(outputClient.aquire()) {
                    OutputFrame f = outputServer.lastFrame(OutputFrameImage.class);
                    if(f != null)
                        lastFrame = (OutputFrameImage)f;
                    else {
                        outputClient.releaseWaitAll();
                        continue;
                    }
                    int w = lastFrame.getWidth();
                    int h = lastFrame.getHeight();
                    embedder.notifyFrameReceived(w, h);
                    int[] buffer = lastFrame.getImageData();
                    renderer.setBackground(buffer, w, h);
                    outputClient.releaseWaitAll();
                    w = renderBufferW = renderer.getRenderWidth();
                    h = renderBufferH = renderer.getRenderHeight();
                    renderBuffer = renderer.getFinishedAndReset();
                    if(w > 0 && h > 0 && (w != screenWidth || h != screenHeight)) {
                        resizeDisplay(w, h, false);
                        embedder.notifySizeChange(w, h);
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
        outputClient.detach();
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
            screenShot(false, null);
        if(command.equals("SSRENDER"))
            screenShot(true, null);
    }

    private void screenShot(boolean asRendered, String name)
    {
        int w = 0;
        int h = 0;
        int[] buffer = null;

        if(asRendered) {
            w = renderBufferW;
            h = renderBufferH;
            buffer = renderBuffer;
        } else {
            if(lastFrame != null) {
                w = lastFrame.getWidth();
                h = lastFrame.getHeight();
                buffer = lastFrame.getImageData();
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

        if(name == null)
            name = "Screenshot-" + System.currentTimeMillis() + "-" + (ssSeq++) + ".png";
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
            if(screenHeight > 0 && screenWidth > 0) {
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

    private int getButtonNumber(MouseEvent e)
    {
        int button = e.getButton();
        if(button == MouseEvent.BUTTON1)
            return 1;
        else if(button == MouseEvent.BUTTON2)
            return 2;
        else if(button == MouseEvent.BUTTON3)
            return 3;
        return 0;
    }

    public void mouseClicked(MouseEvent e)
    {
        int button = getButtonNumber(e);
        int x = e.getX();
        int y = e.getY();
        embedder.sendMessage("MouseClicked " + button + " " + x + " " + y);
    }

    public void mouseEntered(MouseEvent e)
    {
    }

    public void mouseExited(MouseEvent e)
    {
    }

    public void mousePressed(MouseEvent e)
    {
        int button = getButtonNumber(e);
        int x = e.getX();
        int y = e.getY();
        embedder.sendMessage("MousePressed " + button + " " + x + " " + y);
    }

    public void mouseReleased(MouseEvent e)
    {
        int button = getButtonNumber(e);
        int x = e.getX();
        int y = e.getY();
        embedder.sendMessage("MouseReleased " + button + " " + x + " " + y);
    }
}
