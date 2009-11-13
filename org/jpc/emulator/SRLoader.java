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

public class SRLoader
{
    private DataInput underlyingInput;
    private SRDumpable[] objects;
    private int pendingObject;
    private long lastMsgTimestamp;
    private long objectNum;
    private long extLoads, intLoads;
    private static final int INITIAL_OBJECT_CAP = 16384;
    int opNum;
    boolean entryInStack;

    public SRLoader(DataInput di)
    {
        underlyingInput = di;
        objects = new SRDumpable[INITIAL_OBJECT_CAP];
        pendingObject = -1;
        opNum = 0;
        entryInStack = false;
    }

    public boolean loadBoolean() throws IOException
    {
        SRDumper.expect(underlyingInput, SRDumper.TYPE_BOOLEAN, opNum++);
        return underlyingInput.readBoolean();
    }

    public byte loadByte() throws IOException
    {
        SRDumper.expect(underlyingInput, SRDumper.TYPE_BYTE, opNum++);
        return underlyingInput.readByte();
    }

    public short loadShort() throws IOException
    {
        SRDumper.expect(underlyingInput, SRDumper.TYPE_SHORT, opNum++);
        return underlyingInput.readShort();
    }

    public int loadInt() throws IOException
    {
        SRDumper.expect(underlyingInput, SRDumper.TYPE_INT, opNum++);
        return underlyingInput.readInt();
    }

    public long loadLong() throws IOException
    {
        SRDumper.expect(underlyingInput, SRDumper.TYPE_LONG, opNum++);
        return underlyingInput.readLong();
    }

    public String loadString() throws IOException
    {
        SRDumper.expect(underlyingInput, SRDumper.TYPE_STRING, opNum++);
        boolean present = underlyingInput.readBoolean();
        if(present)
            return underlyingInput.readUTF();
        else
            return null;
    }

    public boolean[] loadArrayBoolean() throws IOException
    {
        SRDumper.expect(underlyingInput, SRDumper.TYPE_BOOLEAN_ARRAY, opNum++);
        boolean present = underlyingInput.readBoolean();
        if(present) {
            boolean[] x = new boolean[underlyingInput.readInt()];
            for(int i = 0; i < x.length; i++)
                x[i] = underlyingInput.readBoolean();
            return x;
        } else
            return null;
    }

    public byte[] loadArrayByte() throws IOException
    {
        SRDumper.expect(underlyingInput, SRDumper.TYPE_BYTE_ARRAY, opNum++);
        boolean present = underlyingInput.readBoolean();
        if(present) {
            byte[] x = new byte[underlyingInput.readInt()];
            underlyingInput.readFully(x);
            return x;
        } else
            return null;
    }

    public short[] loadArrayShort() throws IOException
    {
        SRDumper.expect(underlyingInput, SRDumper.TYPE_SHORT_ARRAY, opNum++);
        boolean present = underlyingInput.readBoolean();
        if(present) {
            short[] x = new short[underlyingInput.readInt()];
            for(int i = 0; i < x.length; i++)
                x[i] = underlyingInput.readShort();
            return x;
        } else
            return null;
    }

    public int[] loadArrayInt() throws IOException
    {
        SRDumper.expect(underlyingInput, SRDumper.TYPE_INT_ARRAY, opNum++);
        boolean present = underlyingInput.readBoolean();
        if(present) {
            int[] x = new int[underlyingInput.readInt()];
            for(int i = 0; i < x.length; i++)
                x[i] = underlyingInput.readInt();
            return x;
        } else
            return null;
    }

    public long[] loadArrayLong() throws IOException
    {
        SRDumper.expect(underlyingInput, SRDumper.TYPE_LONG_ARRAY, opNum++);
        boolean present = underlyingInput.readBoolean();
        if(present) {
            long[] x = new long[underlyingInput.readInt()];
            for(int i = 0; i < x.length; i++)
                x[i] = underlyingInput.readLong();
            return x;
        } else
            return null;
    }

    public double[] loadArrayDouble() throws IOException
    {
        SRDumper.expect(underlyingInput, SRDumper.TYPE_DOUBLE_ARRAY, opNum++);
        boolean present = underlyingInput.readBoolean();
        if(present) {
            double[] x = new double[underlyingInput.readInt()];
            for(int i = 0; i < x.length; i++)
                x[i] = underlyingInput.readDouble();
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
        SRDumper.expect(underlyingInput, SRDumper.TYPE_OBJECT_START, opNum++);
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
        SRDumper.expect(underlyingInput, SRDumper.TYPE_OBJECT, opNum++);
        int id = loadInt();
        if(id < 0)
            return null;
        if(objects.length > id && objects[id] != null) {
            //Seen this before. No object follows.
            SRDumper.expect(underlyingInput, SRDumper.TYPE_OBJECT_NOT_PRESENT, opNum++);
            return objects[id];
        } else {
            //Gotta load this object.
            return loadObjectContents(id);
        }
    }

    public void endObject() throws IOException
    {
        objectNum++;
        SRDumper.expect(underlyingInput, SRDumper.TYPE_OBJECT_END, opNum++);
        long newTimestamp = System.currentTimeMillis();
        if(newTimestamp - lastMsgTimestamp > 1000) {
            System.err.println("Informational: Loaded " + objectNum + " objects, stream sequence number " + opNum + ".");
            System.err.println("Informational: Internal loads: " + intLoads + " external loads: " + extLoads +  ".");
            lastMsgTimestamp = newTimestamp;
        }
    }

    public void specialObject(SRDumpable o) throws IOException
    {
        SRDumper.expect(underlyingInput, SRDumper.TYPE_SPECIAL_OBJECT, opNum++);
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
