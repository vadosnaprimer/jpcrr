package org.jpc.mkfs;

import org.jpc.images.ImageID;
import org.jpc.images.BaseImage;
import org.jpc.images.StorageMethod;
import static org.jpc.Misc.tempname;
import static org.jpc.Misc.renameFile;
import java.io.*;
import java.nio.*;
import java.nio.charset.*;
import java.util.List;

public class JPCRRStandardImageEncoder
{
    public static ImageID writeImage(String s, BaseImage img) throws IOException
    {
        return writeImage(new File(s), img);
    }

    public static ImageID writeImage(File f, BaseImage img) throws IOException
    {
        String temporaryName = tempname(f.getAbsolutePath());
        File firstArgFile = new File(temporaryName);
        while(firstArgFile.exists())
            firstArgFile = new File(temporaryName = tempname(f.getAbsolutePath()));
        RandomAccessFile h = new RandomAccessFile(firstArgFile, "rw");
        try {
            ImageID id = writeImage(h, img);
            h.close();
            renameFile(firstArgFile, f);
            return id;
        } catch(IOException e) {
            firstArgFile.delete();
            throw e;
        }
    }

    public static ImageID writeImage(RandomAccessFile h, BaseImage img) throws IOException
    {
        byte[] mainHeader = new byte[24];
        mainHeader[0] = (byte)'I';
        mainHeader[1] = (byte)'M';
        mainHeader[2] = (byte)'A';
        mainHeader[3] = (byte)'G';
        mainHeader[4] = (byte)'E';
        ImageID id = img.getID();
        System.arraycopy(id.getIDAsBytes(), 0, mainHeader, 5, 16);
        switch(img.getType()) {
        case FLOPPY:
            mainHeader[21] = 0;
            break;
        case HARDDRIVE:
            mainHeader[21] = 1;
            break;
        case CDROM:
            mainHeader[21] = 2;
            break;
        case BIOS:
            mainHeader[21] = 3;
            break;
        }
        //Bytes 22 and 23 are zero.
        h.write(mainHeader);

        int tracks = img.getTracks();
        int sides = img.getSides();
        int sectors = img.getSectors();
        if(tracks > 0 && sides > 0 && sectors > 0) {
            byte[] geometry = new byte[8];
            geometry[0] = (byte)((((tracks - 1) >> 8) & 3) | (((sides - 1) & 15) << 2));
            geometry[1] = (byte)(((tracks - 1) & 255));
            geometry[2] = (byte)(((sectors - 1) & 255));
            int[] sectormap = StorageMethod.scanSectorMap(img);
            long storedSectors = StorageMethod.countSectors(sectormap);
            int index = StorageMethod.findBestIndex(sectormap, storedSectors);
            geometry[3] = (byte)index;
            geometry[4] = (byte)(storedSectors >>> 24);
            geometry[5] = (byte)(storedSectors >>> 16);
            geometry[6] = (byte)(storedSectors >>> 8);
            geometry[7] = (byte)storedSectors;
            h.write(geometry);
            StorageMethod.save(index, sectormap, img, storedSectors, h);
        } else {
            int ssize = 512;
            if(img.getType() == BaseImage.Type.BIOS)
                ssize = 1;
            long storedSectors = img.getTotalSectors();
            byte[] geometry = new byte[4];
            geometry[0] = (byte)(storedSectors >>> 24);
            geometry[1] = (byte)(storedSectors >>> 16);
            geometry[2] = (byte)(storedSectors >>> 8);
            geometry[3] = (byte)storedSectors;
            h.write(geometry);
            StorageMethod.saveNormal(img, storedSectors, h, ssize);
        }
        List<String> comments = img.getComments();
        if(comments != null) {
            byte[] header = new byte[2];
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
                h.write(buf2);
            }
            header[0] = 0;
            header[1] = 0;
            h.write(header);
        }
        return id;
    }
};
