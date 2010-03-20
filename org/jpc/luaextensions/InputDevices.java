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

import org.jpc.modules.Joystick;
import org.jpc.emulator.peripheral.Keyboard;

import org.jpc.plugins.LuaPlugin;

//Locking this class is used for preventing termination and when terminating.
public class InputDevices extends LuaPlugin.LuaResource
{
    public void destroy()
    {
    }

    private InputDevices(LuaPlugin plugin)
    {
        super(plugin);
    }

    public static int luaCB_keypressed(Lua l, LuaPlugin plugin)
    {
        if(l.type(1) != Lua.TNUMBER) {
            l.error("Unexpected types to keypressed");
            return 0;
        }
        Keyboard key = (Keyboard)plugin.getComponent(Keyboard.class);
        if(key != null)
            l.pushBoolean(key.getKeyExecStatus((byte)l.checkNumber(1)));
        else
            l.pushBoolean(false);
        return 1;
    }

    public static int luaCB_joystick_state(Lua l, LuaPlugin plugin)
    {
        Joystick joy = (Joystick)plugin.getComponent(Joystick.class);

        if(joy != null) {
            l.push(new Double(joy.axisHoldTime(0, false)));
            l.push(new Double(joy.axisHoldTime(1, false)));
            l.push(new Double(joy.axisHoldTime(2, false)));
            l.push(new Double(joy.axisHoldTime(3, false)));
            l.pushBoolean(joy.buttonState(0, false));
            l.pushBoolean(joy.buttonState(1, false));
            l.pushBoolean(joy.buttonState(2, false));
            l.pushBoolean(joy.buttonState(3, false));
            return 8;
        } else {
            l.pushNil();
            return 1;
        }
    }
}
