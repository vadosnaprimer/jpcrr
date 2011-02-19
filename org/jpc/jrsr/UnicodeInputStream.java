package org.jpc.jrsr;

import java.io.*;

public interface UnicodeInputStream
{
    public void close() throws IOException;
    public int read() throws IOException;
    public int read(int[] b) throws IOException;
    public int read(int[] b, int off, int len) throws IOException;
    public long skip(long n) throws IOException;
    public String readLine() throws IOException;
}
