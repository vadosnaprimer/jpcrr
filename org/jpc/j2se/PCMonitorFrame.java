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

    private int thisMachineClockSpeedMHz = 3000;
    private boolean showSpeedDisplay;

    private long markTime;
    private DecimalFormat fmt, fmt2;
    private double mhz;
    private JProgressBar speedDisplay;

    public PCMonitorFrame(String title, PC pc, String[] args)
    {
        super(title);
        monitor = new PCMonitor(pc);

        this.pc = pc;
   
        try
        {
            showSpeedDisplay = true;
            thisMachineClockSpeedMHz = Integer.parseInt(ArgProcessor.findArg(args, "mhz", "-1"));
            if (thisMachineClockSpeedMHz == -1) {
                thisMachineClockSpeedMHz = 3000;
                showSpeedDisplay = false;
            } else if (thisMachineClockSpeedMHz < 500)
                thisMachineClockSpeedMHz = 3000;	   
        }
        catch (Exception e) {}
        
        mhz = 0;
        fmt = new DecimalFormat("0.00");
        fmt2 = new DecimalFormat("0.000");
        markTime = System.currentTimeMillis();
        monitorPane = new JScrollPane(monitor);
        getContentPane().add("Center", monitorPane);

        JMenuBar bar = new JMenuBar();

        JMenu file = new JMenu("File");
        start = file.add("Start");
        start.addActionListener(this);
        stop = file.add("Stop");
        stop.addActionListener(this);
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
            
        speedDisplay = new JProgressBar();
        speedDisplay.setStringPainted(true);
        speedDisplay.setString(" 0.00 Mhz");
        speedDisplay.setPreferredSize(new Dimension(100, 20));
        if (showSpeedDisplay)
        {
//             bar.add(Box.createHorizontalGlue());
//             bar.add(speedDisplay);
        }

        setJMenuBar(bar);
        setBounds(100, 100, monitor.WIDTH + 20, monitor.HEIGHT + 100);

        getContentPane().add("South", speedDisplay);

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

    private boolean updateMHz(long i)
    {
        long t2 = System.currentTimeMillis();

        if (t2 - markTime < 1000)
            return false;

        if (t2 == markTime)
            mhz = 0;
        else
            mhz = i * 1000.0 / (t2 - markTime) / 1000000;

        markTime = t2;
            
        double clockSpeed = 17.25/770*mhz/7.5*2.790;
        int percent = (int) (clockSpeed / thisMachineClockSpeedMHz * 1000 * 100 * 10);
        speedDisplay.setValue(percent);
        speedDisplay.setString(fmt.format(mhz)+" MHz or "+fmt2.format(clockSpeed)+" GHz Clock");
        //System.err.println("Speed (MHz): "+mhz);
        return true;
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
        markTime = System.currentTimeMillis();
        pc.start();
        long execCount = 0;
        try
        {
            while (running) {
                execCount += pc.execute();
                    
                if (execCount >= 50000) {
                    if (updateMHz(execCount))
                        execCount = 0;
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

