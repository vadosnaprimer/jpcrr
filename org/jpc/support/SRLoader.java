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
import java.util.*;
import java.lang.reflect.*;

public class SRLoader
{
    private DataInput underlyingInput;
    private HashMap objects;
    private Stack tmpStack;
    int opNum;
    
    public SRLoader(DataInput di)
    {
        underlyingInput = di;
        objects = new HashMap();
        tmpStack = new Stack();
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
            for(int i = 0; i < x.length; i++)
                x[i] = underlyingInput.readByte();
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

    private org.jpc.SRDumpable loadObjectContents(Integer id) throws IOException
    {
        org.jpc.SRDumpable x;

        SRDumper.expect(underlyingInput, SRDumper.TYPE_OBJECT_START, opNum++);
        String className = loadString();
        String constructorName = loadString();
        Class classObject;
        Method methodObject;

        //System.err.println("Object ID #" + id + "<" + className + "/" + constructorName + 
        //    "> has not been seen before, loading.");

        try {
            classObject = Class.forName(className);
        } catch(Exception e) {
            throw new IOException("Unknown class \"" + className + "\" encountered:" + e);
        }

        try {
            methodObject = classObject.getMethod(constructorName, getClass(), id.getClass());
        } catch(Exception e) {
            throw new IOException("Unknown constructor \"" + constructorName + "\" encountered:" + e);
        }

        try {
            tmpStack.push(id);
            x = (org.jpc.SRDumpable)methodObject.invoke(null, this, id);
            //System.err.println("Object ID #" + id + "<" + className + "/" + constructorName + 
            //    "> finished loading.");

        } catch(IllegalAccessException e) {
            throw new IOException("Can't invoke constructor (\"" + constructorName + "\" of \"" + className + "\"): " + e);
        } catch(InvocationTargetException e) {
            System.err.println("Exception from constructor:");
            e.getTargetException().printStackTrace();
            throw new IOException("Can't invoke constructor (\"" + constructorName + "\" of \"" + className + "\"): " + e);
        } 

        if(!objects.containsKey(id) || (org.jpc.SRDumpable)(objects.get(id)) != x) {
            throw new IOException("Wrong object assigned to id #" + id + ".");
        }

        return x;
    }

    public void objectCreated(org.jpc.SRDumpable o)
    {
        Integer id = (Integer)(tmpStack.pop());
        int _id = id.intValue();
        //System.err.println("Object ID #" + _id + " is now registered (class" + o.getClass().getName() + ").");
        objects.put(id, o);
    }

    public org.jpc.SRDumpable loadOuter() throws IOException
    {
        SRDumper.expect(underlyingInput, SRDumper.TYPE_OUTER_OBJECT, opNum++);
        int _id = loadInt();
        Integer id = new Integer(_id);
        if(_id < 0)
            return null;
        if(objects.containsKey(id)) {
            //Seen this before. No object follows.
            //System.err.println("Already seen object #" + _id + ".");
            return (org.jpc.SRDumpable)objects.get(id);
        } else {
            //Gotta load this object. 
            return loadObjectContents(id);
        } 
    }

    public org.jpc.SRDumpable checkInnerElide(Integer id) throws IOException
    {
        Integer id2;
        if(objects.containsKey(id)) {
            id2 = (Integer)tmpStack.pop();
            if(id.intValue() == id2.intValue())
                ; //System.err.println("Doing inner elide, (passed/from stack) id #" + id  + ".");
            else
                throw new IOException("checkInnerElide: passed id #" + id + ", id from stack #" + id2 + ".");
            SRDumper.expect(underlyingInput, SRDumper.TYPE_INNER_ELIDE, opNum++);
            SRDumper.expect(underlyingInput, SRDumper.TYPE_OBJECT_END, opNum++);
            return (org.jpc.SRDumpable)(objects.get(id));
        } else
            return null;

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
            //System.err.println("Already seen object #" + _id + ".");
            return (org.jpc.SRDumpable)objects.get(id);
        } else {
            //Gotta load this object. 
            return loadObjectContents(id);
        }
    }

    public void endObject() throws IOException
    {
        SRDumper.expect(underlyingInput, SRDumper.TYPE_OBJECT_END, opNum++);
    }

    public void specialObject(Object o) throws IOException
    {
        SRDumper.expect(underlyingInput, SRDumper.TYPE_SPECIAL_OBJECT, opNum++);
        int id = loadInt();
        //System.out.println("Marked object #" + id + " as special.");
        objects.put(new Integer(id), o);
    }
}
