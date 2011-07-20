/*
    JPC-RR: A x86 PC Hardware Emulator
    Release 1

    Copyright (C) 2007-2009 Isis Innovation Limited
    Copyright (C) 2009-2011 H. Ilari Liusvaara

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

package org.jpc.luaextensions;

import mnj.lua.*;

import org.jpc.emulator.Clock;
import org.jpc.emulator.PC;
import org.jpc.emulator.pci.peripheral.VGACard;
import org.jpc.emulator.DisplayController;
import org.jpc.emulator.EventRecorder;
import org.jpc.plugins.LuaPlugin;
import static org.jpc.Misc.messageForException;


//Locking this class is used for preventing termination and when terminating.
public class Base extends LuaPlugin.LuaResource
{
    public void destroy()
    {
    }

    private Base(LuaPlugin plugin)
    {
        super(plugin);
    }

    public static int luaCB_bus_call(Lua l, LuaPlugin plugin)
    {
        LuaTable args = null;
        Object[] _args = null;
        int elements = 0;
        if(l.type(1) != Lua.TSTRING) {
            l.error("Unexpected types to invoke");
            return 0;
        }

        String cmd = l.value(1).toString();
        if(l.type(2) == Lua.TNONE || l.type(2) == Lua.TNIL) {
           ///Nothing.
        } else if(l.type(2) == Lua.TTABLE) {
           args = (LuaTable)l.value(2);
           elements = Lua.objLen(args);
           _args = new Object[elements];
           for(int i = 0; i < elements; i++) {
               Object tmp = l.getTable(args, new Double(i + 1));
               if(tmp.getClass() == Double.class)
                   _args[i] = new Long((long)(((Double)tmp).doubleValue()));
               else if(!Lua.isNil(tmp))
                   _args[i] = tmp.toString();
               else
                   _args[i] = null;
           }
        } else {
            l.error("Unexpected types to bus_call");
            return 0;
        }

        Object[] ret = null;
        try {
            ret = plugin.callBusCommand(cmd, _args);
        } catch(Exception e) {
            l.error(messageForException(e, true));
        }
        if(ret == null) {
            //System.err.println("No return values");
            return 0;
        }
        int i = 1;
        //System.err.println("" + ret.length + " return value(s)");
        LuaTable tab = l.newTable();
        for(Object retE : ret) {
            if(retE == null)
                i++;
            else if(retE instanceof Boolean)
                l.setTable(tab, new Double(i++), retE);
            else if(retE instanceof Byte)
                l.setTable(tab, new Double(i++), new Double(((Byte)retE).byteValue()));
            else if(retE instanceof Short)
                l.setTable(tab, new Double(i++), new Double(((Short)retE).shortValue()));
            else if(retE instanceof Integer)
                l.setTable(tab, new Double(i++), new Double(((Integer)retE).intValue()));
            else if(retE instanceof Long)
                l.setTable(tab, new Double(i++), new Double(((Long)retE).longValue()));
            else if(retE instanceof String)
                l.setTable(tab, new Double(i++), retE);
            else
                l.setTable(tab, new Double(i++), "<unconvertable object>");
        }
        l.push(tab);
        return 1;
    }

    public static int luaCB_release_vga(Lua l, LuaPlugin plugin)
    {
        plugin.doReleaseVGA();
        return 0;
    }

    public static int luaCB_vga_resolution(Lua l, LuaPlugin plugin)
    {
        l.push(new Double(plugin.getXResolution()));
        l.push(new Double(plugin.getYResolution()));
        return 2;
    }

    public static int luaCB_pc_running(Lua l, LuaPlugin plugin)
    {
        l.pushBoolean(plugin.getPCRunning());
        return 1;
    }

    public static int luaCB_clock_time(Lua l, LuaPlugin plugin)
    {
        Clock clk = (Clock)plugin.getComponent(Clock.class);
        if(clk != null)
            l.push(new Double(clk.getTime()));
        else
            l.pushNil();
        return 1;
    }

    public static int luaCB_wait_event(Lua l, LuaPlugin plugin)
    {
        LuaPlugin.Event msg = plugin.waitEvent();
        int pushed = 1;
        if(msg != null) {
            l.push(msg.type);
            if(msg.data != null) {
                l.push(msg.data);
                pushed++;
            }
        } else
            l.pushNil();
        return pushed;
    }

    public static int luaCB_poll_message(Lua l, LuaPlugin plugin)
    {
        LuaPlugin.Event msg = plugin.pollEvent();
        int pushed = 1;
        if(msg != null) {
            l.push(msg.type);
            if(msg.data != null) {
                l.push(msg.data);
                pushed++;
            }
        } else
            l.pushNil();
        return pushed;
    }

    public static int luaCB_in_frame_hold(Lua l, LuaPlugin plugin)
    {
        l.pushBoolean(plugin.getOwnsVGALock());
        return 1;
    }

    public static int luaCB_pc_connected(Lua l, LuaPlugin plugin)
    {
        l.pushBoolean(plugin.getPCConnected());
        return 1;
    }

    public static int luaCB_stringlessthan(Lua l, LuaPlugin plugin)
    {
        int ret = 0;
        String x = l.value(1).toString();
        String y = l.value(2).toString();
        int xlen = x.length();
        int ylen = y.length();
        for(int i = 0; i < xlen && i < ylen; i++) {
            int xcp = x.charAt(i);
            int ycp = y.charAt(i);
            boolean xs = (xcp >= 0xD800 && xcp <= 0xDFFF);
            boolean ys = (ycp >= 0xD800 && ycp <= 0xDFFF);
            if(!xs && ys)
                ret = (ret == 0) ? 1 : ret;
            else if(xs && !ys)
                ret = (ret == 0) ? -1 : ret;
            else if(xcp < ycp)
                ret = (ret == 0) ? 1 : ret;
            else if(xcp > ycp)
                ret = (ret == 0) ? -1 : ret;
        }
        if(ret == 0 && xlen < ylen)
            ret = 1;
        if(ret == 0 && xlen > ylen)
            ret = -1;

        l.pushBoolean(ret == 1);
        return 1;
    }

    public static int luaCB_frame_number(Lua l, LuaPlugin plugin)
    {
        DisplayController dc = ((DisplayController)plugin.getComponent(DisplayController.class));
        if(dc == null)
            l.pushNil();
        else
            l.pushNumber((double)dc.getFrameNumber());
        return 1;
    }

    public static int luaCB_movie_length(Lua l, LuaPlugin plugin)
    {
        PC.ResetButton brb = ((PC.ResetButton)plugin.getComponent(PC.ResetButton.class));
        if(brb == null) {
            l.pushNil();
            return 1;
        }
        EventRecorder rec = brb.getRecorder();
        long lastTime = rec.getLastEventTime();
        long attachTime = rec.getAttachTime();
        long time = 0;
        if(attachTime < lastTime)
            time = lastTime - attachTime;
        l.push(new Double(time));
        return 1;
    }

    public static int luaCB_movie_rerecords(Lua l, LuaPlugin plugin)
    {
        PC.ResetButton brb = ((PC.ResetButton)plugin.getComponent(PC.ResetButton.class));
        if(brb == null) {
            l.pushNil();
            return 1;
        }
        EventRecorder rec = brb.getRecorder();
        l.push(new Double(rec.getRerecordCount()));
        return 1;
    }

    public static int luaCB_movie_headers(Lua l, LuaPlugin plugin)
    {
        PC.ResetButton brb = ((PC.ResetButton)plugin.getComponent(PC.ResetButton.class));
        if(brb == null) {
            l.pushNil();
            return 1;
        }
        EventRecorder rec = brb.getRecorder();
        String[][] headers = rec.getHeaders();
        LuaTable ret = l.newTable();
        if(headers != null)
            for(int i = 0; i < headers.length; i++) {
                LuaTable tab = l.newTable();
                int j = 1;
                if(headers[i] != null)
                    for(String p : headers[i])
                        l.setTable(tab, new Double(j++), p);
                l.setTable(ret, new Double(i + 1), tab);
            }
        l.push(ret);
        return 1;
    }

    public static int luaCB_vga_edge_count(Lua l, LuaPlugin plugin)
    {
        VGACard card = (VGACard)plugin.getComponent(VGACard.class);
        if(card != null)
            l.push(new Double(card.getVretraceEdgeCount()));
        else
            l.pushNil();
        return 1;
    }

    public static int luaCB_vga_scroll_count(Lua l, LuaPlugin plugin)
    {
        VGACard card = (VGACard)plugin.getComponent(VGACard.class);
        if(card != null)
            l.push(new Double(card.getScrollCount()));
        else
            l.pushNil();
        return 1;
    }

    public static int luaCB_project_id(Lua l, LuaPlugin plugin)
    {
        PC.ResetButton brb = ((PC.ResetButton)plugin.getComponent(PC.ResetButton.class));
        if(brb == null) {
            l.pushNil();
            return 1;
        }
        EventRecorder rec = brb.getRecorder();
        l.push(rec.getProjectID());
        return 1;
    }
}
