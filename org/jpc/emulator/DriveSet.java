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

package org.jpc.emulator;

//Do not even think about adding an import line to this class - especially not import java.net.*!
import java.io.IOException;
import org.jpc.diskimages.BlockDevice;
import org.jpc.diskimages.GenericBlockDevice;
import org.jpc.images.BaseImage;

import org.jpc.emulator.AbstractHardwareComponent;

/**
 * Represents the set of disk drive devices associated with this emulator
 * instance.
 * @author Chris Dennis
 */
public class DriveSet extends AbstractHardwareComponent
{

    public static enum BootType
    {
        FLOPPY,
        HARD_DRIVE,
        CDROM;

        public static byte toNumeric(BootType type)
        {
            if(type == FLOPPY)
                return 1;
            else if(type == HARD_DRIVE)
                return 2;
            else if(type == CDROM)
                return 3;
            else
                return 0;
        }

        public static BootType fromNumeric(byte type)
        {
            if(type == 1)
                return FLOPPY;
            else if(type == 2)
                return HARD_DRIVE;
            else if(type == 3)
                return CDROM;
            else
                return null;
        }
    }

    private BootType bootType;
    private BlockDevice[] ides;

    public void dumpStatusPartial(StatusDumper output)
    {
        super.dumpStatusPartial(output);
        output.println("\tbootType " + bootType);

        for(int i=0; i < ides.length; i++) {
            output.println("\tides[" + i + "] <object #" + output.objectNumber(ides[i]) + ">");
            if(ides[i] != null) ides[i].dumpStatus(output);
        }
    }

    public void dumpStatus(StatusDumper output)
    {
        if(output.dumped(this))
            return;

        output.println("#" + output.objectNumber(this) + ": DriveSet:");
        dumpStatusPartial(output);
        output.endObject();
    }

    public void dumpSRPartial(SRDumper output) throws IOException
    {
        super.dumpSRPartial(output);
        output.dumpByte(BootType.toNumeric(bootType));
        output.dumpInt(ides.length);
        for(int i = 0; i < ides.length; i++)
            output.dumpObject(ides[i]);
    }

    public DriveSet(SRLoader input) throws IOException
    {
        super(input);
        bootType = BootType.fromNumeric(input.loadByte());
        ides = new BlockDevice[input.loadInt()];
        for(int i = 0; i < ides.length; i++)
            ides[i] = (BlockDevice)input.loadObject();
    }

    /**
     * Constructs a driveset with all parameters specified.
     * <p>
     * A drive set can be composed of at most four ide devices and two floppy
     * drive devices.
     * @param boot boot device
     * @param hardDriveA primary master hard disk
     * @param hardDriveB primary slave hard disk
     * @param hardDriveC secondary master hard disk
     * @param hardDriveD secondary slave hard disk
     */
    public DriveSet(BootType boot, BlockDevice hardDriveA, BlockDevice hardDriveB, BlockDevice hardDriveC, BlockDevice hardDriveD)
    {
        this.bootType = boot;

        ides = new BlockDevice[4];
        ides[0] = hardDriveA;
        ides[1] = hardDriveB;
        ides[2] = (hardDriveC == null) ? new GenericBlockDevice(BaseImage.Type.CDROM) : hardDriveC;
        ides[3] = hardDriveD;
    }

    /**
     * Returns the i'th hard drive device.
     * <p>
     * Devices are numbered from 0 to 3 inclusive in order: primary master,
     * primary slave, secondary master, secondary slave.
     * @param index drive index
     * @return hard drive block device
     */
    public BlockDevice getHardDrive(int index)
    {
        return ides[index];
    }

    public void setHardDrive(int index, BlockDevice device)
    {
        ides[index] = device;
    }

    /**
     * Returns the boot type being used by this driveset.
     * @return boot type
     */
    public BootType getBootType()
    {
        return bootType;
    }
}
