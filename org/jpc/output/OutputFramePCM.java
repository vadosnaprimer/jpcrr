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

public class OutputFramePCM extends OutputFrame
{
    private short l;
    private short r;

    public OutputFramePCM(long timeStamp, short lv, short rv)
    {
        super(timeStamp, (byte)1);
        l = lv;
        r = rv;
    }

    protected byte[] dumpInternal()
    {
        byte[] buf = new byte[4];
        buf[0] = (byte)((l >> 8) & 0xFF);
        buf[1] = (byte)(l & 0xFF);
        buf[2] = (byte)((r >> 8) & 0xFF);
        buf[3] = (byte)(r & 0xFF);
        return buf;
    }
};
