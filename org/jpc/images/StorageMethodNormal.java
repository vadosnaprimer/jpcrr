package org.jpc.images;

import org.jpc.mkfs.RawDiskImage;
import java.io.*;

public class StorageMethodNormal implements StorageMethodBase
{
    public long saveSize(int[] sectormap, long sectorsUsed) throws Exception
    {
        return 512 * sectorsUsed;
    }

    public void save(int[] sectormap, RawDiskImage rawImage, long sectorsUsed,
        RandomAccessFile output) throws IOException
    {
        byte[] sector = new byte[512];
        for(int i = 0; i < sectorsUsed; i++) {
             rawImage.readSector(i, sector);
             output.write(sector);
        }
    }

    public long[] loadSectorMap(RandomAccessFile image, long sectorsUsed, long[] _offset)
        throws IOException
    {
        long offset = _offset[0];
        long[] map = new long[(int)sectorsUsed];
        for(long i = 0; i < sectorsUsed; i++)
            map[(int)i] = 512 * i + offset;
        _offset[0] += 512 * sectorsUsed;
        return map;
    }
}
