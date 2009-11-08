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
import java.util.*;
import java.nio.*;
import java.nio.charset.*;

public class JRSRArchiveReader
{
    private RandomAccessFile underlying;
    private Map<String, Long> memberStart;
    private Map<String, Long> memberEnd;
    private String currentMember;

    public class JRSRArchiveInputStream extends InputStream
    {
        private boolean atLineStart;
        private long seekingPoint;
        private long endMarker;
        private byte[] buffer;
        private int bufferStart;
        private int bufferFill;

        JRSRArchiveInputStream(long startPoint, long endPoint)
        {
            atLineStart = true;
            seekingPoint = startPoint;
            endMarker = endPoint;
            buffer = new byte[2048];
            bufferStart = 0;
            bufferFill = 0;
        }

        private void fillBuffer() throws IOException
        {
            synchronized(JRSRArchiveReader.this) {
                if(endMarker == seekingPoint || bufferFill > 0)
                    return;
                underlying.seek(seekingPoint);
                if(endMarker - seekingPoint < buffer.length) {
                    //System.err.println("Reading " + (endMarker - seekingPoint) + " bytes starting from " +
                    //    seekingPoint + ".");
                    underlying.readFully(buffer, 0, (int)(endMarker - seekingPoint));
                    bufferStart = 0;
                    bufferFill = (int)(endMarker - seekingPoint);
                    seekingPoint = endMarker;
                } else {
                    //System.err.println("Reading " + buffer.length + " bytes starting from " +
                    //    seekingPoint + ".");
                    underlying.readFully(buffer);
                    bufferStart = 0;
                    bufferFill = buffer.length;
                    seekingPoint += buffer.length;
                }
            }
        }

        private int min3(int a, int b, int c)
        {
            if(a <= b && a <= c)
                return a;
            if(b <= a && b <= c)
                return b;
            return c;
        }

        public long skip(long n) throws IOException
        {
            long processed = 0;
            while(n > 0) {
                if(bufferFill == 0)
                    if(seekingPoint == endMarker)
                        return processed;
                    else
                        fillBuffer();
                if(atLineStart) {
                    if(buffer[bufferStart] != (byte)43)
                        throw new IOException("Unexpected character while expecting + at start of line");
                    bufferStart++;
                    bufferFill--;
                    atLineStart = false;
                    continue;
                }
                //Find next LF.
                int lfOff = 0;
                while(lfOff < bufferFill && buffer[bufferStart + lfOff] != (byte)10)
                    lfOff++;

                int copy = min3(bufferFill, lfOff + 1, (n < 10000000) ? (int)n : 10000000);
                n -= copy;
                processed += copy;
                bufferStart += copy;
                bufferFill -= copy;
                if(copy == lfOff + 1)
                    atLineStart = true;
            }
            return processed;
        }

        public int read(byte[] b, int off, int len) throws IOException
        {
            int processed = 0;
            while(len > 0) {
                if(bufferFill == 0)
                    if(seekingPoint == endMarker)
                        if(processed > 0)
                            return processed;
                        else
                            return -1;
                    else
                        fillBuffer();

                if(atLineStart) {
                    if(buffer[bufferStart] != (byte)43)
                        throw new IOException("Unexpected character while expecting + at start of line");
                    bufferStart++;
                    bufferFill--;
                    atLineStart = false;
                    continue;
                }
                //Find next LF.
                int lfOff = 0;
                while(lfOff < bufferFill && buffer[bufferStart + lfOff] != (byte)10)
                    lfOff++;

                int copy = min3(bufferFill, lfOff + 1, len);
                System.arraycopy(buffer, bufferStart, b, off, copy);
                len -= copy;
                off += copy;
                processed += copy;
                bufferStart += copy;
                bufferFill -= copy;
                if(copy == lfOff + 1)
                    atLineStart = true;
            }
            return processed;
        }

        public int available()
        {
            return 1000; /* Just return something. */
        }

        public void close()
        {
        }

        public int read() throws IOException
        {
            byte[] x = new byte[1];
            int r;
            r = read(x, 0, 1);
            if(r < 0)
                return -1;
            return x[0];
        }

        public int read(byte[] b) throws IOException
        {
            return read(b, 0, b.length);
        }

        public void mark(int limit)
        {
        }

        public boolean markSupported()
        {
            return false;
        }

        public void reset() throws IOException
        {
            throw new IOException("JRSRArchiveInputStream does not support mark()");
        }
    }

    private void startMember(String name, long endingPosition, long startingPosition) throws IOException
    {
        if(currentMember != null) {
            //System.err.println("Marking end of \"" + currentMember + "\" at position " + endingPosition + ".");
            memberEnd.put(currentMember, new Long(endingPosition));
        }
        //System.err.println("Marking start of \"" + name + "\" at position " + startingPosition + ".");
        if(memberStart.get(name) != null)
            throw new IOException("Invalid JRSR archive: Member \"" + name + "\" present multiple times");
        memberStart.put(name, new Long(startingPosition));
        currentMember = name;
    }

