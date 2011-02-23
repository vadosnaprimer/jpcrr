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

import org.jpc.emulator.StatusDumper;
import org.jpc.images.*;
import java.io.*;
import java.util.*;

public class TreeRawDiskImage implements BaseImage
{
    //Volume label.
    String volumeLabel;
    BaseImage.Type type;
    int sides;
    int tracks;
    int sectors;
    //These all are filled by computeParameters on success.
    TreeFile root;
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
    ImageID id;

    private static final int MAX_FAT16_CLUSTERS = 65518;
    private static final int MAX_FAT12_CLUSTERS = 4078;

    public ImageID getID() throws IOException
    {
        if(id == null)
            id = DiskIDAlgorithm.computeIDForDisk(this);
        return id;
    }

    private int computeParametersFAT(int sectorsAvailable, int rootSectors, int maxFATSectors, int maxClusters,
        int majorBlock, int majorBlockFATSectors, int intermediateBlockSize, int maxIntermediateFATSects)
        throws Exception
    {
        if(sectorsAvailable < 3)
            throw new Exception("Minimum size for FAT/data area is 3 sectors.");
        //Compute cluster size. Maximum size of two FATs is 24 sectors.
        clusterSize = 1;
        while(sectorsAvailable > maxFATSectors + maxClusters * clusterSize +
            (clusterSize - rootSectors % clusterSize) % clusterSize)
            clusterSize *= 2;
        if(clusterSize > 128)
            throw new Exception("Filesystem too large for FAT type");

        //Initially all space is reserved. We'll take the cluster space from reserved space.
        usableClusters = 0;
        reservedSectors = sectorsAvailable;

        //Reserve space for root directory extension.
        reservedSectors -= (clusterSize - rootSectors % clusterSize) % clusterSize;

        //Take majorBlockFATSectors + (majorBlock - 2) * clusterSize sectors and give majorBlock - 2 clusters (first
        //block).
        if(reservedSectors > majorBlockFATSectors + (majorBlock - 2) * clusterSize) {
            usableClusters += (majorBlock - 2);
            reservedSectors -= (majorBlockFATSectors + (majorBlock - 2) * clusterSize);
        }
        //Take as many times majorBlockFATSectors + majorBlock * clusterSize sectors and give majorBlock
        //clusters (whole blocks).
        int majors = reservedSectors / (majorBlockFATSectors + majorBlock * clusterSize);
        usableClusters += majorBlock * majors;
        reservedSectors -= majorBlock * majors;

        //The last block is little whacky.
        int pMajor = 2 + intermediateBlockSize * clusterSize;
        int fatSects = 2 * (reservedSectors / pMajor + 1);
        if(fatSects > maxIntermediateFATSects)
            fatSects = maxIntermediateFATSects;
        usableClusters += (reservedSectors - fatSects) % clusterSize;
        reservedSectors = (reservedSectors - fatSects) % clusterSize;

        fatSize = (maxIntermediateFATSects * (usableClusters + 2) / majorBlock) / 2 + fatSects / 2;

        //Root directory is extended by this amount.
        return (clusterSize - rootSectors % clusterSize) % clusterSize;
    }

    private int computeParametersFAT16(int sectorsAvailable, int rootSectors) throws Exception
    {
        return computeParametersFAT(sectorsAvailable, rootSectors, 512, MAX_FAT16_CLUSTERS, 256, 2, 256, 2);
    }

    private int computeParametersFAT12(int sectorsAvailable, int rootSectors) throws Exception
    {
        return computeParametersFAT(sectorsAvailable, rootSectors, 24, MAX_FAT12_CLUSTERS, 1024, 6, 341, 6);
    }


    private void computeParameters(TreeFile rootDirectory, int _fatType) throws Exception
    {
        fatType = _fatType;
        root = rootDirectory;

        //The root directory size is at least 32 sectors (511+1 entries) and multiple of cluster.
        rootDirectorySize = root.getSizeInSectors();
        if(rootDirectorySize < 32)
            rootDirectorySize = 32;

        if(type == BaseImage.Type.HARDDRIVE) {
            //Reserve Track 0 for HDD partition data.
            superBlockSector = partitionStart = sides * sectors;
            sectorsTotal = sides * sectors * tracks;
            sectorsPartition = (int)sectorsTotal - sides * sectors;
            mbrSector = 0;
        } else {
            //Raw.
            superBlockSector = partitionStart = 0;
            sectorsTotal = sectorsPartition = sides * sectors * tracks;
            mbrSector = -1;
        }

        reservedSectors = sectorsPartition - rootDirectorySize - 1;

        if(fatType == 0) {
            rootDirectorySize += computeParametersFAT16(reservedSectors, rootDirectorySize);
        } else if(fatType == 1) {
            rootDirectorySize += computeParametersFAT12(reservedSectors, rootDirectorySize);
        } else
            throw new Exception("Invalid FAT type code " + type + ".");

        //Fill the layout fields.
        rootDirectoryClusters = rootDirectorySize / clusterSize;
        primaryFATStart = partitionStart + 1 + reservedSectors;
        secondaryFATStart = primaryFATStart + fatSize;
        rootDirectoryStart = secondaryFATStart + fatSize;
        dataAreaStart = rootDirectoryStart + rootDirectorySize;
    }

