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

public class JRSRArchiveReader implements Closeable
{
    private RandomAccessFile underlying;
    private Map<String, Long> memberStart;
    private Map<String, Long> memberEnd;
    private String currentMember;
    private boolean closed;

    private byte[] buffer;
    private int bufferFill;
    private int bufferStart;
    private long bufferBase;
    private boolean eofFlag;
    private int parseState;
    private boolean dreq;

    private static final int STATE_IN_LINE = 0;
    private static final int STATE_LAST_194 = 1;
    private static final int STATE_LAST_226 = 2;
    private static final int STATE_LAST_226_128 = 3;
    private static final int STATE_LINE_START = 4;

    private static final int STATE_MAJOR_MASK = 4;
    private static final int STATE_MINOR_MASK = 3;

    public class JRSRArchiveInputStream extends InputStream
    {
        private int parseState2;
        private long seekingPoint;
        private long endMarker;
        private byte[] buffer;
        private int bufferStart;
        private int bufferFill;
        private boolean closed2;

        JRSRArchiveInputStream(long startPoint, long endPoint)
        {
            seekingPoint = startPoint;
            endMarker = endPoint;
            buffer = new byte[2048];
            bufferStart = 0;
            bufferFill = 0;
            parseState2 = STATE_LINE_START;
        }

        private void fillBuffer() throws IOException
        {
            synchronized(JRSRArchiveReader.this) {
                if(bufferFill > 0 && bufferStart > 0)
                    System.arraycopy(buffer, bufferStart, buffer, 0, bufferFill);
                bufferStart = 0;
                if(endMarker == seekingPoint || bufferFill == buffer.length)
                    return;
                underlying.seek(seekingPoint);
                if(endMarker - seekingPoint < buffer.length - bufferFill) {
                    //System.err.println("Reading (to EOF) " + (endMarker - seekingPoint) + " bytes starting from " +
                    //    seekingPoint + ".");
                    underlying.readFully(buffer, bufferFill, (int)(endMarker - seekingPoint));
                    bufferStart = 0;
                    bufferFill += (int)(endMarker - seekingPoint);
                    seekingPoint = endMarker;
                } else {
                    //System.err.println("Reading " + (buffer.length - bufferFill) + " bytes starting from " +
                    //    seekingPoint + ".");
                    underlying.readFully(buffer, bufferFill, buffer.length - bufferFill);
                    bufferStart = 0;
                    seekingPoint += (buffer.length - bufferFill);
                    bufferFill = buffer.length;
                }
            }
        }

