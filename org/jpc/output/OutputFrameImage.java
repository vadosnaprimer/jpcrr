/*
    JPC-RR: A x86 PC Hardware Emulator
    Release 1

    Copyright (C) 2007-2009 Isis Innovation Limited
    Copyright (C) 2009-2010 H. Ilari Liusvaara

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

package org.jpc.output;

import java.util.*;
import java.util.zip.*;

public class OutputFrameImage extends OutputFrame
{
    private short width;
    private short height;
    private int[] imageData;

    public OutputFrameImage(long timeStamp, short w, short h, int[] i)
    {
        super(timeStamp, (byte)1);
        width = w;
        height = h;
        imageData = i;
    }

    public int getWidth()
    {
        return width;
    }

    public int getHeight()
    {
        return height;
    }

    public int[] getImageData()
    {
        return imageData;
    }

    protected byte[] dumpInternal()
    {
        int outputLen = 4;
        int position = 0;
        int pixels = 0;
        boolean finished = false;
        List<byte[]> hunks = new LinkedList<byte[]>();
        byte[] hHunk = new byte[4];
        hHunk[0] = (byte)((width >> 8) & 0xFF);
        hHunk[1] = (byte)(width & 0xFF);
        hHunk[2] = (byte)((height >> 8) & 0xFF);
        hHunk[3] = (byte)(height & 0xFF);
        hunks.add(hHunk);
        pixels = width * height;

        Deflater d = new Deflater(Deflater.BEST_COMPRESSION);
        byte[] x = new byte[256];
        byte[] z = new byte[4096];
        while(!d.finished()) {
            int r = d.deflate(x);
            if(r > 0) {
                outputLen += r;
                byte[] y = new byte[r];
                System.arraycopy(x, 0, y, 0, r);
                hunks.add(y);
            } else if(!finished) {
                int op = 0;
                while(position < pixels && op < z.length) {
                    z[op + 0] = (byte)((imageData[position] >> 16) & 0xFF);
                    z[op + 1] = (byte)((imageData[position] >> 8) & 0xFF);
                    z[op + 2] = (byte)((imageData[position]) & 0xFF);
                    z[op + 3] = (byte)0;
                    op += 4;
                    position++;
                }
                if(op > 0)
                    d.setInput(z, 0, op);
                else {
                    d.finish();
                    finished = true;
                }
            }
        }
        byte[] out = new byte[outputLen];
        position = 0;
        for(byte[] hunk : hunks) {
            System.arraycopy(hunk, 0, out, position, hunk.length);
            position += hunk.length;
        }
        return out;
    }
};
