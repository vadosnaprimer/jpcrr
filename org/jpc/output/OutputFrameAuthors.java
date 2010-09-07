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
import java.nio.*;
import java.nio.charset.*;

public class OutputFrameAuthors extends OutputFrame
{
    private String name;

    public OutputFrameAuthors(long timeStamp, String _name)
    {
        super(timeStamp, (byte)65);
        name = _name;
    }

    protected byte[] dumpInternal()
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
        return _name;
    }
};
