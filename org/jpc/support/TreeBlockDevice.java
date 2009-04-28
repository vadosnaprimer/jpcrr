/*
    JPC: A x86 PC Hardware Emulator for a pure Java Virtual Machine
    Release Version 2.0

    A project from the Physics Dept, The University of Oxford

    Copyright (C) 2007 Isis Innovation Limited

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
 
    Details (including contact information) can be found at: 

    www.physics.ox.ac.uk/jpc
*/

package org.jpc.support;

import java.io.*;
import java.util.regex.*; //this is used to extract filenames for directory entries
import java.util.*;


public class TreeBlockDevice implements BlockDevice
{
    public int numberOfOpenFiles = 0;

    private File source;
    private boolean locked;
    private byte[] MBR, header, header2, FAT, start, empty;    
    private long rootSize;             // Size in sectors of root directory
    private int startingHead;         //0-255
    private int startingSector;       //1 - 63
    private int startingCylinder;     //0-1023
    private int endingHead;           //0-255
    private int endingSector;         //1 - 63
    private int endingCylinder;       //0-1023
    private long relativeSectors;     // counts from 0
    private int totalSectors, totalClusters, bytesPerSector, sectorsPerCluster, reservedSectors, sectorsPerTrack, hiddenSectors,  sectorsPerFAT,fsinfoSector, backupBootSector,cylinders, heads;
    private long rootStartCluster,freeClusters, lastAllocatedCluster,rootSizeClusters,dataSizeClusters;
    private Map dataMap = new HashMap();
    private Map FATMap = new HashMap();
    private Map writeMap = new HashMap();
    private Vector bufferedClusters = new Vector();
    private int fileReads;
    private rafReads[] openFiles;
    private Map hashFAT; //not used unless writeNewTree is invoked
    public boolean bufferWrites = false; 
    private int maxNumberOfFiles = 5000;

    /* notes:
       Write Tree: to write a copy of the entire directory tree to disk, create a new folder and pass it as a File to writeNewTree(File file)

       Synchronise: to continuously try to synchronise the virtual tree with the underlying tree set bufferWrites to false, otherwise all writes are buffered 
       in an array
    */

    public TreeBlockDevice() {}

    public TreeBlockDevice(File filename, boolean sync) throws IOException
    {
        String specs = "";
        if (sync)
            specs += "sync:";

        specs += filename.getAbsolutePath();

        this.configure(specs);
    }

    public void configure(String specs) throws IOException
    {
        boolean syncWrites = false;
        String fname = specs;
        if (specs.startsWith("sync:")) {
            syncWrites = true;
            fname = specs.substring(5);
        }
        File source = new File(fname);

        openFiles = new rafReads[10];
        fileReads = 0;
        bufferWrites = !syncWrites;
         bytesPerSector = 512;
        sectorsPerCluster = 8;
        this.source = source;

                    System.out.println("start");
        //read in directory structure
        MyDir root = buildTree(source,2);
         System.out.println("end");
        dataSizeClusters=root.getdirSubClusters();
        rootSize=root.getSizeSectors();
           
        locked = false;

        //set Drive Geometry
        sectorsPerTrack = 63;
        heads = 16;//there are subtle issues here with bios remapping, refer to http://www.storagereview.com/guide2000/ref/hdd/bios/modesECHS.html
        if (dataSizeClusters*sectorsPerCluster+rootSize+95 < 1067*sectorsPerTrack*heads) //this needs fixing to a suitable size
            cylinders=1067; // Size of drive in sectors
        else 
            cylinders = (int) (200 + (dataSizeClusters*sectorsPerCluster+rootSize+95)/sectorsPerTrack/heads); 

        //set specifications for disk which go in partition table
        startingHead = 1;
        startingSector = 1;
        startingCylinder = 0;
        endingHead = heads;
        endingSector = sectorsPerTrack;
        endingCylinder = cylinders;
        relativeSectors = 63;
        totalSectors = cylinders*sectorsPerTrack*heads-1;// -1 for the MBR which isn't counted

        //set specifications for disk which go in partition boot sector
        totalClusters = totalSectors/sectorsPerCluster;
        reservedSectors = 32;
        hiddenSectors=63;  //what's not in the partition itself
        sectorsPerFAT = totalClusters*4/bytesPerSector; //add 1 to ensure rounding up
        FAT = new byte[sectorsPerFAT*bytesPerSector];
        mapToFAT(95+2*sectorsPerFAT);
        mapToData(95+2*sectorsPerFAT);
        rootStartCluster = 2;
        fsinfoSector = 1;
        backupBootSector = 6;

        //set specifications for disk which go in FSINFO Sector
        rootSizeClusters = (int) (rootSize-1)/sectorsPerCluster + 1;
        freeClusters = totalClusters-1-rootSizeClusters-dataSizeClusters;          
        lastAllocatedCluster = 2;

        //***********************************************************************************************//
        //set up the master boot record with partition table
        MBR = new byte[512];
        int pos = 0;
        //where mbrcode was
        pos = 446;

        //partition table
        MBR[pos++] = (byte) 0x80;// 80 means system partition, 00 means do not use for booting
        MBR[pos++] = (byte) startingHead;

        // starting Sector (bits 0-5) and high starting cylinder bits 
        int data =  (startingCylinder & 0xC0) | (0x3F & startingSector);
        MBR[pos++] = (byte) data;

        //low 8 bits of starting Cylinder
        MBR[pos++] = (byte) startingCylinder;

        MBR[pos++]=(byte)0x0C;//System ID
        MBR[pos++] = (byte) endingHead;

        // ending Sector (bits 0-5) and high ending cylinder bits 
        data =  (endingCylinder & 0xC0) | (0x3F & endingSector);
        MBR[pos++] = (byte) data;

        //low 8 bits of ending Cylinder
        MBR[pos++] = (byte) endingCylinder;

        //relative sectors
        MBR[pos++] = (byte) (relativeSectors);
        MBR[pos++] = (byte) (relativeSectors >>> 8);
        MBR[pos++] = (byte) (relativeSectors >>> 16);
        MBR[pos++] = (byte) (relativeSectors >>> 24);
        // N.B. remaining sectors on first track after MBR are empty

        //total sectors
        MBR[pos++] = (byte) (totalSectors);
        MBR[pos++] = (byte) (totalSectors >>> 8);
        MBR[pos++] = (byte) (totalSectors >>> 16);
        MBR[pos++] = (byte) (totalSectors >>> 24);

        //fill 3 empty partition entries
        for(int i=0; i< 16*3; i++)
            MBR[pos++]=0x00;

        MBR[0x1FE] = (byte) 0x55;// end of sector marker
        MBR[0x1FF] = (byte) 0xAA;

        //******************************************************************//
        //set up the partition boot sector
        header = new byte[512];
        pos = 0;
        // write header data into this array - don't see www.ntfs.com - it is wrong!
        header[pos++] = (byte) 0xEB;
        header[pos++] = (byte) 0x3C;
        header[pos++] = (byte) 0x90;

        String myOEM = "JPCFS1.0";
        System.arraycopy(myOEM.getBytes(), 0, header, 3, 8);
        pos += 8;

        //BIOS Parameter block
        header[pos++] = (byte) (bytesPerSector);
        header[pos++] = (byte) (bytesPerSector >>> 8);
        header[pos++] = (byte) sectorsPerCluster;
        header[pos++] = (byte) (reservedSectors);
        header[pos++] = (byte) (reservedSectors >>> 8);
        header[pos++] = (byte) 0x02;//number of copies of FAT         byte offset 0x10
        header[pos++] = (byte) 0x00;//this and following 3 bytes are irrelevant for FAT32 (N/A)
        header[pos++] = (byte) 0x00;
        header[pos++] = (byte) 0x00;
        header[pos++] = (byte) 0x00;
        header[pos++] = (byte) 0xF8;//do not change
        header[pos++] = (byte) 0x00;//N/A
        header[pos++] = (byte) 0x00;//N/A
        header[pos++] = (byte) sectorsPerTrack;
        header[pos++] = (byte) 0x00;//              byte offset 0x19
        header[pos++] = (byte) heads;
        header[pos++] = (byte) 0x00;//              byte offset 0x1B

        header[pos++] = (byte) (hiddenSectors);
        header[pos++] = (byte) (hiddenSectors >>> 8);
        header[pos++] = (byte) (hiddenSectors >>> 16);
        header[pos++] = (byte) (hiddenSectors >>> 24);
        
        //byte offset 0x20  = total number of sectors in partition
        header[pos++] = (byte) (totalSectors);
        header[pos++] = (byte) (totalSectors >>> 8);
        header[pos++] = (byte) (totalSectors >>> 16);
        header[pos++] = (byte) (totalSectors >>> 24);
    
        //byte offset 0x24  --  number of sectors per FAT = 520 for 520MB drive (4 bytes)
        header[pos++] = (byte) (sectorsPerFAT);
        header[pos++] = (byte) (sectorsPerFAT >>> 8);
        header[pos++] = (byte) (sectorsPerFAT >>> 16);
        header[pos++] = (byte) (sectorsPerFAT >>> 24);

        header[pos++] = (byte) 0x00;//byte offset 0x28  --  *****this could be wrong, tells which FAT is active (http://home.teleport.com/~brainy/fat32.htm)
        header[pos++] = (byte) 0x00;//byte offset 0x29

        header[pos++] = (byte) 0x00;//byte offset 0x2A  -- this and the next seem irrelevant (FAT32 version)
        header[pos++] = (byte) 0x00;//byte offset 0x2B

        //byte offset 0x2C  -- cluster number of the start of the root directory (4 bytes)
        header[pos++] = (byte) (rootStartCluster);
        header[pos++] = (byte) (rootStartCluster >>> 8);
        header[pos++] = (byte) (rootStartCluster >>> 16);
        header[pos++] = (byte) (rootStartCluster >>> 24);

        //byte offset 0x30  -- sector number of FSINFO sector (is this 1 or two bytes??? although it doesn't matter)
        header[pos++] = (byte) fsinfoSector; 
        header[pos++] = (byte) 0x00;//byte offset 0x31
        //byte offset 0x32  -- sector number of backup boot sector
        header[pos++] = (byte) backupBootSector;
        
        for (int i=1;i<14;i++)
            header[pos++] = (byte) 0x00;

        header[pos++] = (byte) 0x80;//byte offset 0x40
        header[pos++] = (byte) 0x00;
        header[pos++] = (byte) 0x29;//do not change (extended signature)

        header[pos++] = (byte) 0x26;//these four are the serial number of the partition
        header[pos++] = (byte) 0x59;
        header[pos++] = (byte) 0x41;
        header[pos++] = (byte) 0x31;

        header[pos++] = (byte) 0x49;
        header[pos++] = (byte) 0x41;
        header[pos++] = (byte) 0x4E;
        header[pos++] = (byte) 0x52;
        header[pos++] = (byte) 0x4F;
        header[pos++] = (byte) 0x43;
        header[pos++] = (byte) 0x4B;
        header[pos++] = (byte) 0x53;
        header[pos++] = (byte) 0x46;
        header[pos++] = (byte) 0x33;
        header[pos++] = (byte) 0x32;

        header[pos++] = (byte) 0x46;//byte offset 0x52  F
        header[pos++] = (byte) 0x41;//byte offset 0x37  A
        header[pos++] = (byte) 0x54;//byte offset 0x38  T
        header[pos++] = (byte) 0x33;//byte offset 0x39  3
        header[pos++] = (byte) 0x32;//byte offset 0x3A  2
        header[pos++] = (byte) 0x20;//byte offset 0x3B
        header[pos++] = (byte) 0x20;//byte offset 0x3C
        header[pos++] = (byte) 0x20;//byte offset 0x3D

        header[0x1FE] = (byte) 0x55;
        header[0x1FE + 1] = (byte) 0xAA;

        //***************************************************//
        //Now do the second sector, the FSINFO sector
        header2 = new byte[512];
        pos=0;

        header2[pos++] = (byte) 0x52;
        header2[pos++] = (byte) 0x52;
        header2[pos++] = (byte) 0x61;
        header2[pos++] = (byte) 0x41;

        for(int i=0;i<480;i++)
            header2[pos++] = (byte) 0x00;

        header2[pos++] = (byte) 0x72;
        header2[pos++] = (byte) 0x72;
        header2[pos++] = (byte) 0x41;
        header2[pos++] = (byte) 0x61;

        //number of free clusters (4 bytes) set to FFFFFFFF if unknown
        header2[pos++] = (byte) (freeClusters);
        header2[pos++] = (byte) (freeClusters >>> 8);
        header2[pos++] = (byte) (freeClusters >>> 16);
        header2[pos++] = (byte) (freeClusters >>> 24);

        //cluster number of most recently allocated cluster (4 bytes)
        header2[pos++] = (byte) (lastAllocatedCluster);
        header2[pos++] = (byte) (lastAllocatedCluster >>> 8);
        header2[pos++] = (byte) (lastAllocatedCluster >>> 16);
        header2[pos++] = (byte) (lastAllocatedCluster >>> 24);

        for(int i=0;i<14;i++)
            header2[pos++] = (byte) 0x00;

        header2[0x1FE] = (byte) 0x55;
        header2[0x1FE + 1] = (byte) 0xAA;

        //*************************************************************************************//
        //Pad out the first 32 sectors and include copy of bootsector and FSINFO sector
        empty = new byte[512];
        for(int i=0; i<512; i++)
            empty[i] = (byte) 0x00;

        start = new byte[512*95];
        for(int i=1; i<95; i++)
            System.arraycopy(empty, 0, start, i*512, 512);

        System.arraycopy(MBR, 0, start, 0, 512);
        System.arraycopy(header, 0, start, 63*512, 512);
        System.arraycopy(header2, 0, start, 64*512, 512);
        System.arraycopy(header, 0, start, 69*512, 512);
        System.arraycopy(header2, 0, start, 70*512, 512);
    }

