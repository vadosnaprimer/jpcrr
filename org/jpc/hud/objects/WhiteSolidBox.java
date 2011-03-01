package org.jpc.hud.objects;

import org.jpc.hud.RenderObject;

public class WhiteSolidBox implements RenderObject
{
    int x;
    int y;
    int w;
    int h;

    public WhiteSolidBox(int _x, int _y, int _w, int _h)
    {
        x = _x;
        y = _y;
        w = _w;
        h = _h;
    }

    public void render(int[] buffer, int bw, int bh)
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
