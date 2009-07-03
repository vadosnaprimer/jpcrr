/*
    JPC: A x86 PC Hardware Emulator for a pure Java Virtual Machine
    Release Version 2.0

    A project from the Physics Dept, The University of Oxford

    Copyright (C) 2007-2009 Isis Innovation Limited

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

    www-jpc.physics.ox.ac.uk
*/

package org.jpc.classfile.constantpool;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Constant pool element for a floating point double constant
 * @author Mike Moleschi
 */
public class DoubleInfo extends ConstantPoolInfo
{
    private final double value;

    DoubleInfo(DataInputStream in) throws IOException
    {
        this(in.readDouble());
    }

    /**
     * Constructs a constant pool element for the given double value.
     * @param val constant double value
     */
    public DoubleInfo(double val)
    {
        super();
        value = val;
        long longBytes = Double.doubleToRawLongBits(value);
        hashCode = (DoubleInfo.class.hashCode() * 31) ^ (((int) longBytes) * 37) ^ (((int) (longBytes >>> 32)) * 41);
    }

    /**
     * Returns the double value of this constant pool element.
     * @return constant double value
     */
    public double getValue()
    {
        return value;
    }

    public void write(DataOutputStream out) throws IOException
    {
        out.writeByte(DOUBLE);
        out.writeDouble(value);
    }

    public boolean equals(Object obj)
    {
        if (obj == this) return true;
        if (!(obj instanceof DoubleInfo)) return false;

        return getValue() == ((DoubleInfo) obj).getValue();
    }

    public String toString()
    {
        return "CONSTANT_Double_info : value=" + getValue();
    }
}
