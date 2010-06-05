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
import javax.swing.JFileChooser;

//Locking this class is used for preventing termination and when terminating.
public class BaseFSOps extends LuaPlugin.LuaResource
{
    public void destroy()
    {
    }

    private BaseFSOps(LuaPlugin plugin)
    {
        super(plugin);
    }

    public static int luaCB_mkdir(Lua l, LuaPlugin plugin)
    {
        l.pushBoolean((new File(l.checkString(1))).mkdir());
        return 1;
    }

    public static int luaCB_unlink(Lua l, LuaPlugin plugin)
    {
        l.pushBoolean((new File(l.checkString(1))).delete());
        return 1;
    }

    public static int luaCB_rename(Lua l, LuaPlugin plugin)
    {
        l.pushBoolean((new File(l.checkString(1))).renameTo(new File(l.checkString(2))));
        return 1;
    }

    public static int luaCB_opensave_dialog(Lua l, LuaPlugin plugin)
    {
        String title = null;
        String buttonTitle = null;
        boolean  save = false;
        int r = 1;
        JFileChooser c = null;
        l.pushNil();
        l.pushNil();

        save = l.toBoolean(l.value(1));

        if(l.type(2) == Lua.TSTRING)
            title = l.checkString(2);
        else if(l.type(2) == Lua.TNIL)
            title = "Select file to " + (save ? "save" : "open");
        else
            l.error("Unexpected types to opensave_dialog (arg2 must be nil or string)");

        buttonTitle = save ? "save" : "open";

        c = new JFileChooser(System.getProperty("user.dir"));
        c.setDialogTitle(title);
        if(save)
            r = c.showSaveDialog(null);
        else
            r = c.showOpenDialog(null);

        if(r != 0) {
            l.pushNil();
            l.pushString("User canceled");
            return 2;
        } else {
            l.pushString(c.getSelectedFile().getAbsolutePath());
            return 1;
        }
    }
}
