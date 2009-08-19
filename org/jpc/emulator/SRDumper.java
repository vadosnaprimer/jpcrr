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
import java.nio.*;
import java.nio.charset.*;
import java.lang.reflect.*;

public class SRDumper
{
    public static final byte TYPE_BOOLEAN = 1;
    public static final byte TYPE_BYTE = 2;
    public static final byte TYPE_SHORT = 3;
    public static final byte TYPE_INT = 4;
    public static final byte TYPE_LONG = 5;
    public static final byte TYPE_STRING = 6;
    public static final byte TYPE_BOOLEAN_ARRAY = 7;
    public static final byte TYPE_BYTE_ARRAY = 8;
    public static final byte TYPE_SHORT_ARRAY = 9;
    public static final byte TYPE_INT_ARRAY = 10;
    public static final byte TYPE_LONG_ARRAY = 11;
    public static final byte TYPE_DOUBLE_ARRAY = 12;
    public static final byte TYPE_OBJECT = 13;
    public static final byte TYPE_OBJECT_START = 14;
    public static final byte TYPE_OBJECT_END = 15;
    public static final byte TYPE_SPECIAL_OBJECT = 16;
    public static final byte TYPE_OBJECT_NOT_PRESENT = 19;

    DataOutput underlyingOutput;
    int nextObjectNumber;
    static final Boolean FALSE;
    static final Boolean TRUE;
    private java.util.Stack<Integer> objectStack;
    java.util.HashMap<Integer, Boolean> seenObjects;
    java.util.HashMap<Integer, ObjectListEntry> chainingLists;
    java.util.HashSet<String> constructors;
    int objectsCount;

    public void writeConstructorManifest(OutputStream out) throws IOException
    {
        for(String clazz : constructors) {
            ByteBuffer buf;
            try {
                buf = Charset.forName("UTF-8").newEncoder().encode(CharBuffer.wrap(clazz));
            } catch(CharacterCodingException e) {
                throw new IOException("WTF??? UTF-8 can't encode String???");
            }
            byte[] buf2 = new byte[buf.remaining()];
            buf.get(buf2);
            if(buf2.length > 1024)
                throw new IOException("Class name length of 1024 bytes exceeded");
            out.write(buf2);
            out.write(10);
        }
    }

    static String interpretType(byte id)
    {
        switch(id) {
        case TYPE_BOOLEAN:
            return "boolean";
        case TYPE_BYTE:
            return "byte";
        case TYPE_SHORT:
            return "short";
        case TYPE_INT:
            return "int";
        case TYPE_LONG:
            return "long";
        case TYPE_STRING:
            return "String";
        case TYPE_BOOLEAN_ARRAY:
            return "boolean[]";
        case TYPE_BYTE_ARRAY:
            return "byte[]";
        case TYPE_SHORT_ARRAY:
            return "short[]";
        case TYPE_INT_ARRAY:
            return "int[]";
        case TYPE_LONG_ARRAY:
            return "long[]";
        case TYPE_DOUBLE_ARRAY:
            return "double[]";
        case TYPE_OBJECT:
            return "<object>";
        case TYPE_OBJECT_START:
            return "<object start>";
        case TYPE_OBJECT_END:
            return "<object end>";
        case TYPE_SPECIAL_OBJECT:
            return "<special object>";
        case TYPE_OBJECT_NOT_PRESENT:
            return "<object not present>";
        default:
            return "<unknown type " + ((int)id & 0xFF) + ">";
        }
    }

    static void expect(DataInput in, byte id, int num) throws IOException
    {
        byte id2 = in.readByte();
        if(id != id2) {
            throw new IOException("Dumper/Loader fucked up, expected " + interpretType(id) + ", got " +
                interpretType(id2) + " in tag #" + num + ".");
        }
    }

    static class ObjectListEntry
    {
        public Object object;
        public int num;
        public ObjectListEntry next;
    }

    static
    {
        FALSE = new Boolean(false);
        TRUE = new Boolean(true);
    }

    public SRDumper(DataOutput ps)
    {
        nextObjectNumber = 0;
        underlyingOutput = ps;
        seenObjects = new java.util.HashMap<Integer, Boolean>();
        chainingLists = new java.util.HashMap<Integer, ObjectListEntry>();
        objectStack = new java.util.Stack<Integer>();
        objectsCount = 0;
        constructors = new java.util.HashSet<String>();
    }

    public void dumpBoolean(boolean x) throws IOException
    {
        underlyingOutput.writeByte(TYPE_BOOLEAN);
        underlyingOutput.writeBoolean(x);
    }

    public void dumpByte(byte x) throws IOException
    {
        underlyingOutput.writeByte(TYPE_BYTE);
        underlyingOutput.writeByte(x);
    }

    public void dumpShort(short x) throws IOException
    {
        underlyingOutput.writeByte(TYPE_SHORT);
        underlyingOutput.writeShort(x);
    }

    public void dumpInt(int x) throws IOException
    {
        underlyingOutput.writeByte(TYPE_INT);
        underlyingOutput.writeInt(x);
    }

    public void dumpLong(long x) throws IOException
    {
        underlyingOutput.writeByte(TYPE_LONG);
        underlyingOutput.writeLong(x);
    }

    public void dumpString(String x) throws IOException
    {
        underlyingOutput.writeByte(TYPE_STRING);
        if(x != null) {
            underlyingOutput.writeBoolean(true);
            underlyingOutput.writeUTF(x);
        } else
            underlyingOutput.writeBoolean(false);
    }

