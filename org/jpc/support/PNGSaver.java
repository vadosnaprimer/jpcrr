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

package org.jpc.support;
import java.io.*;
import java.util.zip.*;

public class PNGSaver
{
    private int sequenceNumber;
    private String prefix;
    private final static int NUMBERS = 6;

    public PNGSaver(String _prefix)
    {
        prefix = _prefix;
        sequenceNumber = 0;
    }

    public static String numberToString(int number, int pos)
    {
        if(pos == 0)
            return "";
        else
            return numberToString(number / 10, pos - 1) + (number % 10);
    }

    public static String numberToString(int number)
    {
        return numberToString(number, NUMBERS);
    }

    public void savePNG(int[] pixelData, int width, int height) throws IOException
    {
        PNGSaver.savePNG(prefix + numberToString(sequenceNumber) + ".png", pixelData, width, height);
        sequenceNumber++;
    }

    public static void savePNG(String name, int[] pixelData, int width, int height) throws IOException
    {
        File file = new File(name);
        FileOutputStream stream = new FileOutputStream(file);
        BufferedOutputStream buffered = new BufferedOutputStream(stream);
        DataOutputStream dataOut = new DataOutputStream(buffered);
        PNGSaver.savePNG(dataOut, pixelData, width, height);
        buffered.flush();
    }

    private static void flushChunk(DataOutput out, int chunkType, byte[] content, int limitLength) throws IOException
    {
        CRC32 crc;
        byte[] type = new byte[4];
        crc = new CRC32();

        if(limitLength < 0 && content != null)
            limitLength = content.length;

        if(content != null)
            out.writeInt(limitLength);
        else
            out.writeInt(0);
        type[0] = (byte)((chunkType >>> 24) & 0xFF);
        type[1] = (byte)((chunkType >>> 16) & 0xFF);
        type[2] = (byte)((chunkType >>> 8) & 0xFF);
        type[3] = (byte)((chunkType) & 0xFF);
        out.write(type);
        crc.update(type);
        if(content != null) {
            out.write(content, 0, limitLength);
            crc.update(content, 0, limitLength);
        }
        int crcV = (int)crc.getValue();
        out.writeInt(crcV);
    }

    public static void savePNG(DataOutput out, int[] pixelData, int width, int height) throws IOException
    {
        byte[] pngMagic = new byte[]{-119, 80, 78, 71, 13, 10, 26, 10};
        int ihdrType = 0x49484452;
        int iendType = 0x49454E44;
        int idatType = 0x49444154;
        int compressedChunkLen = 32768;
        int tempBufferLen = 10000;
        byte[] tempBuffer = new byte[tempBufferLen];
        byte[] ihdrContent = new byte[]{25, 25, 25, 25, 25, 25, 25, 25, 8, 2, 0, 0, 0};
        byte[] compressed = new byte[compressedChunkLen];
        int compressedFill = 0;
        int pixelIterator = 0;
        int outputSize = 0;
        boolean filterMarker = false;
        boolean finished = false;
        Deflater deflate = new Deflater();

        out.write(pngMagic);

        // Sanity-check the input. It doesn't always appear to be sane.
        if(width == 0)
            width = 720;
        if(height == 0)
            height = 400;
        if(width * height > pixelData.length) {
            System.err.println("Warning: Invalid video input data.");
            pixelData = new int[width * height];
        }

        //Write the IHDR.
        ihdrContent[0] = (byte)((width >>> 24) & 0xFF);
        ihdrContent[1] = (byte)((width >>> 16) & 0xFF);
        ihdrContent[2] = (byte)((width >>> 8) & 0xFF);
        ihdrContent[3] = (byte)((width & 0xFF));
        ihdrContent[4] = (byte)((height >>> 24) & 0xFF);
        ihdrContent[5] = (byte)((height >>> 16) & 0xFF);
        ihdrContent[6] = (byte)((height >>> 8) & 0xFF);
        ihdrContent[7] = (byte)((height & 0xFF));
        flushChunk(out, ihdrType, ihdrContent, -1);

        //Write the IDAT chunk(s).
        while(!deflate.finished()) {
            if(deflate.needsInput()) {
                if(pixelIterator == width * height) {
                    if(!finished) {
                        deflate.finish();
                    }
                    finished = true;
                } else {
                     int tempFill = 0;
                     while(tempFill < tempBufferLen && pixelIterator < width * height) {
                         if(pixelIterator % width == 0 && !filterMarker) {
                             tempBuffer[tempFill++] = 0;    //No filtering.
                             filterMarker = true;
                         }
                         if(tempFill > tempBufferLen - 3)
                             break;                         //Doesn't fit.
                         filterMarker = false;
                         tempBuffer[tempFill++] = (byte)((pixelData[pixelIterator] >> 16) & 0xFF);
                         tempBuffer[tempFill++] = (byte)((pixelData[pixelIterator] >> 8) & 0xFF);
                         tempBuffer[tempFill++] = (byte)((pixelData[pixelIterator]) & 0xFF);
                         pixelIterator++;
                     }
                     outputSize += tempFill;
                     deflate.setInput(tempBuffer, 0, tempFill);
                }
            }
            if(compressedFill == compressedChunkLen) {
                //Flush IDAT.
                flushChunk(out, idatType, compressed, -1);
                compressedFill = 0;
            } else {
                compressedFill += deflate.deflate(compressed, compressedFill, compressedChunkLen - compressedFill);
            }
        }
        if(compressedFill > 0) {
            //Write one final IDAT.
            flushChunk(out, idatType, compressed, compressedFill);
            compressedFill = 0;
        }

        //Write the IEND.
        flushChunk(out, iendType, null, -1);
    }

    //main function for testing.
    public static void main(String[] args)
    {
        int[] array = new int[65536 * 3];
        for(int i = 0; i < 65536; i++) {
            array[i] = 65536 * (i / 256) + 256 * (i % 256);
        }
        for(int i = 0; i < 65536; i++) {
            array[i + 65536] = 256 * (i / 256) + (i % 256);
        }
        for(int i = 0; i < 65536; i++) {
            array[i + 131072] = 65536 * (i / 256) + (i % 256);
        }
        try {
            PNGSaver.savePNG("test.png", array, 256, 256);
        } catch(Exception e) {
            e.printStackTrace();
        }
        return;
    }
}
