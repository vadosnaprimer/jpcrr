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
public class ArchiveIn extends LuaPlugin.LuaResource
{
    JRSRArchiveReader object;

    ArchiveIn(LuaPlugin plugin, String name) throws IOException
    {
        super(plugin);
        object = new JRSRArchiveReader(name);
    }

    public void destroy() throws IOException
    {
        object.close();
    }

    public int luaCB_member_list(Lua l, LuaPlugin plugin)
    {
        try {
           int i = 1;
           LuaTable tab = l.newTable();
           Set<String> memberNames = object.getMembers();
           for(String name : memberNames)
               l.setTable(tab, new Double(i++), name);
           l.push(tab);
        } catch(IOException e) {
           l.pushNil();
           l.pushString("IOException: " + e.getMessage());
           return 2;
        }
        return 1;
    }

    public int luaCB_member(Lua l, LuaPlugin plugin)
    {
        l.pushNil();
        String name = l.checkString(2);
        try {
            InputStream is = object.readMember(name);
            plugin.generateLuaClass(l, new BinaryInFile(plugin, is));
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

    public static int luaCB_open(Lua l, LuaPlugin plugin)
    {
        l.pushNil();
        String name = l.checkString(1);
        try {
            plugin.generateLuaClass(l, new ArchiveIn(plugin,name));
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
