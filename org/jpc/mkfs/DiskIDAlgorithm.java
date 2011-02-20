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

package org.jpc.mkfs;

import java.util.Arrays;
import java.io.IOException;
import org.jpc.images.BaseImage;
import org.jpc.images.ImageID;

public class DiskIDAlgorithm
{
    long chainingStateA;
    long chainingStateB;
    long chainingStateC;
    long chainingStateD;
    long modifiersA;
    long modifiersB;
    byte[] partialBuffer;
    boolean nonzeroFlag;
    int partialBufferFill;
    int dataSoFar;
    private static final int BUFFER = 32;

    public DiskIDAlgorithm()
    {
        partialBuffer = new byte[BUFFER];
        partialBufferFill = 0;
        dataSoFar = 0;

        modifiersB = 0xC400000000000000L;
        modifiersA = 0x0000000000000020L;
        partialBuffer[0] = (byte)'S';
        partialBuffer[1] = (byte)'H';
        partialBuffer[2] = (byte)'A';
        partialBuffer[3] = (byte)'3';
        partialBuffer[4] = (byte)1;
        partialBuffer[8] = (byte)128;
        transform();

        if(chainingStateA != 0x302F7EA23D7FE2E1L ||
                chainingStateB != 0xADE4683A6913752BL ||
                chainingStateC != 0x975CFABEF208AB0AL ||
                chainingStateD != 0x2AF4BA95F831F55BL) {
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
        long keyA = chainingStateA;
        long keyB = chainingStateB;
        long keyC = chainingStateC;
        long keyD = chainingStateD;
        long keyE = keyA ^ keyB ^ keyC ^ keyD ^ 6148914691236517205L;
        long tweakA = modifiersA;
        long tweakB = modifiersB;
        long tweakC = tweakA ^ tweakB;
        long tmp;

        k = 0;
        A += keyA;
        B += (keyB + tweakA);
        C += (keyC + tweakB);
        D += (keyD + k);

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
                A += keyB;
                B += (keyC + tweakB);
                C += (keyD + tweakC);
                D += (keyE + k);
                A += B; B = (B << 26) | (B >>> 38); B ^= A;
                C += D; D = (D << 20) | (D >>> 44); D ^= C;
                A += D; D = (D << 53) | (D >>> 11); D ^= A;
                C += B; B = (B << 35) | (B >>> 29); B ^= C;
                A += B; B = (B << 11) | (B >>> 53); B ^= A;
                C += D; D = (D << 42) | (D >>> 22); D ^= C;
                A += D; D = (D << 59) | (D >>> 5); D ^= A;
                C += B; B = (B << 50) | (B >>> 14); B ^= C;
                k++;
                A += keyC;
                B += (keyD + tweakC);
                C += (keyE + tweakA);
                D += (keyA + k);

                tmp = tweakC;
                tweakC = tweakB;
                tweakB = tweakA;
                tweakA = tmp;

                tmp = keyD;
                keyD = keyA;
                keyA = keyC;
                keyC = keyE;
                keyE = keyB;
                keyB = tmp;
        }

        chainingStateA = feedInput[0] ^ A;
        chainingStateB = feedInput[1] ^ B;
        chainingStateC = feedInput[2] ^ C;
        chainingStateD = feedInput[3] ^ D;

        Arrays.fill(partialBuffer, (byte)0);
        nonzeroFlag = false;
    }

    private void transformZeroes()
    {
        int i;
        int k;

        long A = 0;
        long B = 0;
        long C = 0;
        long D = 0;
        long keyA = chainingStateA;
        long keyB = chainingStateB;
        long keyC = chainingStateC;
        long keyD = chainingStateD;
        long keyE = keyA ^ keyB ^ keyC ^ keyD ^ 6148914691236517205L;
        long tweakA = modifiersA;
        long tweakB = modifiersB;
        long tweakC = tweakA ^ tweakB;
        long tmp;

        k = 0;
        A += keyA;
        B += (keyB + tweakA);
        C += (keyC + tweakB);
        D += (keyD + k);

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
                A += keyB;
                B += (keyC + tweakB);
                C += (keyD + tweakC);
                D += (keyE + k);
                A += B; B = (B << 26) | (B >>> 38); B ^= A;
                C += D; D = (D << 20) | (D >>> 44); D ^= C;
                A += D; D = (D << 53) | (D >>> 11); D ^= A;
                C += B; B = (B << 35) | (B >>> 29); B ^= C;
                A += B; B = (B << 11) | (B >>> 53); B ^= A;
                C += D; D = (D << 42) | (D >>> 22); D ^= C;
                A += D; D = (D << 59) | (D >>> 5); D ^= A;
                C += B; B = (B << 50) | (B >>> 14); B ^= C;
                k++;
                A += keyC;
                B += (keyD + tweakC);
                C += (keyE + tweakA);
                D += (keyA + k);

                tmp = tweakC;
                tweakC = tweakB;
                tweakB = tweakA;
                tweakA = tmp;

                tmp = keyD;
                keyD = keyA;
                keyA = keyC;
                keyC = keyE;
                keyE = keyB;
                keyB = tmp;
        }

