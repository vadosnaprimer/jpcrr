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

package org.jpc.debugger;

import java.util.*;
import java.io.*;

import org.jpc.emulator.*;
import org.jpc.emulator.processor.*;
import org.jpc.emulator.motherboard.*;
import org.jpc.emulator.memory.*;
import org.jpc.emulator.memory.codeblock.*;
import org.jpc.emulator.memory.codeblock.optimised.*;

public class CodeBlockRecord
{
    private long blockCount, instructionCount, decodedCount, optimisedBlockCount;

    private int maxBlockSize;
    private PC pc;
    private Processor processor;
    private LinearAddressSpace memory;
    private CodeBlock[] trace;
    private int[] addresses;
    
    private CodeBlockListener listener;

    public CodeBlockRecord(PC pc)
    {
        this.pc = pc;
        this.memory = pc.getLinearMemory();
        this.processor = pc.getProcessor();
        listener = null;

        blockCount = 0;
        decodedCount = 0;
        instructionCount = 0;
        optimisedBlockCount = 0;
        maxBlockSize = 1000;

        trace = new CodeBlock[5000];
        addresses = new int[trace.length];
    }

    public void setCodeBlockListener(CodeBlockListener l)
    {
        listener = l;
    }

    public int getMaximumBlockSize()
    {
        return maxBlockSize;
    }

    public void setMaximumBlockSize(int value)
    {
        if (value == maxBlockSize)
            return;
        maxBlockSize = value;
    }

    public boolean isDecodedAt(int address)
    {
        return true;
    }

    public CodeBlock decodeBlockAt(int address, boolean force)
    {
        CodeBlock block = pc.decodeCodeBlockAt(address);
	//CodeBlock block = null;

        decodedCount += block.getX86Count();
        if (block instanceof RealModeUBlock)
            optimisedBlockCount++;
            
        if (listener != null)
            listener.codeBlockDecoded(address, memory, block);

        return block;
    }
    
    public CodeBlock executeBlock()
    {
        int ip = processor.getInstructionPointer();
        CodeBlock block = decodeBlockAt(ip, false);
       
       	try
	{	    
            if (pc.executeStep() < 0)
                return null;
	    return block;
        }
	finally
	{
            if (listener != null)
                listener.codeBlockExecuted(ip, memory, block);
            trace[(int) (blockCount % trace.length)] = block;
            addresses[(int) (blockCount % trace.length)] = ip;
            blockCount++;
            instructionCount += block.getX86Count();
	}
    }

    public CodeBlock advanceDecode()
    {
        return advanceDecode(false);
    }

    public CodeBlock advanceDecode(boolean force)
    {
        int ip = processor.getInstructionPointer();
        try
        {
            return decodeBlockAt(ip, force);
        }
        catch (ProcessorException e)
        {
            processor.handleProtectedModeException(e.getVector(), e.hasErrorCode(), e.getErrorCode());   
            return advanceDecode();
        }
    }

    public void reset()
    {
        Arrays.fill(trace, null);
        instructionCount = 0;
        blockCount = 0;
        decodedCount = 0;
    }

    public int getBlockAddress(int row)
    {
        if (blockCount <= trace.length)
            return addresses[row];

        row += (blockCount % trace.length);
        if (row >= trace.length)
            row -= trace.length;

        return addresses[row];
    }

    public CodeBlock getTraceBlockAt(int row)
    {
        if (blockCount <= trace.length)
            return trace[row];

        row += (blockCount % trace.length);
        if (row >= trace.length)
            row -= trace.length;

        return trace[row];
    }

    public int getRowForIndex(long index)
    {
        if (blockCount <= trace.length)
            return (int) index;

        long offset = blockCount - index - 1;
        if ((offset < 0) || (offset >= trace.length))
            return -1;
        
        return trace.length - 1 - (int) offset;
    }

    public long getIndexNumberForRow(int row)
    {
        if (blockCount <= trace.length)
            return row;

        return (int) (blockCount - trace.length + row);
    }

    public int getTraceLength()
    {
        if (blockCount <= trace.length)
            return (int) blockCount;
        return trace.length;
    }

    public int getMaximumTrace()
    {
        return trace.length;
    }

    public long getExecutedBlockCount()
    {
        return blockCount;
    }

    public long getInstructionCount()
    {
        return instructionCount;
    }

    public long getDecodedCount()
    {
        return decodedCount;
    }

    public long getOptimisedBlockCount()
    {
        return optimisedBlockCount;
    }
}
