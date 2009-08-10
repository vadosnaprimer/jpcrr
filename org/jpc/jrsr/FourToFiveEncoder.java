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


public class FourToFiveEncoder extends OutputStream
{
    private OutputStream underlying;
    private byte[] buffer;
    private int bufferFill;
    private int rowModulo;

    public FourToFiveEncoder(OutputStream output)
    {
        underlying = output;
        buffer = new byte[4];
        bufferFill = 0;
        rowModulo = 0;
    }

    public void close() throws IOException
    {
        flush();                     //Dump all output.
        underlying.write((byte)33);  //END OF STREAM.
        underlying.write((byte)10);  //END OF LINE.
        underlying.flush();
        underlying.close();
    }

    public void flush() throws IOException
    {
        if(bufferFill == 0)
            return;
        byte[] out = new byte[5];
        encodeGroup(out, buffer, bufferFill, 0, 0);
        underlying.write(out, 0, bufferFill + 1);
        underlying.flush();
    }

    public void write(byte[] b) throws IOException
    {
        write(b, 0, b.length);
    }

    public void write(int b) throws IOException
    {
        write(new byte[]{(byte)b}, 0, 1);
    }

    private int encodeGroup(byte[] out, byte[] in, int len, int outOff, int inOff)
    {
        int rowShift = 0;
        if(len == 4) {
            long value = (((long)in[inOff] & 0xFF) << 24) | (((long)in[inOff + 1] & 0xFF)  << 16) |
                (((long)in[inOff + 2] & 0xFF) << 8) | ((long)in[inOff + 3] & 0xFF);
            byte x1 = (byte)(value / 74805201 + 66);
            byte x2 = (byte)(value / 804357 % 93 + 34);
            byte x3 = (byte)(value / 8649 % 93 + 34);
            byte x4 = (byte)(value / 93 % 93 + 34);
            byte x5 = (byte)(value % 93 + 34);
            out[outOff + 0] = x1; if(rowModulo == 69) { rowModulo = -1; out[++outOff + 0] = (byte)10; rowShift++; }
            out[outOff + 1] = x2; if(rowModulo == 68) { rowModulo = -2; out[++outOff + 1] = (byte)10; rowShift++; }
            out[outOff + 2] = x3; if(rowModulo == 67) { rowModulo = -3; out[++outOff + 2] = (byte)10; rowShift++; }
            out[outOff + 3] = x4; if(rowModulo == 66) { rowModulo = -4; out[++outOff + 3] = (byte)10; rowShift++; }
            out[outOff + 4] = x5; if(rowModulo == 65) { rowModulo = -5; out[++outOff + 4] = (byte)10; rowShift++; }
            rowModulo += 5;
        } else if(len == 3) {
            long value = (((long)in[inOff] & 0xFF) << 16) | (((long)in[inOff + 1] & 0xFF)  << 8) |
                ((long)in[inOff + 2] & 0xFF);
            byte x2 = (byte)(value / 804357 + 45);
            byte x3 = (byte)(value / 8649 % 93 + 34);
            byte x4 = (byte)(value / 93 % 93 + 34);
            byte x5 = (byte)(value % 93 + 34);
            out[outOff + 0] = x2; if(rowModulo == 69) { rowModulo = -1; out[++outOff + 0] = (byte)10; rowShift++; }
            out[outOff + 1] = x3; if(rowModulo == 68) { rowModulo = -2; out[++outOff + 1] = (byte)10; rowShift++; }
            out[outOff + 2] = x4; if(rowModulo == 67) { rowModulo = -3; out[++outOff + 2] = (byte)10; rowShift++; }
            out[outOff + 3] = x5; if(rowModulo == 66) { rowModulo = -4; out[++outOff + 3] = (byte)10; rowShift++; }
            rowModulo += 4;
        } else if(len == 2) {
            long value = (((long)in[inOff] & 0xFF) << 8) | ((long)in[inOff + 1] & 0xFF);
            byte x3 = (byte)(value / 8649 + 37);
            byte x4 = (byte)(value / 93 % 93 + 34);
            byte x5 = (byte)(value % 93 + 34);
            out[outOff + 0] = x3; if(rowModulo == 69) { rowModulo = -1; out[++outOff + 0] = (byte)10; rowShift++; }
            out[outOff + 1] = x4; if(rowModulo == 68) { rowModulo = -2; out[++outOff + 1] = (byte)10; rowShift++; }
            out[outOff + 2] = x5; if(rowModulo == 67) { rowModulo = -3; out[++outOff + 2] = (byte)10; rowShift++; }
            rowModulo += 3;
        } else if(len == 1) {
            long value = ((long)in[inOff] & 0xFF);
            byte x4 = (byte)(value / 93 + 34);
            byte x5 = (byte)(value % 93 + 34);
            out[outOff + 0] = x4; if(rowModulo == 69) { rowModulo = -1; out[++outOff + 0] = (byte)10; rowShift++; }
            out[outOff + 1] = x5; if(rowModulo == 68) { rowModulo = -2; out[++outOff + 1] = (byte)10; rowShift++; }
            rowModulo += 2;
        }
        return rowShift;
    }

    public void write(byte[] b, int off, int len) throws IOException
    {
        byte[] out = new byte[2100];
        int rowShift = 0;
        while(len > 0) {
            if(len > 4 && bufferFill == 0) {
                //Fastpath copy. 2100 byte buffer => 389 blocks max.
                int blocks = out.length / 5 - out.length / 70 - 1;
                if(len / 4 < blocks)
                    blocks = len / 4;
                for(int i = 0; i < blocks; i++)
                    rowShift += encodeGroup(out, b, 4, 5 * i + rowShift, 4 * i + off);
                off += 4 * blocks;
                len -= 4 * blocks;
                underlying.write(out, 0, 5 * blocks + rowShift);
                rowShift = 0;
                continue;
            }

            //The slowpath. Copy character at time.
            buffer[bufferFill++] = b[off++];
            len--;
            if(bufferFill == 4) {
                rowShift = encodeGroup(out, buffer, 4, 0, 0);
                underlying.write(out, 0, 5 + rowShift);
                bufferFill = 0;
                rowShift = 0;
            }
        }
    }
}
