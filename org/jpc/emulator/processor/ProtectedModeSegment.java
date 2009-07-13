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
import java.util.logging.*;

import org.jpc.emulator.memory.AddressSpace;
import org.jpc.emulator.memory.LinearAddressSpace;

/**
 *
 * @author Chris Dennis
 */
public abstract class ProtectedModeSegment extends Segment
{
    private static final Logger LOGGING = Logger.getLogger(ProtectedModeSegment.class.getName());

    public static final int TYPE_ACCESSED = 0x1;
    public static final int TYPE_CODE = 0x8;
    public static final int TYPE_DATA_WRITABLE = 0x2;
    public static final int TYPE_DATA_EXPAND_DOWN = 0x4;
    public static final int TYPE_CODE_READABLE = 0x2;
    public static final int TYPE_CODE_CONFORMING = 0x4;

    public static final int DESCRIPTOR_TYPE_CODE_DATA = 0x10;

    private final boolean defaultSize;
    private final boolean granularity;
    private final boolean present;
    private final boolean system;
    private final int selector;
    private final int base;
    private final int dpl;
    private final long limit;
    private final long descriptor;
    private int rpl;

    public void dumpStatusPartial(org.jpc.support.StatusDumper output)
    {
        super.dumpStatusPartial(output);
        output.println("\tselector " + selector + " base " + base + " limit " + limit + " rpl " + rpl);
        output.println("\tdefaultSize " + defaultSize + " system " + system + " present " + present);
        output.println("\tdpl " + dpl + " granularity " + granularity + " descriptor " + descriptor);
    }

    public void dumpStatus(org.jpc.support.StatusDumper output)
    {
        if(output.dumped(this))
            return;

        output.println("#" + output.objectNumber(this) + ": ProtectedModeSegment:");
        dumpStatusPartial(output);
        output.endObject();
    }

    public void dumpSRPartial(org.jpc.support.SRDumper output) throws IOException
    {
        super.dumpSRPartial(output);
        output.dumpInt(selector);
        output.dumpInt(base);
        output.dumpInt(dpl);
        output.dumpInt(rpl);
        output.dumpLong(descriptor);
        output.dumpLong(limit);
        output.dumpBoolean(defaultSize);
        output.dumpBoolean(granularity);
        output.dumpBoolean(present);
        output.dumpBoolean(system);
    }

    public ProtectedModeSegment(org.jpc.support.SRLoader input) throws IOException
    {
        super(input);
        selector = input.loadInt();
        base = input.loadInt();
        dpl = input.loadInt();
        rpl = input.loadInt();
        descriptor = input.loadLong();
        limit = input.loadLong();
        defaultSize = input.loadBoolean();
        granularity = input.loadBoolean();
        present = input.loadBoolean();
        system = input.loadBoolean();
    }

    public ProtectedModeSegment(AddressSpace memory, int selector, long descriptor)
    {
        super(memory, true);
        this.selector = selector;
        this.descriptor = descriptor;

        granularity = (descriptor & 0x80000000000000L) != 0;

        if (granularity)
            limit = ((descriptor << 12) & 0xffff000L) | ((descriptor >>> 20) & 0xf0000000L) | 0xfffL;
        else
            limit = (descriptor & 0xffffL) | ((descriptor >>> 32) & 0xf0000L);

        base = (int) ((0xffffffL & (descriptor >> 16)) | ((descriptor >> 32) & 0xffffffffff000000L));
        rpl = selector & 0x3;
        dpl = (int) ((descriptor >> 45) & 0x3);

        defaultSize = (descriptor & (1L << 54)) != 0;
        present = (descriptor & (1L << 47)) != 0;
        system = (descriptor & (1L << 44)) != 0;
    }

    public boolean isPresent()
    {
        return present;
    }

