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

package org.jpc.j2se;

import java.util.*;
import java.io.*;
import java.text.*;
import java.awt.*;
import java.awt.event.*;
import java.security.*;

import javax.swing.*;

import org.jpc.emulator.*;
import org.jpc.support.*;

public class PCMonitorFrame extends JFrame implements ActionListener, Runnable
{
    protected PC pc;
    protected PCMonitor monitor;

    protected JMenuItem quit, stop, start, reset;
    protected JCheckBoxMenuItem doubleSize;

    private JScrollPane monitorPane;

    private boolean running;
    private Thread runner;

    private DecimalFormat fmt, fmt2;

    public PCMonitorFrame(String title, PC pc, String[] args)
    {
        super(title);
        monitor = new PCMonitor(pc);

        this.pc = pc;

        fmt = new DecimalFormat("0.00");
        fmt2 = new DecimalFormat("0.000");
        monitorPane = new JScrollPane(monitor);
        getContentPane().add("Center", monitorPane);

        JMenuBar bar = new JMenuBar();

        JMenu file = new JMenu("File");
        start = file.add("Start");
        start.addActionListener(this);
        start.setAccelerator(KeyStroke.getKeyStroke("control S"));
        stop = file.add("Stop");
        stop.addActionListener(this);
        stop.setAccelerator(KeyStroke.getKeyStroke("control shift S"));
        reset = file.add("Reset");
        reset.addActionListener(this);
        file.addSeparator();
        doubleSize = new JCheckBoxMenuItem("Double Size");
        file.add(doubleSize);
        doubleSize.addActionListener(this);
        file.addSeparator();
        quit = file.add("Quit");
        quit.addActionListener(this);
        bar.add(file);

        setJMenuBar(bar);
        setBounds(100, 100, monitor.WIDTH + 20, monitor.HEIGHT + 100);

        try
        {
            setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        }
        catch (AccessControlException e)
        {
            System.out.println("Not able to add some components to frame: " + e);
        }

    }

    public JScrollPane getMonitorPane()
    {
        return monitorPane;
    }

    protected synchronized void stop()
    {
        running = false;
        try
        {
            runner.join(5000);
        }
        catch (Throwable t){}

        try
        {
            runner.stop();
        }
        catch (Throwable t) {}
        stopNoWait();

        monitor.stopUpdateThread();
    }

    protected synchronized void stopNoWait()
    {
        running = false;
        runner = null;
        monitor.stopUpdateThread();
    }

    protected synchronized void start()
    {
        int p = Math.max(Thread.currentThread().getThreadGroup().getMaxPriority()-4, Thread.MIN_PRIORITY+1);
        System.out.println("Trying to set a thread priority of " + p + " for execute task");
        System.out.println("Trying to set a thread priority of " + p + " for update task");

        if (running)
            return;

        monitor.startUpdateThread(p);

        running = true;
        runner = new Thread(this, "PC Execute");
        runner.setPriority(p);
//         runner.setPriority(Thread.NORM_PRIORITY-1);
        runner.start();
    }

    protected void reset()
    {
        stop();
        pc.reset();
        start();
    }

    public void actionPerformed(ActionEvent evt)
    {
        if (evt.getSource() == quit)
            System.exit(0);
        else if (evt.getSource() == stop)
            stop();
        else if (evt.getSource() == start)
            start();
        else if (evt.getSource() == reset)
            reset();
        else if (evt.getSource() == doubleSize)
            monitor.setDoubleSize(doubleSize.isSelected());
    }

    public void run()
    {
        pc.start();
        long execCount = 0;
        boolean exitNow = false;
        try
        {
            while (running) {
                execCount += pc.execute();
                if(pc.getHitTraceTrap()) {
                    this.stopNoWait();
                    break;
                }
            }
        }
        catch (Exception e)
        {
            System.err.println("Caught exception @ Address:0x" + Integer.toHexString(pc.getProcessor().getInstructionPointer()));
            System.err.println(e);
            e.printStackTrace();
        }
        finally
        {
            pc.stop();
            System.err.println("PC Stopped");
        }
    }

    public static PCMonitorFrame createMonitor(PC pc)
    {
        return createMonitor("JPC", pc, null);
    }

    public static PCMonitorFrame createMonitor(String title, PC pc)
    {
        return createMonitor(title, pc, null);
    }

    public static PCMonitorFrame createMonitor(String title, PC pc, String[] args)
    {
        PCMonitorFrame result = new PCMonitorFrame(title, pc, args);
        result.validate();
        result.setVisible(true);
        result.start();

        return result;
    }
}

