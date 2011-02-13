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
import org.jpc.emulator.SRLoader;
import org.jpc.emulator.SRDumper;
import org.jpc.emulator.StatusDumper;
import org.jpc.emulator.SRDumpable;
import org.jpc.images.BaseImage;
import org.jpc.images.ImageID;

public class DiskImage implements SRDumpable
{
    private boolean readOnly;
    private boolean busy;
    private boolean used;
    private BaseImage.Type type;
    private long totalSectors;
    private int heads;
    private int cylinders;
    private int sectors;
    private String imageFileName;
    private String imageName;
    private int[] sectorOffsetMap;
    private byte[][] copyOnWriteData;
    private byte[] blankPage;
    private ImageID diskID;
    private RandomAccessFile image;
    private static ImageLibrary library;

    public static void setLibrary(ImageLibrary lib)
    {
        library = lib;
    }

    public static ImageLibrary getLibrary()
    {
        return library;
    }

    public void finalize()
    {
        try {
            if(image != null)
                image.close();
        } catch(Throwable e) {
        }
    }

    public void dumpStatusPartial(StatusDumper output)
    {
        output.println("\treadOnly " + readOnly + " busy " + busy + " used " + used + " type " + type);
        output.println("\ttotalSectors " + totalSectors + " heads " + heads + " cylinders " + cylinders);
        output.println("\tsectors " + sectors + " imageFileName " + imageFileName);
        output.println("\timageName " + imageName);
    }

    public void dumpStatus(StatusDumper output)
    {
        if(output.dumped(this))
            return;

        output.println("#" + output.objectNumber(this) + ": DiskImage:");
        dumpStatusPartial(output);
        output.endObject();
    }

    public void dumpSRPartial(SRDumper output) throws IOException
    {
        System.err.println("Informational: Dumping disk image...");
        output.dumpObject(diskID);
        int cowEntries = 0;
        if(copyOnWriteData != null)
            for(int i = 0; i < copyOnWriteData.length; i++) {
                if(copyOnWriteData[i] == null)
                    continue;
                cowEntries++;
            }
        output.dumpInt(cowEntries);
        if(copyOnWriteData != null)
            for(int i = 0; i < copyOnWriteData.length; i++) {
                if(copyOnWriteData[i] == null)
                    continue;
                output.dumpInt(i);
                output.dumpArray(copyOnWriteData[i]);
            }
        System.err.println("Informational: Disk image dumped (" + cowEntries + " cow entries).");
        output.dumpBoolean(used);
        output.dumpBoolean(busy);
        output.dumpString(imageName);
    }

    private void commonConstructor(String fileName) throws IOException
    {
        ImageMaker.ParsedImage p = new ImageMaker.ParsedImage(fileName);
        if(p.typeCode == 0) {
            type = BaseImage.Type.FLOPPY;
            readOnly = false;
        } else if(p.typeCode == 1) {
            type = BaseImage.Type.HARDDRIVE;
            readOnly = false;
        } else if(p.typeCode == 2) {
            type = BaseImage.Type.CDROM;
            readOnly = true;
        } else
            throw new IOException("Can't load " + fileName + ": Image of unknown type!");

        imageName = fileName;
        busy = false;
        used = false;
        totalSectors = p.totalSectors;
        heads = p.sides;
        cylinders = p.tracks;
        sectors = p.sectors;
        imageFileName = fileName;
        sectorOffsetMap = p.sectorOffsetMap;
        if(type != BaseImage.Type.CDROM)
            copyOnWriteData = new byte[(int)totalSectors][];
        else {
            //Parameters from original JPC code...
            cylinders = 2;
            heads = 16;
            sectors = 63;
        }
        diskID = p.diskID;
        blankPage = new byte[512];
        image = new RandomAccessFile(fileName, "r");
    }

    public ImageID getImageID()
    {
        return diskID;
    }

    public DiskImage(SRLoader input) throws IOException
    {
        input.objectCreated(this);
        ImageID id = (ImageID)input.loadObject();
        String fileName = library.lookupFileName(id);
        if(fileName == null)
            throw new IOException("No disk with ID " + id + " found.");
        commonConstructor(fileName);
        int cowEntries = input.loadInt();
        for(int i = 0; i < cowEntries; i++) {
            int j = input.loadInt();
            copyOnWriteData[j] = input.loadArrayByte();
        }
        used = input.loadBoolean();
        busy = input.loadBoolean();
        imageName = input.loadString();
    }

    public DiskImage(String diskName, boolean dummy) throws IOException
    {
        String fileName = library.searchFileName(diskName);
        if(fileName == null)
            throw new IOException(diskName + ": No such image in Library.");
        commonConstructor(fileName);
    }

    public int read(long sectorNum, byte[] buffer, int size)
    {
        if(sectorNum + size > totalSectors) {
            System.err.println("Warning: Trying to read invalid sector range " + sectorNum + "-" + (sectorNum + size - 1) +  ".");
            return -1;
        }

        for(int i = 0; i < size; i++) {
            if(copyOnWriteData != null && copyOnWriteData[(int)sectorNum] != null) {
                //Copy On Write data takes percedence.
                System.arraycopy(copyOnWriteData[(int)sectorNum], 0, buffer, 512 * i, 512);
            } else if(sectorNum < sectorOffsetMap.length && sectorOffsetMap[(int)sectorNum] > 0) {
                //Found from image.
                try {
                    image.seek(sectorOffsetMap[(int)sectorNum]);
                    image.read(buffer, 512 * i, 512);
                } catch(IOException e) {
                    System.err.println("Error: Failed to read sector " + sectorNum + ".");
                    return -1;
                }
            } else {
                //Null page.
                System.arraycopy(blankPage, 0, buffer, 512 * i, 512);
            }
            sectorNum++;
        }
        return 0;
    }

    public int write(long sectorNum, byte[] buffer, int size)
    {
        if(readOnly)
            return -1;      //Error, write to write-protected disk.

        if(sectorNum + size > totalSectors) {
            System.err.println("Warning: Trying to write invalid sector range " + sectorNum + "-" + (sectorNum + size - 1) +  ".");
            return -1;
        }

        for(int i = 0; i < size; i++) {
             if(copyOnWriteData[(int)sectorNum] == null)
                 copyOnWriteData[(int)sectorNum] = new byte[512];
             System.arraycopy(buffer, 512 * i, copyOnWriteData[(int)sectorNum], 0, 512);
             sectorNum++;
        }
        return 512 * size;
    }

    public void use() throws IOException
    {
        if(busy)
            throw new IOException("Trying to use busy disk!");
        busy = true;
        used = true;
    }

    public void unuse()
    {
        busy = false;
    }

    public boolean isReadOnly()
    {
        return readOnly;
    }

    public BaseImage.Type getType()
    {
        return type;
    }

    public long getTotalSectors()
    {
        return totalSectors;
    }

    public int getHeads()
    {
        return heads;
    }

    public int getCylinders()
    {
        return cylinders;
    }

    public int getSectors()
    {
        return sectors;
    }

    public String getImageFileName()
    {
        return imageFileName;
    }

    public void setWP(boolean newState)
    {
        if(type == BaseImage.Type.FLOPPY)
            readOnly = newState;
    }

    public String getName()
    {
        return imageName;
    }

    public void setName(String name)
    {
        imageName = name;
    }
}
