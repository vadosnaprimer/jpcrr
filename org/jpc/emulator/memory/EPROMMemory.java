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

package org.jpc.emulator.memory;

public class EPROMMemory extends LazyCodeBlockMemory
{
    public EPROMMemory(byte[] data)
    {
        this(data, 0, data.length);
    }

    public EPROMMemory(byte[] data, int offset, int length)
    {
        this(length, 0, data, offset, length);
    }

    public EPROMMemory(int size, int base, byte[] data, int offset, int length)
    {
        super(size);
        super.copyContentsFrom(base, data, offset, Math.min(size - base, Math.min(length, data.length - offset)));
    }

    public void setByte(int offset, byte data)
    {
        System.err.println("Tried to write to EPROM");
    }

    public void setWord(int offset, short data)
    {
        System.err.println("Tried to write to EPROM");
    }

    public void setDoubleWord(int offset, int data)
    {
        System.err.println("Tried to write to EPROM");
    }

    public void copyContentsFrom(int address, byte[] buf, int off, int len) {}

    public void clear()
    {
        constructCodeBlocksArray();
    }

    public boolean isVolatile()
    {
        return false;
    }
}
