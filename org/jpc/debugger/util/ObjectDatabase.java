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


package org.jpc.debugger.util;

import java.util.*;

public class ObjectDatabase
{
    private Hashtable table;
    private Vector list;

    public ObjectDatabase()
    {
        table = new Hashtable();
        list = new Vector();
    }
    
    public synchronized boolean addObject(Object value)
    {
        if (value == null)
            return false;
        Class cls = (Class) value.getClass();

        if (table.get(cls) != null)
            return false;
        table.put(cls, value);
        list.add(value);
        return true;
    }

    public synchronized Object getObject(Class cls)
    {
        return table.get(cls);
    }

    public synchronized Object removeObject(Object obj)
    {
        if (obj == null)
            return null;
        return removeObject(obj.getClass());
    }

    public synchronized Object removeObject(Class cls)
    {
        if (cls == null)
            return false;
        Object val = table.get(cls);
        if (val != null)
        {
            list.remove(val);
            table.remove(cls);
        }
        return val;
    }

    public synchronized int getSize()
    {
        return list.size();
    }

    public synchronized Object getObjectAt(int index)
    {
        return list.elementAt(index);
    }
}
