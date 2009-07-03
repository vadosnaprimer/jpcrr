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

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jpc.emulator.memory.codeblock.fastcompiler.FASTCompiler;
import org.jpc.emulator.memory.codeblock.fastcompiler.prot.ProtectedModeTemplateBlock;
import org.jpc.emulator.memory.codeblock.fastcompiler.real.RealModeTemplateBlock;

/**
 *
 * @author Ian Preston
 */
public class CachedCodeBlockCompiler implements CodeBlockCompiler
{
    private boolean loadedClass = false;

    public RealModeCodeBlock getRealModeCodeBlock(InstructionSource source)
    {
        try
        {
            int[] newMicrocodes = getMicrocodesArray(source);
            String className = "org.jpc.dynamic.FAST_RM_" + FASTCompiler.getHash(newMicrocodes);
            
            Class oldClass = Class.forName(className);
            int[] oldMicrocodes = ((RealModeTemplateBlock) oldClass.newInstance()).getMicrocodes();
            boolean same = true;
            if (oldMicrocodes.length != newMicrocodes.length)
                same = false;
            else
            {
                for (int i = 0; i < oldMicrocodes.length; i++)
                {
                    if (oldMicrocodes[i] != newMicrocodes[i])
                    {
                        same = false;
                    }
                }
            }

            if (same) {
                if (!loadedClass)
                {
                    loadedClass = true;
                    System.out.println("Loaded Precompiled Class");
                }
                return (RealModeCodeBlock) oldClass.newInstance();
            } else
                return null;
        } 
        catch (InstantiationException ex)
        {
            Logger.getLogger(CachedCodeBlockCompiler.class.getName()).log(Level.SEVERE, null, ex);
        } 
        catch (IllegalAccessException ex)
        {
            Logger.getLogger(CachedCodeBlockCompiler.class.getName()).log(Level.SEVERE, null, ex);
        } 
        catch (VerifyError e)
        {
            e.printStackTrace();
        } 
        catch (ClassNotFoundException e) {}
        return null;
    }

    public ProtectedModeCodeBlock getProtectedModeCodeBlock(InstructionSource source)
    {
        try
        {
            int[] newMicrocodes = getMicrocodesArray(source);
            String className = "org.jpc.dynamic.FAST_PM_" + FASTCompiler.getHash(newMicrocodes);
            Class oldClass = Class.forName(className);
            int[] oldMicrocodes = ((ProtectedModeTemplateBlock) oldClass.newInstance()).getMicrocodes();
            boolean same = true;
            if (oldMicrocodes.length != newMicrocodes.length)
            {
                same = false;
            } else
            {
                for (int i = 0; i < oldMicrocodes.length; i++)
                {
                    if (oldMicrocodes[i] != newMicrocodes[i])
                    {
                        same = false;
                    }
                }
            }
            if (same)
            {
                if (!loadedClass)
                {
                    loadedClass = true;
                    System.out.println("Loaded Precompiled Class");
                }
                return (ProtectedModeCodeBlock) oldClass.newInstance();
            } else
            {
                return null;
            }
        } catch (InstantiationException ex)
        {
            Logger.getLogger(CachedCodeBlockCompiler.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IllegalAccessException ex)
        {
            Logger.getLogger(CachedCodeBlockCompiler.class.getName()).log(Level.SEVERE, null, ex);
        } catch (VerifyError e)
        {
            e.printStackTrace();
        } catch (ClassNotFoundException e)
        {
        }
        return null;
    }

    public Virtual8086ModeCodeBlock getVirtual8086ModeCodeBlock(InstructionSource source)
    {
        return null;
    }

    private int[] getMicrocodesArray(InstructionSource source)
    {
        source.reset();
        List<Integer> m = new ArrayList<Integer>();

        while (source.getNext())
        {
            int uCodeLength = source.getLength();

            for (int i = 0; i < uCodeLength; i++)
            {
                int data = source.getMicrocode();
                m.add(data);
            }
        }
        int[] ans = new int[m.size()];
        for (int i = 0; i < ans.length; i++)
        {
            ans[i] = m.get(i);
        }
        return ans;
    }
}
