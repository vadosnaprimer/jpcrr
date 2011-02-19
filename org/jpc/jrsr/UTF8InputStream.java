package org.jpc.jrsr;

import java.io.*;
import static org.jpc.Misc.isLinefeed;

public class UTF8InputStream implements UnicodeInputStream
{
    private boolean closed2;
    private int[] buffer;
    private int bufferStart;
    private int bufferFill;
    private UTF8StreamDecoder decoder;
    private InputStream in;
    private boolean strip;

    public UTF8InputStream(InputStream _in, boolean _strip) throws IOException
    {
        in = _in;
        decoder = new UTF8StreamDecoder();
        strip = _strip;
        eatLineFeeds();  //Get the initial line start.
    }

    private boolean readBuffer() throws IOException
    {
        while(bufferStart > 0 || bufferFill < 1) {
            byte[] rawBuf = new byte[2048];
            int r = in.read(rawBuf);
            if(r < 0) {
                decoder.sendEOF();
                return false; //No more.
            }
            buffer = decoder.decode(rawBuf, 0, r);
            bufferStart = 0;
            bufferFill = buffer.length;
        }

        return true;
    }

    //Eat the line feeds and the following '+' character (if not EOF)
    private void eatLineFeeds() throws IOException
    {
        if(!strip) {
            return;
        }
        while(true) {
            if(bufferStart == bufferFill && !readBuffer())
                return;
            if(!isLinefeed(buffer[bufferStart])) {
                if(buffer[bufferStart] != 43)
                    throw new IOException("Parsing JRSR member, expected <43>, got <" + buffer[bufferStart] + ">");
                bufferStart++;
                return;
            }
            bufferStart++;
        }
    }

    public long skip(long n) throws IOException
    {
        if(closed2)
            throw new IOException("Trying to read closed stream");
        long skipped = 0;
        while(n > 0) {
            if(bufferStart == bufferFill && !readBuffer())
                break;
            int r = buffer[bufferStart++];
            if(isLinefeed(r))
                eatLineFeeds();
            skipped++;
        }
        return skipped;
    }

    public int read() throws IOException
    {
        if(closed2)
            throw new IOException("Trying to read closed stream");
        if(bufferStart == bufferFill && !readBuffer())
            return -1;
        int r;
        if(isLinefeed(r = buffer[bufferStart++]))
            eatLineFeeds();
        return r;
    }

    public int read(int[] rBuffer, int offset, int len) throws IOException
    {
        if(closed2)
            throw new IOException("Trying to read closed stream");
        long aRead = offset;
        while(len > 0) {
            if(bufferStart == bufferFill && !readBuffer())
                break;
            if(isLinefeed(rBuffer[(int)(aRead++)] = buffer[bufferStart++]))
                eatLineFeeds();
            len--;
        }

        return (int)aRead;
    }

    public int read(int[] buffer) throws IOException
    {
        return this.read(buffer, 0, buffer.length);
    }

    public void close() throws IOException
    {
        closed2 = true;
        in.close();
    }

    public String readLine() throws IOException
    {
        if(closed2)
            throw new IOException("Trying to operate on closed stream");

        StringBuilder buf = new StringBuilder();
        boolean added = false;
        if(bufferStart == bufferFill && !readBuffer())
            return null;

        while(true) {
            if(bufferStart == bufferFill && !readBuffer()) {
                if(!added)
                    return null;
                return buf.toString();
            }
            int lfpos = bufferStart;
            while(lfpos < bufferFill && !isLinefeed(buffer[lfpos]))
                lfpos++;
            for(int i = bufferStart; i < lfpos; i++) {
                added = true;
                int j = buffer[i];
                if(j <= 0xFFFF)
                    buf.append((char)j);
                else {
                    buf.append((char)(0xD800 + j & 0x3FF));
                    buf.append((char)(0xDC00 + (j >> 10) - 64));
                }
            }
            if(lfpos < bufferFill) {
                bufferStart = lfpos + 1;
                eatLineFeeds();
                return buf.toString();
            }
            bufferStart = bufferFill;
        }
    }

}
