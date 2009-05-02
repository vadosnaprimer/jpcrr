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

public class RawBlockDevice implements BlockDevice
{
    private static final String formatName = "raw";

    protected long totalSectors;
    protected boolean readOnly;
    protected boolean inserted;
    protected boolean removable;
    protected boolean locked;

    protected boolean bootSectorEnabled;
    protected byte[] bootSectorData;
    protected SeekableIODevice data;

    protected int cylinders;
    protected int heads;
    protected int sectors;
    private Magic magic;

    public RawBlockDevice()
    {
        magic = new Magic(Magic.RAW_BLOCK_DEVICE_MAGIC_V1);
    }

    public RawBlockDevice(SeekableIODevice data)
    {
        this.data = data;
        magic = new Magic(Magic.RAW_BLOCK_DEVICE_MAGIC_V1);

        byte[] buffer = new byte[512];
        try {
            totalSectors = data.length() / 512;
            data.seek(0);
            if (data.read(buffer, 0, 512) != 512)
                System.err.println("Not big enough image file");
        } catch (IOException e) {
            System.err.println("RawBlockDevice: Error in File Read: " + e);
        }
        if ((buffer[510] == (byte)0x55) && (buffer[511] == (byte)0xaa)) {
            for (int i = 0; i < 4; i++) {
                int numberSectors = (buffer[0x1be + (16*i) + 12] & 0xff)
                    | ((buffer[0x1be + (16*i) + 13] << 8) & 0xff00)
                    | ((buffer[0x1be + (16*i) + 14] << 16) & 0xff0000)
                    | ((buffer[0x1be + (16*i) + 15] << 24) & 0xff000000);
                if (((0xff & buffer[0x1be + (16*i) + 5]) & numberSectors) != 0) {
                    heads = 1 + (buffer[0x1be + (16*i) + 5] & 0xff);
                    sectors = buffer[0x1be + (16*i) + 6] & 0x3f;
                    if (sectors == 0) continue;
                    cylinders = (int)(totalSectors / (heads * sectors));
                    if (cylinders < 1 || cylinders > 16383) {
                        cylinders = 0;
                        continue;
                    }
                }
            }
        }

        if (cylinders == 0) { //no geometry information?
            //We'll use a standard LBA geometry
            cylinders = (int)(totalSectors / (16 * 63));
            if (cylinders > 16383)
                cylinders = 16383;
            else if (cylinders < 2)
                cylinders = 2;

            heads = 16;
            sectors = 63;
            System.err.println("No Geometry Information, Guessing CHS = " + cylinders + ":" + heads + ":" + sectors);
        }
    }

    public String getImageFileName()
    {
        return data.toString();
    }

    public void close()
    {
    }

    public int read(long sectorNumber, byte[] buffer, int size)
    {
        try
        {
            data.seek((int) (sectorNumber * 512));
            int pos = 0;
            int toRead = Math.min(buffer.length, 512*size);
            while (true)
            {
                int read = data.read(buffer, pos, toRead - pos);
                if ((read < 0) || (pos == toRead))
                    return pos;

                pos += read;
            }
        } catch (IOException e) {
            System.err.println("IO Error Reading From " + data.toString());
            e.printStackTrace();
            return -1;
        }
    }

    public int write(long sectorNumber, byte[] buffer, int size)
    {
        try
        {
            data.seek((int) (sectorNumber * 512));
            data.write(buffer, 0, size * 512);
        } catch (IOException e) {
            System.err.println("IO Error Writing To " + data.toString());
            e.printStackTrace();
            return -1;
        }
        return 0;
    }

    public boolean inserted()
    {
        return (data != null);
    }

    public boolean locked()
    {
        return false;
    }

    public boolean readOnly()
    {
        return false;
    }

    public void setLock(boolean locked)
    {
    }

    /* FIXME: Implement these. */
    public void dumpState(DataOutput output) throws IOException
    {
        magic.dumpState(output);
    }

    public void loadState(DataInput input) throws IOException
    {
        magic.loadState(input);
    }

    public long getTotalSectors()
    {
        return totalSectors;
    }

    public int cylinders()
    {
        return cylinders;
    }

    public int heads()
    {
        return heads;
    }
    public int sectors()
    {
        return sectors;
    }

    public int type()
    {
        return BlockDevice.TYPE_HD;
    }

    public void configure(String specs) throws Exception
    {
        data.configure(specs);
    }

    public int length()
    {
        return data.length();
    }
}
