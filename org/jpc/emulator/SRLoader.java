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

package org.jpc.emulator;

import java.io.*;
import java.util.*;
import java.lang.reflect.*;

import org.jpc.jrsr.UTFInputLineStream;

public final class SRLoader
{
    private InputStream underlyingInput;
    private SRDumpable[] objects;
    private int pendingObject;
    private long lastMsgTimestamp;
    private long objectNum;
    private long extLoads, intLoads;
    private static final int INITIAL_OBJECT_CAP = 16384;
    private static final int BUFFER_MAXSIZE = 4096;  //MUST BE MULTIPLE OF 8!
    private int bufferFill;
    private int bufferStart;
    private boolean bufferEOF;
    private byte[] buffer;
    int lastSuccess;
    int opNum;

    public SRLoader(InputStream di)
    {
        underlyingInput = di;
        objects = new SRDumpable[INITIAL_OBJECT_CAP];
        pendingObject = -1;
        opNum = 0;
        bufferFill = 0;
        bufferStart = 0;
        bufferEOF = false;
        buffer = new byte[BUFFER_MAXSIZE];
        lastSuccess = 0;
    }

    public void ensureBufferFill(int minFill) throws IOException
    {
        if(minFill > BUFFER_MAXSIZE)
            throw new IllegalStateException("ensureBufferFill: Buffer overflow.");
        else if(minFill <= bufferFill)
            return;
        else {
            if(bufferFill > 0)
                System.arraycopy(buffer, bufferStart, buffer, 0, bufferFill);
            bufferStart = 0;
            while(minFill > bufferFill && !bufferEOF) {
                int r = underlyingInput.read(buffer, bufferFill, BUFFER_MAXSIZE - bufferFill);
                if(r < 0)
                    bufferEOF = true;
                else
                    bufferFill += r;
            }
        }
        if(bufferFill < minFill && bufferEOF)
            throw new IllegalStateException("ensureBufferFill: Buffer overrun.");
    }

    private static String interpretType(byte id)
    {
        switch(id) {
        case SRDumper.TYPE_BOOLEAN:
            return "boolean";
        case SRDumper.TYPE_BYTE:
            return "byte";
        case SRDumper.TYPE_SHORT:
            return "short";
        case SRDumper.TYPE_INT:
            return "int";
        case SRDumper.TYPE_LONG:
            return "long";
        case SRDumper.TYPE_STRING:
            return "String";
        case SRDumper.TYPE_BOOLEAN_ARRAY:
            return "boolean[]";
        case SRDumper.TYPE_BYTE_ARRAY:
            return "byte[]";
        case SRDumper.TYPE_SHORT_ARRAY:
            return "short[]";
        case SRDumper.TYPE_INT_ARRAY:
            return "int[]";
        case SRDumper.TYPE_LONG_ARRAY:
            return "long[]";
        case SRDumper.TYPE_DOUBLE_ARRAY:
            return "double[]";
        case SRDumper.TYPE_OBJECT:
            return "<object>";
        case SRDumper.TYPE_OBJECT_START:
            return "<object start>";
        case SRDumper.TYPE_OBJECT_END:
            return "<object end>";
        case SRDumper.TYPE_SPECIAL_OBJECT:
            return "<special object>";
        case SRDumper.TYPE_OBJECT_NOT_PRESENT:
            return "<object not present>";
        default:
            return "<unknown type " + ((int)id & 0xFF) + ">";
        }
    }

    private void expect(byte id, int num) throws IOException
    {
        byte id2 = buffer[bufferStart++]; bufferFill--;
        if(id != id2) {
            System.err.println("Last parsed: " + interpretType((byte)lastSuccess) + ".");
            throw new IOException("Dumper/Loader fucked up, expected " + interpretType(id) + ", got " +
                interpretType(id2) + " in tag #" + num + ".");
        }
        lastSuccess = id2;
    }

    public boolean loadBoolean() throws IOException
    {
        ensureBufferFill(2);
        expect(SRDumper.TYPE_BOOLEAN, opNum++);
        byte id2 = buffer[bufferStart++]; bufferFill--;
        return (id2 != 0);
    }

    public byte loadByte() throws IOException
    {
        ensureBufferFill(2);
        expect(SRDumper.TYPE_BYTE, opNum++);
        byte id2 = buffer[bufferStart++]; bufferFill--;
        return id2;
    }

    public short loadShort() throws IOException
    {
        ensureBufferFill(3);
        expect(SRDumper.TYPE_SHORT, opNum++);
        return readShort(false);
    }

