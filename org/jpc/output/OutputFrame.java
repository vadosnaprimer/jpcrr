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

public abstract class OutputFrame
{
    private long time;
    private byte minor;

    public OutputFrame(long timeStamp, byte minorType)
    {
        time = timeStamp;
        minor = minorType;
    }

    protected void adjustTime(long delta)
    {
        time += delta;
    }

    public byte[] dump(short channel, long timeBase) throws IOException
    {
        int len = 8;
        int llen = 1;
        int skips = 0;
        if(channel == -1)
            throw new IOException("Channel 0xFFFF is reserved");
        long delta = time - timeBase;
        if(delta < 0)
            throw new IOException("Event times must be monotonic");

        byte[] internal = dumpInternal();

        //Calculate length of frame and allocate it.
        while(delta >= 0xFFFFFFFFL) {
            skips++;
            len += 6;
            delta -= 0xFFFFFFFFL;
        }
        int ilen = (internal != null) ? internal.length : 0;
        while(ilen > 127) {
            len++;
            llen++;
            ilen >>>= 7;
        }
        len += ((internal != null) ? internal.length : 0);
        byte[] frame = new byte[len];

        //Write the frame.
        int offset = 6 * skips;
        for(int i = 0; i < offset; i++)
            frame[i] = (byte)255;
        frame[offset + 0] = (byte)((channel >>> 8) & 0xFF);
        frame[offset + 1] = (byte)(channel & 0xFF);
        frame[offset + 2] = (byte)((delta >>> 24) & 0xFF);
        frame[offset + 3] = (byte)((delta >>> 16) & 0xFF);
        frame[offset + 4] = (byte)((delta >>> 8) & 0xFF);
        frame[offset + 5] = (byte)(delta & 0xFF);
        frame[offset + 6] = minor;
        ilen = (internal != null) ? internal.length : 0;
        for(int i = llen; i > 0; i--) {
            frame[offset + 6 + i] = (byte)(((i == llen) ? 0x00 : 0x80) | (ilen & 0x7F));
            ilen >>>= 7;
	}
        if(internal != null)
            System.arraycopy(internal, 0, frame, offset + 7 + llen, internal.length);
        return frame;
    }

    public long getTime()
    {
        return time;
    }

    protected abstract byte[] dumpInternal();
};
