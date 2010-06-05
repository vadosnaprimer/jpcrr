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

package org.jpc.emulator;

import org.jpc.emulator.Clock;
import java.io.*;

/**
 *
 * @author Ian Preston
 */
public class Clock extends AbstractHardwareComponent
{
    private TimerPriorityQueue timers;
    private long currentTime;
    private long lastUpdateAt;
    private long currentMillisecs;
    private long lastMillisecs;

    public void dumpSRPartial(SRDumper output) throws IOException
    {
        super.dumpSRPartial(output);
        output.dumpObject(timers);
        output.dumpLong(currentTime);
    }

    public Clock(SRLoader input) throws IOException
    {
        super(input);
        timers = (TimerPriorityQueue)input.loadObject();
        currentTime = input.loadLong();
        currentMillisecs = 0;
        lastMillisecs = 0;
        lastUpdateAt = 0;
    }

    public Clock()
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

        output.println("#" + output.objectNumber(this) + ": Clock:");
        dumpStatusPartial(output);
        output.endObject();
    }

    public synchronized Timer newTimer(TimerResponsive object)
    {
        Timer tempTimer = new Timer(object, this);
        return tempTimer;
    }

    public synchronized void update(Timer object)
    {
        timers.remove(object);
        if(object.enabled())
            timers.offer(object);
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
        return "Clock";
    }

    public static long timePasses(Clock c, int ticks)
    {
        if(c.currentTime % 1000000000 > (c.currentTime + ticks) % 1000000000) {
            long curTime = System.currentTimeMillis();
            c.currentMillisecs += (curTime - c.lastUpdateAt);
            long dtreal = c.currentMillisecs - c.lastMillisecs;
            if(dtreal < 1) dtreal = 1;  /* Avoid Div-by-zero */
            c.lastUpdateAt = curTime;
            System.err.println("Informational: Timer ticked " + (c.currentTime + ticks) + ", realtime: " +
                dtreal + "ms, " + (100000 / dtreal) + "%," +
                " kips: " + (1000000000 / ticks) / dtreal + ".");
            c.lastMillisecs = c.currentMillisecs;
        }
        c.currentTime += ticks;

        while(true) {
            Timer tempTimer;
            tempTimer = c.timers.peek();
            if((tempTimer == null) || !tempTimer.check(c.getTime())) {
                if(tempTimer == null || !tempTimer.enabled())
                    return -1;
                return tempTimer.getExpiry();
            }
        }
    }

    // When does next timer fire. -1 if there's nothing scheduled.
    public long getNextEventTime()
    {
        Timer tempTimer;
        tempTimer = timers.peek();
        if(tempTimer == null || !tempTimer.enabled())
            return -1;
        return tempTimer.getExpiry();
    }
}