    public int loadInt() throws IOException
    {
        ensureBufferFill(5);
        expect(SRDumper.TYPE_INT, opNum++);
        return readInt(false);
    }

    public long loadLong() throws IOException
    {
        ensureBufferFill(9);
        expect(SRDumper.TYPE_LONG, opNum++);
        return readLong(false);
    }

    public String loadString() throws IOException
    {
        ensureBufferFill(2);
        expect(SRDumper.TYPE_STRING, opNum++);
        byte present = buffer[bufferStart++]; bufferFill--;
        if(present != 0) {
            int length = ((int)readShort(true) & 0xFFFF);
            StringBuffer o = new StringBuffer(length);
            int writeIndex = 0;
            int readIndex = 0;
            int promisedTo = 0;
            while(readIndex < length) {
                if(promisedTo < length &&  bufferFill < BUFFER_MAXSIZE) {
                    if(length - readIndex > BUFFER_MAXSIZE) {
                        ensureBufferFill(BUFFER_MAXSIZE);
                        promisedTo = readIndex + BUFFER_MAXSIZE;
                    }
                    else {
                        ensureBufferFill(length - readIndex);
                        promisedTo = length;
                    }
                }
                int byte1 = (int)buffer[bufferStart++] & 0xFF; bufferFill--;
                if(byte1 < 128) {
                    o.append((char)byte1);
                    readIndex++;
                } else if(byte1 < 224) {
                    byte1 &= 0x1F;
                    int byte2 = (int)buffer[bufferStart++] & 0x3F; bufferFill--;
                    o.append((char)((byte1 << 6) + byte2));
                    readIndex += 2;
                } else {
                    byte1 &= 0x0F;
                    int byte2 = (int)buffer[bufferStart++] & 0x3F; bufferFill--;
                    int byte3 = (int)buffer[bufferStart++] & 0x3F; bufferFill--;
                    o.append((char)((byte1 << 12) + (byte2 << 6) + byte3));
                    readIndex += 3;
                }
            }
            return o.toString();
        } else
            return null;
    }

    private short readShort(boolean ensure) throws IOException
    {
        if(ensure)
            ensureBufferFill(4);
        short id2 = (short)((short)buffer[bufferStart++] & 0xFF); bufferFill--;
        short id3 = (short)((short)buffer[bufferStart++] & 0xFF); bufferFill--;
        return (short)((id2 << 8) + id3);
    }

    private int readInt(boolean ensure) throws IOException
    {
        if(ensure)
            ensureBufferFill(4);
        int id2 = (int)buffer[bufferStart++] & 0xFF; bufferFill--;
        int id3 = (int)buffer[bufferStart++] & 0xFF; bufferFill--;
        int id4 = (int)buffer[bufferStart++] & 0xFF; bufferFill--;
        int id5 = (int)buffer[bufferStart++] & 0xFF; bufferFill--;
        int v = (id2 * 16777216 + id3 * 65536 + id4 * 256 + id5);
        return v;
    }

    private long readLong(boolean ensure) throws IOException
    {
        if(ensure)
            ensureBufferFill(8);
        long id2 = (long)buffer[bufferStart++] & 0xFF; bufferFill--;
        long id3 = (long)buffer[bufferStart++] & 0xFF; bufferFill--;
        long id4 = (long)buffer[bufferStart++] & 0xFF; bufferFill--;
        long id5 = (long)buffer[bufferStart++] & 0xFF; bufferFill--;
        long id6 = (long)buffer[bufferStart++] & 0xFF; bufferFill--;
        long id7 = (long)buffer[bufferStart++] & 0xFF; bufferFill--;
        long id8 = (long)buffer[bufferStart++] & 0xFF; bufferFill--;
        long id9 = (long)buffer[bufferStart++] & 0xFF; bufferFill--;
        return ((id2 * 16777216 + id3 * 65536 + id4 * 256 + id5) << 32) +
		(id6 * 16777216 + id7 * 65536 + id8 * 256 + id9);
    }

    public boolean[] loadArrayBoolean() throws IOException
    {
        ensureBufferFill(2);
        expect(SRDumper.TYPE_BOOLEAN_ARRAY, opNum++);
        byte present = buffer[bufferStart++]; bufferFill--;
        if(present != 0) {
            boolean[] x = new boolean[readInt(true)];
            for(int i = 0; i < x.length; i++) {
                if(bufferFill < 1)
                    ensureBufferFill(1);
                int id2 = buffer[bufferStart++]; bufferFill--;
                x[i] = (id2 != 0);
            }
            return x;
        } else
            return null;
    }