    public void dumpState(DataOutput output) throws IOException
    {
        //write out writeMap
        
    }

    public void loadState(DataInput input) throws IOException
    {
        //load up writeMap

    }

    public String getImageFileName()
    {
        return source.getName();
    }

    //print the byte values of an array in hex
    public void printArray(byte[] input, int start, int length)
    {
        String output = "";
        for (int j=start, count=1; j<start+length; j++, count++)
        {
            output = output + Long.toHexString(input[j]+0L) + " ";
            if (count%16==0)
                output = output + " | ";
            if (count%64==0)
                output=output+ "\n";
        }
        System.out.println(output);
    }

    //****************************************************************************//
    public int getFAT(int cluster)
    {
        if (cluster > totalClusters)
            return 0;
        //retrieve a mapping for a cluster from the FAT
        int answer = (FAT[cluster*4] & 0xFF) + ((FAT[cluster*4 +1] & 0xFF) << 8) + ((FAT[cluster*4 +2] & 0xFF) << 16)+((FAT[cluster*4 +3] & 0xFF) << 24);
        return answer;
    }

    public void close()
    {
        /*
        try 
            {
                BufferedWriter file = new BufferedWriter(...("driveimage.img","rw");
                writeImage(file);
            }
        catch (FileNotFoundException e){}
        */

        //fit writes into directory structure and write out to either an image file or original files
        try
        {
            
        }
        catch (Exception e) {}
    }

   

     //READ up to 16 sectors at a time
    public int read(long sectorNumber, byte[] buffer, int size)
    {
         //check map of writes to see if sector has been written to
        if (bufferWrites)
        {
            if (writeMap.containsKey(new Long(sectorNumber)))
            {
                System.arraycopy((byte[]) writeMap.get(new Long(sectorNumber)),0,buffer,0,512);
                return 0;
            }
        }
        
        if (sectorNumber < 95)        // Initial sectors
            System.arraycopy(start, (int) sectorNumber*512, buffer, 0, size*512);
        else if (sectorNumber < 95+2*sectorsPerFAT & sectorNumber>94)        // FAT sectors
            System.arraycopy(FAT, (int) ((sectorNumber-95) % sectorsPerFAT)*512, buffer, 0, size*512);
        //check datamap to get data sector
        else if (dataMap.containsKey(new Long(sectorNumber)))
        {
            try 
            {
                ((MyFATEntry) dataMap.get(new Long(sectorNumber))).getSector(sectorNumber, buffer);
            } 
            catch (IOException e) 
            {
                System.out.println("IO Exception in reading FAT filesystem");
                e.printStackTrace();
                return -1;
            }
        }
        else if (sectorNumber > totalSectors)
            return -1;
        else
            System.arraycopy(empty, 0, buffer, 0, 512);

        return 0;
    }

