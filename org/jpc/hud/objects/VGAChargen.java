package org.jpc.hud.objects;

import org.jpc.hud.RenderObject;
import static org.jpc.hud.VGAFont.vgaFontData;


public class VGAChargen implements RenderObject
{
    int x;
    int y;
    int stride;
    boolean multiline;
    String vgaChargenString;
    int lineC;
    int lineA;
    int lineIA;
    int fillC;
    int fillA;
    int fillIA;

    public VGAChargen(int _x, int _y, String text, int lr, int lg, int lb, int la, int fr, int fg, int fb,
        int fa, boolean _multiline)
    {
        x = _x;
        y = _y;
        vgaChargenString = text;
        multiline = _multiline;
        lineC = (lr << 16) | (lg << 8) | lb;
        lineA = la * 256 / 255;
        fillC = (fr << 16) | (fg << 8) | fb;
        fillA = fa * 256 / 255;
        lineIA = 256 - lineA;
        fillIA = 256 - fillA;
    }

    private final void renderPartial(int[] buffer, int bw, int bh, int x, int y, long data)
    {
        int useC = 0;
        int useA = 0;
        int useIA = 0;
        for(int i = 0; i < 64; i++) {
            useC = (((data >>> i) & 1) != 0) ? lineC : fillC;
            useA = (((data >>> i) & 1) != 0) ? lineA : fillA;
            useIA = (((data >>> i) & 1) != 0) ? lineIA : fillIA;
            int _x = x + 7 - (i % 8);
            int _y = y + (i / 8);
            int old1 = buffer[_y * bw + _x] & 0xFF00FF;
            int old2 = buffer[_y * bw + _x] & 0x00FF00;
            int new1 = useC & 0xFF00FF;
            int new2 = useC & 0x00FF00;
            int c1 = ((new1 * useA + old1 * useIA) >>> 8) & 0xFF00FF;
            int c2 = ((new2 * useA + old2 * useIA) >>> 8) & 0x00FF00;
            buffer[_y * bw + _x] = c1 | c2;
        }
    }

    public void render(int[] buffer, int bw, int bh)
    {
        int xbase = x, ybase = y;
        int len = vgaChargenString.length();
        for(int i = 0; i < len; i++) {
            int ch = (int)vgaChargenString.charAt(i) & 0xFF;
            if(multiline && (ch == 13 || ch == 10)) {
                ybase += 16;
                xbase = x;
            }
            if(!(xbase < -7 || ybase < -15 || xbase >= bw || ybase >= bh)) {
                renderPartial(buffer, bw, bh, xbase, y, vgaFontData[2 * ch + 0]);
                renderPartial(buffer, bw, bh, xbase, y + 8, vgaFontData[2 * ch + 1]);
            }
            xbase += 8;
        }
    }
}
