/*
    JPC-RR: A x86 PC Hardware Emulator
    Release 1

    Copyright (C) 2007-2009 Isis Innovation Limited
    Copyright (C) 2009 H. Ilari Liusvaara

    This program is free software; you can redistribute it and/or modify
    it under the terms of the GNU General Public License version 2 as published by
    the Free Software Foundation.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License along
    with this program; if not, write to the Free Software Foundation, Inc.,
    51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.

    Based on JPC x86 PC Hardware emulator,
    A project from the Physics Dept, The University of Oxford

    Details about original JPC can be found at:

    www-jpc.physics.ox.ac.uk

*/

package org.jpc.diskimages;

import java.io.*;

public class ImageFormats
{
    static abstract class DiskImageType
    {
        public abstract int saveSize(int code, int[] sectormap, int totalSectors, int usedSectors) throws Exception;
        public abstract void save(int code, int[] sectormap, RawDiskImage rawImage, int totalSectors, int usedSectors,
            RandomAccessFile output) throws IOException;
        public abstract int[] loadSectorMap(RandomAccessFile image, int type, int sectorsUsed, int[] offset) throws
            IOException;
    }

    static class NormalDiskImage extends DiskImageType
    {
        public int saveSize(int code, int[] sectormap, int totalSectors, int sectorsUsed) throws Exception
        {
            return 512 * sectorsUsed;
        }

        public void save(int code, int[] sectormap, RawDiskImage rawImage, int totalSectors, int sectorsUsed,
            RandomAccessFile output) throws IOException
        {
            byte[] sector = new byte[512];
            for(int i = 0; i < sectorsUsed; i++) {
                 rawImage.readSector(i, sector);
                 output.write(sector);
            }
        }

        public int[] loadSectorMap(RandomAccessFile image, int type, int sectorsUsed, int[] _offset)
            throws IOException
        {
            int offset = _offset[0];
            int[] map = new int[sectorsUsed];
            for(int i = 0; i < sectorsUsed; i++)
                map[i] = 512 * i + offset;
            _offset[0] += 512 * sectorsUsed;
            return map;
        }
    }

    static class SectorMapDiskImage extends DiskImageType
    {
        public int saveSize(int code, int[] sectormap, int totalSectors, int sectorsUsed) throws Exception
        {
            int sectorMapSize = (sectorsUsed + 7) / 8;
            int sectorsInUse = 0;
            for(int i = 0; i < sectorsUsed; i++)
                if((sectormap[i / 31] & (1 << (i % 31))) != 0)
                    sectorsInUse++;

            return 512 * sectorsInUse + sectorMapSize;
        }

        public void save(int code, int[] sectormap, RawDiskImage rawImage, int totalSectors, int sectorsUsed,
            RandomAccessFile output) throws IOException
        {
            byte[] savedSectorMap = new byte[(sectorsUsed + 7) / 8];
            for(int i = 0; i < sectorsUsed; i++)
                if((sectormap[i / 31] & (1 << (i % 31))) != 0)
                    savedSectorMap[i / 8] |= (byte)(1 << (i % 8));
            output.write(savedSectorMap);

            byte[] sector = new byte[512];
            for(int i = 0; i < sectorsUsed; i++) {
                if((sectormap[i / 31] & (1 << (i % 31))) != 0) {
                    rawImage.readSector(i, sector);
                    output.write(sector);
                }
            }
        }

        public int[] loadSectorMap(RandomAccessFile image, int type, int sectorsUsed, int[] _offset)
            throws IOException
        {
            int offset = _offset[0];
            byte[] savedSectorMap = new byte[(sectorsUsed + 7) / 8];
            image.seek(offset);
            if(image.read(savedSectorMap) != savedSectorMap.length) {
                throw new IOException("Can't read disk image sector map.");
            }
            offset += savedSectorMap.length;
            int[] map = new int[sectorsUsed];
            for(int i = 0; i < sectorsUsed; i++)
                if((savedSectorMap[i / 8] & (1 << (i % 8))) != 0) {
                    map[i] = offset;
                    offset += 512;
                }
            _offset[0] = offset;
            return map;
        }
    }

    static class ExtentDiskImage extends DiskImageType
    {
        public int saveSize(int code, int[] sectormap, int totalSectors, int sectorsUsed) throws Exception
        {
            if((code & 1) != (sectormap[0] & 1))
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

        public void save(int code, int[] sectormap, RawDiskImage rawImage, int totalSectors, int sectorsUsed,
            RandomAccessFile output) throws IOException
        {
            if((code & 1) != (sectormap[0] & 1))
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
            int groupExpiry = 0;

            byte[] sector = new byte[512];
            for(int i = 0; i < sectorsUsed; i++) {
                if(i == groupExpiry) {
                    //Group Expired. Find the new expiry point.
                    int oldExpiry = i;
                    boolean firstPresent = ((sectormap[i / 31] & (1 << (i % 31))) != 0);
                    groupExpiry = sectorsUsed;
                    for(int j = i + 1; j < sectorsUsed; j++) {
                        boolean secondPresent = ((sectormap[j / 31] & (1 << (j % 31))) != 0);
                        if(secondPresent != firstPresent) {
                            groupExpiry = j;
                            break;
                        }
                    }
                    int extent = groupExpiry - oldExpiry - 1;
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
                    rawImage.readSector(i, sector);
                    output.write(sector);
                }
            }
        }

        public int[] loadSectorMap(RandomAccessFile image, int type, int sectorsUsed, int[] _offset)
            throws IOException
        {
            int offset = _offset[0];
            int[] map = new int[sectorsUsed];
            image.seek(offset);
            boolean present = !((type & 1) != 0);
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
                }
            }
            _offset[0] = offset;
            return map;
        }
    }

    static DiskImageType[] savers;

    static {
        savers = new DiskImageType[] {new NormalDiskImage(), new SectorMapDiskImage(), new ExtentDiskImage(),
            new ExtentDiskImage()};
    }
}
