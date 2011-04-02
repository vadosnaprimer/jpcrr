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

//Locking this class is used for preventing termination and when terminating.
public class HostMemory extends LuaPlugin.LuaResource
{
    public void destroy()
    {
    }

    private HostMemory(LuaPlugin plugin)
    {
        super(plugin);
    }

    public static int luaCB_read(Lua l, LuaPlugin plugin)
    {
        PC p = plugin.getPC();
        if(p != null) {
            l.push(p.readHostMemory());
            return 1;
        }
        return 0;
    }

    public static int luaCB_write(Lua l, LuaPlugin plugin)
    {
        if(l.type(1) != Lua.TSTRING) {
            l.error("Unexpected types to invoke");
            return 0;
        }
        PC p = plugin.getPC();
        if(p != null)
            p.writeHostMemory(l.value(1).toString());
        return 0;
    }
}