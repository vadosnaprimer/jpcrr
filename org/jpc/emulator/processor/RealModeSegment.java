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

import org.jpc.emulator.memory.AddressSpace;

/**
 *
 * @author Chris Dennis
 */
public final class RealModeSegment extends Segment
{
    private int selector;
    private int base;
    private int type;
    private long limit;
    private int rpl;
    private boolean defaultSize = false;
    private boolean segment = true;
    private boolean present = true;

    public void dumpStatusPartial(org.jpc.support.StatusDumper output)
    {
        super.dumpStatusPartial(output);
        output.println("\tselector " + selector + " base " + base + " limit " + limit + " rpl " + rpl);
        output.println("\tdefaultSize " + defaultSize + " segment " + segment + " present " + present);
    }

    public void dumpStatus(org.jpc.support.StatusDumper output)
    {
        if(output.dumped(this))
            return;

        output.println("#" + output.objectNumber(this) + ": RealModeSegment:");
        dumpStatusPartial(output);
        output.endObject();
    }

    public void dumpSRPartial(org.jpc.support.SRDumper output) throws IOException
    {
        super.dumpSRPartial(output);
        output.dumpInt(selector);
        output.dumpInt(base);
        output.dumpInt(type);
        output.dumpInt(rpl);
        output.dumpLong(limit);
        output.dumpBoolean(defaultSize);
        output.dumpBoolean(segment);
        output.dumpBoolean(present);
    }

    public RealModeSegment(org.jpc.support.SRLoader input) throws IOException
    {
        super(input);
        selector = input.loadInt();
        base = input.loadInt();
        type = input.loadInt();
        rpl = input.loadInt();
        limit = input.loadLong();
        defaultSize = input.loadBoolean();
        segment = input.loadBoolean();
        present = input.loadBoolean();
    }


    public RealModeSegment(AddressSpace memory, int selector)
    {
        super(memory, true);
        this.selector = selector;
        base = selector << 4;
        limit = 0xffffL;
        rpl = 0;
        type = ProtectedModeSegment.TYPE_DATA_WRITABLE | ProtectedModeSegment.TYPE_ACCESSED;
    }

    public RealModeSegment(AddressSpace memory, Segment ancestor)
    {
        super(memory, true);
        selector = ancestor.getSelector();
        base = ancestor.getBase();
        type = ancestor.getType();
        limit = 0xffffffffL & ancestor.getLimit();
        defaultSize = ancestor.getDefaultSizeFlag();
        segment = !ancestor.isSystem();
        present = ancestor.isPresent();
        rpl = ancestor.getRPL();
    }

    public boolean getDefaultSizeFlag()
    {
        return defaultSize;
    }

    public int getLimit()
    {
        return (int)limit;
    }

    public int getBase()
    {
        return base;
    }

    public int getSelector()
    {
        return selector;
    }

    public boolean setSelector(int selector)
    {
        this.selector = selector;
        base = selector << 4;
        type = ProtectedModeSegment.TYPE_DATA_WRITABLE | ProtectedModeSegment.TYPE_ACCESSED;
        return true;
    }

    public void checkAddress(int offset)
    {
        if ((0xffffffffL & offset) > limit)
        {
            System.err.println("Emulated: RM Segment Limit exceeded: offset=" + Integer.toHexString(offset) + 
                ", limit=" + Long.toHexString(limit));
            throw new ProcessorException(ProcessorException.Type.GENERAL_PROTECTION, 0, true);
        }
    }

    public int translateAddressRead(int offset)
    {
        //checkAddress(offset);
        return base + offset;
    }

    public int translateAddressWrite(int offset)
    {
        //checkAddress(offset);
        return base + offset;
    }

    public int getRPL()
    {
        return rpl;
    }

    public int getType()
    {
        return type;
    }

    public boolean isPresent()
    {
        return present;
    }

    public boolean isSystem()
    {
        return !segment;
    }

    public int getDPL()
    {
        System.err.println("Critical error: RM segment getDPL()");
        throw new IllegalStateException(getClass().toString());
    }

    public void setRPL(int cpl)
    {
        System.err.println("Critical error: RM segment setRPL()");
        throw new IllegalStateException(getClass().toString());
    }

    public void printState()
    {
        System.out.println("RM Segment");
        System.out.println("selector: " + Integer.toHexString(selector));
        System.out.println("base: " + Integer.toHexString(base));
        System.out.println("rpl: " + Integer.toHexString(rpl));
        System.out.println("limit: " + Long.toHexString(limit));
        System.out.println("type: " + Integer.toHexString(type));
        System.out.println("defaultSize: " + defaultSize);
        System.out.println("segment: " + segment);
        System.out.println("present: " + present);
    }
}
