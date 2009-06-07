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

package org.jpc.emulator;

import org.jpc.support.*;
import java.io.*;

public class Timer implements ComparableObject
{
    private long expireTime;
    private HardwareComponent callback;
    private boolean enabled;

    private Clock myOwner;

    public Timer(HardwareComponent object, Clock parent)
    {
        myOwner = parent;
        callback = object;
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

    public Timer(org.jpc.support.SRLoader input) throws IOException
    {
        input.objectCreated(this);
        expireTime = input.loadLong();
        enabled = input.loadBoolean();
        callback = (HardwareComponent)(input.loadObject());
        myOwner = (Clock)(input.loadObject());
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

    public boolean enabled()
    {
        synchronized(myOwner) {
            return enabled;
        }
    }

    public void setStatus(boolean status)
    {
        synchronized(myOwner) {
            enabled = status;
            myOwner.update(this);
        }
    }

    public void setExpiry(long time)
    {
        synchronized(myOwner) {
            expireTime = time;
            this.setStatus(true);
        }
    }

    public boolean check(long time)
    {
        synchronized(myOwner) {
            return this.enabled && (time >= expireTime);
        }
    }

    public void runCallback()
    {
        callback.timerCallback();
    }

    public long getExpiry()
    {
        synchronized(myOwner) {
            return expireTime;
        }
    }

    public String toString()
    {
        if(this.enabled)
            return "Timer for " + callback.toString() + " [" + expireTime + "]";
        else
            return "Timer for " + callback.toString() + " [Disabled]";
    }

    public int compareTo(Object o)
    {
        if (!(o instanceof Timer)) {
            //System.err.println("Comparing with NOT TIMER.");
            return -1;
        }

        if (this.enabled())
        {
            if (!((Timer)o).enabled())
                return -1;
            else if (((Timer) this).getExpiry() - ((Timer) o).getExpiry() < 0)
                return -1;
            else if (((Timer) this).getExpiry() - ((Timer) o).getExpiry() > 0)
                return 1;
            else
                return 0;
        }
        else
            return 1; //stick disabled timers at end of list
    }


}
