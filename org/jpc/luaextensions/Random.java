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

import java.security.SecureRandom;
import org.jpc.plugins.LuaPlugin;

//Locking this class is used for preventing termination and when terminating.
public class Random extends LuaPlugin.LuaResource
{
    static SecureRandom rng;

    static
    {
        rng = new SecureRandom();
    }

    public void destroy()
    {
    }

    private Random(LuaPlugin plugin)
    {
        super(plugin);
    }

    public static int luaCB_nextBoolean(Lua l, LuaPlugin plugin)
    {
        l.push(new Boolean(rng.nextBoolean()));
        return 1;
    }

    public static int luaCB_nextBytes(Lua l, LuaPlugin plugin)
    {
        l.pushNil();
        int bytes = (int)l.checkNumber(1);
        if(bytes < 0) {
            l.error("nextBytes: Byte count must be positive");
            return 0;
        }
        byte[] tmp = new byte[bytes];
        rng.nextBytes(tmp);
        StringBuilder b = new StringBuilder(bytes);
        for(byte x : tmp)
            b.append((char)((int)x & 0xFF));
        l.push(b.toString());
        return 1;
    }

    public static int luaCB_nextDouble(Lua l, LuaPlugin plugin)
    {
        l.push(new Double(rng.nextDouble()));
        return 1;
    }

    public static int luaCB_nextFloat(Lua l, LuaPlugin plugin)
    {
        l.push(new Double(rng.nextFloat()));
        return 1;
    }

    public static int luaCB_nextGaussian(Lua l, LuaPlugin plugin)
    {
        l.push(new Double(rng.nextGaussian()));
        return 1;
    }

    public static int luaCB_nextInt(Lua l, LuaPlugin plugin)
    {
        l.pushNil();
        if(l.type(1) == Lua.TNIL)
            l.push(new Double((long)rng.nextInt() & 0xFFFFFFFFL));
        else if(l.type(1) == Lua.TNUMBER)
            l.push(new Double((long)rng.nextInt((int)l.checkNumber(1)) & 0xFFFFFFFFL));
        else
            l.error("nextInt: Expected nil or int");
        return 1;
    }

    public static int luaCB_nextLong(Lua l, LuaPlugin plugin)
    {
        l.push(new Double(rng.nextLong()));
        return 1;
    }
}