    public byte[] loadArrayByte() throws IOException
    {
        ensureBufferFill(2);
        expect(SRDumper.TYPE_BYTE_ARRAY, opNum++);
        byte present = buffer[bufferStart++]; bufferFill--;
        if(present != 0) {
            byte[] x = new byte[readInt(true)];
            int remaining = x.length;
            int index = 0;
            while(remaining > 0) {
                int tocopy = remaining;
                if(tocopy > BUFFER_MAXSIZE)
                    tocopy = BUFFER_MAXSIZE;
                 ensureBufferFill(tocopy);
                 System.arraycopy(buffer, bufferStart, x, index, tocopy);
                 bufferStart += tocopy;
                 bufferFill -= tocopy;
                 remaining -= tocopy;
                 index += tocopy;
            }
            return x;
        } else
            return null;
    }

    public short[] loadArrayShort() throws IOException
    {
        ensureBufferFill(2);
        expect(SRDumper.TYPE_SHORT_ARRAY, opNum++);
        byte present = buffer[bufferStart++]; bufferFill--;
        if(present != 0) {
            short[] x = new short[readInt(true)];
            int remaining = x.length;
            int index = 0;
            while(remaining > 0) {
                int tocopy = remaining;
                if(tocopy > BUFFER_MAXSIZE / 2)
                    tocopy = BUFFER_MAXSIZE / 2;
                 ensureBufferFill(2 * tocopy);
                 for(int i = 0; i < tocopy; i++) {
                     x[index + i] = readShort(false);
                 }
                 remaining -= tocopy;
                 index += tocopy;
            }
            return x;
        } else
            return null;
    }

    public int[] loadArrayInt() throws IOException
    {
        ensureBufferFill(2);
        expect(SRDumper.TYPE_INT_ARRAY, opNum++);
        byte present = buffer[bufferStart++]; bufferFill--;
        if(present != 0) {
            int[] x = new int[readInt(true)];
            int remaining = x.length;
            int index = 0;
            while(remaining > 0) {
                int tocopy = remaining;
                if(tocopy > BUFFER_MAXSIZE / 4)
                    tocopy = BUFFER_MAXSIZE / 4;
                 ensureBufferFill(4 * tocopy);
                 for(int i = 0; i < tocopy; i++) {
                     x[index + i] = readInt(false);
                 }
                 remaining -= tocopy;
                 index += tocopy;
            }
            return x;
        } else
            return null;
    }

    public long[] loadArrayLong() throws IOException
    {
        ensureBufferFill(2);
        expect(SRDumper.TYPE_LONG_ARRAY, opNum++);
        byte present = buffer[bufferStart++]; bufferFill--;
        if(present != 0) {
            long[] x = new long[readInt(true)];
            int remaining = x.length;
            int index = 0;
            while(remaining > 0) {
                int tocopy = remaining;
                if(tocopy > BUFFER_MAXSIZE / 8)
                    tocopy = BUFFER_MAXSIZE / 8;
                 ensureBufferFill(8 * tocopy);
                 for(int i = 0; i < tocopy; i++) {
                     x[index + i] = readLong(false);
                 }
                 remaining -= tocopy;
                 index += tocopy;
            }
            return x;
        } else
            return null;
    }

    public double[] loadArrayDouble() throws IOException
    {
        ensureBufferFill(2);
        expect(SRDumper.TYPE_DOUBLE_ARRAY, opNum++);
        byte present = buffer[bufferStart++]; bufferFill--;
        if(present != 0) {
            double[] x = new double[readInt(true)];
            int remaining = x.length;
            int index = 0;
            while(remaining > 0) {
                int tocopy = remaining;
                if(tocopy > BUFFER_MAXSIZE / 8)
                    tocopy = BUFFER_MAXSIZE / 8;
                 ensureBufferFill(8 * tocopy);
                 for(int i = 0; i < tocopy; i++) {
                     x[index + i] = Double.longBitsToDouble(readLong(false));
                 }
                 remaining -= tocopy;
                 index += tocopy;
            }
            return x;
        } else
            return null;
    }

    public static boolean checkConstructorManifest(InputStream in) throws IOException
    {
        Class<?> classObject;
        UTFInputLineStream lines = new UTFInputLineStream(in);
        String clazz = lines.readLine();
        while(clazz != null) {
            try {
                classObject = Class.forName(clazz);
                if(!SRDumpable.class.isAssignableFrom(classObject)) {
                    throw new IOException("Invalid class");
                }
            } catch(Exception e) {
                System.err.println("Error: Constructor manifest refers to unknown/invalid class " + clazz + ".");
                return false;
            }
            clazz = lines.readLine();
        }
        return true;
    }

