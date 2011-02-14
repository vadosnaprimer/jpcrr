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

public class DiskImage implements BaseImage
{
    private BaseImage.Type type;
    private long totalSectors;
    private int heads;
    private int cylinders;
    private int sectors;
    private int[] sectorOffsetMap;
    private byte[] blankPage;
    private ImageID diskID;
    private RandomAccessFile image;
    private static ImageLibrary library;

    public Type getType()
    {
        return type;
    }

    public int getTracks()
    {
        return cylinders;
    }

    public int getSectors()
    {
        return sectors;
    }

    public int getSides()
    {
        return heads;
    }

    public long getTotalSectors()
    {
        return totalSectors;
    }

    public ImageID getID()
    {
        return diskID;
    }

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
        output.println("\ttype " + type);
        output.println("\ttotalSectors " + totalSectors + " heads " + heads + " cylinders " + cylinders);
        output.println("\tsectors " + sectors);
    }

    public void dumpStatus(StatusDumper output)
    {
        if(output.dumped(this))
            return;

        output.println("#" + output.objectNumber(this) + ": DiskImage:");
        dumpStatusPartial(output);
        output.endObject();
    }

    private void commonConstructor(String fileName) throws IOException
    {
        ImageMaker.ParsedImage p = new ImageMaker.ParsedImage(fileName);
        if(p.typeCode == 0) {
            type = BaseImage.Type.FLOPPY;
        } else if(p.typeCode == 1) {
            type = BaseImage.Type.HARDDRIVE;
        } else if(p.typeCode == 2) {
            type = BaseImage.Type.CDROM;
        } else
            throw new IOException("Can't load " + fileName + ": Image of unknown type!");

        totalSectors = p.totalSectors;
        heads = p.sides;
        cylinders = p.tracks;
        sectors = p.sectors;
        sectorOffsetMap = p.sectorOffsetMap;
        if(type == BaseImage.Type.CDROM) {
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

    public DiskImage(ImageID diskName) throws IOException
    {
        String fileName = library.searchFileName(diskName.getIDAsString());
        if(fileName == null)
            throw new IOException(diskName + ": No such image in Library.");
        commonConstructor(fileName);
    }

    public void read(long sectorNum, byte[] buffer, long size) throws IOException
    {
        if(sectorNum + size > totalSectors)
            throw new IOException("Trying to read invalid sector range " + sectorNum + "-" +
                (sectorNum + size - 1) +  ".");

        for(int i = 0; i < size; i++) {
            if(sectorNum < sectorOffsetMap.length && sectorOffsetMap[(int)sectorNum] > 0) {
                //Found from image.
                image.seek(sectorOffsetMap[(int)sectorNum]);
                image.read(buffer, 512 * i, 512);
            } else {
                //Null page.
                System.arraycopy(blankPage, 0, buffer, 512 * i, 512);
            }
            sectorNum++;
        }
    }
}
