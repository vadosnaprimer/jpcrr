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
package org.jpc.pluginsaux;

import java.util.*;

public class HUDRenderer
{
    int[] backgroundBuffer;
    int elementsAllocated;
    int backgroundWidth;
    int backgroundHeight;
    volatile int gapLeft;
    volatile int gapTop;
    volatile int gapBottom;
    volatile int gapRight;
    List<RenderObject> renderObjects;

    private abstract class RenderObject
    {
        abstract void render(int[] buffer, int w, int h);
    }

    public HUDRenderer()
    {
        renderObjects = new LinkedList<RenderObject>();
    }

    public void setBackground(int[] bg, int w, int h)
    {
        if(elementsAllocated < w * h) {
            backgroundBuffer = new int[w * h];
            elementsAllocated = w * h;
        }
        if(w * h > 0)
            System.arraycopy(bg, 0, backgroundBuffer, 0, w * h);
        backgroundWidth = w;
        backgroundHeight = h;
    }

    public int getRenderWidth()
    {
        return gapLeft + backgroundWidth + gapRight;
    }

    public int getRenderHeight()
    {
        return gapTop + backgroundHeight + gapBottom;
    }

    public void setLeftGap(int gap)
    {
        if(gap > 0) {
            gapLeft = gap;
        } else
            gapLeft = 0;
    }

    public void setTopGap(int gap)
    {
        if(gap > 0) {
            gapTop = gap;
        } else
            gapTop = 0;
    }

    public void setRightGap(int gap)
    {
        if(gap > 0) {
            gapRight = gap;
        } else
            gapRight = 0;
    }

    public void setBottomGap(int gap)
    {
        if(gap > 0) {
            gapBottom = gap;
        } else
            gapBottom = 0;
    }

    public int[] getFinishedAndReset()
    {
        int[] ret = null;
        int w = getRenderWidth();
        int h = getRenderHeight();
        if(w * h > 0)
            ret = new int[w * h];

        for(int y = 0; y < backgroundHeight; y++)
            System.arraycopy(backgroundBuffer, y * backgroundWidth, ret, (y + gapTop) * w + gapLeft,
                backgroundWidth);

        for(RenderObject obj : renderObjects)
            obj.render(ret, w, h);
        renderObjects.clear();

        gapLeft = gapRight = gapTop = gapBottom = 0;
        return ret;
    }

    private class WhiteSolidBox extends RenderObject
    {
        int x;
        int y;
        int w;
        int h;
        WhiteSolidBox(int _x, int _y, int _w, int _h)
        {
            x = _x;
            y = _y;
            w = _w;
            h = _h;
        }

        void render(int[] buffer, int bw, int bh)
        {
            for(int j = y; j < y + h && j < bh; j++)
                for(int i = x; i < x + w && i < bw; i++)
                    buffer[j * bw + i] = 0xFFFFFF;
        }
    }

    public void whiteSolidBox(int x, int y, int w, int h)
    {
        renderObjects.add(new WhiteSolidBox(x, y, w, h));
    }
}
