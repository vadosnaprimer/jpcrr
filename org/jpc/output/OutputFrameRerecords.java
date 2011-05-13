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
import java.math.BigInteger;

public class OutputFrameRerecords extends OutputFrame
{
    private BigInteger value;

    public OutputFrameRerecords(long timeStamp, BigInteger _value)
    {
        super(timeStamp, (byte)82);
        value = _value;
    }

    protected byte[] dumpInternal()
    {
        int bits = value.bitLength();
        int bytes = 8;
        if(bits > 64)
            bytes = (bits + 7) / 8;
        byte[] _name = new byte[bytes];
        for(int j = 0; j < bytes; j++)
            _name[j] = (byte)(value.shiftRight(8 * bytes - 8 - 8 * j).intValue());
        return _name;
    }
};
