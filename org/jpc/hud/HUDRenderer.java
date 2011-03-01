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
package org.jpc.hud;

import java.util.*;

public class HUDRenderer
{
    int[] backgroundBuffer;
    int elementsAllocated;
    int backgroundWidth;
    int backgroundHeight;
    int flags;
    volatile int lightAmp;
    volatile int gapLeft;
    volatile int gapTop;
    volatile int gapBottom;
    volatile int gapRight;
    List<RenderObject> renderObjects;

    public HUDRenderer(int _flags)
    {
        renderObjects = new LinkedList<RenderObject>();
        lightAmp = 1;
        flags = _flags;
    }

    public synchronized void setBackground(int[] bg, int w, int h)
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

    public synchronized int getRenderWidth()
    {
        return gapLeft + backgroundWidth + gapRight;
    }

    public synchronized int getRenderHeight()
    {
        return gapTop + backgroundHeight + gapBottom;
    }

    public synchronized void setLeftGap(int _flags, int gap)
    {
        if(((_flags & flags)) != flags)
            return;
        if(gap > 0) {
            gapLeft = gap;
        } else
            gapLeft = 0;
    }

    public synchronized void setTopGap(int _flags, int gap)
    {
        if(((_flags & flags)) != flags)
            return;
        if(gap > 0) {
            gapTop = gap;
        } else
            gapTop = 0;
    }

    public synchronized void setRightGap(int _flags, int gap)
    {
        if(((_flags & flags)) != flags)
            return;
        if(gap > 0) {
            gapRight = gap;
        } else
            gapRight = 0;
    }

    public synchronized void setBottomGap(int _flags, int gap)
    {
        if(((_flags & flags)) != flags)
            return;
        if(gap > 0) {
            gapBottom = gap;
        } else
            gapBottom = 0;
    }

    public synchronized void setLightAmplification(int factor)
    {
        lightAmp = factor;
    }

    public synchronized int[] getFinishedAndReset()
    {
        int[] ret = null;
        int w = getRenderWidth();
        int h = getRenderHeight();
        if(w * h > 0)
            ret = new int[w * h];

        if(lightAmp == 1) {
            for(int y = 0; y < backgroundHeight; y++)
                System.arraycopy(backgroundBuffer, y * backgroundWidth, ret, (y + gapTop) * w + gapLeft,
                    backgroundWidth);
        } else {
            for(int y = 0; y < backgroundHeight; y++)
                for(int x = 0; x < backgroundWidth; x++)
                    ret[(y + gapTop) * w + gapLeft + x] = backgroundBuffer[y * backgroundWidth + x] * lightAmp;
        }

        for(RenderObject obj : renderObjects)
            if(ret != null)
                obj.render(ret, w, h);
        renderObjects.clear();

        gapLeft = gapRight = gapTop = gapBottom = 0;
        return ret;
    }

    public synchronized void addObject(int _flags, RenderObject o)
    {
        if(((_flags & flags)) != flags)
            return;
        renderObjects.add(o);
    }
}
