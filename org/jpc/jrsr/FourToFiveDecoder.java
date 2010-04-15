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

//
// Four to five encoding:
//      - 1 byte encoding:  <00> <00> - <02> <69>
//      - 2 bytes encoding: <03> <00> <00> - <10> <53> <63>
//	- 3 bytes encoding: <11> <00> <00> <00> - <31> <79> <73> <15>
//      - 4 bytes encoding: <32> <00> <00> <00> <00> -  <89> <38> <58> <39> <04>
//
// Base 93.
// Spaces and LF are skipped.
// End of stream is marked by '!'.
// Base character code: 34.


public class FourToFiveDecoder extends InputStream implements Closeable
{
    private InputStream underlying;
    private byte[] buffer;
    private int bufferFill;
    private int bufferBase;
    private int bufferRemainder;
    private int bufferRemainderStart;
    private boolean eofFlag;
    private boolean closed;

    public FourToFiveDecoder(InputStream input)
    {
        underlying = input;
        buffer = new byte[2100];
        bufferFill = 0;
        bufferBase = 0;
        bufferRemainder = 0;
        bufferRemainderStart = 0;
        eofFlag = false;
    }

    public boolean markSupported()
    {
        return false;
    }

    public void mark(int limit)
    {
    }

    public void reset() throws IOException
    {
        throw new IOException("FourToFiveDecoder does not support marks");
    }

    public void close() throws IOException
    {
        underlying.close();
        closed = true;
    }

    public int read(byte[] b) throws IOException
    {
        return read(b, 0, b.length);
    }

    public int available() throws IOException
    {
        if(closed)
            throw new IOException("Trying to operate on closed stream");
        return underlying.available() / 5 * 4;
    }

    public int read() throws IOException
    {
        if(closed)
            throw new IOException("Trying to operate on closed stream");
        byte[] x = new byte[1];
        int y = read(x, 0, 1);
        if(y == -1)
            return -1;
        return (int)x[0] & 0xFF;
    }

    private String formatCode(byte[] buf, int offset, int len)
    {
        char[] chars = new char[len];
        for(int i = 0; i < len; i++) {
            System.err.println("" + ((int)buf[offset + i] & 0xFF));
            chars[i] = (char)((int)buf[offset + i] & 0xFF);
        }
        return new String(chars);
    }

    private int collapseWhitespace(byte[] buffer, int length) throws IOException
    {
        //State value:
        //0 => Nothing special.
        //1 => 194 before (NL)
        //2 => 225 before (Codepoints 1000-1FFF)
        //3 => 226 before (Codepoints 2000-2FFF)
        //4 => 227 before (Codepoints 3000-3FFF)
        //5 => 225 154 before (codepoints (1680-16BF)
        //6 => 225 160 before (codepoints (1800-183F)
        //7 => 226 128 before (codepoints (2000-203F)
        //8 => 226 129 before (codepoints (2040-207F)
        //7 => 227 128 before (codepoints (3000-303F)
        int state = 0;

        int readPosition = 0;
        int writePosition = 0;
        while(readPosition < length) {
            int ch = (int)buffer[readPosition] & 0xFF;
            switch(state) {
            case 0:
                if(ch == 0x09) {
                    //TAB. Eat.
                } else if(ch == 0x0A) {
                    //LF. Eat.
                } else if(ch == 0x0C) {
                    //FF. Eat.
                } else if(ch == 0x0D) {
                    //CR. Eat.
                } else if(ch == 0x1C) {
                    //IS4. Eat.
                } else if(ch == 0x1D) {
                    //IS3. Eat.
                } else if(ch == 0x1E) {
                    //IS2. Eat.
                } else if(ch == 0x20) {
                    //Space. Eat.
                } else if(ch >= 33 && ch <= 126) {
                    //Non-WS. Copy.
                    buffer[writePosition++] = buffer[readPosition];
                } else if(ch == 194) {
                    state = 1;
                } else if(ch == 225) {
                    state = 2;
                } else if(ch == 226) {
                    state = 3;
                } else if(ch == 227) {
                    state = 4;
                } else {
                    throw new IOException("Illegal character " + ch + " in FourToFive stream");
                }
                break;
            case 1:
                if(ch == 133) {
                    state = 0;
                } else {
                    throw new IOException("Illegal character 194-" + ch + " in FourToFive stream");
                }
                break;
            case 2:
                if(ch == 154) {
                    state = 5;
                } else if(ch == 160) {
                    state = 6;
                } else {
                    throw new IOException("Illegal character 225-" + ch + " in FourToFive stream");
                }
                break;
            case 3:
                if(ch == 128) {
                    state = 7;
                } else if(ch == 129) {
                    state = 8;
                } else {
                    throw new IOException("Illegal character 226-" + ch + " in FourToFive stream");
                }
                break;
            case 4:
                if(ch == 128) {
                    state = 9;
                } else {
                    throw new IOException("Illegal character 227-" + ch + " in FourToFive stream");
                }
                break;
            case 5:
                if(ch == 128) {
                    state = 0;
                } else {
                    throw new IOException("Illegal character 225-154-" + ch + " in FourToFive stream");
                }
                break;
            case 6:
                if(ch == 142) {
                    state = 0;
                } else {
                    throw new IOException("Illegal character 225-160-" + ch + " in FourToFive stream");
                }
                break;
            case 7:
                if(ch >= 128 && ch <= 138) {
                    state = 0;
                } else if(ch == 168) {
                    state = 0;
                } else if(ch == 169) {
                    state = 0;
                } else {
                    throw new IOException("Illegal character 226-128-" + ch + " in FourToFive stream");
                }
                break;
            case 8:
                if(ch == 159) {
                    state = 0;
                } else {
                    throw new IOException("Illegal character 226-129-" + ch + " in FourToFive stream");
                }
                break;
            case 9:
                if(ch == 128) {
                    state = 0;
                } else {
                    throw new IOException("Illegal character 227-128-" + ch + " in FourToFive stream");
                }
                break;
            }
            readPosition++;
        }
        return writePosition;
    }

