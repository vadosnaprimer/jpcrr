package org.jpc.images;

import java.io.*;

public class StorageMethodSectormap implements StorageMethodBase
{
    public long saveSize(int[] sectormap, long sectorsUsed) throws Exception
    {
        long sectorMapSize = (sectorsUsed + 7) / 8;
        long sectorsInUse = 0;
        for(int i = 0; i < (int)sectorsUsed; i++)
            if((sectormap[i / 31] & (1 << (i % 31))) != 0)
                sectorsInUse++;

        return 512 * sectorsInUse + sectorMapSize;
    }

    public void save(int[] sectormap, BaseImage rawImage, long sectorsUsed,
        RandomAccessFile output) throws IOException
    {
        byte[] savedSectorMap = new byte[(int)((sectorsUsed + 7) / 8)];
        for(int i = 0; i < (int)sectorsUsed; i++)
            if((sectormap[i / 31] & (1 << (i % 31))) != 0)
                savedSectorMap[i / 8] |= (byte)(1 << (i % 8));
        output.write(savedSectorMap);

        byte[] sector = new byte[512];
        for(int i = 0; i < sectorsUsed; i++) {
            if((sectormap[i / 31] & (1 << (i % 31))) != 0) {
                rawImage.read(i, sector, 1);
                output.write(sector);
            }
        }
    }

    public long[] loadSectorMap(RandomAccessFile image, long sectorsUsed, long[] _offset)
        throws IOException
    {
        long offset = _offset[0];
        byte[] savedSectorMap = new byte[(int)((sectorsUsed + 7) / 8)];
        image.seek(offset);
        if(image.read(savedSectorMap) != savedSectorMap.length) {
            throw new IOException("Can't read disk image sector map.");
        }
        offset += savedSectorMap.length;
        long[] map = new long[(int)sectorsUsed];
        for(int i = 0; i < sectorsUsed; i++)
            if((savedSectorMap[i / 8] & (1 << (i % 8))) != 0) {
                map[i] = offset;
                offset += 512;
            } else
                 map[i] = -1;
        _offset[0] = offset;
        return map;
    }
}
