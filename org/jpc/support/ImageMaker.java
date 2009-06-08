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
import java.util.*;
import java.nio.charset.*;
import java.nio.*;

public class ImageMaker
{
    public static class ParsedImage
    {
        public int typeCode;            //0 => Floppy, 1 => HDD, 2 => (Reserved), 3 => BIOS
        public int tracks;
        public int sectors;
        public int sides;
        public int totalSectors;
        public int sectorsPresent;
        public int method;
        public byte[] geometry;
        public int[] sectorOffsetMap;   //Disk types only.
        public byte[] rawImage;         //BIOS type only.
        public byte[] diskID;
        public String diskName;

        public ParsedImage(String fileName) throws IOException
        {
            RandomAccessFile image = new RandomAccessFile(fileName, "r");
            tracks = -1;
            sectors = -1;
            sides = -1;
            totalSectors = -1;
            sectorsPresent = -1;
            method = -1;
            byte[] header = new byte[24];
            if(image.read(header) < 24 || header[0] != 73 || header[1] != 77 || header[2] != 65 || header[3] != 71 ||
                    header[4] != 69 ) {
                throw new IOException(fileName + " is Not a valid image file file.");
            }
            diskID = new byte[16];
            System.arraycopy(header, 5, diskID, 0, 16);
            typeCode = header[21];
            int nameLength = ((int)header[22] & 0xFF) * 256 + ((int)header[23] & 0xFF);
            byte[] nameBuf = new byte[nameLength];
            if(image.read(nameBuf) < nameLength) {
                throw new IOException(fileName + " is Not a valid image file file.");
            }
            diskName = Charset.forName("UTF-8").newDecoder().decode(ByteBuffer.wrap(nameBuf)).toString();
            if(typeCode == 3) {
                //BIOS.
                byte[] biosLen2 = new byte[4];
                if(image.read(biosLen2) < 4) {
                    throw new IOException(fileName + " is Not a valid image file file.");
                }
                int biosLen = (((int)biosLen2[0] & 0xFF) << 24) |
                    (((int)biosLen2[1] & 0xFF) << 16) |
                    (((int)biosLen2[2] & 0xFF) << 8) |
                    (((int)biosLen2[3] & 0xFF));
                rawImage = new byte[biosLen];
                if(image.read(rawImage) < biosLen) {
                    throw new IOException(fileName + " is Not a valid image file file.");
                }
            } else if(typeCode == 0 || typeCode == 1) {
                geometry = new byte[3];
                if(image.read(geometry) < 3) {
                    throw new IOException(fileName + " is Not a valid image file file.");
                }
                tracks = 1 + (((int)geometry[0] & 3 << 8) | ((int)geometry[1] & 0xFF));
                sectors = 1 + ((int)geometry[2] & 0xFF);
                sides = 1 + (((int)geometry[0] >> 2) & 15);
                int overflow = (((int)geometry[0] & 0xFF) >> 6);
                if(overflow != 0) {
                    throw new IOException(fileName + " has unrecognized geometry " + ((int)geometry[0] & 0xFF) + " " + 
                        ((int)geometry[1] & 0xFF) + " " + ((int)geometry[2] & 0xFF) + ".");
                } else if(typeCode == 0 && (tracks > 256 || sectors > 255 || sides > 2)) {
                    throw new IOException(fileName + " claims to be floppy with illegal geometry: " + tracks + 
                        " tracks, " + sides + " sides and " + sectors + " sectors.");
                } else if(typeCode == 1 && (tracks > 1024 || sectors > 63 || sides > 16)) {
                    throw new IOException(fileName + " claims to be HDD with illegal geometry: " + tracks + 
                        " cylinders, " + sides + " heads and " + sectors + " sectors.");
                }
                totalSectors = tracks * sides * sectors;
                byte[] typeheader = new byte[5];
                if(image.read(typeheader) < 5) {
                    throw new IOException(fileName + " is Not a valid image file.");
                }
                method = (int)typeheader[0] & 0xFF;
                sectorsPresent = (((int)typeheader[1] & 0xFF) << 24) |
                    (((int)typeheader[2] & 0xFF) << 16) |
                    (((int)typeheader[3] & 0xFF) << 8) |
                    (((int)typeheader[4] & 0xFF));
                sectorOffsetMap = ImageFormats.savers[method].loadSectorMap(image, method, sectorsPresent, nameLength + 32);
            } else if(typeCode == 2) {
                byte[] typeheader = new byte[4];
                if(image.read(typeheader) < 4) {
                    throw new IOException(fileName + " is Not a valid image file.");
                }
                sectorsPresent = totalSectors = (((int)typeheader[0] & 0xFF) << 24) |
                    (((int)typeheader[1] & 0xFF) << 16) |
                    (((int)typeheader[2] & 0xFF) << 8) |
                    (((int)typeheader[3] & 0xFF));
                //CD-ROMs always use normal disk mapping.
                sectorOffsetMap = ImageFormats.savers[0].loadSectorMap(image, method, sectorsPresent, nameLength + 28);
            } else {
                throw new IOException(fileName + " is image of unknown type.");
            }

        }
    };

