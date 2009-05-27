/*
    JPC: A x86 PC Hardware Emulator for a pure Java Virtual Machine
    Release Version 2.0

    A project from the Physics Dept, The University of Oxford

    Copyright (C) 2007 Isis Innovation Limited

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

public class FileBackedSeekableIODevice implements SeekableIODevice
{
    private String fileName;
    private RandomAccessFile image;

    private boolean readOnly;

    public void dumpStatusPartial(org.jpc.support.StatusDumper output)
    {
        if(fileName != null)
            output.println("\tfileName \"" + fileName + "\"");
        else
            output.println("\tfileName <null>");
    }
 
    public void dumpStatus(org.jpc.support.StatusDumper output)
    {
        if(output.dumped(this))
            return;

        output.println("#" + output.objectNumber(this) + ": FileBackedSeekableIODevice:");
        dumpStatusPartial(output);
        output.endObject();
    }

    public FileBackedSeekableIODevice()
    {
    }

    public void dumpSR(org.jpc.support.SRDumper output) throws IOException
    {
        if(output.dumped(this))
            return;
        dumpSRPartial(output);
        output.endObject();
    }

    public void dumpSRPartial(org.jpc.support.SRDumper output) throws IOException
    {
        output.dumpString(fileName);
    }

    public FileBackedSeekableIODevice(org.jpc.support.SRLoader input) throws IOException
    {
        input.objectCreated(this);
        fileName = null;
        image = null;
        configure(input.loadString());
    }

    public static org.jpc.SRDumpable loadSR(org.jpc.support.SRLoader input, Integer id) throws IOException
    {
        org.jpc.SRDumpable x = new FileBackedSeekableIODevice(input);
        input.endObject();
        return x;
    }

    public void configure(String spec)
    {
        fileName = spec;

        try
        {
            image = new RandomAccessFile(spec, "rw");
            readOnly = false;
        }
        catch (IOException first)
        {
            try
            {
                image = new RandomAccessFile(spec, "r");
                readOnly = true;
                System.err.println("Opened " + spec + " as read only");
            }
            catch (IOException last)
            {
                System.err.println("Failed Opening Floppy Image");
                image = null;
                readOnly = false;
            }
        }
    }

    public FileBackedSeekableIODevice(String file)
    {
       fileName = file;

        try
        {
            image = new RandomAccessFile(file, "rw");
            readOnly = false;
        }
        catch (IOException first)
        {
            try
            {
                image = new RandomAccessFile(file, "r");
                readOnly = true;
                System.err.println("Opened " + file + " as read only");
            }
            catch (IOException last)
            {
                System.err.println("Failed Opening Floppy Image");
                image = null;
                readOnly = false;
            }
        }
    }

    public void seek(int offset) throws IOException
    {
        image.seek(offset);
    }

    public int write(byte[] data, int offset, int length) throws IOException
    {
        image.write(data, offset, length);
        return length;
    }

    public int read(byte[] data, int offset, int length) throws IOException
    {
        return image.read(data, offset, length);
    }

    public int length()
    {
        try
        {
            return (int) image.length();
        }
        catch (Exception e)
        {
            return -1;
        }
    }

    public boolean readOnly()
    {
        return readOnly;
    }

    public String toString()
    {
        return fileName;
    }
}
