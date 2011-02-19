package org.jpc.jrsr;

import java.io.*;

public interface UnicodeOutputStream
{
    public void close() throws IOException;
    public void flush() throws IOException;
    public void write(int cp) throws IOException;
    public void write(int[] b) throws IOException;
    public void write(int[] b, int off, int len) throws IOException;
    public void writeLine(String line) throws IOException;
    public void encodeLine(Object... line) throws IOException;
}
