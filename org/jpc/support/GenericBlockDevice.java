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

public class GenericBlockDevice implements BlockDevice, org.jpc.SRDumpable
{
    private DiskImage image;
    private boolean isLocked;
    private int diskType;

    public void dumpStatusPartial(org.jpc.support.StatusDumper output)
    {
        output.println("\tdiskType " + diskType + " isLocked " + isLocked);
        output.println("\timage <object #" + output.objectNumber(image) + ">"); if(image != null) image.dumpStatus(output);
    }
 
    public void dumpStatus(org.jpc.support.StatusDumper output)
    {
        if(output.dumped(this))
            return;

        output.println("#" + output.objectNumber(this) + ": GenericBlockDevice:");
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
        output.dumpObject(image);
        output.dumpBoolean(isLocked);
        output.dumpInt(diskType);
    }

    public GenericBlockDevice(org.jpc.support.SRLoader input) throws IOException
    {
        input.objectCreated(this);
        image = (DiskImage)(input.loadObject());
        isLocked = input.loadBoolean();
        diskType = input.loadInt();
    }

    public static org.jpc.SRDumpable loadSR(org.jpc.support.SRLoader input, Integer id) throws IOException
    {
        org.jpc.SRDumpable x = new GenericBlockDevice(input);
        input.endObject();
        return x;
    }

    public GenericBlockDevice(int driveType)
    {
        diskType = driveType;
        isLocked = false;
        image = null;
    }

    public GenericBlockDevice(DiskImage _image) throws Exception
    {
        diskType = _image.getType();
        isLocked = false;
        image = _image;
        image.use();
    }

    public void close()
    {
        image.unuse();
        image = null;
    }

    public int read(long sectorNumber, byte[] buffer, int size)
    {
        if(image != null)
            return image.read(sectorNumber, buffer, size);
        else
            return -1;
    }

    public int write(long sectorNumber, byte[] buffer, int size)
    {
        if(image != null)
            return image.write(sectorNumber, buffer, size);
        else
            return -1;
    }

    public boolean inserted()
    {
        return (image != null);
    }

    public boolean locked()
    {
        return isLocked;
    } 

    public boolean readOnly()
    {
        if(image != null)
            return image.isReadOnly();
        else
            return false;
    }

    public void setLock(boolean locked)
    {
        isLocked = locked;
    } 

    public long getTotalSectors()
    {
        if(image != null)
            return image.getTotalSectors();
        else
            return 0;
    }

    public int cylinders()
    {
        if(image != null)
            return image.getCylinders();
        else
            return 0;
    }

    public int heads()
    {
        if(image != null)
            return image.getHeads();
        else
            return 0;
    }

    public int sectors()
    {
        if(image != null)
            return image.getSectors();
        else
            return 0;
    }

    public int type()
    {
        return diskType;
    }

    public String getImageFileName()
    {
        if(image != null)
           return image.getImageFileName();
        else
           return null;
    }

    public void configure(String spec) throws Exception
    {
        //Just implement this, as its required by interface.
        DiskImage dImage = new DiskImage(spec, false);
        configure(dImage);
    }

    public void configure(DiskImage spec) throws Exception
    {
        if(image.getType() != diskType)
            throw new Exception("Trying to put disk of wrong type to drive.");
        image.unuse();
        image = spec;
        image.use();
    }
}
