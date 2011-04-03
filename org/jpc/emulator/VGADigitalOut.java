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

package org.jpc.emulator;

import java.io.*;
import org.jpc.output.*;

public class VGADigitalOut implements SRDumpable
{
    private int width;
    private int height;
    private int dirtyXMin;
    private int dirtyXMax;
    private int dirtyYMin;
    private int dirtyYMax;
    private int[] buffer;
    private int[] palette;
    private byte[] pBuffer;
    private boolean paletteValid;
    private boolean paletteChanged;
    private long rateNum;
    private long rateDenum;
    private OutputChannelVideo chan;

    public void holdOutput(long timeNow)
    {
        if(paletteValid)
            chan.addFrameVideo(timeNow, (short)width, (short)height, buffer, palette, pBuffer, rateNum, rateDenum);
        else
            chan.addFrameVideo(timeNow, (short)width, (short)height, buffer, null, null, rateNum, rateDenum);
    }

    public void setSink(Output out, String name)
    {
        chan = new OutputChannelVideo(out, name);
    }

    public int getWidth()
    {
        return width;
    }

    public int getHeight()
    {
        return height;
    }

    public int getDirtyXMin()
    {
        return dirtyXMin;
    }

    public int getDirtyXMax()
    {
        return dirtyXMax;
    }

    public int getDirtyYMin()
    {
        return dirtyYMin;
    }

    public int getDirtyYMax()
    {
        return dirtyYMax;
    }

    public int[] getBuffer()
    {
        return buffer;
    }

    public int[] getPalette()
    {
        if(paletteValid)
            return palette;
        else
            return null;
    }

    public byte[] getPBuffer()
    {
        if(paletteValid)
            return pBuffer;
        else
            return null;
    }

    public long getRateNum()
    {
        return rateNum;
    }

    public long getRateDenum()
    {
        return rateDenum;
    }

    public void setParameters(long num, long denum)
    {
        rateNum = num;
        rateDenum = denum;
    }

    public void setPaletted(boolean paletted)
    {
        paletteValid = paletted;
    }

    public void setPaletteChanged(boolean changed)
    {
        paletteChanged = changed;
    }

    public boolean getPaletteChanged()
    {
        return paletteChanged;
    }

    public void dumpStatusPartial(StatusDumper output)
    {
        output.println("\twidth " + width + " height " + height + " paletteValid " + paletteValid);
        output.println("\trate " + rateNum + "/" + rateDenum + " paletteChanged " + paletteChanged);
        output.println("\tdirty area: (" + dirtyXMin + "," + dirtyYMin + ")-(" + dirtyXMax + "," + dirtyYMax + ")");
    }

    public void dumpStatus(StatusDumper output)
    {
        if(output.dumped(this))
            return;

        output.println("#" + output.objectNumber(this) + ": VGADigitalOut:");
        dumpStatusPartial(output);
        output.endObject();
    }

    public void dumpSRPartial(SRDumper output) throws IOException
    {
        output.dumpInt(width);
        output.dumpInt(height);
        output.dumpInt(dirtyXMin);
        output.dumpInt(dirtyXMax);
        output.dumpInt(dirtyYMin);
        output.dumpInt(dirtyYMax);
        output.dumpArray(buffer);
        output.dumpObject(chan);
        output.dumpBoolean(paletteValid);
        if(paletteValid) {
            output.dumpBoolean(paletteChanged);
            output.dumpArray(palette);
            output.dumpArray(pBuffer);
        }
    }

    public VGADigitalOut(SRLoader input) throws IOException
    {
        input.objectCreated(this);
        width = input.loadInt();
        height = input.loadInt();
        dirtyXMin = input.loadInt();
        dirtyXMax = input.loadInt();
        dirtyYMin = input.loadInt();
        dirtyYMax = input.loadInt();
        buffer = input.loadArrayInt();
        chan = (OutputChannelVideo)input.loadObject();
        paletteValid = false;
        palette = new int[256];
        pBuffer = new byte[width * height];
        if(input.objectEndsHere())
            return;
        paletteValid = input.loadBoolean();
        if(!paletteValid)
            return;
        paletteChanged = input.loadBoolean();
        palette = input.loadArrayInt();
        pBuffer = input.loadArrayByte();
    }

    public VGADigitalOut()
    {
        buffer = new int[1];
        chan = null;
        paletteValid = false;
        palette = new int[256];
        pBuffer = new byte[1];
    }

    public int rgbToPixel(int red, int green, int blue)
    {
        return ((0xFF & red) << 16) | ((0xFF & green) << 8) | (0xFF & blue);
    }

    public void resizeDisplay(int _width, int _height)
    {
        int allocSize = _width * _height;
        if(allocSize == 0) allocSize = 1;

        buffer = new int[allocSize];
        pBuffer = new byte[allocSize];
        width = _width;
        height = _height;
        // Mark the entiere display as dirty.
        dirtyXMin = 0;
        dirtyYMin = 0;
        dirtyXMax = width;
        dirtyYMax = height;
    }

    public int[] getDisplayBuffer()
    {
        return buffer;
    }

    public final void dirtyDisplayRegion(int x, int y, int w, int h)
    {
        dirtyXMin = Math.min(x, dirtyXMin);
        dirtyXMax = Math.max(x+w, dirtyXMax);
        dirtyYMin = Math.min(y, dirtyYMin);
        dirtyYMax = Math.max(y+h, dirtyYMax);
    }

    public void resetDirtyRegion()
    {
        dirtyXMin = width;
        dirtyYMin = height;
        dirtyXMax = 0;
        dirtyYMax = 0;
    }
}