    private void fillBuffer() throws IOException
    {
        boolean streamEOF = false;

        //Take the remainder from previous round.
        int data = bufferRemainder;
        System.arraycopy(buffer, bufferRemainderStart, buffer, 0, bufferRemainder);
        bufferRemainder = 0;

        //Fill the buffer with input data.
        while(data < buffer.length) {
            int dataDelta = underlying.read(buffer, data, buffer.length - data);
            if(dataDelta == -1) {
                eofFlag = true;
                break;
            }
            data += dataDelta;
        }

        int readPosition = 0;
        int writePosition = collapseWhitespace(buffer, data);
        data = writePosition;

        //Decode the symbols.
        readPosition = 0;
        writePosition = 0;
        while(readPosition < data) {
            if(buffer[readPosition] == 33) {
                streamEOF = true;
                eofFlag = true;
                break;
            } else if(buffer[readPosition] >= 34 && buffer[readPosition] <= 36) {
                if(readPosition + 2 > data)
                    break;
                if(buffer[readPosition + 1] == 33)
                    throw new IOException("! in middle of sequence not allowed");
                long decoded = (long)(buffer[readPosition] - 34) * 93 +
                    (long)(buffer[readPosition + 1] - 34);
                if(decoded > 255)
                    throw new IOException("Illegal sequence " + formatCode(buffer, readPosition, 2) +
                        " in FourToFive stream");
                readPosition += 2;
                buffer[writePosition++] = (byte)decoded;
            } else if(buffer[readPosition] >= 37 && buffer[readPosition] <= 44) {
                if(readPosition + 3 > data)
                    break;
                if(buffer[readPosition + 1] == 33)
                    throw new IOException("! in middle of sequence not allowed");
                if(buffer[readPosition + 2] == 33)
                    throw new IOException("! in middle of sequence not allowed");
                long decoded = (long)(buffer[readPosition] - 37) * 93 * 93 +
                    (long)(buffer[readPosition + 1] - 34) * 93 +
                    (long)(buffer[readPosition + 2] - 34);
                if(decoded > 65535)
                    throw new IOException("Illegal sequence " + formatCode(buffer, readPosition, 3) +
                        " in FourToFive stream");
                readPosition += 3;
                buffer[writePosition++] = (byte)(decoded / 256);
                buffer[writePosition++] = (byte)(decoded % 256);
            } else if(buffer[readPosition] >= 45 && buffer[readPosition] <= 65) {
                if(readPosition + 4 > data)
                    break;
                if(buffer[readPosition + 1] == 33)
                    throw new IOException("! in middle of sequence not allowed");
                if(buffer[readPosition + 2] == 33)
                    throw new IOException("! in middle of sequence not allowed");
                if(buffer[readPosition + 3] == 33)
                    throw new IOException("! in middle of sequence not allowed");
                long decoded = (long)(buffer[readPosition] - 45) * 93 * 93 * 93 +
                    (long)(buffer[readPosition + 1] - 34) * 93 * 93 +
                    (long)(buffer[readPosition + 2] - 34) * 93 +
                    (long)(buffer[readPosition + 3] - 34);
                if(decoded > 16777215)
                    throw new IOException("Illegal sequence " + formatCode(buffer, readPosition, 4) +
                        " in FourToFive stream");
                readPosition += 4;
                buffer[writePosition++] = (byte)(decoded / 65536);
                buffer[writePosition++] = (byte)(decoded / 256 % 256);
                buffer[writePosition++] = (byte)(decoded % 256);
            } else if(buffer[readPosition] >= 66 && buffer[readPosition] <= 123) {
                if(readPosition + 5 > data)
                    break;
                if(buffer[readPosition + 1] == 33)
                    throw new IOException("! in middle of sequence not allowed");
                if(buffer[readPosition + 2] == 33)
                    throw new IOException("! in middle of sequence not allowed");
                if(buffer[readPosition + 3] == 33)
                    throw new IOException("! in middle of sequence not allowed");
                if(buffer[readPosition + 4] == 33)
                    throw new IOException("! in middle of sequence not allowed");
                long decoded = (long)(buffer[readPosition] - 66) * 93 * 93 * 93 * 93 +
                    (long)(buffer[readPosition + 1] - 34) * 93 * 93 * 93 +
                    (long)(buffer[readPosition + 2] - 34) * 93 * 93 +
                    (long)(buffer[readPosition + 3] - 34) * 93 +
                    (long)(buffer[readPosition + 4] - 34);
                if(decoded > 4294967296L)
                    throw new IOException("Illegal sequence " + formatCode(buffer, readPosition, 5) +
                        " in FourToFive stream (position " + readPosition + ")");
                readPosition += 5;
                buffer[writePosition++] = (byte)(decoded / 16777216);
                buffer[writePosition++] = (byte)(decoded / 65536 % 256);
                buffer[writePosition++] = (byte)(decoded / 256 % 256);
                buffer[writePosition++] = (byte)(decoded % 256);
            } else {
                throw new IOException("Illegal sequence " + formatCode(buffer, readPosition, 1) +
                    " in FourToFive stream");
            }
        }

        //Compute the amount of remainder.
        if(!streamEOF && data > readPosition) {
            bufferRemainderStart = readPosition;
            bufferRemainder = data - readPosition;
        } else
            bufferRemainder = 0;

        if(eofFlag && bufferRemainder > 0) {
            //Incomplete character at the end. This is an error.
            throw new IOException("Unexpected end of FourToFive stream in partial sequence " +
                formatCode(buffer, bufferRemainderStart, bufferRemainder));
        }

        bufferBase = 0;
        bufferFill = writePosition;
    }

