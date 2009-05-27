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

package org.jpc.emulator.pci;
import java.io.*;

public class ByteBuffer implements org.jpc.SRDumpable
{
    private byte[] buffer;

    public void dumpStatusPartial(org.jpc.support.StatusDumper output)
    {
        output.printArray(buffer, "buffer");
    }
 
    public void dumpStatus(org.jpc.support.StatusDumper output)
    {
        if(output.dumped(this))
            return;

        output.println("#" + output.objectNumber(this) + ": ByteBuffer:");
        dumpStatusPartial(output);
        output.endObject();
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
        output.dumpArray(buffer);
    }

    public static org.jpc.SRDumpable loadSR(org.jpc.support.SRLoader input, Integer id) throws IOException
    {
        org.jpc.SRDumpable x = new ByteBuffer(input);
        input.endObject();
        return x;
    }

    public ByteBuffer(org.jpc.support.SRLoader input) throws IOException
    {
        input.objectCreated(this);
        buffer = input.loadArrayByte();
    }

    public ByteBuffer(int size)
    {
        buffer = new byte[size];
        for (int i=0; i<buffer.length; i++)
            buffer[i] = 0;
    }

    public byte get(int index)
    {
        return buffer[index];
    }

    public void set(int index, byte value)
    {
        buffer[index] = value;
    }

    public int size()
    {
        return buffer.length;
    }

    public short getShort(int index)
    {
        int result = 0;
        result |= 0xFF & buffer[index++];
        result |= 0xFF00 & (buffer[index++] << 8);
        return (short) result;
    }

    public void setShort(int index, short value)
    {
        buffer[index++] = (byte) value;
        buffer[index] = (byte) (value >>> 8);
    }

    public int getInt(int index)
    {
        int result = 0;
        result |= 0xFF & buffer[index++];
        result |= 0xFF00 & (buffer[index++] << 8);
        result |= 0xFF0000 & (buffer[index++] << 16);
        result |= 0xFF000000 & (buffer[index++] << 24);
        return result;
    }

    public void setInt(int index, int value)
    {
        buffer[index++] = (byte) value;
        buffer[index++] = (byte) (value >>> 8);
        buffer[index++] = (byte) (value >>> 16);
        buffer[index++] = (byte) (value >>> 24);
    }

    public long getLong(int index)
    {
        long result = 0;
        result |= 0xFF & buffer[index++];
        result |= 0xFF00 & (buffer[index++] << 8);
        result |= 0xFF0000 & (buffer[index++] << 16);
        result |= 0xFF000000l & (((long) buffer[index++]) << 24);
        result |= 0xFF00000000l & (((long) buffer[index++]) << 32);
        result |= 0xFF0000000000l & (((long) buffer[index++]) << 40);
        result |= 0xFF000000000000l & (((long) buffer[index++]) << 48);
        result |= 0xFF00000000000000l & (((long) buffer[index++]) << 56);
        return result;
    }

    public void setLong(int index, long value)
    {
        buffer[index++] = (byte) value;
        buffer[index++] = (byte) (value >>> 8);
        buffer[index++] = (byte) (value >>> 16);
        buffer[index++] = (byte) (value >>> 24);
        buffer[index++] = (byte) (value >>> 32);
        buffer[index++] = (byte) (value >>> 40);
        buffer[index++] = (byte) (value >>> 48);
        buffer[index++] = (byte) (value >>> 56);
    }

    public int get(int index, byte[] target, int offset, int length)
    {
        length = Math.min(length, buffer.length - index);
        length = Math.min(length, target.length - offset);
        System.arraycopy(buffer, index, target, offset, length);
        return length;
    }

    public int set(byte[] src, int offset, int index, int length)
    {
        length = Math.min(length, buffer.length - index);
        length = Math.min(length, src.length - offset);
        System.arraycopy(src, offset, buffer, index, length);
        return length;
    }

    public static void fillIntArray(int[] tgt, int value)
    {
        for (int i=0; i<tgt.length; i++)
            tgt[i] = value;
    }

    public static void fillByteArray(byte[] tgt, byte value)
    {
        for (int i=0; i<tgt.length; i++)
            tgt[i] = value;
    }

    public static void fillShortArray(short[] tgt, short value)
    {
        for (int i=0; i<tgt.length; i++)
            tgt[i] = value;
    }

    public static void fillLongArray(long[] tgt, long value)
    {
        for (int i=0; i<tgt.length; i++)
            tgt[i] = value;
    }
}
