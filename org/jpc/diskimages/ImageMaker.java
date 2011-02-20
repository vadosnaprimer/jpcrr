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
import org.jpc.images.BaseImage;
import org.jpc.images.StorageMethod;
import org.jpc.images.StorageMethodBase;
import org.jpc.images.StorageMethodNormal;
import org.jpc.images.ImageID;
import org.jpc.images.DiskIDAlgorithm;
import static org.jpc.Misc.tempname;
import static org.jpc.Misc.errorDialog;

public class ImageMaker
{
    public static class ParsedImage
    {
        public int typeCode;            //0 => Floppy, 1 => HDD, 2 => (Reserved), 3 => BIOS
        public int tracks;
        public int sectors;
        public int sides;
        public long totalSectors;
        public long sectorsPresent;
        public int method;
        public byte[] geometry;
        public long[] sectorOffsetMap;   //Disk types only.
        public byte[] rawImage;         //BIOS type only.
        public ImageID diskID;
        public List<String> comments;

        public ParsedImage(String fileName) throws IOException
        {
            long commentsOffset = -1;
            comments = new ArrayList<String>();
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
                throw new IOException(fileName + " is Not a valid image file file (unable to read header or " +
                    "bad magic).");
            }
            byte[] tmp = new byte[16];
            System.arraycopy(header, 5, tmp, 0, 16);
            diskID = new ImageID(tmp);
            typeCode = header[21];
            int nameLength = ((int)header[22] & 0xFF) * 256 + ((int)header[23] & 0xFF);
            byte[] nameBuf = new byte[nameLength];
            if(image.read(nameBuf) < nameLength) {
                throw new IOException(fileName + " is Not a valid image file file (unable to read comment field).");
            }
            if(typeCode == 3) {
                //BIOS.
                byte[] biosLen2 = new byte[4];
                if(image.read(biosLen2) < 4) {
                    throw new IOException(fileName + " is Not a valid image file file (unable to read BIOS image " +
                        "length).");
                }
                int biosLen = (((int)biosLen2[0] & 0xFF) << 24) |
                    (((int)biosLen2[1] & 0xFF) << 16) |
                    (((int)biosLen2[2] & 0xFF) << 8) |
                    (((int)biosLen2[3] & 0xFF));
                rawImage = new byte[biosLen];
                if(image.read(rawImage) < biosLen) {
                    throw new IOException(fileName + " is Not a valid image file file (unable to read BIOS image " +
                        "data.");
                }
                commentsOffset = 24 + nameLength + 4 + biosLen;
            } else if(typeCode == 0 || typeCode == 1) {
                geometry = new byte[3];
                if(image.read(geometry) < 3) {
                    throw new IOException(fileName + " is Not a valid image file file (unable to read geometry " +
                        "data.");
                }
                tracks = 1 + ((((int)geometry[0] & 3) << 8) | ((int)geometry[1] & 0xFF));
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
                    throw new IOException(fileName + " is Not a valid image file (unknown compression).");
                }
                method = (int)typeheader[0] & 0xFF;
                sectorsPresent = (((int)typeheader[1] & 0xFF) << 24) |
                    (((int)typeheader[2] & 0xFF) << 16) |
                    (((int)typeheader[3] & 0xFF) << 8) |
                    (((int)typeheader[4] & 0xFF));
                long[] off = new long[]{nameLength + 32};
                sectorOffsetMap = StorageMethod.load(method, image, sectorsPresent, off);
                commentsOffset = off[0];
            } else if(typeCode == 2) {
                byte[] typeheader = new byte[4];
                if(image.read(typeheader) < 4) {
                    throw new IOException(fileName + " is Not a valid image file (unable to read sector count).");
                }
                sectorsPresent = totalSectors = (((int)typeheader[0] & 0xFF) << 24) |
                    (((int)typeheader[1] & 0xFF) << 16) |
                    (((int)typeheader[2] & 0xFF) << 8) |
                    (((int)typeheader[3] & 0xFF));
                //CD-ROMs always use normal disk mapping.
                long[] off = new long[]{nameLength + 28};
                sectorOffsetMap = StorageMethod.loadNormal(image, sectorsPresent, off);
                commentsOffset = off[0];
            } else {
                throw new IOException(fileName + " is image of unknown type.");
            }

