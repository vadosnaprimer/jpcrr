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

package org.jpc.jrsr;

import java.io.*;
import java.nio.*;
import java.nio.charset.*;
import org.jpc.Misc;

public class UTFOutputLineStream implements Closeable
{
    private OutputStream underlying;
    private boolean closed;

    public UTFOutputLineStream(OutputStream out)
    {
        underlying = out;
    }

    public void writeLine(String line) throws IOException
    {
        if(closed)
            throw new IOException("Trying to operate on closed stream");
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

    public void encodeLine(Object... line) throws IOException
    {
        if(closed)
            throw new IOException("Trying to operate on closed stream");
        String[] line2 = new String[line.length];
        for(int i = 0; i < line.length; i++)
            if(line[i] != null)
                line2[i] = line[i].toString();
            else
                return;
        this.writeLine(Misc.encodeLine(line2));
    }

    public void flush() throws IOException
    {
        if(closed)
            throw new IOException("Trying to operate on closed stream");
        underlying.flush();
    }

    public void close() throws IOException
    {
        if(closed)
            return;
        flush();
        underlying.close();
        closed = true;
    }
}
