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
import java.util.zip.*;

import org.jpc.plugins.LuaPlugin;
import org.jpc.jrsr.*;

//Locking this class is used for preventing termination and when terminating.
public class BinaryOutFile extends LuaPlugin.LuaResource
{
    OutputStream object;

    BinaryOutFile(LuaPlugin plugin, OutputStream os) throws IOException
    {
        super(plugin);
        object = os;
    }

    public void destroy() throws IOException
    {
        object.close();
        object = null;
    }

    public int luaCB_four_to_five(Lua l, LuaPlugin plugin)
    {
        try {
            plugin.generateLuaClass(l, new BinaryOutFile(plugin, new FourToFiveEncoder(object)));
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

    public int luaCB_deflate(Lua l, LuaPlugin plugin)
    {
        try {
            plugin.generateLuaClass(l, new BinaryOutFile(plugin, new DeflaterOutputStream(object)));
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

    public int luaCB_text(Lua l, LuaPlugin plugin)
    {
        try {
            plugin.generateLuaClass(l, new TextOutFile(plugin, object));
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

    public int luaCB_write(Lua l, LuaPlugin plugin)
    {
        l.pushNil();
        try {
            String towrite = l.checkString(2);
            if(towrite == "") {
                l.pushBoolean(true);
                return 1;
            }
            byte[] tmp = new byte[towrite.length()];
            for(int i = 0; i < towrite.length(); i++)
                tmp[i] = (byte)towrite.charAt(i);
            object.write(tmp);
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

    public static int luaCB_open(Lua l, LuaPlugin plugin)
    {
        l.pushNil();
        String name = l.checkString(1);
        try {
            plugin.generateLuaClass(l, new BinaryOutFile(plugin, new FileOutputStream(name)));
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
}