    //***************************************************************************//
    //write sectors
    public int write(long sectorNumber, byte[] buffer, int size)
    {
        
        if (sectorNumber > totalSectors)
            return -1;
        
        if (bufferWrites)
        {       
            byte[] write = new byte[512];
            System.arraycopy(buffer, 0, write, 0, 512);
            writeMap.put(new Long(sectorNumber),write);
            return 0;
            /*
              if (sectorNumber <95)
              {
              System.arraycopy(buffer, 0, start, (int) sectorNumber*512, size*512);
              return 0;
              }else if ((sectorNumber < 95+2*sectorsPerFAT) & (sectorNumber>94))
              {
              System.arraycopy(buffer, 0,FAT, (int) ((sectorNumber-95) % sectorsPerFAT),  size*512);
              return 0;
              }
            */
        }
        else //continually commit writes
        {
            //check to see if the sector is in the data section
            if (sectorNumber > 95+2*sectorsPerFAT - 1)
            {
                long offset = (long) (sectorNumber - 95 - 2*sectorsPerFAT) % sectorsPerCluster;
                long cluster = (long) (sectorNumber - 95 - 2*sectorsPerFAT)/sectorsPerCluster+2;

                //see if cluster is allocated in FAT
                if (!dataMap.containsKey(new Long(sectorNumber-offset)))
                {
                    //cluster is not allocated
                    byte[] temp = new byte[512];
                    System.arraycopy(buffer, 0, temp, 0, 512);
                    writeMap.put(new Long(sectorNumber), temp);
                    if (!bufferedClusters.contains(new Long(cluster)))
                        bufferedClusters.add(new Long(cluster));
                    return 0;
                }
                else
                {
                    //cluster is allocated
                    //check if it is allocated to a file or a directory
                    MyFATEntry myFile = (MyFATEntry) dataMap.get(new Long(sectorNumber-offset));
                    File file = myFile.getFile();
                    int nextCluster = (int) myFile.getStartCluster();
                    int clusterCounter = 0;
                    int sum = 0;
                    while(nextCluster != cluster)
                    {
                        //get next cluster from FAT
                        nextCluster = getFAT(nextCluster);
                        clusterCounter++;
                    }
                    if (file.isDirectory()) //start of tricky part...
                    {
                        MyDir myDir = (MyDir) myFile; 
                        byte[] oldDirSector = new byte[512];
                        //it is a directory, compare with old directory entry and act accordingly
                        read(sectorNumber, oldDirSector, 1);
                        //add buffer to directory's set of direntries
                        myDir.changeDirEntry(buffer, clusterCounter*sectorsPerCluster + offset, sectorNumber);
                        //update dataMap
                        dataMap.put(new Long(sectorNumber), myDir);
                        for (int i = 0; i<16; i++)
                        {
                            int newStartCluster = (buffer[32*i+26] & 0xFF) + ((buffer[32*i+27] & 0xFF) << 8) +  ((buffer[32*i+20] & 0xFF) << 16) + ((buffer[32*i+21] & 0xFF) << 24);
                            if ( ((buffer[32*i] & 0xFF) == 0xE5) && (getFAT(newStartCluster)==0))
                            {
                                //file has been deleted so we need to as well
                                nextCluster = newStartCluster;

                                if ( dataMap.get(new Long((newStartCluster-2)*sectorsPerCluster + 95 + 2*sectorsPerFAT)) != null)
                                {
                                    ((MyFATEntry) dataMap.get(new Long((newStartCluster-2)*sectorsPerCluster + 95 + 2*sectorsPerFAT))).getFile().delete();
                                    
                                    //remove all entries for file in dataMap
                                    for (int n = 0; n < clusterCounter+1; n++)
                                    {
                                        for (int s =0; s < 8; s++)
                                        {
                                            dataMap.remove(new Long(95 + 2*sectorsPerFAT + (nextCluster-2)*sectorsPerCluster + s));
                                        }
                                        //get next cluster from FAT
                                        nextCluster = getFAT(nextCluster);
                                    }
                                }
                                continue;
                            }
                            if ((buffer[32*i+11] & 0xFF) == 0xF)
                                continue; //skip LFN's for now
                            if ( (buffer[32*i] & 0xFF) == 0xE5)
                                 continue;
                            boolean changed = false;
                            for (int j = 0; j< 32; j++)
                            {
                                if ((oldDirSector[j + 32*i] & 0xFF )!= (buffer[j + 32*i] & 0xFF))
                                    changed = true;
                            }
                            if (!changed)
                                continue;
                            String name = "";
                            String ext = "";
                            for(int m =0; m<8;m++) {name += (char) buffer[32*i + m];}
                            for(int m =0; m<3;m++) {ext += (char) buffer[32*i + 8 + m];}
                            name = name.trim();
                            ext = ext.trim();
                            if ((name.equals(".")) || (name.equals(".."))) //skip current and parent directory entries
                                continue;
                            long newStartSector = (newStartCluster-2)*sectorsPerCluster + 95 + 2*sectorsPerFAT;
                            boolean isDirectory = (buffer[32*i + 11] & 0x10) == 0x10;
                            //add in other attributes here like readonly, hidden etc.
                            if (dataMap.get(new Long(newStartSector)) == null)
                            {
                                //new dir entry was created and we need to create a new File

                                File next;
                                if (isDirectory)
                                {
                                    next = new File(file.getPath(), name + ext);
                                    next.mkdir();
                                    //make into a MyDir and add to data map
                                    MyDir myNewDir = new MyDir(next.getPath(), next.getName(), newStartCluster);

                                    //initialise it's dirEntry to a cluster of 0's
                                    byte[] zero = new byte[512*sectorsPerCluster];
                                    for (int c = 0; c< zero.length; c++) {zero[c]=0;}
                                    myNewDir.changeDirEntry(zero,0,newStartSector); //need to think about whether this is necessary
                                    for (int d = 0; d <8; d++)
                                        dataMap.put(new Long(newStartSector +d), myNewDir);
                                }
                                else
                                {
                                    long fileSize = (buffer[32*i+ 28] & 0xFF) + ((buffer[32*i+ 29] & 0xFF) << 8) + ((buffer[32*i+ 30] & 0xFF) << 16) + ((buffer[32*i+ 31] & 0xFF) << 24);
                                    
                                    next = new File(file.getPath(), name + "." + ext);
                                    try
                                    {
                                        next.createNewFile();
                                        //make into a MyFile and add to data map
                                        MyFile myNewFile = new MyFile(file.getPath(), name + "." + ext, newStartCluster);
                                        myNewFile.setSizeClusters(-1);
                                        myNewFile.setfileSize(fileSize);
                                        dataMap.put(new Long(newStartSector), myNewFile);
                                    }
                                    catch (Exception e)
                                    {
                                        System.out.println("IO Exception - Can't create new file");
                                        e.printStackTrace();
                                    }
                                }
                                //check if the new object created corresponds to data which was written to an unallocated cluster
                                for (int k = 0; k < 8; k++)
                                {
                                    MyFATEntry newone = (MyFATEntry) dataMap.get(new Long(newStartSector));
                                    if (writeMap.get(new Long(newStartSector +k)) != null)
                                    {
                                        if (!next.isDirectory())
                                        {
                                            try
                                            {
                                                RandomAccessFile out = new RandomAccessFile(next, "rw");
                                                out.seek(k*bytesPerSector);
                                                int len = 512;
                                                MyFile castFile = (MyFile) newone;
                                                if ((clusterCounter*sectorsPerCluster + offset + 1)*bytesPerSector > castFile.getfileSize())
                                                {
                                                    len = (int) (512 +castFile.getfileSize() - (clusterCounter*sectorsPerCluster + offset + 1)*bytesPerSector);
                                                }
                                                out.write((byte[]) writeMap.get(new Long(newStartSector+k)), 0, len);
                                                out.close();
                                                //update cluster list
                                                updateClusterList(newone, newStartSector+k);

                                                if (!dataMap.containsKey(new Long(newStartSector + k)))
                                                    dataMap.put(new Long(newStartSector + k), newone);
                                                writeMap.remove(new Long(newStartSector+k));
                                            }
                                            catch (IOException e)
                                            {
                                                System.out.println("IO Exception in writing to file");
                                                e.printStackTrace();
                                            }
                                        }
                                        else
                                        {
                                            MyDir newDir = (MyDir) newone;
                                            newDir.changeDirEntry((byte[]) writeMap.get(new Long(newStartSector+k)), (long) k, newStartSector);
                                            writeMap.remove(new Long(newStartSector+k));
                                        }
                                        bufferedClusters.remove(new Long(newStartCluster));
                                    }
                                }
                                continue;
                            }
                            else 
                            {
                                //it has changed the properties of a file which is already allocated and we need to update, possibly rename it
                                MyFATEntry changedFile = (MyFATEntry) dataMap.get(new Long(newStartSector));
                                File oldfile = changedFile.getFile();
                                String path = oldfile.getPath();
                                path = path.substring(0,path.length() - oldfile.getName().length());
                                if (isDirectory)
                                {
                                    oldfile.renameTo(new File(path, name + ext));
                                    changedFile.setFile(new File(path, name + ext));
                                    //dataMap.put(new Long(newStartSector), changedFile);
                                    //need to recurse though directories contents and change their paths
                                    changePathOfTree(changedFile, changedFile.getFile().getPath());
                                }
                                else
                                {
                                    oldfile.renameTo(new File(path, name + "." + ext));
                                    changedFile.setFile(new File(path, name + "." + ext));
                                }
                            }
                        } 
                    }
                    else
                    {
                        //it is a file, write sector to it
                        try
                        {
                            RandomAccessFile out = new RandomAccessFile(file, "rw");
                            out.seek((clusterCounter*sectorsPerCluster + offset)*bytesPerSector);
                            int len = 512;
                            MyFile castFile = (MyFile) myFile;
                            if ((clusterCounter*sectorsPerCluster + offset + 1)*bytesPerSector > castFile.getfileSize())
                            {
                                len = (int) (512 +castFile.getfileSize() - (clusterCounter*sectorsPerCluster + offset + 1)*bytesPerSector);
                            }
                            out.write(buffer,0,len); //need to clip zeros here at endof file somehow
                            out.close();
                            //update file's clusterlist
                            updateClusterList(myFile, sectorNumber);

                            if (!dataMap.containsKey(new Long(sectorNumber)))
                                dataMap.put(new Long(sectorNumber), myFile);
                            return 0;
                        }
                        catch (IOException e)
                        {
                            System.out.println("IO Exception in writing to file");
                            e.printStackTrace();
                            return -1;
                        }
                    }
                } 
            }
            if ((sectorNumber > 94) && (sectorNumber < 95 + 2*sectorsPerFAT))
            {
                //sector is in FAT

                //read old FAT first to compare to
                byte[] oldFATSector = new byte[512];
                read(sectorNumber, oldFATSector, 1);
                System.arraycopy(buffer, 0, FAT, (int) ((sectorNumber - 95) % sectorsPerFAT)*512, 512);
                long minCluster = ((sectorNumber-95) % sectorsPerFAT)*512/4;
                //long maxCluster = minCluster+512/4;
                boolean FATChanged;

                //make comparison
                // for (int j = (int) minCluster; j < maxCluster; j++)
                for (int j=0; j < 512/4; j++)
                {
                    FATChanged = false;
                    for (int i=0; i<4; i++)
                    {
                        if (buffer[4*j +i] != oldFATSector[4*j+i])
                            FATChanged = true;
                    }
                    if (FATChanged)
                    {
                        if (getFAT((int) (j + minCluster)) == 0)
                        {
                            //a FAT entry has been set to zero so we need to delete a file or dir
                            MyFATEntry myFile = (MyFATEntry) dataMap.get(new Long((j - 2 + minCluster)*sectorsPerCluster + 95 + 2*sectorsPerFAT));
                            if (myFile != null)
                            {
                                myFile.getFile().delete();
                                
                                //remove all references to the file from the data Map
                                long startClusterOfFile = myFile.getStartCluster();
                                for (int k =0; k<8; k++)
                                {
                                    dataMap.remove(new Long((startClusterOfFile-2)*sectorsPerCluster + 95 + 2*sectorsPerFAT + k));
                                }
                            }
                        }
                    }
                }

                //need to check if references have been made to allow us to commit writes from writeMap
                int i=0;
                while (i < bufferedClusters.size())
                {
             
                    long thisCluster = ((Long) bufferedClusters.get(i)).longValue();
                    //see if anything has been allocated with thisCluster value in FAT
                    for (long cluster = minCluster; cluster < minCluster + 512/5; cluster++)
                    {
                        if (getFAT((int) cluster) == thisCluster)
                        {
                            if (dataMap.get(new Long((cluster-2)*sectorsPerCluster +95 +2*sectorsPerFAT))!= null)
                            {
                                try
                                {
                                    MyFATEntry myFile = (MyFATEntry) dataMap.get(new Long((cluster-2)*sectorsPerCluster +95 +2*sectorsPerFAT));
                                    
                                    //need code to cover if it is a directory ************************************

                                    RandomAccessFile out = new RandomAccessFile(new File(myFile.getPath()), "rw");
                                    //calculate offset in file for this cluster
                                    int offset = 0;
                                    long nextCluster = myFile.getStartCluster();
                                    while (nextCluster != thisCluster)
                                    {
                                        offset++;
                                        nextCluster = getFAT((int) nextCluster);
                                    }
                                    for (int j=0; j<8; j++)
                                    {
                                
                                        if(writeMap.containsKey(new Long((thisCluster-2)*sectorsPerCluster +95 + 2*sectorsPerFAT +j)))
                                        {
                                            out.seek(((offset)*sectorsPerCluster+j)*bytesPerSector);
                                            int len = 512;
                                            MyFile castFile = (MyFile) myFile;
                                            if ((offset*sectorsPerCluster + j + 1)*bytesPerSector > castFile.getfileSize())
                                            {
                                                len = (int) (512 +castFile.getfileSize() - (offset*sectorsPerCluster + j + 1)*bytesPerSector);
                                            }
                                            out.write((byte[]) writeMap.get(new Long((thisCluster-2)*sectorsPerCluster + 95 + 2*sectorsPerFAT + j)), 0, len);
                                            //update cluster list for file
                                            updateClusterList(myFile, (thisCluster-2)*sectorsPerCluster + 95 + 2*sectorsPerFAT +j);
                                            
                                            if (!dataMap.containsKey(new Long((thisCluster-2)*sectorsPerCluster + 95 + 2*sectorsPerFAT +j)))
                                                dataMap.put(new Long((thisCluster-2)*sectorsPerCluster + 95 + 2*sectorsPerFAT +j), myFile);
                                        }
                                    }
                                    out.close();
                                    bufferedClusters.remove(i);
                                    i=i-1;
                                }
                                catch (IOException e)
                                {
                                    System.out.println("IO Exception in writing to file");
                                    e.printStackTrace();
                                }
                            }
                        }
                    }
                    i++;
                }
            }
            if (sectorNumber <95)  //end of tricky part.
            {
                System.arraycopy(buffer, 0, start, (int) sectorNumber*512, 512);
            }
            return 0;
        }
    }

