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
import org.jpc.emulator.StatusDumper;
import org.jpc.images.*;

public class FileRawDiskImage implements BaseImage
{
    RandomAccessFile backingFile;
    int totalSectors;
    int sides, tracks, sectors;
    BaseImage.Type type;
    ImageID id;
    int sectorSize;

    public FileRawDiskImage(String fileName, int _sides, int _tracks, int _sectors, BaseImage.Type _type)
        throws IOException
    {
        backingFile = new RandomAccessFile(fileName, "r");
        sectorSize = (_type == BaseImage.Type.BIOS) ? 1 : 512;
        if(backingFile.length() > 0xFFFFFFFFL * sectorSize)
            throw new IOException("Raw image too large (1TB+).");
        totalSectors = (int)(backingFile.length() / sectorSize);
        if(backingFile.length() % sectorSize != 0)
            throw new IOException("Raw image file length not divisible by " + sectorSize + ".");
        sides = _sides;
        tracks = _tracks;
        sectors = _sectors;
        type = _type;
        if(sides > 0 && tracks > 0 && sectors > 0 && totalSectors != sides * tracks * sectors)
            throw new IOException("Raw image file does not have correct number of sectors");
    }

    public BaseImage.Type getType()
    {
        return type;
    }

    public int getTotalSectors()
    {
        return totalSectors;
    }

    public int getSides()
    {
        return sides;
    }

    public int getTracks()
    {
        return tracks;
    }

    public int getSectors()
    {
        return sectors;
    }

    public boolean read(int sector, byte[] buffer, int sectors) throws IOException
    {
        if(sector + sectors > totalSectors)
            throw new IOException("Trying to read sector out of range.");
        backingFile.seek(sectorSize * sector);
        if(backingFile.read(buffer, 0, sectorSize * sectors) < sectorSize * sectors)
            throw new IOException("Can't read sector " + sector + " from image.");
        for(int i = 0; i < sectorSize * sectors; i++)
            if(buffer[i] != 0)
                return true;
        return false;
    }

    public boolean nontrivialContents(int sector) throws IOException
    {
        byte[] buffer = new byte[512];
        return read(sector, buffer, 1);
    }

    public List<String> getComments()
    {
        return new ArrayList<String>();
    }

    public ImageID getID() throws IOException
    {
        if(id == null)
            id = DiskIDAlgorithm.computeIDForDisk(this);
        return id;
    }

    public void dumpStatus(StatusDumper x)
    {
        //This should never be called.
    }
}
