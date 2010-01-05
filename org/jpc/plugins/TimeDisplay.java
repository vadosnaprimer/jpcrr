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

import org.jpc.emulator.PC;
import org.jpc.emulator.Clock;
import org.jpc.pluginsbase.Plugins;
import org.jpc.pluginsbase.Plugin;
import org.jpc.pluginsbase.ExternalCommandInterface;
import org.jpc.pluginsaux.ConstantTableLayout;
import static org.jpc.Misc.errorDialog;

import javax.swing.*;
import java.util.*;
import java.awt.event.*;
import java.awt.*;

public class TimeDisplay implements Plugin, ExternalCommandInterface
{
    private JFrame window;
    private JPanel panel;
    private JLabel display;
    private PC pc;
    private Plugins pluginManager;
    private int nativeWidth, nativeHeight;

    public boolean invokeCommand(String cmd, String[] args)
    {
        if("timedisplay-setwinpos".equals(cmd) && args.length == 2) {
            int x2, y2;
            try {
                x2 = Integer.parseInt(args[0]);
                y2 = Integer.parseInt(args[1]);
            } catch(Exception e) {
                pluginManager.signalCommandCompletion();
                return true;
            }
            final int x = x2;
            final int y = y2;
            final int w = nativeWidth;
            final int h = nativeHeight;

            if(!SwingUtilities.isEventDispatchThread())
                try {
                    SwingUtilities.invokeAndWait(new Thread() { public void run() {
                        TimeDisplay.this.window.setBounds(x, y, w, h); }});
                } catch(Exception e) {
                }
            else
                window.setBounds(x, y, w, h);
            pluginManager.signalCommandCompletion();
            return true;
        }
        return false;
    }


    public TimeDisplay(Plugins _pluginManager)
    {
            pluginManager = _pluginManager;
            pc = null;
            window = new JFrame("Time Display");
            panel = new JPanel();
            window.add(panel);
            display = new JLabel("Time: <NO PC CONNECTED>");
            panel.add(display);

            window.pack();
            window.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
            Dimension d = window.getSize();
            nativeWidth = d.width;
            nativeHeight = d.height;
            window.setVisible(true);
    }

    public void main()
    {
        //This runs entierely in other threads
    }

    public boolean systemShutdown()
    {
        //OK to proceed with JVM shutdown.
        return true;
    }

    private void updateTime(long time)
    {
        String text1;
        if(time >= 0)
            text1 = "Time: " + time / 1000000;
        else if(time == -1)
            text1 = "Time: <NO PC CONNECTED>";
        else
            text1 = "Time: <N/A>";
        final String text = text1;

        if(pluginManager.isShuttingDown())
            return;  //Too much of deadlock risk.

        if(!SwingUtilities.isEventDispatchThread())
            try {
                SwingUtilities.invokeAndWait(new Thread() { public void run() { display.setText(text); }});
            } catch(Exception e) {
            }
        else
            display.setText(text);
    }

    public void pcStarting()
    {
        updateTime(-2);
    }

    public void pcStopping()
    {
        updateTime(((Clock)pc.getComponent(Clock.class)).getTime());
    }

    public void reconnect(org.jpc.emulator.PC _pc)
    {
        pc = _pc;
        if(pc != null)
            updateTime(((Clock)pc.getComponent(Clock.class)).getTime());
        else
            updateTime(-1);
    }
}
