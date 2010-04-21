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

import org.jpc.emulator.peripheral.Keyboard;
import org.jpc.emulator.KeyboardStatusListener;
import org.jpc.pluginsbase.Plugins;
import org.jpc.pluginsbase.Plugin;
import org.jpc.pluginsaux.ConstantTableLayout;
import static org.jpc.Misc.errorDialog;
import static org.jpc.Misc.moveWindow;

import javax.swing.*;
import java.util.*;
import java.awt.event.*;
import java.awt.*;

public class MouseInput implements ActionListener, Plugin, KeyboardStatusListener
{
    private JFrame window;
    private JPanel panel;
    private JPanel xpanel;
    private JPanel ypanel;
    private JTextField xMotion;
    private JTextField yMotion;
    private JButton sendMotion;
    private JToggleButton mouseButtons[];
    private org.jpc.emulator.peripheral.Keyboard keyboard;
    private boolean[] cachedState;
    private Plugins pluginManager;
    private int nativeWidth, nativeHeight;

    public void eci_mouseInput_setwinpos(Integer x, Integer y)
    {
        moveWindow(window, x.intValue(), y.intValue(), nativeWidth, nativeHeight);
    }

    public MouseInput(Plugins _pluginManager)
    {
            pluginManager = _pluginManager;
            keyboard = null;
            window = new JFrame("Mouse Input");

            panel = new JPanel();
            panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
            window.add(panel);

            xpanel = new JPanel();
            xpanel.setLayout(new BoxLayout(xpanel, BoxLayout.X_AXIS));
            ypanel = new JPanel();
            ypanel.setLayout(new BoxLayout(ypanel, BoxLayout.X_AXIS));

            xpanel.add(new JLabel("X motion:"));
            ypanel.add(new JLabel("Y motion:"));
            xpanel.add(xMotion = new JTextField("0", 5));
            ypanel.add(yMotion = new JTextField("0", 5));

            panel.add(xpanel);
            panel.add(ypanel);
            sendMotion = new JButton("Send motion");
            sendMotion.setActionCommand("SEND");
            sendMotion.addActionListener(this);
            panel.add(sendMotion);

            cachedState = new boolean[5];
            mouseButtons = new JToggleButton[5];
            for(int i = 0; i < 5; i++) {
                mouseButtons[i] = new JToggleButton("Button #" + (i + 1));
                mouseButtons[i].setActionCommand("BUTTON");
                mouseButtons[i].addActionListener(this);
                panel.add(mouseButtons[i]);
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
        //TODO
    }

    public void keyExecStatusChange(int scancode, boolean pressed)
    {
        //Not interesting.
    }

    public void keyStatusChange(int scancode, boolean pressed)
    {
        //Not interesting.
    }

    public void keyStatusReload()
    {
        if(!SwingUtilities.isEventDispatchThread())
            try {
                SwingUtilities.invokeLater(new Thread() { public void run() { MouseInput.this.resetButtons(); }});
            } catch(Exception e) {
            }
        else
            resetButtons();
    }

    public void ledStatusChange(int newstatus)
    {
        //Not interesting.
    }

    public void mouseButtonsChange(int newstatus)
    {
        if(!SwingUtilities.isEventDispatchThread())
            try {
                SwingUtilities.invokeLater(new Thread() { public void run() { MouseInput.this.resetButtons(); }});
            } catch(Exception e) {
            }
        else
            resetButtons();
    }

    public void mouseExecButtonsChange(int newstatus)
    {
        //Not interesting.
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
                SwingUtilities.invokeAndWait(new Thread() { public void run() { MouseInput.this.resetButtons(); }});
            } catch(Exception e) {
            }
        else
            resetButtons();
    }

    public void reconnect(org.jpc.emulator.PC pc)
    {
        if(keyboard != null)
            keyboard.removeStatusListener(this);
        if(pc != null) {
            Keyboard keys = (Keyboard)pc.getComponent(Keyboard.class);
            keyboard = keys;
            keyboard.addStatusListener(this);
            keyStatusReload();
        } else {
            keyboard = null;
            keyStatusReload();
        }
    }

    public void actionPerformed(ActionEvent evt)
    {
        if(keyboard == null)
            return;

        String command = evt.getActionCommand();
        if("SEND".equals(command)) {
        } else if("BUTTON".equals(command)) {
        }
    }
}
