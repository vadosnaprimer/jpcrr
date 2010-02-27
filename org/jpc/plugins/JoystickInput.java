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

import org.jpc.modules.Joystick;
import org.jpc.pluginsbase.Plugins;
import org.jpc.pluginsbase.Plugin;
import static org.jpc.Misc.errorDialog;
import static org.jpc.Misc.moveWindow;

import javax.swing.*;
import java.util.*;
import java.io.*;
import java.awt.event.*;
import java.awt.*;

public class JoystickInput implements ActionListener, Plugin
{
    private JFrame window;
    private JPanel panel;
    private org.jpc.modules.Joystick joy;
    private Plugins pluginManager;
    private int nativeWidth, nativeHeight;
    private JTextField[] axisInput;
    private JLabel[] axisValue;
    private JButton[] updateAxis;
    private JToggleButton[] buttons;

    public void eci_joystickinput_setwinpos(Integer x, Integer y)
    {
        moveWindow(window, x.intValue(), y.intValue(), nativeWidth, nativeHeight);
    }

    public JoystickInput(Plugins _pluginManager)
    {
            pluginManager = _pluginManager;
            window = new JFrame("Joystick input");
            GridLayout layout = new GridLayout(0, 4);
            panel = new JPanel(layout);
            window.add(panel);

            axisInput = new JTextField[4];
            axisValue = new JLabel[4];
            updateAxis = new JButton[4];
            buttons = new JToggleButton[4];

            for(int i = 0; i < 4; i++) {
                axisValue[i] = new JLabel("<N/A>       ");
                panel.add(axisValue[i]);
                axisInput[i] = new JTextField("10000", 10);
                panel.add(axisInput[i]);
                updateAxis[i] = new JButton("Update");
                updateAxis[i].setActionCommand("A" + i);
                updateAxis[i].setEnabled(false);
                updateAxis[i].addActionListener(this);
                panel.add(updateAxis[i]);
                buttons[i] = new JToggleButton("Button");
                buttons[i].setActionCommand("B" + i);
                buttons[i].addActionListener(this);
                buttons[i].setSelected(false);
                buttons[i].setEnabled(false);
                panel.add(buttons[i]);
            }

            window.pack();
            window.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
            Dimension d = window.getSize();
            nativeWidth = d.width;
            nativeHeight = d.height;
            window.setVisible(true);
    }

    public void resetButtons()
    {
        if(joy != null) {
            for(int i = 0; i < 4; i++) {
                axisValue[i].setText("" + joy.axisHoldTime(i, true));
                updateAxis[i].setEnabled(true);
                buttons[i].setEnabled(true);
                buttons[i].setSelected(joy.buttonState(i, true));
            }
        } else {
            for(int i = 0; i < 4; i++) {
                axisValue[i].setText("<N/A>");
                updateAxis[i].setEnabled(false);
                buttons[i].setSelected(false);
                buttons[i].setEnabled(false);
            }
        }
    }

    public void main()
    {
        //This runs entierely in UI thread.
    }

    public boolean systemShutdown()
    {
        //OK to proceed with JVM shutdown.
        return true;
    }

    public void pcStarting()
    {
        //Not interested.
    }

    public void pcStopping()
    {
        if(pluginManager.isShuttingDown())
            return;  //Too much of deadlock risk.

        if(!SwingUtilities.isEventDispatchThread())
            try {
                SwingUtilities.invokeAndWait(new Thread() { public void run() { JoystickInput.this.resetButtons(); }});
            } catch(Exception e) {
            }
        else
            resetButtons();
    }

    public void reconnect(org.jpc.emulator.PC pc)
    {
        if(pc != null) {
            joy = (Joystick)pc.getComponent(Joystick.class);
        } else {
            joy = null;
        }
        pcStopping();   //Do the equivalent actions.
    }

    public void actionPerformed(ActionEvent evt)
    {
        if(joy == null)
            return;

        String command = evt.getActionCommand();
        if(command == null)
            return;

        if(command.startsWith("A")) {
            try {
                int i = Integer.parseInt(command.substring(1));
                joy.setAxis(i, Long.parseLong(axisInput[i].getText()));
            } catch(Exception e) {
                errorDialog(new IOException("Can't set joystick Axis"), "Can't send event", null, "Dismiss");
            }
        }
        if(command.startsWith("B")) {
            try {
                int i = Integer.parseInt(command.substring(1));
                joy.setButton(i, buttons[i].isSelected());
            } catch(Exception e) {
                errorDialog(new IOException("Can't set joystick Button"), "Can't send event", null, "Dismiss");
            }
        }

        resetButtons();
    }
}
