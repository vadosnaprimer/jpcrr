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
public class DelayedDelete extends LuaPlugin.LuaResource
{
    File object;

    DelayedDelete(LuaPlugin plugin, String name)
    {
        super(plugin);
        object = new File(name);
    }

    public void destroy() throws IOException
    {
        object.delete();
        object = null;
    }

    public static int luaCB_random_temporary_name(Lua l, LuaPlugin plugin)
    {
        String name = tempname(l.checkString(2));
        String fileName = l.checkString(1) + name;
        new DelayedDelete(plugin, fileName);
        l.push(name);
        l.push(fileName);
        return 2;
    }
}
