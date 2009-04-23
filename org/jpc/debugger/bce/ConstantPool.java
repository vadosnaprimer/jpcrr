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


package org.jpc.debugger.bce;

import java.util.*;
import java.io.*;

public class ConstantPool
{
    public static final byte CONSTANT_Class = 7;  
    public static final byte CONSTANT_Fieldref = 9; 
    public static final byte CONSTANT_Methodref = 10;  
    public static final byte CONSTANT_InterfaceMethodref = 11;  
    public static final byte CONSTANT_String = 8;  
    public static final byte CONSTANT_Integer = 3;  
    public static final byte CONSTANT_Float = 4;  
    public static final byte CONSTANT_Long = 5;  
    public static final byte CONSTANT_Double = 6;  
    public static final byte CONSTANT_NameAndType = 12;  
    public static final byte CONSTANT_Utf8  = 1;  

    public static final byte CONSTANT_INVALID  = (byte) -1;  

    private Vector<Entry> entries;

    public ConstantPool()
    {
        entries = new Vector();
    }

    public void addEntry(Entry entry)
    {
        entries.add(entry);
    }

    public void removeEntry(Entry e)
    {
        entries.remove(e);
    }

    public Entry getEntryAt(int index)
    {
        Entry e = entries.elementAt(index-1);
        if (e instanceof InvalidEntry)
            return null;
        return e;
    }
    
    public int size()
    {
        return entries.size();
    }

    public int indexOf(Entry e)
    {
        if (e instanceof InvalidEntry)
            return -1;

        int idx = entries.indexOf(e);
        return idx;
    }

    public String toString()
    {
        StringBuffer buf = new StringBuffer("Constant Pool Size: "+entries.size()+"\n");
        for (int i=0; i<entries.size(); i++)
            buf.append(i+": "+entries.elementAt(i)+"\n");
        return buf.toString();
    }

    public static boolean matchesUTF8(ConstantPool.Entry e, String value)
    {
        if (!(e instanceof ConstantUTF8Entry))
            return false;
        ConstantUTF8Entry ee = (ConstantUTF8Entry) e;
        return ee.value.equals(value);
    }

    public abstract static class Entry
    {
        public byte getType()
        {
            return CONSTANT_INVALID;
        }

        boolean resolved()
        {
            return true;
        }

        Entry resolve(ConstantPool tgt)
        {
            return this;
        }
    }

    static class UnresolvedEntry extends Entry
    {
        short ref;
        
        UnresolvedEntry(int ref)
        {
            this.ref = (short) ref;
        }

        boolean resolved()
        {
            return false;
        }

        Entry resolve(ConstantPool tgt)
        {
            return tgt.getEntryAt(ref);
        }

        public String toString()
        {
            return "_"+ref+"_";
        }
    }

    public static class InvalidEntry extends Entry
    {
    }

    public static class ConstantClassEntry extends Entry
    {
        Entry name;

        public ConstantClassEntry(Entry name)
        {
            this.name = name;
        }

        public byte getType()
        {
            return CONSTANT_Class;
        }

        boolean resolved()
        {
            return name.resolved();
        }

        Entry resolve(ConstantPool tgt)
        {
            name = name.resolve(tgt);
            return this;
        }

        public String toString()
        {
            return "CLASS["+name+"]";
        }
    }

    public static class ConstantMethodEntry extends Entry
    {
        Entry className, methodNameAndType;

        public ConstantMethodEntry(Entry cls, Entry nameAndType)
        {
            className = cls;
            methodNameAndType = nameAndType;
        }

        public byte getType()
        {
            return CONSTANT_Methodref;
        }

        boolean resolved()
        {
            return className.resolved() && methodNameAndType.resolved();
        }

        Entry resolve(ConstantPool tgt)
        {
            className = className.resolve(tgt);
            methodNameAndType = methodNameAndType.resolve(tgt);
            return this;
        }

        public String toString()
        {
            return "METHOD["+className+":"+methodNameAndType+"]";
        }
    }

    public static class ConstantFieldEntry extends Entry
    {
        Entry className, fieldNameAndType;

        public ConstantFieldEntry(Entry cls, Entry nameAndType)
        {
            className = cls;
            fieldNameAndType = nameAndType;
        }

        public byte getType()
        {
            return CONSTANT_Fieldref;
        }

        boolean resolved()
        {
            return className.resolved() && fieldNameAndType.resolved();
        }

        Entry resolve(ConstantPool tgt)
        {
            className = className.resolve(tgt);
            fieldNameAndType = fieldNameAndType.resolve(tgt);
            return this;
        }

        public String toString()
        {
            return "FIELD["+className+":"+fieldNameAndType+"]";
        }
    }

    public static class ConstantInterfaceMethodEntry extends Entry
    {
        Entry className, methodNameAndType;

        public ConstantInterfaceMethodEntry(Entry cls, Entry nameAndType)
        {
            className = cls;
            methodNameAndType = nameAndType;
        }

        public byte getType()
        {
            return CONSTANT_InterfaceMethodref;
        }

        boolean resolved()
        {
            return className.resolved() && methodNameAndType.resolved();
        }

        Entry resolve(ConstantPool tgt)
        {
            className = className.resolve(tgt);
            methodNameAndType = methodNameAndType.resolve(tgt);
            return this;
        }

        public String toString()
        {
            return "INTERFACE_METHOD["+className+":"+methodNameAndType+"]";
        }
    }

    public static class ConstantStringEntry extends Entry
    {
        Entry utf;

