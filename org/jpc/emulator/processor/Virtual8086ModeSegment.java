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

import java.io.IOException;
import org.jpc.emulator.memory.AddressSpace;

/**
 *
 * @author Ian Preston
 */
public class Virtual8086ModeSegment extends Segment {
    private int selector;
    private int base;
    private int type;
    private int dpl, rpl;
    private long limit;

    public void dumpStatusPartial(org.jpc.support.StatusDumper output)
    {
        super.dumpStatusPartial(output);
        output.println("\tselector " + selector + " base " + base + " limit " + limit + " rpl " + rpl);
        output.println("\tdpl " + dpl + " type " + type);
    }

    public void dumpStatus(org.jpc.support.StatusDumper output)
    {
        if(output.dumped(this))
            return;

        output.println("#" + output.objectNumber(this) + ": Virtual8086ModeSegment:");
        dumpStatusPartial(output);
        output.endObject();
    }

    public void dumpSRPartial(org.jpc.support.SRDumper output) throws IOException
    {
        output.dumpInt(selector);
        output.dumpInt(base);
        output.dumpInt(type);
        output.dumpInt(dpl);
        output.dumpInt(rpl);
        output.dumpLong(limit);
    }

    public Virtual8086ModeSegment(org.jpc.support.SRLoader input) throws IOException
    {
        super(input);
        selector = input.loadInt();
        base = input.loadInt();
        type = input.loadInt();
        dpl = input.loadInt();
        rpl = input.loadInt();
        limit = input.loadLong();
    }

    public Virtual8086ModeSegment(AddressSpace memory, int selector, boolean isCode)
    {
        super(memory, true);
        this.selector = selector;
        base = selector << 4;
        limit = 0xffffL;
        dpl = 3;
        rpl = 3;
        if (isCode)
            type = ProtectedModeSegment.TYPE_CODE | ProtectedModeSegment.TYPE_CODE_READABLE | ProtectedModeSegment.TYPE_ACCESSED;
        else
            type = ProtectedModeSegment.TYPE_DATA_WRITABLE | ProtectedModeSegment.TYPE_ACCESSED;
    }

    public Virtual8086ModeSegment(AddressSpace memory, Segment ancestor)
    {
        super(memory, true);
        selector = ancestor.getSelector();
        base = ancestor.getBase();
        type = ancestor.getType();
        limit = 0xffffffffL & ancestor.getLimit();
    }

    public boolean getDefaultSizeFlag()
    {
        return false;
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
            throw ProcessorException.GENERAL_PROTECTION_0;
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
        return true;
    }

    public boolean isSystem()
    {
        return false;
    }

    public int getDPL()
    {
        return dpl;
    }

    public void setRPL(int cpl)
    {
        rpl = cpl;
    }

    public void printState()
    {
        System.out.println("VM86 Segment");
        System.out.println("selector: " + Integer.toHexString(selector));
        System.out.println("base: " + Integer.toHexString(base));
        System.out.println("dpl: " + Integer.toHexString(dpl));
        System.out.println("rpl: " + Integer.toHexString(rpl));
        System.out.println("limit: " + Long.toHexString(limit));
        System.out.println("type: " + Integer.toHexString(type));
    }
}
