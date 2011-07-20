package org.jpc.images;

import java.io.*;

public class StorageMethod
{
    private static StorageMethodBase[] savers;

    static {
        savers = new StorageMethodBase[] {new StorageMethodNormal(), new StorageMethodSectormap(),
            new StorageMethodExtent(false), new StorageMethodExtent(true)};
    }

    public static int[] scanSectorMap(BaseImage file) throws IOException
    {
        long totalsectors = file.getTotalSectors();
        int[] sectors = new int[(int)((totalsectors + 30) / 31)];

        for(int i = 0; i < totalsectors; i++)
            if(file.nontrivialContents(i))
                sectors[i / 31] |= (1 << ((i) % 31));
        return sectors;
    }


    public static long countSectors(int[] sectormap)
    {
        long used = 0;
        for(int i = 0; i < sectormap.length * 31; i++) {
            if((sectormap[i / 31] & (1 << (i % 31))) != 0)
               used = i + 1;
        }
        return used;
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