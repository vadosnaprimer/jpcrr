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

import java.io.*;

public class Implementation
{
    public int maxStack, maxLocals;
    public byte[] bytecode;
    public ExceptionTableEntry[] exceptions;
    public AttributeInfo[] attributes;
    
    public Implementation(int maxStack, int maxLocals, byte[] bytecode, ExceptionTableEntry[] exceptions, AttributeInfo[] attrs)
    {
        this.maxStack = maxStack;
        this.maxLocals = maxLocals;
        this.bytecode = bytecode;
        this.exceptions = exceptions;
        this.attributes = attrs;
    }

    public String toString()
    {
        return "SK["+maxStack+"] VARS["+maxLocals+"] Code["+bytecode.length+"] EX["+exceptions.length+"] ATTR["+attributes.length+"]";
    }

    public static class ExceptionTableEntry
    {
        public int startPC, endPC, handlerPC;
        public ConstantPool.Entry catchType;

        ExceptionTableEntry(int s, int e, int h, ConstantPool.Entry type)
        {
            startPC = s;
            endPC = e;
            handlerPC = h;
            catchType = type;
        }

        public String toString()
        {
            return "EX["+startPC+" to "+endPC+"] -> "+handlerPC+" {"+catchType+"}";
        }
    }
    
    public static Implementation read(byte[] raw, ConstantPool cp)
    {
        DataInput in = new DataInputStream(new ByteArrayInputStream(raw));

        try
        { 
            int maxStack = in.readUnsignedShort();
            int maxLocals = in.readUnsignedShort();
            int codeLength = in.readInt();
            byte[] code = new byte[codeLength];
            in.readFully(code);
            int exLength = in.readUnsignedShort();
            ExceptionTableEntry[] exs = new ExceptionTableEntry[exLength];
            for (int i=0; i<exs.length; i++)
            {
                int s = in.readUnsignedShort();
                int e = in.readUnsignedShort();
                int h = in.readUnsignedShort();
                int c = in.readUnsignedShort();
                ConstantPool.Entry type = null;
                if (c != 0)
                    type = cp.getEntryAt(c);

                exs[i] = new ExceptionTableEntry(s, e, h, type);
            }

            int attrsCount = in.readUnsignedShort();
            AttributeInfo[] attrs = new AttributeInfo[attrsCount];
            for (int i=0; i<attrs.length; i++)
                attrs[i] = AttributeInfo.read(in, cp);

            return new Implementation(maxStack, maxLocals, code, exs, attrs);
        }
        catch (IOException e)
        {
            e.printStackTrace();
            return null;
        }
    }
}
    
