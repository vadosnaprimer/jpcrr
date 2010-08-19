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

import org.jpc.emulator.PC;
import org.jpc.emulator.Clock;
import org.jpc.emulator.EventRecorder;
import org.jpc.Misc;
import org.jpc.pluginsbase.Plugins;
import org.jpc.pluginsbase.Plugin;
import static org.jpc.Misc.moveWindow;

import javax.swing.*;
import java.awt.*;

public class TimeDisplay implements Plugin
{
    private JFrame window;
    private JPanel panel;
    private JLabel display;
    private PC pc;
    private Plugins pluginManager;
    private int nativeWidth, nativeHeight;

    public void eci_timedisplay_setwinpos(Integer x, Integer y)
    {
        moveWindow(window, x.intValue(), y.intValue(), nativeWidth, nativeHeight);
    }


    public TimeDisplay(Plugins _pluginManager)
    {
            pluginManager = _pluginManager;
            pc = null;
            window = new JFrame("Time Display" + Misc.emuname);
            panel = new JPanel();
            window.add(panel);
            display = new JLabel("Time: <NO PC CONNECTED>           ");
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

    private void updateTime(long timeNow, long timeEnd)
    {
        String text1;
        if(timeNow >= 0)
            text1 = "Time: " + (timeNow / 1000000) + " / " + (timeEnd / 1000000);
        else if(timeNow == -1)
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
        updateTime(-2, 0);
    }

    public void pcStopping()
    {
        PC.ResetButton brb = (PC.ResetButton)pc.getComponent(PC.ResetButton.class);
        EventRecorder rec = brb.getRecorder();
        long lastTime = rec.getLastEventTime();
        updateTime(((Clock)pc.getComponent(Clock.class)).getTime(), lastTime);
    }

    public void reconnect(org.jpc.emulator.PC _pc)
    {
        pc = _pc;
        if(pc != null) {
            PC.ResetButton brb = (PC.ResetButton)pc.getComponent(PC.ResetButton.class);
            EventRecorder rec = brb.getRecorder();
            long lastTime = rec.getLastEventTime();
            updateTime(((Clock)pc.getComponent(Clock.class)).getTime(), lastTime);
        } else
            updateTime(-1, 0);
    }
}
