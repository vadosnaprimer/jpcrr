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

package org.jpc.support;

import java.io.*;
import java.nio.*;
import java.nio.charset.*;

public class UTFOutputLineStream
{
    private OutputStream underlying;

    public UTFOutputLineStream(OutputStream out)
    {
        underlying = out;
    }

    public void writeLine(String line) throws IOException
    {
        ByteBuffer buf;
        try {
            buf = Charset.forName("UTF-8").newEncoder().encode(CharBuffer.wrap(line));
        } catch(CharacterCodingException e) {
            throw new IOException("WTF??? UTF-8 can't encode String???");
        }
        byte[] buf2 = new byte[buf.remaining()];
        buf.get(buf2);
        underlying.write(buf2);
        underlying.write(10);

    }

    public void flush() throws IOException
    {
        underlying.flush();
    }

    public void close() throws IOException
    {
        flush();
        underlying.close();
    }
}
