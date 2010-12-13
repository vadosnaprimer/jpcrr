/*
    JPC: An x86 PC Hardware Emulator for a pure Java Virtual Machine
    Release Version 2.4

    A project from the Physics Dept, The University of Oxford

    Copyright (C) 2007-2010 The University of Oxford

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

    jpc.sourceforge.net
    or the developer website
    sourceforge.net/projects/jpc/

    Conceived and Developed by:
    Rhys Newman, Ian Preston, Chris Dennis

    End of licence header
*/

package org.jpc.classfile.constantpool;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Constant pool element for a long integer constant
 * @author Mike Moleschi
 */
public class LongInfo extends ConstantPoolInfo
{
    private final long value;

    LongInfo(DataInputStream in) throws IOException
    {
        this(in.readLong());
    }

    /**
     * Constructs a constant pool element for the given long integer value.
     * @param val constant long value
     */
    public LongInfo(long val)
    {
        super();
        value = val;
        hashCode = (LongInfo.class.hashCode() * 31) ^ (((int) value) * 37) ^ (((int) (value >>> 32)) * 41);
    }

    /**
     * Returns the long value of this constant pool element.
     * @return constant long value
     */
    public long getValue()
    {
        return value;
    }

    public void write(DataOutputStream out) throws IOException
    {
        out.writeByte(LONG);
        out.writeLong(value);
    }

    public boolean equals(Object obj)
    {
        if (obj == this) return true;
        if (!(obj instanceof LongInfo)) return false;

        return getValue() == ((LongInfo) obj).getValue();
    }

    public String toString()
    {
        return "CONSTANT_Long_info : value=" + getValue();
    }
}
