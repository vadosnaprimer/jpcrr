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

package org.jpc.mkfs;

import org.jpc.diskimages.*;
import org.jpc.emulator.StatusDumper;
import org.jpc.images.*;
import java.io.*;
import java.util.*;

public class TreeRawDiskImage implements BaseImage
{
    //Volume label.
    String volumeLabel;
    //These all are filled by computeParameters on success.
    TreeFile root;
    ImageMaker.IFormat diskGeometry;
    int partitionStart;                          //Sector partition starts from. (X)
    int primaryFATStart;                         //Sector Primary FAT starts from. (X)
    int secondaryFATStart;                       //Sector Secondary FAT starts from. (X)
    int rootDirectoryStart;                      //Sector root directory starts from. (X)
    int dataAreaStart;                           //Sector data area starts from. (X)
    long sectorsTotal;                            //Total sectors. (X)
    int sectorsPartition;                        //Total partition sectors. (X)
    int fatSize;                                 //Size of FAT in sectors. (X)
    int rootDirectorySize;                       //Size of root directory in sectors. (X)
    int rootDirectoryClusters;                   //Size of root directory in clusters (notional). (X)
    int clusterSize;                             //Sectors In cluster. (X)
    int usableClusters;                          //Usable Clusters. (X)
    int reservedSectors;                         //Reserved sectors. (X)
    int fatType;                                 //0 => FAT16, 1 => FAT12. (X)
    int mbrSector;                               //0 if MBR is present, -1 otherwise. (X)
    int superBlockSector;                        //FAT superblock sector. (X)
    //Filled when tree is layouted.
    int firstUnusedCluster;                      //First free cluster.
    int sectorLimit;                             //Last used sector + 1.
    int[] fat;                                   //Actual FAT.
    HashMap<Integer, TreeFile> clusterToFile;    //File that's stored in each cluster.
    TreeFile lastCached;                         //Last cached file.
    BaseImage.Type dType;

    private static final int MAX_FAT16_CLUSTERS = 65518;
    private static final int MAX_FAT12_CLUSTERS = 4078;

    public ImageID getID()
    {
        return null;
    }

