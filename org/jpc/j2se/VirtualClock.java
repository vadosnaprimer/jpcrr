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
    private static boolean useNanos = false;

    private PriorityVector timers;

    private boolean ticksEnabled;
    private long ticksOffset;
    private long ticksStatic;
    private long currentTime;

    private VirtualClockBackgroundTask clockTask;

    private static final int CLOCK_TASK_PERIOD = 10;
    private static final int CLOCK_DRIFT_ADJUST_PERIOD = 1000;

    static {
	try {
	    System.nanoTime();
	    useNanos = true;
	} catch (Throwable t) {};
    }

    public VirtualClock()
    {
	timers = new PriorityVector(20); // initial capacity to be revised
	ticksEnabled = false;
	ticksOffset = 0;
	ticksStatic = 0;
	currentTime = getSystemTimer();
    }

    public void dumpState(DataOutput output) throws IOException
    {
        output.writeBoolean(ticksEnabled);
        output.writeLong(ticksOffset);
        output.writeLong(getTime());
    }

    public void loadState(DataInput input) throws IOException
    {
        timers = new PriorityVector(20);
        ticksEnabled = input.readBoolean();
        ticksOffset = input.readLong();
        ticksStatic = input.readLong();
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
	if (ticksEnabled)
	    return this.getRealTime() + ticksOffset;
        else
	    return ticksStatic;
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
        if (clockTask == null)
            return;
        
        clockTask.running = false;
        try
        {
            clockTask.join(5000);
        }
        catch (Throwable t) {}

        try
        {
            clockTask.stop();
        }
        catch (Throwable t) {}
        clockTask = null;

	if (ticksEnabled) 
        {
	    ticksStatic = getTime();
	    ticksEnabled = false;
	}
    }

    public void resume()
    {
        clockTask = new VirtualClockBackgroundTask();
        clockTask.start();

	if (!ticksEnabled) 
        {
	    ticksOffset = ticksStatic - getRealTime();
	    ticksEnabled = true;
	}

    }

    public void reset()
    {
	this.pause();
	ticksOffset = 0;
	ticksStatic = 0;
    }

    public String toString()
    {
	return "Virtual Clock";
    }

    private long getSystemTimer()
    {
	if (useNanos)
	    return System.nanoTime();
	else
	    return 1000000L * System.currentTimeMillis();
    }

    class VirtualClockBackgroundTask extends Thread
    {
	boolean running = true;

	public VirtualClockBackgroundTask()
	{
	    super("Virtual Clock Task");
            int p = Math.min(Thread.currentThread().getThreadGroup().getMaxPriority(), Thread.MAX_PRIORITY-2);
	    this.setPriority(p);
	}

	public void run()
	{
	    currentTime = getSystemTimer();

	    long driftCorrection = 0;
	    int countdown = CLOCK_DRIFT_ADJUST_PERIOD / CLOCK_TASK_PERIOD;

	    while (running) {
                try {
                    synchronized (this)	{
                        wait(CLOCK_TASK_PERIOD);
                    }
		    
		    currentTime += (1000000L * CLOCK_TASK_PERIOD) + driftCorrection;
		    
                    process();
		    
		    if (--countdown < 0) {
			countdown = CLOCK_DRIFT_ADJUST_PERIOD / CLOCK_TASK_PERIOD;
			long delta = getSystemTimer() - currentTime;
			driftCorrection += (delta / countdown);
		    }

                }
                catch (Throwable e) 
                {
                    System.out.println("Exception thrown from VirtualClock: " + e);
                }
            }
	}
    }
}
