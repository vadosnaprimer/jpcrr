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

package org.jpc.jrsr;

import java.io.*;
import java.util.*;
import java.nio.*;
import java.nio.charset.*;
import static org.jpc.Misc.isspace;
import static org.jpc.Misc.isLinefeed;

public class JRSRArchiveReader implements Closeable
{
    private RandomAccessFile underlying;
    private Map<String, Long> memberStart;
    private Map<String, Long> memberEnd;
    private String currentMember;
    private boolean closed;

    private byte[] rBuffer;
    private int[] buffer;
    private int bufferStart;
    private int bufferFill;
    private long firstBufferParsePosition;
    private UTF8StreamDecoder decoder;
    private boolean eofSeen;

    private static final int BUFFER_LOW_WATER_MARK = 2048;
    private static final int BUFFER_READ_BLOCK = 40960;

    public class JRSRArchiveInputStream extends InputStream
    {
        private boolean closed2;
        private long seekingPoint;
        private long endMarker;
        private byte[] buf;

        JRSRArchiveInputStream(long startPoint, long endPoint)
        {
            seekingPoint = startPoint;
            endMarker = endPoint;
            buf = new byte[1];
        }

        public int read() throws IOException
        {
            if(closed || closed2)
                throw new IOException("Trying to read closed stream");
            if(seekingPoint == endMarker)
                return -1;
            read(buf, 0, 1);
            return (int)buf[0] & 0xFF;
        }

        public int read(byte[] buffer, int off, int len) throws IOException
        {
            if(closed || closed2)
                throw new IOException("Trying to read closed stream");
            if(seekingPoint == endMarker)
                return -1;
            if(len > (int)(endMarker - seekingPoint))
                len = (int)(endMarker - seekingPoint);
            int aRead = len;
            synchronized(JRSRArchiveReader.this) {
                underlying.seek(seekingPoint);
                while(len > 0) {
                    int r = underlying.read(buffer, off, len);
                    if(r < 0)
                        throw new IOException("JRSR file unexpectedly truncated or dictionary data corrupt");
                    seekingPoint += r;
                    off += r;
                    len -= r;
                }
            }
            return aRead;
        }

        public long skip(long len) throws IOException
        {
            if(closed || closed2)
                throw new IOException("Trying to read closed stream");
            len = Math.min(len, endMarker - seekingPoint);
            seekingPoint += len;
            return len;
        }

        public int available()
        {
            return (int)Math.min(endMarker - seekingPoint, 0x7FFFFFFF);
        }

