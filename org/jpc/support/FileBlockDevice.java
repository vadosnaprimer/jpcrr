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
//import java.nio.*; // This import is only needed for the ByteBuffer & ByteOrder stuff
import java.util.regex.*; //this is used to extract filenames for directory entries

public class FileBlockDevice implements BlockDevice
{
    private File source;
    private RandomAccessFile raf;
    private boolean locked;
    private byte[] MBR;
    private byte[] header;
    private byte[] header2;
    private byte[] start;             // first 33 sectors of drive
    private byte[] FAT;
    private byte[] rootdir;
    private byte[] empty;             // An empty sector
    private int cylinders;
    private int heads;    
    private long fileSizeSectors;     //Size of file in sectors
    private int rootSize;             // Size in sectors of root directory
    private int rootSizeClusters;
    private int startingHead;         //0-255
    private int startingSector;       //1 - 63
    private int startingCylinder;     //0-1023
    private int endingHead;           //0-255
    private int endingSector;         //1 - 63
    private int endingCylinder;       //0-1023
    private long relativeSectors;     // counts from 0
    private int totalSectors;
    private int bytesPerSector;       //normally 512
    private int sectorsPerCluster;
    private int reservedSectors;
    private int sectorsPerTrack;
    private int hiddenSectors;
    private int sectorsPerFAT;
    private long rootStartCluster;
    private int fsinfoSector;
    private int backupBootSector;
    private long freeClusters;
    private long lastAllocatedCluster;
    private static Pattern pattern;   //this and the next are the regular expression matching bits
    private static Matcher matcher;
    private int fileSizeClusters;

    public FileBlockDevice()
    {
    }