    private void computeParameters(ImageMaker.IFormat geometry, TreeFile rootDirectory, int sectorsInCluster, int type)
        throws Exception
    {
        clusterSize = sectorsInCluster;
        fatType = type;
        root = rootDirectory;
        diskGeometry = geometry;

        //The root directory size is at least 32 sectors (511+1 entries) and multiple of cluster.
        rootDirectorySize = root.getSizeInSectors();
        if(rootDirectorySize < 32)
            rootDirectorySize = 32;
        rootDirectorySize = ((rootDirectorySize + clusterSize - 1) / clusterSize) * clusterSize;

        if(geometry.typeCode == 1) {
            //Reserve Track 0 for HDD partition data.
            superBlockSector = partitionStart = geometry.sides * geometry.sectors;
            sectorsTotal = geometry.sides * geometry.sectors * geometry.tracks;
            sectorsPartition = (int)sectorsTotal - geometry.sides * geometry.sectors;
            mbrSector = 0;
        } else {
            //Raw.
            superBlockSector = partitionStart = 0;
            sectorsTotal = sectorsPartition = geometry.sides * geometry.sectors * geometry.tracks;
            mbrSector = -1;
        }

        reservedSectors = sectorsPartition - rootDirectorySize - 1;
        if(reservedSectors < 2 + clusterSize)
            throw new Exception("Specified parameters for FAT are impossible to satisfy.");

        if(type == 0) {
            //FAT16. The equation of cluster count and reserved sectors is.
            //1 + reservedSectors + 2 * ceil((usableClusters + 2) / 256) + rootsectors + usableclusters * clustersize =
            //sectorsPartition. This gives allocation group of 256 clusters, taking 2 + 256 * clusterSize sectors.
            if(reservedSectors > 2 + 254 * clusterSize) {
                usableClusters = 254;
                reservedSectors -= (2 + 254 * clusterSize);
                usableClusters += (256 * reservedSectors / (2 + 256 * clusterSize));
                reservedSectors = reservedSectors % (2 + 256 * clusterSize);
                //If we have more space for clusters, assign that space.
                if(reservedSectors >= 2 + clusterSize) {
                    usableClusters += (reservedSectors - 2) / clusterSize;
                    reservedSectors = (reservedSectors - 2) % clusterSize;
                }
            } else {
                usableClusters = (reservedSectors - 2) / clusterSize;
                reservedSectors = (reservedSectors - 2) % clusterSize;
            }

            if(usableClusters > MAX_FAT16_CLUSTERS)
                throw new Exception("Specified parameters for FAT are impossible to satisfy.");

            fatSize = (usableClusters + 257) / 256;
        } else if(type == 1) {
            //FAT12. The equation of cluster count and reserved sectors is.
            //1 + reserved + 2 * ceil(3 * (usableClusters + 2) / 1024) + rootsectors + usableclusters * clustersize =
            //sectorsPartition. This gives allocation group of 1024 clusters, taking 6 + 1024 * clusterSize sectors.
            if(reservedSectors > 6 + 1022 * clusterSize) {
                usableClusters = 1022;
                reservedSectors -= (6 + 1022 * clusterSize);

                usableClusters += 1024 * (reservedSectors / (6 + 1024 * clusterSize));
                reservedSectors = reservedSectors % (6 + 1024 * clusterSize);
                //If we have more space for clusters, assign that space.
                int extraClusters = reservedSectors / clusterSize;
                while(extraClusters * clusterSize + 2 * ((3 * extraClusters + 1023) / 1024) > reservedSectors)
                    extraClusters--;
                usableClusters += extraClusters;
                reservedSectors -= (extraClusters * clusterSize + 2 * ((3 * extraClusters + 1023) / 1024));
            } else {
                int extraClusters = reservedSectors / clusterSize;
                while(extraClusters * clusterSize + 2 * ((3 * (extraClusters + 2) + 1023) / 1024) > reservedSectors)
                    extraClusters--;
                usableClusters += extraClusters;
                reservedSectors -= (extraClusters * clusterSize + 2 * ((3 * extraClusters + 1023) / 1024));
            }

            if(usableClusters > MAX_FAT12_CLUSTERS)
                throw new Exception("Specified parameters for FAT are impossible to satisfy.");

            fatSize = (3 * (usableClusters + 2) + 1023) / 1024;
        } else
            throw new Exception("Invalid FAT type code " + type + ".");

        //Fill the layout fields.
        rootDirectoryClusters = rootDirectorySize / clusterSize;
        primaryFATStart = partitionStart + 1 + reservedSectors;
        secondaryFATStart = primaryFATStart + fatSize;
        rootDirectoryStart = secondaryFATStart + fatSize;
        dataAreaStart = rootDirectoryStart + rootDirectorySize;
    }

    public TreeRawDiskImage(TreeFile files, ImageMaker.IFormat format, String label, BaseImage.Type _type)
        throws IOException
    {
        int type;
        int sectors = format.tracks * format.sides * format.sectors;
        dType = _type;

        volumeLabel = label;
        if(format.typeCode != 0 && format.typeCode != 1)
            throw new IOException("Unsupported image type. Only floppies and HDDs are supported.");
        else if(format.typeCode == 1)
            sectors -= format.sides * format.sectors;    //Reserve Cylinder 0.

        //Figure out FAT type.
        if(sectors < 65536) {
            type = 1;
        } else {
            type = 0;
        }

        //Figure out the sectors per cluster.
        boolean ok = false;
        int sectorsPerCluster = 1;
        while(!ok) {
            try {
                computeParameters(format, files, sectorsPerCluster, type);
                ok = true;
            } catch(Exception e) {
                sectorsPerCluster = 2 * sectorsPerCluster;
            }
        }

        root.setClusterSize(clusterSize);
        firstUnusedCluster = root.assignCluster(2 - rootDirectoryClusters);
        sectorLimit = dataAreaStart + (firstUnusedCluster - 2) * clusterSize;

        if(sectorLimit > sectorsTotal)
            throw new IOException("Too much data to fit into given space.");

        fat = new int[firstUnusedCluster + 350];      //350 entries is enough to fill a sector...
        TreeFile iterator = root.nextFile();              //Skip Root.
        clusterToFile = new HashMap<Integer, TreeFile>();

        while(iterator != null) {
            int start = iterator.getStartCluster();
            int end = iterator.getEndCluster();
            for(int i = start; i <= end; i++) {
                if(i == end)
                    fat[i] = 0xFFFF;
                else
                    fat[i] = i + 1;
                clusterToFile.put(new Integer(i), iterator);
            }
            iterator = iterator.nextFile();
        }
    }

