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

package org.jpc.emulator;

import org.jpc.emulator.Clock;
import java.io.*;

/**
 *
 * @author Ian Preston
 */
public class VirtualClock extends AbstractHardwareComponent implements Clock
{
    private TimerPriorityQueue timers;
    private long currentTime;
    private long startTime;
    private long lastUpdateAt;
    private long currentMillisecs;

    public void dumpSRPartial(SRDumper output) throws IOException
    {
        super.dumpSRPartial(output);
        output.dumpObject(timers);
        output.dumpLong(currentTime);
    }

    public VirtualClock(SRLoader input) throws IOException
    {
        super(input);
        timers = (TimerPriorityQueue)input.loadObject();
        currentTime = input.loadLong();
        startTime = currentTime;
        currentMillisecs = 0;
        lastUpdateAt = 0;
    }

    public VirtualClock()
    {
        timers = new TimerPriorityQueue(); // initial capacity to be revised
        currentTime = 0;
    }

    public void dumpStatusPartial(StatusDumper output)
    {
        super.dumpStatusPartial(output);
        output.println("\tcurrentTime " + currentTime);
        output.println("\ttimers <object #" + output.objectNumber(timers) + ">"); if(timers != null) timers.dumpStatus(output);
    }

    public void dumpStatus(StatusDumper output)
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
        long curTime = System.currentTimeMillis();
        currentMillisecs += (curTime - lastUpdateAt);
        lastUpdateAt = curTime;
    }

    public void resume()
    {
        lastUpdateAt = System.currentTimeMillis();
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
            long curTime = System.currentTimeMillis();
            currentMillisecs += (curTime - lastUpdateAt);
            lastUpdateAt = curTime;
            System.err.println("Informational: Timer ticked " + (currentTime + ticks) + ", realtime: " + 
                currentMillisecs + "ms, " + ((currentTime + ticks) / (10000 * currentMillisecs)) + "%.");
        }
        currentTime += ticks;
        process();
    }
}