    public boolean isSystem()
    {
        return !system;
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

    public void checkAddress(int offset)
    {
        if ((0xffffffffL & offset) > limit) {
            LOGGING.log(Level.INFO, this + "segment limit exceeded: 0x{0} > 0x{1}", new Object[]{Integer.toHexString(offset), Integer.toHexString((int) limit)});
            throw new ProcessorException(ProcessorException.Type.GENERAL_PROTECTION,0,true);//ProcessorException.GENERAL_PROTECTION_0;
//ProcessorException.GENERAL_PROTECTION_0;
        }
    }

    public boolean getDefaultSizeFlag()
    {
        return defaultSize;
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
        return (selector & 0xFFFC) | rpl;
    }

    public int getRPL()
    {
        return rpl;
    }

    public int getDPL()
    {
        return dpl;
    }

    public void setRPL(int cpl)
    {
        rpl = cpl;
    }

    public boolean setSelector(int selector)
    {
        throw new IllegalStateException("Cannot set a selector for a descriptor table segment");
    }

    public static abstract class ReadOnlyProtectedModeSegment extends ProtectedModeSegment
    {
        public void dumpSRPartial(org.jpc.support.SRDumper output) throws IOException
        {
            super.dumpSRPartial(output);
        }

        public ReadOnlyProtectedModeSegment(org.jpc.support.SRLoader input) throws IOException
        {
            super(input);
        }

        public void dumpStatusPartial(org.jpc.support.StatusDumper output)
        {
            super.dumpStatusPartial(output);
        }

        public void dumpStatus(org.jpc.support.StatusDumper output)
        {
            if(output.dumped(this))
                return;

            output.println("#" + output.objectNumber(this) + ": ReadOnlyProtectedModeSegment:");
            dumpStatusPartial(output);
            output.endObject();
        }

        public ReadOnlyProtectedModeSegment(AddressSpace memory, int selector, long descriptor)
        {
            super(memory, selector, descriptor);
        }

        void writeAttempted()
        {
            throw new IllegalStateException();
        }

        public final void setByte(int offset, byte data)
        {
            writeAttempted();
        }

        public final void setWord(int offset, short data)
        {
            writeAttempted();
        }

        public final void setDoubleWord(int offset, int data)
        {
            writeAttempted();
        }

        public final void setQuadWord(int offset, long data)
        {
            writeAttempted();
        }
    }

    public static final class ReadOnlyDataSegment extends ReadOnlyProtectedModeSegment
    {
        public void dumpSRPartial(org.jpc.support.SRDumper output) throws IOException
        {
            super.dumpSRPartial(output);
        }

        public ReadOnlyDataSegment(org.jpc.support.SRLoader input) throws IOException
        {
            super(input);
        }

        public void dumpStatusPartial(org.jpc.support.StatusDumper output)
        {
            super.dumpStatusPartial(output);
        }

        public void dumpStatus(org.jpc.support.StatusDumper output)
        {
            if(output.dumped(this))
                return;

            output.println("#" + output.objectNumber(this) + ": ReadOnlyDataSegment:");
            dumpStatusPartial(output);
            output.endObject();
        }

        public ReadOnlyDataSegment(AddressSpace memory, int selector, long descriptor)
        {
            super(memory, selector, descriptor);
        }

        public int getType()
        {
            return DESCRIPTOR_TYPE_CODE_DATA;
        }

        void writeAttempted()
        {
            throw ProcessorException.GENERAL_PROTECTION_0;
        }
    }

    public static final class ReadOnlyAccessedDataSegment extends ReadOnlyProtectedModeSegment
    {
        public void dumpSRPartial(org.jpc.support.SRDumper output) throws IOException
        {
            super.dumpSRPartial(output);
        }

        public ReadOnlyAccessedDataSegment(org.jpc.support.SRLoader input) throws IOException
        {
            super(input);
        }

        public void dumpStatusPartial(org.jpc.support.StatusDumper output)
        {
            super.dumpStatusPartial(output);
        }

        public void dumpStatus(org.jpc.support.StatusDumper output)
        {
            if(output.dumped(this))
                return;

            output.println("#" + output.objectNumber(this) + ": ReadOnlyAccessedDataSegment:");
            dumpStatusPartial(output);
            output.endObject();
        }

        public ReadOnlyAccessedDataSegment(AddressSpace memory, int selector, long descriptor)
        {
            super(memory, selector, descriptor);
        }

        public int getType()
        {
            return DESCRIPTOR_TYPE_CODE_DATA | TYPE_ACCESSED;
        }

        void writeAttempted()
        {
            throw ProcessorException.GENERAL_PROTECTION_0;
        }
    }

    public static final class ReadWriteDataSegment extends ProtectedModeSegment
    {
        public void dumpSRPartial(org.jpc.support.SRDumper output) throws IOException
        {
            super.dumpSRPartial(output);
        }

        public ReadWriteDataSegment(org.jpc.support.SRLoader input) throws IOException
        {
            super(input);
        }

        public void dumpStatusPartial(org.jpc.support.StatusDumper output)
        {
            super.dumpStatusPartial(output);
        }

        public void dumpStatus(org.jpc.support.StatusDumper output)
        {
            if(output.dumped(this))
                return;

            output.println("#" + output.objectNumber(this) + ": ReadWriteDataSegment:");
            dumpStatusPartial(output);
            output.endObject();
        }

        public ReadWriteDataSegment(AddressSpace memory, int selector, long descriptor)
        {
            super(memory, selector, descriptor);
        }

        public int getType()
        {
            return DESCRIPTOR_TYPE_CODE_DATA | TYPE_DATA_WRITABLE;
        }
    }

    public static final class DownReadWriteDataSegment extends ProtectedModeSegment
    {
        public void dumpSRPartial(org.jpc.support.SRDumper output) throws IOException
        {
            super.dumpSRPartial(output);
        }

        public DownReadWriteDataSegment(org.jpc.support.SRLoader input) throws IOException
        {
            super(input);
        }

        public void dumpStatusPartial(org.jpc.support.StatusDumper output)
        {
            super.dumpStatusPartial(output);
        }

        public void dumpStatus(org.jpc.support.StatusDumper output)
        {
            if(output.dumped(this))
                return;

            output.println("#" + output.objectNumber(this) + ": DownReadWriteDataSegment:");
            dumpStatusPartial(output);
            output.endObject();
        }

        public DownReadWriteDataSegment(AddressSpace memory, int selector, long descriptor)
        {
            super(memory, selector, descriptor);
        }

        public int getType()
        {
            return DESCRIPTOR_TYPE_CODE_DATA | TYPE_DATA_WRITABLE;
        }

        public final int translateAddressRead(int offset)
        {
            checkAddress(offset);
            return super.base + offset;
        }

        public final int translateAddressWrite(int offset)
        {
            checkAddress(offset);
            return super.base + offset;
        }

        public final void checkAddress(int offset)
        {
            if ((0xffffffffL & offset) > super.limit)
            {
                throw new ProcessorException(ProcessorException.Type.GENERAL_PROTECTION, 0, true);//ProcessorException.GENERAL_PROTECTION_0;
            }
        }
    }

    public static final class ReadWriteAccessedDataSegment extends ProtectedModeSegment
    {
        public void dumpSRPartial(org.jpc.support.SRDumper output) throws IOException
        {
            super.dumpSRPartial(output);
        }

        public ReadWriteAccessedDataSegment(org.jpc.support.SRLoader input) throws IOException
        {
            super(input);
        }

        public void dumpStatusPartial(org.jpc.support.StatusDumper output)
        {
            super.dumpStatusPartial(output);
        }

        public void dumpStatus(org.jpc.support.StatusDumper output)
        {
            if(output.dumped(this))
                return;

            output.println("#" + output.objectNumber(this) + ": ReadWriteAccessedDataSegment:");
            dumpStatusPartial(output);
            output.endObject();
        }

        public ReadWriteAccessedDataSegment(AddressSpace memory, int selector, long descriptor)
        {
            super(memory, selector, descriptor);
        }

        public int getType()
        {
            return DESCRIPTOR_TYPE_CODE_DATA | TYPE_DATA_WRITABLE | TYPE_ACCESSED;
        }
    }

    public static final class ExecuteOnlyCodeSegment extends ReadOnlyProtectedModeSegment
    {
        public void dumpSRPartial(org.jpc.support.SRDumper output) throws IOException
        {
            super.dumpSRPartial(output);
        }

        public ExecuteOnlyCodeSegment(org.jpc.support.SRLoader input) throws IOException
        {
            super(input);
        }

        public void dumpStatusPartial(org.jpc.support.StatusDumper output)
        {
            super.dumpStatusPartial(output);
        }

        public void dumpStatus(org.jpc.support.StatusDumper output)
        {
            if(output.dumped(this))
                return;

            output.println("#" + output.objectNumber(this) + ": ExecuteOnlyCodeSegment:");
            dumpStatusPartial(output);
            output.endObject();
        }

        public ExecuteOnlyCodeSegment(AddressSpace memory, int selector, long descriptor)
        {
            super(memory, selector, descriptor);
        }

        public int getType()
        {
            return DESCRIPTOR_TYPE_CODE_DATA | TYPE_CODE;
        }
    }

    public static final class ExecuteReadAccessedCodeSegment extends ProtectedModeSegment
    {
        public void dumpSRPartial(org.jpc.support.SRDumper output) throws IOException
        {
            super.dumpSRPartial(output);
        }

        public ExecuteReadAccessedCodeSegment(org.jpc.support.SRLoader input) throws IOException
        {
            super(input);
        }

        public void dumpStatusPartial(org.jpc.support.StatusDumper output)
        {
            super.dumpStatusPartial(output);
        }

        public void dumpStatus(org.jpc.support.StatusDumper output)
        {
            if(output.dumped(this))
                return;

            output.println("#" + output.objectNumber(this) + ": ExecuteReadAccessedCodeSegment:");
            dumpStatusPartial(output);
            output.endObject();
        }

        public ExecuteReadAccessedCodeSegment(AddressSpace memory, int selector, long descriptor)
        {
            super(memory, selector, descriptor);
        }

        public int getType()
        {
            return DESCRIPTOR_TYPE_CODE_DATA | TYPE_CODE | TYPE_CODE_READABLE | TYPE_ACCESSED;
        }
    }

    public static final class ExecuteReadCodeSegment extends ProtectedModeSegment
    {
        public void dumpSRPartial(org.jpc.support.SRDumper output) throws IOException
        {
            super.dumpSRPartial(output);
        }

        public ExecuteReadCodeSegment(org.jpc.support.SRLoader input) throws IOException
        {
            super(input);
        }

        public void dumpStatusPartial(org.jpc.support.StatusDumper output)
        {
            super.dumpStatusPartial(output);
        }

        public void dumpStatus(org.jpc.support.StatusDumper output)
        {
            if(output.dumped(this))
                return;

            output.println("#" + output.objectNumber(this) + ": ExecuteReadCodeSegment:");
            dumpStatusPartial(output);
            output.endObject();
        }

        public ExecuteReadCodeSegment(AddressSpace memory, int selector, long descriptor)
        {
            super(memory, selector, descriptor);
        }

        public int getType()
        {
            return DESCRIPTOR_TYPE_CODE_DATA | TYPE_CODE | TYPE_CODE_READABLE;
        }
    }

    public static final class ExecuteOnlyConformingAccessedCodeSegment extends ReadOnlyProtectedModeSegment
    {
        public void dumpSRPartial(org.jpc.support.SRDumper output) throws IOException
        {
            super.dumpSRPartial(output);
        }

        public ExecuteOnlyConformingAccessedCodeSegment(org.jpc.support.SRLoader input) throws IOException
        {
            super(input);
        }

        public void dumpStatusPartial(org.jpc.support.StatusDumper output)
        {
            super.dumpStatusPartial(output);
        }

        public void dumpStatus(org.jpc.support.StatusDumper output)
        {
            if(output.dumped(this))
                return;

            output.println("#" + output.objectNumber(this) + ": ExecuteOnlyConformingAccessedCodeSegment:");
            dumpStatusPartial(output);
            output.endObject();
        }

        public ExecuteOnlyConformingAccessedCodeSegment(AddressSpace memory, int selector, long descriptor)
        {
            super(memory, selector, descriptor);
        }

        public int getType()
        {
            return DESCRIPTOR_TYPE_CODE_DATA | TYPE_CODE | TYPE_CODE_CONFORMING | TYPE_ACCESSED;
        }
    }

    public static final class ExecuteReadConformingAccessedCodeSegment extends ProtectedModeSegment
    {
        public void dumpSRPartial(org.jpc.support.SRDumper output) throws IOException
        {
            super.dumpSRPartial(output);
        }

        public ExecuteReadConformingAccessedCodeSegment(org.jpc.support.SRLoader input) throws IOException
        {
            super(input);
        }

        public void dumpStatusPartial(org.jpc.support.StatusDumper output)
        {
            super.dumpStatusPartial(output);
        }

        public void dumpStatus(org.jpc.support.StatusDumper output)
        {
            if(output.dumped(this))
                return;

            output.println("#" + output.objectNumber(this) + ": ExecuteReadConformingAccessedCodeSegment:");
            dumpStatusPartial(output);
            output.endObject();
        }

        public ExecuteReadConformingAccessedCodeSegment(AddressSpace memory, int selector, long descriptor)
        {
            super(memory, selector, descriptor);
        }

        public int getType()
        {
            return DESCRIPTOR_TYPE_CODE_DATA | TYPE_CODE | TYPE_CODE_CONFORMING | TYPE_CODE_READABLE | TYPE_ACCESSED;
        }
    }

    public static final class ExecuteReadConformingCodeSegment extends ProtectedModeSegment
    {
        public void dumpSRPartial(org.jpc.support.SRDumper output) throws IOException
        {
            super.dumpSRPartial(output);
        }

        public ExecuteReadConformingCodeSegment(org.jpc.support.SRLoader input) throws IOException
        {
            super(input);
        }

        public void dumpStatusPartial(org.jpc.support.StatusDumper output)
        {
            super.dumpStatusPartial(output);
        }

        public void dumpStatus(org.jpc.support.StatusDumper output)
        {
            if(output.dumped(this))
                return;

            output.println("#" + output.objectNumber(this) + ": ExecuteReadConformingCodeSegment:");
            dumpStatusPartial(output);
            output.endObject();
        }

        public ExecuteReadConformingCodeSegment(AddressSpace memory, int selector, long descriptor)
        {
            super(memory, selector, descriptor);
        }

        public int getType()
        {
            return DESCRIPTOR_TYPE_CODE_DATA | TYPE_CODE | TYPE_CODE_CONFORMING | TYPE_CODE_READABLE;
        }
    }

    public static abstract class AbstractTSS extends ReadOnlyProtectedModeSegment
    {
        public void dumpSRPartial(org.jpc.support.SRDumper output) throws IOException
        {
            super.dumpSRPartial(output);
        }

        public AbstractTSS(org.jpc.support.SRLoader input) throws IOException
        {
            super(input);
        }

        public void dumpStatusPartial(org.jpc.support.StatusDumper output)
        {
            super.dumpStatusPartial(output);
        }

        public void dumpStatus(org.jpc.support.StatusDumper output)
        {
            if(output.dumped(this))
                return;

            output.println("#" + output.objectNumber(this) + ": AbstractTSS:");
            dumpStatusPartial(output);
            output.endObject();
        }

        public AbstractTSS(AddressSpace memory, int selector, long descriptor)
        {
            super(memory, selector, descriptor);
        }

        public void saveCPUState(Processor cpu)
        {
            int initialAddress = translateAddressWrite(0);
            memory.setDoubleWord(initialAddress + 32, cpu.eip);
            memory.setDoubleWord(initialAddress + 36, cpu.getEFlags());
            memory.setDoubleWord(initialAddress + 40, cpu.eax);
            memory.setDoubleWord(initialAddress + 44, cpu.ecx);
            memory.setDoubleWord(initialAddress + 48, cpu.edx);
            memory.setDoubleWord(initialAddress + 52, cpu.ebx);
            memory.setDoubleWord(initialAddress + 56, cpu.esp);
            memory.setDoubleWord(initialAddress + 60, cpu.ebp);
            memory.setDoubleWord(initialAddress + 64, cpu.esi);
            memory.setDoubleWord(initialAddress + 68, cpu.edi);
            memory.setDoubleWord(initialAddress + 72, cpu.es.getSelector());
            memory.setDoubleWord(initialAddress + 76, cpu.cs.getSelector());
            memory.setDoubleWord(initialAddress + 80, cpu.ss.getSelector());
            memory.setDoubleWord(initialAddress + 84, cpu.ds.getSelector());
            memory.setDoubleWord(initialAddress + 88, cpu.fs.getSelector());
            memory.setDoubleWord(initialAddress + 92, cpu.gs.getSelector());
        }

        public void restoreCPUState(Processor cpu)
        {
            int initialAddress = translateAddressRead(0);
            cpu.eip = memory.getDoubleWord(initialAddress + 32);
            cpu.setEFlags(memory.getDoubleWord(initialAddress + 36));
            cpu.eax = memory.getDoubleWord(initialAddress + 40);
            cpu.ecx = memory.getDoubleWord(initialAddress + 44);
            cpu.edx = memory.getDoubleWord(initialAddress + 48);
            cpu.ebx = memory.getDoubleWord(initialAddress + 52);
            cpu.esp = memory.getDoubleWord(initialAddress + 56);
            cpu.ebp = memory.getDoubleWord(initialAddress + 60);
            cpu.esi = memory.getDoubleWord(initialAddress + 64);
            cpu.edi = memory.getDoubleWord(initialAddress + 68);
            cpu.es = cpu.getSegment(0xFFFF & memory.getDoubleWord(initialAddress + 72));
            cpu.cs = cpu.getSegment(0xFFFF & memory.getDoubleWord(initialAddress + 76));
            cpu.ss = cpu.getSegment(0xFFFF & memory.getDoubleWord(initialAddress + 80));
            cpu.ds = cpu.getSegment(0xFFFF & memory.getDoubleWord(initialAddress + 84));
            cpu.fs = cpu.getSegment(0xFFFF & memory.getDoubleWord(initialAddress + 88));
            cpu.gs = cpu.getSegment(0xFFFF & memory.getDoubleWord(initialAddress + 92));
            // non dynamic fields
            cpu.ldtr = cpu.getSegment(0xFFFF & memory.getDoubleWord(initialAddress + 96));
            cpu.setCR3(memory.getDoubleWord(initialAddress + 28));
        }

        public byte getByte(int offset)
        {
            boolean isSup = ((LinearAddressSpace) memory).isSupervisor();
            try {
                ((LinearAddressSpace) memory).setSupervisor(true);
                return super.getByte(offset);
            } finally {
                ((LinearAddressSpace) memory).setSupervisor(isSup);
            }
        }

        public short getWord(int offset)
        {
            boolean isSup = ((LinearAddressSpace) memory).isSupervisor();
            try {
                ((LinearAddressSpace) memory).setSupervisor(true);
                return super.getWord(offset);
            } finally {
                ((LinearAddressSpace) memory).setSupervisor(isSup);
            }
        }

        public int getDoubleWord(int offset)
        {
            boolean isSup = ((LinearAddressSpace) memory).isSupervisor();
            try {
                ((LinearAddressSpace) memory).setSupervisor(true);
                return super.getDoubleWord(offset);
            } finally {
                ((LinearAddressSpace) memory).setSupervisor(isSup);
            }
        }

        public long getQuadWord(int offset)
        {
            boolean isSup = ((LinearAddressSpace) memory).isSupervisor();
            try {
                ((LinearAddressSpace) memory).setSupervisor(true);
                return super.getQuadWord(offset);
            } finally {
                ((LinearAddressSpace) memory).setSupervisor(isSup);
            }
        }
    }

    public static final class Available32BitTSS extends AbstractTSS
    {
        public void dumpSRPartial(org.jpc.support.SRDumper output) throws IOException
        {
            super.dumpSRPartial(output);
        }

        public Available32BitTSS(org.jpc.support.SRLoader input) throws IOException
        {
            super(input);
        }

        public void dumpStatusPartial(org.jpc.support.StatusDumper output)
        {
            super.dumpStatusPartial(output);
        }

        public void dumpStatus(org.jpc.support.StatusDumper output)
        {
            if(output.dumped(this))
                return;

            output.println("#" + output.objectNumber(this) + ": Available32BitTSS:");
            dumpStatusPartial(output);
            output.endObject();
        }

        public Available32BitTSS(AddressSpace memory, int selector, long descriptor)
        {
            super(memory, selector, descriptor);
        }

        public int getType()
        {
            return 0x09;
        }
    }

    public static final class Busy32BitTSS extends AbstractTSS
    {
        public void dumpSRPartial(org.jpc.support.SRDumper output) throws IOException
        {
            super.dumpSRPartial(output);
        }

        public Busy32BitTSS(org.jpc.support.SRLoader input) throws IOException
        {
            super(input);
        }

        public void dumpStatusPartial(org.jpc.support.StatusDumper output)
        {
            super.dumpStatusPartial(output);
        }

        public void dumpStatus(org.jpc.support.StatusDumper output)
        {
            if(output.dumped(this))
                return;

            output.println("#" + output.objectNumber(this) + ": Busy32BitTSS:");
            dumpStatusPartial(output);
            output.endObject();
        }
        public Busy32BitTSS(AddressSpace memory, int selector, long descriptor)
        {
            super(memory, selector, descriptor);
        }

        public int getType()
        {
            return 0x0b;
        }
    }

    public static final class LDT extends ReadOnlyProtectedModeSegment
    {
        public void dumpSRPartial(org.jpc.support.SRDumper output) throws IOException
        {
            super.dumpSRPartial(output);
        }

        public LDT(org.jpc.support.SRLoader input) throws IOException
        {
            super(input);
        }

        public void dumpStatusPartial(org.jpc.support.StatusDumper output)
        {
            super.dumpStatusPartial(output);
        }

        public void dumpStatus(org.jpc.support.StatusDumper output)
        {
            if(output.dumped(this))
                return;

            output.println("#" + output.objectNumber(this) + ": LDT:");
            dumpStatusPartial(output);
            output.endObject();
        }

        public LDT(AddressSpace memory, int selector, long descriptor)
        {
            super(memory, selector, descriptor);
        }

        public int getType()
        {
            return 0x02;
        }
    }

    public static abstract class GateSegment extends ReadOnlyProtectedModeSegment
    {
        private int targetSegment,  targetOffset;

        public void dumpSRPartial(org.jpc.support.SRDumper output) throws IOException
        {
            super.dumpSRPartial(output);
            output.dumpInt(targetSegment);
            output.dumpInt(targetOffset);
        }

        public GateSegment(org.jpc.support.SRLoader input) throws IOException
        {
            super(input);
            targetSegment = input.loadInt();
            targetOffset = input.loadInt();
        }

        public void dumpStatusPartial(org.jpc.support.StatusDumper output)
        {
            super.dumpStatusPartial(output);
            output.println("\ttargetSegment " + targetSegment + " targetOffset " + targetOffset);
        }

        public void dumpStatus(org.jpc.support.StatusDumper output)
        {
            if(output.dumped(this))
                return;

            output.println("#" + output.objectNumber(this) + ": GateSegment:");
            dumpStatusPartial(output);
            output.endObject();
        }

        public GateSegment(AddressSpace memory, int selector, long descriptor)
        {
            super(memory, selector, descriptor);

            targetSegment = (int) ((descriptor >> 16) & 0xffff);
            targetOffset = (int) ((descriptor & 0xffff) | ((descriptor >>> 32) & 0xffff0000));
        }

        public int getTargetSegment()
        {
            return targetSegment;
        }

        public int getTargetOffset()
        {
            return targetOffset;
        }
    }

    public static final class TaskGate extends GateSegment
    {
        public void dumpSRPartial(org.jpc.support.SRDumper output) throws IOException
        {
            super.dumpSRPartial(output);
        }

        public TaskGate(org.jpc.support.SRLoader input) throws IOException
        {
            super(input);
        }

        public void dumpStatusPartial(org.jpc.support.StatusDumper output)
        {
            super.dumpStatusPartial(output);
        }

        public void dumpStatus(org.jpc.support.StatusDumper output)
        {
            if(output.dumped(this))
                return;

            output.println("#" + output.objectNumber(this) + ": TaskGate:");
            dumpStatusPartial(output);
            output.endObject();
        }

        public TaskGate(AddressSpace memory, int selector, long descriptor)
        {
            super(memory, selector, descriptor);
        }

        public final int getTargetOffset()
        {
            throw new IllegalStateException();
        }

        public int getType()
        {
            return 0x05;
        }
    }

    public static final class InterruptGate32Bit extends GateSegment
    {
        public void dumpSRPartial(org.jpc.support.SRDumper output) throws IOException
        {
            super.dumpSRPartial(output);
        }

        public InterruptGate32Bit(org.jpc.support.SRLoader input) throws IOException
        {
            super(input);
        }

        public void dumpStatusPartial(org.jpc.support.StatusDumper output)
        {
            super.dumpStatusPartial(output);
        }

        public void dumpStatus(org.jpc.support.StatusDumper output)
        {
            if(output.dumped(this))
                return;

            output.println("#" + output.objectNumber(this) + ": InterruptGate32Bit:");
            dumpStatusPartial(output);
            output.endObject();
        }

        public InterruptGate32Bit(AddressSpace memory, int selector, long descriptor)
        {
            super(memory, selector, descriptor);
        }

        public int getType()
        {
            return 0x0e;
        }
    }

    public static final class InterruptGate16Bit extends GateSegment
    {
        public void dumpSRPartial(org.jpc.support.SRDumper output) throws IOException
        {
            super.dumpSRPartial(output);
        }

        public InterruptGate16Bit(org.jpc.support.SRLoader input) throws IOException
        {
            super(input);
        }

        public void dumpStatusPartial(org.jpc.support.StatusDumper output)
        {
            super.dumpStatusPartial(output);
        }

        public void dumpStatus(org.jpc.support.StatusDumper output)
        {
            if(output.dumped(this))
                return;

            output.println("#" + output.objectNumber(this) + ": InterruptGate16Bit:");
            dumpStatusPartial(output);
            output.endObject();
        }

        public InterruptGate16Bit(AddressSpace memory, int selector, long descriptor)
        {
            super(memory, selector, descriptor);
        }

        public int getType()
        {
            return 0x06;
        }
    }

    public static final class TrapGate32Bit extends GateSegment
    {
        public void dumpSRPartial(org.jpc.support.SRDumper output) throws IOException
        {
            super.dumpSRPartial(output);
        }

        public TrapGate32Bit(org.jpc.support.SRLoader input) throws IOException
        {
            super(input);
        }

        public void dumpStatusPartial(org.jpc.support.StatusDumper output)
        {
            super.dumpStatusPartial(output);
        }

        public void dumpStatus(org.jpc.support.StatusDumper output)
        {
            if(output.dumped(this))
                return;

            output.println("#" + output.objectNumber(this) + ": TrapGate32Bit:");
            dumpStatusPartial(output);
            output.endObject();
        }

        public TrapGate32Bit(AddressSpace memory, int selector, long descriptor)
        {
            super(memory, selector, descriptor);
        }

        public int getType()
        {
            return 0x0f;
        }
    }

    public static final class TrapGate16Bit extends GateSegment
    {
        public void dumpSRPartial(org.jpc.support.SRDumper output) throws IOException
        {
            super.dumpSRPartial(output);
        }

        public TrapGate16Bit(org.jpc.support.SRLoader input) throws IOException
        {
            super(input);
        }

        public void dumpStatusPartial(org.jpc.support.StatusDumper output)
        {
            super.dumpStatusPartial(output);
        }

        public void dumpStatus(org.jpc.support.StatusDumper output)
        {
            if(output.dumped(this))
                return;

            output.println("#" + output.objectNumber(this) + ": TrapGate16Bit:");
            dumpStatusPartial(output);
            output.endObject();
        }

        public TrapGate16Bit(AddressSpace memory, int selector, long descriptor)
        {
            super(memory, selector, descriptor);
        }

        public int getType()
        {
            return 0x07;
        }
    }

    public static final class CallGate32Bit extends GateSegment
    {
        private final int parameterCount;

        public void dumpSRPartial(org.jpc.support.SRDumper output) throws IOException
        {
            super.dumpSRPartial(output);
            output.dumpInt(parameterCount);
        }

        public CallGate32Bit(org.jpc.support.SRLoader input) throws IOException
        {
            super(input);
            parameterCount = input.loadInt();
        }

        public void dumpStatusPartial(org.jpc.support.StatusDumper output)
        {
            super.dumpStatusPartial(output);
            output.println("\tparameterCount " + parameterCount);
        }

        public void dumpStatus(org.jpc.support.StatusDumper output)
        {
            if(output.dumped(this))
                return;

            output.println("#" + output.objectNumber(this) + ": CallGate32Bit:");
            dumpStatusPartial(output);
            output.endObject();
        }

        public CallGate32Bit(AddressSpace memory, int selector, long descriptor)
        {
            super(memory, selector, descriptor);
            parameterCount = (int) ((descriptor >> 32) & 0xF);
        }

        public int getType()
        {
            return 0x0c;
        }

        public final int getParameterCount()
        {
            return parameterCount;
        }
    }

    public static final class CallGate16Bit extends GateSegment
    {
        private final int parameterCount;

        public void dumpSRPartial(org.jpc.support.SRDumper output) throws IOException
        {
            super.dumpSRPartial(output);
            output.dumpInt(parameterCount);
        }

        public CallGate16Bit(org.jpc.support.SRLoader input) throws IOException
        {
            super(input);
            parameterCount = input.loadInt();
        }

        public void dumpStatusPartial(org.jpc.support.StatusDumper output)
        {
            super.dumpStatusPartial(output);
            output.println("\tparameterCount " + parameterCount);
        }

        public void dumpStatus(org.jpc.support.StatusDumper output)
        {
            if(output.dumped(this))
                return;

            output.println("#" + output.objectNumber(this) + ": CallGate16Bit:");
            dumpStatusPartial(output);
            output.endObject();
        }

        public CallGate16Bit(AddressSpace memory, int selector, long descriptor)
        {
            super(memory, selector, descriptor);
            parameterCount = (int) ((descriptor >> 32) & 0xF);
        }

        public int getType()
        {
            return 0x04;
        }

        public final int getParameterCount()
        {
            return parameterCount;
        }
    }

    public static final class Available16BitTSS extends ProtectedModeSegment
    {
        public void dumpSRPartial(org.jpc.support.SRDumper output) throws IOException
        {
            super.dumpSRPartial(output);
        }

        public Available16BitTSS(org.jpc.support.SRLoader input) throws IOException
        {
            super(input);
        }

        public void dumpStatusPartial(org.jpc.support.StatusDumper output)
        {
            super.dumpStatusPartial(output);
        }

        public void dumpStatus(org.jpc.support.StatusDumper output)
        {
            if(output.dumped(this))
                return;

            output.println("#" + output.objectNumber(this) + ": Available16BitTSS:");
            dumpStatusPartial(output);
            output.endObject();
        }

        public Available16BitTSS(AddressSpace memory, int selector, long descriptor)
        {
            super(memory, selector, descriptor);
        }

        public int getType()
        {
            return 0x01;
        }
    }

    public static final class Busy16BitTSS extends ProtectedModeSegment
    {
        public void dumpSRPartial(org.jpc.support.SRDumper output) throws IOException
        {
            super.dumpSRPartial(output);
        }

        public Busy16BitTSS(org.jpc.support.SRLoader input) throws IOException
        {
            super(input);
        }

        public void dumpStatusPartial(org.jpc.support.StatusDumper output)
        {
            super.dumpStatusPartial(output);
        }

        public void dumpStatus(org.jpc.support.StatusDumper output)
        {
            if(output.dumped(this))
                return;

            output.println("#" + output.objectNumber(this) + ": Busy16BitTSS:");
            dumpStatusPartial(output);
            output.endObject();
        }

        public Busy16BitTSS(AddressSpace memory, int selector, long descriptor)
        {
            super(memory, selector, descriptor);
        }

        public int getType()
        {
            return 0x03;
        }
    }

    public void printState()
    {
        System.out.println("PM Mode segment");
        System.out.println("selector: " + Integer.toHexString(selector));
        System.out.println("base: " + Integer.toHexString(base));
        System.out.println("dpl: " + Integer.toHexString(dpl));
        System.out.println("rpl: " + Integer.toHexString(rpl));
        System.out.println("limit: " + Long.toHexString(limit));
        System.out.println("descriptor: " + Long.toHexString(descriptor));
    }
}
