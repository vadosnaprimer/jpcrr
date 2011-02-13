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

package org.jpc.diskimages;

import java.io.*;
import org.jpc.emulator.SRLoader;
import org.jpc.emulator.SRDumper;
import org.jpc.emulator.StatusDumper;
import org.jpc.emulator.SRDumpable;
import org.jpc.images.BaseImage;

public class DiskImageSet implements SRDumpable
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

    public int[] diskIndicesByType(BaseImage.Type type)
    {
        int images = 0, j = 0;
        for(int i = 0; i < disks.length; i++)
            if(disks[i] != null && disks[i].getType() == type)
                images++;
        int[] diskIDs = new int[images];
        for(int i = 0; i < disks.length; i++)
            if(disks[i] != null && disks[i].getType() == type)
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

    public void dumpStatusPartial(StatusDumper output)
    {
        output.println("\tdiskCount " + diskCount + " lowestGap " + lowestGap);
        for(int i=0; i < disks.length; i++) {
            output.println("\tdisks[" + i + "] <object #" + output.objectNumber(disks[i]) + ">");
            if(disks[i] != null) disks[i].dumpStatus(output);
        }
    }

    public void dumpStatus(StatusDumper output)
    {
        if(output.dumped(this))
            return;

        output.println("#" + output.objectNumber(this) + ": DiskImageSet:");
        dumpStatusPartial(output);
        output.endObject();
    }

    public void dumpSRPartial(SRDumper output) throws IOException
    {
        output.dumpInt(diskCount);
        output.dumpInt(lowestGap);
        output.dumpInt(disks.length);
        for(int i = 0; i < disks.length; i++)
            output.dumpObject(disks[i]);
    }

    public DiskImageSet(SRLoader input) throws IOException
    {
        input.objectCreated(this);
        diskCount = input.loadInt();
        lowestGap = input.loadInt();
        disks = new DiskImage[input.loadInt()];
        for(int i = 0; i < disks.length; i++)
            disks[i] = (DiskImage)(input.loadObject());
    }
}
