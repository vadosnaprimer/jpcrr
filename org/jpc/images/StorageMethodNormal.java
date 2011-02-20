package org.jpc.images;

import java.io.*;

public class StorageMethodNormal implements StorageMethodBase
{
    public long saveSize(int[] sectormap, long sectorsUsed, int ssize) throws Exception
    {
        return ssize * sectorsUsed;
    }

    public void save(int[] sectormap, BaseImage rawImage, long sectorsUsed,
        RandomAccessFile output, int ssize) throws IOException
    {
        byte[] sector = new byte[ssize];
        for(int i = 0; i < sectorsUsed; i++) {
             rawImage.read(i, sector, 1);
             output.write(sector);
        }
    }

    public long[] loadSectorMap(RandomAccessFile image, long sectorsUsed, long[] _offset, int ssize)
        throws IOException
    {
        long offset = _offset[0];
        long[] map = new long[(int)sectorsUsed];
        for(long i = 0; i < sectorsUsed; i++)
            map[(int)i] = ssize * i + offset;
        _offset[0] += ssize * sectorsUsed;
        return map;
    }

    public long saveSize(int[] sectormap, long sectorsUsed) throws Exception
    {
        return saveSize(sectormap, sectorsUsed, 512);
    }

    public void save(int[] sectormap, BaseImage rawImage, long sectorsUsed,
        RandomAccessFile output) throws IOException
    {
        save(sectormap, rawImage, sectorsUsed, output, 512);
    }

    public long[] loadSectorMap(RandomAccessFile image, long sectorsUsed, long[] _offset)
        throws IOException
    {
        return loadSectorMap(image, sectorsUsed, _offset, 512);
    }

}
