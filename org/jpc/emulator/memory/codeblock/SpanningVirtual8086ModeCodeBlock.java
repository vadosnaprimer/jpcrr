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

package org.jpc.emulator.memory.codeblock;

import org.jpc.emulator.processor.Processor;
import org.jpc.emulator.memory.AddressSpace;

/**
 * 
 * @author Chris Dennis
 */
class SpanningVirtual8086ModeCodeBlock extends SpanningCodeBlock implements Virtual8086ModeCodeBlock
{
    private ByteSourceWrappedMemory byteSource = new ByteSourceWrappedMemory();

    private CodeBlockFactory[] factories;

    public SpanningVirtual8086ModeCodeBlock(CodeBlockFactory[] factories)
    {
	this.factories = factories;
    }

    protected CodeBlock decode(Processor cpu)
    {
	Virtual8086ModeCodeBlock block = null;
	AddressSpace memory = cpu.linearMemory;
	int address = cpu.getInstructionPointer();
	for (int i = 0; (i < factories.length) && (block == null); i++) {
	    try {
		byteSource.set(memory, address);
		block = factories[i].getVirtual8086ModeCodeBlock(byteSource);
	    } catch (IllegalStateException e) {}
	}
	
        byteSource.set(null, 0);
	return block;
    }
    
    public String toString()
    {
	return "Spanning Virtual8086 Mode CodeBlock";
    }
}
