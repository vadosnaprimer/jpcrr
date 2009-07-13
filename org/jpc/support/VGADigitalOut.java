/*
    JPC: A x86 PC Hardware Emulator for a pure Java Virtual Machine
    Release Version 2.0

    A project from the Physics Dept, The University of Oxford

    Copyright (C) 2007 Isis Innovation Limited
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

    Details (including contact information) can be found at:

    www.physics.ox.ac.uk/jpc
*/

package org.jpc.support;

import java.io.*;

public class VGADigitalOut implements org.jpc.SRDumpable
{
    private int width;
    private int height;
    private int dirtyXMin;
    private int dirtyXMax;
    private int dirtyYMin;
    private int dirtyYMax;
    private int[] buffer;
    private boolean writing;

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

    public void dumpStatusPartial(org.jpc.support.StatusDumper output)
    {
        output.println("\twidth " + width + " height " + height + " writing " + writing);
        output.println("\tdirty area: (" + dirtyXMin + "," + dirtyYMin + ")-(" + dirtyXMax + "," + dirtyYMax + ")");
    }

    public void dumpStatus(org.jpc.support.StatusDumper output)
    {
        if(output.dumped(this))
            return;

        output.println("#" + output.objectNumber(this) + ": VGADigitalOut:");
        dumpStatusPartial(output);
        output.endObject();
    }

    public void dumpSRPartial(org.jpc.support.SRDumper output) throws IOException
    {
        output.dumpBoolean(writing);
        output.dumpInt(width);
        output.dumpInt(height);
        output.dumpInt(dirtyXMin);
        output.dumpInt(dirtyXMax);
        output.dumpInt(dirtyYMin);
        output.dumpInt(dirtyYMax);
        output.dumpArray(buffer);
    }

    public VGADigitalOut(org.jpc.support.SRLoader input) throws IOException
    {
        input.objectCreated(this);
        writing = input.loadBoolean();
        width = input.loadInt();
        height = input.loadInt();
        dirtyXMin = input.loadInt();
        dirtyXMax = input.loadInt();
        dirtyYMin = input.loadInt();
        dirtyYMax = input.loadInt();
        buffer = input.loadArrayInt();
    }

    public VGADigitalOut()
    {
        writing = true;
        buffer = new int[1];
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

    public synchronized boolean waitReadable()
    {
        while(writing) {
            try {
                wait();
            } catch(InterruptedException e) {
                return !writing;
            }
        }
        return true;
    }

    public synchronized void waitWritable()
    {
        while(!writing) {
            try {
                wait();
            } catch(InterruptedException e) {
            }
        }
    }

    public synchronized void endReadable()
    {
        if(writing)
            return;
        writing = true;
        notifyAll();
    }

    public synchronized void endWritable()
    {
        if(!writing)
            return;
        writing = false;
        notifyAll();
    }
}
