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

package org.jpc.emulator.peripheral;

public class FloppyFormat
{
    private static final int DISK_288   = 0x01; // 2.88 MB disk
    private static final int DISK_144   = 0x02; // 1.44 MB disk
    private static final int DISK_720   = 0x03; // 720 kB disk
    private static final int DISK_USER  = 0x04; // User defined geometry
    private static final int DISK_NONE  = 0x05; // No disk

    private static final FloppyFormat[] formats = {
        /* First entry is default format */
        /* 1.44 MB 3"1/2 floppy disks */
        new FloppyFormat( FloppyController.FloppyDrive.DRIVE_144, DISK_144, 18, 80, 1, "1.44 MB 3\"1/2" ),
        new FloppyFormat( FloppyController.FloppyDrive.DRIVE_144, DISK_144, 20, 80, 1,  "1.6 MB 3\"1/2" ),
        new FloppyFormat( FloppyController.FloppyDrive.DRIVE_144, DISK_144, 21, 80, 1, "1.68 MB 3\"1/2" ),
        new FloppyFormat( FloppyController.FloppyDrive.DRIVE_144, DISK_144, 21, 82, 1, "1.72 MB 3\"1/2" ),
        new FloppyFormat( FloppyController.FloppyDrive.DRIVE_144, DISK_144, 21, 83, 1, "1.74 MB 3\"1/2" ),
        new FloppyFormat( FloppyController.FloppyDrive.DRIVE_144, DISK_144, 22, 80, 1, "1.76 MB 3\"1/2" ),
        new FloppyFormat( FloppyController.FloppyDrive.DRIVE_144, DISK_144, 23, 80, 1, "1.84 MB 3\"1/2" ),
        new FloppyFormat( FloppyController.FloppyDrive.DRIVE_144, DISK_144, 24, 80, 1, "1.92 MB 3\"1/2" ),
        /* 2.88 MB 3"1/2 floppy disks */
        new FloppyFormat( FloppyController.FloppyDrive.DRIVE_288, DISK_288, 36, 80, 1, "2.88 MB 3\"1/2" ),
        new FloppyFormat( FloppyController.FloppyDrive.DRIVE_288, DISK_288, 39, 80, 1, "3.12 MB 3\"1/2" ),
        new FloppyFormat( FloppyController.FloppyDrive.DRIVE_288, DISK_288, 40, 80, 1,  "3.2 MB 3\"1/2" ),
        new FloppyFormat( FloppyController.FloppyDrive.DRIVE_288, DISK_288, 44, 80, 1, "3.52 MB 3\"1/2" ),
        new FloppyFormat( FloppyController.FloppyDrive.DRIVE_288, DISK_288, 48, 80, 1, "3.84 MB 3\"1/2" ),
        /* 720 kB 3"1/2 floppy disks */
        new FloppyFormat( FloppyController.FloppyDrive.DRIVE_144, DISK_720, 9, 80, 1,  "720 kB 3\"1/2" ),
        new FloppyFormat( FloppyController.FloppyDrive.DRIVE_144, DISK_720, 10, 80, 1,  "800 kB 3\"1/2" ),
        new FloppyFormat( FloppyController.FloppyDrive.DRIVE_144, DISK_720, 10, 82, 1,  "820 kB 3\"1/2" ),
        new FloppyFormat( FloppyController.FloppyDrive.DRIVE_144, DISK_720, 10, 83, 1,  "830 kB 3\"1/2" ),
        new FloppyFormat( FloppyController.FloppyDrive.DRIVE_144, DISK_720, 13, 80, 1, "1.04 MB 3\"1/2" ),
        new FloppyFormat( FloppyController.FloppyDrive.DRIVE_144, DISK_720, 14, 80, 1, "1.12 MB 3\"1/2" ),
        /* 1.2 MB 5"1/4 floppy disks */
        new FloppyFormat( FloppyController.FloppyDrive.DRIVE_120, DISK_288, 15, 80, 1,  "1.2 kB 5\"1/4" ),
        new FloppyFormat( FloppyController.FloppyDrive.DRIVE_120, DISK_288, 18, 80, 1, "1.44 MB 5\"1/4" ),
        new FloppyFormat( FloppyController.FloppyDrive.DRIVE_120, DISK_288, 18, 82, 1, "1.48 MB 5\"1/4" ),
        new FloppyFormat( FloppyController.FloppyDrive.DRIVE_120, DISK_288, 18, 83, 1, "1.49 MB 5\"1/4" ),
        new FloppyFormat( FloppyController.FloppyDrive.DRIVE_120, DISK_288, 20, 80, 1,  "1.6 MB 5\"1/4" ),
        /* 720 kB 5"1/4 floppy disks */
        new FloppyFormat( FloppyController.FloppyDrive.DRIVE_120, DISK_288, 9, 80, 1,  "720 kB 5\"1/4" ),
        new FloppyFormat( FloppyController.FloppyDrive.DRIVE_120, DISK_288, 11, 80, 1,  "880 kB 5\"1/4" ),
        /* 360 kB 5"1/4 floppy disks */
        new FloppyFormat( FloppyController.FloppyDrive.DRIVE_120, DISK_288, 9, 40, 1,  "360 kB 5\"1/4" ),
        new FloppyFormat( FloppyController.FloppyDrive.DRIVE_120, DISK_288, 9, 40, 0,  "180 kB 5\"1/4" ),
        new FloppyFormat( FloppyController.FloppyDrive.DRIVE_120, DISK_288, 10, 41, 1,  "410 kB 5\"1/4" ),
        new FloppyFormat( FloppyController.FloppyDrive.DRIVE_120, DISK_288, 10, 42, 1,  "420 kB 5\"1/4" ),
        /* 320 kB 5"1/4 floppy disks */
        new FloppyFormat( FloppyController.FloppyDrive.DRIVE_120, DISK_288, 8, 40, 1,  "320 kB 5\"1/4" ),
        new FloppyFormat( FloppyController.FloppyDrive.DRIVE_120, DISK_288, 8, 40, 0,  "160 kB 5\"1/4" ),
        /* 360 kB must match 5"1/4 better than 3"1/2... */
        new FloppyFormat( FloppyController.FloppyDrive.DRIVE_144, DISK_720, 9, 80, 0,  "360 kB 3\"1/2" ),
        /* end */
        new FloppyFormat( FloppyController.FloppyDrive.DRIVE_NONE, DISK_NONE, -1, -1, 0, "" )
    };

