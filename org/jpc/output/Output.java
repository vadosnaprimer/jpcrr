/*
    JPC-RR: A x86 PC Hardware Emulator
    Release 1

    Copyright (C) 2007-2009 Isis Innovation Limited
    Copyright (C) 2009-2010 H. Ilari Liusvaara

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

package org.jpc.output;

import java.io.*;
import java.util.*;
import org.jpc.emulator.StatusDumper;
import org.jpc.emulator.SRDumpable;
import org.jpc.emulator.SRDumper;
import org.jpc.emulator.SRLoader;

public class Output implements SRDumpable
{
    //Not saved.
    private long timeAdjust;
    private OutputStatic staticOutput;
    private boolean channelTableUpdated;
    //Saved.
    private Map<Short, OutputChannel> channels;

    public Output()
    {
        timeAdjust = 0;
        staticOutput = null;
        channelTableUpdated = true;
        channels = new TreeMap<Short, OutputChannel>();
    }

    public void newChannel(OutputChannel chan)
    {
        channelTableUpdated = true;
        short firstUnalloc = 0;
        for(Map.Entry<Short, OutputChannel> e : channels.entrySet())
            if(e.getKey().shortValue() == firstUnalloc)
                firstUnalloc++;
        channels.put(new Short(firstUnalloc), chan);
        chan.setChan(firstUnalloc);
    }

    public void addFrame(OutputChannel chan, OutputFrame frame, boolean sync)
    {
        frame.adjustTime(timeAdjust);
        short ch = chan.getChan();
        if(staticOutput != null) {
            if(channelTableUpdated) {
                staticOutput.updateChannelTable(channels);
                channelTableUpdated = false;
            }
            staticOutput.addFrame(ch, frame, sync);
        }
    }

    public void setStaticOutput(OutputStatic staticOut, long newAdjust)
    {
        timeAdjust = newAdjust;
        staticOutput = staticOut;
        channelTableUpdated = true;
    }

    public void dumpSRPartial(SRDumper output) throws IOException
    {
        for(Map.Entry<Short, OutputChannel> e : channels.entrySet()) {
            output.dumpBoolean(true);
            output.dumpObject(e.getValue());
        }
        output.dumpBoolean(false);
    }

    public void dumpStatusPartial(StatusDumper output)
    {
        for(Map.Entry<Short, OutputChannel> e : channels.entrySet()) {
            output.println("\tchannels[" + e.getKey() + "] <object #" + output.objectNumber(e.getValue()) + ">"); if(e.getValue() != null) e.getValue().dumpStatus(output);
        }
    }

    public Output(SRLoader input) throws IOException
    {
        this();
        input.objectCreated(this);
        while(input.loadBoolean()) {
            OutputChannel ch = (OutputChannel)input.loadObject();
            channels.put(new Short(ch.getChan()), ch);
        }
        channelTableUpdated = true;
    }

    public void dumpStatus(StatusDumper output)
    {
        if(output.dumped(this))
            return;

        output.println("#" + output.objectNumber(this) + ": Output:");
        dumpStatusPartial(output);
        output.endObject();
    }
};
