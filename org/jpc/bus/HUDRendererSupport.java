package org.jpc.bus;

import java.util.List;
import java.util.ArrayList;
import org.jpc.pluginsaux.HUDRenderer;
import static org.jpc.Misc.castToByte;
import static org.jpc.Misc.castToShort;
import static org.jpc.Misc.castToInt;
import static org.jpc.Misc.castToLong;

class HUDRendererSupport
{
    private List<HUDRenderer> renderers;

    public HUDRendererSupport(Bus _bus)
    {
        _bus.setCommandHandler(this, "addRenderer", "add-renderer");
        _bus.setCommandHandler(this, "removeRenderer", "remove-renderer");
        _bus.setCommandHandler(this, "listRenderers", "list-renderers");
        renderers = new ArrayList<HUDRenderer>();
    }

    public void addRenderer(BusRequest req, String cmd, Object[] args) throws IllegalArgumentException
    {
        if(args == null || args.length != 1)
            throw new IllegalArgumentException("Command needs an argument");
        HUDRenderer o = (HUDRenderer)args[0];
        renderers.add(o);
        req.doReturn();
    }

    public void removeRenderer(BusRequest req, String cmd, Object[] args) throws IllegalArgumentException
    {
        if(args == null || args.length != 1)
            throw new IllegalArgumentException("Command needs an argument");
        HUDRenderer o = (HUDRenderer)args[0];
        renderers.remove(o);
        req.doReturn();
    }

    public void listRenderers(BusRequest req, String cmd, Object[] args)
    {
        req.doReturnA(renderers.toArray());
    }
}