        //Eat greedy match of (<0x0d|0x0a|0xc285)*.
        private boolean eatLinefeeds() throws IOException
        {
            boolean gotAny = false;

            if(parseState2 != STATE_LINE_START)
                throw new IllegalStateException("Unexpected state (not STATE_LINE_START) in eatLineFeeds.");

            while(true) {
                int next = -1;
                int readAdvance = 0;
                switch(parseState2 & STATE_MINOR_MASK) {
                case STATE_IN_LINE:
                    readAdvance = 0;
                    break;
                case STATE_LAST_194:
                case STATE_LAST_226:
                    readAdvance = 1;
                    break;
                case STATE_LAST_226_128:
                    readAdvance = 2;
                    break;
                }

                if(bufferFill <= readAdvance)
                    fillBuffer();
                if(bufferFill <= readAdvance) {
                    if(seekingPoint < endMarker)
                        continue;
                 } else
                     next = (int)buffer[bufferStart + readAdvance] & 0xFF;

//System.err.println("eatLinefeeds(): Ate " + next + ".");

                switch(parseState2 & STATE_MINOR_MASK) {
                case STATE_IN_LINE:
                    switch(next) {
                    case 10:
                    case 13:
                    case 28:
                    case 29:
                    case 30:
                        //Eat these.
//System.err.println("eatLinefeeds(): Committing.");
                        bufferStart++;
                        bufferFill--;
                        gotAny = true;
                        parseState2 = STATE_LINE_START;
                        break;
                    case 194:
                        parseState2 = (parseState2 & STATE_MAJOR_MASK) | STATE_LAST_194;
                        break;
                    case 226:
                        parseState2 = (parseState2 & STATE_MAJOR_MASK) | STATE_LAST_226;
                        break;
                    default:
                        //Hit end of linefeed run.
                        return gotAny;
                    }
                    break;
                case STATE_LAST_194:
                    switch(next) {
                    case 133:
                        //Eat these.
//System.err.println("eatLinefeeds(): Committing.");
                        bufferStart += 2;
                        bufferFill -= 2;
                        gotAny = true;
                        parseState2 = STATE_LINE_START;
                        break;
                    default:
                        //Hit end of linefeed run. Undo state update for 194.
//System.err.println("eatLinefeeds(): Exiting.");
                        parseState2 = (parseState2 & STATE_MAJOR_MASK) | STATE_IN_LINE;
                        return gotAny;
                    }
                    break;
                case STATE_LAST_226:
                    switch(next) {
                    case 128:
                        //Eat these.
                        parseState2 = (parseState2 & STATE_MAJOR_MASK) | STATE_LAST_226_128;
                        break;
                    default:
                        //Hit end of linefeed run. Undo state update for 226.
//System.err.println("eatLinefeeds(): Exiting.");
                        parseState2 = (parseState2 & STATE_MAJOR_MASK) | STATE_IN_LINE;
                        return gotAny;
                    }
                    break;
                case STATE_LAST_226_128:
                    switch(next) {
                    case 169:
                        //Eat these.
//System.err.println("eatLinefeeds(): Committing.");
                        bufferStart += 3;
                        bufferFill -= 3;
                        gotAny = true;
                        parseState2 = STATE_LINE_START;
                        break;
                    default:
                        //Hit end of linefeed run. Undo state update for 226-128.
//System.err.println("eatLinefeeds(): Exiting.");
                        parseState2 = (parseState2 & STATE_MAJOR_MASK) | STATE_IN_LINE;
                        return gotAny;
                    }
                    break;
                }
            }
        }

        private final long copyLine(byte[] target, int offset, long bound) throws IOException
        {
            long processed = 0;
            while(processed < bound) {
                int next = -1;

                if(bufferFill <= 0)
                    fillBuffer();
                if(bufferFill <= 0) {
                    if(seekingPoint < endMarker)
                        continue;
                } else
                    next = (int)buffer[bufferStart] & 0xFF;

//System.err.println("copyLine(): Ate " + next + ".");

                if(next == -1)
                    return processed;

                if(parseState2 == STATE_LINE_START) {
                    if(eatLinefeeds())
                        continue;
                    if(next != 43)
                        throw new IOException("Parsing JRSR member, expected <43>, got byte <" + next + ">.");
                    parseState2 = STATE_IN_LINE;
                    //Eat the '+'.
                    bufferStart++;
                    bufferFill--;
                    continue;
                } else if((parseState2 & STATE_MAJOR_MASK) == STATE_LINE_START) {
                    throw new IllegalStateException("Unexpected state STATE_LINE_START_LAST_x in copyLine.");
                }
                //Copy character.
                if(target != null)
                    target[offset] = (byte)next;
                offset++;
                processed++;
                bufferStart++;
                bufferFill--;

                switch(parseState2) {
                case STATE_IN_LINE:
                    switch(next) {
                    case 10:
                    case 13:
                    case 28:
                    case 29:
                    case 30:
                        parseState2 = STATE_LINE_START;
                        break;
                    case 194:
                        parseState2 = STATE_LAST_194;
                        break;
                    case 226:
                        parseState2 = STATE_LAST_226;
                        break;
                    }
                    break;
                case STATE_LAST_194:
                    parseState2 = (next == 133) ? STATE_LINE_START : STATE_IN_LINE;
                    break;
                case STATE_LAST_226:
                    parseState2 = (next == 128) ? STATE_LAST_226_128 : STATE_IN_LINE;
                    break;
                case STATE_LAST_226_128:
                    parseState2 = (next == 169) ? STATE_LINE_START : STATE_IN_LINE;
                    break;
                }
            }
            return processed;
        }

