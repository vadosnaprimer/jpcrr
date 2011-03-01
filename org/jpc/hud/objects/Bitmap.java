package org.jpc.hud.objects;

import org.jpc.hud.RenderObject;
import static org.jpc.hud.VGAFont.vgaFontData;


public class Bitmap implements RenderObject
{
    int x;
    int y;
    org.jpc.hud.Bitmap bmap;

    public Bitmap(int _x, int _y, org.jpc.hud.Bitmap _bmap)
    {
        x = _x;
        y = _y;
        bmap = _bmap;
    }

    public void render(int[] buffer, int bw, int bh)
    {
        bmap.render(buffer, bw, bh, x, y);
    }
}
