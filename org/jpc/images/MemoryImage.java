package org.jpc.images;

import java.io.*;
import java.util.*;
import org.jpc.emulator.StatusDumper;

class MemoryImage extends AbstractBaseImage
{
    private byte[] backingData;
    private static final byte[] BLANK;

    static
    {
        BLANK = new byte[BaseImage.SECTOR_SIZE];
    }

    public MemoryImage(BaseImage.Type _type, int _tracks, int _sides, int _sectors,
        long _totalSectors, byte[] _data, ImageID _id, String _name,
        List<String> _comments)
    {
        super(_type, _tracks, _sides, _sectors, _totalSectors, _id, _name, _comments);
        backingData = _data;
    }

    public boolean nontrivialContents(long sector) throws IOException
    {
        if(type == BaseImage.Type.BIOS)
            return (sector < backingData.length);
        if(BaseImage.SECTOR_SIZE * sector >= backingData.length)
            return false;
        for(int i = 0; i < 512; i++)
            if(backingData[BaseImage.SECTOR_SIZE * (int)sector + i] != 0)
                return true;
        return false;
    }

    public boolean read(long start, byte[] data, long sectors) throws IOException
    {
        boolean nz = false;
        int sectorSize = (type == BaseImage.Type.BIOS) ? 1 : BaseImage.SECTOR_SIZE;
        if(data == null || data.length < sectorSize * sectors)
            throw new IOException("Error: Read request exceeds buffer");

        //Copy what we have and pad with zeroes.
        int psects = Math.max(backingData.length / sectorSize - (int)start, (int)sectors);
        System.arraycopy(backingData, sectorSize * (int)start, data, 0, sectorSize * psects);
        Arrays.fill(data, sectorSize * psects, (int)sectors * sectorSize, (byte)0);
        return (psects > 0);
    }

    void dumpStatusPartial(StatusDumper output)
    {
        super.dumpStatusPartial(output);
    }

    public void dumpStatus(StatusDumper output)
    {
        if(output.dumped(this))
            return;

        output.println("#" + output.objectNumber(this) + ": MemoryImage:");
        dumpStatusPartial(output);
        output.endObject();
    }
}
