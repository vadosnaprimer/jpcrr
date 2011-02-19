package org.jpc.jrsr;

import java.io.*;

public class UTF8StreamEncoder
{
    byte[] encode(int[] utf8) throws IOException
    {
        return encode(utf8, 0, utf8.length);
    }

    byte[] encode(int[] utf8, int off, int len) throws IOException
    {
        int needed = 0;
        for(int i = off; i < off + len; i++) {
            int x = utf8[i];
            if((x >= 0xD800 && x <= 0xDFFF) || (x >>> 16) > 16 || (x & 0xFFFE) == 0xFFFE)
                throw new IOException("Trying to encode an invalid character!");
            if(x > 0xFFFF)
                needed++;
            if(x > 0x7FF)
                needed++;
            if(x > 0x7F)
                needed++;
            needed++;
        }
        byte[] ret = new byte[needed];
        int optr = 0;
        for(int i = off; i < off + len; i++) {
            int x = utf8[i];
            if(x > 0xFFFF) {
                ret[optr++] = (byte)((x >>> 18) - 16);
                ret[optr++] = (byte)(((x >>> 12) & 0x3F) - 128);
                ret[optr++] = (byte)(((x >>> 6) & 0x3F) - 128);
                ret[optr++] = (byte)((x & 0x3F) - 128);
            } else if(x > 0x7FF) {
                ret[optr++] = (byte)((x >>> 12) - 32);
                ret[optr++] = (byte)(((x >>> 6) & 0x3F) - 128);
                ret[optr++] = (byte)((x & 0x3F) - 128);
            } else if(x > 0x7F) {
                ret[optr++] = (byte)((x >>> 6) - 64);
                ret[optr++] = (byte)((x & 0x3F) - 128);
            } else {
                ret[optr++] = (byte)x;
            }
        }
        return ret;
    }
}
