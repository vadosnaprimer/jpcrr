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


public class FourToFiveDecoder extends InputStream
{
    private InputStream underlying;
    private byte[] buffer;
    private int bufferFill;
    private int bufferBase;
    private int bufferRemainder;
    private int bufferRemainderStart;
    private boolean eofFlag;

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
    }

    public int read(byte[] b) throws IOException
    {
        return read(b, 0, b.length);
    }

    public int available() throws IOException
    {
        return underlying.available() / 5 * 4;
    }

    public int read() throws IOException
    {
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

        //Collapse the whitespace.
        int readPosition = 0;
        int writePosition = 0;
        while(readPosition < data) {
            if(buffer[readPosition] == (byte)10 || buffer[readPosition] == (byte)32)
                ;
            else if(buffer[readPosition] < 32 || buffer[readPosition] > 126)
                throw new IOException("Illegal character " + ((int)buffer[readPosition] & 0xFF) + " in FourToFive stream");
            else
                buffer[writePosition++] = buffer[readPosition];
            readPosition++;
        }
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
