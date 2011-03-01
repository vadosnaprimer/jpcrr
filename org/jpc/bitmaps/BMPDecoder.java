package org.jpc.bitmaps;

import java.io.*;

class BMPDecoder
{
    private static class BMPHeader
    {
        long bmpFileSize;
        long bmpDataOffset;
        long bmpInfoSize;
        long bmpVersion;
        long bmpWidth;
        long bmpHeight;
        long bmpStride;
        int bmpPlanes;
        int bmpDepth;
        long bmpCompression;
        long bmpImageSize;
        long bmpPaletteColors;
        int bmpRedMask;
        int bmpGreenMask;
        int bmpBlueMask;
        int bmpAlphaMask;
        int bmpRedSize;
        int bmpRedShift;
        int bmpGreenSize;
        int bmpGreenShift;
        int bmpBlueSize;
        int bmpBlueShift;
        int bmpAlphaSize;
        int bmpAlphaShift;
        boolean topDown;
        int[] bmpPalette;
        //These are all the important headers.

        int sizeOf(long mask)
        {
            mask &= 0xFFFFFFFFL;
            mask >>>= shiftOf(mask);
            int size = 0;
            while(mask > 0) {
                size++;
                mask >>>= 1;
            }
            return size;
        }

        int shiftOf(long mask)
        {
            mask &= 0xFFFFFFFFL;
            int shift = 0;
            while(mask != 0 && (mask & 1) == 0) {
                shift++;
                mask >>>= 1;
            }
            return shift;
        }

        static long read4(byte[] x, int offset, boolean signed)
        {
            if(!signed) {
                return ((long)x[offset] & 0xFF) |
                    (((long)x[offset + 1] & 0xFF) << 8) |
                    (((long)x[offset + 2] & 0xFF) << 16) |
                    (((long)x[offset + 3] & 0xFF) << 24);
            } else {
                return (((int)x[offset] & 0xFF)) |
                    (((int)x[offset + 1] & 0xFF) << 8) |
                    (((int)x[offset + 2] & 0xFF) << 16) |
                    (((int)x[offset + 3] & 0xFF) << 24);
            }
        }

        static int read2(byte[] x, int offset, boolean signed)
        {
            if(!signed) {
                return (((int)x[offset] & 0xFF)) |
                    (((int)x[offset + 1] & 0xFF) << 8);
            } else {
                return (((short)x[offset] & 0xFF)) |
                    (((short)x[offset + 1] & 0xFF) << 8);
            }
        }

        static int read3(byte[] x, int offset)
        {
            return (((int)x[offset] & 0xFF)) |
                (((int)x[offset + 1] & 0xFF) << 8) |
                (((int)x[offset + 2] & 0xFF) << 16);
        }

        BMPHeader(RandomAccessFile bmp) throws IOException
        {
            byte[] hdr = new byte[18];
            bmp.readFully(hdr);
            bmpFileSize = read4(hdr, 2, false);
            bmpDataOffset = read4(hdr, 10, false);
            bmpInfoSize = read4(hdr, 14, false);
            byte[] ihdr = new byte[(int)bmpInfoSize];
            System.arraycopy(hdr, 14, ihdr, 0, 4);
            bmp.readFully(ihdr, 4, (int)(bmpInfoSize - 4));
            decodeBitmapInfoHeaders(ihdr, (int)bmpInfoSize);
            bmpRedShift = shiftOf(bmpRedMask);
            bmpRedSize = sizeOf(bmpRedMask);
            bmpGreenShift = shiftOf(bmpGreenMask);
            bmpGreenSize = sizeOf(bmpGreenMask);
            bmpBlueShift = shiftOf(bmpBlueMask);
            bmpBlueSize = sizeOf(bmpBlueMask);
            bmpAlphaShift = shiftOf(bmpAlphaMask);
            bmpAlphaSize = sizeOf(bmpAlphaMask);
            if(bmpPalette != null) {
                byte[] p = new byte[bmpPalette.length * 4];
                bmp.readFully(p);
                for(int i = 0; i < bmpPalette.length; i++)
                    bmpPalette[i] = (int)read4(p, 4 * i, false);
            }

            if(bmpDepth >= 8)
                bmpStride = (bmpWidth * ((bmpDepth + 7) / 8) + 3) / 4 * 4;
            else if(bmpDepth == 4)
                bmpStride = ((bmpWidth + 1) / 2 + 3) / 4 * 4;
            else  /* Depth 1 */
                bmpStride = ((bmpWidth + 7) / 8 + 3) / 4 * 4;

        }

