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
import org.jpc.emulator.StatusDumper;
import org.jpc.emulator.SRDumpable;
import org.jpc.emulator.SRDumper;
import org.jpc.emulator.SRLoader;
import java.nio.*;
import java.nio.charset.*;

public class OutputChannel implements SRDumpable
{
    private short type;
    private String name;
    private short chan;
    private Output out;

    public OutputChannel(Output _out, short chanType, String chanName)
    {
        type = chanType;
        name = chanName;
        chan = -1;
        out = _out;
        out.newChannel(this);
    }

    protected void setChan(short _chan)
    {
        chan = _chan;
    }

    protected short getChan()
    {
        return chan;
    }

    protected String getName()
    {
        return name;
    }

    protected short getType()
    {
        return type;
    }

    public void addFrame(OutputFrame newFrame, boolean sync)
    {
        out.addFrame(this, newFrame, sync);
    }

    public byte[] channelHeader()
    {
        ByteBuffer _xname = null;
        byte[] _name = null;
        try {
            _xname = Charset.forName("UTF-8").newEncoder().encode(CharBuffer.wrap(name));
            _name = new byte[_xname.remaining()];
            _xname.get(_name);
        } catch(Exception e) {
            _name = new byte[]{0x33};	//WTF?
        }
        int len = 6 + _name.length;
        byte[] buf = new byte[len];
        buf[0] = (byte)((chan >> 8) & 0xFF);
        buf[1] = (byte)(chan & 0xFF);
        buf[2] = (byte)((type >> 8) & 0xFF);
        buf[3] = (byte)(type & 0xFF);
        buf[4] = (byte)((_name.length >> 8) & 0xFF);
        buf[5] = (byte)(_name.length & 0xFF);
        System.arraycopy(_name, 0, buf, 6, _name.length);
        return buf;
    }

    public void dumpSRPartial(SRDumper output) throws IOException
    {
        output.dumpShort(type);
        output.dumpShort(chan);
        output.dumpString(name);
        output.dumpObject(out);
    }

    public void dumpStatusPartial(StatusDumper output)
    {
        output.println("\ttype " + type + " chan " + chan);
        output.println("\tname " + name);
        output.println("\tout <object #" + output.objectNumber(out) + ">"); if(out != null) out.dumpStatus(output);
    }

    public OutputChannel(SRLoader input) throws IOException
    {
        input.objectCreated(this);
        type = input.loadShort();
        chan = input.loadShort();
        name = input.loadString();
        out = (Output)input.loadObject();
    }

    public void dumpStatus(StatusDumper output)
    {
        if(output.dumped(this))
            return;

        output.println("#" + output.objectNumber(this) + ": OutputChannel:");
        dumpStatusPartial(output);
        output.endObject();
    }
};