    static class IFormat
    {
        public int typeCode;
        public int tracks;
        public int sectors;
        public int sides;

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
            else if(specifier.equals("--floppy880"))   { typeCode = 0; tracks = 80; sectors = 10; sides = 2; }
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
                throw new Exception("Invalid format specifier " + specifier + ".");
        }
    }

    private static void usage()
    {
        System.err.println("java ImageMaker <format> <destination> <source> <diskname>");
        System.err.println("Valid formats are:");
        System.err.println("--BIOS                           BIOS image.");
        System.err.println("--CDROM                          CD-ROM image.");
        System.err.println("--HDD=cylinders,sectors,heads    Hard disk with specified geometry.");
        System.err.println("--floppy=tracks,sectors,sides    Floppy disk with specified geometry.");
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
    }

    static int[] scanSectorMap(RawDiskImage file, int totalsectors) throws IOException
    {
         byte[] sector = new byte[512];
         if(totalsectors != file.getSectorCount())
             throw new IOException("The image has " + file.getSectorCount() + " sectors while it should have " + 
                 totalsectors + " according to selected geometry.");
         int[] sectors = new int[(totalsectors + 30) / 31];

         for(int i = 0; i < totalsectors; i++) {
             if(!file.isSectorEmpty(i))
                 sectors[i / 31] |= (1 << ((i) % 31));
         }
         return sectors;
    }

    public static void writeImageHeader(RandomAccessFile output, byte[] diskID, byte[] typeID, String diskName) throws
        IOException
    {
        byte[] header = new byte[] {73, 77, 65, 71, 69};
        output.write(header);
        output.write(diskID);
        output.write(typeID);
        ByteBuffer buf;
        try {
            buf = Charset.forName("UTF-8").newEncoder().encode(CharBuffer.wrap(diskName));
        } catch(CharacterCodingException e) {
            throw new IOException("Invalid disk name (Should not happen!).");
        }
        int length = buf.remaining();
        byte[] buf2 = new byte[length + 2];
        buf2[0] = (byte)((length >>> 8) & 0xFF);
        buf2[1] = (byte)(length & 0xFF);
        buf.get(buf2, 2, length);
        output.write(buf2);
    }

    public static byte[] computeDiskID(RawDiskImage input, byte[] typeID, byte[] geometry) throws
        IOException
    {
        DiskIDAlgorithm algo = new DiskIDAlgorithm();
        byte[] sector = new byte[512];
        int inLength = input.getSectorCount();
        int tracks = -1, sectors = -1, sides = -1;
        int backupTotal;

        if(geometry != null) {
            if(geometry[0] > 63) {
                throw new IOException("Invalid geometry to be written.");
            }
            tracks = 1 + (((int)geometry[0] & 3 << 8) | ((int)geometry[1] & 0xFF));
            sectors = 1 + ((int)geometry[2] & 0xFF);
            sides = 1 + (((int)geometry[0] >> 2) & 15);
        }
        algo.addBuffer(typeID);
        if(geometry != null)
            algo.addBuffer(geometry);
        if(geometry != null)
            backupTotal = tracks * sectors * sides;
        else
            backupTotal = inLength;
        for(int i = 0; i < backupTotal; i++) {
            input.readSector(i, sector);
            algo.addBuffer(sector);
        } 

        byte[] diskID = algo.getFinalOutput();
        return diskID;        
    }

    private static int countSectors(int[] sectormap)
    {
        int used = 0;
        for(int i = 0; i < sectormap.length * 31; i++) {
            if((sectormap[i / 31] & (1 << (i % 31))) != 0)
               used = i + 1;
        }
        return used;
    }

    public static void imageInfo(String name) 
    {
        try {
            ParsedImage pimg = new ParsedImage(name);
            RandomAccessFile image = new RandomAccessFile(name, "r");
            String typeString;
            switch(pimg.typeCode) {
            case 0:
                typeString = "floppy";
                break;
            case 1:
                typeString = "HDD";
                break;
            case 2:
                typeString = "CD-ROM";
                break;
            case 3:
                typeString = "BIOS";
                break;
            default:
                typeString = "<Unknown>";
                break;
            }
            System.out.println("Type               : " + typeString);
            System.out.println("Disk name          : " + pimg.diskName);
            if(pimg.typeCode == 0 || pimg.typeCode == 1) {
                byte[] sector = new byte[512];
                byte[] zero = new byte[512];
                System.out.println("Tracks             : " + pimg.tracks);
                System.out.println("Sides              : " + pimg.sides);
                System.out.println("Sectors            : " + pimg.sectors);
                System.out.println("Total sectors      : " + pimg.totalSectors);
                System.out.println("Primary extent size: " + pimg.sectorsPresent);
                System.out.println("Storage Method     : " + pimg.method);
                int actualSectors = 0;

                DiskIDAlgorithm algo = new DiskIDAlgorithm();
                algo.addBuffer(new byte[] {(byte)pimg.typeCode});   //ID it as Floppy/HDD.
                algo.addBuffer(pimg.geometry);

                for(int i = 0; i < pimg.totalSectors; i++) {
                    if(i < pimg.sectorOffsetMap.length && pimg.sectorOffsetMap[i] > 0) {
                        image.seek(pimg.sectorOffsetMap[i]);
                        if(image.read(sector) < 512) {
                            throw new IOException("Failed to read sector from image file.");
                        }
                        algo.addBuffer(sector);
                        actualSectors++;
                    } else
                        algo.addBuffer(zero);
                }
                System.out.println("Sectors present    : " + actualSectors);
                System.out.println("Calculated Disk ID : " + algo.getFinalOutputString());
            } else if(pimg.typeCode == 2) {
                byte[] sector = new byte[512];
                byte[] zero = new byte[512];
                System.out.println("Total sectors      : " + pimg.totalSectors);
                DiskIDAlgorithm algo = new DiskIDAlgorithm();
                algo.addBuffer(new byte[] {(byte)pimg.typeCode});   //ID it as CD-ROM.

                for(int i = 0; i < pimg.totalSectors; i++) {
                    if(i < pimg.sectorOffsetMap.length && pimg.sectorOffsetMap[i] > 0) {
                        image.seek(pimg.sectorOffsetMap[i]);
                        if(image.read(sector) < 512) {
                            throw new IOException("Failed to read sector from image file.");
                        }
                        algo.addBuffer(sector);
                    } else
                        algo.addBuffer(zero);
                }
                System.out.println("Calculated Disk ID : " + algo.getFinalOutputString());
            } else if(pimg.typeCode == 3) {
                System.out.println("Image Size         : " + pimg.rawImage.length);
                DiskIDAlgorithm algo = new DiskIDAlgorithm();
                algo.addBuffer(new byte[] {3});   //ID it as BIOS.
                algo.addBuffer(pimg.rawImage);
                System.out.println("Calculated Disk ID : " + algo.getFinalOutputString());
            }
            System.out.println("Claimed Disk ID    : " + (new ImageLibrary.ByteArray(pimg.diskID)));
        } catch(IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args)
    {
        int typeCode = -1;
        int tracks = -1;
        int sides = -1;
        int sectors = -1;
        int[] sectorMap;

        if(args.length == 1) {
            imageInfo(args[0]);
            return;
        }
 
        if(args.length < 4) {
            usage();
            return;
        }

        IFormat format;
        try {
            format = new IFormat(args[0]);
        } catch(Exception e) {
            System.err.println(e);
            usage();
            return;
        }

        RawDiskImage input;
        RandomAccessFile input2;
        RandomAccessFile output;

        try {
            input = new FileRawDiskImage(args[2]);
            input2 = new RandomAccessFile(args[2], "r");
            output = new RandomAccessFile(args[1], "rw");
            String diskName = args[3];
            int biosSize = -1;

            if(format.typeCode == 3) {
                //Read the image.
                biosSize = (int)input2.length();
                byte[] bios = new byte[biosSize];
                if(input2.read(bios) < biosSize)
                    throw new IOException("Can't read raw bios image file.");

                //Calculate "Disk" ID.                
                DiskIDAlgorithm algo = new DiskIDAlgorithm();
                byte[] typeID = new byte[] {3};
                algo.addBuffer(typeID);
                algo.addBuffer(bios);
                byte[] diskID = algo.getFinalOutput();
                ImageMaker.writeImageHeader(output, diskID, typeID, diskName);
                byte[] imageLen = new byte[4];
                imageLen[0] = (byte)((biosSize >>> 24) & 0xFF);
                imageLen[1] = (byte)((biosSize >>> 16) & 0xFF);
                imageLen[2] = (byte)((biosSize >>> 8) & 0xFF);
                imageLen[3] = (byte)((biosSize) & 0xFF);
                output.write(imageLen);
                output.write(bios);
                output.close();
                System.out.println((new ImageLibrary.ByteArray(diskID)));
            } else if(format.typeCode == 2) {
                byte[] typeID = new byte[1];
                typeID[0] = (byte)format.typeCode;
                byte[] diskID = ImageMaker.computeDiskID(input, typeID, null);
                ImageMaker.writeImageHeader(output, diskID, typeID, diskName);
                int sectorsUsed = input.getSectorCount();
                byte[] type = new byte[4];
                type[0] = (byte)((sectorsUsed >>> 24) & 0xFF);
                type[1] = (byte)((sectorsUsed >>> 16) & 0xFF);
                type[2] = (byte)((sectorsUsed >>> 8) & 0xFF);
                type[3] = (byte)((sectorsUsed) & 0xFF);
                output.write(type);

                ImageFormats.savers[0].save(0, null, input, sectorsUsed, sectorsUsed, output);
                System.out.println((new ImageLibrary.ByteArray(diskID)));
            } else {
                byte[] geometry = new byte[3];
                geometry[0] = (byte)((((format.tracks - 1) >> 8) & 3) | (((format.sides - 1) & 15) << 2));
                geometry[1] = (byte)(((format.tracks - 1) & 255));
                geometry[2] = (byte)(((format.sectors - 1) & 255));
                sectorMap = ImageMaker.scanSectorMap(input, format.tracks * format.sectors * format.sides);
                byte[] typeID = new byte[1];
                typeID[0] = (byte)format.typeCode;
                byte[] diskID = ImageMaker.computeDiskID(input, typeID, geometry);
                ImageMaker.writeImageHeader(output, diskID, typeID, diskName);
                output.write(geometry);
                ImageFormats.DiskImageType best = null;
                int bestIndex = 0;
                int sectorsUsed = countSectors(sectorMap);
                int score = 0x7FFFFFFF;
                for(int i = 0; i < ImageFormats.savers.length; i++) {
                    try {
                        int scored = ImageFormats.savers[i].saveSize(i, sectorMap, format.tracks * format.sectors * 
                            format.sides, sectorsUsed);
                        if(score > scored) {
                            best = ImageFormats.savers[i];
                            score = scored;
                            bestIndex = i;
                        }
                    } catch(Exception e) {
                        //That method can't save it.
                    }
                }
                byte[] type = new byte[5];
                type[0] = (byte)bestIndex;
                type[1] = (byte)((sectorsUsed >>> 24) & 0xFF);
                type[2] = (byte)((sectorsUsed >>> 16) & 0xFF);
                type[3] = (byte)((sectorsUsed >>> 8) & 0xFF);
                type[4] = (byte)((sectorsUsed) & 0xFF);
                output.write(type);

                best.save(bestIndex, sectorMap, input, format.tracks * format.sectors * format.sides, sectorsUsed, output);
                System.out.println((new ImageLibrary.ByteArray(diskID)));
            }

        } catch(IOException e) {
            System.err.println(e);
        }
    }
}
