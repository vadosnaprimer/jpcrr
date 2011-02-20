package org.jpc.images;

import org.jpc.emulator.StatusDumper;
import java.io.*;
import java.util.*;
import java.nio.charset.*;
import java.nio.*;

public class JPCRRStandardImageDecoder
{
    private static BaseImage parseFloppyHDD(RandomAccessFile f, ImageID id, long[] offset,
        BaseImage.Type typeCode, int maxSides, int maxTracks, int maxSectors, String name) throws IOException
    {
        //Read the storage header.
        byte[] storageData = new byte[8];
        f.readFully(storageData);
        int tracks = 1 + ((((int)storageData[0] & 3) << 8) | ((int)storageData[1] & 0xFF));
        int sectors = 1 + ((int)storageData[2] & 0xFF);
        int sides = 1 + (((int)storageData[0] >> 2) & 15);
        int overflow = (((int)storageData[0] & 0xFF) >> 6);
        if(overflow != 0 || tracks > maxTracks || sectors > maxSectors || sides > maxSides)
            throw new IOException(name + " has invalid geometry");
        int storageMethod = (int)storageData[3] & 0xFF;
        int sectorsPresent = (((int)storageData[4] & 0xFF) << 24) |
                    (((int)storageData[5] & 0xFF) << 16) |
                    (((int)storageData[6] & 0xFF) << 8) |
                    (((int)storageData[7] & 0xFF));
        offset[0] += 8;

        if((long)sectorsPresent > (long)tracks * sectors * sides)
            throw new IOException("Image claims to have more sectors present than total");
        long[] sectorOffsetMap = StorageMethod.load(storageMethod, f, sectorsPresent, offset);
        return new UncompressedLocalFileImage(typeCode, tracks, sides, sectors, sectorsPresent,
            sectorOffsetMap, f, id, name, parseComments(f, offset[0]));
    }

    private static BaseImage parseCDROMBIOS(RandomAccessFile f, ImageID id, long[] offset, BaseImage.Type typeCode,
        String name) throws IOException
    {
        int ssize = (typeCode == BaseImage.Type.BIOS) ? 1 : 512;
        byte[] sectorHeader = new byte[4];
        f.readFully(sectorHeader);
        int sectorsPresent = (((int)sectorHeader[0] & 0xFF) << 24) |
                    (((int)sectorHeader[1] & 0xFF) << 16) |
                    (((int)sectorHeader[2] & 0xFF) << 8) |
                    (((int)sectorHeader[3] & 0xFF));
        //CD-ROMs always use normal disk mapping. The +4 is because of sectors present header.
        offset[0] += 4;
        long[] sectorOffsetMap = StorageMethod.loadNormal(f, sectorsPresent, offset, ssize);
        return new UncompressedLocalFileImage(typeCode, 0, 0, 0, sectorsPresent,
            sectorOffsetMap, f, id, name, parseComments(f, offset[0]));
    }

    private static List<String> parseComments(RandomAccessFile h, long offset) throws IOException
    {
        List<String> ret = new ArrayList<String>();
        byte[] lenField = new byte[2];
        int x;
        h.seek(offset);
        x = h.read(lenField);
        if(x < 0)
            return ret; //No comments.
        if(x < 2)
            throw new IOException("Incomplete comment length field");
        while(lenField[0] != 0 || lenField[1] != 0) {
            byte[] cLine = new byte[(int)lenField[0] * 256 + lenField[1] - 1];
            if(cLine.length > 0) {
                h.readFully(cLine);
                String comment = Charset.forName("UTF-8").newDecoder().decode(ByteBuffer.wrap(cLine)).toString();
                ret.add(comment);
            } else {
                ret.add("");
            }
            h.readFully(lenField);
        }
        return ret;
    }

    public static BaseImage readImage(File f) throws IOException
    {
        RandomAccessFile h = new RandomAccessFile(f, "r");
        byte[] header = new byte[24];   //Header is 24 bytes (+ image name, but that's not used anymore).
        byte[] idBuf = new byte[16];
        h.readFully(header);

        //Check magic.
        if(header[0] != (byte)'I' || header[1] != (byte)'M' || header[2] != (byte)'A' || header[3] != (byte)'G' ||
            header[4] != (byte)'E')
            throw new IOException("Not a valid JPC-RR image");

        //Extract Image ID, type code and name length.
        System.arraycopy(header, 5, idBuf, 0, 16);
        ImageID id = new ImageID(idBuf);
        int typeCode = (int)header[21] & 0xFF;
        int nameLen = (int)header[22] * 256 + header[23];
        long[] offset = new long[]{24 + nameLen};  //Actual image data starts from this offset.

        //Read the name away and ignore.
        if(nameLen > 0) {
            byte[] tmp = new byte[nameLen];
            h.readFully(tmp);
        }

        //Read the actual image part.
        BaseImage img;
        switch(typeCode) {
        case 0:
            img = parseFloppyHDD(h, id, offset, BaseImage.Type.FLOPPY, 2, 256, 255, f.getAbsolutePath());
            break;
        case 1:
            img = parseFloppyHDD(h, id, offset, BaseImage.Type.HARDDRIVE, 16, 1024, 63, f.getAbsolutePath());
            break;
        case 2:
            img = parseCDROMBIOS(h, id, offset, BaseImage.Type.CDROM, f.getAbsolutePath());
            break;
        case 3:
            img = parseCDROMBIOS(h, id, offset, BaseImage.Type.BIOS, f.getAbsolutePath());
            break;
        default:
            throw new IOException("Unknown image type code " + typeCode + ".");
        }
        return img;
    }

    public static ImageID readIDFromImage(File f, BaseImage.Type[] type) throws IOException
    {
        RandomAccessFile h = new RandomAccessFile(f, "r");
        byte[] header = new byte[24];   //Header is 24 bytes (+ image name, but that's not used anymore).
        byte[] idBuf = new byte[16];
        h.readFully(header);
        h.close();

        //Check magic.
        if(header[0] != (byte)'I' || header[1] != (byte)'M' || header[2] != (byte)'A' || header[3] != (byte)'G' ||
            header[4] != (byte)'E' || ((int)header[21] & 0xFF) > 3)
            throw new IOException("Not a valid JPC-RR image");
        //Extract Image ID.
        System.arraycopy(header, 5, idBuf, 0, 16);
        switch(header[21]) {
        case 0:
            type[0] = BaseImage.Type.FLOPPY;
            break;
        case 1:
            type[0] = BaseImage.Type.HARDDRIVE;
            break;
        case 2:
            type[0] = BaseImage.Type.CDROM;
            break;
        case 3:
            type[0] = BaseImage.Type.BIOS;
            break;
        }
        return new ImageID(idBuf);
    }

    public static ImageID readIDFromImage(String s, BaseImage.Type[] type) throws IOException
    {
        return readIDFromImage(new File(s), type);
    }

    public static BaseImage readImage(String s) throws IOException
    {
        return readImage(new File(s));
    }

};
