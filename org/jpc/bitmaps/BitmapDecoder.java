package org.jpc.bitmaps;

import java.io.*;

public class BitmapDecoder
{
    public static DecodedBitmap decode(String filename) throws IOException
    {
        RandomAccessFile f = new RandomAccessFile(filename, "r");
        byte[] hdr = new byte[4];
        f.readFully(hdr);
        f.seek(0);
        if(hdr[0] == 0x42 && hdr[1] == 0x4D) {
            return BMPDecoder.decode(f);
        }
        throw new IOException("Unsupported image type");
    }

    public static void main(String[] args) throws IOException
    {
        decode(args[0]);
    }
}
