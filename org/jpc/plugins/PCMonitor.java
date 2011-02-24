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
import org.jpc.bus.*;
import org.jpc.pluginsaux.HUDRenderer;
import org.jpc.pluginsaux.PCMonitorPanel;
import org.jpc.pluginsaux.PCMonitorPanelEmbedder;
import org.jpc.Misc;
import java.io.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import org.jpc.emulator.VGADigitalOut;
import org.jpc.emulator.*;
import static org.jpc.Misc.castToInt;
import static org.jpc.Misc.moveWindow;
import static org.jpc.Misc.errorDialog;
import org.jpc.pluginsaux.PNGSaver;

/**
 *
 * @author Rhys Newman
 */
public class PCMonitor implements PCMonitorPanelEmbedder
{
    private static final long serialVersionUID = 7;
    private int nativeWidth;
    private int nativeHeight;
    private JFrame monitorWindow;
    private PCMonitorPanel panel;
    private Bus bus;

    public void emunameChanged(String cmd, Object[] args)
    {
        if(monitorWindow != null)
            Misc.emunameHelper(monitorWindow, "VGA Monitor");
    }

    public PCMonitor(Bus _bus)
    {
        bus = _bus;
        bus.setShutdownHandler(this, "systemShutdown");
        bus.setEventHandler(this, "emunameChanged", "emuname-changed");
        bus.setCommandHandler(this, "setWinPos", "set-pcmonitor-window-position");

        panel = new PCMonitorPanel(this, bus);

        monitorWindow = new JFrame("VGA Monitor" + Misc.getEmuname());
        monitorWindow.getContentPane().add("Center", panel.getMonitorPanel());
        JMenuBar bar = new JMenuBar();
        for(JMenu menu : panel.getMenusNeeded())
            bar.add(menu);
        monitorWindow.setJMenuBar(bar);
        monitorWindow.pack();
        monitorWindow.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        Dimension d = monitorWindow.getSize();
        nativeWidth = d.width;
        nativeHeight = d.height;
        panel.startThread();
        monitorWindow.setVisible(true);
    }

    public void sendMessage(String msg)
    {
        try {
            bus.executeCommandSynchronous("send-lua-message", new Object[]{msg});
        } catch(Exception e) {
        }
    }

    public String setWinPos_help(String cmd, boolean brief)
    {
        if(brief)
            return "Set PC monitor plugin window position";
        System.err.println("Synopsis: " + cmd + " <x> <y>");
        System.err.println("Moves the PC monitor plugin window to coordinates <x>, <y>.");
        return null;
    }

    public void setWinPos(BusRequest req, String cmd, Object[] args) throws IllegalArgumentException
    {
        if(args == null || args.length != 2)
            throw new IllegalArgumentException("Command takes two arguments");
        moveWindow(monitorWindow, castToInt(args[0]), castToInt(args[1]), nativeWidth, nativeHeight);
        req.doReturn();
    }

    public boolean systemShutdown()
    {
        //JVM will kill us.
        panel.exitMontorPanelThread();
        try {
            bus.executeCommandSynchronous("remove-renderer", new Object[]{panel.getRenderer()});
        } catch(Exception e) {
        }
        if(!bus.isShuttingDown()) {
            monitorWindow.dispose();
        }
        return true;
    }

    public void notifySizeChange(int w, int h)
    {
        //Run it in AWT event thread to avoid deadlocking.
        SwingUtilities.invokeLater(new Runnable() { public void run() {
            monitorWindow.pack();
            Dimension d = monitorWindow.getSize();
            nativeWidth = d.width;
            nativeHeight = d.height;
        }});
    }

    public void notifyRenderer(HUDRenderer r)
    {
        try {
            bus.executeCommandSynchronous("add-renderer", new Object[]{r});
        } catch(Exception e) {
        }
    }

    public void notifyFrameReceived(int w, int h)
    {
    }

    public void main()
    {
        //Panel has its own thread.
    }
}
