/*
    JPC-RR: A x86 PC Hardware Emulator
    Release 1

    Copyright (C) 2007-2009 Isis Innovation Limited
    Copyright (C) 2009-2010 H. Ilari Liusvaara

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

public final class SRDumper
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
    public static final byte TYPE_DOUBLE = 20;

    OutputStream underlyingOutput;
    int nextObjectNumber;
    static final Boolean FALSE;
    static final Boolean TRUE;
    private java.util.Stack<Integer> objectStack;
    private int firstUnseenObject;
    java.util.HashMap<Integer, ObjectListEntry> chainingLists;
    java.util.HashSet<String> constructors;
    int objectsCount;
    private int bufferStart;
    private byte[] buffer;
    private static final int BUFFER_MAXSIZE = 4096;  //MUST BE MULTIPLE OF 8.

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

    private void ensureBufferSpace(int minSpace) throws IOException
    {
        if(minSpace > BUFFER_MAXSIZE)
            throw new IllegalStateException("ensureBufferSpace: The amount requested is too large.");
        while(minSpace > BUFFER_MAXSIZE - bufferStart) {
            underlyingOutput.write(buffer, 0, bufferStart);
            bufferStart = 0;
        }
    }

    public SRDumper(OutputStream ps)
    {
        nextObjectNumber = 0;
        underlyingOutput = ps;
        firstUnseenObject = 0;
        chainingLists = new java.util.HashMap<Integer, ObjectListEntry>();
        objectStack = new java.util.Stack<Integer>();
        objectsCount = 0;
        constructors = new java.util.HashSet<String>();
        bufferStart = 0;
        buffer = new byte[BUFFER_MAXSIZE];
    }

    public void flush() throws IOException
    {
        ensureBufferSpace(BUFFER_MAXSIZE);
    }

    public void dumpBoolean(boolean x) throws IOException
    {
        ensureBufferSpace(2);
        buffer[bufferStart++] = TYPE_BOOLEAN;
        buffer[bufferStart++] = x ? (byte)1 : (byte)0;
    }

    public void dumpByte(byte x) throws IOException
    {
        ensureBufferSpace(2);
        buffer[bufferStart++] = TYPE_BYTE;
        buffer[bufferStart++] = x;
    }

    public void dumpShort(short x, boolean ensure) throws IOException
    {
        if(ensure)
            ensureBufferSpace(2);
        buffer[bufferStart++] = (byte)(x >>> 8);
        buffer[bufferStart++] = (byte)x;
    }

    public void dumpShort(short x) throws IOException
    {
        ensureBufferSpace(3);
        buffer[bufferStart++] = TYPE_SHORT;
        dumpShort(x, false);
    }

    public void dumpInt(int x, boolean ensure) throws IOException
    {
        if(ensure)
            ensureBufferSpace(4);
        buffer[bufferStart++] = (byte)(x >>> 24);
        buffer[bufferStart++] = (byte)(x >>> 16);
        buffer[bufferStart++] = (byte)(x >>> 8);
        buffer[bufferStart++] = (byte)x;
    }

    public void dumpInt(int x) throws IOException
    {
        ensureBufferSpace(5);
        buffer[bufferStart++] = TYPE_INT;
        dumpInt(x, false);
    }

    public void dumpLong(long x, boolean ensure) throws IOException
    {
        if(ensure)
            ensureBufferSpace(8);
        buffer[bufferStart++] = (byte)(x >>> 56);
        buffer[bufferStart++] = (byte)(x >>> 48);
        buffer[bufferStart++] = (byte)(x >>> 40);
        buffer[bufferStart++] = (byte)(x >>> 32);
        buffer[bufferStart++] = (byte)(x >>> 24);
        buffer[bufferStart++] = (byte)(x >>> 16);
        buffer[bufferStart++] = (byte)(x >>> 8);
        buffer[bufferStart++] = (byte)x;
    }


    public void dumpLong(long x) throws IOException
    {
        ensureBufferSpace(9);
        buffer[bufferStart++] = TYPE_LONG;
        dumpLong(x, false);
    }

    public void dumpDouble(double x) throws IOException
    {
        ensureBufferSpace(9);
        buffer[bufferStart++] = TYPE_DOUBLE;
        dumpLong(Double.doubleToLongBits(x), false);
    }

    public void dumpString(String x) throws IOException
    {
        ensureBufferSpace(2);
        buffer[bufferStart++] = TYPE_STRING;
        if(x != null) {
            buffer[bufferStart++] = 1;
            int bytes = 0;
            int characters = x.length();
            for(int i = 0; i < characters; i++) {
                char ch = x.charAt(i);
                if(ch == 0)
                    bytes += 2;
                else if(ch < 128)
                    bytes += 1;
                else if(ch < 2048)
                    bytes += 2;
                else
                    bytes += 3;
            }
            dumpShort((short)bytes, true);
            for(int i = 0; i < characters; i++) {
                char ch = x.charAt(i);
                if(ch == 0) {
                    ensureBufferSpace(2);
                    buffer[bufferStart++] = (byte)192;
                    buffer[bufferStart++] = (byte)128;
                } else if(ch < 128) {
                    ensureBufferSpace(1);
                    buffer[bufferStart++] = (byte)ch;
                } else if(ch < 2048) {
                    ensureBufferSpace(2);
                    buffer[bufferStart++] = (byte)((ch >> 6) + 192);
                    buffer[bufferStart++] = (byte)(128 + ch & 0x3F);
                } else {
                    ensureBufferSpace(3);
                    buffer[bufferStart++] = (byte)((ch >> 12) + 224);
                    buffer[bufferStart++] = (byte)(128 + (ch >> 6) & 0x3F);
                    buffer[bufferStart++] = (byte)(128 + ch & 0x3F);
                }
            }
        } else
            buffer[bufferStart++] = 0;
    }

    public void dumpArray(boolean[] x) throws IOException
    {
        ensureBufferSpace(2);
        buffer[bufferStart++] = TYPE_BOOLEAN_ARRAY;
        if(x != null) {
            buffer[bufferStart++] = 1;
            dumpInt(x.length, true);
            for(int i = 0; i < x.length; i++) {
                if(bufferStart > BUFFER_MAXSIZE - 1)
                    ensureBufferSpace(1);
                 buffer[bufferStart++] = x[i] ? (byte)1 : (byte)0;
            }
        } else
            buffer[bufferStart++] = 0;
    }

    public void dumpArray(byte[] x) throws IOException
    {
        ensureBufferSpace(2);
        buffer[bufferStart++] = TYPE_BYTE_ARRAY;
        if(x != null) {
            buffer[bufferStart++] = 1;
            dumpInt(x.length, true);
            int remaining = x.length;
            int index = 0;
            while(remaining > 0) {
                int tocopy = remaining;
                if(tocopy > BUFFER_MAXSIZE)
                    tocopy = BUFFER_MAXSIZE;
                ensureBufferSpace(tocopy);
                System.arraycopy(x, index, buffer, bufferStart, tocopy);
                bufferStart += tocopy;
                remaining -= tocopy;
                index += tocopy;
            }
        } else
            buffer[bufferStart++] = 0;
    }

    public void dumpArray(short[] x) throws IOException
    {
        ensureBufferSpace(2);
        buffer[bufferStart++] = TYPE_SHORT_ARRAY;
        if(x != null) {
            buffer[bufferStart++] = 1;
            dumpInt(x.length, true);
            int remaining = x.length;
            int index = 0;
            while(remaining > 0) {
                 int tocopy = remaining;
                 if(tocopy > BUFFER_MAXSIZE / 2)
                     tocopy = BUFFER_MAXSIZE / 2;
                 ensureBufferSpace(2 * tocopy);
                 for(int i = 0; i < tocopy; i++)
                     dumpShort(x[index + i], false);
                 remaining -= tocopy;
                 index += tocopy;
            }
        } else
            buffer[bufferStart++] = 0;
    }

    public void dumpArray(int[] x) throws IOException
    {
        ensureBufferSpace(2);
        buffer[bufferStart++] = TYPE_INT_ARRAY;
        if(x != null) {
            buffer[bufferStart++] = 1;
            dumpInt(x.length, true);
            int remaining = x.length;
            int index = 0;
            while(remaining > 0) {
                 int tocopy = remaining;
                 if(tocopy > BUFFER_MAXSIZE / 4)
                     tocopy = BUFFER_MAXSIZE / 4;
                 ensureBufferSpace(4 * tocopy);
                 for(int i = 0; i < tocopy; i++)
                     dumpInt(x[index + i], false);
                 remaining -= tocopy;
                 index += tocopy;
            }
        } else
            buffer[bufferStart++] = 0;
    }

    public void dumpArray(long[] x) throws IOException
    {
        ensureBufferSpace(2);
        buffer[bufferStart++] = TYPE_LONG_ARRAY;
        if(x != null) {
            buffer[bufferStart++] = 1;
            dumpInt(x.length, true);
            int remaining = x.length;
            int index = 0;
            while(remaining > 0) {
                 int tocopy = remaining;
                 if(tocopy > BUFFER_MAXSIZE / 8)
                     tocopy = BUFFER_MAXSIZE / 8;
                 ensureBufferSpace(8 * tocopy);
                 for(int i = 0; i < tocopy; i++)
                     dumpLong(x[index + i], false);
                 remaining -= tocopy;
                 index += tocopy;
            }
        } else
            buffer[bufferStart++] = 0;
    }

    public void dumpArray(double[] x) throws IOException
    {
        ensureBufferSpace(2);
        buffer[bufferStart++] = TYPE_DOUBLE_ARRAY;
        if(x != null) {
            buffer[bufferStart++] = 1;
            dumpInt(x.length, true);
            int remaining = x.length;
            int index = 0;
            while(remaining > 0) {
                 int tocopy = remaining;
                 if(tocopy > BUFFER_MAXSIZE / 8)
                     tocopy = BUFFER_MAXSIZE / 8;
                 ensureBufferSpace(8 * tocopy);
                 for(int i = 0; i < tocopy; i++)
                     dumpLong(Double.doubleToLongBits(x[index + i]), false);
                 remaining -= tocopy;
                 index += tocopy;
            }
        } else
            buffer[bufferStart++] = 0;
    }

    public void dumpObject(SRDumpable o) throws IOException
    {
        ensureBufferSpace(1);
        buffer[bufferStart++] = TYPE_OBJECT;
        dumpInt(objectNumber(o));
        if(o != null) {
            builtinDumpSR(o);
        }
    }

    public void specialObject(SRDumpable o) throws IOException
    {
        int assigned = objectNumber(o);
        ensureBufferSpace(1);
        buffer[bufferStart++] = TYPE_SPECIAL_OBJECT;
        dumpInt(assigned);
        firstUnseenObject = assigned + 1;
    }

    private void builtinDumpSR(SRDumpable obj) throws IOException
    {
        try {
            if(dumped(obj))
                return;
            obj.dumpSRPartial(this);
            endObject();
        } catch(Throwable e) {
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
            if(e instanceof RuntimeException)
                throw (RuntimeException)e;
            if(e instanceof Error)
                throw (Error)e;
            throw new IOException("Unknown exception while invoking dumper: " + e2);
        }
    }

    public int dumpedObjects()
    {
        return objectsCount;
    }

    private void addObject(Object O, int n)
    {
        Integer hcode = new Integer(System.identityHashCode(O));
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
        Integer hcode = new Integer(System.identityHashCode(O));
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
        }
        return assignedNum;
    }

    public boolean dumped(Object O) throws IOException
    {
        int objn;
        Integer obj = new Integer(objn = objectNumber(O));

        if(objn >= firstUnseenObject) {
            firstUnseenObject = objn + 1;
            objectsCount++;
            ensureBufferSpace(1);
            buffer[bufferStart++] = TYPE_OBJECT_START;
            dumpString(O.getClass().getName());
            constructors.add(O.getClass().getName());
            objectStack.push(obj);
            return false;
        } else {
            ensureBufferSpace(1);
            buffer[bufferStart++] = TYPE_OBJECT_NOT_PRESENT;
            return true;
        }
    }

    public void endObject() throws IOException
    {
        ensureBufferSpace(1);
        buffer[bufferStart++] = TYPE_OBJECT_END;
    }
}
