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

public class UTFInputLineStream implements Closeable
{
    private InputStream underlying;
    private byte[] buffer;
    private int bufferStart;
    private int bufferFill;
    private boolean eofFlag;

    private char[] cBuffer;
    private int cBufferStart;
    private int cBufferFill;
    private boolean cEOFFlag;

    private int bytesTotal;
    private int bytesComing;
    private int partialValue;
    private boolean invalidRunFlag;

    private boolean closed;

    public UTFInputLineStream(InputStream in)
    {
        underlying = in;
        buffer = new byte[1024];
        bufferStart = 0;
        bufferFill = 0;
        eofFlag = false;

        cBuffer = new char[384];
        cBufferStart = 0;
        cBufferFill = 0;
        cEOFFlag = false;

        bytesTotal = 0;
        bytesComing = 0;
        partialValue = 0;
        invalidRunFlag = false;
    }

    public void close() throws IOException
    {
        underlying.close();
        closed = true;
    }

    private void fillBuffer() throws IOException
    {
        //Top off the buffer.
        if(bufferStart > 0 && bufferFill > 0)
            System.arraycopy(buffer, bufferStart, buffer, 0, bufferFill);
        bufferStart = 0;
        if(bufferFill > buffer.length / 2)
            return;
        if(!eofFlag) {
            int r = underlying.read(buffer, bufferFill, buffer.length - bufferFill);
            if(r < 0)
                eofFlag = true;
            else
                bufferFill += r;
        }
    }

    private void fillCBuffer() throws IOException
    {
        if(cBufferStart > 0 && cBufferFill > 0)
            System.arraycopy(cBuffer, cBufferStart, cBuffer, 0, cBufferFill);
        cBufferStart = 0;

        //-1 is there to leave space for extra stuff.
        while(bufferFill > 0 && cBufferFill < cBuffer.length - 2) {
            int ch = (int)buffer[bufferStart++] & 0xFF;
            bufferFill--;
            if(bytesComing > 0) {
                //Continuation.
                if(ch < 128 && ch > 159) {
                    //Invalid character. Resynchronize.
                    cBuffer[cBufferFill++] = (char)0xFFFD;
                    bytesTotal = 0;
                    bytesComing = 0;
                    partialValue = 0;
                    bufferStart--;
                    bufferFill++;
                    continue;
                }
                partialValue = partialValue * 64 + (ch - 128);
                bytesComing--;
                if(bytesComing == 0) {
                    //It completed. Check overlong forms and encode.
                    if(bytesTotal == 2 && partialValue < 0x80)
                        partialValue = 0xFFFD;
                    if(bytesTotal == 3 && partialValue < 0x800)
                        partialValue = 0xFFFD;
                    if(bytesTotal == 4 && partialValue < 0x10000)
                        partialValue = 0xFFFD;
                    if(bytesTotal < 1 && bytesTotal > 4)
                        partialValue = 0xFFFD;
                    if(partialValue > 127 && partialValue < 160 && partialValue != 133)
                        throw new IOException("Illegal character " + partialValue + " in stream");
                    if(partialValue >= 0xD800 && partialValue <= 0xDFFF)
                        throw new IOException("Illegal character " + partialValue + " in stream");
                    if((partialValue & 0xFFFE) == 0xFFFE)
                        throw new IOException("Illegal character " + partialValue + " in stream");
                    if(partialValue > 0x10FFFF)
                        throw new IOException("Illegal character " + partialValue + " in stream");
                    if(partialValue < 0x10000)
                        cBuffer[cBufferFill++] = (char)partialValue;
                    else {
                        cBuffer[cBufferFill++] = (char)((partialValue + 0x35F0000) >> 10);
                        cBuffer[cBufferFill++] = (char)((partialValue & 0x3FF) + 0xDC00);
                    }
                }
                continue;
            }
            if(ch < 128) {
                //One byte form.
                if((ch < 32 && ch != 13 && ch != 10 && ch != 9 && ch != 12 &&
                    ch != 28 && ch != 29 && ch != 30) || ch == 127)
                    throw new IOException("Illegal character " + ch + " in stream");
                bytesComing = 0;
                cBuffer[cBufferFill++] = (char)ch;
                invalidRunFlag = false;
                continue;
            } else if((ch >= 128 && ch < 192) || ch >= 248) {
                //Invalid continuation or other.
                if(!invalidRunFlag)
                    cBuffer[cBufferFill++] = (char)0xFFFD;
                invalidRunFlag = true;
                continue;
            } else if(ch >= 192 && ch < 224) {
                //Two-byte form.
                bytesTotal = 2;
                bytesComing = 1;
                partialValue = ch - 192;
                invalidRunFlag = false;
            } else if(ch >= 224 && ch < 240) {
                //Three-byte form.
                bytesTotal = 3;
                bytesComing = 2;
                partialValue = ch - 224;
                invalidRunFlag = false;
            } else if(ch >= 240 && ch < 248) {
                //Four-byte form.
                bytesTotal = 4;
                bytesComing = 3;
                partialValue = ch - 240;
                invalidRunFlag = false;
            }
        }
        if(bufferFill == 0 && eofFlag) {
            cEOFFlag = true;
            if(bytesComing > 0)
                cBuffer[cBufferFill++] = (char)0xFFFD;
        }
    }

    public String readLine() throws IOException
    {
        if(closed)
            throw new IOException("Trying to operate on closed stream");

        int CR = 13;
        int LF = 10;
        int IS4 = 28;
        int IS3 = 29;
        int IS2 = 30;
        int PS = 0x2029;
        int NL = 133;
        int NP = -1;
        int EOF = -2;
        int eollen = 1;
        StringBuilder buf = new StringBuilder();
        if(cEOFFlag && cBufferFill == 0)
            return null;

        while(true) {
            fillBuffer();
            fillCBuffer();

            eollen = 1;

            int lfOff = 0;
            while(lfOff < cBufferFill) {
                int thisChar = (int)cBuffer[cBufferStart + lfOff];
                int nextChar;
                if(lfOff < cBufferFill - 1)
                    nextChar = (int)cBuffer[cBufferStart + lfOff + 1];
                else if(cEOFFlag)
                    nextChar = EOF;
                else
                    nextChar = NP;

                if(thisChar == LF)
                    break;    //Break line.
                else if(thisChar == IS4)
                    break;    //Break line.
                else if(thisChar == IS3)
                    break;    //Break line.
                else if(thisChar == IS2)
                    break;    //Break line.
                else if(thisChar == PS)
                    break;    //Break line.
                else if(thisChar == NL)
                    break;    //Break line.
                else if(thisChar == CR && nextChar == NP) {
                    eollen = 0;   //Copy to make more room.
                    break;
                } else if(thisChar == CR && nextChar == EOF)
                    break;    //Break line.
                else if(thisChar == CR && nextChar == LF) {
                    eollen = 2;   //Eat CRLF.
                    break;
                } else if(thisChar == CR)
                    break;    //Break line.
                lfOff++;
            }

            if(lfOff == cBufferFill) {
                //No LF. Just dump the entiere buffer into string.
                buf.append(cBuffer, cBufferStart, cBufferFill);
                cBufferFill = 0;
                if(cEOFFlag)
                    break;
            } else {
                //Dump up to specified position to string.
                buf.append(cBuffer, cBufferStart, lfOff);
                cBufferStart += (lfOff + eollen);
                cBufferFill -= (lfOff + eollen);
                if(eollen > 0)
                    break;
            }
        }
        return buf.toString();
    }
}
