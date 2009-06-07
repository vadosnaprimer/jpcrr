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

//Do not event think about adding an import line to this class - especially not import java.net.*!
import org.jpc.emulator.*;
import java.io.*;
import org.jpc.support.ArgProcessor;

public class DriveSet extends AbstractHardwareComponent
{
    public static final int FLOPPY_BOOT = 0;
    public static final int HARD_DRIVE_BOOT = 1;
    public static final int CD_BOOT = 2;

    private int bootType;
    private BlockDevice bootDevice;
    private BlockDevice[] floppies;
    private BlockDevice[] ides;
    private String[] initialArgs;

    public void dumpStatusPartial(org.jpc.support.StatusDumper output)
    {
        super.dumpStatusPartial(output);
        output.println("\tbootType " + bootType);
        output.println("\tbootDevice <object #" + output.objectNumber(bootDevice) + ">"); if(bootDevice != null) bootDevice.dumpStatus(output);

        for (int i=0; i < floppies.length; i++) {
            output.println("\tfloppies[" + i + "] <object #" + output.objectNumber(floppies[i]) + ">"); if(floppies[i] != null) floppies[i].dumpStatus(output);
        }
        for (int i=0; i < ides.length; i++) {
            output.println("\tides[" + i + "] <object #" + output.objectNumber(ides[i]) + ">"); if(ides[i] != null) ides[i].dumpStatus(output);
        }
        if(initialArgs != null)
            for (int i=0; i < initialArgs.length; i++) {
                if(initialArgs[i] != null)
                    output.println("\tinitialArgs[" + i + "] \"" + initialArgs[i] + "\"");
                else
                    output.println("\tinitialArgs[" + i + "] null");
            }
        else
            output.println("\tinitialArgs null");
    }
 
    public void dumpStatus(org.jpc.support.StatusDumper output)
    {
        if(output.dumped(this))
            return;

        output.println("#" + output.objectNumber(this) + ": DriveSet:");
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
        super.dumpSRPartial(output);
        output.dumpInt(bootType);
        output.dumpObject(bootDevice);
        output.dumpInt(floppies.length);
        for(int i = 0; i < floppies.length; i++)
            output.dumpObject(floppies[i]);
        output.dumpInt(ides.length);
        for(int i = 0; i < ides.length; i++)
            output.dumpObject(ides[i]);
        output.dumpInt(initialArgs.length);
        for(int i = 0; i < initialArgs.length; i++)
            output.dumpString(initialArgs[i]);
    }

    public DriveSet(org.jpc.support.SRLoader input) throws IOException
    {
        super(input);
        bootType = input.loadInt();
        bootDevice = (BlockDevice)(input.loadObject());
        floppies = new BlockDevice[input.loadInt()];
        for(int i = 0; i < floppies.length; i++)
            floppies[i] = (BlockDevice)(input.loadObject());
        ides = new BlockDevice[input.loadInt()];
        for(int i = 0; i < ides.length; i++)
            ides[i] = (BlockDevice)(input.loadObject());
        initialArgs = new String[input.loadInt()];
        for(int i = 0; i < initialArgs.length; i++)
            initialArgs[i] = input.loadString();
    }

    public static org.jpc.SRDumpable loadSR(org.jpc.support.SRLoader input, Integer id) throws IOException
    {
        org.jpc.SRDumpable x = new DriveSet(input);
        input.endObject();
        return x;
    }

    public DriveSet(int bootType, BlockDevice floppyDrive, BlockDevice hardDrive)
    {
        this(bootType, floppyDrive, null, hardDrive, null, null, null);
    }

    public DriveSet(int bootType, BlockDevice floppyDriveA, BlockDevice floppyDriveB, BlockDevice hardDriveA, BlockDevice hardDriveB, BlockDevice hardDriveC, BlockDevice hardDriveD)
    {
        this.bootType = bootType;

        floppies = new BlockDevice[2];
        floppies[0] = floppyDriveA;
        floppies[1] = floppyDriveB;

        ides = new BlockDevice[4];
        ides[0] = hardDriveA;
        ides[1] = hardDriveB;
        ides[2] = (hardDriveC == null) ? new CDROMBlockDevice() : hardDriveC;
        ides[3] = hardDriveD;

        if (bootType == FLOPPY_BOOT)
            bootDevice = floppyDriveA;
        else if (bootType == CD_BOOT)
            bootDevice = hardDriveC;
        else
            bootDevice = hardDriveA;
    }

