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

public class TreeRegularFile extends TreeFile
{
    int size;
    RandomAccessFile cFile;
    String lookup;

    public TreeRegularFile(String self, String lookupPath) throws IOException
    {
        super(self);
        File file = new File(lookupPath);
        if(!file.isFile())
            throw new IOException("Expected " + lookupPath + " to be regular file, but it isn't.");
        size = (int)file.length();
        cFile = null;
        lookup = lookupPath;
    }
 
    public void setClusterZeroOffset(int offset)
    {
        doSetClusterZeroOffset(offset);
    }

    public void setClusterSize(int size)
    {
        doSetClusterSize(size);
    } 
 
    public void readSector(int sector, byte[] data) throws IOException
    {
        if(cFile == null)
            cFile = new RandomAccessFile(lookup, "r");
        int expected = 512;
        if(sector == size / 512)
            expected = size % 512;
        else if(sector > size / 512)
            expected = 0;
        
        if(expected > 0) {
            cFile.seek(512 * sector);
            if(cFile.read(data, 0, expected) < expected) {
                throw new IOException("Can't read from " + lookup + ".");
            }
        }
        for(int i = expected; i < 512; i++)
            data[i] = 0;
    }

    public void readSectorEnd()
    {
        try {
            cFile.close();
        } catch(Exception e) {
            //Swallow it.
        }
        cFile = null;
    }

    public int assignCluster(int base)
    {
        setStartCluster(base);
        return base + getSizeInClusters();
    }

    public TreeFile nextFile()
    {
        return parent.nextFile(selfName);
    }

    protected TreeFile nextFile(String key)
    {
        return null;    //Trying to transverse file!
    }

    public void addFile(TreeFile newFile) throws Exception
    {
        throw new Exception("What you think I am? A subdirectory?");
    }

    public int getSize()
    {
        return size;
    }
};
