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

public interface BlockDevice
{
    public static final int TYPE_HD = 0;
    public static final int TYPE_CDROM = 1;
    public static final int TYPE_FLOPPY = 2;

    public void close();

    public int read(long sectorNumber, byte[] buffer, int size);
    public int write(long sectorNumber, byte[] buffer, int size);

    public boolean inserted();
    public boolean locked();
    public boolean readOnly();

    public void setLock(boolean locked);

    public void dumpChanges(DataOutput output) throws IOException;
    public void loadChanges(DataInput input) throws IOException;

    public long getTotalSectors();

    public int cylinders();
    public int heads();
    public int sectors();

    public int type();
    public String getImageFileName();

    public void configure(String spec) throws Exception;
}
