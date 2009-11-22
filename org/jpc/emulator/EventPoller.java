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

import java.io.*;

public class EventPoller implements SRDumpable, TimerResponsive {

    private Clock timingSource;
    private Timer callback;
    private static final long CALLBACK_INTERVAL = 20000;

    public EventPoller(Clock clock)
    {
        this.timingSource = clock;
        callback = clock.newTimer(this);
        callback.setExpiry(CALLBACK_INTERVAL);
    }

    public void dumpStatusPartial(StatusDumper output)
    {
        output.println("\ttimingSource <object #" + output.objectNumber(timingSource) + ">"); if(timingSource != null) timingSource.dumpStatus(output);
        output.println("\tcallback <object #" + output.objectNumber(callback) + ">"); if(callback != null) callback.dumpStatus(output);
    }

    public void dumpStatus(StatusDumper output)
    {
        if(output.dumped(this))
            return;

        output.println("#" + output.objectNumber(this) + ": EventPoller:");
        dumpStatusPartial(output);
        output.endObject();
    }

    public void dumpSRPartial(SRDumper output) throws IOException
    {
        output.dumpObject(timingSource);
        output.dumpObject(callback);
    }

    public EventPoller(SRLoader input) throws IOException
    {
        input.objectCreated(this);
        timingSource = (Clock)input.loadObject();
        callback = (Timer)input.loadObject();
    }

    public void callback()
    {
        callback.setExpiry(timingSource.getTime() + CALLBACK_INTERVAL);
    }

    public int getTimerType()
    {
        return 73;
    }
}
