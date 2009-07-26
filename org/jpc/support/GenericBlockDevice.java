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
import org.jpc.emulator.SRLoader;
import org.jpc.emulator.SRDumper;
import org.jpc.emulator.StatusDumper;

public class GenericBlockDevice implements BlockDevice, org.jpc.SRDumpable
{
    private DiskImage image;
    private boolean isLocked;
    private BlockDevice.Type diskType;

    public void dumpStatusPartial(StatusDumper output)
    {
        output.println("\tdiskType " + diskType + " isLocked " + isLocked);
        output.println("\timage <object #" + output.objectNumber(image) + ">"); if(image != null) image.dumpStatus(output);
    }

    public void dumpStatus(StatusDumper output)
    {
        if(output.dumped(this))
            return;

        output.println("#" + output.objectNumber(this) + ": GenericBlockDevice:");
        dumpStatusPartial(output);
        output.endObject();
    }

    public void dumpSRPartial(SRDumper output) throws IOException
    {
        output.dumpObject(image);
        output.dumpBoolean(isLocked);
        switch(diskType) {
        case FLOPPY:
            output.dumpByte((byte)0);
            break;
        case HARDDRIVE:
            output.dumpByte((byte)1);
            break;
        case CDROM:
            output.dumpByte((byte)2);
            break;
        }
    }

    public GenericBlockDevice(SRLoader input) throws IOException
    {
        input.objectCreated(this);
        image = (DiskImage)(input.loadObject());
        isLocked = input.loadBoolean();
        byte tmpDiskType = input.loadByte();
        switch(tmpDiskType) {
        case 0:
            diskType = BlockDevice.Type.FLOPPY;
            break;
        case 1:
            diskType = BlockDevice.Type.HARDDRIVE;
            break;
        case 2:
            diskType = BlockDevice.Type.CDROM;
            break;
        case 3:
            throw new IOException("Invalid disk type in GenericBlockDevice.");
        }
    }

    public GenericBlockDevice(BlockDevice.Type driveType)
    {
        diskType = driveType;
        isLocked = false;
        image = null;
    }

    public GenericBlockDevice(DiskImage _image) throws IOException
    {
        diskType = _image.getType();
        isLocked = false;
        image = _image;
        image.use();
    }

    public GenericBlockDevice(DiskImage _image, BlockDevice.Type expectedType) throws IOException
    {
        if(_image != null && _image.getType() != expectedType)
            throw new IOException("Disk is of wrong type.");
        diskType = expectedType;
        isLocked = false;
        image = _image;
        if(image != null)
            image.use();
    }

    public byte[] getImageID()
    {
        if(image != null)
            return image.getImageID();
        else
            return null;
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

    public boolean isInserted()
    {
        return (image != null);
    }

    public boolean isLocked()
    {
        return isLocked;
    }

    public boolean isReadOnly()
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

    public int getCylinders()
    {
        if(image != null)
            return image.getCylinders();
        else
            return 0;
    }

    public int getHeads()
    {
        if(image != null)
            return image.getHeads();
        else
            return 0;
    }

    public int getSectors()
    {
        if(image != null)
            return image.getSectors();
        else
            return 0;
    }

    public BlockDevice.Type getType()
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

    public void configure(String spec) throws IOException
    {
        //Just implement this, as its required by interface.
        DiskImage dImage = new DiskImage(spec, false);
        configure(dImage);
    }

    public void configure(DiskImage spec) throws IOException
    {
        if(spec != null && spec.getType() != diskType)
            throw new IOException("Trying to put disk of wrong type to drive.");
        if(isLocked)
            throw new IOException("Can not change disk in locked drive.");
        if(image != null)
            image.unuse();
        image = spec;
        if(image != null)
            image.use();
    }
}