    public void changePathOfTree(MyFATEntry mydir, String path)
    {
        MyDir myDir = (MyDir) mydir;
        byte[] dirEntry = myDir.getdirEntry();
        //loop over sectors of dirEntry
        for (int i = 0; i <  (int) dirEntry.length/32 ; i++)
        {
            int newStartCluster = (dirEntry[32*i+26] & 0xFF) + ((dirEntry[32*i+27] & 0xFF) << 8) +  ((dirEntry[32*i+20] & 0xFF) << 16) + ((dirEntry[32*i+21] & 0xFF) << 24);
            if ((dirEntry[32*i] & 0xFF) == 0xE5) 
                continue;
            if ((dirEntry[32*i+11] & 0xFF) == 0xF)
                continue; //skip LFN's for now
            if ((dirEntry[32*i] & 0xFF) == 0x00)
                continue;
            String name = "";
            String ext = "";
            for(int m =0; m<8;m++) {name += (char) dirEntry[32*i + m];}
            for(int m =0; m<3;m++) {ext += (char) dirEntry[32*i + 8 + m];}
            name = name.trim();
            ext = ext.trim();
            if ((name.equals(".")) || (name.equals(".."))) //skip current and parent directory entries
                continue;
            long newStartSector = (newStartCluster-2)*sectorsPerCluster + 95 + 2*sectorsPerFAT;
            boolean isDirectory = (dirEntry[32*i + 11] & 0x10) == 0x10;
            MyFATEntry myfile = (MyFATEntry) dataMap.get(new Long(newStartSector));
            File file = myfile.getFile();
            if (!isDirectory)
            {
                myfile.setFile(new File(path, name + "." + ext));
            }
            else
            {
                myfile.setFile(new File(path, name + ext));
                changePathOfTree((MyDir) myfile, myfile.getFile().getPath());
            }
        }
    }

