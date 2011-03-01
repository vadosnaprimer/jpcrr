package org.jpc.hud.objects;

import org.jpc.hud.RenderObject;

public class Circle implements RenderObject
{
    int x;
    int y;
    int r;
    long r2inner;
    long r2outer;
    int lineC;
    int lineA;
    int lineIA;
    int fillC;
    int fillA;
    int fillIA;

    public Circle(int _x, int _y, int _r, int _thick, int lr, int lg, int lb, int la, int fr, int fg, int fb,
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
        for(int j = y - r; j < y + r && j < bh; j++) {
            if(j < 0)
                continue;
            for(int i = x - r; i < x + r && i < bw; i++) {
                if(i < 0)
                    continue;
                long ox = i - x;
                long oy = j - y;
                long d = ox * ox + oy * oy;
                if(d > r2outer)
                    continue;
                useC = (d >= r2inner) ? lineC : fillC;
                useA = (d >= r2inner) ? lineA : fillA;
                useIA = (d >= r2inner) ? lineIA : fillIA;
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
