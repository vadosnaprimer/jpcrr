package org.jpc.images;

import org.jpc.mkfs.RawDiskImage;
import java.io.*;

public interface StorageMethodBase
{
    public long saveSize(int[] sectormap, long usedSectors) throws Exception;
    public void save(int[] sectormap, RawDiskImage rawImage, long usedSectors,
        RandomAccessFile output) throws IOException;
    public long[] loadSectorMap(RandomAccessFile image, long sectorsUsed, long[] offset)
        throws IOException;
};
