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

import org.jpc.emulator.*;
import org.jpc.support.*;
import java.io.*;

public class VirtualClock extends AbstractHardwareComponent implements Clock
{
    private PriorityVector timers;
    private long currentTime;

    public VirtualClock()
    {
        timers = new PriorityVector(25); // initial capacity to be revised
        currentTime = 0;
    }

    public void dumpStatusPartial(org.jpc.support.StatusDumper output)
    {
        super.dumpStatusPartial(output);
        output.println("\tcurrentTime" + currentTime);
        output.println("\ttimers <object #" + output.objectNumber(timers) + ">"); if(timers != null) timers.dumpStatus(output);
    }

    public void dumpStatus(org.jpc.support.StatusDumper output)
    {
        if(output.dumped(this))
            return;
        output.println("#" + output.objectNumber(this) + ": VirtualClock:");
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
        output.dumpObject(timers);
        output.dumpLong(currentTime);
    }

    public VirtualClock(org.jpc.support.SRLoader input) throws IOException
    {
        super(input);
        timers = (PriorityVector)(input.loadObject());
        currentTime = input.loadLong(); 
    }

    public static org.jpc.SRDumpable loadSR(org.jpc.support.SRLoader input, Integer id) throws IOException
    {
        org.jpc.SRDumpable x = new VirtualClock(input);
        input.endObject();
        return x;
    }

    public synchronized Timer newTimer(HardwareComponent object)
    {
        //System.out.println("Adding timer for " + (object.toString()) + ".");
        Timer tempTimer = new Timer(object, this);
        this.timers.addComparableObject(tempTimer);
        //System.out.println("Timers in list after addition:");
        //timers.printContents();
        return tempTimer;
    }

    public synchronized void process()
    {
        while(true)
        {
            Timer tempTimer = (Timer) timers.firstElement();
            if ((tempTimer == null) || !tempTimer.check(getTime()))
                break;

            timers.removeIfFirstElement(tempTimer);
            tempTimer.setStatus(false);
            tempTimer.runCallback();
        }

    }

    public synchronized void update(Timer object)
    {
        timers.removeElement(object);
        timers.addComparableObject(object);
    }

    public long getTime()
    {
        return currentTime;
    }

    private long getRealTime()
    {
        return currentTime++;
    }

    public long getTickRate()
    {
        return 1000000000L;
    }

    public void pause()
    {
    }

    public void resume()
    {
    }

    public void reset()
    {
    }

    public String toString()
    {
        return "Virtual Clock";
    }

    private long getSystemTimer()
    {
        return currentTime;
    }

    public void timePasses(int ticks)
    {
        if(currentTime % 1000000000 > (currentTime + ticks) % 1000000000) {
            System.out.println("Timer ticked " + (currentTime + ticks) + ".");
        }
        currentTime += ticks;
        process();
    }
}
