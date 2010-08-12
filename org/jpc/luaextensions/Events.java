/*
    JPC-RR: A x86 PC Hardware Emulator
    Release 1

    Copyright (C) 2007-2009 Isis Innovation Limited
    Copyright (C) 2009-2010 H. Ilari Liusvaara

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
import org.jpc.emulator.DisplayController;
import org.jpc.emulator.EventRecorder;
import org.jpc.plugins.LuaPlugin;

public class Events extends LuaPlugin.LuaResource
{
    public void destroy()
    {
    }

    private Events(LuaPlugin plugin)
    {
        super(plugin);
    }

    private static EventRecorder getRecorder(Lua l, LuaPlugin plugin)
    {
        PC.ResetButton brb = ((PC.ResetButton)plugin.getComponent(PC.ResetButton.class));
        if(brb == null) {
            l.pushNil();
            return null;
        }
        return brb.getRecorder();
    }

    public static int luaCB_count(Lua l, LuaPlugin plugin)
    {
        EventRecorder rec = getRecorder(l, plugin);
        if(rec != null) {
            long count = rec.getEventCount();
            l.pushNumber((double)count);
        }
        return 1;
    }

    public static int luaCB_current_sequence(Lua l, LuaPlugin plugin)
    {
        EventRecorder rec = getRecorder(l, plugin);
        if(rec != null) {
            long count = rec.getEventCurrentSequence();
            l.pushNumber((double)count);
        }
        return 1;
    }

    public static int luaCB_by_sequence(Lua l, LuaPlugin plugin)
    {
        EventRecorder rec = getRecorder(l, plugin);
        if(rec != null) {
            l.pushNil();
            long num = (long)l.checkNumber(1);
            LuaTable ret = l.newTable();
            EventRecorder.ReturnEvent evr = rec.getEventBySequence(num);
            if(evr != null) {
                l.setTable(ret, new Double(1), new Double(evr.timestamp));
                int j = 2;
                if(evr.eventData != null)
                    for(String p : evr.eventData)
                        l.setTable(ret, new Double(j++), p);
            }
            l.push(ret);
        }
        return 1;
    }
}
