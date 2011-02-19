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
import org.jpc.jrsr.*;
import static org.jpc.Misc.nextParseLine;

//Locking this class is used for preventing termination and when terminating.
public class TextInFile extends LuaPlugin.LuaResource
{
    UnicodeInputStream object;

    public TextInFile(LuaPlugin plugin, UnicodeInputStream is) throws IOException
    {
        super(plugin);
        object = is;
    }

    public void destroy() throws IOException
    {
        object.close();
        object = null;
    }

    public int luaCB_read(Lua l, LuaPlugin plugin)
    {
        try {
            String x = object.readLine();
            if(x != null)
                l.push(x);
            else {
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

    public int luaCB_four_to_five(Lua l, LuaPlugin plugin)
    {
        try {
            plugin.generateLuaClass(l, new BinaryInFile(plugin, new FourToFiveDecoder(object)));
        } catch(IOException e) {
            l.pushNil();
            l.pushString("IOException: " + e.getMessage());
            return 2;
        } catch(IllegalArgumentException e) {
            l.pushNil();
            l.pushString("Illegal argument: " + e.getMessage());
            return 2;
        }
        return 1;
    }


    public int luaCB_read_component(Lua l, LuaPlugin plugin)
    {
        try {
            String[] x = nextParseLine(object);
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