    public long getTotalSectors()
    {
        return sectorsTotal;
    }

    public int getSides()
    {
        return diskGeometry.sides;
    }

    public int getTracks()
    {
        return diskGeometry.tracks;
    }

    public int getSectors()
    {
        return diskGeometry.sectors;
    }

    private void writeWord(byte[] buffer, int offset, int value)
    {
        buffer[offset] = (byte)(value & 0xFF);
        buffer[offset + 1] = (byte)((value >>> 8) & 0xFF);
    }

    private void writeDWord(byte[] buffer, int offset, int value)
    {
        buffer[offset] = (byte)(value & 0xFF);
        buffer[offset + 1] = (byte)((value >>> 8) & 0xFF);
        buffer[offset + 2] = (byte)((value >>> 16) & 0xFF);
        buffer[offset + 3] = (byte)((value >>> 24) & 0xFF);
    }

    private void writeGeometry(byte[] buffer, int offset, int cylinder, int head, int sector)
    {
        buffer[offset] = (byte)(head & 0xFF);
        buffer[offset + 1] = (byte)(sector + ((cylinder & 0x300) >>> 2));
        buffer[offset + 2] = (byte)(cylinder & 0xFF);
    }

    public boolean read(long sector, byte[] buffer, long sectors) throws IOException
    {
        byte[] buf = new byte[512];
        boolean nz = false;
        for(int i = 0; i < sectors; i++) {
            nz |= readSector((int)(sector + i), buf);
            System.arraycopy(buf, 0, buffer, 512 * i, 512);
        }
        return nz;
    }

