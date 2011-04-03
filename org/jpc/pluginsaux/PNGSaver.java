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

package org.jpc.pluginsaux;
import java.io.*;
import java.util.zip.*;
import static org.jpc.Misc.errorDialog;

public class PNGSaver
{
    private int sequenceNumber;
    private String prefix;
    private final static int NUMBERS = 12;
    private String lastName;

    public PNGSaver(String _prefix)
    {
        prefix = _prefix;
        sequenceNumber = 0;
    }

    public String lastPNGName()
    {
        return lastName;
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
        lastName = prefix + numberToString(sequenceNumber) + ".png";
        PNGSaver.savePNG(lastName, pixelData, width, height);
        sequenceNumber++;
    }

    public void savePNG8(byte[] pixelData, int[] palette, int width, int height) throws IOException
    {
        lastName = prefix + numberToString(sequenceNumber) + ".png";
        PNGSaver.savePNG8(lastName, pixelData, palette, width, height);
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

    public static void savePNG8(String name, byte[] pixelData, int[] palette, int width, int height) throws IOException
    {
        File file = new File(name);
        FileOutputStream stream = new FileOutputStream(file);
        BufferedOutputStream buffered = new BufferedOutputStream(stream);
        DataOutputStream dataOut = new DataOutputStream(buffered);
        PNGSaver.savePNG8(dataOut, pixelData, palette, width, height);
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

    private static void fillAndFlushIHDR(DataOutput out, byte[] ihdrTemplate, int width, int height) throws IOException
    {
        int ihdrType = 0x49484452;
        ihdrTemplate[0] = (byte)((width >>> 24) & 0xFF);
        ihdrTemplate[1] = (byte)((width >>> 16) & 0xFF);
        ihdrTemplate[2] = (byte)((width >>> 8) & 0xFF);
        ihdrTemplate[3] = (byte)((width & 0xFF));
        ihdrTemplate[4] = (byte)((height >>> 24) & 0xFF);
        ihdrTemplate[5] = (byte)((height >>> 16) & 0xFF);
        ihdrTemplate[6] = (byte)((height >>> 8) & 0xFF);
        ihdrTemplate[7] = (byte)((height & 0xFF));
        flushChunk(out, ihdrType, ihdrTemplate, -1);
    }

    private static void fillAndFlushPLTE(DataOutput out, int[] palette) throws IOException
    {
        int plteType = 0x504C5445;
        //Write the PLTE chunk.
        byte[] plte = new byte[768];
        for(int i = 0; i < 256; i++) {
            plte[3 * i + 0] = (byte)((palette[i] >> 16) & 0xFF);
            plte[3 * i + 1] = (byte)((palette[i] >> 8) & 0xFF);
            plte[3 * i + 2] = (byte)(palette[i] & 0xFF);
        }
        flushChunk(out, plteType, plte, -1);
    }

    private static void flushIEND(DataOutput out) throws IOException
    {
        int iendType = 0x49454E44;
        flushChunk(out, iendType, null, -1);
    }

    private static void fillAndFlushIDAT(DataOutput out, byte[] data1, int[] data2, int width, int height)
        throws IOException
    {
        int idatType = 0x49444154;
        Deflater deflate = new Deflater();
        int compressedChunkLen = 32768;
        int tempBufferLen = 10000;
        byte[] tempBuffer = new byte[tempBufferLen];
        byte[] compressed = new byte[compressedChunkLen];
        int compressedFill = 0;
        int pixelIterator = 0;
        int outputSize = 0;
        boolean filterMarker = false;
        boolean finished = false;

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
                         if(data2 != null) {
                             tempBuffer[tempFill++] = (byte)((data2[pixelIterator] >> 16) & 0xFF);
                             tempBuffer[tempFill++] = (byte)((data2[pixelIterator] >> 8) & 0xFF);
                             tempBuffer[tempFill++] = (byte)((data2[pixelIterator]) & 0xFF);
                         } else {
                             tempBuffer[tempFill++] = data1[pixelIterator];
                         }
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

    }


    public static void savePNG(DataOutput out, int[] pixelData, int width, int height) throws IOException
    {
        byte[] pngMagic = new byte[]{-119, 80, 78, 71, 13, 10, 26, 10};
        int idatType = 0x49444154;
        byte[] ihdrContent = new byte[]{25, 25, 25, 25, 25, 25, 25, 25, 8, 2, 0, 0, 0};

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

        fillAndFlushIHDR(out, ihdrContent, width, height);
        fillAndFlushIDAT(out, null, pixelData, width, height);
        flushIEND(out);
    }

    public static void savePNG8(DataOutput out, byte[] pixelData, int[] palette, int width, int height)
        throws IOException
    {
        byte[] pngMagic = new byte[]{-119, 80, 78, 71, 13, 10, 26, 10};
        int idatType = 0x49444154;
        int compressedChunkLen = 32768;
        int tempBufferLen = 10000;
        byte[] tempBuffer = new byte[tempBufferLen];
        byte[] ihdrContent = new byte[]{25, 25, 25, 25, 25, 25, 25, 25, 8, 3, 0, 0, 0};
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
            pixelData = new byte[width * height];
        }

        fillAndFlushIHDR(out, ihdrContent, width, height);
        fillAndFlushPLTE(out, palette);
        fillAndFlushIDAT(out, pixelData, null, width, height);
        flushIEND(out);
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
            errorDialog(e, "Failed to save test PNG", null, "Quit");
        }
        return;
    }
}
