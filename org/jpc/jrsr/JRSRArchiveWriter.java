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
import static org.jpc.Misc.tempname;
import static org.jpc.Misc.renameFile;

public class JRSRArchiveWriter implements Closeable
{
    private boolean active;
    private OutputStream underlying;
    private String finalName;
    private File temporary;
    private boolean closed;

    public class JRSRArchiveOutputStream extends OutputStream
    {
        private boolean closed2;

        JRSRArchiveOutputStream()
        {
        }

        public void close() throws IOException
        {
            if(closed || closed2)
                return;
            flush();
            active = false;
            underlying.write(new byte[]{10});
            underlying.flush();
            closed2 = true;
        }

        public void flush() throws IOException
        {
            if(closed || closed2)
                throw new IOException("Trying to operate on closed stream");
            underlying.flush();
        }

        public void write(byte[] b, int off, int len) throws IOException
        {
            if(closed || closed2)
                throw new IOException("Trying to operate on closed stream");
            underlying.write(b, off, len);
        }

        public void write(byte[] b) throws IOException
        {
            write(b, 0, b.length);
        }

        public void write(int b) throws IOException
        {
            byte[] x = new byte[]{(byte)b};
            write(x, 0, 1);
        }
    }

    public JRSRArchiveWriter(String file) throws IOException
    {
        active = false;
        String temporaryName = tempname(file);
        temporary = new File(temporaryName);
        finalName = file;
        underlying = new FileOutputStream(temporary);
        byte[] prefix = new byte[]{74, 82, 83, 82, 10};
        underlying.write(prefix);
    }

    public void rollback() throws IOException
    {
        if(closed)
            return;
        underlying.close();
        temporary.delete();
        closed = true;
    }

    public void close() throws IOException
    {
        if(closed)
            return;
        if(active)
            throw new IOException("Trying close JRSR Archive without closing member");
        byte[] prefix = new byte[]{33, 69, 78, 68, 10};
        underlying.write(prefix);
        underlying.flush();
        underlying.close();
        renameFile(temporary, new File(finalName));
        closed = true;
    }

    public UnicodeOutputStream addMember(String name) throws IOException
    {
        if(closed)
            throw new IOException("Trying to operate on closed stream");
        if(active)
            throw new IOException("Trying to add new member to JRSR Archive without closing previous");
        byte[] prefix = new byte[]{33, 66, 69, 71, 73, 78, 32};
        byte[] postfix = new byte[]{10};

        ByteBuffer buf;
        try {
            buf = Charset.forName("UTF-8").newEncoder().encode(CharBuffer.wrap(name));
        } catch(CharacterCodingException e) {
            throw new IOException("WTF??? UTF-8 can't encode String???");
        }
        byte[] buf2 = new byte[buf.remaining()];
        buf.get(buf2);
        underlying.write(prefix);
        underlying.write(buf2);
        underlying.write(postfix);
        active = true;
        return new UTF8OutputStream(new JRSRArchiveOutputStream(), true);
    }
}
