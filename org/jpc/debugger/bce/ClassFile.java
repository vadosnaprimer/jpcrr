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

public class ClassFile
{
    public static final int MAGIC_NUMBER = 0xCAFEBABE;

    public short majorVer, minorVer;
    public ConstantPool constantPool;
    public Modifiers modifiers;
    public ConstantPool.Entry thisClassEntry, superClassEntry;
    public ConstantPool.Entry[] interfaces;
    public FieldInfo[] fields;
    public MethodInfo[] methods;
    public AttributeInfo[] attributes;

    public ClassFile(DataInput in) throws IOException
    {
        if (in.readInt() != MAGIC_NUMBER)
            throw new IOException("Magic Number mismatch");

        minorVer = (short) in.readUnsignedShort();
        majorVer = (short) in.readUnsignedShort();

        constantPool = ConstantPool.read(in);
        modifiers = new Modifiers(in.readUnsignedShort());

        thisClassEntry = constantPool.getEntryAt(in.readUnsignedShort());
        superClassEntry = constantPool.getEntryAt(in.readUnsignedShort());

        interfaces = new ConstantPool.Entry[in.readUnsignedShort()];
        for (int i=0; i<interfaces.length; i++)
            interfaces[i] = constantPool.getEntryAt(in.readUnsignedShort());

        fields = new FieldInfo[in.readUnsignedShort()];
        for (int i=0; i<fields.length; i++)
            fields[i] = FieldInfo.read(in, constantPool);
        
        methods = new MethodInfo[in.readUnsignedShort()];
        for (int i=0; i<methods.length; i++)
            methods[i] = MethodInfo.read(in, constantPool);

        attributes = new AttributeInfo[in.readUnsignedShort()];
        for (int i=0; i<attributes.length; i++)
            attributes[i] = AttributeInfo.read(in, constantPool);
    }

    public short getMajorVersion()
    {
        return majorVer;
    }

    public short getMinorVersion()
    {
        return minorVer;
    }

    public static void main(String[] args) throws Exception
    {
        //FileInputStream fin = new FileInputStream("org/jpc/debugger/JPC.class");
        FileInputStream fin = new FileInputStream("Test.class");
        ClassFile cf = new ClassFile(new DataInputStream(fin));
        
        System.out.println(cf.constantPool);

        AttributeInfo[] attrs = cf.attributes;
        for (int i=0; i<attrs.length; i++)
            System.out.println("AA "+i+"  "+attrs[i]);

        MethodInfo[] m = cf.methods;
        for (int i=0; i<m.length; i++)
        {
            System.out.println(i+"  "+m[i]);
            attrs = m[i].attributes;
            for (int j=0; j<attrs.length; j++)
                System.out.println("   "+attrs[j]);

            System.out.println("CODE "+m[i].getImplementation(cf.constantPool));
            
        }
    }

}