    public void setInitialArgs(String[] init)
    {
        initialArgs = init;
    }

    public BlockDevice getHardDrive(int index)
    {
        if (index > 3)
            return null;

        return ides[index];
    }

    public void setHardDrive(int index, BlockDevice device)
    {
        ides[index] = device;
    }

    public BlockDevice getFloppyDrive(int index)
    {
        if (index > 1)
            return null;

        return floppies[index];
    }

    public BlockDevice getBootDevice()
    {
        return bootDevice;
    }

    public int getBootType()
    {
        return bootType;
    }

    private static BlockDevice createFloppyBlockDevice(String spec)
    {
        if (spec == null)
            return null;

        BlockDevice device = null;
        try {
            DiskImage img = new DiskImage(spec, false);
            if(img.getType() != BlockDevice.TYPE_FLOPPY)
                throw new Exception(spec + ": Not a floppy disk image.");
            device = new GenericBlockDevice(img);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }

        return device;
    }

    private static BlockDevice createHardDiskBlockDevice(String spec)
    {
        if (spec == null)
            return null;

        BlockDevice device = null;
        try {
            DiskImage img = new DiskImage(spec, false);
            if(img.getType() != BlockDevice.TYPE_HD)
                throw new Exception(spec + ": Not a hard drive image.");
            device = new GenericBlockDevice(img);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }

        return device;
    }

    public static DriveSet buildFromArgs(String[] args)
    {
        String[] initialArgs = (String[]) args.clone();
        int bootKey = DriveSet.HARD_DRIVE_BOOT;
        BlockDevice floppyA = null, floppyB = null, hardDiskA = null, hardDiskB = null, hardDiskC = null, hardDiskD = null;

        String floppyAFileName = ArgProcessor.findArg(args, "-fda", null);
        floppyA = createFloppyBlockDevice(floppyAFileName);
        if (floppyA != null)
            bootKey = DriveSet.FLOPPY_BOOT;

        String floppyBFileName = ArgProcessor.findArg(args, "-fdb", null);
        floppyB = createFloppyBlockDevice(floppyBFileName);

        String hardDiskPrimaryMasterFileName = ArgProcessor.findArg(args, "-hda", null);
        hardDiskA = createHardDiskBlockDevice(hardDiskPrimaryMasterFileName);
        if (hardDiskA != null)
            bootKey = DriveSet.HARD_DRIVE_BOOT;

        String hardDiskPrimarySlaveFileName = ArgProcessor.findArg(args, "-hdb", null);
        hardDiskB = createHardDiskBlockDevice(hardDiskPrimarySlaveFileName);

        String hardDiskSecondaryMasterFileName = ArgProcessor.findArg(args, "-hdc", null);
        hardDiskC = createHardDiskBlockDevice(hardDiskSecondaryMasterFileName);

        String hardDiskSecondarySlaveFileName = ArgProcessor.findArg(args, "-hdd", null);
        hardDiskD = createHardDiskBlockDevice(hardDiskSecondarySlaveFileName);

        String cdRomFileName = ArgProcessor.findArg(args, "-cdrom", null);
        if (cdRomFileName != null)
        {
            try {
                Class ioDeviceClass = Class.forName("org.jpc.support.FileBackedSeekableIODevice");
                SeekableIODevice ioDevice = (SeekableIODevice)(ioDeviceClass.newInstance());
                ioDevice.configure(cdRomFileName);
                hardDiskC = new CDROMBlockDevice(ioDevice);
                bootKey = DriveSet.CD_BOOT;
            } catch (Exception e) {}
        }

        String bootArg = ArgProcessor.findArg(args, "-boot", null);
        if (bootArg != null)
        {
            bootArg = bootArg.toLowerCase();
            if (bootArg.equals("fda"))
                bootKey = DriveSet.FLOPPY_BOOT;
            else if (bootArg.equals("hda"))
                bootKey = DriveSet.HARD_DRIVE_BOOT;
            else if (bootArg.equals("cdrom"))
                bootKey = DriveSet.CD_BOOT;
        }

        DriveSet temp = new DriveSet(bootKey, floppyA, floppyB, hardDiskA, hardDiskB, hardDiskC, hardDiskD);
        temp.setInitialArgs(initialArgs);
        return temp;
    }
}
