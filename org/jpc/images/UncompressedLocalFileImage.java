package org.jpc.images;

import java.io.*;
import java.util.*;
import org.jpc.emulator.StatusDumper;

class UncompressedLocalFileImage implements BaseImage
{
    public static final long SECTOR_NOT_PRESENT = -1;
    private BaseImage.Type type;
    private int tracks;
    private int sides;
    private int sectors;
    private long totalSectors;
    private long[] offsetMap;
    private RandomAccessFile backingFile;
    private ImageID id;
    private String name;
    private List<String> comments;
    private static final byte[] BLANK;

    static
    {
        BLANK = new byte[BaseImage.SECTOR_SIZE];
    }

    public UncompressedLocalFileImage(BaseImage.Type _type, int _tracks, int _sides, int _sectors,
        long _totalSectors, long[] _offsetMap, RandomAccessFile _backingFile, ImageID _id, String _name,
        List<String> _comments)
    {
        type = _type;
        tracks = _tracks;
        sides = _sides;
        sectors = _sectors;
        totalSectors = _totalSectors;
        offsetMap = _offsetMap;
        backingFile = _backingFile;
        id = _id;
        name = _name;
        comments = _comments;
    }

    public Type getType()
    {
        return type;
    }

    public int getTracks()
    {
        return tracks;
    }

    public int getSectors()
    {
        return sectors;
    }

    public int getSides()
    {
        return sides;
    }

    public long getTotalSectors()
    {
        return totalSectors;
    }

    public ImageID getID()
    {
        return id;
    }

    public boolean nontrivialContents(long sector) throws IOException
    {
        byte[] buf = new byte[(type == BaseImage.Type.BIOS) ? 1 : BaseImage.SECTOR_SIZE];
        read(sector, buf, 1);
        for(byte x : buf)
            if(x != 0)
                return true;
        return false;
    }

    public boolean read(long start, byte[] data, long sectors) throws IOException
    {
        boolean nz = false;
        int sectorSize = (type == BaseImage.Type.BIOS) ? 1 : BaseImage.SECTOR_SIZE;
        if(data == null || data.length < sectorSize * sectors)
            throw new IOException("Error: Read request exceeds buffer");
        long currentFilePosition = -1;  //Unknown.
        for(long snum = start; snum < start + sectors;) {
            if(snum >= offsetMap.length || offsetMap[(int)snum] < 0) {
                System.arraycopy(BLANK, 0, data, (int)(sectorSize * (snum - start)), sectorSize);
                snum++;
                continue;
            }
            if(offsetMap[(int)snum] != currentFilePosition)
                backingFile.seek(offsetMap[(int)snum]);
            long maxExtentSize = sectors - snum + start;
            long extentSize = 1;
            while(extentSize < maxExtentSize && (snum + extentSize - 1) < offsetMap.length &&
                offsetMap[(int)(snum + extentSize - 1)] + sectorSize == offsetMap[(int)(snum + extentSize)])
                extentSize++;
            backingFile.readFully(data, (int)(sectorSize * (snum - start)), (int)(sectorSize * extentSize));
            snum += extentSize;
            nz = true;
        }
        return nz;
    }

    protected void finalize()
    {
        try {
            backingFile.close();
        } catch(Exception e) {
        }
    }

    public void dumpStatus(StatusDumper output)
    {
        if(type == BaseImage.Type.HARDDRIVE)
            output.println("\ttype HDD");
        if(type == BaseImage.Type.CDROM)
            output.println("\ttype CDROM");
        if(type == BaseImage.Type.FLOPPY)
            output.println("\ttype FLOPPY");
        if(type == BaseImage.Type.BIOS)
            output.println("\ttype BIOS");
        output.println("\ttracks " + tracks + " sides " + sides + " sectors " + sectors);
        output.println("\tTotalsectors " + totalSectors + " name " + name);
        output.println("\tid <object #" + output.objectNumber(id) + ">"); if(id != null) id.dumpStatus(output);
    }

    public List<String> getComments()
    {
        return comments;
    }

}
