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

public class FloppyBlockDevice implements BlockDevice
{
    public static final int TYPE_HD = 0;
    public static final int TYPE_CDROM = 1;
    public static final int TYPE_FLOPPY = 2;

    private SeekableIODevice data;

    public FloppyBlockDevice(SeekableIODevice data)
    {
        this.data = data;
    }

    public String getImageFileName()
    {
        return data.toString();
    }

    public void close()
    {
    }

    public int read(long sectorNumber, byte[] buffer, int size)
    {
	try 
        {
	    data.seek((int) (sectorNumber * 512));
	    if (data.read(buffer, 0, size * 512) != size * 512) {
		System.err.println("Did not read enough! ERROR?");
		return -1;
	    } else {
		return 0;
	    }
	} catch (IOException e) {
	    System.err.println("IO Error Reading From " + data.toString());
	    e.printStackTrace();
	    return -1;
	}
    }
    public int write(long sectorNumber, byte[] buffer, int size)
    {
	try 
        {
	    data.seek((int) (sectorNumber * 512));
	    data.write(buffer, 0, size * 512);
	} catch (IOException e) {
	    System.err.println("IO Error Writing To " + data.toString());
	    e.printStackTrace();
	    return -1;
	}
	return 0;
    }

    public boolean inserted()
    {
	return (data != null);
    }

    public boolean locked()
    {
	return false;
    }

    public void setLock(boolean locked)
    {
    }

    public long getTotalSectors()
    {
        return data.length();
    }

    public int cylinders()
    {
	return -1;
    }

    public int heads()
    {
	return -1;
    }

    public int sectors()
    {
	return -1;
    }

    public int type()
    {
	return TYPE_FLOPPY;
    }

    public boolean readOnly()
    {
	return data.readOnly();
    }
    
    public String toString()
    {
        return "Floppy: "+ data.toString();
    }

    /* FIXME: Implement these. */
    public void dumpState(DataOutput output) throws IOException {}
    public void loadState(DataInput input) throws IOException {}

    public void configure(String spec) throws Exception
    {
	data.configure(spec);
    }
}
