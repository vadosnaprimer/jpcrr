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

public class OutputFrameOneBitMono extends OutputFrame
{
    private boolean state;

    public OutputFrameOneBitMono(long timeStamp, boolean _state)
    {
        super(timeStamp, (byte)1);
        state = _state;
    }

    protected byte[] dumpInternal()
    {
        byte[] buf = new byte[4];
        if(state) {
            buf[0] = buf[2] = 127;
            buf[1] = buf[3] = -1;
        } else {
            buf[0] = buf[2] = -128;
            buf[1] = buf[3] = 0;
        }
        return buf;
    }
};