        void decodeBitmapInfoMain(byte[] bitmapinfo) throws IOException
        {
            bmpWidth = read4(bitmapinfo, 4, false);
            bmpHeight = read4(bitmapinfo, 8, true);
            if(bmpHeight < 0) {
                bmpHeight = -bmpHeight;
                topDown = true;
            }
            bmpPlanes = read2(bitmapinfo, 12, false);
            bmpDepth = read2(bitmapinfo, 14, false);
            if(bmpDepth != 1 && bmpDepth != 4 && bmpDepth != 8 && bmpDepth != 16 && bmpDepth != 24 &&
                bmpDepth != 32)
                throw new IOException("Unsupported BMP bpp " + bmpDepth + " for BMP v1+");
            bmpCompression = read4(bitmapinfo, 16, false);
            bmpImageSize = read4(bitmapinfo, 20, false);
            if(bmpCompression == 0 && bmpImageSize == 0)
                if(bmpDepth >= 8)
                    bmpImageSize = bmpHeight * (bmpWidth * ((bmpDepth + 7) / 8) + 3) / 4 * 4;
                else if(bmpDepth == 4)
                    bmpImageSize = bmpHeight * ((bmpWidth + 1) / 2 + 3) / 4 * 4;
                else  /* Depth 1 */
                    bmpImageSize = bmpHeight * ((bmpWidth + 7) / 8 + 3) / 4 * 4;
            bmpPaletteColors = read4(bitmapinfo, 32, false);
            if(bmpPaletteColors == 0 && bmpDepth <= 8)
                    bmpPaletteColors = (1 << bmpDepth);
        }

        void decodeBitmapInfoHeaders(byte[] bitmapinfo, int size) throws IOException
        {
            switch(size) {
            case 12:  //Core header.
                bmpWidth = read2(bitmapinfo, 4, false);
                bmpHeight = read2(bitmapinfo, 6, false);
                bmpPlanes = read2(bitmapinfo, 10, false);
                bmpDepth = read2(bitmapinfo, 12, false);
                if(bmpDepth != 1 && bmpDepth != 4 && bmpDepth != 8 && bmpDepth != 24)
                    throw new IOException("Unsupported BMP bpp " + bmpDepth + " for BMP v0");
                bmpVersion = 0;
                bmpCompression = 0;  //No compression.
                if(bmpDepth >= 8)
                    bmpImageSize = bmpHeight * (bmpWidth * ((bmpDepth + 7) / 8) + 3) / 4 * 4;
                else if(bmpDepth == 4)
                    bmpImageSize = bmpHeight * ((bmpWidth + 1) / 2 + 3) / 4 * 4;
                else  /* Depth 1 */
                    bmpImageSize = bmpHeight * ((bmpWidth + 7) / 8 + 3) / 4 * 4;
                if(bmpDepth <= 8)
                    bmpPaletteColors = (1 << bmpDepth);
                if(bmpDepth == 24) {
                    bmpRedMask = 0xFF0000;
                    bmpGreenMask = 0xFF00;
                    bmpBlueMask = 0xFF;
                }
                if(bmpDepth <= 8)
                    bmpPalette = new int[(int)bmpPaletteColors];
                return;
            case 40:  //V1
                bmpVersion = 1;
                decodeBitmapInfoMain(bitmapinfo);
                if(bmpCompression != 0)
                    throw new IOException("Unsupported BMP compression " + bmpCompression + " for BMP v1");
                if(bmpDepth == 16) {  //This is really 15 bits.
                    bmpRedMask = 0x7C00;
                    bmpGreenMask = 0x3E0;
                    bmpBlueMask = 0x1F;
                }
                if(bmpDepth == 24) {
                    bmpRedMask = 0xFF0000;
                    bmpGreenMask = 0xFF00;
                    bmpBlueMask = 0xFF;
                }
                if(bmpDepth == 32) {
                    bmpRedMask = 0xFF0000;
                    bmpGreenMask = 0xFF00;
                    bmpBlueMask = 0xFF;
                    bmpAlphaMask = 0xFF000000;
                }
                if(bmpDepth <= 8)
                    bmpPalette = new int[(int)bmpPaletteColors];
                return;
            case 52:  //V2
            case 56:  //V3
            case 108:  //V4
            case 124:  //V5
                bmpVersion = (size > 52) ? 3 : 2;
                decodeBitmapInfoMain(bitmapinfo);
                bmpRedMask = (int)read4(bitmapinfo, 40, false);
                bmpGreenMask = (int)read4(bitmapinfo, 44, false);
                bmpBlueMask = (int)read4(bitmapinfo, 48, false);
                if(bmpVersion >= 3)
                    bmpAlphaMask = (int)read4(bitmapinfo, 52, false);
                if(bmpCompression != 0 && bmpCompression != 3)
                    throw new IOException("Unsupported BMP compression " + bmpCompression + " for BMP v2+");
                if(bmpCompression == 0 && bmpDepth == 16) {  //This is really 15 bits.
                    bmpRedMask = 0x7C00;
                    bmpGreenMask = 0x3E0;
                    bmpBlueMask = 0x1F;
                }
                if(bmpCompression == 0 && bmpDepth == 24) {
                    bmpRedMask = 0xFF0000;
                    bmpGreenMask = 0xFF00;
                    bmpBlueMask = 0xFF;
                }
                if(bmpCompression == 0 && bmpDepth == 32) {
                    bmpRedMask = 0xFF0000;
                    bmpGreenMask = 0xFF00;
                    bmpBlueMask = 0xFF;
                    bmpAlphaMask = 0xFF000000;
                }
                if(bmpDepth <= 8)
                    bmpPalette = new int[(int)bmpPaletteColors];
                return;
            default:
                throw new IOException("Unknown BMP version (" + size + ")");
            }
        }
    }

