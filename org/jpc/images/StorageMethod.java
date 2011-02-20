package org.jpc.images;

import java.io.*;

public class StorageMethod
{
    private static StorageMethodBase[] savers;

    static {
        savers = new StorageMethodBase[] {new StorageMethodNormal(), new StorageMethodSectormap(),
            new StorageMethodExtent(false), new StorageMethodExtent(true)};
    }

    public static int findBestIndex(int[] sectormap, long usedSectors) throws IOException
    {
        StorageMethodBase best = null;
        int bestIndex = 0;
        long score = 0x7FFFFFFFFFFFFFFFL;
        for(int i = 0; i < savers.length; i++) {
            try {
                long scored = savers[i].saveSize(sectormap, usedSectors);
                if(score > scored) {
                    best = savers[i];
                    score = scored;
                    bestIndex = i;
                }
            } catch(Exception e) {
                //That method can't save it.
            }
        }
        if(best == null)
            throw new IOException("No known format can save that");
        return bestIndex;
    }

    public static void save(int index, int[] sectormap, BaseImage rawImage, long usedSectors,
        RandomAccessFile output) throws IOException
    {
        savers[index].save(sectormap, rawImage, usedSectors, output);
    }

    public static long[] load(int index, RandomAccessFile image, long sectorsUsed, long[] offset)
        throws IOException
    {
        return savers[index].loadSectorMap(image, sectorsUsed, offset);
    }

    public static void saveNormal(BaseImage rawImage, long usedSectors, RandomAccessFile output, int ssize)
        throws IOException
    {
        ((StorageMethodNormal)savers[0]).save(null, rawImage, usedSectors, output, ssize);
    }

    public static long[] loadNormal(RandomAccessFile image, long sectorsUsed, long[] offset, int ssize)
        throws IOException
    {
        return ((StorageMethodNormal)savers[0]).loadSectorMap(image, sectorsUsed, offset, ssize);
    }
}
