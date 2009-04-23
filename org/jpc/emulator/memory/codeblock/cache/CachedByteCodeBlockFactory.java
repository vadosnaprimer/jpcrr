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

import org.jpc.emulator.memory.codeblock.*;

public class CachedByteCodeBlockFactory implements CodeBlockFactory, ByteSource, ObjectTreeCache
{
    private Decoder decoder;
    private CodeBlockCompiler compiler;
    //private CodeBlockFactory factory;
    private ByteSource source;

    private int bufferOffset;
    private byte[] bufferBytes;
    private int replayIndex;
    private ObjectTreeStateMachine codeBlockTree;

    protected ObjectTreeStateMachine realModeTree;
    protected ObjectTreeStateMachine protectedModeTree;
    protected ObjectTreeStateMachine virtual8086ModeTree;

    protected int foundRealModeBlockCount, addedRealModeBlockCount;
    protected int foundProtectedModeBlockCount, addedProtectedModeBlockCount;
    protected int foundVirtual8086ModeBlockCount, addedVirtual8086ModeBlockCount;
 
    public CachedByteCodeBlockFactory(Decoder decoder, CodeBlockCompiler compiler)
    {
	//this.factory = factory;
	this.decoder = decoder;
	this.compiler = compiler;

	bufferBytes = new byte[100];
        bufferOffset = 0;
 
        realModeTree = new ObjectTreeStateMachine();
        protectedModeTree = new ObjectTreeStateMachine();
        foundRealModeBlockCount = addedRealModeBlockCount = 0;
        foundProtectedModeBlockCount = addedProtectedModeBlockCount = 0;
    }

    public byte getByte()
    {
        if (replayIndex < bufferOffset)
            return bufferBytes[replayIndex++];
        else
        {
            byte b = source.getByte();
            codeBlockTree.stepTree(b);
            return b;
        }
    }

    public CodeBlock getCodeBlock(ObjectTreeStateMachine codeBlockTree)
    {
        bufferOffset = 0;
        boolean byteInTree = true;

        while(byteInTree)
        {
            // get ucode and step through tree, see if cb present
            byte b = source.getByte();
            byteInTree = codeBlockTree.stepTree(b);
            CodeBlock outputCodeBlock = (CodeBlock) codeBlockTree.getObjectAtState();
            if (outputCodeBlock != null)
                return outputCodeBlock;

            // if no cb, buffer bytes in order to pass on to backup compiler
            try 
            {
                bufferBytes[bufferOffset] = b;
            } 
            catch (ArrayIndexOutOfBoundsException e) 
            {
                byte[] newBytes = new byte[bufferBytes.length * 2];
                System.arraycopy(bufferBytes, 0, newBytes, 0, bufferBytes.length);
                bufferBytes = newBytes;
                bufferBytes[bufferOffset] = b;
            }
            bufferOffset++;
        }
        return null;
    }

    public RealModeCodeBlock getRealModeCodeBlock(ByteSource source)
    {
        this.source = source;

        realModeTree.resetTreeState();
        CodeBlock outputCodeBlock = getCodeBlock(realModeTree);

        if (outputCodeBlock == null)
        {
            replayIndex = 0;
            codeBlockTree = realModeTree;
            //outputCodeBlock = factory.getRealModeCodeBlock(this);
	    outputCodeBlock = compiler.getRealModeCodeBlock(decoder.decodeReal(this));
            if (bufferOffset > 0) 
            {
                realModeTree.setObjectAtState(outputCodeBlock);
                addedRealModeBlockCount++;
            }
        }
        else 
            foundRealModeBlockCount++;

//         System.out.println("real found: " + foundRealModeBlockCount + "\tadded: " + addedRealModeBlockCount);
        return (RealModeCodeBlock) outputCodeBlock;
    }

    public ProtectedModeCodeBlock getProtectedModeCodeBlock(ByteSource source, boolean operandSize)
    {
        this.source = source;

        protectedModeTree.resetTreeState();
        CodeBlock outputCodeBlock = getCodeBlock(protectedModeTree);

        if (outputCodeBlock == null)
        {
            replayIndex = 0;
            codeBlockTree = protectedModeTree;
            //outputCodeBlock = factory.getProtectedModeCodeBlock(this, operandSize);
	    outputCodeBlock = compiler.getProtectedModeCodeBlock(decoder.decodeProtected(this, operandSize));
            if (bufferOffset > 0)
            {
                protectedModeTree.setObjectAtState(outputCodeBlock);
                addedProtectedModeBlockCount++;
            }
        }
        else
            foundProtectedModeBlockCount++;

//         System.out.println("prot found: " + foundProtectedModeBlockCount + "\tadded: " + addedProtectedModeBlockCount);
        return (ProtectedModeCodeBlock) outputCodeBlock;
    }

    public Virtual8086ModeCodeBlock getVirtual8086ModeCodeBlock(ByteSource source)
    {
        this.source = source;

        virtual8086ModeTree.resetTreeState();
        CodeBlock outputCodeBlock = getCodeBlock(virtual8086ModeTree);

        if (outputCodeBlock == null)
        {
            replayIndex = 0;
            codeBlockTree = virtual8086ModeTree;
            //outputCodeBlock = factory.getVirtual8086ModeCodeBlock(this, operandSize);
	    outputCodeBlock = compiler.getVirtual8086ModeCodeBlock(decoder.decodeVirtual8086(this));
            if (bufferOffset > 0)
            {
                virtual8086ModeTree.setObjectAtState(outputCodeBlock);
                addedVirtual8086ModeBlockCount++;
            }
        }
        else
            foundVirtual8086ModeBlockCount++;

//         System.out.println("prot found: " + foundVirtual8086ModeBlockCount + "\tadded: " + addedVirtual8086ModeBlockCount);
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

    public boolean skip(int count)
    {
        throw new IllegalStateException("Skip not implemented on "+getClass());
    }

    public boolean rewind(int count)
    {
        throw new IllegalStateException("rewind not implemented on "+getClass());
    }

    public boolean reset()
    {
        throw new IllegalStateException("reset not implemented on "+getClass());
    }
}
