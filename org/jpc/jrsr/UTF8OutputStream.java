package org.jpc.jrsr;

import java.io.*;
import static org.jpc.Misc.isLinefeed;
import org.jpc.Misc;


public class UTF8OutputStream implements UnicodeOutputStream
{
    private boolean closed;
    private boolean unstrip;
    private boolean atLineStart;
    private OutputStream underlying;
    private UTF8StreamEncoder encoder;

    public UTF8OutputStream(OutputStream out, boolean _unstrip)
    {
        encoder = new UTF8StreamEncoder();
        unstrip = _unstrip;
        underlying = out;
        atLineStart = true;
    }

    public void close() throws IOException
    {
        if(closed)
            return;
        flush();
        underlying.close();
        closed = true;
    }

    public void flush() throws IOException
    {
        if(closed)
            throw new IOException("Trying to operate on closed stream");
        underlying.flush();
    }

    public void write(int cp) throws IOException
    {
        int[] x;
        if(unstrip && isLinefeed(cp)) {
            x = new int[2];
            x[1] = 43;
        } else
            x = new int[1];
        x[0] = cp;
        underlying.write(encoder.encode(x));
    }
    public void write(int[] b) throws IOException
    {
        write(b, 0, b.length);
    }

    public void write(int[] b, int off, int len) throws IOException
    {
        int codepoints = len;
        boolean tmp = atLineStart;
        for(int i = off; i < off + len; i++) {
            if(tmp && unstrip)
                codepoints++;
            tmp = isLinefeed(b[i]);
        }
        int[] buf = new int[codepoints];
        int writePosition = 0;
        for(int i = off; i < off + len; i++) {
            int x = b[i];
            if(atLineStart && unstrip)
                buf[writePosition++] = 43;  //+
            buf[writePosition++] = x;
            atLineStart = isLinefeed(x);
        }
        underlying.write(encoder.encode(buf));
    }

    public void writeLine(String line) throws IOException
    {
        int codepoints = 0;
        int len = line.length();
        boolean tmp = atLineStart;
        for(int i = 0; i < len; i++) {
            int x = line.codePointAt(i);
            if(tmp && unstrip)
                codepoints++;
            if(x > 0xFFFF)
                i++;
            codepoints++;
            tmp = isLinefeed(x);
        }
        int[] buf = new int[codepoints + 1];
        int writePosition = 0;
        for(int i = 0; i < len; i++) {
            int x = line.codePointAt(i);
            if(x > 0xFFFF)
                i++;
            if(atLineStart && unstrip)
                buf[writePosition++] = 43;  //+
            buf[writePosition++] = x;
            atLineStart = isLinefeed(x);
        }
        buf[writePosition++] = 10; //LF.
        underlying.write(encoder.encode(buf));
        atLineStart = true;
    }

    public void encodeLine(Object... line) throws IOException
    {
        if(closed)
            throw new IOException("Trying to operate on closed stream");
        String[] line2 = new String[line.length];
        for(int i = 0; i < line.length; i++)
            if(line[i] != null)
                line2[i] = line[i].toString();
            else
                return;
        this.writeLine(Misc.encodeLine(line2));
    }
}