        public void close()
        {
            closed2 = true;
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

    private void syncBuffer()
    {
        firstBufferParsePosition += decoder.relativeOffset(buffer, bufferStart);
        if(bufferFill > 0)
            System.arraycopy(buffer, bufferStart, buffer, 0, bufferFill);
        bufferStart = 0;
    }

    private void fillBuffer() throws IOException
    {
        syncBuffer();
        while(!eofSeen && bufferFill < BUFFER_LOW_WATER_MARK) {
            int r = underlying.read(rBuffer);
            if(r < 0) {
                eofSeen = true;
                decoder.sendEOF();
                break;
            };
            int[] buf = decoder.decode(rBuffer, 0, r);
            System.arraycopy(buf, 0, buffer, bufferFill, buf.length);
            bufferFill += buf.length;
        }
    }

    private void initBuffers(RandomAccessFile in) throws IOException
    {
        rBuffer = new byte[BUFFER_READ_BLOCK];
        buffer = new int[BUFFER_READ_BLOCK + BUFFER_LOW_WATER_MARK];
        underlying = in;
        fillBuffer();
        if(bufferFill < 5 || buffer[0] != (int)'J' || buffer[1] != (int)'R' || buffer[2] != (int)'S' ||
            buffer[3] != (int)'R' || !isLinefeed(buffer[4]))
            throw new IOException("Invalid JRSR magic (not a JRSR file)");
        bufferStart += 5;
        bufferFill -= 5;
    }

    private void skipRestOfLine() throws IOException
    {
        boolean seenEOLs = false;
        while(true) {
            if(bufferFill == 0)
                fillBuffer();
            if(bufferFill == 0)
                break;  //EOF.
            boolean isEOL = isLinefeed(buffer[bufferStart]);
            if(isEOL && !seenEOLs)
                seenEOLs = true;
            if(!isEOL && seenEOLs)
                break;
            bufferStart++;
            bufferFill--;
        }
    }

    private void skipMember() throws IOException
    {
        while(true) {
            if(bufferFill == 0)
                fillBuffer();
            if(bufferFill == 0)
                break;  //EOF.
            if(buffer[bufferStart] == (int)'!')
                break;  //Control command.
            if(buffer[bufferStart] != '+')
                throw new IOException("Invalid JRSR file, expected <43> after EOL, got <" + buffer[bufferStart] +
                    ">.");
            skipRestOfLine();
        }
    }

    private String readToEOL() throws IOException
    {
        StringBuilder buf = new StringBuilder();
        boolean seenEOLs = false;
        while(true) {
            if(bufferFill == 0)
                fillBuffer();
            if(bufferFill == 0)
                break;  //EOF.
            boolean isEOL = isLinefeed(buffer[bufferStart]);
            if(isEOL && !seenEOLs)
                seenEOLs = true;
            if(!isEOL && seenEOLs)
                break;
            if(!isEOL) {
                int j = buffer[bufferStart];
                if(j <= 0xFFFF)
                    buf.append((char)j);
                else {
                    buf.append((char)(0xD800 + j & 0x3FF));
                    buf.append((char)(0xDBC0 + j >> 10));
                }
            }
            bufferStart++;
            bufferFill--;
        }
        return buf.toString();
    }

    private void readCommandLine() throws IOException
    {
        fillBuffer();  //This also syncs the buffer.
        long endAddress = firstBufferParsePosition;
        if(bufferFill > 3 && buffer[0] == (int)'!' && buffer[1] == (int)'E' && buffer[2] == (int)'N' &&
            buffer[3] == (int)'D' && (bufferFill == 4 || isLinefeed(buffer[4]))) {
            //!END
            endMember(endAddress);
            skipRestOfLine();
        } else if(bufferFill > 7 && buffer[0] == (int)'!' && buffer[1] == (int)'B' && buffer[2] == (int)'E' &&
            buffer[3] == (int)'G' && buffer[4] == (int)'I' && buffer[5] == (int)'N' && isspace(buffer[6])) {
            //!BEGIN
            bufferStart += 7;
            bufferFill -= 7;
            String name = readToEOL();
            if("".equals(name))
                throw new IOException("Empty member name not allowed in JRSR file");
            syncBuffer();
            long startAddress = firstBufferParsePosition;
            startMember(name, endAddress, startAddress);
        } else
            throw new IOException("Invalid command \"" + readToEOL() + "\".");
    }

    public JRSRArchiveReader(String file) throws IOException
    {
        RandomAccessFile in;
        memberStart = new HashMap<String, Long>();
        memberEnd = new HashMap<String, Long>();
        try {
            in = new RandomAccessFile(file, "r");
        } catch(IOException e) {
            throw new IOException("Can not open JRSR archive \"" + file + "\"");
        }
        decoder = new UTF8StreamDecoder();
        initBuffers(in);
        while(bufferFill > 0 || !eofSeen) {
            readCommandLine();
            if(currentMember != null)
                skipMember();
        }
        if(currentMember != null)
            throw new IOException("JRSR file ends in the middle of a member");
    }

    public void close() throws IOException
    {
        memberStart = null;
        memberEnd = null;
        underlying.close();
        closed = true;
    }

    public UnicodeInputStream readMember(String name) throws IOException
    {
        if(closed)
            throw new IOException("Trying to operate on closed stream");
        Long start = memberStart.get(name);
        Long end = memberEnd.get(name);
        if(start == null || end == null)
            throw new IOException("No such member \"" + name + "\" in JRSR archive.");
        return new UTF8InputStream(new JRSRArchiveInputStream(start.longValue(), end.longValue()), true);
    }

    public Set<String> getMembers() throws IOException
    {
        Set<String> ret = new HashSet<String>();
        if(closed)
            throw new IOException("Trying to operate on closed stream");
        for(Map.Entry<String, Long> member : memberStart.entrySet())
            ret.add(member.getKey());
        return ret;
    }
}
