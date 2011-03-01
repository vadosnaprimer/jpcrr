package org.jpc.hud;

public class Bitmap
{
    public int w;
    public int h;
    public int[] bitmapC;
    public int[] bitmapA;
    public int[] bitmapIA;

    public Bitmap(int[] c, int[] a, int _w, int _h)
    {
        w = _w;
        h = _h;
        bitmapC = c;
        bitmapA = a;
        bitmapIA = new int[_w * _h];
        for(int i = 0; i < _w * _h; i++)
            bitmapIA[i] = 256 - bitmapA[i];
    }

    public void render(int[] buffer, int bw, int bh, int x, int y)
    {
        for(int j = y; j < y + h && j < bh; j++) {
            if(j < 0)
                continue;
            int yoff = j - y;
            for(int i = x; i < x + w && i < bw; i++) {
                if(i < 0)
                    continue;
                int xoff = i - x;
                int bIndex = yoff * w + xoff;
                int sIndex = j * bw + i;
                int orig1 = buffer[sIndex] & 0xFF00FF;
                int orig2 = buffer[sIndex] & 0x00FF00;
                int new1 = bitmapC[bIndex] & 0xFF00FF;
                int new2 = bitmapC[bIndex] & 0x00FF00;
                int c1, c2;
                c1 = ((new1 * bitmapA[bIndex] + orig1 * bitmapIA[bIndex]) >>> 8) & 0xFF00FF;
                c2 = ((new2 * bitmapA[bIndex] + orig2 * bitmapIA[bIndex]) >>> 8) & 0x00FF00;
                buffer[sIndex] = c1 | c2;
            }
        }
    }
}
