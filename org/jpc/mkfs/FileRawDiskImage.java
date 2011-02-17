/*
    JPC-RR: A x86 PC Hardware Emulator
    Release 1

    Copyright (C) 2007-2009 Isis Innovation Limited
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

    Based on JPC x86 PC Hardware emulator,
    A project from the Physics Dept, The University of Oxford

    Details about original JPC can be found at:

    www-jpc.physics.ox.ac.uk

*/

package org.jpc.mkfs;

import java.io.*;
import java.util.*;

public class FileRawDiskImage implements RawDiskImage
{
    RandomAccessFile backingFile;
    long totalSectors;
    int sides, tracks, sectors;

    public FileRawDiskImage(String fileName, int _sides, int _tracks, int _sectors) throws IOException
    {
        backingFile = new RandomAccessFile(fileName, "r");
        totalSectors = backingFile.length() / 512;
        if(backingFile.length() % 512 != 0)
            throw new IOException("Raw image file length not divisible by 512.");
        sides = _sides;
        tracks = _tracks;
        sectors = _sectors;
        if(sides > 0 && tracks > 0 && sectors > 0 && totalSectors != (long)sides * tracks * sectors)
            throw new IOException("Raw image file does not have correct number of sectors");
    }

    public long getSectorCount() throws IOException
    {
        return totalSectors;
    }

    public int getSides() throws IOException
    {
        return sides;
    }

    public int getTracks() throws IOException
    {
        return tracks;
    }

    public int getSectors() throws IOException
    {
        return sectors;
    }

    public boolean readSector(int sector, byte[] buffer) throws IOException
    {
        if(sector >= totalSectors)
            throw new IOException("Trying to read sector out of range.");
        backingFile.seek(512 * sector);
        if(backingFile.read(buffer, 0, 512) < 512)
            throw new IOException("Can't read sector " + sector + " from image.");
        return true;
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

    public List<String> getComments()
    {
        return null;
    }
}