    public void updateClusterList(MyFATEntry myFile, long sectorNumber)
    {
        if (!myFile.clusterList.containsKey(new Long(clusterNumber(sectorNumber))))
        {
            myFile.clusterList.put(new Long(clusterNumber(sectorNumber)), new Long(myFile.getSizeClusters() + 1));
            myFile.setSizeClusters(myFile.getSizeClusters() + 1);
            if (myFile.getLastSector() < sectorNumber)//wrong
                myFile.setLastSector(sectorNumber);
        }
    }

    public long clusterNumber(long sector) 
    {
        return (long) (sector - 95 - 2*sectorsPerFAT)/sectorsPerCluster + 2 ;
    }

    public boolean inserted()
    {
        return true;
    }

    public boolean locked()
    {
        return locked;
    }

    public boolean readOnly()
    {
        return true;
    }

    public void setLock(boolean l)
    {
        locked = l;
    }

    public long getTotalSectors()
    {
        return totalSectors;
    }

    public int cylinders()
    {
            //smaller than 1024
        return cylinders; 
    }

    public int heads()
    {
            //smaller than 256
        return heads;
    }

    public int sectors()// is this looking for totalSectors or 63??
    {
        return (int) sectorsPerTrack;
    }

    public int type()
    {
        return TYPE_HD;
    }
 
    //convert FATmap to FAT
    private void mapToFAT(long offset)
    {
        // set up first 8 bytes of FAT
        byte[] initialbuffer = {(byte) 0xF8,(byte) 0xFF,(byte) 0xFF,(byte) 0x0F,(byte) 0xFF,(byte) 0xFF,(byte) 0xFF,(byte) 0x0F};
        byte[] endmark = {(byte) 0xFF,(byte) 0xFF,(byte) 0xFF,(byte) 0x0F};
        System.arraycopy(initialbuffer, 0, FAT,0, 8);
        
          Set entries = FATMap.entrySet();
        Iterator itt = entries.iterator();
        while (itt.hasNext()) 
        {
            Map.Entry entry = (Map.Entry) itt.next();
            long startCluster =  ((Long) entry.getKey()).longValue();
            long lengthClusters = ((MyFATEntry) entry.getValue()).getSizeClusters();
            
            int pos, next;
            for (long j = 0; j<lengthClusters-1; j++)
            {
                pos = (int) ((startCluster+j)*4);
                next = (int) (startCluster+1+j);
                FAT[pos++] = (byte) next;
                FAT[pos++] = (byte) (next >>> 8);
                FAT[pos++] = (byte) (next >>> 16);
                FAT[pos++] = (byte) (next >>> 24);
            }
            System.arraycopy(endmark, 0, FAT, (int)(startCluster+lengthClusters-1)*4, 4);
        }
    }

    //convert FATmap to data map
    private void mapToData(long offset)
    {
        Set entries = FATMap.entrySet();
        Iterator itt = entries.iterator();
        while (itt.hasNext()) 
        {
            //Long key = (Long) itt.next();
            //MyFATEntry fe = (MyFATEntry) FATMap.get(key);

            Map.Entry entry = (Map.Entry) itt.next();
            for (long j = 0; j < ((MyFATEntry)entry.getValue()).getSizeSectors(); j++)
                dataMap.put(new Long(offset+(((Long)entry.getKey()).longValue()-2)*sectorsPerCluster+j),entry.getValue());
        }
    }

    //method to write out to a new directory tree
    public void writeNewTree(File root)
    {
        //assume sectorsPerCluster = 8
        int relativeSectors;
        int reservedSectors;
        int sectorsPerFAT;
        
        //write disk to a new directory structure
        {
            if (root.isDirectory() && root.canWrite())
            {
                //read in first two sectors to determine drive properties
                byte[] s0 = new byte[512];
                byte[] s1 = new byte[512];
                read(0, s0, 1);
                relativeSectors = (int) s0[454] + (s0[455] << 8) + (s0[456] << 16) + (s0[457] << 24);
                read(relativeSectors, s1, 1);
                reservedSectors = (int) s1[14] + (s1[15] << 8);
                sectorsPerFAT = (int) s1[36] + (s1[37] << 8) + (s1[38] << 16) + (s1[39] << 24);

                /*
                //read FAT into byte[]
                FAT = new byte[512*sectorsPerFAT];
                for (int i = relativeSectors + reservedSectors; i < relativeSectors + reservedSectors + sectorsPerFAT; i++)
                {
                    read((long) i, s0, 1);
                    System.arraycopy(s0,0,FAT,(i - relativeSectors - reservedSectors)*512,512);
                }
                */

                //convert FAT to Hashmap
                hashFAT = new HashMap();
                int sum;
                for (int j = 2; j < FAT.length/4; j++)
                {
                    //convert FAT entry to an int
                    sum = 0;
                    for (int k = 0; k<4; k++)
                    {
                        sum += (int) ((FAT[4*j + k] & 0xFF) << k*8);
                    }

                    //check it isn't empty
                    if (sum != 0)
                    {
                        hashFAT.put(new Integer(j), new Integer(sum));
                    }
                }
                readToWrite(2, root);
            }
        }
    }
    
