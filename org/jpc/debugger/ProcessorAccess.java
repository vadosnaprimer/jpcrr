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

import java.lang.reflect.*;
import java.util.*;

import org.jpc.emulator.processor.*;
import org.jpc.emulator.memory.*;

public class ProcessorAccess
{
    private Processor processor;
    private HashMap<String, Object> lookup;

    public ProcessorAccess(Processor proc)
    {
        processor = proc;

        try
        {
            lookup = new HashMap<String, Object>();

            addField("eax");
            addField("ecx");
            addField("edx");
            addField("ebx");
            addField("esp");
            addField("ebp");
            addField("esi");
            addField("edi");

            addField("eip");

            addField("cr0");
            addField("cr1");
            addField("cr2");
            addField("cr3");
            addField("cr4");
            
            addField("cs");
            addField("ds");
            addField("ss");
            addField("es");
            addField("fs");
            addField("gs");

            addField("idtr");
            addField("gdtr");
            addField("ldtr");
        }
        catch (Throwable t) {t.printStackTrace();}
    }

    private void addField(String fieldName) throws Exception
    {
        Field targetField = Processor.class.getDeclaredField(fieldName);
        targetField.setAccessible(true);

        lookup.put(fieldName, targetField);   
    }

    public int getValue(String name, int defaultValue)
    {
        if (name.equals("eflags"))
            return processor.getEFlags();

        try
        {
            Field f = (Field) lookup.get(name);
            
            try
            {
                return f.getInt(processor);
            }
            catch (Exception e) {}
            
            Segment sel = (Segment) f.get(processor);
            return sel.getBase();
            //return sel.getSelector();
        }
        catch (Throwable t) {}

        return defaultValue;
    }

    public boolean setValue(String name, int value)
    {
        try
        {
            Object obj = lookup.get(name);
            if (obj instanceof Field)
            {
                ((Field) obj).setInt(processor, value);
                return true;
            }
        }
        catch (Throwable t) {}

        return false;
    }
}
