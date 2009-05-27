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
import java.io.*;

public class SegmentFactory implements org.jpc.SRDumpable
{
    private static final long DESCRIPTOR_TYPE = 0x100000000000l;
    private static final long SEGMENT_TYPE = 0xf0000000000l;

    public static final Segment NULL_SEGMENT = new NullSegment();

    public static final int DESCRIPTOR_TYPE_CODE_DATA = 0x10;

    public static final int TYPE_ACCESSED = 0x1;
    public static final int TYPE_CODE = 0x8;

    public static final int TYPE_DATA_WRITABLE = 0x2;
    public static final int TYPE_DATA_EXPAND_DOWN = 0x4;

    public static final int TYPE_CODE_READABLE = 0x2;
    public static final int TYPE_CODE_CONFORMING = 0x4;

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
        output.println("\tNULL_SEGMENT <object #" + output.objectNumber(NULL_SEGMENT) + ">"); if(NULL_SEGMENT != null) NULL_SEGMENT.dumpStatus(output);
    }

    public void dumpSR(org.jpc.support.SRDumper output) throws IOException
    {
        if(output.dumped(this))
            return;
        dumpSRPartial(output);
        output.endObject();
    }

    public void dumpSRPartial(org.jpc.support.SRDumper output) throws IOException
    {
    }

    public static org.jpc.SRDumpable loadSR(org.jpc.support.SRLoader input, Integer id) throws IOException
    {
        org.jpc.SRDumpable x = new LazyCodeBlockMemory(input);
        input.endObject();
        return x;
    }

    public SegmentFactory(org.jpc.support.SRLoader input) throws IOException
    {
        input.objectCreated(this);
    }


    abstract static class DefaultSegment extends Segment
    {
        Memory memory;

        public abstract void dumpSR(org.jpc.support.SRDumper output) throws IOException;
 
        public DefaultSegment()
        {
            memory = null;
        }

        public void dumpSRPartial(org.jpc.support.SRDumper output) throws IOException
        {
            super.dumpSRPartial(output);
            output.dumpObject(memory);
        }

        public DefaultSegment(org.jpc.support.SRLoader input) throws IOException
        {
            super(input);
            memory = (Memory)(input.loadObject());
        }

        public void dumpStatus(org.jpc.support.StatusDumper output)
        {
            if(output.dumped(this))
                return;

            output.println("#" + output.objectNumber(this) + ": DefaultSegment:");
            dumpStatusPartial(output);
            output.endObject();
        }

        public void dumpStatusPartial(org.jpc.support.StatusDumper output)
        {
            super.dumpStatusPartial(output);
            output.println("\tmemory <object #" + output.objectNumber(memory) + ">"); if(memory != null) memory.dumpStatus(output);
        }

        public DefaultSegment(Memory memory)
        {
            this.memory = memory;
        }

        public void setAddressSpace(AddressSpace memory)
        {
            this.memory = memory;
        }

        public boolean isPresent()
        {
            return true;
        }

        public void invalidateAddress(int address) {}

        public int getType()
        {
            throw new IllegalStateException(getClass().toString());
        }

        public boolean getDefaultSizeFlag()
        {
            throw new IllegalStateException(getClass().toString());
        }

        public int getLimit()
        {
            throw new IllegalStateException(getClass().toString());
        }

        public int getBase()
        {
            throw new IllegalStateException(getClass().toString());
        }

        public int getSelector()
        {
            throw new IllegalStateException(getClass().toString());
        }

        public boolean setSelector(int selector)
        {
            throw new IllegalStateException(getClass().toString());
        }

        public int getRPL()
        {
            throw new IllegalStateException(getClass().toString());
        }

        public void setRPL(int cpl)
        {
            throw new IllegalStateException(getClass().toString());
        }

        public int getDPL()
        {
            throw new IllegalStateException(getClass().toString());
        }

        public abstract void checkAddress(int offset);

        public abstract int translateAddressRead(int offset);

        public abstract int translateAddressWrite(int offset);

        public byte getByte(int offset)
        {
            return memory.getByte(translateAddressRead(offset));
        }

        public short getWord(int offset)
        {
            return memory.getWord(translateAddressRead(offset));
        }

        public int getDoubleWord(int offset)
        {
            return memory.getDoubleWord(translateAddressRead(offset));
        }

        public long getQuadWord(int offset)
        {
            int off = translateAddressRead(offset);
            long result = 0xFFFFFFFFl & memory.getDoubleWord(off);
            off = translateAddressRead(offset+4);
            result |= (((long) memory.getDoubleWord(off)) << 32);
            return result;
        }

        public void setByte(int offset, byte data)
        {
            memory.setByte(translateAddressWrite(offset), data);
        }

        public void setWord(int offset, short data)
        {
            memory.setWord(translateAddressWrite(offset), data);
        }

        public void setDoubleWord(int offset, int data)
        {
            memory.setDoubleWord(translateAddressWrite(offset), data);
        }

        public void setQuadWord(int offset, long data)
        {
            int off = translateAddressWrite(offset);
            memory.setDoubleWord(off, (int) data);
            off = translateAddressWrite(offset+4);
            memory.setDoubleWord(off, (int) (data >>> 32));
        }
    }

    public static final class RealModeSegment extends DefaultSegment
    {
        private int selector, base, limit;

        public void dumpSR(org.jpc.support.SRDumper output) throws IOException
        {
            if(output.dumped(this))
                return;
            dumpSRPartial(output);
            output.endObject();
        }

        public void dumpSRPartial(org.jpc.support.SRDumper output) throws IOException
        {
            super.dumpSRPartial(output);
            output.dumpInt(selector);
            output.dumpInt(base);
            output.dumpInt(limit);
        }

        public static org.jpc.SRDumpable loadSR(org.jpc.support.SRLoader input, Integer id) throws IOException
        {
            org.jpc.SRDumpable x = new RealModeSegment(input);
            input.endObject();
            return x;
        }

        public RealModeSegment(org.jpc.support.SRLoader input) throws IOException
        {
            super(input);
            selector = input.loadInt();
            base = input.loadInt();
            limit = input.loadInt();
        }

        public RealModeSegment(Memory memory, int selector)
        {
            super(memory);
            this.selector = selector;
            base = selector << 4;
            limit = 0xffff;
        }

        public int dumpState(DataOutput output) throws IOException
        {
            output.writeInt(1);
            output.writeInt(selector);
            return 8;
        }

        public void dumpStatusPartial(org.jpc.support.StatusDumper output)
        {
            super.dumpStatusPartial(output);
            output.println("\tselector " + selector + " base " + base + " limit " + limit + ".");
        }

        public void dumpStatus(org.jpc.support.StatusDumper output)
        {
            if(output.dumped(this))
                return;
            output.println("#" + output.objectNumber(this) + ": RealModeSegment:");
            dumpStatusPartial(output);
            output.endObject();
        }

        public boolean getDefaultSizeFlag()
        {
            return false;
        }

        public int getLimit()
        {
            return limit;
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
            return true;
        }

        public void checkAddress(int offset)
        {
        }

        public int translateAddressRead(int offset)
        {
            return base + offset;
        }

        public int translateAddressWrite(int offset)
        {
            return base + offset;
        }

        public int getRPL()
        {
            return 0;
        }
    }

    public static Segment createRealModeSegment(Memory memory, int selector)
    {
        if (memory == null)
            throw new NullPointerException("Null reference to memory");

        return new RealModeSegment(memory, selector);
    }

    public static final class DescriptorTableSegment extends DefaultSegment
    {
        private int base;
        private long limit;

        public void dumpSR(org.jpc.support.SRDumper output) throws IOException
        {
            if(output.dumped(this))
                return;
            dumpSRPartial(output);
            output.endObject();
        }

        public void dumpSRPartial(org.jpc.support.SRDumper output) throws IOException
        {
            super.dumpSRPartial(output);
            output.dumpInt(base);
            output.dumpLong(limit);
        }

        public static org.jpc.SRDumpable loadSR(org.jpc.support.SRLoader input, Integer id) throws IOException
        {
            org.jpc.SRDumpable x = new DescriptorTableSegment(input);
            input.endObject();
            return x;
        }

        public DescriptorTableSegment(org.jpc.support.SRLoader input) throws IOException
        {
            super(input);
            base = input.loadInt();
            limit = input.loadLong();
        }

        public DescriptorTableSegment(Memory memory, int base, int limit)
        {
            super(memory);
            this.base = base;
            this.limit = 0xFFFFFFFFl & limit;
        }

        public int dumpState(DataOutput output) throws IOException
        {
            output.writeInt(2);
            output.writeInt(base);
            output.writeInt((int) limit);
            return 12;
        }

        public void dumpStatusPartial(org.jpc.support.StatusDumper output)
        {
            super.dumpStatusPartial(output);
            output.println("\tbase " + base + " limit " + limit + ".");
        }

        public void dumpStatus(org.jpc.support.StatusDumper output)
        {
            if(output.dumped(this))
                return;
            output.println("#" + output.objectNumber(this) + ": DescriptorTableSegment:");
            dumpStatusPartial(output);
            output.endObject();
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
            throw new IllegalStateException("No selector for a descriptor table segment");
        }

        public boolean setSelector(int selector)
        {
            throw new IllegalStateException("Cannot set a selector for a descriptor table segment");
        }

        public void checkAddress(int offset)
        {
            if ((0xFFFFFFFFl & offset) > limit)
                throw new ProcessorException(Processor.PROC_EXCEPTION_GP, offset, true);
        }

        public int translateAddressRead(int offset)
        {
            return base + offset;
        }

        public int translateAddressWrite(int offset)
        {
            return base + offset;
        }
    }

    public static Segment createDescriptorTableSegment(Memory memory, int base, int limit)
    {
        if (memory == null)
            throw new NullPointerException("Null reference to memory");

        return new DescriptorTableSegment(memory, base, limit);
    }

    abstract static class DefaultProtectedModeSegment extends DefaultSegment
    {
        private boolean defaultSize, granularity, present;
        private int selector, limit, base, rpl, dpl;
        private long longLimit, descriptor;

        public abstract void dumpSR(org.jpc.support.SRDumper output) throws IOException;

        public void dumpSRPartial(org.jpc.support.SRDumper output) throws IOException
        {
            super.dumpSRPartial(output);
            output.dumpBoolean(defaultSize);
            output.dumpBoolean(granularity);
            output.dumpBoolean(present);
            output.dumpInt(selector);
            output.dumpInt(limit);
            output.dumpInt(base);
            output.dumpInt(rpl);
            output.dumpInt(dpl);
            output.dumpLong(longLimit);
            output.dumpLong(descriptor);
        }

        public DefaultProtectedModeSegment(org.jpc.support.SRLoader input) throws IOException
        {
            super(input);
            defaultSize = input.loadBoolean();
            granularity = input.loadBoolean();
            present = input.loadBoolean();
            selector = input.loadInt();
            limit = input.loadInt();
            base = input.loadInt();
            rpl = input.loadInt();
            dpl = input.loadInt();
            longLimit = input.loadLong();
            descriptor = input.loadLong();
        }

        public DefaultProtectedModeSegment(Memory memory, int selector, long descriptor)
        {
            super(memory);
            this.selector = selector;
            this.descriptor = descriptor;

            granularity = (descriptor & 0x80000000000000l) != 0;

            limit = (int)((descriptor & 0xffff) | ((descriptor >>> 32) & 0xf0000));

            if (granularity)
                limit = (limit << 12) | 0xfff;

            longLimit = 0xffffffffl & limit;
            base = (int) ((0xffffff & (descriptor >> 16)) | ((descriptor >> 32) & 0xFF000000));
            rpl = selector & 0x3;
            dpl = (int) ((descriptor >> 45) & 0x3);

            defaultSize = (descriptor & (0x1l << 54)) != 0;
            present = (descriptor & (0x1l << 47)) != 0;
        }

        public int dumpState(DataOutput output) throws IOException
        {
            output.writeInt(3);
            output.writeInt(selector);
            output.writeLong(descriptor);
            return 12;
        }

        public void dumpStatusPartial(org.jpc.support.StatusDumper output)
        {
            super.dumpStatusPartial(output);
            output.println("\tdefaultSize " + defaultSize + " granularity " + granularity + " present " + present + ".");
            output.println("\tselector " + selector + " limit " + limit + " base " + base + ".");
            output.println("\trpl " + rpl + " dpl " + dpl + ".");
            output.println("\tlonglimit " + longLimit + " descriptor " + descriptor + ".");
        }

        public void dumpStatus(org.jpc.support.StatusDumper output)
        {
            if(output.dumped(this))
                return;
            output.println("#" + output.objectNumber(this) + ": DefaultProtectedModeSegment:");
            dumpStatusPartial(output);
            output.endObject();
        }

        public boolean isPresent()
        {
            return present;
        }

        public final int translateAddressRead(int offset)
        {
            checkAddress(offset);
            return base + offset;
        }

        public final int translateAddressWrite(int offset)
        {
            checkAddress(offset);
            return base + offset;
        }

        public final void checkAddress(int offset)
        {
            if ((0xffffffffl & offset) > longLimit) {
                System.err.println("Segment limit exceeded: " + Integer.toHexString(offset) + " > " + Integer.toHexString((int)longLimit));
                throw new ProcessorException(Processor.PROC_EXCEPTION_GP, 0, true);
            }
        }

        public boolean getDefaultSizeFlag()
        {
            return defaultSize;
        }

        public int getLimit()
        {
            return limit;
        }

        public int getBase()
        {
            return base;
        }

        public int getSelector()
        {
            return selector;
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
    }

    static abstract class ReadOnlyProtectedModeSegment extends DefaultProtectedModeSegment
    {
        public abstract void dumpSR(org.jpc.support.SRDumper output) throws IOException;

        public void dumpSRPartial(org.jpc.support.SRDumper output) throws IOException
        {
            super.dumpSRPartial(output);
        }

        public ReadOnlyProtectedModeSegment(org.jpc.support.SRLoader input) throws IOException
        {
            super(input);
        }

        public ReadOnlyProtectedModeSegment(Memory memory, int selector, long descriptor)
        {
            super(memory, selector, descriptor);
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

        void writeAttempted()
        {
            throw new IllegalStateException();
        }

        public void setByte(int offset, byte data)
        {
            writeAttempted();
        }

        public void setWord(int offset, short data)
        {
            writeAttempted();
        }

        public void setDoubleWord(int offset, int data)
        {
            writeAttempted();
        }

        public void setQuadWord(int offset, long data)
        {
            writeAttempted();
        }
    }

    public static final class ReadOnlyDataSegment extends ReadOnlyProtectedModeSegment
    {
        public void dumpSR(org.jpc.support.SRDumper output) throws IOException
        {
            if(output.dumped(this))
                return;
            dumpSRPartial(output);
            output.endObject();
        }

        public void dumpSRPartial(org.jpc.support.SRDumper output) throws IOException
        {
            super.dumpSRPartial(output);
        }

        public static org.jpc.SRDumpable loadSR(org.jpc.support.SRLoader input, Integer id) throws IOException
        {
            org.jpc.SRDumpable x = new ReadOnlyDataSegment(input);
            input.endObject();
            return x;
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

        public ReadOnlyDataSegment(Memory memory, int selector, long descriptor)
        {
            super(memory, selector, descriptor);
        }

        public int getType()
        {
            return DESCRIPTOR_TYPE_CODE_DATA;
        }

        void writeAttempted()
        {
            throw new ProcessorException(Processor.PROC_EXCEPTION_GP, 0, true);
        }
    }

    public static final class ReadOnlyAccessedDataSegment extends ReadOnlyProtectedModeSegment
    {
        public void dumpSR(org.jpc.support.SRDumper output) throws IOException
        {
            if(output.dumped(this))
                return;
            dumpSRPartial(output);
            output.endObject();
        }

        public void dumpSRPartial(org.jpc.support.SRDumper output) throws IOException
        {
            super.dumpSRPartial(output);
        }

        public static org.jpc.SRDumpable loadSR(org.jpc.support.SRLoader input, Integer id) throws IOException
        {
            org.jpc.SRDumpable x = new ReadOnlyAccessedDataSegment(input);
            input.endObject();
            return x;
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

        public ReadOnlyAccessedDataSegment(Memory memory, int selector, long descriptor)
        {
            super(memory, selector, descriptor);
        }

        public int getType()
        {
            return DESCRIPTOR_TYPE_CODE_DATA | TYPE_ACCESSED;
        }

        void writeAttempted()
        {
            throw new ProcessorException(Processor.PROC_EXCEPTION_GP, 0, true);
        }
    }

    public static final class ReadWriteDataSegment extends DefaultProtectedModeSegment
    {
        public void dumpSR(org.jpc.support.SRDumper output) throws IOException
        {
            if(output.dumped(this))
                return;
            dumpSRPartial(output);
            output.endObject();
        }

        public void dumpSRPartial(org.jpc.support.SRDumper output) throws IOException
        {
            super.dumpSRPartial(output);
        }

        public static org.jpc.SRDumpable loadSR(org.jpc.support.SRLoader input, Integer id) throws IOException
        {
            org.jpc.SRDumpable x = new ReadWriteDataSegment(input);
            input.endObject();
            return x;
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

        public ReadWriteDataSegment(Memory memory, int selector, long descriptor)
        {
            super(memory, selector, descriptor);
        }

        public int getType()
        {
            return DESCRIPTOR_TYPE_CODE_DATA | TYPE_DATA_WRITABLE;
        }
    }

    public static final class ReadWriteAccessedDataSegment extends DefaultProtectedModeSegment
    {
        public void dumpSR(org.jpc.support.SRDumper output) throws IOException
        {
            if(output.dumped(this))
                return;
            dumpSRPartial(output);
            output.endObject();
        }

        public void dumpSRPartial(org.jpc.support.SRDumper output) throws IOException
        {
            super.dumpSRPartial(output);
        }

        public static org.jpc.SRDumpable loadSR(org.jpc.support.SRLoader input, Integer id) throws IOException
        {
            org.jpc.SRDumpable x = new ReadWriteAccessedDataSegment(input);
            input.endObject();
            return x;
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

        public ReadWriteAccessedDataSegment(Memory memory, int selector, long descriptor)
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
        public void dumpSR(org.jpc.support.SRDumper output) throws IOException
        {
            if(output.dumped(this))
                return;
            dumpSRPartial(output);
            output.endObject();
        }

        public void dumpSRPartial(org.jpc.support.SRDumper output) throws IOException
        {
            super.dumpSRPartial(output);
        }

        public static org.jpc.SRDumpable loadSR(org.jpc.support.SRLoader input, Integer id) throws IOException
        {
            org.jpc.SRDumpable x = new ExecuteOnlyCodeSegment(input);
            input.endObject();
            return x;
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

        public ExecuteOnlyCodeSegment(Memory memory, int selector, long descriptor)
        {
            super(memory, selector, descriptor);
        }

        public int getType()
        {
            return DESCRIPTOR_TYPE_CODE_DATA | TYPE_CODE;
        }
    }

    public static final class ExecuteReadAccessedCodeSegment extends DefaultProtectedModeSegment
    {
        public void dumpSR(org.jpc.support.SRDumper output) throws IOException
        {
            if(output.dumped(this))
                return;
            dumpSRPartial(output);
            output.endObject();
        }

        public void dumpSRPartial(org.jpc.support.SRDumper output) throws IOException
        {
            super.dumpSRPartial(output);
        }

        public static org.jpc.SRDumpable loadSR(org.jpc.support.SRLoader input, Integer id) throws IOException
        {
            org.jpc.SRDumpable x = new ExecuteReadAccessedCodeSegment(input);
            input.endObject();
            return x;
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

        public ExecuteReadAccessedCodeSegment(Memory memory, int selector, long descriptor)
        {
            super(memory, selector, descriptor);
        }

        public int getType()
        {
            return DESCRIPTOR_TYPE_CODE_DATA | TYPE_CODE | TYPE_CODE_READABLE | TYPE_ACCESSED;
        }
    }

    public static final class ExecuteReadCodeSegment extends DefaultProtectedModeSegment
    {
        public void dumpSR(org.jpc.support.SRDumper output) throws IOException
        {
            if(output.dumped(this))
                return;
            dumpSRPartial(output);
            output.endObject();
        }

        public void dumpSRPartial(org.jpc.support.SRDumper output) throws IOException
        {
            super.dumpSRPartial(output);
        }

        public static org.jpc.SRDumpable loadSR(org.jpc.support.SRLoader input, Integer id) throws IOException
        {
            org.jpc.SRDumpable x = new ExecuteReadCodeSegment(input);
            input.endObject();
            return x;
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

        public ExecuteReadCodeSegment(Memory memory, int selector, long descriptor)
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
        public void dumpSR(org.jpc.support.SRDumper output) throws IOException
        {
            if(output.dumped(this))
                return;
            dumpSRPartial(output);
            output.endObject();
        }

        public void dumpSRPartial(org.jpc.support.SRDumper output) throws IOException
        {
            super.dumpSRPartial(output);
        }

        public static org.jpc.SRDumpable loadSR(org.jpc.support.SRLoader input, Integer id) throws IOException
        {
            org.jpc.SRDumpable x = new ExecuteOnlyConformingAccessedCodeSegment(input);
            input.endObject();
            return x;
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

        public ExecuteOnlyConformingAccessedCodeSegment(Memory memory, int selector, long descriptor)
        {
            super(memory, selector, descriptor);
        }

        public int getType()
        {
            return DESCRIPTOR_TYPE_CODE_DATA | TYPE_CODE | TYPE_CODE_CONFORMING | TYPE_ACCESSED;
        }
    }

    public static final class ExecuteReadConformingAccessedCodeSegment extends DefaultProtectedModeSegment
    {
        public void dumpSR(org.jpc.support.SRDumper output) throws IOException
        {
            if(output.dumped(this))
                return;
            dumpSRPartial(output);
            output.endObject();
        }

        public void dumpSRPartial(org.jpc.support.SRDumper output) throws IOException
        {
            super.dumpSRPartial(output);
        }

        public static org.jpc.SRDumpable loadSR(org.jpc.support.SRLoader input, Integer id) throws IOException
        {
            org.jpc.SRDumpable x = new ExecuteReadConformingAccessedCodeSegment(input);
            input.endObject();
            return x;
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

        public ExecuteReadConformingAccessedCodeSegment(Memory memory, int selector, long descriptor)
        {
            super(memory, selector, descriptor);
        }

        public int getType()
        {
            return DESCRIPTOR_TYPE_CODE_DATA | TYPE_CODE | TYPE_CODE_CONFORMING | TYPE_CODE_READABLE | TYPE_ACCESSED;
        }
    }

    public static final class ExecuteReadConformingCodeSegment extends DefaultProtectedModeSegment
    {
        public void dumpSR(org.jpc.support.SRDumper output) throws IOException
        {
            if(output.dumped(this))
                return;
            dumpSRPartial(output);
            output.endObject();
        }

        public void dumpSRPartial(org.jpc.support.SRDumper output) throws IOException
        {
            super.dumpSRPartial(output);
        }

        public static org.jpc.SRDumpable loadSR(org.jpc.support.SRLoader input, Integer id) throws IOException
        {
            org.jpc.SRDumpable x = new ExecuteReadConformingCodeSegment(input);
            input.endObject();
            return x;
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

        public ExecuteReadConformingCodeSegment(Memory memory, int selector, long descriptor)
        {
            super(memory, selector, descriptor);
        }

        public int getType()
        {
            return DESCRIPTOR_TYPE_CODE_DATA | TYPE_CODE | TYPE_CODE_CONFORMING | TYPE_CODE_READABLE;
        }
    }

    static public abstract class AbstractTSS extends ReadOnlyProtectedModeSegment
    {
        public abstract void dumpSR(org.jpc.support.SRDumper output) throws IOException;

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

        public AbstractTSS(Memory memory, int selector, long descriptor)
        {
            super(memory, selector, descriptor);
        }

        public void saveCPUState(Processor cpu)
        {
            int initialAddress = translateAddressWrite(0);
            memory.setDoubleWord( initialAddress + 32,cpu.eip);
            memory.setDoubleWord( initialAddress + 36,cpu.getEFlags());
            memory.setDoubleWord( initialAddress + 40,cpu.eax);
            memory.setDoubleWord( initialAddress + 44,cpu.ecx);
            memory.setDoubleWord( initialAddress + 48,cpu.edx);
            memory.setDoubleWord( initialAddress + 52,cpu.ebx);
            memory.setDoubleWord( initialAddress + 56,cpu.esp);
            memory.setDoubleWord( initialAddress + 60,cpu.ebp);
            memory.setDoubleWord( initialAddress + 64,cpu.esi);
            memory.setDoubleWord( initialAddress + 68,cpu.edi);
            memory.setDoubleWord( initialAddress + 72,cpu.es.getSelector());
            memory.setDoubleWord( initialAddress + 76,cpu.cs.getSelector());
            memory.setDoubleWord( initialAddress + 80,cpu.ss.getSelector());
            memory.setDoubleWord( initialAddress + 84,cpu.ds.getSelector());
            memory.setDoubleWord( initialAddress + 88,cpu.fs.getSelector());
            memory.setDoubleWord( initialAddress + 92,cpu.gs.getSelector());
        }

        public void restoreCPUState(Processor cpu)
        {
            int initialAddress = translateAddressRead(0);
            cpu.eip = memory.getDoubleWord( initialAddress + 32);
            cpu.setEFlags(memory.getDoubleWord( initialAddress + 36));
            cpu.eax = memory.getDoubleWord( initialAddress + 40);
            cpu.ecx = memory.getDoubleWord( initialAddress + 44);
            cpu.edx = memory.getDoubleWord( initialAddress + 48);
            cpu.ebx = memory.getDoubleWord( initialAddress + 52);
            cpu.esp = memory.getDoubleWord( initialAddress + 56);
            cpu.ebp = memory.getDoubleWord( initialAddress + 60);
            cpu.esi = memory.getDoubleWord( initialAddress + 64);
            cpu.edi = memory.getDoubleWord( initialAddress + 68);
            cpu.es = cpu.getSegment(0xFFFF & memory.getDoubleWord( initialAddress + 72));
            cpu.cs = cpu.getSegment(0xFFFF & memory.getDoubleWord( initialAddress + 76));
            cpu.ss = cpu.getSegment(0xFFFF & memory.getDoubleWord( initialAddress + 80));
            cpu.ds = cpu.getSegment(0xFFFF & memory.getDoubleWord( initialAddress + 84));
            cpu.fs = cpu.getSegment(0xFFFF & memory.getDoubleWord( initialAddress + 88));
            cpu.gs = cpu.getSegment(0xFFFF & memory.getDoubleWord( initialAddress + 92));
            // non dynamic fields
            cpu.ldtr = cpu.getSegment(0xFFFF & memory.getDoubleWord( initialAddress + 96));
            cpu.setCR3(memory.getDoubleWord( initialAddress + 28));
        }

        public byte getByte(int offset)
        {
            boolean isSup = ((LinearAddressSpace)memory).isSupervisor();
            try {
                ((LinearAddressSpace)memory).setSupervisor(true);
                return super.getByte(offset);
            } finally {
                ((LinearAddressSpace)memory).setSupervisor(isSup);
            }
        }

        public short getWord(int offset)
        {
            boolean isSup = ((LinearAddressSpace)memory).isSupervisor();
            try {
                ((LinearAddressSpace)memory).setSupervisor(true);
                return super.getWord(offset);
            } finally {
                ((LinearAddressSpace)memory).setSupervisor(isSup);
            }
        }

        public int getDoubleWord(int offset)
        {
            boolean isSup = ((LinearAddressSpace)memory).isSupervisor();
            try {
                ((LinearAddressSpace)memory).setSupervisor(true);
                return super.getDoubleWord(offset);
            } finally {
                ((LinearAddressSpace)memory).setSupervisor(isSup);
            }
        }

        public long getQuadWord(int offset)
        {
            boolean isSup = ((LinearAddressSpace)memory).isSupervisor();
            try {
                ((LinearAddressSpace)memory).setSupervisor(true);
                return super.getQuadWord(offset);
            } finally {
                ((LinearAddressSpace)memory).setSupervisor(isSup);
            }
        }

    }

    public static final class Available32BitTSS extends AbstractTSS
    {
        public void dumpSR(org.jpc.support.SRDumper output) throws IOException
        {
            if(output.dumped(this))
                return;
            dumpSRPartial(output);
            output.endObject();
        }

        public void dumpSRPartial(org.jpc.support.SRDumper output) throws IOException
        {
            super.dumpSRPartial(output);
        }

        public static org.jpc.SRDumpable loadSR(org.jpc.support.SRLoader input, Integer id) throws IOException
        {
            org.jpc.SRDumpable x = new Available32BitTSS(input);
            input.endObject();
            return x;
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

        public Available32BitTSS(Memory memory, int selector, long descriptor)
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
        public void dumpSR(org.jpc.support.SRDumper output) throws IOException
        {
            if(output.dumped(this))
                return;
            dumpSRPartial(output);
            output.endObject();
        }

        public void dumpSRPartial(org.jpc.support.SRDumper output) throws IOException
        {
            super.dumpSRPartial(output);
        }

        public static org.jpc.SRDumpable loadSR(org.jpc.support.SRLoader input, Integer id) throws IOException
        {
            org.jpc.SRDumpable x = new Busy32BitTSS(input);
            input.endObject();
            return x;
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

        public Busy32BitTSS(Memory memory, int selector, long descriptor)
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
        public void dumpSR(org.jpc.support.SRDumper output) throws IOException
        {
            if(output.dumped(this))
                return;
            dumpSRPartial(output);
            output.endObject();
        }

        public void dumpSRPartial(org.jpc.support.SRDumper output) throws IOException
        {
            super.dumpSRPartial(output);
        }

        public static org.jpc.SRDumpable loadSR(org.jpc.support.SRLoader input, Integer id) throws IOException
        {
            org.jpc.SRDumpable x = new LDT(input);
            input.endObject();
            return x;
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

        public LDT(Memory memory, int selector, long descriptor)
        {
            super(memory, selector, descriptor);
        }

        public int getType()
        {
            return 0x02;
        }
    }

    public static class GateSegment extends ReadOnlyProtectedModeSegment
    {
        private int targetSegment, targetOffset;

        public void dumpSR(org.jpc.support.SRDumper output) throws IOException
        {
            if(output.dumped(this))
                return;
            dumpSRPartial(output);
            output.endObject();
        }

        public void dumpSRPartial(org.jpc.support.SRDumper output) throws IOException
        {
            super.dumpSRPartial(output);
            output.dumpInt(targetSegment);
            output.dumpInt(targetOffset);
        }

        public static org.jpc.SRDumpable loadSR(org.jpc.support.SRLoader input, Integer id) throws IOException
        {
            org.jpc.SRDumpable x = new GateSegment(input);
            input.endObject();
            return x;
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

        public GateSegment(Memory memory, int selector, long descriptor)
        {
            super(memory, selector, descriptor);

            targetSegment = (int)((descriptor >> 16) & 0xffff);
            targetOffset = (int)((descriptor & 0xffff) | ((descriptor >>> 32) & 0xffff0000));
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
        public void dumpSR(org.jpc.support.SRDumper output) throws IOException
        {
            if(output.dumped(this))
                return;
            dumpSRPartial(output);
            output.endObject();
        }

        public void dumpSRPartial(org.jpc.support.SRDumper output) throws IOException
        {
            super.dumpSRPartial(output);
        }

        public static org.jpc.SRDumpable loadSR(org.jpc.support.SRLoader input, Integer id) throws IOException
        {
            org.jpc.SRDumpable x = new TaskGate(input);
            input.endObject();
            return x;
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

        public TaskGate(Memory memory, int selector, long descriptor)
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
        public void dumpSR(org.jpc.support.SRDumper output) throws IOException
        {
            if(output.dumped(this))
                return;
            dumpSRPartial(output);
            output.endObject();
        }

        public void dumpSRPartial(org.jpc.support.SRDumper output) throws IOException
        {
            super.dumpSRPartial(output);
        }

        public static org.jpc.SRDumpable loadSR(org.jpc.support.SRLoader input, Integer id) throws IOException
        {
            org.jpc.SRDumpable x = new InterruptGate32Bit(input);
            input.endObject();
            return x;
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

        public InterruptGate32Bit(Memory memory, int selector, long descriptor)
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
        public void dumpSR(org.jpc.support.SRDumper output) throws IOException
        {
            if(output.dumped(this))
                return;
            dumpSRPartial(output);
            output.endObject();
        }

        public void dumpSRPartial(org.jpc.support.SRDumper output) throws IOException
        {
            super.dumpSRPartial(output);
        }

        public static org.jpc.SRDumpable loadSR(org.jpc.support.SRLoader input, Integer id) throws IOException
        {
            org.jpc.SRDumpable x = new InterruptGate16Bit(input);
            input.endObject();
            return x;
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

        public InterruptGate16Bit(Memory memory, int selector, long descriptor)
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
        public void dumpSR(org.jpc.support.SRDumper output) throws IOException
        {
            if(output.dumped(this))
                return;
            dumpSRPartial(output);
            output.endObject();
        }

        public void dumpSRPartial(org.jpc.support.SRDumper output) throws IOException
        {
            super.dumpSRPartial(output);
        }

        public static org.jpc.SRDumpable loadSR(org.jpc.support.SRLoader input, Integer id) throws IOException
        {
            org.jpc.SRDumpable x = new TrapGate32Bit(input);
            input.endObject();
            return x;
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

        public TrapGate32Bit(Memory memory, int selector, long descriptor)
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
        public void dumpSR(org.jpc.support.SRDumper output) throws IOException
        {
            if(output.dumped(this))
                return;
            dumpSRPartial(output);
            output.endObject();
        }

        public void dumpSRPartial(org.jpc.support.SRDumper output) throws IOException
        {
            super.dumpSRPartial(output);
        }

        public static org.jpc.SRDumpable loadSR(org.jpc.support.SRLoader input, Integer id) throws IOException
        {
            org.jpc.SRDumpable x = new TrapGate16Bit(input);
            input.endObject();
            return x;
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

        public TrapGate16Bit(Memory memory, int selector, long descriptor)
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
        private int parameterCount;

        public void dumpSR(org.jpc.support.SRDumper output) throws IOException
        {
            if(output.dumped(this))
                return;
            dumpSRPartial(output);
            output.endObject();
        }

        public void dumpSRPartial(org.jpc.support.SRDumper output) throws IOException
        {
            super.dumpSRPartial(output);
            output.dumpInt(parameterCount);
        }

        public static org.jpc.SRDumpable loadSR(org.jpc.support.SRLoader input, Integer id) throws IOException
        {
            org.jpc.SRDumpable x = new CallGate32Bit(input);
            input.endObject();
            return x;
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

        public CallGate32Bit(Memory memory, int selector, long descriptor)
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
        private int parameterCount;

        public void dumpSR(org.jpc.support.SRDumper output) throws IOException
        {
            if(output.dumped(this))
                return;
            dumpSRPartial(output);
            output.endObject();
        }

        public void dumpSRPartial(org.jpc.support.SRDumper output) throws IOException
        {
            super.dumpSRPartial(output);
            output.dumpInt(parameterCount);
        }

        public static org.jpc.SRDumpable loadSR(org.jpc.support.SRLoader input, Integer id) throws IOException
        {
            org.jpc.SRDumpable x = new CallGate16Bit(input);
            input.endObject();
            return x;
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


        public CallGate16Bit(Memory memory, int selector, long descriptor)
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

    public static final class Available16BitTSS extends DefaultProtectedModeSegment
    {
        public void dumpSR(org.jpc.support.SRDumper output) throws IOException
        {
            if(output.dumped(this))
                return;
            dumpSRPartial(output);
            output.endObject();
        }

        public void dumpSRPartial(org.jpc.support.SRDumper output) throws IOException
        {
            super.dumpSRPartial(output);
        }

        public static org.jpc.SRDumpable loadSR(org.jpc.support.SRLoader input, Integer id) throws IOException
        {
            org.jpc.SRDumpable x = new Available16BitTSS(input);
            input.endObject();
            return x;
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

        public Available16BitTSS(Memory memory, int selector, long descriptor)
        {
            super(memory, selector, descriptor);
        }

        public int getType()
        {
            return 0x01;
        }
    }

    public static final class Busy16BitTSS extends DefaultProtectedModeSegment
    {
        public void dumpSR(org.jpc.support.SRDumper output) throws IOException
        {
            if(output.dumped(this))
                return;
            dumpSRPartial(output);
            output.endObject();
        }

        public void dumpSRPartial(org.jpc.support.SRDumper output) throws IOException
        {
            super.dumpSRPartial(output);
        }

        public static org.jpc.SRDumpable loadSR(org.jpc.support.SRLoader input, Integer id) throws IOException
        {
            org.jpc.SRDumpable x = new Busy16BitTSS(input);
            input.endObject();
            return x;
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

        public Busy16BitTSS(Memory memory, int selector, long descriptor)
        {
            super(memory, selector, descriptor);
        }

        public int getType()
        {
            return 0x03;
        }
    }

    public static final class NullSegment extends DefaultSegment
    {
        public void dumpSR(org.jpc.support.SRDumper output) throws IOException
        {
            if(output.dumped(this))
                return;
            dumpSRPartial(output);
            output.endObject();
        }

        public void dumpSRPartial(org.jpc.support.SRDumper output) throws IOException
        {
            super.dumpSRPartial(output);
        }

        public static org.jpc.SRDumpable loadSR(org.jpc.support.SRLoader input, Integer id) throws IOException
        {
            org.jpc.SRDumpable x = new NullSegment(input);
            input.endObject();
            return x;
        }

        public NullSegment(org.jpc.support.SRLoader input) throws IOException
        {
            super(input);
        }

        public NullSegment()
        {
        }

        public int dumpState(DataOutput output)  throws IOException
        {
            output.writeInt(4);
            return 0;
        }

        public void dumpStatusPartial(org.jpc.support.StatusDumper output)
        {
            super.dumpStatusPartial(output);
        }

        public void dumpStatus(org.jpc.support.StatusDumper output)
        {
            if(output.dumped(this))
                return;
            output.println("#" + output.objectNumber(this) + ": NullSegment:");
            dumpStatusPartial(output);
            output.endObject();
        }

        public void loadState(DataInput input) {}

        public int getType()
        {
            throw new ProcessorException(Processor.PROC_EXCEPTION_GP, 0, true);
        }

        public int getSelector()
        {
            return 0;
        }

        public void checkAddress(int offset)
        {
            throw new ProcessorException(Processor.PROC_EXCEPTION_GP, 0, true);
        }

        public int translateAddressRead(int offset)
        {
            throw new ProcessorException(Processor.PROC_EXCEPTION_GP, 0, true);
        }

        public int translateAddressWrite(int offset)
        {
            throw new ProcessorException(Processor.PROC_EXCEPTION_GP, 0, true);
        }

        public void invalidateAddress(int offset)
        {
            throw new ProcessorException(Processor.PROC_EXCEPTION_GP, 0, true);
        }
    }

    public static Segment createProtectedModeSegment(Memory memory, int selector, long descriptor)
    {
        switch ((int)((descriptor & (DESCRIPTOR_TYPE | SEGMENT_TYPE)) >>> 40)) {

            // System Segments
        default:
        case 0x00: //Reserved
        case 0x08: //Reserved
        case 0x0a: //Reserved
        case 0x0d: //Reserved
            System.out.println(Integer.toHexString(selector)+"  "+Long.toString(descriptor, 16));
            throw new IllegalStateException("Attempted To Construct Reserved Segment Type");
        case 0x01: //System Segment: 16-bit TSS (Available)
            return new Available16BitTSS(memory, selector, descriptor);
        case 0x02: //System Segment: LDT
            return new LDT(memory, selector, descriptor);
        case 0x03: //System Segment: 16-bit TSS (Busy)
            return new Busy16BitTSS(memory, selector, descriptor);
        case 0x04: //System Segment: 16-bit Call Gate
            return new CallGate16Bit(memory, selector, descriptor);
        case 0x05: //System Segment: Task Gate
            return new TaskGate(memory, selector, descriptor);
        case 0x06: //System Segment: 16-bit Interrupt Gate
            return new InterruptGate16Bit(memory, selector, descriptor);
        case 0x07: //System Segment: 16-bit Trap Gate
            return new TrapGate16Bit(memory, selector, descriptor);
        case 0x09: //System Segment: 32-bit TSS (Available)
            return new Available32BitTSS(memory, selector, descriptor);
        case 0x0b: //System Segment: 32-bit TSS (Busy)
            return new Busy32BitTSS(memory, selector, descriptor);
        case 0x0c: //System Segment: 32-bit Call Gate
            return new CallGate32Bit(memory, selector, descriptor);
        case 0x0e: //System Segment: 32-bit Interrupt Gate
            return new InterruptGate32Bit(memory, selector, descriptor);
        case 0x0f: //System Segment: 32-bit Trap Gate
            return new TrapGate32Bit(memory, selector, descriptor);

            // Code and Data Segments
        case 0x10: //Data Segment: Read-Only
            return new ReadOnlyDataSegment(memory, selector, descriptor);
        case 0x11: //Data Segment: Read-Only, Accessed
            return new ReadOnlyAccessedDataSegment(memory, selector, descriptor);
        case 0x12: //Data Segment: Read/Write
            return new ReadWriteDataSegment(memory, selector, descriptor);
        case 0x13: //Data Segment: Read/Write, Accessed
            return new ReadWriteAccessedDataSegment(memory, selector, descriptor);
        case 0x14: //Data Segment: Read-Only, Expand-Down
            throw new IllegalStateException("Unimplemented Data Segment: Read-Only, Expand-Down");
        case 0x15: //Data Segment: Read-Only, Expand-Down, Accessed
            throw new IllegalStateException("Unimplemented Data Segment: Read-Only, Expand-Down, Accessed");
        case 0x16: //Data Segment: Read/Write, Expand-Down
            throw new IllegalStateException("Unimplemented Data Segment: Read/Write, Expand-Down");
        case 0x17: //Data Segment: Read/Write, Expand-Down, Accessed
            throw new IllegalStateException("Unimplemented Data Segment: Read/Write, Expand-Down, Accessed");

        case 0x18: //Code, Execute-Only
            return new ExecuteOnlyCodeSegment(memory, selector, descriptor);
        case 0x19: //Code, Execute-Only, Accessed
            throw new IllegalStateException("Unimplemented Code Segment: Execute-Only, Accessed");
        case 0x1a: //Code, Execute/Read
            return new ExecuteReadCodeSegment(memory, selector, descriptor);
        case 0x1b: //Code, Execute/Read, Accessed
            return new ExecuteReadAccessedCodeSegment(memory, selector, descriptor);
        case 0x1c: //Code: Execute-Only, Conforming
            throw new IllegalStateException("Unimplemented Code Segment: Execute-Only, Conforming");
        case 0x1d: //Code: Execute-Only, Conforming, Accessed
            return new ExecuteOnlyConformingAccessedCodeSegment(memory, selector, descriptor);
        case 0x1e: //Code: Execute/Read, Conforming
            return new ExecuteReadConformingCodeSegment(memory, selector, descriptor);
        case 0x1f: //Code: Execute/Read, Conforming, Accessed
            return new ExecuteReadConformingAccessedCodeSegment(memory, selector, descriptor);
        }
    }
}
