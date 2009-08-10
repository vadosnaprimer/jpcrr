/*
    JPC: A x86 PC Hardware Emulator for a pure Java Virtual Machine
    Release Version 2.0

    A project from the Physics Dept, The University of Oxford

    Copyright (C) 2007 Isis Innovation Limited
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

    Details (including contact information) can be found at:

    www.physics.ox.ac.uk/jpc
*/

package org.jpc.jrsr;

import java.io.*;
import java.nio.*;
import java.nio.charset.*;
import static org.jpc.Misc.tempname;

public class JRSRArchiveWriter
{
    private boolean active;
    private OutputStream underlying;
    private String finalName;
    private File temporary;

    public class JRSRArchiveOutputStream extends OutputStream
    {
        public boolean atLineStart;

        JRSRArchiveOutputStream()
        {
            atLineStart = true;
        }

        public void close() throws IOException
        {
            flush();
            active = false;
            byte[] postfix = new byte[]{10};
            if(!atLineStart)
                underlying.write(postfix);
            underlying.flush();
        }

        public void flush() throws IOException
        {
            underlying.flush();
        }

        private int min3(int a, int b, int c)
        {
            if(a <= b && a <= c)
                return a;
            if(b <= a && b <= c)
                return b;
            return c;
        }

        public void write(byte[] b, int off, int len) throws IOException
        {
            byte[] outputBuffer = new byte[2048];
            int outputFill = 0;
            while(len > 0) {
                /* Forward-search for linefeed. */
                int lfPos = 0;
                while(lfPos < len && b[off + lfPos] != (byte)10)
                    lfPos++;
                if(atLineStart) {
                    outputBuffer[outputFill++] = (byte)43;
                    atLineStart = false;
                }

                /* Copy until one of the following occurs: 
                   1) OutputBuffer fills up.
                   2) To (and including) next LF.
                   3) input buffer runs out.
 
                   In condition 2, use impossibly large value if no LF.
                */
                int condition1Length = outputBuffer.length - outputFill;
                int condition2Length = (lfPos == len) ? (outputBuffer.length + 1) : lfPos + 1;
                int minLength = min3(condition1Length, condition2Length, len);

                System.arraycopy(b, off, outputBuffer, outputFill, minLength);
                off += minLength;
                len -= minLength;
                outputFill += minLength;

                // If we hit output buffer end, flush it.
                if(condition1Length == minLength) {
                    underlying.write(outputBuffer);
                    outputFill = 0;
                }

                // If we hit LF, mark that we are at line start.
                if(condition2Length == minLength) {
                    atLineStart = true;
                }
            }
            if(outputFill > 0)
                underlying.write(outputBuffer, 0, outputFill);
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
        underlying.close();
        temporary.delete();
    }

    public void close() throws IOException
    {
        if(active)
            throw new IOException("Trying close JRSR Archive without closing member");
        byte[] prefix = new byte[]{33, 69, 78, 68, 10};
        underlying.write(prefix);
        underlying.flush();
        underlying.close();
        temporary.renameTo(new File(finalName));
    }

    public JRSRArchiveOutputStream addMember(String name) throws IOException
    {
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
        if(buf2.length > 1024)
            throw new IOException("JRSR member maximum name length of 1024 bytes exceeded");
        underlying.write(prefix);
        underlying.write(buf2);
        underlying.write(postfix);
        active = true;
        return new JRSRArchiveOutputStream();
    }
}