            image.seek(commentsOffset);
            byte[] lbuffer = new byte[2];
            int x = image.read(lbuffer);
            if(x <= 0) {
                comments = null;
                image.close();
                return;
            }
            if(x < 2) {
                throw new IOException(fileName + " is Not a valid image file (unable to read comment length).");
            }
            while(lbuffer[0] != 0 || lbuffer[1] != 0) {
                int clength = (int)lbuffer[0] * 256 + (int)lbuffer[1] - 1;
                if(clength > 0) {
                    byte[] cbuffer = new byte[clength];
                    if(image.read(cbuffer) < clength) {
                        throw new IOException(fileName + " is Not a valid image file (unable to read comment).");
                    }
                    String comment = Charset.forName("UTF-8").newDecoder().decode(ByteBuffer.wrap(cbuffer))
                        .toString();
                    comments.add(comment);
                } else
                    comments.add("");

                x = image.read(lbuffer);
                if(x < 2) {
                    throw new IOException(fileName + " is Not a valid image file (unable to read comment length).");
                }
            }
            image.close();
        }
    };

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

    private static byte[] getGeometry(BaseImage input) throws IOException
    {
        byte[] geometry = new byte[3];
        int tracks = input.getTracks();
        int sectors = input.getSectors();
        int sides = input.getSides();
        geometry[0] = (byte)((((tracks - 1) >> 8) & 3) | (((sides - 1) & 15) << 2));
        geometry[1] = (byte)(((tracks - 1) & 255));
        geometry[2] = (byte)(((sectors - 1) & 255));
        return geometry;
    }



    static int[] scanSectorMap(BaseImage file) throws IOException
    {
        long totalsectors = file.getTotalSectors();
        int[] sectors = new int[(int)((totalsectors + 30) / 31)];

        for(int i = 0; i < totalsectors; i++)
            if(file.nontrivialContents(i))
                sectors[i / 31] |= (1 << ((i) % 31));
        return sectors;
    }

    public static void writeImageHeader(RandomAccessFile output, ImageID diskID, int typeID) throws
        IOException
    {
        byte[] header = new byte[] {73, 77, 65, 71, 69};
        output.write(header);
        output.write(diskID.getIDAsBytes());
        output.write(new byte[]{(byte)typeID, 0, 0});
    }

    public static ImageID computeDiskID(BaseImage input, int typeID) throws
        IOException
    {
        DiskIDAlgorithm algo = new DiskIDAlgorithm();
        byte[] sector = new byte[512];
        boolean hasGeometry = (input.getSides() > 0 && input.getTracks() > 0 && input.getSectors() > 0);
        long inLength = input.getTotalSectors();
        int tracks = -1, sectors = -1, sides = -1;
        long backupTotal;

        algo.addBuffer(new byte[]{(byte)typeID});
        if(hasGeometry) {
            tracks = input.getTracks();
            sectors = input.getSectors();
            sides = input.getSides();
            algo.addBuffer(getGeometry(input));
            backupTotal = tracks * sectors * sides;
        } else
            backupTotal = inLength;
        for(int i = 0; i < backupTotal; i++) {
            if(input.read(i, sector, 1))
                algo.addBuffer(sector);
            else
                algo.addZeroes(512);
        }

        return algo.getID();
    }

    private static long countSectors(int[] sectormap)
    {
        long used = 0;
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
            if(pimg.typeCode == 0 || pimg.typeCode == 1) {
                byte[] sector = new byte[512];
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
                        algo.addZeroes(512);
                }
                System.out.println("Sectors present    : " + actualSectors);
                System.out.println("Calculated Disk ID : " + algo.getID());
            } else if(pimg.typeCode == 2) {
                byte[] sector = new byte[512];
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
                        algo.addZeroes(512);
                }
                System.out.println("Calculated Disk ID : " + algo.getID());
            } else if(pimg.typeCode == 3) {
                System.out.println("Image Size         : " + pimg.rawImage.length);
                DiskIDAlgorithm algo = new DiskIDAlgorithm();
                algo.addBuffer(new byte[] {3});   //ID it as BIOS.
                algo.addBuffer(pimg.rawImage);
                System.out.println("Calculated Disk ID : " + algo.getID());
            }

            ImageID claimedID = pimg.diskID;
            System.out.println("Claimed Disk ID    : " + claimedID);
            List<String> comments = pimg.comments;
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

    public static ImageID makeBIOSImage(RandomAccessFile output, RandomAccessFile input, IFormat format)
        throws IOException
    {
        int biosSize = (int)input.length();
        byte[] bios = new byte[biosSize];
        if(input.read(bios) < biosSize)
            throw new IOException("Can't read raw bios image file.");

        //Calculate "Disk" ID.
        DiskIDAlgorithm algo = new DiskIDAlgorithm();
        algo.addBuffer(new byte[]{(byte)3});
        algo.addBuffer(bios);
        ImageID diskID = algo.getID();
        ImageMaker.writeImageHeader(output, diskID, 3);
        byte[] imageLen = new byte[4];
        imageLen[0] = (byte)((biosSize >>> 24) & 0xFF);
        imageLen[1] = (byte)((biosSize >>> 16) & 0xFF);
        imageLen[2] = (byte)((biosSize >>> 8) & 0xFF);
        imageLen[3] = (byte)((biosSize) & 0xFF);
        output.write(imageLen);
        output.write(bios);
        output.close();
        return diskID;
    }

    public static ImageID makeCDROMImage(RandomAccessFile output, FileRawDiskImage input)
        throws IOException
    {
        ImageID diskID = ImageMaker.computeDiskID(input, 2);
        ImageMaker.writeImageHeader(output, diskID, 2);
        long sectorsUsed = input.getTotalSectors();
        byte[] type = new byte[4];
        type[0] = (byte)((sectorsUsed >>> 24) & 0xFF);
        type[1] = (byte)((sectorsUsed >>> 16) & 0xFF);
        type[2] = (byte)((sectorsUsed >>> 8) & 0xFF);
        type[3] = (byte)((sectorsUsed) & 0xFF);
        output.write(type);

        StorageMethod.saveNormal(input, sectorsUsed, output);
        output.close();
        return diskID;
    }

    public static ImageID makeFloppyHDDImage(RandomAccessFile output, BaseImage input, int typeCode)
        throws IOException
    {
        int[] sectorMap;
        sectorMap = ImageMaker.scanSectorMap(input);
        byte[] typeID = new byte[1];
        ImageID diskID = ImageMaker.computeDiskID(input, typeCode);
        ImageMaker.writeImageHeader(output, diskID, typeCode);
        output.write(getGeometry(input));
        StorageMethodBase best = null;
        long sectorsUsed = countSectors(sectorMap);
        int bestIndex = StorageMethod.findBestIndex(sectorMap, sectorsUsed);
        byte[] type = new byte[5];
        type[0] = (byte)bestIndex;
        type[1] = (byte)((sectorsUsed >>> 24) & 0xFF);
        type[2] = (byte)((sectorsUsed >>> 16) & 0xFF);
        type[3] = (byte)((sectorsUsed >>> 8) & 0xFF);
        type[4] = (byte)((sectorsUsed) & 0xFF);
        output.write(type);

        StorageMethod.save(bestIndex, sectorMap, input, sectorsUsed, output);

        List<String> comments = input.getComments();
        if(comments != null)
            for(String x : comments) {
                ByteBuffer buf;
                try {
                    buf = Charset.forName("UTF-8").newEncoder().encode(CharBuffer.wrap(x));
                } catch(CharacterCodingException e) {
                    throw new IOException("Invalid comment (Should not happen!).");
                }
                int length = buf.remaining() + 1;
                byte[] buf2 = new byte[length + 1];
                buf2[0] = (byte)((length >>> 8) & 0xFF);
                buf2[1] = (byte)(length & 0xFF);
                buf.get(buf2, 2, length - 1);
                output.write(buf2);
            }
        output.write(new byte[]{0, 0});

        output.close();
        return diskID;
    }

    public static void main(String[] args)
    {
        int firstArg = -1;
        int secondArg = -1;
        String label = null;
        String timestamp = null;

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

            String temporaryName = tempname(args[firstArg]);
            File firstArgFile = new File(temporaryName);
            while(firstArgFile.exists())
                firstArgFile = new File(temporaryName = tempname(args[firstArg]));
            firstArgFile.deleteOnExit();

            output = new RandomAccessFile(firstArgFile, "rw");

            if(format.typeCode == 3) {
                //Read the image.
                if(!arg2.isFile()) {
                    System.err.println("Error: BIOS images can only be made out of regular files.");
                    return;
                }
                RandomAccessFile input2 = new RandomAccessFile(args[secondArg], "r");
                System.out.println(makeBIOSImage(output, input2, format));
            } else if(format.typeCode == 2) {
                if(!arg2.isFile()) {
                    System.err.println("Error: CD images can only be made out of regular files.");
                    return;
                }
                FileRawDiskImage input2 = new FileRawDiskImage(args[secondArg], 0, 0, 0, BaseImage.Type.CDROM);
                System.out.println(makeCDROMImage(output, input2));
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
                System.out.println(makeFloppyHDDImage(output, input, format.typeCode));
            } else {
                System.err.println("Error: Format for image required.");
                usage();
                return;
            }

            firstArgFile.renameTo(new File(args[firstArg]));

        } catch(IOException e) {
            System.err.println("Error: " + e.getMessage());
        }
    }

    public static ImageID makeImage(RandomAccessFile out, RandomAccessFile input2) throws IOException
    {
        byte[] buffer = new byte[1024];
        byte[] id = new byte[16];
        boolean idObtained = false;
        while(true) {
            int r = input2.read(buffer);
            if(r <= 0)
                break;
            if(!idObtained)
                System.arraycopy(buffer, 5, id, 0, 16);
            idObtained = true;
            out.write(buffer, 0, r);
        }
        return new ImageID(id);
    }
}