        public ConstantStringEntry(Entry utf)
        {
            this.utf = utf;
        }

        public byte getType()
        {
            return CONSTANT_String;
        }

        boolean resolved()
        {
            return utf.resolved();
        }

        Entry resolve(ConstantPool tgt)
        {
            utf = utf.resolve(tgt);
            return this;
        }

        public String toString()
        {
            return "STRING["+utf+"]";
        }
    }

    public static class ConstantIntegerEntry extends Entry
    {
        int value;

        public ConstantIntegerEntry(int value)
        {
            this.value = value;
        }

        public byte getType()
        {
            return CONSTANT_Integer;
        }

        public String toString()
        {
            return "INTEGER["+value+"]";
        }
    }

    public static class ConstantFloatEntry extends Entry
    {
        float value;

        public ConstantFloatEntry(float value)
        {
            this.value = value;
        }

        public byte getType()
        {
            return CONSTANT_Float;
        }

        public String toString()
        {
            return "FLOAT["+value+"]";
        }
    }

    public static class ConstantLongEntry extends Entry
    {
        long value;

        public ConstantLongEntry(long value)
        {
            this.value = value;
        }

        public byte getType()
        {
            return CONSTANT_Long;
        }

        public String toString()
        {
            return "LONG["+value+"]";
        }
    }

    public static class ConstantDoubleEntry extends Entry
    {
        double value;

        public ConstantDoubleEntry(double value)
        {
            this.value = value;
        }

        public byte getType()
        {
            return CONSTANT_Double;
        }

        public String toString()
        {
            return "DOUBLE["+value+"]";
        }
    }

    public static class ConstantNameAndTypeEntry extends Entry
    {
        Entry name, descriptor; 

        public ConstantNameAndTypeEntry(Entry name, Entry desc)
        {
            this.name = name;
            this.descriptor = desc;
        }

        public byte getType()
        {
            return CONSTANT_NameAndType;
        }

        boolean resolved()
        {
            return name.resolved() && descriptor.resolved();
        }

        Entry resolve(ConstantPool tgt)
        {
            name = name.resolve(tgt);
            descriptor = descriptor.resolve(tgt);
            return this;
        }

        public String toString()
        {
            return "NAME_AND_TYPE["+name+";"+descriptor+"]";
        }
    }

    public static class ConstantUTF8Entry extends Entry
    {
        String value;

        public ConstantUTF8Entry(String val) 
        {
            value = val;
        }

        public byte getType()
        {
            return CONSTANT_Utf8;
        }

        public String toString()
        {
            return "UTF8["+value+"]";
        }
    }

    private static Entry readInitialEntry(DataInput in) throws IOException
    {
        byte t = (byte) in.readUnsignedByte();
        Entry name, type, cls, nt;

        switch (t)
        {
        case CONSTANT_Class:
            name = new UnresolvedEntry(in.readUnsignedShort());
            return new ConstantClassEntry(name);
        case CONSTANT_Fieldref:
            cls = new UnresolvedEntry(in.readUnsignedShort());
            nt = new UnresolvedEntry(in.readUnsignedShort());
            return new ConstantFieldEntry(cls, nt);
        case CONSTANT_Methodref:
            cls = new UnresolvedEntry(in.readUnsignedShort());
            nt = new UnresolvedEntry(in.readUnsignedShort());
            return new ConstantMethodEntry(cls, nt);
        case CONSTANT_InterfaceMethodref:
            cls = new UnresolvedEntry(in.readUnsignedShort());
            nt = new UnresolvedEntry(in.readUnsignedShort());
            return new ConstantInterfaceMethodEntry(cls, nt);
        case CONSTANT_String:
            name = new UnresolvedEntry(in.readUnsignedShort());
            return new ConstantStringEntry(name);
        case CONSTANT_Integer:
            return new ConstantIntegerEntry(in.readInt());
        case CONSTANT_Float:
            return new ConstantFloatEntry(in.readFloat());
        case CONSTANT_Long:
            return new ConstantLongEntry(in.readLong());
        case CONSTANT_Double:
            return new ConstantDoubleEntry(in.readDouble());
        case CONSTANT_NameAndType:
            name = new UnresolvedEntry(in.readUnsignedShort());
            type = new UnresolvedEntry(in.readUnsignedShort());
            return new ConstantNameAndTypeEntry(name, type);
        case CONSTANT_Utf8:
            return new ConstantUTF8Entry(in.readUTF());
        default:
            throw new IOException("Invalid constant pool entry: "+t);
        }
    }

    public static ConstantPool read(DataInput in) throws IOException
    {
        ConstantPool result = new ConstantPool();

        int constPoolCount = in.readShort();
        Entry[] entries = new Entry[constPoolCount];
        entries[0] = new InvalidEntry();

        for (int i=1; i<constPoolCount; i++)
        {
            entries[i] = readInitialEntry(in);
            result.addEntry(entries[i]);
            if ((entries[i] instanceof ConstantDoubleEntry) || (entries[i] instanceof ConstantLongEntry))
            {
                i++;
                entries[i] = new InvalidEntry();
                result.addEntry(entries[i]);
            }
        }

        boolean resolved = true;
        for (int i=0; i<100; i++)
        {
            for (int j=0; j<entries.length; j++)
                if (!entries[j].resolved())
                {
                    resolved = false;
                    entries[j].resolve(result);
                }

            if (resolved)
                break;
        }

        return result;
    }
}
