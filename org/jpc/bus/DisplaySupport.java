package org.jpc.bus;

import java.util.List;
import java.util.ArrayList;
import org.jpc.hud.HUDRenderer;
import org.jpc.emulator.PC;
import org.jpc.output.OutputStatic;
import static org.jpc.Misc.castToByte;
import static org.jpc.Misc.castToShort;
import static org.jpc.Misc.castToInt;
import static org.jpc.Misc.castToLong;

class DisplaySupport
{
    private List<HUDRenderer> renderers;
    private OutputStatic outputConnector;
    private PC pc;

    public DisplaySupport(Bus _bus)
    {
        _bus.setCommandHandler(this, "addRenderer", "add-renderer");
        _bus.setCommandHandler(this, "removeRenderer", "remove-renderer");
        _bus.setCommandHandler(this, "listRenderers", "list-renderers");
        _bus.setCommandHandler(this, "getPCOutput", "get-pc-output");
        _bus.setCommandHandler(this, "getPC", "get-pc");
        _bus.setEventHandler(this, "pcChange", "pc-change");
        renderers = new ArrayList<HUDRenderer>();
        outputConnector = new OutputStatic();
    }

    public String addRenderer_help(String cmd, boolean brief)
    {
        if(brief)
            return "(Internal) Add a renderer to the list of renderers";
        System.err.println("Synopsis: add-renderer <renderer>");
        System.err.println("Adds renderer <renderer> to the renderer list.");
        return null;
    }


    public void addRenderer(BusRequest req, String cmd, Object[] args) throws IllegalArgumentException
    {
        if(args == null || args.length != 1)
            throw new IllegalArgumentException("Command needs an argument");
        HUDRenderer o = (HUDRenderer)args[0];
        renderers.add(o);
        req.doReturn();
    }

    public String removeRenderer_help(String cmd, boolean brief)
    {
        if(brief)
            return "(Internal) Remove a renderer from the list of renderers";
        System.err.println("Synopsis: remove-renderer <renderer>");
        System.err.println("Removes renderer <renderer> from the renderer list.");
        return null;
    }

    public void removeRenderer(BusRequest req, String cmd, Object[] args) throws IllegalArgumentException
    {
        if(args == null || args.length != 1)
            throw new IllegalArgumentException("Command needs an argument");
        HUDRenderer o = (HUDRenderer)args[0];
        renderers.remove(o);
        req.doReturn();
    }

    public String listRenderers_help(String cmd, boolean brief)
    {
        if(brief)
            return "Print the list of renderers";
        System.err.println("Synopsis: list-renderers");
        System.err.println("Prints the renderer list.");
        return null;
    }

    public void listRenderers(BusRequest req, String cmd, Object[] args)
    {
        req.doReturnA(renderers.toArray());
    }

    public String getPCOutput_help(String cmd, boolean brief)
    {
        if(brief)
            return "(Internal) Get the pc output object";
        System.err.println("Synopsis: get-pc-output");
        System.err.println("Returns the PC output object.");
        return null;
    }

    public void getPCOutput(BusRequest req, String cmd, Object[] args)
    {
        req.doReturnL(outputConnector);
    }

    public String getPC_help(String cmd, boolean brief)
    {
        if(brief)
            return "(Internal) Get the pc object";
        System.err.println("Synopsis: get-pc");
        System.err.println("Returns the PC object.");
        return null;
    }

    public void getPC(BusRequest req, String cmd, Object[] args)
    {
        req.doReturnL(pc);
    }

    public void pcChange(String cmd, Object[] args) throws IllegalArgumentException
    {
        if(args == null || args.length != 1)
            throw new IllegalArgumentException("pc-change: Event needs an argument");
        PC _pc = (PC)args[0];

        if(pc != null)
            pc.getOutputs().setStaticOutput(null, 0);
        pc = _pc;
        if(pc != null)
            pc.getOutputs().setStaticOutput(outputConnector, outputConnector.getLastTime() - pc.getTime());
    }
}
