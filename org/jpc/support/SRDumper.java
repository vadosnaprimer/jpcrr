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
    DataOutput underlyingOutput;
    int nextObjectNumber;
    static final Boolean TRUE;
    static final Boolean FALSE;
    java.util.HashMap seenObjects;
    java.util.HashMap chainingLists;
    int objectsCount;
    static final long OBJECT_START_MAGIC = 4265863787363385245L;
    static final long OBJECT_END_MAGIC = 6783678257832758327L;

    static class ObjectListEntry
    {
        public Object object;
        public int num;
        public ObjectListEntry next;
    }

    static
    {
        TRUE = new Boolean(true);
        FALSE = new Boolean(false);
    }

    public SRDumper(DataOutput ps)
    {
        nextObjectNumber = 0;
        underlyingOutput = ps;
        seenObjects = new java.util.HashMap();
        chainingLists = new java.util.HashMap();
        objectsCount = 0;
    }

    public void dumpBoolean(boolean x) throws IOException
    {
        underlyingOutput.writeBoolean(x);
    }

    public void dumpByte(byte x) throws IOException
    {
        underlyingOutput.writeByte(x);
    }

    public void dumpShort(short x) throws IOException
    {
        underlyingOutput.writeShort(x);
    }

    public void dumpInt(int x) throws IOException
    {
        underlyingOutput.writeInt(x);
    }

    public void dumpLong(long x) throws IOException
    {
        underlyingOutput.writeLong(x);
    }

    public void dumpString(String x) throws IOException
    {
        if(x != null) {
            underlyingOutput.writeBoolean(true);
            underlyingOutput.writeUTF(x);
        } else
            underlyingOutput.writeBoolean(false);
    }

    public void dumpArray(boolean[] x) throws IOException
    {
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
        if(x != null) {
            underlyingOutput.writeBoolean(true);
            underlyingOutput.writeInt(x.length);
            for(int i = 0; i < x.length; i++)
                underlyingOutput.writeByte(x[i]);
        } else
            underlyingOutput.writeBoolean(false);
    }

    public void dumpArray(short[] x) throws IOException
    {
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
        dumpInt(objectNumber(o));
        if(o != null) {
            o.dumpSR(this);
        }
    }

    public void dumpOuter(org.jpc.SRDumpable o) throws IOException
    {
        dumpObject(o);
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
            seenObjects.put(new Integer(assignedNum), FALSE);
        }
        return assignedNum;
    }

    public boolean dumped(Object O) throws IOException
    {
        boolean seenBefore = false;
        Integer obj = new Integer(objectNumber(O));

        seenBefore = ((Boolean)seenObjects.get(obj)).booleanValue();
        if(!seenBefore) {
            seenObjects.put(obj, TRUE);
            objectsCount++;
            dumpLong(OBJECT_START_MAGIC);
            dumpString(O.getClass().getName());
            return false;
        } else
            return true;
    }

    public void endObject() throws IOException
    {
            dumpLong(OBJECT_END_MAGIC);
    }
}
