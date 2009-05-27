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

package org.jpc.j2se;

import java.util.*;
import org.jpc.emulator.*;
import java.io.*;

public class PriorityVector implements org.jpc.SRDumpable
{
    private Vector backingVector;
    int initSize;

    public PriorityVector(int initialSize)
    {
        initSize = initialSize;
        backingVector = new Vector(initialSize);
    }

    public void addComparableObject(ComparableObject obj)
    {
        //Can only add elements through here, so we can optimise more easily
        synchronized (backingVector) {
            for (int i=0; i<size(); i++) {
                ComparableObject t = (ComparableObject) backingVector.elementAt(i);
                if (obj.compareTo(t) <= 0) {
                    backingVector.insertElementAt(obj, i);
                    return;
                }
            }
            backingVector.addElement(obj); //adds to end of list
        }
    }

    public int size()
    {
        return backingVector.size();
    }

    public Object firstElement()
    {
        try {
            return backingVector.firstElement();
        } catch (NoSuchElementException e) {
            return null;
        }
    }

    public void printContents()
    {
        for (int i=0; i< backingVector.size(); i++)
        {
            System.out.println(backingVector.get(i).toString());
        }
    }

    public void removeFirstElement()
    {
        backingVector.removeElementAt(0);
    }

    public void removeIfFirstElement(Object first)
    {
        synchronized (backingVector) {
            if (backingVector.elementAt(0) == first)
                backingVector.removeElementAt(0);
        }
    }

    public void removeElement(Object element)
    {
        backingVector.removeElement(element);
    }

    public void dumpStatusPartial(org.jpc.support.StatusDumper output)
    {
        for (int i=0; i<size(); i++) {
           ComparableObject t = (ComparableObject) backingVector.elementAt(i);
           output.println("\tbackingVector[" + i + "] <object #" + output.objectNumber(t) + ">"); if(t != null) t.dumpStatus(output);
        }
    }

    public void dumpStatus(org.jpc.support.StatusDumper output)
    {
        if(output.dumped(this))
            return;
        output.println("#" + output.objectNumber(this) + ": PriorityVector:");
        dumpStatusPartial(output);
        output.endObject();
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
        output.dumpInt(initSize);
        output.dumpInt(size());
        for (int i=0; i<size(); i++) {
           ComparableObject t = (ComparableObject) backingVector.elementAt(i);
           output.dumpObject(t);
        }
    }

    public PriorityVector(org.jpc.support.SRLoader input) throws IOException
    {
        input.objectCreated(this);
        backingVector = new Vector(input.loadInt());
        int entries = input.loadInt();
        for (int i = 0; i < entries; i++) {
           ComparableObject t = (ComparableObject)(input.loadObject());
           backingVector.insertElementAt(t, i);
        }
    }

    public static org.jpc.SRDumpable loadSR(org.jpc.support.SRLoader input, Integer id) throws IOException
    {
        org.jpc.SRDumpable x = new PriorityVector(input);
        input.endObject();
        return x;
    }
}
