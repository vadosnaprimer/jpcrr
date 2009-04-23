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

public class CDROMBlockDevice extends RawBlockDevice
{
    private static final String formatName = "cdrom";

    private boolean locked;

    public CDROMBlockDevice(SeekableIODevice data)
    {
        this.data = data;

	cylinders = 2;
	heads = 16;
	sectors = 63;

        totalSectors = data.length()/512;
    }

    public CDROMBlockDevice()
    {
	data = null;

	cylinders = 2;
	heads = 16;
	sectors = 63;
    }

    public void close()
    {
	System.out.println("Trying To Close CDROM");
	eject();
    }

    public boolean locked()
    {
	return locked;
    }

    public boolean readOnly()
    {
	return true;
    }

    public void setLock(boolean locked)
    {
	this.locked = locked;
    }

    public boolean insert(SeekableIODevice data)
    {
	if (locked)
	    return false;

	if (inserted())
	    eject();

        totalSectors = data.length()/512;

	return true;
    }

    public boolean eject()
    {
	if (locked)
	    return false;

	data = null;
	return true;
    }

    public int type()
    {
	return BlockDevice.TYPE_CDROM;
    }

    public String getImageFileName()
    {
        return data.toString();
    }
}
