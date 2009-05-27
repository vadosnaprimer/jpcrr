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


package org.jpc.emulator.processor;

import org.jpc.emulator.memory.*;
import org.jpc.emulator.*;
import java.io.*;

public abstract class Segment implements Hibernatable, org.jpc.SRDumpable
{
    public abstract boolean isPresent();

    public abstract void setAddressSpace(AddressSpace memory);

    public abstract int getType();

    public abstract int getSelector();

    public abstract int getLimit();

    public abstract int getBase();

    public abstract boolean getDefaultSizeFlag();

    public abstract int getRPL();

    public abstract void setRPL(int cpl);

    public abstract int getDPL();

    public abstract boolean setSelector(int selector);

    public abstract void checkAddress(int offset) throws ProcessorException;

    public abstract int translateAddressRead(int offset);

    public abstract int translateAddressWrite(int offset);

    public abstract byte getByte(int offset);

    public abstract short getWord(int offset);

    public abstract int getDoubleWord(int offset);

    public abstract long getQuadWord(int offset);

    public abstract void setByte(int offset, byte data);

    public abstract void setWord(int offset, short data);

    public abstract void setDoubleWord(int offset, int data);

    public abstract void setQuadWord(int offset, long data);

    public abstract int dumpState(DataOutput output) throws IOException;

    public void dumpStatus(org.jpc.support.StatusDumper output)
    {
        if(output.dumped(this))
            return;

        output.println("#" + output.objectNumber(this) + ": SegmentFactory:");
        dumpStatusPartial(output);
        output.endObject();
    }

    public void dumpStatusPartial(org.jpc.support.StatusDumper output)
    {
    }

    public abstract void dumpSR(org.jpc.support.SRDumper output) throws IOException;

    public void dumpSRPartial(org.jpc.support.SRDumper output) throws IOException
    {
    }

    public Segment(org.jpc.support.SRLoader input) throws IOException
    {
        input.objectCreated(this);
    }

    public Segment()
    {
    }

}
