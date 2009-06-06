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

package org.jpc.support;

import org.jpc.emulator.memory.*;
import java.io.*;

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
    public static final byte TYPE_OUTER_OBJECT = 17;
    public static final byte TYPE_INNER_ELIDE = 18;

    DataOutput underlyingOutput;
    int nextObjectNumber;
    static final Integer NOT_SEEN;
    static final Integer DUMPING;
    static final Integer DUMPED;
    private java.util.Stack objectStack;
    java.util.HashMap seenObjects;
    java.util.HashMap chainingLists;
    int objectsCount;

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
        case TYPE_OUTER_OBJECT:
            return "<outer object>";
        case TYPE_INNER_ELIDE:
            return "<inner elide>";
        default:
            return "<unknown type>";
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
        NOT_SEEN = new Integer(0);
        DUMPING = new Integer(1);
        DUMPED = new Integer(2);
    }

    public SRDumper(DataOutput ps)
    {
        nextObjectNumber = 0;
        underlyingOutput = ps;
        seenObjects = new java.util.HashMap();
        chainingLists = new java.util.HashMap();
        objectStack = new java.util.Stack();
        objectsCount = 0;
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

    public void dumpObject(org.jpc.SRDumpable o) throws IOException
    {
        underlyingOutput.writeByte(TYPE_OBJECT);
        dumpInt(objectNumber(o));
        if(o != null) {
            o.dumpSR(this);
        }
    }

    public void specialObject(org.jpc.SRDumpable o) throws IOException
    {
        int assigned = objectNumber(o);
        underlyingOutput.writeByte(TYPE_SPECIAL_OBJECT);
        dumpInt(assigned);
        seenObjects.put(new Integer(assigned), DUMPED);   //Special objects are always considered dumped.
    }

    public boolean dumpOuter(org.jpc.SRDumpable o, org.jpc.SRDumpable inner) throws IOException
    {
        Integer innerID = new Integer(objectNumber(inner));

        //The outer object may wind up dumping the inner one too. Due to ordering constraints,
        //we need to dump the object in outermost class in scope chain.
        if(seenObjects.containsKey(innerID) && seenObjects.get(innerID) == DUMPING)
            seenObjects.put(innerID, NOT_SEEN);

        underlyingOutput.writeByte(TYPE_OUTER_OBJECT);
        dumpInt(objectNumber(o));
        if(o != null) {
            o.dumpSR(this);
        }
        if(seenObjects.containsKey(innerID) && seenObjects.get(innerID) == DUMPED) {
            //System.err.println("Performing inner elide on object #" + innerID.intValue() + ".");
            underlyingOutput.writeByte(TYPE_INNER_ELIDE);
            return false;
        } else
            return true;
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
            e.next = (ObjectListEntry)(chainingLists.get(hcode));
            chainingLists.put(hcode, e);
        }
    }

    private int lookupObject(Object O)
    {
        Integer hcode = new Integer(O.hashCode());
        if(!chainingLists.containsKey(hcode))
            return -1;
        ObjectListEntry e = (ObjectListEntry)(chainingLists.get(hcode));
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
            seenObjects.put(new Integer(assignedNum), NOT_SEEN);
        }
        return assignedNum;
    }

    public boolean dumped(Object O) throws IOException
    {
        return dumped(O, O.getClass().getName(), "loadSR");
    }

    public boolean dumped(Object O, String overrideName, String overrideConstructor) throws IOException
    {
        Integer seenBefore = NOT_SEEN;
        Integer obj = new Integer(objectNumber(O));

        seenBefore = (Integer)seenObjects.get(obj);
        if(seenBefore == NOT_SEEN) {
            seenObjects.put(obj, DUMPING);
            objectsCount++;
            underlyingOutput.writeByte(TYPE_OBJECT_START);
            dumpString(overrideName);
            dumpString(overrideConstructor);
            //System.err.println("Saving object #" + obj.intValue() + " <" + overrideName + "/" + overrideConstructor + ">.");
            objectStack.push(obj);
            return false;
        } else {
            //System.err.println("Referencing object #" + obj.intValue() + " <" + overrideName + "/" + overrideConstructor + ">.");
            return true;
        }
    }

    public void endObject() throws IOException
    {
        Integer obj = (Integer)(objectStack.pop());
        seenObjects.put(obj, DUMPED);
        underlyingOutput.writeByte(TYPE_OBJECT_END);
    }
}