        chainingStateA = A;
        chainingStateB = B;
        chainingStateC = C;
        chainingStateD = D;
    }

    private void collapse(boolean fini)
    {
        boolean init = (dataSoFar == 0);
        dataSoFar += partialBufferFill;
        partialBufferFill = 0;
        modifiersB = 0x3000000000000000L;
        modifiersA = dataSoFar;
        if(init)
            modifiersB |= 0x4000000000000000L;
        if(fini)
            modifiersB |= 0x8000000000000000L;
        if(nonzeroFlag)
            transform();
        else
            transformZeroes();
    }

    private void outputTransform()
    {
        modifiersB = 0xFF00000000000000L;
        modifiersA = 0x0000000000000008L;
        transformZeroes();
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
                nonzeroFlag = true;
            } else {
                System.arraycopy(buffer, start, partialBuffer, partialBufferFill, BUFFER - partialBufferFill);
                start += (BUFFER - partialBufferFill);
                length2 -= (BUFFER - partialBufferFill);
                partialBufferFill = BUFFER;
                nonzeroFlag = true;
            }
        }
    }

    public void addZeroes(int length2)
    {
        while(length2 > 0) {
            if(partialBufferFill == BUFFER) {
                collapse(false);
            }
            if(partialBufferFill + length2 <= BUFFER) {
                if(nonzeroFlag)
                    Arrays.fill(partialBuffer, partialBufferFill, partialBufferFill + length2, (byte)0);
                partialBufferFill += length2;
                length2 = 0;
            } else {
                if(nonzeroFlag)
                    Arrays.fill(partialBuffer, partialBufferFill, BUFFER, (byte)0);
                length2 -= (BUFFER - partialBufferFill);
                partialBufferFill = BUFFER;
            }
        }
    }

    //Trashes state.
    public ImageID getID()
    {
        collapse(true);       //This is done anyway even for 0-byte input.
        outputTransform();
        byte[] output = new byte[16];
        for(int i = 0; i < 8; i++) {
            output[i] = (byte)((chainingStateA >>> (8 * (i % 8))));
            output[i + 8] = (byte)((chainingStateB >>> (8 * (i % 8))));
        }
        return new ImageID(output);
    }

    public static void main(String[] args)
    {
        byte[] zeroes = new byte[1024];
        final int ITERATIONS = 131072;

        long time = System.currentTimeMillis();

        DiskIDAlgorithm calc = new DiskIDAlgorithm();
        for(int i = 0; i < ITERATIONS; i++)
            calc.addBuffer(zeroes);

        long time2 = System.currentTimeMillis();
        System.err.println("Answer " + calc.getID() + " calculated in " +
            (time2 - time) + "ms.");

        time = System.currentTimeMillis();

        calc = new DiskIDAlgorithm();
        for(int i = 0; i < ITERATIONS; i++)
            calc.addZeroes(1024);

        time2 = System.currentTimeMillis();
        System.err.println("Answer " + calc.getID() + " calculated in " +
            (time2 - time) + "ms.");
    }

    public static ImageID computeIDForDisk(BaseImage image) throws IOException
    {
        byte[] header;
        byte[] sector = new byte[BaseImage.SECTOR_SIZE];
        int tracks = image.getTracks();
        int sides = image.getSides();
        int sectors = image.getSectors();
        DiskIDAlgorithm h = new DiskIDAlgorithm();
        BaseImage.Type t = image.getType();
        switch(t) {
        case FLOPPY:
            header = new byte[4];
            header[0] = 0;  //Floppy.
            header[1] = (byte)((((tracks - 1) >> 8) & 3) | (((sides - 1) & 15) << 2));
            header[2] = (byte)(((tracks - 1) & 255));
            header[3] = (byte)(((sectors - 1) & 255));
            h.addBuffer(header);
            break;
        case HARDDRIVE:
            header = new byte[4];
            header[0] = 1;  //HDD
            header[1] = (byte)((((tracks - 1) >> 8) & 3) | (((sides - 1) & 15) << 2));
            header[2] = (byte)(((tracks - 1) & 255));
            header[3] = (byte)(((sectors - 1) & 255));
            h.addBuffer(header);
            break;
        case CDROM:
            header = new byte[1];
            header[0] = 2;  //CDROM
            h.addBuffer(header);
            break;
        case BIOS:
            header = new byte[1];
            header[0] = 3;  //BIOS
            h.addBuffer(header);
            break;
        }
        long tSectors = image.getTotalSectors();
        for(long i = 0; i < tSectors; i++) {
            image.read(i, sector, 1);
            h.addBuffer(sector, 0, (t == BaseImage.Type.BIOS) ? 1 : BaseImage.SECTOR_SIZE);
        }
        return h.getID();
    }
}
