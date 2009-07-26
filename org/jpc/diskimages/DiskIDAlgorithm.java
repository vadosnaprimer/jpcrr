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

package org.jpc.diskimages;


public class DiskIDAlgorithm
{
    long[] chainingState;
    long[] modifiers;
    byte[] partialBuffer;
    int partialBufferFill;
    int dataSoFar;
    private static final int BUFFER = 32;

    public DiskIDAlgorithm()
    {
        chainingState = new long[4];
        modifiers = new long[2];
        partialBuffer = new byte[BUFFER];
        partialBufferFill = 0;
        dataSoFar = 0;

        modifiers[1] = 0xC400000000000000L;
        modifiers[0] = 0x0000000000000020L;
        partialBuffer[0] = (byte)'S';
        partialBuffer[1] = (byte)'H';
        partialBuffer[2] = (byte)'A';
        partialBuffer[3] = (byte)'3';
        partialBuffer[4] = (byte)1;
        partialBuffer[8] = (byte)128;

        transform();

        if(chainingState[0] != 0x302F7EA23D7FE2E1L ||
                chainingState[1] != 0xADE4683A6913752BL ||
                chainingState[2] != 0x975CFABEF208AB0AL ||
                chainingState[3] != 0x2AF4BA95F831F55BL) {
            System.err.println("PANIC: IV calculated value incorrect.");
        }
    }

    private void transform()
    {
        int i;
        int k;

        long[] feedInput = new long[4];
        for(i = 0; i < BUFFER; i++) {
            long b = (long)partialBuffer[i] & 0xFF;
            feedInput[i / 8] |= (b << (8 * (i % 8)));
        }


        long A = feedInput[0];
        long B = feedInput[1];
        long C = feedInput[2];
        long D = feedInput[3];
        long[] _key = new long[22];
        long[] _tweak = new long[20];

        _key[4] = 6148914691236517205L;
        for(i = 0; i < 4; i++) {
                _key[4] ^= (_key[i] = chainingState[i]);
        }
        for(i = 5; i < 22; i++) {
                _key[i] = _key[i - 5];
        }

        _tweak[0] = modifiers[0];
        _tweak[1] = modifiers[1];
        _tweak[2] = modifiers[0] ^ modifiers[1];
        for(i = 3; i < 20; i++)
                _tweak[i] = _tweak[i - 3];


        k = 0;


        A += _key[k + 0];
        B += (_key[k + 1] + _tweak[k + 0]);
        C += (_key[k + 2] + _tweak[k + 1]);
        D += (_key[k + 3] + k);


        for(i = 0; i < 9; i++) {
                A += B; B = (B << 5) | (B >>> 59); B ^= A;
                C += D; D = (D << 56) | (D >>> 8); D ^= C;
                A += D; D = (D << 36) | (D >>> 28); D ^= A;
                C += B; B = (B << 28) | (B >>> 36); B ^= C;
                A += B; B = (B << 13) | (B >>> 51); B ^= A;
                C += D; D = (D << 46) | (D >>> 18); D ^= C;
                A += D; D = (D << 58) | (D >>> 6); D ^= A;
                C += B; B = (B << 44) | (B >>> 20); B ^= C;
                k++;
                A += _key[k + 0];
                B += (_key[k + 1] + _tweak[k + 0]);
                C += (_key[k + 2] + _tweak[k + 1]);
                D += (_key[k + 3] + k);
                A += B; B = (B << 26) | (B >>> 38); B ^= A;
                C += D; D = (D << 20) | (D >>> 44); D ^= C;
                A += D; D = (D << 53) | (D >>> 11); D ^= A;
                C += B; B = (B << 35) | (B >>> 29); B ^= C;
                A += B; B = (B << 11) | (B >>> 53); B ^= A;
                C += D; D = (D << 42) | (D >>> 22); D ^= C;
                A += D; D = (D << 59) | (D >>> 5); D ^= A;
                C += B; B = (B << 50) | (B >>> 14); B ^= C;
                k++;
                A += _key[k + 0];
                B += (_key[k + 1] + _tweak[k + 0]);
                C += (_key[k + 2] + _tweak[k + 1]);
                D += (_key[k + 3] + k);
        }

        chainingState[0] = feedInput[0] ^ A;
        chainingState[1] = feedInput[1] ^ B;
        chainingState[2] = feedInput[2] ^ C;
        chainingState[3] = feedInput[3] ^ D;

        for(i = 0; i < BUFFER; i++)
            partialBuffer[i] = 0;

    }

    private void collapse(boolean fini)
    {
        boolean init = (dataSoFar == 0);
        dataSoFar += partialBufferFill;
        partialBufferFill = 0;
        modifiers[1] = 0x3000000000000000L;
        modifiers[0] = dataSoFar;
        if(init)
            modifiers[1] |= 0x4000000000000000L;
        if(fini)
            modifiers[1] |= 0x8000000000000000L;
        transform();
    }

    private void outputTransform()
    {
        modifiers[1] = 0xFF00000000000000L;
        modifiers[0] = 0x0000000000000008L;
        transform();
    }

    public void addBuffer(byte[] buffer)
    {
        addBuffer(buffer, 0, buffer.length);
    }

    public void addBuffer(byte[] buffer, int start, int length2)
    {
        while(length2 > 0) {
            if(partialBufferFill == BUFFER) {
                collapse(false);
            }
            if(partialBufferFill + length2 <= BUFFER) {
                System.arraycopy(buffer, start, partialBuffer, partialBufferFill, length2);
                start += length2;
                partialBufferFill += length2;
                length2 = 0;
            } else {
                System.arraycopy(buffer, start, partialBuffer, partialBufferFill, BUFFER - partialBufferFill);
                start += (BUFFER - partialBufferFill);
                length2 -= (BUFFER - partialBufferFill);
                partialBufferFill = BUFFER;
            }
        }
    }

    //Trashes state.
    public byte[] getFinalOutput()
    {
        collapse(true);       //This is done anyway even for 0-byte input.
        outputTransform();
        byte[] output = new byte[16];
        for(int i = 0; i < 16; i++) {
            output[i] = (byte)((chainingState[i / 8] >>> (8 * (i % 8))));
        }
        return output;
    }

    public String getFinalOutputString()
    {
        byte[] out = getFinalOutput();
        char[] hex = new char[16];
        hex[0] = '0';  hex[1] = '1';  hex[2] = '2';  hex[3] = '3';
        hex[4] = '4';  hex[5] = '5';  hex[6] = '6';  hex[7] = '7';
        hex[8] = '8';  hex[9] = '9';  hex[10] = 'A';  hex[11] = 'B';
        hex[12] = 'C';  hex[13] = 'D';  hex[14] = 'E';  hex[15] = 'F';
        StringBuffer buf = new StringBuffer(32);
        for(int i = 0; i < 16; i++) {
            int b = (int)out[i] & 0xFF;
            buf.append(hex[b / 16]);
            buf.append(hex[b % 16]);
        }
        return buf.toString();
    }
}
