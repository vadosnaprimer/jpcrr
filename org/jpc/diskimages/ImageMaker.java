/*
    JPC-RR: A x86 PC Hardware Emulator
    Release 1

    Copyright (C) 2007-2009 Isis Innovation Limited
    Copyright (C) 2009-2010 H. Ilari Liusvaara

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
import java.util.*;
import java.nio.charset.*;
import java.nio.*;
import org.jpc.mkfs.*;
import org.jpc.images.JPCRRStandardImageDecoder;
import org.jpc.images.BaseImage;
import org.jpc.images.StorageMethod;
import org.jpc.images.StorageMethodBase;
import org.jpc.images.StorageMethodNormal;
import org.jpc.images.ImageID;
import org.jpc.mkfs.DiskIDAlgorithm;
import static org.jpc.Misc.tempname;
import static org.jpc.Misc.probeRenameOver;
import static org.jpc.Misc.errorDialog;

public class ImageMaker
{
    public static class IFormat
    {
        public int typeCode;
        public int tracks;
        public int sectors;
        public int sides;
        public String timestamp;
        public String volumeLabel;

        private void parseSpec(String spec) throws Exception
        {
            String[] values = spec.split(",");
            if(values.length != 3)
                throw new Exception("Invalid spec " + spec + " in parseSpec");
            try {
                tracks = Integer.decode(values[0]);
                sectors = Integer.decode(values[1]);
                sides = Integer.decode(values[2]);
            } catch(NumberFormatException e) {
                throw new Exception("Invalid spec " + spec + " in parseSpec");
            }
        }

        public IFormat(String specifier) throws Exception
        {
            typeCode = -1;
            tracks = -1;
            sectors = -1;
            sides = -1;
            if(specifier != null)
                addArgument(specifier);
        }

        public void addArgument(String specifier) throws Exception
        {
            if(specifier.startsWith("--HDD=")) {
                typeCode = 1;
                try {
                    parseSpec(specifier.substring(6));
                } catch(Exception e) {
                    throw new Exception("Invalid format specifier " + specifier + ".");
                }
                if(tracks < 1 || tracks > 1024 || sectors < 1 || sectors > 63 || sides < 1 || sides > 16) {
                    throw new Exception("Invalid HDD geometry. Heads must be 1-16, Sectors 1-63 and cylinders 1-1024.");
                }
            } else if(specifier.startsWith("--floppy=")) {
                typeCode = 0;
                try {
                    parseSpec(specifier.substring(9));
                } catch(Exception e) {
                    throw new Exception("Invalid format specifier " + specifier + ".");
                }
                if(tracks < 1 || tracks > 256 || sectors < 1 || sectors > 255 || sides < 1 || sides > 2) {
                    throw new Exception("Invalid floppy geometry. Sides must be 1 or 2, Sectors 1-255 and tracks 1-256.");
                }
            } else if(specifier.startsWith("--timestamp=")) {
                timestamp = specifier.substring(12);
            } else if(specifier.startsWith("--volumelabel=")) {
                volumeLabel = specifier.substring(14);
            }
            else if(specifier.equals("--BIOS"))        { typeCode = 3; }
            else if(specifier.equals("--CDROM"))       { typeCode = 2; }
            else if(specifier.equals("--floppy160"))   { typeCode = 0; tracks = 40; sectors =  8; sides = 1; }
            else if(specifier.equals("--floppy180"))   { typeCode = 0; tracks = 40; sectors =  9; sides = 1; }
            else if(specifier.equals("--floppy320"))   { typeCode = 0; tracks = 40; sectors =  8; sides = 2; }
            else if(specifier.equals("--floppy360"))   { typeCode = 0; tracks = 40; sectors =  9; sides = 2; }
            else if(specifier.equals("--floppy410"))   { typeCode = 0; tracks = 41; sectors = 10; sides = 2; }
            else if(specifier.equals("--floppy420"))   { typeCode = 0; tracks = 42; sectors = 10; sides = 2; }
            else if(specifier.equals("--floppy720"))   { typeCode = 0; tracks = 80; sectors =  9; sides = 2; }
            else if(specifier.equals("--floppy800"))   { typeCode = 0; tracks = 80; sectors = 10; sides = 2; }
            else if(specifier.equals("--floppy820"))   { typeCode = 0; tracks = 82; sectors = 10; sides = 2; }
            else if(specifier.equals("--floppy830"))   { typeCode = 0; tracks = 83; sectors = 10; sides = 2; }
            else if(specifier.equals("--floppy880"))   { typeCode = 0; tracks = 80; sectors = 11; sides = 2; }
            else if(specifier.equals("--floppy1040"))  { typeCode = 0; tracks = 80; sectors = 13; sides = 2; }
            else if(specifier.equals("--floppy1120"))  { typeCode = 0; tracks = 80; sectors = 14; sides = 2; }
            else if(specifier.equals("--floppy1200"))  { typeCode = 0; tracks = 80; sectors = 15; sides = 2; }
            else if(specifier.equals("--floppy1440"))  { typeCode = 0; tracks = 80; sectors = 18; sides = 2; }
            else if(specifier.equals("--floppy1476"))  { typeCode = 0; tracks = 82; sectors = 18; sides = 2; }
            else if(specifier.equals("--floppy1494"))  { typeCode = 0; tracks = 83; sectors = 18; sides = 2; }
            else if(specifier.equals("--floppy1600"))  { typeCode = 0; tracks = 80; sectors = 20; sides = 2; }
            else if(specifier.equals("--floppy1680"))  { typeCode = 0; tracks = 80; sectors = 20; sides = 2; }
            else if(specifier.equals("--floppy1722"))  { typeCode = 0; tracks = 82; sectors = 21; sides = 2; }
            else if(specifier.equals("--floppy1743"))  { typeCode = 0; tracks = 83; sectors = 21; sides = 2; }
            else if(specifier.equals("--floppy1760"))  { typeCode = 0; tracks = 80; sectors = 22; sides = 2; }
            else if(specifier.equals("--floppy1840"))  { typeCode = 0; tracks = 80; sectors = 23; sides = 2; }
            else if(specifier.equals("--floppy1920"))  { typeCode = 0; tracks = 80; sectors = 24; sides = 2; }
            else if(specifier.equals("--floppy2880"))  { typeCode = 0; tracks = 80; sectors = 36; sides = 2; }
            else if(specifier.equals("--floppy3120"))  { typeCode = 0; tracks = 80; sectors = 39; sides = 2; }
            else if(specifier.equals("--floppy3200"))  { typeCode = 0; tracks = 80; sectors = 40; sides = 2; }
            else if(specifier.equals("--floppy3520"))  { typeCode = 0; tracks = 80; sectors = 44; sides = 2; }
            else if(specifier.equals("--floppy3840"))  { typeCode = 0; tracks = 80; sectors = 48; sides = 2; }
            else
                throw new Exception("Invalid format specifier/option " + specifier + ".");
        }
    }

    private static void usage()
    {
        System.err.println("java ImageMaker <imagefile>");
        System.err.println("java ImageMaker [<options>...] <format> <destination> <source>");
        System.err.println("Valid formats are:");
        System.err.println("--BIOS                           BIOS image.");
        System.err.println("--CDROM                          CD-ROM image.");
        System.err.println("--HDD=cylinders,sectors,heads    Hard disk with specified geometry.");
        System.err.println("                                 Cylinders allowed: 1-1024.");
        System.err.println("                                 Sectors allowed:   1-63.");
        System.err.println("                                 Heads allowed:     1-16.");
        System.err.println("--floppy=tracks,sectors,sides    Floppy disk with specified geometry.");
        System.err.println("                                 Tracks allowed:    1-256.");
        System.err.println("                                 Sectors allowed:   1-255.");
        System.err.println("                                 Sides allowed:     1 or 2.");
        System.err.println("--floppy160                      160KiB floppy (40 tracks, 8 sectors, Single sided).");
        System.err.println("--floppy180                      180KiB floppy (40 tracks, 9 sectors, Single sided).");
        System.err.println("--floppy320                      320KiB floppy (40 tracks, 8 sectors, Double sided).");
        System.err.println("--floppy360                      360KiB floppy (40 tracks, 9 sectors, Double sided).");
        System.err.println("--floppy410                      410KiB floppy (41 tracks, 10 sectors, Double sided).");
        System.err.println("--floppy420                      420KiB floppy (42 tracks, 10 sectors, Double sided).");
        System.err.println("--floppy720                      720KiB floppy (80 tracks, 9 sectors, Double sided).");
        System.err.println("--floppy800                      800KiB floppy (80 tracks, 10 sectors, Double sided).");
        System.err.println("--floppy820                      820KiB floppy (82 tracks, 10 sectors, Double sided).");
        System.err.println("--floppy830                      830KiB floppy (83 tracks, 10 sectors, Double sided).");
        System.err.println("--floppy880                      880KiB floppy (80 tracks, 11 sectors, Double sided).");
        System.err.println("--floppy1040                     1040KiB floppy (80 tracks, 13 sectors, Double sided).");
        System.err.println("--floppy1120                     1120KiB floppy (80 tracks, 14 sectors, Double sided).");
        System.err.println("--floppy1200                     1200KiB floppy (80 tracks, 15 sectors, Double sided).");
        System.err.println("--floppy1440                     1440KiB floppy (80 tracks, 18 sectors, Double sided).");
        System.err.println("--floppy1476                     1476KiB floppy (82 tracks, 18 sectors, Double sided).");
        System.err.println("--floppy1494                     1494KiB floppy (83 tracks, 18 sectors, Double sided).");
        System.err.println("--floppy1600                     1600KiB floppy (80 tracks, 20 sectors, Double sided).");
        System.err.println("--floppy1680                     1680KiB floppy (80 tracks, 21 sectors, Double sided).");
        System.err.println("--floppy1722                     1722KiB floppy (82 tracks, 21 sectors, Double sided).");
        System.err.println("--floppy1743                     1743KiB floppy (83 tracks, 21 sectors, Double sided).");
        System.err.println("--floppy1760                     1760KiB floppy (80 tracks, 22 sectors, Double sided).");
        System.err.println("--floppy1840                     1840KiB floppy (80 tracks, 23 sectors, Double sided).");
        System.err.println("--floppy1920                     1920KiB floppy (80 tracks, 24 sectors, Double sided).");
        System.err.println("--floppy2880                     2880KiB floppy (80 tracks, 36 sectors, Double sided).");
        System.err.println("--floppy3120                     3120KiB floppy (80 tracks, 39 sectors, Double sided).");
        System.err.println("--floppy3200                     3200KiB floppy (80 tracks, 40 sectors, Double sided).");
        System.err.println("--floppy3520                     3520KiB floppy (80 tracks, 44 sectors, Double sided).");
        System.err.println("--floppy3840                     3840KiB floppy (80 tracks, 48 sectors, Double sided).");
        System.err.println("Valid options are:");
        System.err.println("--timestamp=value                Timestamp for files in form YYYYMMDDHHMMSS");
        System.err.println("                                 (default is 19900101000000Z).");
        System.err.println("--volumelabel=label              Volume label (default is no label).");
    }

    public static void imageInfo(String name)
    {
        try {
            BaseImage pimg = JPCRRStandardImageDecoder.readImage(name);
            String typeString;
            switch(pimg.getType()) {
            case FLOPPY:
                typeString = "floppy";
                break;
            case HARDDRIVE:
                typeString = "HDD";
                break;
            case CDROM:
                typeString = "CD-ROM";
                break;
            case BIOS:
                typeString = "BIOS";
                break;
            default:
                typeString = "<Unknown>";
                break;
            }
            System.out.println("Type               : " + typeString);
            if(pimg.getType() == BaseImage.Type.FLOPPY || pimg.getType() == BaseImage.Type.HARDDRIVE) {
                byte[] sector = new byte[512];
                System.out.println("Tracks             : " + pimg.getTracks());
                System.out.println("Sides              : " + pimg.getSides());
                System.out.println("Sectors            : " + pimg.getSectors());
                System.out.println("Total sectors      : " + pimg.getTotalSectors());
            } else if(pimg.getType() == BaseImage.Type.CDROM) {
                System.out.println("Total sectors      : " + pimg.getTotalSectors());
            } else if(pimg.getType() == BaseImage.Type.BIOS) {
                System.out.println("Image Size         : " + pimg.getTotalSectors());
            }
            System.out.println("Calculated Disk ID : " + DiskIDAlgorithm.computeIDForDisk(pimg));
            System.out.println("Claimed Disk ID    : " + pimg.getID());
            List<String> comments = pimg.getComments();
            if(comments != null) {
                System.out.println("");
                System.out.println("Comments section:");
                System.out.println("");
                for(String x : comments)
                    System.out.println(x);
            }
        } catch(IOException e) {
            errorDialog(e, "Failed to read image", null, "Quit");
        }
    }

    public static void main(String[] args)
    {
        int firstArg = -1;
        int secondArg = -1;
        String label = null;
        String timestamp = null;

        probeRenameOver(false);

        if(args.length == 1) {
            imageInfo(args[0]);
            return;
        }

        IFormat format;
        try {
            format = new IFormat(null);
            for(int i = 0; i < args.length; i++) {
                if(args[i].startsWith("--"))
                    try {
                        format.addArgument(args[i]);
                    } catch(Exception e) {
                        System.err.println("Error: Invalid option \"" + args[i] + "\".");
                        usage();
                        return;
                    }
                else if(firstArg < 0)
                    firstArg = i;
                else if(secondArg < 0)
                    secondArg = i;
                else {
                    System.err.println("Error: Third non-option argument not allowed.");
                    return;
                }
            }
        } catch(Exception e) {
            System.err.println(e);
            usage();
            return;
        }

        if(secondArg < 0) {
            System.err.println("Error: Two non-option arguments required.");
            usage();
            return;
        }

        label = format.volumeLabel;
        timestamp = format.timestamp;

        BaseImage input;
        RandomAccessFile output;

        try {
            File arg2 = new File(args[secondArg]);
            if(!arg2.exists()) {
                System.err.println("Error: \"" + args[secondArg] + "\" does not exist.");
                return;
            }
            if(!arg2.isFile() && !arg2.isDirectory()) {
                System.err.println("Error: \"" + args[secondArg] + "\" is neither regular file nor a directory.");
                return;
            }

            if(format.typeCode == 3 || format.typeCode == 2) {
                BaseImage.Type type;
                if(format.typeCode == 2)
                    type = BaseImage.Type.CDROM;
                else
                    type = BaseImage.Type.BIOS;
                //Read the image.
                if(!arg2.isFile()) {
                    System.err.println("Error: CDROM/BIOS images can only be made out of regular files.");
                    return;
                }
                FileRawDiskImage input2 = new FileRawDiskImage(args[secondArg], 0, 0, 0, type);
                System.out.println(JPCRRStandardImageEncoder.writeImage(args[firstArg], input2));
            } else if(format.typeCode == 0 || format.typeCode == 1) {
                BaseImage.Type type;
                if(format.typeCode == 0)
                    type = BaseImage.Type.FLOPPY;
                else
                    type = BaseImage.Type.HARDDRIVE;
                if(arg2.isFile()) {
                    input = new FileRawDiskImage(args[secondArg], format.sides, format.tracks, format.sectors,
                        type);
                } else if(arg2.isDirectory()) {
                    TreeDirectoryFile root = TreeDirectoryFile.importTree(args[secondArg], label, timestamp);
                    input = new TreeRawDiskImage(root, format, label, type);
                } else {
                    System.err.println("BUG: Internal error: Didn't I check this is regular or directory?");
                    return;
                }
                System.out.println(JPCRRStandardImageEncoder.writeImage(args[firstArg], input));
            } else {
                System.err.println("Error: Format for image required.");
                usage();
                return;
            }
        } catch(IOException e) {
            System.err.println("Error: " + e.getMessage());
        }
    }
}
