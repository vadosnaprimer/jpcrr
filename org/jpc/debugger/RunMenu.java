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
import java.io.*;
import java.awt.*;
import java.awt.event.*;

import javax.swing.*;
import javax.swing.event.*;
import javax.swing.text.*;
import javax.swing.undo.*;

import org.jpc.emulator.*;
import org.jpc.emulator.processor.*;
import org.jpc.emulator.memory.*;
import org.jpc.support.*;
import org.jpc.emulator.memory.codeblock.*;

public class RunMenu extends JMenu implements ActionListener
{
    private Runner runner;
    private JCheckBoxMenuItem background, normalBlocks, compiledBlocks, cachedUBlocks, riscBlocks, pauseTimer;
    private JMenuItem run, step, reset, setBlockSize, multiStep;
    private FunctionKeys functionKeys;

    private Processor processor;
    private PhysicalAddressSpace memory;
    private CodeBlockRecord codeBlockRecord;
    private Clock clock;
    private int multiSteps;

    public RunMenu()
    {
        super("Run");

        run = add("Start");
        run.addActionListener(this);
        run.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F5, 0, false));

        step = add("Single Step");
        step.addActionListener(this);
        step.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F8, 0, false));
        
        multiSteps = 10;
        multiStep = add("Multiple Steps ("+multiSteps+")");
        multiStep.addActionListener(this);
        multiStep.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F7, 0, false));
         
        addSeparator();
        background = new JCheckBoxMenuItem("Background Execution");
        add(background);
        background.setSelected(true);
        background.addActionListener(this);
        
        ButtonGroup gp = new ButtonGroup();
        gp.add(normalBlocks);
        gp.add(riscBlocks);
        gp.add(cachedUBlocks);
        gp.add(compiledBlocks);

        setBlockSize = add("Set Max Code Block Size");
        setBlockSize.addActionListener(this);

        addSeparator();
        pauseTimer = new JCheckBoxMenuItem("Pause Virtual Clock");
        pauseTimer.setSelected(false);
        add(pauseTimer);

        addSeparator();
        reset = add("Reset - NI");
        reset.addActionListener(this);

        runner = new Runner();
        functionKeys = new FunctionKeys();
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(functionKeys);
    }

    public void refresh()
    {
        processor = (Processor) JPC.getObject(Processor.class);
        memory = (PhysicalAddressSpace) JPC.getObject(PhysicalAddressSpace.class);
        codeBlockRecord = (CodeBlockRecord) JPC.getObject(CodeBlockRecord.class);
        
        PC pc = (PC) JPC.getObject(PC.class);
        if (pc != null)
            clock = pc.getSystemClock();

        if (codeBlockRecord != null) 
            codeBlockRecord.advanceDecode();
    }

    public void dispose()
    {
        stop();
        KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(functionKeys);
    }    

    class FunctionKeys implements KeyEventDispatcher
    {
        public boolean dispatchKeyEvent(KeyEvent e) 
        {
            if (e.getID() == KeyEvent.KEY_PRESSED)
            {
                if (e.getKeyCode() == KeyEvent.VK_F8)
                {
                    executeStep();
                    return true;
                }
                else if (e.getKeyCode() == KeyEvent.VK_F7)
                {
                    executeSteps(multiSteps);
                    return true;
                }

                if (e.getKeyCode() == KeyEvent.VK_F2)
                    JPC.getInstance().statusReport();
            }

            return false;
        }
    }

    class Runner implements Runnable
    {
        boolean running;
        Thread runnerThread;

        Runner()
        {
            running = false;
            runnerThread = null;
        }

        void startRunning()
        {
            if (running)
                return;

            if (runnerThread != null)
            {
                running = false;
                try
                {
                    runnerThread.join(1000);
                }
                catch (Exception e) {}

                try
                {
                    runnerThread.stop();
                }
                catch (Throwable t) {}
                runnerThread = null;
            }

            running = true;
            runnerThread = new Thread(this);
            runnerThread.setPriority(Thread.MIN_PRIORITY+1);
            runnerThread.start();
        }

        void stopRunning()
        {
            running = false;
        }

        class U1 implements Runnable
        {
            public void run()
            {
                JPC.getInstance().refresh();
            }
        }

        class Alerter implements Runnable
        {
            int type;
            String title, message;
            
            Alerter(String title, String message, int type)
            {
                this.title = title;
                this.message = message;
                this.type = type;
            }

            public void show()
            {
                try
                {
                    SwingUtilities.invokeAndWait(this);
                }
                catch (Exception e) {}
            }

            public void run()
            {
                 JOptionPane.showMessageDialog(JPC.getInstance(), message, title, type);
            }
        }

        public void run()
        {
	    
            if (codeBlockRecord == null)
                return;

            PC pc = (PC) JPC.getObject(PC.class);
	    pc.start();

            try
            {
                int delay = 2000;
                boolean bg = false;
                BreakpointsFrame bps = (BreakpointsFrame) JPC.getInstance().getObject(BreakpointsFrame.class);
                ExecutionTraceFrame trace = (ExecutionTraceFrame) JPC.getInstance().getObject(ExecutionTraceFrame.class);
                U1 u = new U1();
                long t1 = System.currentTimeMillis();

                JPC.getInstance().notifyExecutionStarted();
                for (long c=0; running; c++)
                {
                    bg = background.getState();

                    try
                    {
                        if (codeBlockRecord == null)
                            return;
                        
                        if (pauseTimer.isSelected())
                            clock.pause();
                        else
                            clock.resume();
                        
                        if (codeBlockRecord.executeBlock() == null)
                            throw new Exception("Unimplemented Opcode. IP = "+Integer.toHexString(processor.getInstructionPointer()).toUpperCase());
                        CodeBlock nextBlock = codeBlockRecord.advanceDecode();

                        if (bps != null)
                        {
                            int blockStart = processor.getInstructionPointer();
                            int blockEnd = blockStart + 1;
                            if (nextBlock != null)
                                blockEnd = blockStart + nextBlock.getX86Length();

                            BreakpointsFrame.Breakpoint bp = bps.checkForBreak(blockStart, blockEnd);
                            if (bp != null)
                            {
                                SwingUtilities.invokeAndWait(u);
                                
                                String addr = MemoryViewPanel.zeroPadHex(bp.getAddress(), 8);
                                String name = bp.getName();
                                new Alerter("Breakpoint", "Break at "+addr+": "+name, JOptionPane.INFORMATION_MESSAGE).show();
                                break;
                            }
                        }
                    }
                    catch (Exception e)
                    {
                        e.printStackTrace();
                        new Alerter("Processor Exception", "Exception during execution: "+e, JOptionPane.ERROR_MESSAGE).show();
                        break;
                    }
                    
                    if (bg)
                    {
                        if ((c % 1000) != 0)
                            continue;
                        
                        long t2 = System.currentTimeMillis();
                        if (t2 - t1 < delay)
                            continue;
                        t1 = t2;
                    }
                    
                    try
                    {
                        SwingUtilities.invokeAndWait(u);
                    }
                    catch (Exception e){}
                }
            }
            finally
            {
		pc.start();
                running = false;
                runnerThread = null;
                run.setLabel("Start");
                step.setEnabled(true);
                
                JPC.getInstance().notifyExecutionStopped();
            }
        }
    }
    
    public void executeStep()
    {
        executeSteps(1);
    }

    public void executeSteps(int count)
    {
        if (codeBlockRecord == null)
            return;

	PC pc = (PC) JPC.getObject(PC.class);
	pc.start();

        JPC.getInstance().notifyExecutionStarted();
        try
        {
            BreakpointsFrame bps = (BreakpointsFrame) JPC.getInstance().getObject(BreakpointsFrame.class);
            if (pauseTimer.isSelected())
                clock.pause();
            else
                clock.resume();

            for (int i=0; i<count; i++)
            {
                CodeBlock block = codeBlockRecord.executeBlock();
                if (block == null)
                    throw new Exception("Unimplemented Opcode at "+Integer.toHexString(processor.getInstructionPointer()).toUpperCase());
                
                CodeBlock nextBlock = codeBlockRecord.advanceDecode();
                
                if (bps != null)
                {
                    int blockStart = processor.getInstructionPointer();
                    int blockEnd = blockStart + 1;
                    if (nextBlock != null)
                        blockEnd = blockStart + nextBlock.getX86Length();
                    BreakpointsFrame.Breakpoint bp = bps.checkForBreak(blockStart, blockEnd);
                    if (bp != null)
                    {
                        String addr = MemoryViewPanel.zeroPadHex(bp.getAddress(), 8);
                        String name = bp.getName();
                        JOptionPane.showMessageDialog(JPC.getInstance(), "Break at "+addr+": "+name, "Breakpoint", JOptionPane.INFORMATION_MESSAGE);
                        break;
                    }
                }

                if ((count % 1000) == 999)
                    JPC.getInstance().refresh();
            }
        }
        catch (Exception e)
        {
            JOptionPane.showMessageDialog(JPC.getInstance(), "Exception During Step:\n"+e, "Execute", JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
        }
        finally
        {
	    pc.stop();
	    clock.pause();
            JPC.getInstance().notifyExecutionStopped();
        }
    }

    public void stop()
    {
        if (runner.running)
        {
            runner.stopRunning();
            run.setLabel("Start");
            step.setEnabled(true);
        }
    }

    public void toggleRun()
    {
        if (!runner.running)
        {
            runner.startRunning();
            run.setLabel("Stop");
            step.setEnabled(false);
        }
        else
        {
            runner.stopRunning();
            run.setLabel("Start");
            step.setEnabled(true);
        }

        JPC.getInstance().refresh();
    }

    public void actionPerformed(ActionEvent evt)
    {
        Object src = evt.getSource();

        if (src == reset)
        {
            stop();
            try
            {
                PC pc = (PC) JPC.getObject(PC.class);
                if (pc != null)
                    pc.reset();
                JPC.getInstance().loadNewPC(pc);
            }
            catch (Exception e) 
            {
                JOptionPane.showMessageDialog(this, "Failed to load BIOS: "+e, "PC Init Error", JOptionPane.ERROR_MESSAGE);
            }
        }
        else if (src == step)
            executeStep();
        else if (src == multiStep)
        {
            try
            {
                String val = JOptionPane.showInputDialog(JPC.getInstance(), "Enter the new number of steps to take each time", ""+multiSteps);
                multiSteps = Math.max(1, Integer.parseInt(val.toString().trim()));
                multiStep.setText("Multiple Steps ("+multiSteps+")");
            }
            catch (Exception e) {}
        }
        else if (src == run)
            toggleRun();
        else if (src == setBlockSize)
        {
            stop();
            int size = codeBlockRecord.getMaximumBlockSize();
            try
            {
                String val = JOptionPane.showInputDialog(JPC.getInstance(), "Enter the new code block size\n", "JPC Debugger", JOptionPane.QUESTION_MESSAGE);
                size = Integer.parseInt(val);
                codeBlockRecord.setMaximumBlockSize(size);
            }
            catch (Exception e) {}
            
            JPC.getInstance().refresh();
        }
    }

}
