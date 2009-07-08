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

public class DiskImageSet implements org.jpc.SRDumpable
{
    private DiskImage[] disks;
    private int diskCount;
    private int lowestGap;

    public DiskImageSet()
    {
        disks = new DiskImage[5];
        diskCount = 0;
        lowestGap = 0;
    }

    public int addDisk(DiskImage image)
    {
        if(image == null)
            return -1;
        int base = lowestGap;
        if(diskCount == disks.length) {
            DiskImage[] newDisks = new DiskImage[2 * disks.length];
            System.arraycopy(disks, 0, newDisks, 0, disks.length);
            disks = newDisks;
            base = diskCount;
        }
        for(int i = base; i < disks.length; i++) {
            if(disks[i] == null) {
                disks[i] = image;
                diskCount++;
                lowestGap = i + 1;
                return i;
            } else
                lowestGap = i;
        }
        return -1;      //Can't come here.
    }

    public void addDisk(int id, DiskImage image)
    {
        while(id >= disks.length) {
            DiskImage[] newDisks = new DiskImage[2 * disks.length];
            System.arraycopy(disks, 0, newDisks, 0, disks.length);
            disks = newDisks;
        }
        if(disks[id] == null && image != null)
            diskCount++;
        if(lowestGap == id && image != null)
            lowestGap++;
        else if(lowestGap > id && image == null)
            lowestGap = id;
        disks[id] = image;
    }


    public DiskImage lookupDisk(int index) 
    {
        if(index == -1)
            return null;
        try {
            return disks[index];
        } catch(Exception e) {
            return null;
        }
    }

    public int[] diskIndicesByType(BlockDevice.Type type)
    {
        int images = 0, j = 0;
        for(int i = 0; i < disks.length; i++)
            if(disks[i].getType() == type)
                images++;
        int[] diskIDs = new int[images];
        for(int i = 0; i < disks.length; i++)
            if(disks[i].getType() == type)
                diskIDs[j++] = i;
        return diskIDs;
    }

    public int getDiskCount()
    {
        return diskCount;
    }

    public int highestDiskIndex()
    {
        return disks.length - 1;
    }

    public void dumpStatusPartial(org.jpc.support.StatusDumper output)
    {
        output.println("\tdiskCount " + diskCount + " lowestGap " + lowestGap);
        for (int i=0; i < disks.length; i++) {
            output.println("\tdisks[" + i + "] <object #" + output.objectNumber(disks[i]) + ">"); if(disks[i] != null) disks[i].dumpStatus(output);
        }
    }

    public void dumpStatus(org.jpc.support.StatusDumper output)
    {
        if(output.dumped(this))
            return;

        output.println("#" + output.objectNumber(this) + ": DiskImageSet:");
        dumpStatusPartial(output);
        output.endObject();
    }

    public void dumpSR(org.jpc.support.SRDumper output) throws IOException
    {
        if(output.dumped(this))
            return;
        dumpSRPartial(output);
        output.endObject();
    }

    public void dumpSRPartial(org.jpc.support.SRDumper output) throws IOException
    {
        output.dumpInt(diskCount);
        output.dumpInt(lowestGap);
        output.dumpInt(disks.length);
        for(int i = 0; i < disks.length; i++)
            output.dumpObject(disks[i]);
    }

    public DiskImageSet(org.jpc.support.SRLoader input) throws IOException
    {
        input.objectCreated(this);
        diskCount = input.loadInt();
        lowestGap = input.loadInt();
        disks = new DiskImage[input.loadInt()];
        for(int i = 0; i < disks.length; i++)
            disks[i] = (DiskImage)(input.loadObject());
    }

    public static org.jpc.SRDumpable loadSR(org.jpc.support.SRLoader input, Integer id) throws IOException
    {
        org.jpc.SRDumpable x = new DiskImageSet(input);
        input.endObject();
        return x;
    }
}