    private SRDumpable builtinObjectLoader(int id, Class<?> clazz) throws IOException
    {
        SRDumpable x;
        Constructor<?> constructorObject = null;

        intLoads++;

        try {
            if(constructorObject == null)
                constructorObject = clazz.getConstructor(getClass());
        } catch(Exception e) {
            throw new IOException("<init>(SRLoader) required for object loading: " + e);
        }

        try {
            pendingObject = id;
            x = (SRDumpable)constructorObject.newInstance(this);
            endObject();
        } catch(IllegalAccessException e) {
            throw new IOException("Can't invoke <init>(SRLoader) of \"" + clazz.getName() + "\": " + e);
        } catch(InvocationTargetException e) {
            Throwable e2 = e.getCause();
            //If the exception is something unchecked, just pass it through.
            if(e2 instanceof RuntimeException)
                throw (RuntimeException)e2;
            if(e2 instanceof Error)
                throw (Error)e2;
            //Also pass IOException through.
            if(e2 instanceof IOException)
                throw (IOException)e2;
            //What the heck is that?
            throw new IOException("Unknown exception while invoking loader: " + e2);
        } catch(InstantiationException e) {
            throw new IOException("Instatiation of class \"" + clazz.getName() + "\" failed:" + e);
        }

        if(objects.length <= id || objects[id] != x) {
            throw new IOException("Wrong object assigned to id #" + id + ".");
        }

        return x;
    }

    private SRDumpable loadObjectContents(int id) throws IOException
    {
        ensureBufferFill(1);
        expect(SRDumper.TYPE_OBJECT_START, opNum++);
        String className = loadString();
        Class<?> classObject;

        try {
            classObject = Class.forName(className);
        } catch(Exception e) {
            throw new IOException("Unknown class \"" + className + "\" encountered:" + e);
        }

        return builtinObjectLoader(id, classObject);
    }

    public void objectCreated(SRDumpable o)
    {
        int id = pendingObject;
        pendingObject = -1;
        if(objects.length > id) {
            objects[id] = o;
        } else if(2 * objects.length > id) {
            SRDumpable[] objects2 = new SRDumpable[2 * objects.length];
            System.arraycopy(objects, 0, objects2, 0, objects.length);
            objects = objects2;
            objects[id] = o;
        } else {
            SRDumpable[] objects2 = new SRDumpable[id + 1];
            System.arraycopy(objects, 0, objects2, 0, objects.length);
            objects = objects2;
            objects[id] = o;
        }
    }

    public SRDumpable loadObject() throws IOException
    {
        ensureBufferFill(1);
        expect(SRDumper.TYPE_OBJECT, opNum++);
        int id = loadInt();
        if(id < 0) {
            return null;
        }
        if(objects.length > id && objects[id] != null) {
            //Seen this before. No object follows.
            ensureBufferFill(1);
            expect(SRDumper.TYPE_OBJECT_NOT_PRESENT, opNum++);
            return objects[id];
        } else {
            //Gotta load this object.
            return loadObjectContents(id);
        }
    }

    public void endObject() throws IOException
    {
        objectNum++;
        ensureBufferFill(1);
        expect(SRDumper.TYPE_OBJECT_END, opNum++);
        long newTimestamp = System.currentTimeMillis();
        if(newTimestamp - lastMsgTimestamp > 1000) {
            System.err.println("Informational: Loaded " + objectNum + " objects, stream sequence number " + opNum + ".");
            System.err.println("Informational: Internal loads: " + intLoads + " external loads: " + extLoads +  ".");
            lastMsgTimestamp = newTimestamp;
        }
    }

    public void specialObject(SRDumpable o) throws IOException
    {
        ensureBufferFill(1);
        expect(SRDumper.TYPE_SPECIAL_OBJECT, opNum++);
        int id = loadInt();
        if(objects.length > id) {
            objects[id] = o;
        } else if(2 * objects.length > id) {
            SRDumpable[] objects2 = new SRDumpable[2 * objects.length];
            System.arraycopy(objects, 0, objects2, 0, objects.length);
            objects = objects2;
            objects[id] = o;
        } else {
            SRDumpable[] objects2 = new SRDumpable[id + 1];
            System.arraycopy(objects, 0, objects2, 0, objects.length);
            objects = objects2;
            objects[id] = o;
        }
    }
}
