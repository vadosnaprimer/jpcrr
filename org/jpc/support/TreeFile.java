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

public abstract class TreeFile
{
    protected int clusterSize;           //In sectors.
    protected int startCluster;          //Root directory can have this negative!!!
    protected int clusterZeroOffset;     //Quite purely abstract concept. Two cluster sizes before data area start.
    protected String selfName;
    protected TreeFile parent;

    protected TreeFile(String self)
    {
        clusterSize = 1;
        startCluster = 0;
        clusterZeroOffset = 0;
        selfName = self;
        parent = null;
    }
 
    protected String getSelfName()
    {
        return selfName;
    }

    protected void parentTo(TreeFile newParent)
    {
        parent = newParent;
    }

    protected void doSetClusterSize(int cluster)
    {
        clusterSize = cluster;
    }

    public int getClusterSize()
    {
        return clusterSize;
    }

    protected void setStartCluster(int cluster)
    {
        startCluster = cluster;
    }

    public int getStartCluster()
    {
        return startCluster;
    }

    public int getEndCluster()
    {
        return startCluster + getSizeInClusters() - 1;
    }

    protected void doSetClusterZeroOffset(int offset)
    {
        clusterZeroOffset = offset;
    }

    public int getSizeInClusters()
    {
        int sectors = getSizeInSectors();
        return (sectors + clusterSize - 1) / clusterSize;
    }

    public int getSizeInSectors()
    {
        int bytes = getSize();
        return (bytes + 511) / 512;
    }

    //Recurse and call doSetClusterZeroOffset().
    public abstract void setClusterZeroOffset(int offset);
    //Recurse and call doSetClusterSize().
    public abstract void setClusterSize(int size);
    //Return all-zeroes after file has ended.
    public abstract void readSector(int sector, byte[] data) throws IOException;
    //Hint that readSectors have ended.
    public abstract void readSectorEnd();
    //Recursively assign clusters to files. Call setStartCluster(base) and return next cluster number.
    public abstract int assignCluster(int base);
    //Return next entry in tree, or null if none exists.
    public abstract TreeFile nextFile();
    //Return next entry from given one.
    protected abstract TreeFile nextFile(String key);
    //Add file.
    public abstract void addFile(TreeFile newFile) throws Exception;
    //Get true size of file.
    public abstract int getSize();
};