    public void dumpArray(boolean[] x) throws IOException
    {
        underlyingOutput.writeByte(TYPE_BOOLEAN_ARRAY);
        if(x != null) {
            underlyingOutput.writeBoolean(true);
            underlyingOutput.writeInt(x.length);
            for(int i = 0; i < x.length; i++)
                underlyingOutput.writeBoolean(x[i]);
        } else
            underlyingOutput.writeBoolean(false);
    }

    public void dumpArray(byte[] x) throws IOException
    {
        underlyingOutput.writeByte(TYPE_BYTE_ARRAY);
        if(x != null) {
            underlyingOutput.writeBoolean(true);
            underlyingOutput.writeInt(x.length);
            underlyingOutput.write(x);
        } else
            underlyingOutput.writeBoolean(false);
    }

    public void dumpArray(short[] x) throws IOException
    {
        underlyingOutput.writeByte(TYPE_SHORT_ARRAY);
        if(x != null) {
            underlyingOutput.writeBoolean(true);
            underlyingOutput.writeInt(x.length);
            for(int i = 0; i < x.length; i++)
                underlyingOutput.writeShort(x[i]);
        } else
            underlyingOutput.writeBoolean(false);
    }

    public void dumpArray(int[] x) throws IOException
    {
        underlyingOutput.writeByte(TYPE_INT_ARRAY);
        if(x != null) {
            underlyingOutput.writeBoolean(true);
            underlyingOutput.writeInt(x.length);
            for(int i = 0; i < x.length; i++)
                underlyingOutput.writeInt(x[i]);
        } else
            underlyingOutput.writeBoolean(false);
    }

    public void dumpArray(long[] x) throws IOException
    {
        underlyingOutput.writeByte(TYPE_LONG_ARRAY);
        if(x != null) {
            underlyingOutput.writeBoolean(true);
            underlyingOutput.writeInt(x.length);
            for(int i = 0; i < x.length; i++)
                underlyingOutput.writeLong(x[i]);
        } else
            underlyingOutput.writeBoolean(false);
    }

    public void dumpArray(double[] x) throws IOException
    {
        underlyingOutput.writeByte(TYPE_DOUBLE_ARRAY);
        if(x != null) {
            underlyingOutput.writeBoolean(true);
            underlyingOutput.writeInt(x.length);
            for(int i = 0; i < x.length; i++)
                underlyingOutput.writeDouble(x[i]);
        } else
            underlyingOutput.writeBoolean(false);
    }

    public void dumpObject(SRDumpable o) throws IOException
    {
        underlyingOutput.writeByte(TYPE_OBJECT);
        dumpInt(objectNumber(o));
        if(o != null) {
            builtinDumpSR(o);
        }
    }

    public void specialObject(SRDumpable o) throws IOException
    {
        int assigned = objectNumber(o);
        underlyingOutput.writeByte(TYPE_SPECIAL_OBJECT);
        dumpInt(assigned);
        seenObjects.put(new Integer(assigned), TRUE);   //Special objects are always considered dumped.
    }

    private void builtinDumpSR(SRDumpable obj) throws IOException
    {
        Class<?> clazz = obj.getClass();
        Method methodObject;

        try {
            methodObject = clazz.getMethod("dumpSRPartial", getClass());
        } catch(Exception e) {
            throw new IOException("Can't find dumper function in \"" + clazz.getName() + "\": " + e);
        }

        try {
            if(dumped(obj))
                return;
            methodObject.invoke(obj, this);
            endObject();
        } catch(IllegalAccessException e) {
            throw new IOException("Can't invoke dumper of \"" + clazz.getName() + "\"): " + e);
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
            throw new IOException("Unknown exception while invoking dumper: " + e2);
        }
    }

    public int dumpedObjects()
    {
        return objectsCount;
    }

    private void addObject(Object O, int n)
    {
        Integer hcode = new Integer(O.hashCode());
        ObjectListEntry e = new ObjectListEntry();
        e.object = O;
        e.num = n;
        e.next = null;
        if(!chainingLists.containsKey(hcode)) {
            chainingLists.put(hcode, e);
        } else {
            e.next = chainingLists.get(hcode);
            chainingLists.put(hcode, e);
        }
    }

    private int lookupObject(Object O)
    {
        Integer hcode = new Integer(O.hashCode());
        if(!chainingLists.containsKey(hcode))
            return -1;
        ObjectListEntry e = chainingLists.get(hcode);
        while(e != null) {
            if(e.object == O)
                return e.num;
            e = e.next;
        }
        return -1;
    }

    public int objectNumber(Object O)
    {
        int assignedNum;
        boolean isNew = false;

        if(O == null)
            return -1;

        assignedNum = lookupObject(O);
        if(assignedNum == -1)
            isNew = true;

        if(isNew) {
            assignedNum = nextObjectNumber++;
            addObject(O, assignedNum);
            seenObjects.put(new Integer(assignedNum), FALSE);
        }
        return assignedNum;
    }

    public boolean dumped(Object O) throws IOException
    {
        Boolean seenBefore = FALSE;
        Integer obj = new Integer(objectNumber(O));

        seenBefore = seenObjects.get(obj);
        if(seenBefore == FALSE) {
            seenObjects.put(obj, TRUE);
            objectsCount++;
            underlyingOutput.writeByte(TYPE_OBJECT_START);
            dumpString(O.getClass().getName());
            constructors.add(O.getClass().getName());
            objectStack.push(obj);
            return false;
        } else {
            underlyingOutput.writeByte(TYPE_OBJECT_NOT_PRESENT);
            return true;
        }
    }

    public void endObject() throws IOException
    {
        Integer obj = objectStack.pop();
        seenObjects.put(obj, TRUE);
        underlyingOutput.writeByte(TYPE_OBJECT_END);
    }
}
