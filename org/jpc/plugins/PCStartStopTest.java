/*
    JPC: A x86 PC Hardware Emulator for a pure Java Virtual Machine
    Release Version 2.0

    A project from the Physics Dept, The University of Oxford

    Copyright (C) 2007-2009 Isis Innovation Limited

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

    Details (including contact information) can be found at:

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

public class PCStartStopTest extends JFrame implements org.jpc.Plugin
{
    private static final long serialVersionUID = 8;
    private Plugins vPluginManager;

    public void systemShutdown()
    {
        //Not interested.
    }

    public void reconnect(PC pc)
    {
        //Not interested.
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

        JMenu file = new JMenu("Action");
        file.add("Start").addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e)
                {
                    vPluginManager.startPC();
                }
            });
        file.add("Stop").addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e)
                {
                    vPluginManager.stopPC();
                }
            });

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
