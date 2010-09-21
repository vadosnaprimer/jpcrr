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
import org.jpc.pluginsaux.PCMonitorPanel;
import org.jpc.pluginsaux.PCMonitorPanelEmbedder;
import org.jpc.Misc;
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
public class PCMonitor implements Plugin, PCMonitorPanelEmbedder
{
    private static final long serialVersionUID = 7;
    private int nativeWidth;
    private int nativeHeight;
    private JFrame monitorWindow;
    private PCMonitorPanel panel;
    private Plugins pManager;

    public PCMonitor(Plugins manager)
    {
        pManager = manager;
        panel = new PCMonitorPanel(this, manager.getOutputConnector());
        manager.addSlaveObject(this, panel);

        monitorWindow = new JFrame("VGA Monitor" + Misc.emuname);
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
        pManager.invokeExternalCommand("luaplugin-sendmessage", new Object[]{msg});
    }

    public void eci_pcmonitor_setwinpos(Integer x, Integer y)
    {
        moveWindow(monitorWindow, x.intValue(), y.intValue(), nativeWidth, nativeHeight);
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
        panel.setPC(pc);
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
        pManager.addRenderer(r);
    }

    public void notifyFrameReceived(int w, int h)
    {
    }

    public void main()
    {
        //Panel has its own thread.
    }
}
