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
import org.jpc.emulator.PC;
import org.jpc.emulator.memory.PhysicalAddressSpace;

public class HUDRenderer
{
    int[] backgroundBuffer;
    int elementsAllocated;
    int backgroundWidth;
    int backgroundHeight;
    volatile int lightAmp;
    volatile int gapLeft;
    volatile int gapTop;
    volatile int gapBottom;
    volatile int gapRight;
    List<RenderObject> renderObjects;
    volatile int vgaChargenBase;

    private abstract class RenderObject
    {
        abstract void render(int[] buffer, int w, int h);
    }

    public HUDRenderer()
    {
        renderObjects = new LinkedList<RenderObject>();
        lightAmp = 1;
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

    public synchronized void setLeftGap(int gap)
    {
        if(gap > 0) {
            gapLeft = gap;
        } else
            gapLeft = 0;
    }

    public synchronized void setTopGap(int gap)
    {
        if(gap > 0) {
            gapTop = gap;
        } else
            gapTop = 0;
    }

    public synchronized void setRightGap(int gap)
    {
        if(gap > 0) {
            gapRight = gap;
        } else
            gapRight = 0;
    }

    public synchronized void setBottomGap(int gap)
    {
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

    final void renderPixel(int[] buffer, int bw, int bh, int x, int y, boolean state, int fillR, int fillG,
        int fillB, int fillA, int lineR, int lineG, int lineB, int lineA)
    {
        if(x < 0 || y < 0 || x >= bw || y >= bh)
            return;
        int useR = fillR;
        int useG = fillG;
        int useB = fillB;
        int useA = fillA;

        if(state) {
            useR = lineR;
            useG = lineG;
            useB = lineB;
            useA = lineA;
        }
        useR &= 0xFF;
        useG &= 0xFF;
        useB &= 0xFF;
        useA &= 0xFF;

        if(useA == 0) {
            //Nothing to modify.
        } else if(useA == 255) {
            buffer[y * bw + x] = (useR << 16) | (useG << 8) | useB;
        } else {
            int oldpx = buffer[x * bw + y];
            float oldR = (oldpx >>> 16) & 0xFF;
            float oldG = (oldpx >>> 8) & 0xFF;
            float oldB = oldpx & 0xFF;
            float fA = (float)useA / 255;
            useR = (int)(useR * fA + oldR * (1 - fA));
            useG = (int)(useG * fA + oldG * (1 - fA));
            useB = (int)(useB * fA + oldB * (1 - fA));
            buffer[x * bw + y] = (useR << 16) | (useG << 8) | useB;
        }
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
            for(int j = y; j < y + h; j++) {
                if(j < 0 || j >= bh)
                    continue;
                for(int i = x; i < x + w; i++) {
                    if(i < 0 || i >= bw)
                        continue;
                    buffer[j * bw + i] = 0xFFFFFF;
                }
            }
        }
    }

    public synchronized void whiteSolidBox(int x, int y, int w, int h)
    {
        renderObjects.add(new WhiteSolidBox(x, y, w, h));
    }

    private class Box extends RenderObject
    {
        int x;
        int y;
        int w;
        int h;
        int thick;
        int lineR;
        int lineG;
        int lineB;
        int lineA;
        int fillR;
        int fillG;
        int fillB;
        int fillA;
        Box(int _x, int _y, int _w, int _h, int _thick, int lr, int lg, int lb, int la, int fr, int fg, int fb,
            int fa)
        {
            x = _x;
            y = _y;
            w = _w;
            h = _h;
            thick = _thick;
            lineR = lr;
            lineG = lg;
            lineB = lb;
            lineA = la;
            fillR = fr;
            fillG = fg;
            fillB = fb;
            fillA = fa;
        }

        void render(int[] buffer, int bw, int bh)
        {
            for(int j = y; j < y + h && j < bh; j++) {
                if(j < 0 || j >= bh)
                    continue;
                for(int i = x; i < x + w && i < bw; i++) {
                    int dist = i - x;
                    if(j - y < dist)
                        dist = j - y;
                    if(x + w - i - 1 < dist)
                        dist = x + w - i - 1;
                    if(y + h - j - 1 < dist)
                        dist = y + h - j - 1;
                    renderPixel(buffer, bw, bh, i, j, dist < thick, fillR, fillG, fillB, fillA, lineR, lineG,
                        lineB, lineA);
                }
            }
        }
    }

    public synchronized void box(int _x, int _y, int _w, int _h, int _thick, int lr, int lg, int lb, int la, int fr,
        int fg, int fb, int fa)
    {
        renderObjects.add(new Box(_x, _y, _w, _h, _thick, lr, lg, lb, la, fr, fg, fb, fa));
    }

    private class Circle extends RenderObject
    {
        int x;
        int y;
        int r;
        long r2inner;
        long r2outer;
        int lineR;
        int lineG;
        int lineB;
        int lineA;
        int fillR;
        int fillG;
        int fillB;
        int fillA;
        Circle(int _x, int _y, int _r, int _thick, int lr, int lg, int lb, int la, int fr, int fg, int fb,
            int fa)
        {
            x = _x;
            y = _y;
            r = _r;
            r2outer = (long)_r * _r;
            if(_r < _thick)
                r2inner = 0;
            else
                r2inner = (long)(_r - _thick) * (_r - _thick);
            lineR = lr;
            lineG = lg;
            lineB = lb;
            lineA = la;
            fillR = fr;
            fillG = fg;
            fillB = fb;
            fillA = fa;
        }

        void render(int[] buffer, int bw, int bh)
        {
            for(int j = y - r; j < y + r && j < bh; j++) {
                if(j < 0 || j >= bh)
                    continue;
                for(int i = x - r; i < x + r && i < bw; i++) {
                    if(i < 0 || i >= bw)
                        continue;
                    long ox = i - x;
                    long oy = j - y;
                    long d = ox * ox + oy * oy;
                    if(d > r2outer)
                        continue;
                    renderPixel(buffer, bw, bh, i, j, d >= r2inner, fillR, fillG, fillB, fillA, lineR, lineG,
                        lineB, lineA);
                }
            }
        }
    }

    public synchronized void circle(int _x, int _y, int _r, int _thick, int lr, int lg, int lb, int la, int fr,
        int fg, int fb, int fa)
    {
        renderObjects.add(new Circle(_x, _y, _r, _thick, lr, lg, lb, la, fr, fg, fb, fa));
    }

    private class Bitmap extends RenderObject
    {
        private static final int PIXELS_PER_ELEMENT = 31;
        int x;
        int y;
        int w;
        int h;
        int stride;
        int[] bitmapData;
        String vgaChargenString;
        PhysicalAddressSpace vgaChargenFrom;
        int lineR;
        int lineG;
        int lineB;
        int lineA;
        int fillR;
        int fillG;
        int fillB;
        int fillA;

        Bitmap(int _x, int _y, String text, int lr, int lg, int lb, int la, int fr, int fg, int fb,
            int fa, PC fontdata)
        {
            x = _x;
            y = _y;
            lineR = lr;
            lineG = lg;
            lineB = lb;
            lineA = la;
            fillR = fr;
            fillG = fg;
            fillB = fb;
            fillA = fa;
            w = 0;
            h = 0;
            vgaChargenString = text;
            vgaChargenFrom = (PhysicalAddressSpace)fontdata.getComponent(PhysicalAddressSpace.class);
        }

        Bitmap(int _x, int _y, String bmap, int lr, int lg, int lb, int la, int fr, int fg, int fb,
            int fa, boolean dummy)
        {
            int i = 0;
            x = _x;
            y = _y;
            lineR = lr;
            lineG = lg;
            lineB = lb;
            lineA = la;
            fillR = fr;
            fillG = fg;
            fillB = fb;
            fillA = fa;
            w = 0;
            h = 0;
            try {
                w = bmap.charAt(i++);
                if(w > 127)
                    w = (w & 0x7F) | (bmap.charAt(i++) << 7);
                stride = (w + PIXELS_PER_ELEMENT - 1) / PIXELS_PER_ELEMENT;
                int rawbytes = 4 * (w / PIXELS_PER_ELEMENT);
                rawbytes += ((w % PIXELS_PER_ELEMENT) + 7) / 8;
                h = (bmap.length() - i) / rawbytes;
                bitmapData = new int[h * stride + 2];
                for(int j = 0; j < h; j++)
                    for(int k = 0; k < rawbytes; k++)
                        bitmapData[j * stride + k / 4] |= ((int)bmap.charAt(i++) << (8 * (k % 4)));
            } catch(Exception e) {
                System.err.println("Bitmap: Failed to parse bitmap: " + e.getMessage());
                e.printStackTrace();
            }
        }

        Bitmap(int _x, int _y, String bmap, int lr, int lg, int lb, int la, int fr, int fg, int fb,
            int fa)
        {
            x = _x;
            y = _y;
            lineR = lr;
            lineG = lg;
            lineB = lb;
            lineA = la;
            fillR = fr;
            fillG = fg;
            fillB = fb;
            fillA = fa;
            int cx = 0;
            int cy = 0;
            boolean newLine = true;
            for(int i = 0; i < bmap.length(); i++) {
                char ch = bmap.charAt(i);
                switch(ch) {
                case '\r':
                case '\n':
                    if(!newLine)
                        cy++;
                    cx = 0;
                    newLine = true;
                    break;
                default:
                    newLine = false;
                    if(cy >= h)
                        h = cy + 1;
                    if(cx >= w)
                        w = cx + 1;
                    cx++;
                    break;
                }
            }
            stride = (w + PIXELS_PER_ELEMENT - 1) / PIXELS_PER_ELEMENT;
            bitmapData = new int[h * stride + 2];
            cx = 0;
            cy = 0;
            newLine = true;
            for(int i = 0; i < bmap.length(); i++) {
                char ch = bmap.charAt(i);
                switch(ch) {
                case '\r':
                case '\n':
                    if(!newLine)
                        cy++;
                    cx = 0;
                    newLine = true;
                    break;
                case ' ':
                case '.':
                    newLine = false;
                    cx++;
                    break;
                default:
                    bitmapData[cy * stride + cx / PIXELS_PER_ELEMENT] |=
                        (1 << (cx % PIXELS_PER_ELEMENT));
                    newLine = false;
                    cx++;
                    break;
                }
            }
        }


        final void renderPartial(int[] buffer, int bw, int bh, int x, int y, int data)
        {
            for(int i = 0; i < 32; i++)
                renderPixel(buffer, bw, bh, x + 7 - (i % 8), y + (i / 8), ((data >>> i) & 1) != 0, fillR, fillG, fillB,
                    fillA, lineR, lineG, lineB, lineA);
        }

        void render(int[] buffer, int bw, int bh)
        {
            if(vgaChargenFrom != null) {
                if(y < -15 || y >= bh)
                    return;   //Nothing visible.

                int len = vgaChargenString.length();
                for(int i = 0; i < len; i++) {
                    int xbase = x + 8 * i;
                    if(xbase < -7)
                        continue;  //Not visible
                    if(xbase >= bw)
                        break;     //Off the screen.
                    int ch = (int)vgaChargenString.charAt(i) & 0xFF;
                    int charD1 = vgaChargenFrom.getDoubleWord(vgaChargenBase + 16 * ch);
                    int charD2 = vgaChargenFrom.getDoubleWord(vgaChargenBase + 16 * ch + 4);
                    int charD3 = vgaChargenFrom.getDoubleWord(vgaChargenBase + 16 * ch + 8);
                    int charD4 = vgaChargenFrom.getDoubleWord(vgaChargenBase + 16 * ch + 12);
                    renderPartial(buffer, bw, bh, xbase, y, charD1);
                    renderPartial(buffer, bw, bh, xbase, y + 4, charD2);
                    renderPartial(buffer, bw, bh, xbase, y + 8, charD3);
                    renderPartial(buffer, bw, bh, xbase, y + 12, charD4);
                }
            } else {
                if(bitmapData == null)
                    return;
                int counter = 0;
                int pixel = bitmapData[counter];
                int pixelModulus = 0;
                for(int j = y; j < y + h && j < bh; j++) {
                    for(int i = x; i < x + w; i++) {
                        renderPixel(buffer, bw, bh, i, j, ((pixel >> pixelModulus) & 1) != 0, fillR, fillG, fillB,
                            fillA, lineR, lineG, lineB, lineA);
                        pixelModulus++;
                        if(pixelModulus == PIXELS_PER_ELEMENT) {
                            pixel = bitmapData[++counter];
                            pixelModulus = 0;
                        }
                    }
                    if(pixelModulus > 0) {
                        pixel = bitmapData[++counter];
                        pixelModulus = 0;
                    }
                }
            }
        }
    }


    public synchronized void bitmap(int _x, int _y, String bmap, int lr, int lg, int lb, int la, int fr,
        int fg, int fb, int fa)
    {
        renderObjects.add(new Bitmap(_x, _y, bmap, lr, lg, lb, la, fr, fg, fb, fa));
    }

    public synchronized void bitmapBinary(int _x, int _y, String bmap, int lr, int lg, int lb, int la, int fr,
        int fg, int fb, int fa)
    {
        renderObjects.add(new Bitmap(_x, _y, bmap, lr, lg, lb, la, fr, fg, fb, fa, true));
    }

    public synchronized void vgaChargen(int _x, int _y, String text, int lr, int lg, int lb, int la, int fr,
        int fg, int fb, int fa, PC pc)
    {
        renderObjects.add(new Bitmap(_x, _y, text, lr, lg, lb, la, fr, fg, fb, fa, pc));
    }

    public synchronized void setVGAChargen(int addr)
    {
        vgaChargenBase = addr;
    }

}
