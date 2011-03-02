package org.jpc.images;

import java.io.*;
import java.util.*;
import org.jpc.emulator.StatusDumper;

class UncompressedLocalFileImage extends AbstractBaseImage
{
    public static final long SECTOR_NOT_PRESENT = -1;
    private long[] offsetMap;
    private RandomAccessFile backingFile;
    private static final byte[] BLANK;

    static
    {
        BLANK = new byte[BaseImage.SECTOR_SIZE];
    }

    public UncompressedLocalFileImage(BaseImage.Type _type, int _tracks, int _sides, int _sectors,
        int _totalSectors, long[] _offsetMap, RandomAccessFile _backingFile, ImageID _id, String _name,
        List<String> _comments)
    {
        super(_type, _tracks, _sides, _sectors, _totalSectors, _id, _name, _comments);
        offsetMap = _offsetMap;
        backingFile = _backingFile;
    }

    public boolean nontrivialContents(int sector) throws IOException
    {
        if(type == BaseImage.Type.BIOS)
            return (sector < totalSectors);
        byte[] buf = new byte[BaseImage.SECTOR_SIZE];
        read(sector, buf, 1);
        for(byte x : buf)
            if(x != 0)
                return true;
        return false;
    }

    public boolean read(int start, byte[] data, int sectors) throws IOException
    {
        boolean nz = false;
        int sectorSize = (type == BaseImage.Type.BIOS) ? 1 : BaseImage.SECTOR_SIZE;
        if(data == null || data.length < sectorSize * sectors)
            throw new IOException("Error: Read request exceeds buffer");
        long currentFilePosition = -1;  //Unknown.
        for(int snum = start; snum < start + sectors;) {
            if(snum >= offsetMap.length || offsetMap[snum] < 0) {
                System.arraycopy(BLANK, 0, data, sectorSize * (snum - start), sectorSize);
                snum++;
                continue;
            }
            if(offsetMap[snum] != currentFilePosition)
                backingFile.seek(offsetMap[snum]);
            int maxExtentSize = sectors - snum + start;
            int extentSize = 1;
            while(extentSize < maxExtentSize && (snum + extentSize - 1) < offsetMap.length &&
                offsetMap[snum + extentSize - 1] + sectorSize == offsetMap[snum + extentSize])
                extentSize++;
            backingFile.readFully(data, sectorSize * (snum - start), sectorSize * extentSize);
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

    void dumpStatusPartial(StatusDumper output)
    {
        super.dumpStatusPartial(output);
    }

    public void dumpStatus(StatusDumper output)
    {
        if(output.dumped(this))
            return;

        output.println("#" + output.objectNumber(this) + ": UncompressedLocalFileImage:");
        dumpStatusPartial(output);
        output.endObject();
    }
}
