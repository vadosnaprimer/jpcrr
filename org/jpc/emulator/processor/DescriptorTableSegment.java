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

package org.jpc.emulator.processor;

import java.io.*;

import org.jpc.emulator.StatusDumper;
import org.jpc.emulator.SRLoader;
import org.jpc.emulator.SRDumper;
import org.jpc.emulator.memory.AddressSpace;

/**
 *
 * @author Chris Dennis
 */
public class DescriptorTableSegment extends Segment
{
    private final int base;
    private final long limit;

    public void dumpStatusPartial(StatusDumper output)
    {
        super.dumpStatusPartial(output);
        output.println("\tbase " + base + " limit " + limit);
    }

    public void dumpStatus(StatusDumper output)
    {
        if(output.dumped(this))
            return;

        output.println("#" + output.objectNumber(this) + ": DescriptorTableSegment:");
        dumpStatusPartial(output);
        output.endObject();
    }

    public void dumpSRPartial(SRDumper output) throws IOException
    {
        super.dumpSRPartial(output);
        output.dumpInt(base);
        output.dumpLong(limit);
    }

    public DescriptorTableSegment(SRLoader input) throws IOException
    {
        super(input);
        base = input.loadInt();
        limit = input.loadLong();
    }

    public DescriptorTableSegment(AddressSpace memory, int base, int limit)
    {
        super(memory, true);
        this.base = base;
        this.limit = 0xffffffffL & limit;
    }

    public int getLimit()
    {
        return (int) limit;
    }

    public int getBase()
    {
        return base;
    }

    public int getSelector()
    {
        System.err.println("Critical error: No selector for a descriptor table segment.");
        throw new IllegalStateException("No selector for a descriptor table segment");
    }

    public boolean setSelector(int selector)
    {
        System.err.println("Critical error: Can not set selector for a descriptor table segment.");
        throw new IllegalStateException("Cannot set a selector for a descriptor table segment");
    }

    public void checkAddress(int offset)
    {
        if ((0xffffffffL & offset) > limit)
        {
            System.err.println("Emulated: Offset beyond end of Descriptor Table Segment: Offset=" +
                Integer.toHexString(offset) + ", limit=" + Long.toHexString(limit));
            throw new ProcessorException(ProcessorException.Type.GENERAL_PROTECTION, offset, true);
        }
    }

    public int translateAddressRead(int offset)
    {
        checkAddress(offset);
        return base + offset;
    }

    public int translateAddressWrite(int offset)
    {
        checkAddress(offset);
        return base + offset;
    }

    public int getDPL()
    {
        System.err.println("Critical error: Descriptor Table Segment getDPL().");
        throw new IllegalStateException(getClass().toString());
    }

    public int getRPL()
    {
        System.err.println("Critical error: Descriptor Table Segment getRPL().");
        throw new IllegalStateException(getClass().toString());
    }

    public void setRPL(int cpl)
    {
        System.err.println("Critical error: Descriptor Table Segment setRPL().");
        throw new IllegalStateException(getClass().toString());
    }

    public boolean getDefaultSizeFlag()
    {
        System.err.println("Critical error: Descriptor Table Segment getDefaultSizeFlag().");
        throw new IllegalStateException(getClass().toString());
    }

    public int getType()
    {
        System.err.println("Critical error: Descriptor Table Segment getType().");
        throw new IllegalStateException(getClass().toString());
    }

    public boolean isPresent()
    {
        return true;
    }

    public boolean isSystem()
    {
        return true;
    }

    public void printState()
    {
        System.out.println("Descriptor Table Segment");
        System.out.print("base: " + Integer.toHexString(base));
        System.out.println("limit: " + Long.toHexString(limit));
    }
}