    private boolean readSector(int sector, byte[] buffer) throws IOException
    {
        byte[] zeroes = new byte[512];
        System.arraycopy(zeroes, 0, buffer, 0, 512);
        if(!nontrivialContents(sector)) {
            return false;
        }
        if(sector == mbrSector) {
            //MASTER BOOT RECORD.
            writeWord(buffer, 0, 0xFEEB);                        //Since there isn't valid boot code, hang the computer.
            buffer[446] = (byte)0x80;                            //Active.
            writeGeometry(buffer, 447, 1, 0, 1);                 //Start from Head 0, cylinder 1, sector 0.
            if(fatType == 1)
                buffer[450] = (byte)1;                           //FAT12.
            else if(sectorsPartition >= 65536)
                buffer[450] = (byte)6;                           //FAT16 large.
            else
                buffer[450] = (byte)2;                           //FAT16 small.
            //End at last sector.
            writeGeometry(buffer, 451, diskGeometry.tracks - 1, diskGeometry.sides - 1, diskGeometry.sectors);
            writeDWord(buffer, 454, partitionStart);             //Space between MBR and partition start.
            writeDWord(buffer, 458, sectorsPartition);           //Partition size.
            writeWord(buffer, 510, 0xAA55);                      //Valid MBR marker.
            return true;
        } else if(sector == superBlockSector) {
            //FAT superblock.
            writeWord(buffer, 0, 0x3CEB);                        //Jump to boot block.
            buffer[2] = (byte)0x90;
            writeDWord(buffer, 3, 0x5243504A);                  //OEM.
            writeDWord(buffer, 7, 0x444B4D52);                  //OEM.
            writeWord(buffer, 11, 512);                         //512 bytes per sector.
            writeWord(buffer, 13, clusterSize);
            buffer[13] = (byte)clusterSize;
            writeWord(buffer, 14, reservedSectors + 1);
            buffer[16] = (byte)2;                                //2 FAT copies.
            writeWord(buffer, 17, rootDirectorySize * 16);       //Root directory entries.
            if(sectorsPartition < 65536)
                writeWord(buffer, 19, sectorsPartition);         //Partition sectors.
            else
                writeWord(buffer, 19, 0);
            if(diskGeometry.typeCode == 1)
                buffer[21] = (byte)0xF8;                         //Hard Disk.
            else
                buffer[21] = (byte)0xF0;                         //Floppy Disk.
            writeWord(buffer, 22, fatSize);                      //FAT size.
            writeWord(buffer, 24, diskGeometry.sectors);
            writeWord(buffer, 26, diskGeometry.sides);
            writeDWord(buffer, 28, 0);                           //1 hidden sector (boot sector)
            writeDWord(buffer, 32, sectorsPartition);            //Partition sectors.
            writeWord(buffer, 36, 128);                          //Logical number 128.
            buffer[38] = (byte)0x29;                             //Extension signature.
            writeDWord(buffer, 39, 0xDEADBEEF);                  //Serial number.
            if(volumeLabel == null) {
                writeDWord(buffer, 43, 0);                       //Blank disk Label.
                writeDWord(buffer, 47, 0);
                writeWord(buffer, 51, 0);
                buffer[53] = (byte)0;
            } else {
                TreeDirectoryFile.writeEntryName(buffer, volumeLabel, 43, true);
            }
            writeDWord(buffer, 54, 0x31544146);                  //FAT name.
            if(fatType == 0)
                writeDWord(buffer, 58, 0x20202036);
            else
                writeDWord(buffer, 58, 0x20202032);
            writeWord(buffer, 62, 0xFEEB);                        //Since there isn't valid boot code, hang the computer.
            writeWord(buffer, 510, 0xAA55);                       //Valid Boot sector marker.
        } else if(sector < rootDirectoryStart) {
            int fatSector = (sector - primaryFATStart) % fatSize;
            if(fatType == 0) {
                int firstCluster = 256 * fatSector;
                for(int i = 0; i < 256; i++)
                    writeWord(buffer, 2 * i, fat[firstCluster + i]);
            } else {
                //Whee, 512 bytes is not integral multiple of 12 bits, so this is going to be whacky.
                int firstNibble = 1024 * fatSector;
                for(int i = 0; i < 512; i++) {
                    int nibbleBase = firstNibble + 2 * i;
                    byte nibble1 = (byte)((fat[nibbleBase / 3] >> (4 * (nibbleBase % 3))) & 0xF);
                    byte nibble2 = (byte)((fat[(nibbleBase + 1) / 3] >> (4 * ((nibbleBase + 1) % 3))) & 0xF);
                    buffer[i] = (byte)((nibble2 << 4) | nibble1);
                }
            }
        } else if(sector < dataAreaStart) {
            root.readSector(sector - rootDirectoryStart, buffer);
        } else {
            //Data area. Find the cluster sector belongs to.
            int clusterNum = 2 + (sector - dataAreaStart) / clusterSize;
            TreeFile currentFile = clusterToFile.get(new Integer(clusterNum));
            if(currentFile != lastCached && lastCached != null)
                lastCached.readSectorEnd();
            lastCached = currentFile;
            if(currentFile != null) {
                int baseSector = dataAreaStart + (currentFile.getStartCluster() - 2) * clusterSize;
                currentFile.readSector(sector - baseSector, buffer);
            }
        }
        return true;
    }

    public boolean nontrivialContents(long _sector) throws IOException
    {
        //Not exactly right, but close enough.
        int sector = (int)_sector;
        if(sector == mbrSector || sector == superBlockSector)
            return true;
        else if(sector < primaryFATStart)
            return false;
        else if(sector < rootDirectoryStart) {
            int fatSector = (sector - primaryFATStart) % fatSize;
            int firstCluster;
            if(fatType == 0)
                firstCluster = 2 + 256 * fatSector;
            else
                firstCluster = 2 + 1024 * fatSector / 3;
            if(firstCluster >= firstUnusedCluster + 2)
                return false;
            else
                return true;
        } else if(sector < sectorLimit)
            return true;
        else
            return false;
    }

    public List<String> getComments()
    {
        List<String> comments = root.getComments("", null);
        return comments;
    }

    public BaseImage.Type getType()
    {
        return dType;
    }

    public void dumpStatus(StatusDumper x)
    {
        //This should never be called.
    }
}
