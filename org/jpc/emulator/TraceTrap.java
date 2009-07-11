/*
    JPC: A x86 PC Hardware Emulator for a pure Java Virtual Machine
    Release Version 2.0

    A project from the Physics Dept, The University of Oxford

    Copyright (C) 2007 Isis Innovation Limited
    Copyrighy (C) 2009 H. Ilari Liusvaara

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

package org.jpc.emulator;

import org.jpc.support.*;
import org.jpc.emulator.processor.Processor;
import java.io.*;

public class TraceTrap extends AbstractHardwareComponent implements TimerResponsive
{
    private long traceFlags;
    private boolean trapActive;
    private Timer trapTimer;
    private Processor processor;
    public final static long TRACE_STOP_VRETRACE_START = 0x00000001;
    public final static long TRACE_STOP_VRETRACE_END = 0x00000002;
    public final static long TRACE_STOP_IMMEDIATE = 0x80000000;

    public TraceTrap()
    {
        traceFlags = 0;
        trapActive = false;
        trapTimer = null;
    }

    public void setTrapTime(long trapTime)
    {
        trapTimer.setExpiry(trapTime);
    }

    public void clearTrapTime()
    {
        trapTimer.disable();
    }

    public synchronized boolean getAndClearTrapActive()
    {
       boolean tmp = trapActive;
       trapActive = false;
       processor.eflagsMachineHalt = false;
       return tmp;
    }

    public synchronized void setTrapFlag(long flag, boolean status)
    {
        if(status)
            traceFlags |= flag;
        else
            traceFlags &= ~flag;
        System.out.println("Trap flags now " + traceFlags + ".");
    }

    public synchronized void doPotentialTrap(long flag)
    {
        if(((traceFlags | TRACE_STOP_IMMEDIATE) & flag) != 0) {
            System.out.println("Doing trap because of " + (traceFlags & flag) + ".");
            trapActive = true;
            processor.eflagsMachineHalt = true;
        }
    }

    public boolean initialised()
    {
        return (trapTimer != null && processor != null);
    }

    public boolean updated()
    {
        return (trapTimer != null && processor != null);
    }

    public void updateComponent(HardwareComponent component)
    {
        //Nothing to do here.
    }

    public void acceptComponent(HardwareComponent component)
    {
        if ((component instanceof Clock) && component.initialised())
        {
            trapTimer = ((Clock)component).newTimer(this);
        }
        if ((component instanceof Processor) && component.initialised())
        {
            processor = (Processor)component;
        }
    }

    public void dumpStatusPartial(org.jpc.support.StatusDumper output)
    {
        super.dumpStatusPartial(output);
        //As PC can't be savestated when running and traps only matter when its running, don't savestate the
        //traps.
    }

    public void dumpStatus(org.jpc.support.StatusDumper output)
    {
        if(output.dumped(this))
            return;

        output.println("#" + output.objectNumber(this) + ": TraceTrap:");
        dumpStatusPartial(output);
        output.endObject();
    }

    public void dumpSR(org.jpc.support.SRDumper output) throws IOException
    {
        if(output.dumped(this))
            return;
        dumpSRPartial(output);
        output.endObject();
    }

    public void dumpSRPartial(org.jpc.support.SRDumper output) throws IOException
    {
        super.dumpSRPartial(output);
        output.dumpObject(processor);
    }

    public static org.jpc.SRDumpable loadSR(org.jpc.support.SRLoader input, Integer id) throws IOException
    {
        org.jpc.SRDumpable x = new TraceTrap(input);
        input.endObject();
        return x;
    }

    public TraceTrap(org.jpc.support.SRLoader input) throws IOException
    {
        super(input);
        processor = (Processor)input.loadObject();
    }

    public void callback()
    {
        doPotentialTrap(TRACE_STOP_IMMEDIATE);
    }

    public int getTimerType()
    {
        return 8;
    }

}
