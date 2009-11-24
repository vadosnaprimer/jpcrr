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

import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.*;
import java.util.zip.*;
import java.security.AccessControlException;
import javax.swing.*;

import org.jpc.pluginsbase.Plugins;
import org.jpc.pluginsbase.Plugin;
import org.jpc.pluginsbase.ExternalCommandInterface;
import org.jpc.emulator.PC;
import org.jpc.emulator.peripheral.Keyboard;
import static org.jpc.Misc.errorDialog;

public class PCStartStopTest extends JFrame implements Plugin, ExternalCommandInterface
{
    private static final long serialVersionUID = 8;
    private Plugins vPluginManager;
    private Keyboard keyboard;

    public boolean systemShutdown()
    {
        //Not interested (JVM kill ok)
        return true;
    }

    public void reconnect(PC pc)
    {
        if(pc != null) {
            keyboard = (Keyboard)pc.getComponent(Keyboard.class);
        } else {
            keyboard = null;
        }
    }

    public void pcStarting()
    {
        //Not interested.
    }

    public void pcStopping()
    {
        //Not interested.
    }

    public void main()
    {
        //Not interested.
    }

    public void connectPC(PC pc)
    {
        //Not interested.
    }

    public boolean invokeCommand(String cmd, String[] args)
    {
        if("pcstartstoptest-setwinpos".equals(cmd) && args.length == 2) {
            int x2, y2;
            try {
                x2 = Integer.parseInt(args[0]);
                y2 = Integer.parseInt(args[1]);
            } catch(Exception e) {
                return true;
            }
            final int x = x2;
            final int y = y2;

            if(!SwingUtilities.isEventDispatchThread())
                try {
                    SwingUtilities.invokeAndWait(new Thread() { public void run() {
                        PCStartStopTest.this.setBounds(x, y, 720, 50); }});
                } catch(Exception e) {
                }
            else
                setBounds(x, y, 720, 50);
            return true;
        }
        return false;
    }


    public PCStartStopTest(Plugins manager) throws Exception
    {
        super("Control test");

        this.vPluginManager = manager;

        JMenuBar bar = new JMenuBar();
        JMenuItem tmp;

        JMenu file = new JMenu("Action");
        (tmp = file.add("Start")).addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e)
                {
                    vPluginManager.invokeExternalCommand("pc-start", null);
                }
            });
        tmp.setAccelerator(KeyStroke.getKeyStroke("5"));

        (tmp = file.add("Stop")).addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e)
                {
                    vPluginManager.invokeExternalCommand("pc-stop", null);
                }
            });
        tmp.setAccelerator(KeyStroke.getKeyStroke("6"));

        (tmp = file.add("Send <Left> Edge")).addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e)
                {
                    try {
                        keyboard.sendEdge(128 + 75);
                    } catch(Exception f) {
                        errorDialog(f, "Failed to send keyboard event", null, "Dismiss");
                    }
                }
            });
        tmp.setAccelerator(KeyStroke.getKeyStroke("J"));

        (tmp = file.add("Send <Down> Edge")).addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e)
                {
                    try {
                        keyboard.sendEdge(128 + 80);
                    } catch(Exception f) {
                        errorDialog(f, "Failed to send keyboard event", null, "Dismiss");
                    }
                }
            });
        tmp.setAccelerator(KeyStroke.getKeyStroke("K"));

        (tmp = file.add("Send <Right> Edge")).addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e)
                {
                    try {
                        keyboard.sendEdge(128 + 77);
                    } catch(Exception f) {
                        errorDialog(f, "Failed to send keyboard event", null, "Dismiss");
                    }
                }
            });
        tmp.setAccelerator(KeyStroke.getKeyStroke("L"));

        (tmp = file.add("Send <Up> Edge")).addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e)
                {
                    try {
                        keyboard.sendEdge(128 + 72);
                    } catch(Exception f) {
                        errorDialog(f, "Failed to send keyboard event", null, "Dismiss");
                    }
                }
            });
        tmp.setAccelerator(KeyStroke.getKeyStroke("I"));

        (tmp = file.add("Send <Enter> Edge")).addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e)
                {
                    try {
                        keyboard.sendEdge(28);
                    } catch(Exception f) {
                        errorDialog(f, "Failed to send keyboard event", null, "Dismiss");
                    }
                }
            });
        tmp.setAccelerator(KeyStroke.getKeyStroke("M"));

        (tmp = file.add("Send <Escape> Edge")).addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e)
                {
                    try {
                        keyboard.sendEdge(1);
                    } catch(Exception f) {
                        errorDialog(f, "Failed to send keyboard event", null, "Dismiss");
                    }
                }
            });
        tmp.setAccelerator(KeyStroke.getKeyStroke("N"));

        (tmp = file.add("Send <RShift> Edge")).addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e)
                {
                    try {
                        keyboard.sendEdge(54);
                    } catch(Exception f) {
                        errorDialog(f, "Failed to send keyboard event", null, "Dismiss");
                    }
                }
            });
        tmp.setAccelerator(KeyStroke.getKeyStroke("B"));

        (tmp = file.add("Send <RCTRL> Edge")).addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e)
                {
                    try {
                        keyboard.sendEdge(128 + 29);
                    } catch(Exception f) {
                        errorDialog(f, "Failed to send keyboard event", null, "Dismiss");
                    }
                }
            });
        tmp.setAccelerator(KeyStroke.getKeyStroke("V"));

        (tmp = file.add("Send <RALT> Edge")).addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e)
                {
                    try {
                        keyboard.sendEdge(128 + 56);
                    } catch(Exception f) {
                        errorDialog(f, "Failed to send keyboard event", null, "Dismiss");
                    }
                }
            });
        tmp.setAccelerator(KeyStroke.getKeyStroke("C"));

        (tmp = file.add("Send <SPACE> Edge")).addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e)
                {
                    try {
                        keyboard.sendEdge(57);
                    } catch(Exception f) {
                        errorDialog(f, "Failed to send keyboard event", null, "Dismiss");
                    }
                }
            });
        tmp.setAccelerator(KeyStroke.getKeyStroke("X"));

        bar.add(file);

        JMenu save = new JMenu("Save");
        for(int i = 1; i <= 12; i++) {
            final int i2 = i;
            (tmp = save.add("Save slot #" + i)).addActionListener(new ActionListener() {
                    public void actionPerformed(ActionEvent e)
                    {
                        try {
                            vPluginManager.invokeExternalCommand("state-save", new String[]{"saveslot-" + i2 + ".jrsr"});
                        } catch(Exception f) {
                            errorDialog(f, "Failed to save state", null, "Dismiss");
                        }
                    }
                });
            tmp.setAccelerator(KeyStroke.getKeyStroke("F" + i));

            (tmp = save.add("Load slot #" + i)).addActionListener(new ActionListener() {
                    public void actionPerformed(ActionEvent e)
                    {
                        try {
                            vPluginManager.invokeExternalCommand("state-load", new String[]{"saveslot-" + i2 + ".jrsr"});
                        } catch(Exception f) {
                            errorDialog(f, "Failed to load state", null, "Dismiss");
                        }
                    }
                });
            tmp.setAccelerator(KeyStroke.getKeyStroke("shift F" + i));
        }

        bar.add(save);
        setJMenuBar(bar);

        try
        {
            setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        }
        catch (AccessControlException e)
        {
            System.err.println("Error: Not able to add some components to frame: " + e.getMessage());
        }

        setBounds(150, 150, 720, 50);
        validate();
        setVisible(true);
    }
}
