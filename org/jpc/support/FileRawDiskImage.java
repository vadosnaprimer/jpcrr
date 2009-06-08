/*
    JPC: A x86 PC Hardware Emulator for a pure Java Virtual Machine
    Release Version 2.0

    A project from the Physics Dept, The University of Oxford

    Copyright (C) 2007 Isis Innovation Limited
    Copyright (C) 2009 H. Ilari Liusvaara

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
import java.nio.charset.*;
import java.nio.*;

public class FileRawDiskImage implements RawDiskImage
{
    RandomAccessFile backingFile;
    int sectors;

    public FileRawDiskImage(String fileName) throws IOException
    {
        backingFile = new RandomAccessFile(fileName, "r");
        sectors = (int)backingFile.length() / 512;
        if(backingFile.length() % 512 != 0)
            throw new IOException("Raw image file length not divisible by 512.");
    }

    public int getSectorCount() throws IOException
    {
        return sectors;
    }
 
    public void readSector(int sector, byte[] buffer) throws IOException
    {
        if(sector >= sectors)
            throw new IOException("Trying to read sector out of range.");
        backingFile.seek(512 * sector);
        if(backingFile.read(buffer, 0, 512) < 512)
            throw new IOException("Can't read sector " + sector + " from image.");
    }
    
    public boolean isSectorEmpty(int sector) throws IOException
    {
        byte[] buffer = new byte[512];
        readSector(sector, buffer);
        for(int i = 0; i < 512; i++)
            if(buffer[i] != 0)
                return false;
        return true;
    }
}
