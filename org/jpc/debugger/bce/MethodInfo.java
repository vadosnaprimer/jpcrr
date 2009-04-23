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

public class MethodInfo
{
    public Modifiers modifiers;
    public ConstantPool.Entry name, descriptor;
    public AttributeInfo[] attributes;

    public MethodInfo(Modifiers mods, ConstantPool.Entry name, ConstantPool.Entry descriptor, AttributeInfo[] attributes)
    {
        this.modifiers = mods;
        this.name = name;
        this.descriptor = descriptor;
        this.attributes = attributes;
    }

    public Implementation getImplementation(ConstantPool cp)
    {
        for (int i=0; i<attributes.length; i++)
        {
            if (ConstantPool.matchesUTF8(attributes[i].name, "Code"))
                return Implementation.read(attributes[i].data, cp);
        }

        return null;
    }

    public String toString()
    {
        return modifiers+" "+name+" ["+descriptor+"]";
    }

    public static MethodInfo read(DataInput in, ConstantPool cp) throws IOException
    {
        Modifiers mods = new Modifiers(in.readShort());
        int nameIndex = in.readUnsignedShort();
        int descIndex = in.readUnsignedShort();

        int attrCount = in.readUnsignedShort();
        AttributeInfo[] attrs = new AttributeInfo[attrCount];
        for (int i=0; i<attrs.length; i++)
            attrs[i] = AttributeInfo.read(in, cp);

        return new MethodInfo(mods, cp.getEntryAt(nameIndex), cp.getEntryAt(descIndex), attrs);
    }
}
