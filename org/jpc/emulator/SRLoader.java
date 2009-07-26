/*
    JPC: A x86 PC Hardware Emulator for a pure Java Virtual Machine
    Release Version 2.0

    A project from the Physics Dept, The University of Oxford

    Copyright (C) 2007 Isis Innovation Limited
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

    Details (including contact information) can be found at:

    www.physics.ox.ac.uk/jpc
*/

package org.jpc.emulator;

import java.io.*;
import java.util.*;
import java.lang.reflect.*;

public class SRLoader
{
    private DataInput underlyingInput;
    private HashMap<Integer, org.jpc.SRDumpable> objects;
    private Stack<Integer> tmpStack;
    private long lastMsgTimestamp;
    private long objectNum;
    private long extLoads, intLoads;
    int opNum;

    public SRLoader(DataInput di)
    {
        underlyingInput = di;
        objects = new HashMap<Integer, org.jpc.SRDumpable>();
        tmpStack = new Stack<Integer>();
        opNum = 0;
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

    public static boolean checkConstructorManifest(DataInput in) throws IOException
    {
        Class<?> classObject;
        Class<Integer> intClass = Integer.class;
        boolean cf = in.readBoolean();
        while(cf) {
            String clazz = in.readUTF();

            try {
                classObject = Class.forName(clazz);
                if(!org.jpc.SRDumpable.class.isAssignableFrom(classObject)) {
                    throw new IOException("Invalid class");
                }
            } catch(Exception e) {
                System.err.println("Error: Constructor manifest refers to unknown/invalid class " + clazz + ".");
                return false;
            }
            cf = in.readBoolean();
        }
        return true;
    }

    private org.jpc.SRDumpable builtinObjectLoader(Integer id, Class<?> clazz) throws IOException
    {
        org.jpc.SRDumpable x;
        org.jpc.SRDumpable y;
        Constructor constructorObject = null;

        intLoads++;

        try {
            if(constructorObject == null)
                constructorObject = clazz.getConstructor(getClass());
        } catch(Exception e) {
            throw new IOException("<init>(SRLoader) required for object loading: " + e);
        }

        try {
            tmpStack.push(id);
            x = (org.jpc.SRDumpable)constructorObject.newInstance(this);
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

        if(!objects.containsKey(id) || objects.get(id) != x) {
            throw new IOException("Wrong object assigned to id #" + id + ".");
        }

        return x;
    }

    private org.jpc.SRDumpable loadObjectContents(Integer id) throws IOException
    {
        org.jpc.SRDumpable x;

        SRDumper.expect(underlyingInput, SRDumper.TYPE_OBJECT_START, opNum++);
        String className = loadString();
        Class<?> classObject;
        Method methodObject;

        try {
            classObject = Class.forName(className);
        } catch(Exception e) {
            throw new IOException("Unknown class \"" + className + "\" encountered:" + e);
        }

        return builtinObjectLoader(id, classObject);
    }

    public void objectCreated(org.jpc.SRDumpable o)
    {
        Integer id = tmpStack.pop();
        objects.put(id, o);
    }

    public org.jpc.SRDumpable loadObject() throws IOException
    {
        SRDumper.expect(underlyingInput, SRDumper.TYPE_OBJECT, opNum++);
        int _id = loadInt();
        Integer id = new Integer(_id);
        if(_id < 0)
            return null;
        if(objects.containsKey(id)) {
            //Seen this before. No object follows.
            SRDumper.expect(underlyingInput, SRDumper.TYPE_OBJECT_NOT_PRESENT, opNum++);
            return objects.get(id);
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

    public void specialObject(org.jpc.SRDumpable o) throws IOException
    {
        SRDumper.expect(underlyingInput, SRDumper.TYPE_SPECIAL_OBJECT, opNum++);
        int id = loadInt();
        objects.put(new Integer(id), o);
    }
}
