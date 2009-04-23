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

package org.jpc.emulator.memory.codeblock.cache;

import org.jpc.emulator.memory.*;
import org.jpc.emulator.memory.codeblock.*;
import org.jpc.emulator.memory.codeblock.optimised.*;
import org.jpc.emulator.processor.*;

public class CachedInstructionCompiler implements CodeBlockCompiler, InstructionSource, ObjectTreeCache
{
    private CodeBlockCompiler underlying;
    private int operationBufferOffset;
    private int microcodeBufferOffset;
    private int[] bufferMicrocodes;
    private int[] bufferLength;
    private int[] bufferX86Length;

    private int operationReplayIndex;
    private int microcodeReplayIndex;

    private ObjectTreeStateMachine realModeTree;
    private ObjectTreeStateMachine protectedModeTree;
    private ObjectTreeStateMachine virtual8086ModeTree;
    private int foundRealModeBlockCount;
    private int addedRealModeBlockCount;
    private int foundProtectedModeBlockCount;
    private int addedProtectedModeBlockCount;
    private int foundVirtual8086ModeBlockCount;
    private int addedVirtual8086ModeBlockCount;

    public CachedInstructionCompiler(CodeBlockCompiler backup)
    {
	bufferMicrocodes = new int[100];
	bufferLength = new int[100];
	bufferX86Length = new int[100];
        microcodeBufferOffset = 0;
        operationBufferOffset = 0;

	this.underlying = backup;
 
        realModeTree = new ObjectTreeStateMachine();
        protectedModeTree = new ObjectTreeStateMachine();
        virtual8086ModeTree = new ObjectTreeStateMachine();
        foundRealModeBlockCount = addedRealModeBlockCount = 0;
        foundProtectedModeBlockCount = addedProtectedModeBlockCount = 0;
        foundVirtual8086ModeBlockCount = addedVirtual8086ModeBlockCount = 0;
    }

    public boolean getNext()
    {
        operationReplayIndex++;
        return  (operationReplayIndex < operationBufferOffset);
    }
 
    public int getMicrocode()
    {
        return bufferMicrocodes[microcodeReplayIndex++];
    }
 
    public int getLength()
    {
        return bufferLength[operationReplayIndex];
    }
 
    public int getX86Length()
    {
        return bufferX86Length[operationReplayIndex];
    }

    public CodeBlock getCodeBlock(ObjectTreeStateMachine codeBlockTree, InstructionSource source)
    {
        CodeBlock lastGoodBlock = null;
        microcodeBufferOffset = 0;
        operationBufferOffset = 0;

        while(source.getNext())
        {
            int uCodeLength = source.getLength();
            try {
                bufferLength[operationBufferOffset] = uCodeLength;
                bufferX86Length[operationBufferOffset] = source.getX86Length();
            } catch (ArrayIndexOutOfBoundsException e) {
                int[] newLength = new int[bufferLength.length * 2];
                int[] newX86Length = new int[bufferX86Length.length * 2];
                System.arraycopy(bufferLength, 0, newLength, 0, bufferLength.length);
                System.arraycopy(bufferX86Length, 0, newX86Length, 0, bufferX86Length.length);
                bufferLength = newLength;
                bufferX86Length = newX86Length;
                bufferLength[operationBufferOffset] = uCodeLength;
                bufferX86Length[operationBufferOffset] = source.getX86Length();
            }
            operationBufferOffset++;


            for(int i = 0; i < uCodeLength; i++)
            {
                // get ucode and step through tree, see if cb present
                int uCode = source.getMicrocode();

                codeBlockTree.stepTree(uCode);
                CodeBlock outputCodeBlock = (CodeBlock) codeBlockTree.getObjectAtState();
                if (outputCodeBlock != null)
                    lastGoodBlock = outputCodeBlock;

                // if no cb, buffer ucodes in order to pass on to backup factory
                try 
                {
                    bufferMicrocodes[microcodeBufferOffset] = uCode;
                } 
                catch (ArrayIndexOutOfBoundsException e) 
                {
                    int[] newMicrocodes = new int[bufferMicrocodes.length * 2];
                    System.arraycopy(bufferMicrocodes, 0, newMicrocodes, 0, bufferMicrocodes.length);
                    bufferMicrocodes = newMicrocodes;
                    bufferMicrocodes[microcodeBufferOffset] = uCode;
                }
                microcodeBufferOffset++;
            }
        }
        return lastGoodBlock;
    }


    public RealModeCodeBlock getRealModeCodeBlock(InstructionSource source)
    {
        realModeTree.resetTreeState();
        CodeBlock outputCodeBlock = getCodeBlock(realModeTree, source);

        if (outputCodeBlock == null)
        {
            operationReplayIndex = -1;
            microcodeReplayIndex = 0;
            outputCodeBlock = underlying.getRealModeCodeBlock(this);
            if (operationBufferOffset > 0) 
            {
                realModeTree.setObjectAtState(outputCodeBlock);
                addedRealModeBlockCount++;
            }
        }
        else 
        {
            foundRealModeBlockCount++;
        }
        
        return (RealModeCodeBlock) outputCodeBlock;
    }


    public ProtectedModeCodeBlock getProtectedModeCodeBlock(InstructionSource source)
    {
        protectedModeTree.resetTreeState();
        CodeBlock outputCodeBlock = getCodeBlock(protectedModeTree, source);

        if (outputCodeBlock == null)
        {
            operationReplayIndex = -1;
            microcodeReplayIndex = 0;
            outputCodeBlock = underlying.getProtectedModeCodeBlock(this);
            if (operationBufferOffset > 0)
            {
                protectedModeTree.setObjectAtState(outputCodeBlock);
                addedProtectedModeBlockCount++;
            }
        }
        else
        {
            foundProtectedModeBlockCount++;
        }

        return (ProtectedModeCodeBlock) outputCodeBlock;
    }

    public Virtual8086ModeCodeBlock getVirtual8086ModeCodeBlock(InstructionSource source)
    {
        virtual8086ModeTree.resetTreeState();
        CodeBlock outputCodeBlock = getCodeBlock(virtual8086ModeTree, source);

        if (outputCodeBlock == null)
        {
            operationReplayIndex = -1;
            microcodeReplayIndex = 0;
            outputCodeBlock = underlying.getVirtual8086ModeCodeBlock(this);
            if (operationBufferOffset > 0)
            {
                virtual8086ModeTree.setObjectAtState(outputCodeBlock);
                addedVirtual8086ModeBlockCount++;
            }
        }
        else
        {
            foundVirtual8086ModeBlockCount++;
        }

        return (Virtual8086ModeCodeBlock) outputCodeBlock;
    }



    public ObjectTreeStateMachine getObjectTree()
    {
        return realModeTree;
    }

    public long getAddedCount()
    {
        return addedRealModeBlockCount;
    }

    public long getFoundCount()
    {
        return foundRealModeBlockCount;
    }

}
