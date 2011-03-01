package org.jpc.hud.objects;

import org.jpc.hud.RenderObject;

public class Pixel implements RenderObject
{
    int x;
    int y;
    int useC;
    int useA;
    int useIA;

    public Pixel(int _x, int _y, int lr, int lg, int lb, int la)
    {
        x = _x;
        y = _y;
        useC = (lr << 16) | (lg << 8) | lb;
        useA = la * 256 / 255;
        useIA = 256 - useA;
    }

    public void render(int[] buffer, int bw, int bh)
    {
        int old1 = buffer[y * bw + x] & 0xFF00FF;
        int old2 = buffer[y * bw + x] & 0x00FF00;
        int new1 = useC & 0xFF00FF;
        int new2 = useC & 0x00FF00;
        int c1 = ((new1 * useA + old1 * useIA) >>> 8) & 0xFF00FF;
        int c2 = ((new2 * useA + old2 * useIA) >>> 8) & 0x00FF00;
        buffer[y * bw + x] = c1 | c2;
    }
}
