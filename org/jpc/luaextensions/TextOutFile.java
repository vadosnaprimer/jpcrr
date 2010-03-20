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

import org.jpc.plugins.LuaPlugin;
import org.jpc.jrsr.*;

//Locking this class is used for preventing termination and when terminating.
public class TextOutFile extends LuaPlugin.LuaResource
{
    UTFOutputLineStream object;

    public TextOutFile(LuaPlugin plugin, OutputStream os) throws IOException
    {
        super(plugin);
        object = new UTFOutputLineStream(os);
    }

    public void destroy() throws IOException
    {
        object.close();
        object = null;
    }

    public int luaCB_write(Lua l, LuaPlugin plugin)
    {
        l.pushNil();
        try {
            object.writeLine(l.checkString(2));
            l.pushBoolean(true);
        } catch(IOException e) {
            l.pushBoolean(false);
            l.pushString("IOException: " + e.getMessage());
            return 2;
        }
        return 1;
    }

    public int luaCB_write_component(Lua l, LuaPlugin plugin)
    {
        l.pushNil();
        try {
            int c = Lua.objLen(l.value(2));
            if(c <= 0) {
                l.pushBoolean(false);
                l.pushString("Required at least one component to write.");
                return 2;
            }
            Object tab = l.value(2);
            String[] x = new String[c];
            for(int i = 0; i < c; i++) {
                Object obj = l.getTable(tab, new Double(i + 1));
                if(obj instanceof String)
                   x[i] = (String)obj;
                else
                   l.error("Can't write non-string component");
            }
            object.encodeLine((Object[])x);
            l.pushBoolean(true);
        } catch(IOException e) {
            l.pushBoolean(false);
            l.pushString("IOException: " + e.getMessage());
            return 2;
       }
       return 1;
    }

    public int luaCB_close(Lua l, LuaPlugin plugin)
    {
        try {
            plugin.destroyLuaObject(l);
            l.pushBoolean(true);
        } catch(IOException e) {
            l.pushBoolean(false);
            l.pushString("IOException: " + e.getMessage());
            return 2;
        }
        return 1;
    }
}