        public long skip(long n) throws IOException
        {
            if(closed || closed2)
                throw new IOException("Trying to operate on closed stream");
            long processed = 0;
            while(n > 0) {
                long x = copyLine(null, 0, n);
                processed += x;
                n -= x;
                if(x == 0 && seekingPoint == endMarker)
                    return processed;
            }
            return processed;
        }

        public int read(byte[] b, int off, int len) throws IOException
        {
            if(closed || closed2)
                throw new IOException("Trying to operate on closed stream");
            long processed = 0;
            while(len > 0) {
                long x = copyLine(b, off, len);
                processed += x;
                len -= (int)x;
                off += (int)x;
                if(x == 0 && seekingPoint == endMarker)
                    return (processed > 0) ? (int)processed : -1;
            }
            return (int)processed;
        }

        public int available()
        {
            return 1000; /* Just return something. */
        }

        public void close()
        {
            closed2 = true;
        }

        public int read() throws IOException
        {
            if(closed || closed2)
                throw new IOException("Trying to operate on closed stream");
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

    private final void initBuffers(long position)
    {
        buffer = new byte[2048];
        bufferFill = 0;
        bufferStart = 0;
        bufferBase = position;
        dreq = true;
        parseState = STATE_LINE_START;
    }

    private final void fillBuffers() throws IOException
    {
        if(bufferStart > 0 && bufferFill > 0) {
            //System.err.println("Copying buffer range (" + bufferStart + "," + bufferFill + ") -> (0," +
            //    bufferFill + ").");
            System.arraycopy(buffer, bufferStart, buffer, 0, bufferFill);
        }
        bufferStart = 0;
        int filled = 0;
        if(dreq || bufferFill == 0) {
            if(!eofFlag && bufferFill < buffer.length) {
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
            dreq = false;
        }
    }

    private final void skipRestOfLine() throws IOException
    {
        while(true) {
            int next = -1;
            fillBuffers();
            if(bufferFill <= 0) {
                if(!eofFlag)
                    continue;
            } else
                next = (int)buffer[bufferStart] & 0xFF;

            if(next == -1)
                throw new IOException("Unexpected end of JRSR archive while skipping rest of line.");

            //Skip the character.
            bufferStart++;
            bufferBase++;
            bufferFill--;

            parseState = (parseState & STATE_MINOR_MASK) | STATE_IN_LINE;
            switch(parseState) {
            case STATE_IN_LINE:
                switch(next) {
                case 10:
                case 13:
                case 28:
                case 29:
                case 30:
                    parseState = STATE_LINE_START;
                    return;
                case 194:
                    parseState = STATE_LAST_194;
                    break;
                case 226:
                    parseState = STATE_LAST_226;
                    break;
                }
                break;
            case STATE_LAST_194:
                if(next == 133) {
                    parseState = STATE_LINE_START;
                    return;
                }
                break;
            case STATE_LAST_226:
                if(next == 128)
                    parseState = STATE_LAST_226_128;
                break;
            case STATE_LAST_226_128:
                if(next == 169) {
                    parseState = STATE_LINE_START;
                    return;
                }
                break;
            }
        }

    }

    private final String utf8ToString(byte[] buffer, int start, int count) throws IOException
    {
        return Charset.forName("UTF-8").newDecoder().decode(ByteBuffer.wrap(buffer,
            start, count)).toString();
    }

    private final boolean processCommand(boolean inMember) throws IOException
    {
        long commandPos = bufferBase;
        int commandLineLen = 0;
        long commandEndPos = bufferBase;
        int scanPos = 0;
        int eollen = 1;
        //Get end of line to buffer window.
        while(true) {
            dreq = true;
            fillBuffers();

            if(scanPos == bufferFill) {
                if(bufferFill == buffer.length)
                    throw new IOException("JRSR command directive too long");
                if(eofFlag)
                    throw new IOException("Unexpected end of file while parsin JRSR command directive");
                continue;
            }
            int next1 = -1;
            int next2 = -1;
            int next3 = -1;
            if(scanPos < bufferFill)
                next1 = (int)buffer[bufferStart + scanPos] & 0xFF;
            if(scanPos < bufferFill - 1)
                next2 = (int)buffer[bufferStart + scanPos + 1] & 0xFF;
            if(scanPos < bufferFill - 2)
                next3 = (int)buffer[bufferStart + scanPos + 2] & 0xFF;

            if(next1 == 10 || next1 == 13 || next1 == 28 || next1 == 29 || next1 == 30) {
                //Single-byte line breaks.
                break;
            }
            if(next1 == 194 && next2 == 133) {
                //Two-byte line breaks.
                eollen = 2;
                break;
            }
            if(next1 == 226 && next2 == 128 && next3 == 169) {
                //Three-byte line breaks.
                eollen = 3;
                break;
            }
            scanPos++;
        }
        commandLineLen = scanPos;
        commandEndPos = commandPos + scanPos + eollen;

        String cmd = utf8ToString(buffer, bufferStart, commandLineLen);
        bufferStart = bufferStart + scanPos + eollen;
        bufferFill = bufferFill - scanPos - eollen;
        bufferBase = commandEndPos;
        return processCommand(cmd, inMember, commandPos, commandEndPos);
    }

    private final boolean processCommand(String cmd, boolean inMember, long cmdPos, long cmdEndPos) throws IOException
    {
        boolean ret = false;
        if("!END".equals(cmd)) {
            //ENd command.
            if(!inMember)
                throw new IOException("JRSR !END not allowed outside member.");
            endMember(cmdPos);
            ret = false;
        } else if(cmd.startsWith("!BEGIN") && isspace(cmd.charAt(6))) {
            //Begin command.
            int i = 6;
            while(i < cmd.length() && isspace(cmd.charAt(i)))
                i++;
            if(i == cmd.length())
                throw new IOException("JRSR !BEGIN requires member name.");
            startMember(cmd.substring(i), cmdPos, cmdEndPos);
            ret = true;
        } else
            throw new IOException("JRSR Unknown command line: '" + cmd + "'.");

        return ret;
    }

    //Eat greedy match of (<0x0d|0x0a|0xc285)*.
    private boolean eatLinefeeds() throws IOException
    {
        boolean gotAny = false;

        if(parseState != STATE_LINE_START)
            throw new IllegalStateException("Unexpected state (not STATE_LINE_START) in eatLineFeeds.");


        while(true) {
            int next = -1;
            int readAdvance = 0;
            switch(parseState & STATE_MINOR_MASK) {
            case STATE_IN_LINE:
                readAdvance = 0;
                break;
            case STATE_LAST_194:
            case STATE_LAST_226:
                readAdvance = 1;
                break;
            case STATE_LAST_226_128:
                readAdvance = 2;
                break;
            }

            if(bufferFill <= readAdvance) {
                dreq = true;
                fillBuffers();
            }
            if(bufferFill <= readAdvance) {
                if(!eofFlag)
                    continue;
            } else
                next = (int)buffer[bufferStart + readAdvance] & 0xFF;

            switch(parseState & STATE_MINOR_MASK) {
            case STATE_IN_LINE:
                switch(next) {
                case 10:
                case 13:
                case 28:
                case 29:
                case 30:
                    //Eat these.
                    bufferStart++;
                    bufferFill--;
                    bufferBase++;
                    gotAny = true;
                    parseState = STATE_LINE_START;
                    break;
                case 194:
                    parseState = (parseState & STATE_MAJOR_MASK) | STATE_LAST_194;
                    break;
                case 226:
                    parseState = (parseState & STATE_MAJOR_MASK) | STATE_LAST_226;
                    break;
                default:
                    //Hit end of linefeed run.
                    return gotAny;
                }
                break;
            case STATE_LAST_194:
                switch(next) {
                case 133:
                    //Eat these.
                    bufferStart += 2;
                    bufferFill -= 2;
                    bufferBase += 2;
                    gotAny = true;
                    parseState = STATE_LINE_START;
                    break;
                default:
                    //Hit end of linefeed run. Undo state update for 194.
                    parseState = (parseState & STATE_MAJOR_MASK) | STATE_IN_LINE;
                    return gotAny;
                }
                break;
            case STATE_LAST_226:
                switch(next) {
                case 128:
                    //Eat these.
                    parseState = (parseState & STATE_MAJOR_MASK) | STATE_LAST_226_128;
                    break;
                default:
                    //Hit end of linefeed run. Undo state update for 226.
                    parseState = (parseState & STATE_MAJOR_MASK) | STATE_IN_LINE;
                    return gotAny;
                }
                break;
            case STATE_LAST_226_128:
                switch(next) {
                case 169:
                    //Eat these.
                    bufferStart += 3;
                    bufferFill -= 3;
                    bufferBase += 3;
                    gotAny = true;
                    parseState = STATE_LINE_START;
                    break;
                default:
                    //Hit end of linefeed run. Undo state update for 226-128.
                    parseState = (parseState & STATE_MAJOR_MASK) | STATE_IN_LINE;
                    return gotAny;
                }
                    break;
            }
        }
    }

    private void parseMembers(long position) throws IOException
    {
        boolean inMember = false;

        initBuffers(position);

        while(!eofFlag || bufferFill > 0) {
            fillBuffers();
            if(eofFlag && bufferFill == 0)
                continue;
            if(eatLinefeeds())
                continue;

            /* Now try with the additional data. */
            if(inMember) {
                //System.err.println("At line start inside member.");
                /* Shift the '+'. Process the '!' */
                if(buffer[bufferStart] == (byte)43) {
                    //Just skip the whole line.
                    skipRestOfLine();
                } else if(buffer[bufferStart] == (byte)33) {
                    //Process special comkmand.
                    inMember = processCommand(true);
                } else
                    throw new IOException("Unexpected character while expecting + or ! at start of line");
            } else {
                /* Expecting line starting with '!' */
                //System.err.println("At line start not inside member.");
                if(buffer[bufferStart] != (byte)33)
                    throw new IOException("Unexpected character while expecting ! at start of line");
                inMember = processCommand(false);
            }
        }
        if(inMember)
            throw new IOException("Unexpected end of JRSR archive (still inside member)");
    }

    public JRSRArchiveReader(String file) throws IOException
    {
        long base = 5;
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
                    header[3] != (byte)82)
                throw new IOException("Bad magic");
            switch((int)header[4] & 0xFF) {
            case 10:
            case 13:
            case 28:
            case 29:
            case 30:
                break;
            case 194:
                int headerC = -1;
                try {
                    headerC = underlying.readUnsignedByte();
                } catch(EOFException e) {
                    throw new IOException("Bad magic");
                }
                if(headerC == 133)
                    base++;
                else
                    throw new IOException("Bad magic");
                break;
            case 226:
                int headerC1 = -1;
                int headerC2 = -1;
                try {
                    headerC1 = underlying.readUnsignedByte();
                    headerC2 = underlying.readUnsignedByte();
                } catch(EOFException e) {
                    throw new IOException("Bad magic");
                }
                if(headerC1 == 128 && headerC2 == 169)
                    base += 2;
                else
                    throw new IOException("Bad magic");
                break;
            }
        } catch(IOException e) {
            throw new IOException("Bad JRSR archive magic in \"" + file + "\"");
        }
        parseMembers(base);
    }

    public void close() throws IOException
    {
        memberStart = null;
        memberEnd = null;
        underlying.close();
        closed = true;
    }

    public JRSRArchiveInputStream readMember(String name) throws IOException
    {
        if(closed)
            throw new IOException("Trying to operate on closed stream");
        Long start = memberStart.get(name);
        Long end = memberEnd.get(name);
        if(start == null || end == null)
            throw new IOException("No such member \"" + name + "\" in JRSR archive.");
        return new JRSRArchiveInputStream(start.longValue(), end.longValue());
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

