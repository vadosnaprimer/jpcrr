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

import java.io.*;

import org.jpc.support.Clock;

/**
 * This class provides for the triggering of events on <code>TimerResponsive</code>
 * objects at defined and reconfigurable times.
 * @author Chris Dennis
 */
public class Timer implements Comparable<Timer>, org.jpc.SRDumpable
{
    private long expireTime;
    private TimerResponsive callback;
    private boolean enabled;
    private Clock myOwner;

    /**
     * Constructs a <code>Timer</code> which fires events on the specified
     * <code>TimerReponsive</code> object using the specified <code>Clock</code>
     * object as a time-source.
     * <p>
     * The constructed timer is initially disabled.
     * @param target object on which to fire callbacks.
     * @param parent time-source used to test expiry.
     */
    public Timer(TimerResponsive target, Clock parent)
    {
        myOwner = parent;
        callback = target;
        enabled = false;
    }

    public void dumpStatusPartial(org.jpc.support.StatusDumper output)
    {
        output.println("\texpireTime " + expireTime + " enabled " + enabled);
        output.println("\tcallback <object #" + output.objectNumber(callback) + ">"); if(callback != null) callback.dumpStatus(output);
        output.println("\tmyOwner <object #" + output.objectNumber(myOwner) + ">"); if(myOwner != null) myOwner.dumpStatus(output);
    }

    public void dumpStatus(org.jpc.support.StatusDumper output)
    {
        if(output.dumped(this))
            return;

        output.println("#" + output.objectNumber(this) + ": Timer:");
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
        output.dumpLong(expireTime);
        output.dumpBoolean(enabled);
        output.dumpObject(callback);
        output.dumpObject(myOwner);
    }

    public static org.jpc.SRDumpable loadSR(org.jpc.support.SRLoader input, Integer id) throws IOException
    {
        org.jpc.SRDumpable x = new Timer(input);
        input.endObject();
        return x;
    }

    public Timer(org.jpc.support.SRLoader input) throws IOException
    {
        input.objectCreated(this);
        expireTime = input.loadLong();
        enabled = input.loadBoolean();
        callback = (TimerResponsive)input.loadObject();
        myOwner = (Clock)input.loadObject();
    }

    public int getTimerType() {
        return callback.getTimerType();
    }

    /**
     * Returns <code>true</code> if this timer will expire at some point in the
     * future.
     * @return <code>true</code> if this timer is enabled.
     */
    public synchronized boolean enabled()
    {
        return enabled;
    }

    /**
     * Disables this timer.  Following a call to <code>disable</code> the timer
     * cannot ever fire again unless a call is made to <code>setExpiry</code>
     */
    public synchronized void disable()
    {
        setStatus(false);
    }

    /**
     * Sets the expiry time for and enables this timer.
     * <p>
     * No restrictions are set on the value of the expiry time.  Times in the past
     * will fire a callback at the next check.  Times in the future will fire on
     * the first call to check after their expiry time has passed.  Time units are
     * decided by the implementation of <code>Clock</code> used by this timer.
     * @param time absolute time of expiry for this timer.
     */
    public synchronized void setExpiry(long time)
    {
        expireTime = time;
        setStatus(true);
    }

    /**
     * Returns <code>true</code> and fires the targets callback method if this timer is enabled
     * and its expiry time is earlier than the supplied time.
     * @param time value of time to check against.
     * @return <code>true</code> if timer had expired and callback was fired.
     */
    public synchronized boolean check(long time)
    {
        if (this.enabled && (time >= expireTime)) {
            disable();
            callback.callback();
            return true;
        } else
            return false;
    }

    private void setStatus(boolean status)
    {
        enabled = status;
        myOwner.update(this);
    }

    public long getExpiry()
    {
        return expireTime;
    }

    public String toString()
    {
        if(this.enabled)
            return "Timer for " + callback.toString() + " [" + expireTime + "]";
        else
            return "Timer for " + callback.toString() + " [Disabled]";
    }

    public int compareTo(Timer o)
    {
        if (getExpiry() - o.getExpiry() < 0)
            return -1;
        else if (getExpiry() == o.getExpiry())
            return 0;
        else
            return 1;
    }

    public int hashCode()
    {
        int hash = 7;
        hash = 67 * hash + (int) (this.expireTime ^ (this.expireTime >>> 32));
        hash = 67 * hash + (this.enabled ? 1 : 0);
        return hash;
    }

    public boolean equals(Object o)
    {
        if (!(o instanceof Timer))
            return false;

        Timer t = (Timer)o;

        return (t.enabled() == enabled()) && (t.getExpiry() == getExpiry());
    }
}
