package org.jpc.images;

import java.io.*;

public class StorageMethodExtent implements StorageMethodBase
{
    private boolean firstPresent;

    public StorageMethodExtent(boolean fflag)
    {
        firstPresent = fflag;
    }

    public long saveSize(int[] sectormap, long sectorsUsed) throws Exception
    {
        if((firstPresent ? 1 : 0) != (sectormap[0] & 1))
            throw new Exception("Sector 0 wrong type for method.");

        int extentsSize = 0;
        int extentSize = 0;
        int sectorsInUse = 0;
        int currentType = -1;

        extentSize = 4;
        if(sectorsUsed <= 16777216)
            extentSize = 3;
        if(sectorsUsed <= 65536)
            extentSize = 2;
        if(sectorsUsed <= 256)
            extentSize = 1;

        for(int i = 0; i < sectorsUsed; i++) {
            boolean present = ((sectormap[i / 31] & (1 << (i % 31))) != 0);
            if(present)
                sectorsInUse++;
            if(present && currentType != 1) {
                extentsSize += extentSize;
                currentType = 1;
            }
            if(!present && currentType != 0) {
                extentsSize += extentSize;
                currentType = 0;
            }
        }

        return 512 * sectorsInUse + extentsSize;
    }

    public void save(int[] sectormap, BaseImage rawImage, long sectorsUsed,
        RandomAccessFile output) throws IOException
    {
        if((firstPresent ? 1 : 0) != (sectormap[0] & 1))
            throw new IOException("Sector 0 wrong type for method.");

        int extentSize = 0;
        extentSize = 4;
        if(sectorsUsed <= 16777216)
            extentSize = 3;
        if(sectorsUsed <= 65536)
            extentSize = 2;
        if(sectorsUsed <= 256)
            extentSize = 1;
        byte[] extentBuf = new byte[extentSize];
        long groupExpiry = 0;

        byte[] sector = new byte[512];
        for(int i = 0; i < (int)sectorsUsed; i++) {
            if(i == groupExpiry) {
                //Group Expired. Find the new expiry point.
                long oldExpiry = i;
                boolean firstPresent = ((sectormap[i / 31] & (1 << (i % 31))) != 0);
                groupExpiry = sectorsUsed;
                for(int j = i + 1; j < sectorsUsed; j++) {
                    boolean secondPresent = ((sectormap[j / 31] & (1 << (j % 31))) != 0);
                    if(secondPresent != firstPresent) {
                        groupExpiry = j;
                        break;
                    }
                }
                long extent = groupExpiry - oldExpiry - 1;
                extentBuf[0] = (byte)((extent) & 0xFF);
                if(extentSize > 1)
                    extentBuf[1] = (byte)((extent >>> 8) & 0xFF);
                if(extentSize > 2)
                    extentBuf[2] = (byte)((extent >>> 16) & 0xFF);
                if(extentSize > 3)
                    extentBuf[3] = (byte)((extent >>> 24) & 0xFF);
                output.write(extentBuf);
            }
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
        long[] map = new long[(int)sectorsUsed];
        image.seek(offset);
        boolean present = !firstPresent;
        int extentSize = 0;
        extentSize = 4;
        if(sectorsUsed <= 16777216)
            extentSize = 3;
        if(sectorsUsed <= 65536)
            extentSize = 2;
        if(sectorsUsed <= 256)
            extentSize = 1;
        byte[] extentBuf = new byte[extentSize];
        int flipAt = 0;

        for(int i = 0; i < sectorsUsed; i++) {
            if(i == flipAt) {
               image.seek(offset);
               if(image.read(extentBuf) != extentBuf.length) {
                   throw new IOException("Can't read disk image extent.");
               }
               flipAt = i + 1 + ((int)extentBuf[0] & 0xFF);
               if(extentSize > 1)
                   flipAt += (((int)extentBuf[1] & 0xFF) << 8);
               if(extentSize > 2)
                   flipAt += (((int)extentBuf[2] & 0xFF) << 16);
               if(extentSize > 3)
                   flipAt += (((int)extentBuf[3] & 0xFF) << 24);
               offset += extentSize;
               present = !present;
            }
            if(present) {
                 map[i] = offset;
                 offset += 512;
            } else
                 map[i] = -1;
        }
        _offset[0] = offset;
        return map;
    }
}
