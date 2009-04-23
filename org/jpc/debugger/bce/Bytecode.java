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

public class Bytecode
{
    public static final Bytecode AALOAD = new Bytecode(0x32, "AALOAD");

    private byte code;
    private String name;
    
    public Bytecode(int code, String name)
    {
        this.code = (byte) code;
        this.name = name;
    }

    public byte getCode()
    {
        return code;
    }

    public String toString()
    {
        return name;
    }

    public static Bytecode decode(DataInput input) throws IOException
    {
        return AALOAD;
    }
}
    