    public void configure(String specs) throws IOException
    {
	File source = new File(specs);
        this.source = source;
        raf = new RandomAccessFile(source, "rw");
        
	locked = false;
        
	//set Drive Geometry
	if (raf.length()/512 < 2147483646*512)
	    fileSizeSectors=raf.length()/512+1;

	sectorsPerTrack = 63;
	heads = 255;
	if (fileSizeSectors <67*sectorsPerTrack*heads)
	    cylinders=67; // Size of drive in sectors
	else
	    cylinders = 1000; //(int) fileSizeSectors/sectorsPerTrack/heads+1;

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
	bytesPerSector = 512;
	sectorsPerCluster = 8;
	reservedSectors = 32;
	hiddenSectors=63;  //what's not in the partition itself
	sectorsPerFAT = totalSectors/sectorsPerCluster*4/bytesPerSector+1; //add 1 to ensure rounding up
	rootStartCluster = 2;
	fsinfoSector = 1;
	backupBootSector = 6;

	System.out.println("sectorsPerFAT = " + sectorsPerFAT);

	//set specifications for disk which go in FSINFO Sector
	//freeClusters=100; not set yet but doesn't matter 
	lastAllocatedCluster = 3;

	FAT = new byte[sectorsPerFAT*bytesPerSector];
	rootSize=1;


	//set up the master boot record with partition table
	MBR =new byte[512];
	int pos = 0;

	//put in executable code to first 446 bytes
	byte[] MBRcode = {
	(byte)0x00 ,(byte)0x33 ,(byte)0xC0 ,(byte)0x8E ,(byte)0xD0 ,(byte)0xBC ,(byte)0x00 ,(byte)0x7C,
	(byte)0x8B ,(byte)0xF4 ,(byte)0x50 ,(byte)0x07 ,(byte)0x50 ,(byte)0x1F ,(byte)0xFB ,(byte)0xFC,
	(byte)0xBF ,(byte)0x00 ,(byte)0x06 ,(byte)0xB9 ,(byte)0x00 ,(byte)0x01 ,(byte)0xF2 ,(byte)0xA5,
	(byte)0xEA ,(byte)0x1D ,(byte)0x06 ,(byte)0x00 ,(byte)0x00 ,(byte)0xBE ,(byte)0xBE ,(byte)0x07,
	(byte)0xB3 ,(byte)0x04 ,(byte)0x80 ,(byte)0x3C ,(byte)0x80 ,(byte)0x74 ,(byte)0x0E ,(byte)0x80,
	(byte)0x3C ,(byte)0x00 ,(byte)0x75 ,(byte)0x1C ,(byte)0x83 ,(byte)0xC6 ,(byte)0x10 ,(byte)0xFE,
	(byte)0xCB ,(byte)0x75 ,(byte)0xEF ,(byte)0xCD ,(byte)0x18 ,(byte)0x8B ,(byte)0x14 ,(byte)0x8B,
	(byte)0x4C ,(byte)0x02 ,(byte)0x8B ,(byte)0xEE ,(byte)0x83 ,(byte)0xC6 ,(byte)0x10 ,(byte)0xFE,
	(byte)0xCB ,(byte)0x74 ,(byte)0x1A ,(byte)0x80 ,(byte)0x3C ,(byte)0x00 ,(byte)0x74 ,(byte)0xF4,
	(byte)0xBE ,(byte)0x8B ,(byte)0x06 ,(byte)0xAC ,(byte)0x3C ,(byte)0x00 ,(byte)0x74 ,(byte)0x0B,
	(byte)0x56 ,(byte)0xBB ,(byte)0x07 ,(byte)0x00 ,(byte)0xB4 ,(byte)0x0E ,(byte)0xCD ,(byte)0x10,
	(byte)0x5E ,(byte)0xEB ,(byte)0xF0 ,(byte)0xEB ,(byte)0xFE ,(byte)0xBF ,(byte)0x05 ,(byte)0x00,
	(byte)0xBB ,(byte)0x00 ,(byte)0x7C ,(byte)0xB8 ,(byte)0x01 ,(byte)0x02 ,(byte)0x57 ,(byte)0xCD,
	(byte)0x13 ,(byte)0x5F ,(byte)0x73 ,(byte)0x0C ,(byte)0x33 ,(byte)0xC0 ,(byte)0xCD ,(byte)0x13,
	(byte)0x4F ,(byte)0x75 ,(byte)0xED ,(byte)0xBE ,(byte)0xA3 ,(byte)0x06 ,(byte)0xEB ,(byte)0xD3,
	(byte)0xBE ,(byte)0xC2 ,(byte)0x06 ,(byte)0xBF ,(byte)0xFE ,(byte)0x7D ,(byte)0x81 ,(byte)0x3D,
	(byte)0x55 ,(byte)0xAA ,(byte)0x75 ,(byte)0xC7 ,(byte)0x8B ,(byte)0xF5 ,(byte)0xEA ,(byte)0x00,
	(byte)0x7C ,(byte)0x00 ,(byte)0x00 ,(byte)0x49 ,(byte)0x6E ,(byte)0x76 ,(byte)0x61 ,(byte)0x6C,
	(byte)0x69 ,(byte)0x64 ,(byte)0x20 ,(byte)0x70 ,(byte)0x61 ,(byte)0x72 ,(byte)0x74 ,(byte)0x69,
	(byte)0x74 ,(byte)0x69 ,(byte)0x6F ,(byte)0x6E ,(byte)0x20 ,(byte)0x74 ,(byte)0x61 ,(byte)0x62,
	(byte)0x6C ,(byte)0x65 ,(byte)0x00 ,(byte)0x45 ,(byte)0x72 ,(byte)0x72 ,(byte)0x6F ,(byte)0x72,
	(byte)0x20 ,(byte)0x6C ,(byte)0x6F ,(byte)0x61 ,(byte)0x64 ,(byte)0x69 ,(byte)0x6E ,(byte)0x67,
	(byte)0x20 ,(byte)0x6F ,(byte)0x70 ,(byte)0x65 ,(byte)0x72 ,(byte)0x61 ,(byte)0x74 ,(byte)0x69,
	(byte)0x6E ,(byte)0x67 ,(byte)0x20 ,(byte)0x73 ,(byte)0x79 ,(byte)0x73 ,(byte)0x74 ,(byte)0x65,
	(byte)0x6D ,(byte)0x00 ,(byte)0x4D ,(byte)0x69 ,(byte)0x73 ,(byte)0x73 ,(byte)0x69 ,(byte)0x6E,
	(byte)0x67 ,(byte)0x20 ,(byte)0xF6 ,(byte)0x70 ,(byte)0x65 ,(byte)0x72 ,(byte)0x61 ,(byte)0x74,
	(byte)0x69 ,(byte)0x6E ,(byte)0x67 ,(byte)0x20 ,(byte)0x73 ,(byte)0x79 ,(byte)0x73 ,(byte)0x74,
	(byte)0x65 ,(byte)0x6D ,(byte)0x00 ,(byte)0x00 ,(byte)0x80 ,(byte)0x45 ,(byte)0x14 ,(byte)0x15,
	(byte)0x00 ,(byte)0x00 ,(byte)0x00 ,(byte)0x00 ,(byte)0x00 ,(byte)0x00 ,(byte)0x00 ,(byte)0x00,
	(byte)0x00 ,(byte)0x00 ,(byte)0x00 ,(byte)0x00 ,(byte)0x00 ,(byte)0x00 ,(byte)0x00 ,(byte)0x00,
	(byte)0x00 ,(byte)0x00 ,(byte)0x00 ,(byte)0x00 ,(byte)0x00 ,(byte)0x00 ,(byte)0x00 ,(byte)0x00,
	(byte)0x00 ,(byte)0x00 ,(byte)0x00 ,(byte)0x00 ,(byte)0x00 ,(byte)0x00 ,(byte)0x00 ,(byte)0x00,
	(byte)0x00 ,(byte)0x00 ,(byte)0x00 ,(byte)0x00 ,(byte)0x00 ,(byte)0x00 ,(byte)0x00 ,(byte)0x00,
	(byte)0x00 ,(byte)0x00 ,(byte)0x00 ,(byte)0x00 ,(byte)0x00 ,(byte)0x00 ,(byte)0x00 ,(byte)0x00,
	(byte)0x00 ,(byte)0x00 ,(byte)0x00 ,(byte)0x00 ,(byte)0x00 ,(byte)0x00 ,(byte)0x00 ,(byte)0x00,
	(byte)0x00 ,(byte)0x00 ,(byte)0x00 ,(byte)0x00 ,(byte)0x00 ,(byte)0x00 ,(byte)0x00 ,(byte)0x00,
	(byte)0x00 ,(byte)0x00 ,(byte)0x00 ,(byte)0x00 ,(byte)0x00 ,(byte)0x00 ,(byte)0x00 ,(byte)0x00,
	(byte)0x00 ,(byte)0x00 ,(byte)0x00 ,(byte)0x00 ,(byte)0x00 ,(byte)0x00 ,(byte)0x00 ,(byte)0x00,
	(byte)0x00 ,(byte)0x00 ,(byte)0x00 ,(byte)0x00 ,(byte)0x00 ,(byte)0x00 ,(byte)0x00 ,(byte)0x00,
	(byte)0x00 ,(byte)0x00 ,(byte)0x00 ,(byte)0x00 ,(byte)0x00 ,(byte)0x00 ,(byte)0x00 ,(byte)0x00,
	(byte)0x00 ,(byte)0x00 ,(byte)0x00 ,(byte)0x00 ,(byte)0x00 ,(byte)0x00 ,(byte)0x00 ,(byte)0x00,
	(byte)0x00 ,(byte)0x00 ,(byte)0x00 ,(byte)0x00 ,(byte)0x00 ,(byte)0x00 ,(byte)0x00 ,(byte)0x00,
	(byte)0x00 ,(byte)0x00 ,(byte)0x00 ,(byte)0x00 ,(byte)0x00 ,(byte)0x00 ,(byte)0x00 ,(byte)0x00,
	(byte)0x00 ,(byte)0x00 ,(byte)0x00 ,(byte)0x00 ,(byte)0x00 ,(byte)0x00 ,(byte)0x00 ,(byte)0x00,
	(byte)0x00 ,(byte)0x00 ,(byte)0x00 ,(byte)0x00 ,(byte)0x00 ,(byte)0x00 ,(byte)0x00 ,(byte)0x00,
	(byte)0x00 ,(byte)0x00 ,(byte)0x00 ,(byte)0x00 ,(byte)0x00 ,(byte)0x00 ,(byte)0x00 ,(byte)0x00,
	(byte)0x00 ,(byte)0x00 ,(byte)0x00 ,(byte)0x00 ,(byte)0x00 ,(byte)0x00 ,(byte)0x00 ,(byte)0x00,
	(byte)0x00 ,(byte)0x00 ,(byte)0x00 ,(byte)0x00 ,(byte)0x00 ,(byte)0x00 ,(byte)0x00 ,(byte)0x00,
	(byte)0x00 ,(byte)0x00 ,(byte)0x00 ,(byte)0x00 ,(byte)0x00 ,(byte)0x00 ,(byte)0x00 ,(byte)0x00,
	(byte)0x00 ,(byte)0x00 ,(byte)0x00 ,(byte)0x00 ,(byte)0x00 ,(byte)0x00 ,(byte)0x00 ,(byte)0x00,
	(byte)0x00 ,(byte)0x00 ,(byte)0x00 ,(byte)0x00 ,(byte)0x00 ,(byte)0x00 ,(byte)0x00 ,(byte)0x00,
	(byte)0x00 ,(byte)0x00 ,(byte)0x00 ,(byte)0x00 ,(byte)0x00 ,(byte)0x00 ,(byte)0x00 ,(byte)0x00,
	(byte)0x00 ,(byte)0x00 ,(byte)0x00 ,(byte)0x00 ,(byte)0x00 ,(byte)0x00 ,(byte)0x00 ,(byte)0x00,
	(byte)0x00 ,(byte)0x00 ,(byte)0x00 ,(byte)0x00 ,(byte)0x00 ,(byte)0x00 ,(byte)0x00 ,(byte)0x00,
	(byte)0x00 ,(byte)0x00 ,(byte)0x00 ,(byte)0x00 ,(byte)0x00 ,(byte)0x00 ,(byte)0x00 ,(byte)0x00,
	(byte)0xFD ,(byte)0x4E ,(byte)0xF2 ,(byte)0x14 ,(byte)0x00 ,(byte)0x00
	};


	System.arraycopy(MBRcode,0,MBR,0,446);
	pos=446;

	//partition table
	MBR[pos++]=(byte)0x80;// 80 means system partition, 00 means do not use for booting
	System.arraycopy(toByteArray(startingHead,1),0,MBR,pos++,1);

	// starting Sector (bits 0-5) and high starting cylinder bits 
	System.arraycopy(toByteArray(startingSector | ((startingCylinder >>> 2) & (64+128)),1),0,MBR,pos++,1);
	//low 8 bits of starting Cylinder
	System.arraycopy(toByteArray(startingCylinder & 0xFF,1),0,MBR,pos++,1);

	MBR[pos++]=(byte)0x0C;//System ID
	System.arraycopy(toByteArray(endingHead,1),0,MBR,pos++,1);

	// ending Sector (bits 0-5) and high ending cylinder bits 
	System.arraycopy(toByteArray(endingSector | ((endingCylinder >>> 2) & (64+128)),1),0,MBR,pos++,1);
	//low 8 bits of ending Cylinder
	System.arraycopy(toByteArray(endingCylinder & 0xFF,1),0,MBR,pos++,1);

	//relative sectors
	System.arraycopy(toByteArray(relativeSectors,4),0,MBR,pos++,4);
	pos=pos+3;
	// N.B. remaining sectors on first track after MBR are empty

	//total sectors
	System.arraycopy(toByteArray(totalSectors,4),0,MBR,pos++,4);
	pos=pos+3;

	//fill 3 empty partition entries
	for(int i=0;i<16*3;i++)
            MBR[pos++]=0x00;

	MBR[0x1FE]=(byte)0x55;// end of sector marker
	MBR[0x1FF]=(byte)0xAA;

	//******************************************************************
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
	System.arraycopy(toByteArray(bytesPerSector,2),0,header,pos++,2);
	pos++;
	System.arraycopy(toByteArray(sectorsPerCluster,1),0,header,pos++,1);
	System.arraycopy(toByteArray(reservedSectors,2),0,header,pos++,2);
	pos++;
        header[pos++] = (byte) 0x02;//number of copies of FAT         byte offset 0x10
        header[pos++] = (byte) 0x00;//this and following 3 bytes are irrelevant for FAT32 (N/A)
        header[pos++] = (byte) 0x00;
        header[pos++] = (byte) 0x00;
        header[pos++] = (byte) 0x00;
        header[pos++] = (byte) 0xF8;//do not change
        header[pos++] = (byte) 0x00;//N/A
        header[pos++] = (byte) 0x00;//N/A
        
        System.arraycopy(toByteArray(sectorsPerTrack,1),0,header,pos++,1);
        header[pos++] = (byte) 0x00;//                       byte offset 0x19
	System.arraycopy(toByteArray(heads,1),0,header,pos++,1);
	header[pos++] = (byte) 0x00;//                         byte offset 0x1B
	System.arraycopy(toByteArray(hiddenSectors,4),0,header,pos++,4);//number of hidden sectors (63 on non partitioned media)  byte offset 0x1C
	pos=pos+3;
        
	//byte offset 0x20  = total number of sectors in partition
	System.arraycopy(toByteArray(totalSectors,4),0,header,pos++,4);
	pos=pos+3;
    
	//byte offset 0x24  --  number of sectors per FAT = 520 for 520MB drive (4 bytes)
	System.arraycopy(toByteArray(sectorsPerFAT,4),0,header,pos++,4);
	pos=pos+3;
	header[pos++] = (byte) 0x00;//byte offset 0x28  --  *****this could be wrong, tells which FAT is active (http://home.teleport.com/~brainy/fat32.htm)
        header[pos++] = (byte) 0x00;//byte offset 0x29

        header[pos++] = (byte) 0x00;//byte offset 0x2A  -- this and the next seem irrelevant (FAT32 version) (possibly set to 0)
        header[pos++] = (byte) 0x00;//byte offset 0x2B

	//byte offset 0x2C  -- cluster number of the start of the root directory (4 bytes)
	System.arraycopy(toByteArray(rootStartCluster,4),0,header,pos++,4);
	pos=pos+3;
	//byte offset 0x30  -- sector number of FSINFO sector (is this 1 or two bytes??? although it doesn't matter)
	System.arraycopy(toByteArray(fsinfoSector,1),0,header,pos++,1);
        header[pos++] = (byte) 0x00;//byte offset 0x31
	//byte offset 0x32  -- sector number of backup boot sector
	System.arraycopy(toByteArray(backupBootSector,1),0,header,pos++,1);
        header[pos++] = (byte) 0x00;//byte offset 0x33

        header[pos++] = (byte) 0x00;//byte offset 0x34  -- 12 bytes of N/A
        header[pos++] = (byte) 0x00;//byte offset 0x35
        header[pos++] = (byte) 0x00;
        header[pos++] = (byte) 0x00;
        header[pos++] = (byte) 0x00;
        header[pos++] = (byte) 0x00;
        header[pos++] = (byte) 0x00;
        header[pos++] = (byte) 0x00;
        header[pos++] = (byte) 0x00;
        header[pos++] = (byte) 0x00;
        header[pos++] = (byte) 0x00;
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

        //Need to copy in the bootstrap code (420 bytes)
        byte[] bootstrap = {
	    (byte)0x33, (byte)0xC9, (byte)0x8E, (byte)0xD1, (byte)0xBC, (byte)0xF4, (byte)0x7B, (byte)0x8E,
	    (byte)0xC1, (byte)0x8E, (byte)0xD9, (byte)0xBD, (byte)0x00, (byte)0x7C, (byte)0x88, (byte)0x4E,
	    (byte)0x02, (byte)0x8A, (byte)0x56, (byte)0x40, (byte)0xB4, (byte)0x08, (byte)0xCD, (byte)0x13,
	    (byte)0x73, (byte)0x05, (byte)0xB9, (byte)0xFF, (byte)0xFF, (byte)0x8A, (byte)0xF1, (byte)0x66,
	    (byte)0x0F, (byte)0xB6, (byte)0xC6, (byte)0x40, (byte)0x66, (byte)0x0F, (byte)0xB6, (byte)0xD1,
	    (byte)0x80, (byte)0xE2, (byte)0x3F, (byte)0xF7, (byte)0xE2, (byte)0x86, (byte)0xCD, (byte)0xC0,
	    (byte)0xED, (byte)0x06, (byte)0x41, (byte)0x66, (byte)0x0F, (byte)0xB7, (byte)0xC9, (byte)0x66,
	    (byte)0xF7, (byte)0xE1, (byte)0x66, (byte)0x89, (byte)0x46, (byte)0xF8, (byte)0x83, (byte)0x7E,
	    (byte)0x16, (byte)0x00, (byte)0x75, (byte)0x38, (byte)0x83, (byte)0x7E, (byte)0x2A, (byte)0x00,
	    (byte)0x77, (byte)0x32, (byte)0x66, (byte)0x8B, (byte)0x46, (byte)0x1C, (byte)0x66, (byte)0x83,
	    (byte)0xC0, (byte)0x0C, (byte)0xBB, (byte)0x00, (byte)0x80, (byte)0xB9, (byte)0x01, (byte)0x00,
	    (byte)0xE8, (byte)0x2B, (byte)0x00, (byte)0xE9, (byte)0x48, (byte)0x03, (byte)0xA0, (byte)0xFA,
	    (byte)0x7D, (byte)0xB4, (byte)0x7D, (byte)0x8B, (byte)0xF0, (byte)0xAC, (byte)0x84, (byte)0xC0,
	    (byte)0x74, (byte)0x17, (byte)0x3C, (byte)0xFF, (byte)0x74, (byte)0x09, (byte)0xB4, (byte)0x0E,
	    (byte)0xBB, (byte)0x07, (byte)0x00, (byte)0xCD, (byte)0x10, (byte)0xEB, (byte)0xEE, (byte)0xA0,
	    (byte)0xFB, (byte)0x7D, (byte)0xEB, (byte)0xE5, (byte)0xA0, (byte)0xF9, (byte)0x7D, (byte)0xEB,
	    (byte)0xE0, (byte)0x98, (byte)0xCD, (byte)0x16, (byte)0xCD, (byte)0x19, (byte)0x66, (byte)0x60,
	    (byte)0x66, (byte)0x3B, (byte)0x46, (byte)0xF8, (byte)0x0F, (byte)0x82, (byte)0x4A, (byte)0x00,
	    (byte)0x66, (byte)0x6A, (byte)0x00, (byte)0x66, (byte)0x50, (byte)0x06, (byte)0x53, (byte)0x66,
	    (byte)0x68, (byte)0x10, (byte)0x00, (byte)0x01, (byte)0x00, (byte)0x80, (byte)0x7E, (byte)0x02,
	    (byte)0x00, (byte)0x0F, (byte)0x85, (byte)0x20, (byte)0x00, (byte)0xB4, (byte)0x41, (byte)0xBB,
	    (byte)0xAA, (byte)0x55, (byte)0x8A, (byte)0x56, (byte)0x40, (byte)0xCD, (byte)0x13, (byte)0x0F,
	    (byte)0x82, (byte)0x1C, (byte)0x00, (byte)0x81, (byte)0xFB, (byte)0x55, (byte)0xAA, (byte)0x0F,
	    (byte)0x85, (byte)0x14, (byte)0x00, (byte)0xF6, (byte)0xC1, (byte)0x01, (byte)0x0F, (byte)0x84,
	    (byte)0x0D, (byte)0x00, (byte)0xFE, (byte)0x46, (byte)0x02, (byte)0xB4, (byte)0x42, (byte)0x8A,
	    (byte)0x56, (byte)0x40, (byte)0x8B, (byte)0xF4, (byte)0xCD, (byte)0x13, (byte)0xB0, (byte)0xF9,
	    (byte)0x66, (byte)0x58, (byte)0x66, (byte)0x58, (byte)0x66, (byte)0x58, (byte)0x66, (byte)0x58,
	    (byte)0xEB, (byte)0x2A, (byte)0x66, (byte)0x33, (byte)0xD2, (byte)0x66, (byte)0x0F, (byte)0xB7,
	    (byte)0x4E, (byte)0x18, (byte)0x66, (byte)0xF7, (byte)0xF1, (byte)0xFE, (byte)0xC2, (byte)0x8A,
	    (byte)0xCA, (byte)0x66, (byte)0x8B, (byte)0xD0, (byte)0x66, (byte)0xC1, (byte)0xEA, (byte)0x10,
	    (byte)0xF7, (byte)0x76, (byte)0x1A, (byte)0x86, (byte)0xD6, (byte)0x8A, (byte)0x56, (byte)0x40,
	    (byte)0x8A, (byte)0xE8, (byte)0xC0, (byte)0xE4, (byte)0x06, (byte)0x0A, (byte)0xCC, (byte)0xB8,
	    (byte)0x01, (byte)0x02, (byte)0xCD, (byte)0x13, (byte)0x66, (byte)0x61, (byte)0x0F, (byte)0x82,
	    (byte)0x54, (byte)0xFF, (byte)0x81, (byte)0xC3, (byte)0x00, (byte)0x02, (byte)0x66, (byte)0x40,
	    (byte)0x49, (byte)0x0F, (byte)0x85, (byte)0x71, (byte)0xFF, (byte)0xC3, (byte)0x4E, (byte)0x54,
	    (byte)0x4C, (byte)0x44, (byte)0x52, (byte)0x20, (byte)0x20, (byte)0x20, (byte)0x20, (byte)0x20,
	    (byte)0x20, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
	    (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
	    (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
	    (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
	    (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
	    (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
	    (byte)0x00, (byte)0x00, (byte)0x0D, (byte)0x0A, (byte)0x52, (byte)0x65, (byte)0x6D, (byte)0x6F,
	    (byte)0x76, (byte)0x65, (byte)0x20, (byte)0x64, (byte)0x69, (byte)0x73, (byte)0x6B, (byte)0x73,
	    (byte)0x20, (byte)0x6F, (byte)0x72, (byte)0x20, (byte)0x6F, (byte)0x74, (byte)0x68, (byte)0x65,
	    (byte)0x72, (byte)0x20, (byte)0x6D, (byte)0x65, (byte)0x64, (byte)0x69, (byte)0x61, (byte)0x2E,
	    (byte)0xFF, (byte)0x0D, (byte)0x0A, (byte)0x44, (byte)0x69, (byte)0x73, (byte)0x6B, (byte)0x20,
	    (byte)0x65, (byte)0x72, (byte)0x72, (byte)0x6F, (byte)0x72, (byte)0xFF, (byte)0x0D, (byte)0x0A,
	    (byte)0x50, (byte)0x72, (byte)0x65, (byte)0x73, (byte)0x73, (byte)0x20, (byte)0x61, (byte)0x6E,
	    (byte)0x79, (byte)0x20, (byte)0x6B, (byte)0x65, (byte)0x79, (byte)0x20, (byte)0x74, (byte)0x6F,
	    (byte)0x20, (byte)0x72, (byte)0x65, (byte)0x73, (byte)0x74, (byte)0x61, (byte)0x72, (byte)0x74,
	    (byte)0x0D, (byte)0x0A, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0xAC,
	    (byte)0xCB, (byte)0xD8, (byte)0x00, (byte)0x00
	};

        for(int i=0;i<bootstrap.length;i++)
            header[pos++] = bootstrap[i];
        
        header[0x1FE] = (byte) 0x55;
        header[0x1FE + 1] = (byte) 0xAA;
        
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
        header2[pos++] = (byte) 0xFF;
        header2[pos++] = (byte) 0xFF;
        header2[pos++] = (byte) 0xFF;
        header2[pos++] = (byte) 0xFF;

        //cluster number of most recently allocated cluster (4 bytes)
        System.arraycopy(toByteArray(lastAllocatedCluster,4),0,header,pos++,4);
	pos=pos+3;

        for(int i=0;i<14;i++)
            header2[pos++] = (byte) 0x00;

        header2[0x1FE] = (byte) 0x55;
        header2[0x1FE + 1] = (byte) 0xAA;

	//**************************************************************************************

        //FAT1  at sector 32
	// set up first 8 bytes of FAT
	byte[] initialbuffer = {(byte) 0xF8,(byte) 0xFF,(byte) 0xFF,(byte) 0x0F,(byte) 0xFF,(byte) 0xFF,(byte) 0xFF,(byte) 0x0F};
	byte[] endmark = {(byte) 0xFF,(byte) 0xFF,(byte) 0xFF,(byte) 0xFF};
	System.arraycopy(initialbuffer, 0,FAT,0, 8);

	rootSizeClusters = (int) (rootSize-1)/sectorsPerCluster + 1;
	// put in root directory
	for(int i = 0;i < rootSizeClusters-1 ;i++)
            System.arraycopy(toByteArray((long) 3+i,(int) 4),0,FAT,4*(i+2),4);
	// put in end of root directory
	System.arraycopy(endmark,0,FAT, rootSizeClusters + 1 , 4);

	// put in file
	fileSizeClusters = (int) (fileSizeSectors-1)/sectorsPerCluster+1;
	for(int i = rootSizeClusters + 2 ; i < rootSizeClusters + 1 + fileSizeClusters ; i++)
            System.arraycopy(toByteArray((long) 1+i ,(int) 4), 0, FAT, 4*i, 4);

	//put in end of file
	System.arraycopy(endmark,0,FAT, rootSizeClusters + 1 + fileSizeClusters , 4);

	//**************************************************************************************
	//make root directory
	rootdir=new byte[rootSizeClusters * sectorsPerCluster * bytesPerSector];
	for (int i=0; i<rootdir.length;i++){
	    rootdir[i]=(byte) 0x00;
	}

	//first file entry - points to nothing. this is the volume label
	String volumelabel = "IANROCKSF32";
	System.arraycopy(volumelabel.getBytes(),0,rootdir,0,11);
	rootdir[11]=(byte) 0x08;

	//first real file entry
	int startByte = 32;
	String filename = source.getName();
	pattern = Pattern.compile("(\\w*).(\\w*)");
        matcher = pattern.matcher(filename);
	matcher.find();
	String name = matcher.group(1);
	String ext = matcher.group(2);

//	System.out.println(name +"." + ext + ". length is " + name.length());

	//put filename into file entry
	System.arraycopy(name.getBytes(),0,rootdir,startByte,name.length());
	//	\w{0,8}.\w{1,3}
	//put in blank space after filename if it is less than 8 characters
	for (int i =name.length(); i< 8;i++){
	    rootdir[startByte+i]=(byte) 0x20;
	}
	System.arraycopy(ext.getBytes(),0,rootdir,startByte + 8,ext.length());
	rootdir[startByte+11]=(byte) 0x20;  //Attrib Byte

	//put in starting cluster (high 2 bytes)
	System.arraycopy(toByteArray((rootSizeClusters+2)>>>16,2),0,rootdir,startByte + 20,2);

	//put in starting cluster (low 2 bytes)
	System.arraycopy(toByteArray((rootSizeClusters+2) & 0xFFFF,2),0,rootdir,startByte + 26,2);

	//put in file size in bytes 
	System.arraycopy(toByteArray(raf.length(),4),0,rootdir,startByte + 28,4);

	//*************************************************************************************
        //Pad out the first 32 sectors and include copy of bootsector and FSINFO sector
	empty = new byte[512];
	for(int i=0;i<512;i++){
		empty[i]=(byte) 0x00;
	}
	start = new byte[512*95];
	for(int i=1;i<95;i++){
		System.arraycopy(empty,0,start,i*512,512);
	}
	System.arraycopy(MBR,0,start,0,512);
	System.arraycopy(header,0,start,63*512,512);
	System.arraycopy(header2,0,start,64*512,512);
	System.arraycopy(header,0,start,69*512,512);
	System.arraycopy(header2,0,start,70*512,512);
    }

    public void dumpChanges(DataOutput output) throws IOException
    {}

    public void loadChanges(DataInput input) throws IOException
    {}

    //Convert Int to byte[] of certain length
    public static byte[] toByteArray(int n, int l)
    {
	byte[] input = toByteArray(n);
	byte[] answer = new byte[l];
	if(input.length>l)
	    System.arraycopy(input,0,answer,0,l);
	else
        {
	    for(int i=0;i<l;i++)
		answer[i]=(byte)0x00;
	    System.arraycopy(input,0,answer,0,input.length);
	}

	return answer;
    }

    //Convert Int to byte[] with shifting
    public static byte[] toByteArray(int n)
    {
	long length =  Long.toHexString(n).length();
	byte[] answer = new byte[(int) length];
	byte[] finalanswer;

	for(int i = 0;i<length/2+.5;i++)
	    answer[i]=(byte) ((n >>> 8*i) & 0xFF);

	int i =answer.length -1;
	while(answer[i]==(byte)0xFF & i!=0)
	    i--;
       
	finalanswer = new byte[i+1];
	System.arraycopy(answer,0,finalanswer,0,i+1);
	return finalanswer;
    }

    //Convert Long to byte[] of certain length
    public static byte[] toByteArray(long n, int l)
    {
	byte[] input = toByteArray(n);
	byte[] answer = new byte[l];
	if(input.length>l)
	    System.arraycopy(input,0,answer,0,l);
	else 
        {
	    for(int i=0;i<l;i++)
		answer[i]=(byte)0x00;
	    System.arraycopy(input,0,answer,0,input.length);
	}
	return answer;
    }

    //Convert Long to byte[] with shifting
    public static byte[] toByteArray(long n)
    {
	long length =  Long.toHexString(n).length();
	byte[] answer = new byte[(int) length];
	byte[] finalanswer;

	for(int i = 0;i<length/2+.5;i++)
	    answer[i]=(byte) ((n >>> 8*i) & 0xFF);

	int i =answer.length -1;
	while(answer[i]==(byte)0xFF & i!=0)
	    i--;

	finalanswer = new byte[i+1];
	System.arraycopy(answer,0,finalanswer,0,i+1);
	return finalanswer;
    }

    //reverse an array of bytes
    public byte[] reverse(byte[] input)
    {
	byte[] output;
	output = new byte[input.length];
	for(int i=0;i<input.length;i++){
	    output[i]=input[input.length-i-1];
	}
	return output;
    }

    public void close()
    {
        try
        {
            raf.close();
        }
        catch (Exception e) {}
    }

    //READ up to 16 sectors at a time
    public int read(long sectorNumber, byte[] buffer, int size)
    {
	//	System.out.println("Reading Sector number " + sectorNumber + " and size = " + size);

        if (sectorNumber < 95){	// Initial sectors
	    System.arraycopy(start, (int) sectorNumber*512, buffer, 0, size*512);
	    return 0;
	}else if (sectorNumber < 95+2*sectorsPerFAT & sectorNumber>94){	// FAT sectors
	    
	    System.arraycopy(FAT, (int) ((sectorNumber-95) % sectorsPerFAT)*512, buffer, 0, size*512);
	    return 0;
	}else if ((sectorNumber > fileSizeSectors +rootSize+2*sectorsPerFAT+95-1) & (sectorNumber < totalSectors +1)){
	    
	    for (int i = 0;i<size;i++){
		System.arraycopy(empty,0,buffer,512*i,512);
	    }
	    return 0;
	}else if (sectorNumber >  totalSectors){
	    return -1;
	}else if ((sectorNumber > 95 + 2*sectorsPerFAT-1) & (sectorNumber < 95 + 2*sectorsPerFAT + rootSizeClusters*sectorsPerCluster)){//root folder
	    System.out.println("reading root sector...");
	    System.arraycopy(rootdir,0,buffer,0,size*512);
	    return 0;
	}

	//if (sectorNumber > 95+2*sectorsPerFAT & sectorNumber < totalSectors){
	    // data
	try
	    {
		long offset =  (sectorNumber - 95-2*sectorsPerFAT) * 512;
		raf.seek(offset);
		int len = Math.min(size*512, (int) (raf.length() - offset));
		raf.read(buffer, 0, len);
		return 0;
	    }
	catch (Exception e)
	    {
		e.printStackTrace();
		return -1;
	    }

    }

    //write sectors
    public int write(long sectorNumber, byte[] buffer, int size)
    {
	System.out.println("Write attempt");
        if (sectorNumber <95)
        {
            System.arraycopy(buffer, 0, start, (int) sectorNumber*512, size*512);
            return 0;
        }else if (sectorNumber < 95+2*sectorsPerFAT & sectorNumber>94)
        {
            System.arraycopy(buffer, 0,FAT, (int) ((sectorNumber-95) % sectorsPerFAT),  size*512);
            return 0;
        }

	if (sectorNumber > totalSectors){
	    return -1;
	}

        try
        {
            long offset = (sectorNumber - 95-2*sectorsPerFAT-rootSize) * 512;
            raf.seek(offset);
            int len = Math.min(size*512, (int) (raf.length() - offset));
            raf.write(buffer, 0, len);
            return 0;
        }
        catch (Exception e)
        {
            e.printStackTrace();
            return -1;
        }

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

    public String getImageFileName()
    {
        return source.getAbsolutePath();
    }
}





