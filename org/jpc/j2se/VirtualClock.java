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

package org.jpc.j2se;

import org.jpc.emulator.*;
import org.jpc.support.Clock;
import java.io.*;

/**
 *
 * @author Ian Preston
 */
public class VirtualClock extends AbstractHardwareComponent implements Clock
{
    private TimerPriorityQueue timers;
    private long currentTime;

    public void dumpSRPartial(org.jpc.support.SRDumper output) throws IOException
    {
        super.dumpSRPartial(output);
        output.dumpObject(timers);
        output.dumpLong(currentTime);
    }

    public VirtualClock(org.jpc.support.SRLoader input) throws IOException
    {
        super(input);
        timers = (TimerPriorityQueue)input.loadObject();
        currentTime = input.loadLong();
    }

    public VirtualClock()
    {
        timers = new TimerPriorityQueue(); // initial capacity to be revised
        currentTime = 0;
    }

    public void dumpStatusPartial(org.jpc.support.StatusDumper output)
    {
        super.dumpStatusPartial(output);
        output.println("\tcurrentTime " + currentTime);
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

    public synchronized Timer newTimer(TimerResponsive object)
    {
        Timer tempTimer = new Timer(object, this);
        return tempTimer;
    }

    private void process()
    {
        while(true) {
            Timer tempTimer;
            tempTimer = timers.peek();
            if ((tempTimer == null) || !tempTimer.check(getTime()))
                return;
        }
    }

    public synchronized void update(Timer object)
    {
        timers.remove(object);
        if (object.enabled())
        {
            timers.offer(object);
        }
    }

    public long getTime()
    {
        return currentTime;
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

    public void timePasses(int ticks)
    {
        if(currentTime % 1000000000 > (currentTime + ticks) % 1000000000) {
            System.err.println("Informational: Timer ticked " + (currentTime + ticks) + ".");
        }
        currentTime += ticks;
        process();
    }
}