    static class BMPImage implements DecodedBitmap
    {
        byte[] r;
        byte[] g;
        byte[] b;
        byte[] a;
        int w;
        int h;

        private BMPImage(int _w, int _h)
        {
            w = _w;
            h = _h;
            r = new byte[w * h];
            g = new byte[w * h];
            b = new byte[w * h];
            a = new byte[w * h];
        }

        public int getW() { return w; }
        public int getH() { return h; }
        public byte[] getR() { return r; }
        public byte[] getG() { return g; }
        public byte[] getB() { return b; }
        public byte[] getA() { return a; }
    }

    static BMPImage decode(RandomAccessFile bmp) throws IOException
    {
        BMPHeader hdr = new BMPHeader(bmp);
        if(hdr.bmpPlanes != 1)
            throw new IOException("Multi-plane BMPs are not supported");
/*
        System.err.println("BMP: Size: " + hdr.bmpFileSize);
        System.err.println("BMP: Data offset: " + hdr.bmpDataOffset);
        System.err.println("BMP: Info Size: " + hdr.bmpInfoSize);
        System.err.println("BMP: Version: " + hdr.bmpVersion);
        System.err.println("BMP: Width: " + hdr.bmpWidth);
        System.err.println("BMP: Height: " + hdr.bmpHeight);
        System.err.println("BMP: Stride: " + hdr.bmpStride);
        System.err.println("BMP: Planes: " + hdr.bmpPlanes);
        System.err.println("BMP: Bit depth: " + hdr.bmpDepth);
        System.err.println("BMP: Compression: " + hdr.bmpCompression);
        System.err.println("BMP: Image Size: " + hdr.bmpImageSize);
        System.err.println("BMP: Palette colors: " + hdr.bmpPaletteColors);
        System.err.println("BMP: Red mask: " + ((long)hdr.bmpRedMask & 0xFFFFFFFFL) + "(" + hdr.bmpRedShift + "/" +
            hdr.bmpRedSize + ")");
        System.err.println("BMP: Green mask: " + ((long)hdr.bmpGreenMask & 0xFFFFFFFFL) + "(" + hdr.bmpGreenShift +
            "/" + hdr.bmpGreenSize + ")");
        System.err.println("BMP: Blue mask: " + ((long)hdr.bmpBlueMask & 0xFFFFFFFFL) + "(" + hdr.bmpBlueShift + "/" +
            hdr.bmpBlueSize + ")");
        System.err.println("BMP: Alpha mask: " + ((long)hdr.bmpAlphaMask & 0xFFFFFFFFL) + "(" + hdr.bmpAlphaShift +
            "/" + hdr.bmpAlphaSize + ")");
        System.err.println("BMP: Topdown: " + hdr.topDown);
        if(hdr.bmpPalette != null)
            for(int i = 0; i < hdr.bmpPalette.length; i++) {
                System.err.println("BMP: Palette entry #" + i + ": " +
                    ((hdr.bmpPalette[i] >>> 16) & 0xFF) + "/" +
                    ((hdr.bmpPalette[i] >>> 8) & 0xFF) + "/" +
                    ((hdr.bmpPalette[i]) & 0xFF));
            }
*/
        BMPImage img = new BMPImage((int)hdr.bmpWidth, (int)hdr.bmpHeight);
        byte[] imgBuf = new byte[(int)hdr.bmpStride];
        bmp.seek(hdr.bmpDataOffset);
        for(int i = 0; i < hdr.bmpHeight; i++) {
            bmp.readFully(imgBuf);
            int scanRow = hdr.topDown ? i : (int)(hdr.bmpHeight - i - 1);
            for(int j = 0; j < hdr.bmpWidth; j++) {
                int c = 0;
                int r, g, b, a;
                switch(hdr.bmpDepth) {
                case 1:
                    c = (((int)imgBuf[j / 8] & 0xFF) >> (7 - j % 8)) & 1;
                    break;
                case 4:
                    if(j % 2 == 0)
                        c = ((int)imgBuf[j / 2] & 0xF0) >> 4;
                    else
                        c = (int)imgBuf[j / 2] & 0xF;
                    break;
                case 8:
                    c = (int)imgBuf[j] & 0xFF;
                    break;
                case 16:
                    c = (int)BMPHeader.read2(imgBuf, 2 * j, false) & 0xFFFF;
                    break;
                case 24:
                    c = (int)BMPHeader.read3(imgBuf, 3 * j);
                    break;
                case 32:
                    c = (int)BMPHeader.read4(imgBuf, 4 * j, false);
                    break;
                }
                if(hdr.bmpPalette != null) {
                    r = ((hdr.bmpPalette[c] >>> 16) & 0xFF);
                    g = ((hdr.bmpPalette[c] >>> 8) & 0xFF);
                    b = (hdr.bmpPalette[c] & 0xFF);
                    a = (byte)255;
                } else {
                    r = ((c >>> hdr.bmpRedShift) & ((1 << hdr.bmpRedSize) - 1)) * 255 / ((1 << hdr.bmpRedSize) - 1);
                    g = ((c >>> hdr.bmpGreenShift) & ((1 << hdr.bmpGreenSize) - 1)) * 255 /
                        ((1 << hdr.bmpGreenSize) - 1);
                    b = ((c >>> hdr.bmpBlueShift) & ((1 << hdr.bmpBlueSize) - 1)) * 255 / ((1 << hdr.bmpBlueSize) - 1);
                    if(hdr.bmpAlphaSize == 0)
                        a = (byte)255;
                    else
                        a = ((c >>> hdr.bmpAlphaShift) & ((1 << hdr.bmpAlphaSize) - 1)) * 255 /
                            ((1 << hdr.bmpAlphaSize) - 1);
                }
                img.r[scanRow * (int)hdr.bmpWidth + j] = (byte)r;
                img.g[scanRow * (int)hdr.bmpWidth + j] = (byte)g;
                img.b[scanRow * (int)hdr.bmpWidth + j] = (byte)b;
                img.a[scanRow * (int)hdr.bmpWidth + j] = (byte)a;
            }
        }
        return img;
    }

    public static void main(String[] args) throws IOException
    {
        decode(new RandomAccessFile(args[0], "r"));
    }
};
