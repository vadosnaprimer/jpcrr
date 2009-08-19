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

import org.jpc.support.Plugins;
import org.jpc.emulator.PC;
import org.jpc.emulator.peripheral.Keyboard;

public class PCStartStopTest extends JFrame implements org.jpc.Plugin
{
    private static final long serialVersionUID = 8;
    private Plugins vPluginManager;
    private Keyboard keyboard;

    public void systemShutdown()
    {
        //Not interested.
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

    public void notifyArguments(String[] args)
    {
        //Not interested.
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
                    vPluginManager.startPC();
                }
            });
        tmp.setAccelerator(KeyStroke.getKeyStroke("F5"));

        (tmp = file.add("Stop")).addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e)
                {
                    vPluginManager.stopPC();
                }
            });
        tmp.setAccelerator(KeyStroke.getKeyStroke("F6"));

        (tmp = file.add("Send <Left> Edge")).addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e)
                {
                    try {
                        keyboard.sendEdge(128 + 75);
                    } catch(Exception f) { f.printStackTrace(); }
                }
            });
        tmp.setAccelerator(KeyStroke.getKeyStroke("J"));

        (tmp = file.add("Send <Down> Edge")).addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e)
                {
                    try {
                        keyboard.sendEdge(128 + 80);
                    } catch(Exception f) { f.printStackTrace(); }
                }
            });
        tmp.setAccelerator(KeyStroke.getKeyStroke("K"));

        (tmp = file.add("Send <Right> Edge")).addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e)
                {
                    try {
                        keyboard.sendEdge(128 + 77);
                    } catch(Exception f) { f.printStackTrace(); }
                }
            });
        tmp.setAccelerator(KeyStroke.getKeyStroke("L"));

        (tmp = file.add("Send <Up> Edge")).addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e)
                {
                    try {
                        keyboard.sendEdge(128 + 72);
                    } catch(Exception f) { f.printStackTrace(); }
                }
            });
        tmp.setAccelerator(KeyStroke.getKeyStroke("I"));

        (tmp = file.add("Send <Enter> Edge")).addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e)
                {
                    try {
                        keyboard.sendEdge(28);
                    } catch(Exception f) { f.printStackTrace(); }
                }
            });
        tmp.setAccelerator(KeyStroke.getKeyStroke("M"));

        (tmp = file.add("Send <Escape> Edge")).addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e)
                {
                    try {
                        keyboard.sendEdge(1);
                    } catch(Exception f) { f.printStackTrace(); }
                }
            });
        tmp.setAccelerator(KeyStroke.getKeyStroke("N"));

        (tmp = file.add("Send <RShift> Edge")).addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e)
                {
                    try {
                        keyboard.sendEdge(54);
                    } catch(Exception f) { f.printStackTrace(); }
                }
            });
        tmp.setAccelerator(KeyStroke.getKeyStroke("B"));

        (tmp = file.add("Send <RCTRL> Edge")).addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e)
                {
                    try {
                        keyboard.sendEdge(128 + 29);
                    } catch(Exception f) { f.printStackTrace(); }
                }
            });
        tmp.setAccelerator(KeyStroke.getKeyStroke("V"));

        (tmp = file.add("Send <RALT> Edge")).addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e)
                {
                    try {
                        keyboard.sendEdge(128 + 56);
                    } catch(Exception f) { f.printStackTrace(); }
                }
            });
        tmp.setAccelerator(KeyStroke.getKeyStroke("C"));

        (tmp = file.add("Send <SPACE> Edge")).addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e)
                {
                    try {
                        keyboard.sendEdge(57);
                    } catch(Exception f) { f.printStackTrace(); }
                }
            });
        tmp.setAccelerator(KeyStroke.getKeyStroke("X"));

        bar.add(file);
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
