package org.jpc.hud.objects;

import org.jpc.hud.RenderObject;

public class Box implements RenderObject
{
    int x;
    int y;
    int w;
    int h;
    int thick;
    int lineC;
    int lineA;
    int lineIA;
    int fillC;
    int fillA;
    int fillIA;

    public Box(int _x, int _y, int _w, int _h, int _thick, int lr, int lg, int lb, int la, int fr, int fg, int fb,
        int fa)
    {
        x = _x;
        y = _y;
        w = _w;
        h = _h;
        thick = _thick;
        lineC = (lr << 16) | (lg << 8) | lb;
        lineA = la * 256 / 255;
        fillC = (fr << 16) | (fg << 8) | fb;
        fillA = fa * 256 / 255;
        lineIA = 256 - lineA;
        fillIA = 256 - fillA;
    }

    public void render(int[] buffer, int bw, int bh)
    {
        int useC = 0;
        int useA = 0;
        int useIA = 0;
        for(int j = y; j < y + h && j < bh; j++) {
            if(j < 0)
                continue;
            for(int i = x; i < x + w && i < bw; i++) {
                if(i < 0)
                    continue;
                int dist = i - x;
                if(j - y < dist)
                    dist = j - y;
                if(x + w - i - 1 < dist)
                    dist = x + w - i - 1;
                if(y + h - j - 1 < dist)
                    dist = y + h - j - 1;
                useC = (dist < thick) ? lineC : fillC;
                useA = (dist < thick) ? lineA : fillA;
                useIA = (dist < thick) ? lineIA : fillIA;
                int old1 = buffer[y * bw + x] & 0xFF00FF;
                int old2 = buffer[y * bw + x] & 0x00FF00;
                int new1 = useC & 0xFF00FF;
                int new2 = useC & 0x00FF00;
                int c1 = ((new1 * useA + old1 * useIA) >>> 8) & 0xFF00FF;
                int c2 = ((new2 * useA + old2 * useIA) >>> 8) & 0x00FF00;
                buffer[y * bw + x] = c1 | c2;
            }
        }
    }
}
