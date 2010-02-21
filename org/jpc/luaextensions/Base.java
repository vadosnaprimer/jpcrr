/*
    JPC-RR: A x86 PC Hardware Emulator
    Release 1

    Copyright (C) 2007-2009 Isis Innovation Limited
    Copyright (C) 2009 H. Ilari Liusvaara

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

import java.io.*;
import java.util.*;
import java.lang.reflect.*;

import org.jpc.plugins.LuaPlugin;
import org.jpc.jrsr.*;
import static org.jpc.Misc.parseStringToComponents;
import static org.jpc.Misc.errorDialog;
import static org.jpc.Misc.tempname;
import static org.jpc.Misc.nextParseLine;
import static org.jpc.Misc.parseString;
import static org.jpc.Misc.encodeLine;

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

    private static int intLuaCBInvoke(Lua l, boolean sync, LuaPlugin plugin)
    {
        LuaTable args = null;
        String[] _args = null;
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
           elements = l.objLen(args);
           _args = new String[elements];
           for(int i = 0; i < elements; i++) {
               _args[i] = l.getTable(args, new Double(i + 1)).toString();
               if(_args[i] == null)
                   _args[i] = "";
           }
        } else {
            l.error("Unexpected types to invoke");
            return 0;
        }

        plugin.callInvokeCommand(cmd, _args, sync);
        return 0;
    }

    public static int luaCB_call(Lua l, LuaPlugin plugin)
    {
        LuaTable args = null;
        String[] _args = null;
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
           elements = l.objLen(args);
           _args = new String[elements];
           for(int i = 0; i < elements; i++) {
               _args[i] = l.getTable(args, new Double(i + 1)).toString();
               if(_args[i] == null)
                   _args[i] = "";
           }
        } else {
            l.error("Unexpected types to invoke");
            return 0;
        }

        Object[] ret = plugin.callCommand(cmd, _args);
        if(ret == null) {
            System.err.println("No return values");
            return 0;
        }
        int i = 1;
        System.err.println("" + ret.length + " return value(s)");
        LuaTable tab = l.newTable();
        for(Object retE : ret) {
            if(retE == null)
                i++;
            else if(retE instanceof Boolean)
                l.setTable(tab, new Double(i++), retE);
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

    public static int luaCB_invoke(Lua l, LuaPlugin plugin)
    {
        return intLuaCBInvoke(l, false, plugin);
    }

    public static int luaCB_invoke_synchronous(Lua l, LuaPlugin plugin)
    {
        return intLuaCBInvoke(l, true, plugin);
    }

    public static int luaCB_wait_vga(Lua l, LuaPlugin plugin)
    {
        plugin.doLockVGA();
        l.pushBoolean(plugin.getOwnsVGALock());
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
        long c = plugin.getClockTime();
        if(c >= 0) {
            l.push(new Double(c));
            return 1;
        } else
            return 0;
    }

    public static int luaCB_pc_connected(Lua l, LuaPlugin plugin)
    {
        l.pushBoolean(plugin.getPCConnected());
        return 1;
    }
}