    private int drive;
    private int disk;
    private int lastSector;
    private int maxTrack;
    private int maxHead;
    private String description;

    public void dumpStatusPartial(org.jpc.support.StatusDumper output)
    {
        //super.dumpStatusPartial(output);
        output.println("\tdrive " + drive + " disk " + disk + " lastSector " + lastSector + " maxTrack " + maxTrack);
        output.println("\tmaxHead " + maxHead + "description \"" + description + "\"");
    }
 
    public void dumpStatus(org.jpc.support.StatusDumper output)
    {
        if(output.dumped(this))
            return;

        output.println("#" + output.objectNumber(this) + ": FloppyFormat:");
        dumpStatusPartial(output);
        output.endObject();
    }

    private FloppyFormat(int drive, int disk, int lastSector, int maxTrack, int maxHead, String description)
    {
        this.drive = drive;
        this.disk = disk;
        this.lastSector = lastSector;
        this.maxTrack = maxTrack;
        this.maxHead = maxHead;
        this.description = description;
    }

    public int heads()
    {
        return maxHead + 1;
    }

    public int tracks()
    {
        return maxTrack;
    }

    public int sectors()
    {
        return lastSector;
    }

    public int drive()
    {
        return drive;
    }

    public long length()
    {
        return heads() * tracks() * sectors() * 512;
    }

    public String toString()
    {
        return description;
    }

    public static FloppyFormat findFormat(long size, int drive)
    {
        int firstMatch = -1;
        for (int i = 0; i < formats.length; i++) {
            if (formats[i].drive() == FloppyController.FloppyDrive.DRIVE_NONE)
                break;
            if ((drive == formats[i].drive()) || (drive == FloppyController.FloppyDrive.DRIVE_NONE)) {
                if (formats[i].length() == size) {
                    return formats[i];
                }
                if (firstMatch == -1)
                    firstMatch = i;

            }
        }
        if (firstMatch == -1)
            return formats[1]; // Should this return the NULL format?
        else
            return formats[firstMatch];
    }
}
