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

package org.jpc.support;

import java.io.*;
import java.util.*;

public class CachedWriteSeekableIODevice implements SeekableIODevice
{
    private byte[] dummy;
    private Hashtable writes; 
    private SeekableIODevice src;

    public CachedWriteSeekableIODevice(SeekableIODevice src)
    {
        this.src = src;
        writes = new Hashtable();
        dummy = new byte[4*1024];
    }

    public void seek(int offset) throws IOException
    {
        src.seek(offset);
    }

    public int write(byte[] data, int offset, int length) throws IOException
    {
        int toWrite = Math.min(data.length - offset, length);
        for (int i=0; i<toWrite; i++)
            writes.put(new Integer(i), new Byte(data[offset + i]));

        int skip = toWrite;
        while (skip > 0)
        {
            int s = Math.min(dummy.length, skip);
            src.read(dummy, 0, s);
            skip -= s;
        }
        
        return toWrite;
    }
    
    public int read(byte[] data, int offset, int length) throws IOException
    {
        return src.read(data, offset, length);
    }
    
    public int length()
    {
        return src.length();
    }

    public boolean readOnly()
    {
        return false;
    }

    public void configure(String specs) throws Exception
    {
	src.configure(specs);
    }
}
