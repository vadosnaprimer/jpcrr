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
        timers = new PriorityVector(20); // initial capacity to be revised
        currentTime = 0;
    }

    public void dumpState(DataOutput output) throws IOException
    {
        output.writeLong(currentTime);
    }

    public void loadState(DataInput input) throws IOException
    {
        timers = new PriorityVector(20);
        currentTime = input.readLong();
    }

    public Timer newTimer(HardwareComponent object)
    {
        Timer tempTimer = new Timer(object, this);
        this.timers.addComparableObject(tempTimer);
        return tempTimer;
    }

    public void process()
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

    public void update(Timer object)
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
        currentTime += ticks;
        process();
    }
}
