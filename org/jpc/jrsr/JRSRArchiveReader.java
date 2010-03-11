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
    private boolean dreq;
    private boolean last194;


    public class JRSRArchiveInputStream extends InputStream
    {
        private boolean atLineStart;
        private boolean last194;
        private long seekingPoint;
        private long endMarker;
        private byte[] buffer;
        private int bufferStart;
        private int bufferFill;
        private boolean closed2;

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

            while(seekingPoint < endMarker || bufferFill > 0) {
                //We need at least 2 bytes to indentify NL.
                if(bufferFill < 2)
                    fillBuffer();
                if(bufferFill == 0)
                    continue;

                if(buffer[bufferStart] == (byte)13 || buffer[bufferStart] == (byte)10) {
                    //Just eat these.
                    bufferStart++;
                    bufferFill--;
                    gotAny = true;
                } else if(bufferFill > 1 && buffer[bufferStart] == (byte)194 && buffer[bufferStart + 1] == (byte)133) {
                    //Just eat these.
                    bufferStart += 2;
                    bufferFill -= 2;
                    gotAny = true;
                } else if(bufferFill > 1 || seekingPoint == endMarker || buffer[bufferStart] != (byte)194) {
                    //Other byte, can't possibly be part of NL. Retrun.
                    return gotAny;
                }
            }
            return gotAny;
        }

        private final long copyLine(byte[] target, int offset, long bound) throws IOException
        {
            long processed = 0;
            while(processed < bound) {
                if(bufferFill == 0)
                    if(seekingPoint == endMarker)
                        return processed;
                    else
                        fillBuffer();
                byte next = buffer[bufferStart];
                if(last194) {
                    last194 = false;
                     //Last was 194. If next is 133, line ends after it.
                    if(next == (byte)133) {
                        if(target != null)
                            target[offset++] = next;
                        atLineStart = true;
                        processed++;
                        bufferStart++;
                        bufferFill--;
                        return processed;
                    }
                    //If there is 194, 194 keep the last194 flag.
                    if(next == (byte)194)
                        last194 = true;
                    //Otherwise just normal byte to copy.
                    if(target != null)
                        target[offset++] = next;
                    processed++;
                    bufferStart++;
                    bufferFill--;
                }
                if(atLineStart) {
                    if(eatLinefeeds())
                        continue;
                    //If next isn't 43, its bad line.
                    if(next != (byte)43)
                        throw new IOException("Unexpected character while expecting + at start of line (got " + ((int)next & 0xFF) + ").");
                    //Don't copy this.
                    bufferStart++;
                    bufferFill--;
                    atLineStart = false;
                    continue;
                }
                if(target != null)
                    target[offset++] = next;
                processed++;
                bufferStart++;
                bufferFill--;

                if(next == (byte)10 || next == (byte)13) {
                    //Last character on line.
                    atLineStart = true;
                    return processed;
                } else if(next == (byte)194) {
                    last194 = true;
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
        eofFlag = false;
        dreq = true;
        last194 = false;
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
            fillBuffers();
            if(eofFlag && bufferFill == 0)
                throw new IOException("Unexpected end of JRSR archive while skipping rest of line.");
            byte next = buffer[bufferStart];
            if(last194) {
                //If next is 133, it is part of line that ends after it.
                if(next == (byte)133) {
                    bufferStart++;
                    bufferBase++;
                    bufferFill--;
                    last194 = false;
                    return;
                }
                //Otherwise, line doesn't end.
                last194 = (next == (byte)194);
                bufferStart++;
                bufferBase++;
                bufferFill--;
                continue;
            }
            if(next == (byte)194)
                last194 = true;
            if(next == (byte)10 || next == (byte)13) {
                //This is last on its line.
                bufferStart++;
                bufferBase++;
                bufferFill--;
                return;
            }
            //Skip the character.
            bufferStart++;
            bufferBase++;
            bufferFill--;
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
        int signature;
        boolean ret = false;
        //Get end of line to buffer window.
        while(true) {
            dreq = true;
            fillBuffers();

            if(scanPos == bufferFill) {
                if(bufferFill == buffer.length)
                    throw new IOException("JRSR command directive too long.");
                continue;
            }

            signature = (int)buffer[bufferStart + scanPos] & 0xFF;
            if(scanPos < bufferFill - 1)
                signature += ((int)buffer[bufferStart + scanPos + 1] & 0xFF) << 8;
            else
                signature += 65536;

            if((signature & 0xFF) == 0x0A) {
                //LF.
                break;
            }
            if(signature == 0xA0D) {
                //CRLF.
                eollen = 2;
                break;
            }
            if((signature & 0xFF) == 0x0D) {
                //other CR.
                break;
            }
            if(signature == 0x85C2) {
                //NL
                eollen = 2;
                break;
            }
            scanPos++;
        }
        commandLineLen = scanPos;
        commandEndPos = commandPos + scanPos + eollen;

        String cmd = utf8ToString(buffer, bufferStart, commandLineLen);
        if("!END".equals(cmd)) {
            //ENd command.
            if(!inMember)
                throw new IOException("JRSR !END not allowed outside member.");
            endMember(commandPos);
            ret = false;
        } else if(cmd.startsWith("!BEGIN") && isspace(cmd.charAt(6))) {
            //Begin command.
            int i = 6;
            while(i < cmd.length() && isspace(cmd.charAt(i)))
                i++;
            if(i == cmd.length())
                throw new IOException("JRSR !BEGIN requires member name.");
            startMember(cmd.substring(i), commandPos, commandEndPos);
            ret = true;
        } else
            throw new IOException("JRSR Unknown command line: '" + cmd + "'.");

        bufferStart = bufferStart + scanPos + eollen;
        bufferFill = bufferFill - scanPos - eollen;
        bufferBase = commandEndPos;
        return ret;
    }

    //Eat greedy match of (<0x0d|0x0a|0xc285)*.
    private boolean eatLinefeeds() throws IOException
    {
        boolean gotAny = false;

        while(!eofFlag || bufferFill > 0) {
            //We need at least 2 bytes to indentify NL.
            if(bufferFill < 2)
                dreq = true;
            fillBuffers();
            if(eofFlag && bufferFill == 0)
                return gotAny;
            if(bufferFill == 0)
                continue;

            if(buffer[bufferStart] == (byte)13 || buffer[bufferStart] == (byte)10) {
                //Just eat these.
                bufferStart++;
                bufferBase++;
                bufferFill--;
                gotAny = true;
            } else if(bufferFill > 1 && buffer[bufferStart] == (byte)194 && buffer[bufferStart + 1] == (byte)133) {
                //Just eat these.
                bufferStart += 2;
                bufferBase += 2;
                bufferFill -= 2;
                gotAny = true;
            } else if(bufferFill > 1 || eofFlag || buffer[bufferStart] != (byte)194) {
                //Other byte, can't possibly be part of NL. Retrun.
                 return gotAny;
            }
        }
        return gotAny;
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
            throw new IOException("Unexpected end of JRSR archive");
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
            if(header[4] != (byte)10 && header[4] != (byte)13 && header[4] != (byte)194)
                throw new IOException("Bad magic");
            if(header[4] == (byte)13) {
                //Eat LF if its there.
                int headerC = -1;
                try {
                    headerC = underlying.readUnsignedByte();
                } catch(EOFException e) {
                    //No members in archive.
                    return;
                }
                if(headerC == 10)
                    base++;
                else
                    underlying.seek(5);  //Undo the read.
            } else if(header[4] == (byte)194) {
                int headerC = -1;
                try {
                    headerC = underlying.readUnsignedByte();
                } catch(EOFException e) {
                }
                if(headerC == 133)
                    base++;
                else
                    throw new IOException("Bad magic");
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