    private boolean nextGeometry(BaseImage.Type _type, int[] geom)
    {
        if(_type == BaseImage.Type.HARDDRIVE && geom[0] == 0 && geom[1] == 0 && geom[2] == 0) {
            geom[0] = 16;
            geom[1] = 16;
            geom[2] = 63;
            return true;
        }
        if(_type == BaseImage.Type.HARDDRIVE && geom[0] == 16 && geom[1] == 16 && geom[2] == 63) {
            geom[1] = 32;
            return true;
        }
        if(_type == BaseImage.Type.HARDDRIVE && geom[0] == 16 && geom[1] == 32 && geom[2] == 63) {
            geom[1] = 64;
            return true;
        }
        if(_type == BaseImage.Type.HARDDRIVE && geom[0] == 16 && geom[1] == 64 && geom[2] == 63) {
            geom[1] = 128;
            return true;
        }
        if(_type == BaseImage.Type.HARDDRIVE && geom[0] == 16 && geom[1] == 128 && geom[2] == 63) {
            geom[1] = 256;
            return true;
        }
        if(_type == BaseImage.Type.HARDDRIVE && geom[0] == 16 && geom[1] == 256 && geom[2] == 63) {
            geom[1] = 512;
            return true;
        }
        if(_type == BaseImage.Type.HARDDRIVE && geom[0] == 16 && geom[1] == 512 && geom[2] == 63) {
            geom[1] = 1023;
            return true;
        }
        if(_type == BaseImage.Type.FLOPPY && geom[0] == 0 && geom[1] == 0 && geom[2] == 0) {
            geom[0] = 2;
            geom[1] = 80;
            geom[2] = 18;
            return true;
        }
        if(_type == BaseImage.Type.FLOPPY && geom[0] == 2 && geom[1] == 80 && geom[2] == 18) {
            geom[2] = 36;
            return true;
        }
        if(_type == BaseImage.Type.FLOPPY && geom[0] == 2 && geom[1] == 80 && geom[2] == 36) {
            geom[2] = 63;
            return true;
        }
        if(_type == BaseImage.Type.FLOPPY && geom[0] == 2 && geom[1] == 80 && geom[2] == 63) {
            geom[1] = 128;
            return true;
        }
        if(_type == BaseImage.Type.FLOPPY && geom[0] == 2 && geom[1] == 128 && geom[2] == 63) {
            geom[1] = 256;
            return true;
        }
        return false;
    }

    public boolean tryGeometry(TreeFile files, int _sides, int _tracks, int _sectors)
    {
        sides = _sides;
        tracks = _tracks;
        sectors = _sectors;
        int tSectors = tracks * sides * sectors;
        if(type == BaseImage.Type.HARDDRIVE)
            tSectors -= sides * sectors;    //Reserve Cylinder 0.

        //Figure out FAT type.
        int _fatType = (tSectors < 65536) ? 1 : 0;

        try {
            computeParameters(files, _fatType);
        } catch(Exception e) {
            return false;
        }

        root.setClusterSize(clusterSize);
        firstUnusedCluster = root.assignCluster(2 - rootDirectoryClusters);
        sectorLimit = dataAreaStart + (firstUnusedCluster - 2) * clusterSize;

        if(sectorLimit > sectorsTotal)
            return false;
        return true;
    }

    public TreeRawDiskImage(TreeFile files, String label, BaseImage.Type _type, int _sides, int _tracks, int _sectors)
        throws IOException
    {
        type = _type;
        volumeLabel = label;
        int _fatType = 0;
        boolean ok = false;
        boolean autoGeometry = false;
        if(_sides == 0 && _tracks == 0 && _sectors == 0)
            autoGeometry = true;

        if(type != BaseImage.Type.FLOPPY && type != BaseImage.Type.HARDDRIVE)
            throw new IOException("Unsupported image type. Only floppies and HDDs are supported.");

        int[] geom = new int[3];
        geom[0] = _sides;
        geom[1] = _tracks;
        geom[2] = _sectors;
        if(autoGeometry && !nextGeometry(type, geom))
            throw new IOException("No automatic geometry available for this image type");
        while(true) {
            if(tryGeometry(files, geom[0], geom[1], geom[2]))
                break;
            if(!autoGeometry || !nextGeometry(type, geom))
                throw new IOException("Too much data for given disk size");
        }

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
        return sides;
    }

    public int getTracks()
    {
        return tracks;
    }

    public int getSectors()
    {
        return sectors;
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
            writeGeometry(buffer, 451, tracks - 1, sides - 1, sectors);
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
            if(type == BaseImage.Type.HARDDRIVE)
                buffer[21] = (byte)0xF8;                         //Hard Disk.
            else
                buffer[21] = (byte)0xF0;                         //Floppy Disk.
            writeWord(buffer, 22, fatSize);                      //FAT size.
            writeWord(buffer, 24, sectors);
            writeWord(buffer, 26, sides);
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
        return type;
    }

    public void dumpStatus(StatusDumper x)
    {
        //This should never be called.
    }
}