    private void endMember(long position)
    {
        Long pos = new Long(position);
        if(currentMember != null) {
            //System.err.println("Marking end of \"" + currentMember + "\" at position " + position + ".");
            memberEnd.put(currentMember, pos);
        }
        currentMember = null;
    }

    private void parseMembers(long position) throws IOException
    {
        byte[] buffer = new byte[2048];
        int bufferFill = 0;
        int bufferStart = 0;
        long bufferBase = position;
        boolean eofFlag = false;
        boolean dReq = true;
        boolean inMember = false;
        boolean atLineStart = false;

        while(!eofFlag || bufferFill > 0) {
            if(bufferStart > 0 && bufferFill > 0) {
                //System.err.println("Copying buffer range (" + bufferStart + "," + bufferFill + ") -> (0," +
                //    bufferFill + ").");
                System.arraycopy(buffer, bufferStart, buffer, 0, bufferFill);
            }
            bufferStart = 0;
            int filled = 0;
            if(dReq || bufferFill == 0) {
                if(!eofFlag) {
                    filled = underlying.read(buffer, bufferFill, buffer.length - bufferFill);
                }
                if(filled < 0) {
                    //System.err.println("Got End Of File.");
                    eofFlag = true;
                } else {
                    //System.err.println("Buffer fill: " + bufferFill + "+" + filled  + "=" +
                    //    (bufferFill + filled) + ".");
                    bufferFill += filled;
                }
                dReq = false;
            }
            if(eofFlag && bufferFill == 0)
                continue;
            /* Now try with the additional data. */
            if(atLineStart && inMember) {
                //System.err.println("At line start inside member.");
                /* Shift the '+'. Process the '!' */
                if(buffer[bufferStart] == (byte)43) {
                    //System.err.println("Shifting the +.");
                    bufferBase++;
                    bufferStart++;
                    bufferFill--;
                    atLineStart = false;
                } else if(buffer[bufferStart] == (byte)33) {
                    /* Line starting with '!' */
                    //System.err.println("Noted the !.");
                    long position1 = bufferBase;
                    int lfOff = 0;
                    while(lfOff < bufferFill && buffer[bufferStart + lfOff] != (byte)10)
                        lfOff++;
                    //System.err.println("lfOff=" + lfOff + ", bufferFill=" + bufferFill + ".");
                    if(lfOff == bufferFill) {
                        /* No LF found. Assert data Request. if buffer not filled. */
                        if(eofFlag)
                            throw new IOException("Unexpected end of file");
                        else if(bufferFill == buffer.length)
                             throw new IOException("! line way too long");
                        //System.err.println("No LF. Asserting data request.");
                        dReq = true;
                        continue;
                    }
                    long position2 = bufferBase + lfOff + 1;
                    //System.err.println("Line start: " + position1 + ", line end: " + position2 + ".");
                    if(buffer[bufferStart + 1] == (byte)66 && buffer[bufferStart + 2] == (byte)69 &&
                           buffer[bufferStart + 3] == (byte)71 && buffer[bufferStart + 4] == (byte)73 &&
                           buffer[bufferStart + 5] == (byte)78 && buffer[bufferStart + 6] == (byte)32) {
                        /* !BEGIN. BufferStart + 7..lfOff is the range of name. */
                        //System.err.println("Directive !BEGIN recognized.");
                        if(lfOff - (bufferStart + 7) > 1024)
                            throw new IOException("Member name too long");
                        //System.err.println("Computed name length: " + (lfOff - (bufferStart + 7)) + ".");
                        String memberName;
                        memberName = Charset.forName("UTF-8").newDecoder().decode(ByteBuffer.wrap(buffer,
                            bufferStart + 7, lfOff - (bufferStart + 7))).toString();
                        //System.err.println("Decoded name: \"" + memberName + "\".");
                        startMember(memberName, position1, position2);
                        inMember = true;
                        atLineStart = true;
                    } else if(buffer[bufferStart + 1] == (byte)69 && buffer[bufferStart + 2] == (byte)78 &&
                            buffer[bufferStart + 3] == (byte)68 && buffer[bufferStart + 4] == (byte)10) {
                        /* !END. */
                        //System.err.println("Directive !END recognized.");
                        endMember(position1);
                        inMember = false;
                        atLineStart = true;
                    } else {
                         throw new IOException("Unknown ! directive");
                    }
                    //System.err.println("Eating " + (lfOff + 1) + " bytes.");
                    bufferBase += (lfOff + 1);
                    bufferStart += (lfOff + 1);
                    bufferFill -= (lfOff + 1);
                } else
                    throw new IOException("Unexpected character while expecting + or ! at start of line");
                continue;
            } else if(!inMember) {
                /* Expecting line starting with '!' */
                //System.err.println("At line start not inside member.");
                long position1 = bufferBase;
                if(buffer[bufferStart] != (byte)33)
                    throw new IOException("Unexpected character while expecting ! at start of line");
                int lfOff = 0;
                while(lfOff < bufferFill && buffer[bufferStart + lfOff] != (byte)10)
                    lfOff++;
                //System.err.println("lfOff=" + lfOff + ", bufferFill=" + bufferFill + ".");
                if(lfOff == bufferFill) {
                    /* No LF found. Assert data Request. if buffer not filled. */
                    if(eofFlag)
                        throw new IOException("Unexpected end of file");
                    else if(bufferFill == buffer.length)
                        throw new IOException("! line way too long");
                    //System.err.println("No LF. Asserting data request.");
                    dReq = true;
                    continue;
                }
                long position2 = bufferBase + lfOff + 1;
                //System.err.println("Line start: " + position1 + ", line end: " + position2 + ".");
                if(buffer[bufferStart + 1] == (byte)66 && buffer[bufferStart + 2] == (byte)69 &&
                        buffer[bufferStart + 3] == (byte)71 && buffer[bufferStart + 4] == (byte)73 &&
                        buffer[bufferStart + 5] == (byte)78 && buffer[bufferStart + 6] == (byte)32) {
                    /* !BEGIN. BufferStart + 7..lfOff is the range of name. */
                    //System.err.println("Directive !BEGIN recognized.");
                    if(lfOff - (bufferStart + 7) > 1024)
                        throw new IOException("Member name too long");
                    //System.err.println("Computed name length: " + (lfOff - (bufferStart + 7)) + ".");
                    String memberName;
                    memberName = Charset.forName("UTF-8").newDecoder().decode(ByteBuffer.wrap(buffer,
                        bufferStart + 7, lfOff - (bufferStart + 7))).toString();
                    //System.err.println("Decoded name: \"" + memberName + "\".");
                    startMember(memberName, position1, position2);
                    inMember = true;
                    atLineStart = true;
                } else {
                    throw new IOException("Unknown ! directive.");
                }
                //System.err.println("Eating " + (lfOff + 1) + " bytes.");
                bufferBase += (lfOff + 1);
                bufferStart += (lfOff + 1);
                bufferFill -= (lfOff + 1);
            } else {
                /* Data line. Skip to next LF. */
                //System.err.println("At line continuation inside member.");
                int lfOff = 0;
                while(lfOff < bufferFill && buffer[bufferStart + lfOff] != (byte)10)
                    lfOff++;
                //System.err.println("lfOff=" + lfOff + ", bufferFill=" + bufferFill + ".");
                if(lfOff == bufferFill) {
                    /* No LF. Just empty the buffer then. */
                    //System.err.println("Eating " + bufferFill + " bytes.");
                    bufferBase += bufferFill;
                    bufferFill = 0;
                } else {
                    /* Skip to next LF. */
                    //System.err.println("Eating " + (lfOff + 1) + " bytes.");
                    bufferBase += (lfOff + 1);
                    bufferStart += (lfOff + 1);
                    bufferFill -= (lfOff + 1);
                    atLineStart = true;
                }
            }
        }
        if(inMember)
            throw new IOException("Unexpected end of JRSR archive");
    }

    public JRSRArchiveReader(String file) throws IOException
    {
        memberStart = new HashMap<String, Long>();
        memberEnd = new HashMap<String, Long>();
        try {
            underlying = new RandomAccessFile(file, "r");
        } catch(IOException e) {
            throw new IOException("Can not open JRSR archive \"" + file + "\"");
        }
        currentMember = null;
        byte[] header = new byte[5];
        try {
            underlying.readFully(header);
            if(header[0] != (byte)74 || header[1] != (byte)82 || header[2] != (byte)83 ||
                    header[3] != (byte)82 || header[4] != (byte)10)
                throw new IOException("Bad magic");
        } catch(IOException e) {
            throw new IOException("Bad JRSR archive magic in \"" + file + "\"");
        }
        parseMembers(5);
    }

    public void close() throws IOException
    {
        memberStart = null;
        memberEnd = null;
        underlying.close();
    }

    public JRSRArchiveInputStream readMember(String name) throws IOException
    {
        Long start = memberStart.get(name);
        Long end = memberEnd.get(name);
        if(start == null || end == null)
            throw new IOException("No such member \"" + name + "\" in JRSR archive.");
        return new JRSRArchiveInputStream(start.longValue(), end.longValue());
    }
}