    public long skip(long n) throws IOException
    {
        if(closed)
            throw new IOException("Trying to operate on closed stream");

        long processed = 0;
        while(n > 0) {
            if(bufferFill == 0)
                if(eofFlag)
                    return processed;
                else
                    fillBuffer();
            if(n < (long)bufferFill) {
                bufferBase += (int)n;
                bufferFill -= (int)n;
                processed += n;
                n = 0;
            } else {
                n -= (long)bufferFill;
                bufferBase += bufferFill;
                processed += (long)bufferFill;
                bufferFill = 0;
            }
        }
        return processed;
    }

    public int read(byte[] b, int off, int len) throws IOException
    {
        if(closed)
            throw new IOException("Trying to operate on closed stream");

        int processed = 0;
        while(len > 0) {
            if(bufferFill == 0)
                if(eofFlag && processed == 0) {
                    //System.err.println("No data due to EOF.");
                    return -1;
                } else if(eofFlag) {
                    //System.err.println("Incomplete read (" + processed + ") due to EOF.");
                    return processed;
                } else
                    fillBuffer();
            if(len < bufferFill) {
                System.arraycopy(buffer, bufferBase, b, off, len);
                bufferBase += len;
                bufferFill -= len;
                off += len;
                processed += len;
                len = 0;
            } else {
                System.arraycopy(buffer, bufferBase, b, off, bufferFill);
                off += bufferFill;
                len -= bufferFill;
                bufferBase += bufferFill;
                processed += bufferFill;
                bufferFill = 0;
            }
        }
        //System.err.println("Returned request of " + processed + " bytes.");
        return processed;
    }
}
