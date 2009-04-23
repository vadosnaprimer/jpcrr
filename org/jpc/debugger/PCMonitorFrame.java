/*
    JPC: A x86 PC Hardware Emulator for a pure Java Virtual Machine
    Release Version 2.0

    A project from the Physics Dept, The University of Oxford

    Copyright (C) 2007 Isis Innovation Limited

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

    www.physics.ox.ac.uk/jpc
*/


package org.jpc.debugger;

import java.util.*;
import java.util.zip.*;
import java.io.*;
import java.awt.*;
import java.awt.event.*;

import javax.swing.*;
import javax.swing.table.*;
import javax.swing.event.*;
import javax.swing.text.*;
import javax.swing.undo.*;

import org.jpc.debugger.util.*;
import org.jpc.emulator.*;
import org.jpc.support.*;
import org.jpc.j2se.*;
import org.jpc.emulator.processor.*;
import org.jpc.emulator.motherboard.*;
import org.jpc.emulator.memory.*;

public class PCMonitorFrame extends UtilityFrame implements PCListener
{
    private PC currentPC;
    private PCMonitor monitor;
    private JScrollPane main;

    public PCMonitorFrame()
    {
        super("PC Monitor");

        currentPC = null;
        monitor = null;
        main = new JScrollPane();

        add("Center", main);
        setPreferredSize(new Dimension(PCMonitor.WIDTH + 20, PCMonitor.HEIGHT + 40));
        JPC.getInstance().objects().addObject(this);
    }

    public void loadMonitorState(File f) throws IOException
    {
        monitor.loadState(f);
    }

    public void resizeDisplay()
    {
        currentPC.getGraphicsCard().resizeDisplay(monitor);
    }
    
    public void saveState(ZipOutputStream zip) throws IOException
    {
        monitor.saveState(zip);
    }

    public void frameClosed()
    {
        if (monitor != null)
            monitor.stopUpdateThread();
        JPC.getInstance().objects().removeObject(this);
    }

    public void PCCreated() {}

    public void PCDisposed()
    {
        dispose();
    }
    
    public void executionStarted() {}

    public void executionStopped() {}

    public void refreshDetails() 
    {
        PC pc = (PC) JPC.getObject(PC.class);
        if (pc != currentPC)
        {
            if (monitor != null)
            {
                monitor.stopUpdateThread();
                main.setViewportView(new JPanel());
            }

            currentPC = pc;
            if (pc != null)
            {
                monitor = new PCMonitor(pc);
                monitor.startUpdateThread();
                main.setViewportView(monitor);
                monitor.revalidate();
                main.revalidate();
                monitor.requestFocus();
            }
        }
    }
}
