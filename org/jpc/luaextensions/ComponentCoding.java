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

import java.io.*;

import org.jpc.plugins.LuaPlugin;
import static org.jpc.Misc.parseString;
import static org.jpc.Misc.encodeLine;

//Locking this class is used for preventing termination and when terminating.
public class ComponentCoding extends LuaPlugin.LuaResource
{
    public void destroy()
    {
    }

    private ComponentCoding(LuaPlugin plugin)
    {
        super(plugin);
    }

    public static int luaCB_decode(Lua l, LuaPlugin plugin)
    {
        l.pushNil();
        try {
            String[] x = parseString(l.checkString(1));
            if(x != null) {
                LuaTable tab = l.newTable();
                for(int i = 0; i < x.length; i++)
                    l.setTable(tab, new Double(i + 1), x[i]);
                l.push(tab);
            } else {
                l.pushNil();
                l.pushNil();
                return 2;
            }
        } catch(IOException e) {
            l.pushNil();
            l.pushString("IOException: " + e.getMessage());
            return 2;
        }
        return 1;
    }

    public static int luaCB_encode(Lua l, LuaPlugin plugin)
    {
        l.pushNil();
        int c = Lua.objLen(l.value(1));
        if(c <= 0) {
            l.pushBoolean(false);
            l.pushString("Required at least one component to write.");
            return 2;
        }
        Object tab = l.value(1);
        String[] x = new String[c];
        for(int i = 0; i < c; i++) {
            Object obj = l.getTable(tab, new Double(i + 1));
            if(obj instanceof String)
                x[i] = (String)obj;
            else
                l.error("Can't write non-string component");
        }
        l.push(encodeLine(x));
        return 1;
    }
}
