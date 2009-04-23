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

public class AttributeInfo
{
    public ConstantPool.Entry name;
    public byte[] data;

    public AttributeInfo(ConstantPool.Entry name, byte[] data) throws IOException
    {
        this.name = name;
        this.data = data;
    }

    public String toString()
    {
        return name+" ["+data.length+"]";
    }

    public static AttributeInfo read(DataInput in, ConstantPool cp) throws IOException
    {
        int nameIndex = in.readUnsignedShort();
        int length = in.readInt();
        byte[] data = new byte[length];
        in.readFully(data, 0, length);

        return new AttributeInfo(cp.getEntryAt(nameIndex), data);
    }
}