    //method to read a directory or file from this TBD and write it out to a new physical one
    private void readToWrite(int startingCluster, File file) //make this return an int to signify success
    {
        int cluster = startingCluster;
        byte[] buffer = new byte[512];
        
        if (file.isDirectory())
        {
            byte[] acluster = new byte[512*8];
            byte[] dir = new byte[0];
            
            //read directory into byte[]
            while (true)
            {
                //read cluster of dir sector by sector and store in array
                for (int i=0; i<8; i++)
                {
                    if (read(relativeSectors + reservedSectors + 2*sectorsPerFAT + (cluster-2)*8 + i, buffer, 1) == 0)
                    {
                        System.arraycopy(buffer, 0, acluster, i*512, 512);
                    }
                }
                
                byte[] newdir = new byte[dir.length + acluster.length];
                System.arraycopy(dir, 0, newdir, 0, dir.length);
                System.arraycopy(acluster, 0, newdir, dir.length, acluster.length);
                dir = newdir;
                    
                //check for more clusters
                cluster = ((Integer) hashFAT.get(new Integer(cluster))).intValue();
                if (cluster == 268435455)//251592447) //endmark
                    break;
            }
            
            //for each directory entry create a new file/dir and call this method on it with its start cluster
            int newStartCluster;
            String name;
            String ext;
            boolean isDirectory;
            for (int k = 0; k < dir.length/32; k++)
            {
                if ((dir[32*k]==0) || dir[32*k +11] ==  0xF) 
                    continue;
                
                name = "";
                ext = "";
                for(int m =0; m<8;m++) {name += (char) dir[32*k + m];}
                for(int m =0; m<3;m++) {ext += (char) dir[32*k + 8 + m];}
                name = name.trim();
                ext = ext.trim();
                newStartCluster = (dir[32*k + 26] & 0xFF) + ((dir[32*k + 27] & 0xFF) << 8) +  ((dir[32*k + 20] & 0xFF) << 16) + ((dir[32*k + 21] & 0xFF) << 24);
                isDirectory = (dir[32*k + 11] & 0x10) == 0x10;
                //add in other attributes here like readonly, hidden etc.
                File next;
                if (isDirectory)
                    {
                        next = new File(file.getPath(), name + ext);
                        next.mkdir();
                    }
                else
                {
                    next = new File(file.getPath(), name + "." + ext);
                    try
                    {
                        next.createNewFile();
                        }
                    catch (Exception e)
                    {
                        System.out.println("IO Exception - Can't create new file");
                        e.printStackTrace();
                    }
                }  
                readToWrite(newStartCluster, next);
            }
        }
        
        if (file.isFile())
        {
            //open stream to write to file
            try
            {
                BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(file));
                
                while (true)
                {
                    //read cluster of file sector by sector and write to disk
                    int data = 0;
                    for (int i=0; i<8; i++)
                    {
                        for (int h = 0; h < 512; h++) { buffer[h] = (byte) 0x00;}
                        if (read(relativeSectors + reservedSectors + 2*sectorsPerFAT + (cluster-2)*8 + i, buffer, 1) == 0)
                        {
                            //if the first byte is 0 check if it is a blank sector
                            if (buffer[0] == 0) 
                                for ( int j = 0; j<512; j++) 
                                {
                                    if (buffer[j]==0)
                                        data += 1;
                                }
                            //if it is a blank sector don't write it
                            if (data == 512)
                                break;

                            //if sector ends in a 0 and we are in the last cluster, then trim the zeroes from the end
                            if ((((Integer) hashFAT.get(new Integer(cluster))).intValue() == 268435455) && (buffer[511] == 0))
                            {
                                int index = 511;
                                while (buffer[index]==0)
                                {
                                    index--;
                                    if (index == -1)
                                        break;
                                }
                                out.write(buffer,0,index+1);
                                break;
                            }
                            out.write(buffer);
                        }
                    }
                    
                    //check for more clusters
                    cluster = ((Integer) hashFAT.get(new Integer(cluster))).intValue();
                    if (cluster == 268435455) //EOF marker
                        break;
                }
                out.close();
            }
            catch (IOException e)
            {
                System.out.println("IO Exception in writing new directory tree");
                e.printStackTrace();
            }
        }
    }
    
    //write image of disk
    private void writeImage(DataOutput dout) throws IOException
    {
        byte[] buffer=new byte[512];
        long i = 0;

        while (read(i, buffer, 1) != -1)
        {
            dout.write(buffer);
            i++;
        }
    }
    

    //***********************************************************************************//
    //build directory structure

    private MyDir buildTree(File root, long startcluster) throws IOException
    {
        if (!root.exists()) 
            return null;
        if (!root.isDirectory()) 
            return null;//change this to work for a single file eventually

        String path = root.getPath();
        String name = root.getName();
        MyDir myDir = new MyDir(path,name,startcluster);
        long subClusters;
        String[] files = root.list();

        //figure out size of directory entry
        int size =0;
        int hiddenFiles = 1;
        for (int i=0; i<files.length; i++)
        {
            String longext="",longname="";
            if(files[i].lastIndexOf(".") != -1)
            {
                longname = files[i].substring(0, files[i].lastIndexOf("."));
                longext = files[i].substring(files[i].lastIndexOf(".")+1);
                if (longname.length() == 0)
                    longname = "~" + hiddenFiles;
                hiddenFiles++;
            }
            else
            {
                longname = files[i];
                longext = "";
            }

            if ((longname.length() < 9) && (longext.length()<4))
                size += 1;
            else 
            {
                if (longext.length() == 0)
                    size += (int) 2+(longname.length()-1)/13;
                else
                    size += (int) 2+(longname.length()+longext.length())/13;
            }
        }

        //set cluster size of this directory
        myDir.setSize(size+2);  // + 2 for the . and .. entries
        myDir.setSizeSectors((long) (size+2)*32/bytesPerSector+1);
        
        subClusters=0;
        for (int i=0; i<files.length;i++)
        {
            File fnew = new File(path, files[i]);
            if (!fnew.isFile())
                continue;

            MyFile file = new MyFile(path, files[i], myDir.getStartCluster() + myDir.getSizeClusters() + subClusters);
            //generate cluster list
            file.makeClusterList();

            myDir.addFile(file);
            subClusters += file.getSizeClusters();
            //add entry to FAT map
            FATMap.put(new Long(file.getStartCluster()), file);
        }

        myDir.setdirSubClusters(myDir.getdirSubClusters() + subClusters);
        for (int i=0; i<files.length;i++)
        {
            File fnew = new File(path,files[i]);
            if (!fnew.isDirectory())
                continue;

            File fnewdir=new File(path,files[i]);
            MyDir m = buildTree(fnewdir, myDir.getStartCluster() + myDir.getSizeClusters() + subClusters); //the cool call
            subClusters+=m.getSizeClusters()+m.getdirSubClusters();
            if (m!= null) 
                myDir.addDir(m);
        }
               myDir.setdirSubClusters(myDir.getdirSubClusters() + subClusters);
        myDir.makedirEntry();

        //generate cluster list
        myDir.makeClusterList();

        //add entry to FAT map
        FATMap.put(new Long(myDir.getStartCluster()), myDir);

        return myDir;
    }

    private abstract class MyFATEntry
    {
        private String path, name;
        private long startCluster, sizeSectors, sizeClusters;
         private File thisFile;
        public Map clusterList = new HashMap(); // list of clusters in object
        private long lastSector;

        MyFATEntry(File file, long startCluster)
        {
            thisFile = file;
            this.path = file.getPath();
            this.name = file.getName();
            this.startCluster = startCluster;
        }

        abstract int getSector(long sectorNumber, byte[] buffer) throws IOException;

        public void setSizeSectors(long i) 
        {
            sizeSectors = i; 
            sizeClusters = (long) (sizeSectors-1)/sectorsPerCluster+1;
        }

        public void makeClusterList()
        {
            long cluster = getStartCluster();
            for (long counter = 0; counter< getSizeClusters(); counter++ )
            {
                clusterList.put(new Long(cluster), new Long(counter));
                cluster++;
            }
            setLastSector(getSizeSectors() + (getStartCluster()-2)*sectorsPerCluster + 95 + 2*sectorsPerFAT);
        }

        public void setLastSector(long sector)
        {
            this.lastSector = sector;
        }

        public long getLastSector()
        {
            return lastSector;
        }

        public long getStartCluster() 
        {
            return startCluster;
        }
        
        long getSizeSectors() 
        {
            return sizeSectors;
        }
        
        long getSizeClusters() 
        {
            return sizeClusters;
        }

        public void setSizeClusters(long length)
        {
            this.sizeClusters = length;
        }

        public File getFile()
        {
            return thisFile;
        }

        public void setFile(File file)
        {
            thisFile = file;
        }

        String getName()
        {
            return name;
        }
        
        String getPath()
        {
            return path;
        }
    }

    //**************************//
    //File Class
    private class MyFile extends MyFATEntry
    {
        private RandomAccessFile thisRAF;
        private long fileSize;
  
        MyFile(String path, String name, long start) throws IOException
        {
            super(new File(path, name), start);        
 
            //Get rid of this!!
            numberOfOpenFiles++;
            if (numberOfOpenFiles % 100==0)
                System.out.println("Opened " + numberOfOpenFiles + " files.");

            //check if too many files have been loaded
            if (numberOfOpenFiles > maxNumberOfFiles)
                throw new IndexOutOfBoundsException("Too many files loaded: try a directory with fewer files.");

             fileSize = getFile().length();
            setSizeSectors((long) fileSize/512+1);
          }  
  
         public int getSector(long sectorNumber, byte[] buffer) throws IOException
        {
            try
            {
                long oddsectors = (sectorNumber-95-2*sectorsPerFAT) % sectorsPerCluster;
                long offset = ((Long) clusterList.get(new Long(clusterNumber(sectorNumber)))).longValue()*sectorsPerCluster*512;
                offset = offset + oddsectors*512;

                //see if file is already open
                boolean openAlready = false;
                if (thisRAF != null)
                {
                    for (int i = 0; i< openFiles.length; i++)
                    {
                        if (openFiles[i] != null)
                            if (thisRAF == openFiles[i].RAF())
                            {
                                openAlready = true;
                                //update read counter
                                fileReads++;
                                openFiles[i].setReads(fileReads);
                                break;
                            }
                    }
                }
                if (!openAlready)
                {
                    Arrays.sort(openFiles, new rafComparator());
                    //if 10 or more files have been opened, then close the last file
                    if (openFiles[openFiles.length-1] != null)
                    {
                        openFiles[openFiles.length-1].RAF().close();
                    }
                    //open the new file and add it to the open list
                    thisRAF =  new RandomAccessFile(getFile(), "r");                        
                    openFiles[openFiles.length-1] = new rafReads(fileReads, thisRAF);
                    fileReads++;
                }
                thisRAF.seek(offset);
                int len = Math.min(512, (int) (thisRAF.length() - offset));
                thisRAF.readFully(buffer, 0, len);
                   return 0;
            }
            catch (Exception e)
            {
                e.printStackTrace();
                return -1;
            }
        }

        public void setfileSize(long size)
        {
            this.fileSize = size;
        }

        public long getfileSize() {return fileSize;}
    }

    //*********************//
    //Directory Class
    public class MyDir extends MyFATEntry
    {
    
        // All files in this directory:
        private Vector files = new Vector();
        // All directories in this directory:
        private Vector dirs = new Vector();
         private long dirSubClusters;
        private Vector shortnames = new Vector();
        private byte[] dirEntry = new byte[0];
        private byte[] lfn = new byte[0];
        private int size;
        
        public MyDir(String path, String name, long startCluster) 
        {
            super(new File(path), startCluster);
            this.dirSubClusters=0;
           }  

        public byte[] getdirEntry()
        {
            return dirEntry;
        }

         //get set of directory entries for this directory
        public int getSector(long sectorNumber, byte[] buffer)
        {
            long oddsectors = (sectorNumber-95-2*sectorsPerFAT) % sectorsPerCluster;
            long offset = ((Long) clusterList.get(new Long(clusterNumber(sectorNumber)))).longValue()*sectorsPerCluster*512;
            offset = offset + oddsectors*512;

            //   long offset =  (sectorNumber - 95-2*sectorsPerFAT -(super.getStartCluster()-2)*sectorsPerCluster) * 512;
            int len;
            if ( size*32 - offset > 512)
                len = 512;
            else 
                len = (int) (size*32 - offset);

            for (int i=0; i<512; i++)
                buffer[i] = 0x00;

            System.arraycopy(dirEntry, (int) offset, buffer, 0, len);
            //needs some error catching
            return 0;
        }

        public void setSize(int i) {this.size=i;}
 
        public void setdirSubClusters(long i) {dirSubClusters = i;}        
        public long getdirSubClusters() {return dirSubClusters;}

        public void addFile(MyFile f) 
        { 
            files.addElement(f); 
        }  
        
        public void addDir(MyDir d) 
        { 
            dirs.addElement(d); 
        }  
        
        public void changeDirEntry(byte[] buffer, long sectorOffset, long sectorNumber)
        {
            //update clusterlist
            clusterList.put(new Long(clusterNumber(sectorNumber)), new Long(sectorOffset/sectorsPerCluster));

            if (dirEntry.length < 512 + sectorOffset*512)
            {
                byte[] temp = new byte[(int) (sectorOffset+1)*512];
                System.arraycopy(dirEntry, 0, temp, 0, dirEntry.length);
                dirEntry = temp;
            }
            size = dirEntry.length/32;
            System.arraycopy(buffer, 0, dirEntry, (int) sectorOffset*bytesPerSector, 512);
        }

        public Vector getFiles() { return files; }  
        
        public Vector getDirs() { return dirs; }  
        
        public void makedirEntry()
        {
            byte[] middle;
            //add . and .. entries to direntry
            byte[] dotdot = new byte[64];
            dotdot[0]=(byte) 0x2E;
            for (int i = 1; i<11; i++)
                dotdot[i]=(byte) 0x20;
            dotdot[11]=(byte) 0x10;

            dotdot[32] = (byte) 0x2E;
            dotdot[33] = (byte) 0x2E;
                for (int i = 34; i<43; i++)
                dotdot[i]=(byte) 0x20;
            dotdot[43]=(byte) 0x10;

            this.dirEntry = dotdot;

            for (int i=0; i<dirs.size();i++)
            {
                middle = makeDirEntry((MyDir) dirs.get(i));
                byte[] temp = new byte[this.dirEntry.length + middle.length];
                System.arraycopy(this.dirEntry, 0, temp, 0, this.dirEntry.length);
                System.arraycopy(middle, 0, temp, this.dirEntry.length, middle.length);
                this.dirEntry = temp;
            }

            for (int i=0; i<files.size();i++)
            {
                middle = makeDirEntry((MyFile) files.get(i));
                byte[] temp = new byte[this.dirEntry.length + middle.length];
                System.arraycopy(this.dirEntry, 0, temp, 0, this.dirEntry.length);
                System.arraycopy(middle, 0, temp, this.dirEntry.length, middle.length);
                this.dirEntry = temp;
            }  
        }

        private byte[] makeDirEntry(MyFile file)
        {
            byte[] entry = new byte[32];
            for (int i=0;i<32;i++) 
                entry[i]= (byte) 0x00;

            byte[] fullEntry;
              String filename = file.getName();
            String name = filename;
            String ext= "   ";

            if (filename.lastIndexOf(".") != -1)
            {
                name = filename.substring(0,filename.lastIndexOf("."));
                ext = filename.substring(filename.lastIndexOf(".")+1);
                while (ext.length() < 3) 
                    ext = ext+" ";
            }

            //put filename into file entry
            if ((name.length()<9) && (ext.length()<4))
            {
                //add shortname to shortnames list
                shortnames.add(name);
                
                //put dir name into dir entry
                System.arraycopy(name.toUpperCase().getBytes(),0,entry,0,name.length());
                
                //put in blank space after filename if it is less than 8 characters
                for (int i =name.length(); i< 8;i++)
                    entry[i]=(byte) 0x20;

                //put in extension
                System.arraycopy(ext.toUpperCase().getBytes(),0,entry,8,ext.length());
                
                //pad ext with spaces
                for (int i =ext.length(); i< 3;i++)
                    entry[8+i]=(byte) 0x20;
            }
            else 
            {
                String shortname = makeFullLFNDirEntry(name, this.lfn, ext);
                System.arraycopy(shortname.toUpperCase().getBytes(), 0, entry, 0, 8);
                System.arraycopy(ext.toUpperCase().getBytes(), 0, entry, 8, 3);
            }
            
            entry[11]=(byte) 0x20;  //Attrib Byte
            if(file.getFile().isHidden()) 
                entry[11]+=(byte) 0x02; //hidden
            if(!file.getFile().canWrite()) 
                entry[11]+=(byte) 0x01;//read only

            //put in starting cluster (high 2 bytes)
            int startCluster = (int) file.getStartCluster();
            entry[20] = (byte) (startCluster >>> 16);
            entry[21] = (byte) (startCluster >>> 24);

            //put in starting cluster (low 2 bytes)
            entry[26] = (byte) startCluster;
            entry[27] = (byte) (startCluster >>> 8);

            //put in file size in bytes 
            int fileSize = (int) file.getfileSize();
            entry[28] = (byte) fileSize;
            entry[29] = (byte) (fileSize >>> 8);
            entry[30] = (byte) (fileSize >>> 16);
            entry[31] = (byte) (fileSize >>> 24);

            //time and date stuff
            GregorianCalendar cal = new GregorianCalendar();
            cal.setTimeInMillis(file.getFile().lastModified());
            int year = cal.get(Calendar.YEAR);
            int month = cal.get(Calendar.MONTH) + 1;
            int day = cal.get(Calendar.DAY_OF_MONTH);
            int hour24 = cal.get(Calendar.HOUR_OF_DAY);
            int minute = cal.get(Calendar.MINUTE);
            int second = cal.get(Calendar.SECOND);
            int time = ((int) second/2) + (minute << 5) + (hour24 << 11);   
            int date = day+ (month << 5) + ((year -1980) << 9);

            entry[22] = (byte) time;
            entry[23] = (byte) (time >>> 8);
            entry[24] = (byte) date;
            entry[25] = (byte) (date >>> 8);
            entry[16] = entry[24];
            entry[17] = entry[25];
            entry[18] = entry[24];
            entry[19] = entry[25];
            entry[14] = entry[22];
            entry[15] = entry[23];

            fullEntry = new byte[this.lfn.length + 32];
            System.arraycopy(this.lfn, 0, fullEntry, 0, this.lfn.length);
            System.arraycopy(entry, 0, fullEntry, this.lfn.length, 32);
            this.lfn=new byte[0];
            return fullEntry;
        }

        private byte[] makeLFNDirEntry(byte[] unicodeName, int checksum, int ordinal, boolean last)
        {
            int length = unicodeName.length;

            byte[] entry = new byte[32];
            for (int i=0;i<32;i++) entry[i]= (byte) 0x00;
            entry[11]=0xF;

            byte ordinalb;
            if (!last)
                ordinalb = (byte) ordinal;
            else 
                ordinalb = (byte) (ordinal | 0x40);
            entry[0] = ordinalb;
            entry[13] = (byte) checksum;

            //put up to 13 unicode characters into entry
            if(!last)
            {
                System.arraycopy(unicodeName, 0, entry, 1, 10);
                System.arraycopy(unicodeName, 10, entry, 14, 12);
                System.arraycopy(unicodeName, 22, entry, 28, 4);
            }
            else
            {
                if(length<11)
                    System.arraycopy(unicodeName,0,entry,1,length);
                else if (length<22)
                {
                    System.arraycopy(unicodeName,0,entry,1,10);
                    System.arraycopy(unicodeName,10,entry,14,length-10);
                }
                else
                {
                    System.arraycopy(unicodeName,0,entry,1,10);
                    System.arraycopy(unicodeName,10,entry,14,12);
                    System.arraycopy(unicodeName,22,entry,28,length-22);
                }
            }
            return entry;
        }

        private String makeFullLFNDirEntry(String input, byte[] lfn,  String ext)
        {
            byte[] unicodeName;
            byte[] prename = null;
            byte[] defaultCharBytes = null;
            String defaultChar = "_";
            String shortName="";
            int pos = 0;

            if (ext.length() != 0)
                unicodeName = new byte[2*(input.length() + ext.length() + 1)];
            else
                unicodeName = new byte[2*input.length()];

            try
            {
                System.arraycopy(input.getBytes("UTF-16LE"), 0, unicodeName, 0, 2*input.length());
                defaultCharBytes = defaultChar.getBytes("UTF-16LE");
            }
            catch (Exception e) {}

            //get each unicode character that is valid and put in array
            for(int i=0; i<input.length(); i++) 
            {
                char letter = input.charAt(i);
                //need to include |, ^, &, (, ), {, } in theory
                if(Character.isLetterOrDigit(letter) || ("_~#$-%@".indexOf(letter) >= 0))
                {
                    pos++;
                    if (shortName.length() < 7) 
                        shortName = shortName+letter;
                    if (shortName.length() == 7)
                        shortName = shortName.substring(0,6) +"~1";
                }
                else
                {
                    System.arraycopy(defaultCharBytes, 0, unicodeName, pos++, 2);
                    if (shortName.length() < 7) {shortName = shortName + defaultChar;}
                    if (shortName.length() == 6) {shortName = shortName.substring(0,6) + "~1";}
                }
            }
            
            if (ext.length() != 0)
            {
                String dotext = "."+ ext;
                try
                {
                    prename = dotext.getBytes("UTF-16LE");
                }
                catch (Exception e) {}

                System.arraycopy(prename, 0, unicodeName, unicodeName.length - prename.length, prename.length);
                for(int i=0; i<dotext.length(); i++) 
                {
                    char letter = dotext.charAt(i);
                    pos++;
                    //need to include |, ^, &, (, ), {, } in theory
                    if(!(Character.isLetterOrDigit(letter) || ("_~#$-%@".indexOf(letter) >= 0))) 
                        System.arraycopy(defaultCharBytes,0,unicodeName,pos,2);
                }
            }
            
            //rename files with duplicate shortnames of 8 characters length
            if (shortName.length() == 8)
            {
                for (int i=1; shortnames.contains(shortName); i++)
                {
                    if (i < 10)
                        shortName = shortName.substring(0, 7) + i;
                    else if (i < 100)
                        shortName = shortName.substring(0, 5)+ "~" + i;
                }
            }
            
            //code to cover if short name is < 8 long and is a duplicate
            if (shortName.length() <8)
            {
                int i=1;
                while (shortnames.contains(shortName))
                {
                    if (i < 9)
                    {
                        i++;
                        shortName=shortName.substring(0, Math.min(5,shortName.length()))+"~" + i;
                    }
                    else
                    {
                        i++;
                        shortName=shortName.substring(0, Math.min(4,shortName.length()))+ "~" + i;
                    }
                }
            }
              
            //add spaces to pad out shortname
            while (shortName.length()<8) 
                shortName = shortName+ " ";
            shortnames.add(shortName);
            
            //add spaces to pad out ext
            while (ext.length()<3) 
                ext = ext+ " ";

            //do checksum
            int checksum = 0;
            String checkString = shortName+ext;
            for (int i=0; i<11; i++)
                checksum = (((checksum & 1) << 7) | ((checksum & 0xFE) >> 1)) + checkString.charAt(i);
        
            //create each LFN entry in reverse order with the checksum
            int numberOfEntries = (int) (unicodeName.length/2-1)/13+1;
            byte[] unicodeNamePart; 
            unicodeNamePart = new byte[unicodeName.length % 26];
            this.lfn = new byte[32 * numberOfEntries];

            for (int j=numberOfEntries; j>0; j--)
            {
                if (j == numberOfEntries-1)
                    unicodeNamePart = new byte[26];
             
                System.arraycopy(unicodeName, (j-1)*26, unicodeNamePart, 0, unicodeNamePart.length);
                byte[] lfnd = makeLFNDirEntry(unicodeNamePart, checksum, j, j == numberOfEntries);
                System.arraycopy(lfnd, 0, this.lfn, 32*(numberOfEntries-j), 32);
            }
                
            return shortName;
        }

        private byte[] makeDirEntry(MyDir dir)
        {
            byte[] entry = new byte[32];
            byte[] fullEntry;

            for (int i=0;i<32;i++) 
                entry[i]= (byte) 0x00;
                
            String filename = dir.getName();

            //put dir name into dir entry
            if (filename.length()<9)
            {
                //put dir name into dir entry
                System.arraycopy(filename.toUpperCase().getBytes(),0,entry,0,filename.length());
                
                //put in blank space after filename if it is less than 8 characters
                for (int i =filename.length(); i< 11;i++)
                    entry[i]=(byte) 0x20;
            }
            else
            {
                String shortname=makeFullLFNDirEntry(filename,lfn,"");
                System.arraycopy(shortname.toUpperCase().getBytes(),0,entry,0,8);
                for (int i =8; i< 11;i++)
                    entry[i]=(byte) 0x20;    
            }

            entry[11]=(byte) 0x10;  //Attrib Byte
            if (dir.getFile().isHidden()) 
                entry[11]+=(byte) 0x02; //hidden
            if (!dir.getFile().canWrite()) 
                entry[11]+=(byte) 0x01;//read only

            //put in starting cluster (high 2 bytes)
            int startCluster = (int) dir.getStartCluster();
            entry[20] = (byte) (startCluster >>> 16);
            entry[21] = (byte) (startCluster >>> 24);

            //put in starting cluster (low 2 bytes)
            entry[26] = (byte) startCluster;
            entry[27] = (byte) (startCluster >>> 8);

            //time and date stuff
            GregorianCalendar cal = new GregorianCalendar();
            cal.setTimeInMillis(dir.getFile().lastModified());
            int year = cal.get(Calendar.YEAR);
            int month = cal.get(Calendar.MONTH) + 1;
            int day = cal.get(Calendar.DAY_OF_MONTH);
            int hour24 = cal.get(Calendar.HOUR_OF_DAY);
            int minute = cal.get(Calendar.MINUTE);
            int second = cal.get(Calendar.SECOND);
            int time = ((int) second/2) + (minute << 5) + (hour24 << 11);   
            int date = day+ (month << 5) + ((year -1980) << 9);

            entry[22] = (byte) time;
            entry[23] = (byte) (time >>> 8);
            entry[24] = (byte) date;
            entry[25] = (byte) (date >>> 8);
            entry[16] = entry[24];
            entry[17] = entry[25];
            entry[18] = entry[24];
            entry[19] = entry[25];
            entry[14] = entry[22];
            entry[15] = entry[23];
           
            fullEntry = new byte[this.lfn.length + 32];
            System.arraycopy(this.lfn, 0, fullEntry, 0, this.lfn.length);
            System.arraycopy(entry, 0, fullEntry, this.lfn.length, 32);
            this.lfn=new byte[0];
            return fullEntry;
        }
    }
    
    //override compare for rafReads objects
    private class rafComparator implements Comparator
    {
        public int compare(Object o1,Object o2)
        {
            if ((o2 == null) && (o1 != null))
                return 1;
            if ((o1 == null) && (o2 != null))
                return -1;
            if ((o1 == null) && (o2 == null))
                return 0;
            if(((rafReads)o1).readAt()  < ((rafReads)o2).readAt()) 
                return -1;
            if(((rafReads)o1).readAt()  > ((rafReads)o2).readAt()) 
                return 1;
            else return 0;
        }
    }

    //used to store files which have been recently read in an array
    private class rafReads 
    {
        int readAt;
        RandomAccessFile RAF;

        rafReads() {};
        
        rafReads(int k, RandomAccessFile file)
        {
            readAt = k;
            RAF = file;
        }

        public RandomAccessFile RAF()
        {
            return RAF;
        }

        public int readAt()
        {
            return readAt;
        }

        public void setReads(int k)
        {
            readAt = k;
        }
    }
}

