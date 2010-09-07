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
import java.util.*;
import java.lang.reflect.*;
import org.jpc.plugins.LuaPlugin;

public class HUD extends LuaPlugin.LuaResource
{
    private static class ClassMethod
    {
        Class<?> c;
        Method m;
    }

    static Map<String, ClassMethod> methods;

    static
    {
        methods = new HashMap<String, ClassMethod>();
    }

    public void destroy()
    {
    }

    private HUD(LuaPlugin plugin)
    {
        super(plugin);
    }

    private static void lookupMethod(List<Object> renderers, String name)
    {
        String mName = "REMOTE_" + name;
        for(Object r : renderers) {
            Class<?> c = r.getClass();
            Method[] ma = c.getDeclaredMethods();
            for(Method m : ma)
                if(mName.equals(m.getName())) {
                    ClassMethod cm = new ClassMethod();
                    cm.c = c;
                    cm.m = m;
                    methods.put(name, cm);
                }
        }
    }

    private static boolean callMethod(List<Object> renderers, ClassMethod cm, Lua l)
    {
        Class<?> c = cm.c;
        Method m = cm.m;
        boolean hadSuccess = false;
        Class<?>[] paramTypes = m.getParameterTypes();

        Object[] parameterArray = new Object[paramTypes.length];
        int index = 2;
        int sIndex = 0;
        for(Class<?> param : paramTypes) {
            Object p = l.value(index);
            int luaType = l.type(p);
            if(param == boolean.class || param == Boolean.class)
                if(luaType == Lua.TBOOLEAN)
                    parameterArray[sIndex++] = new Boolean(l.toBoolean(p));
                else
                    l.error("Bad HUD parameter #" + index + " (expected boolean)");
            else if(param == byte.class || param == Byte.class)
                if(luaType == Lua.TNUMBER)
                    parameterArray[sIndex++] = new Byte((byte)l.toNumber(p));
                else
                    l.error("Bad HUD parameter #" + index + " (expected number)");
            else if(param == short.class || param == Short.class)
                if(luaType == Lua.TNUMBER)
                    parameterArray[sIndex++] = new Short((short)l.toNumber(p));
                else
                    l.error("Bad HUD parameter #" + index + " (expected number)");
            else if(param == int.class || param == Integer.class)
                if(luaType == Lua.TNUMBER)
                    parameterArray[sIndex++] = new Integer((int)l.toNumber(p));
                else
                    l.error("Bad HUD parameter #" + index + " (expected number)");
            else if(param == long.class || param == Long.class)
                if(luaType == Lua.TNUMBER)
                    parameterArray[sIndex++] = new Long((long)l.toNumber(p));
                else
                    l.error("Bad HUD parameter #" + index + " (expected number)");
            else if(param == float.class || param == Float.class)
                if(luaType == Lua.TNUMBER)
                    parameterArray[sIndex++] = new Float((float)l.toNumber(p));
                else
                    l.error("Bad HUD parameter #" + index + " (expected number)");
            else if(param == double.class || param == Double.class)
                if(luaType == Lua.TNUMBER)
                    parameterArray[sIndex++] = new Double((double)l.toNumber(p));
                else
                    l.error("Bad HUD parameter #" + index + " (expected number)");
            else if(param == String.class)
                if(luaType == Lua.TSTRING)
                    parameterArray[sIndex++] = l.toString(p);
                else
                    l.error("Bad HUD parameter #" + index + " (expected string)");
            index++;
        }

        for(Object r : renderers) {
            if(r.getClass() != c)
                continue;

            try {
                m.invoke(r, parameterArray);
                hadSuccess = true;
            } catch(Exception e) {
                e.printStackTrace();
            }
        }
        return hadSuccess;
    }

    public static int luaCB_HUD(Lua l, LuaPlugin plugin)
    {
        l.pushNil();
        String res = l.checkString(1);
        if(!methods.containsKey(res))
            lookupMethod(plugin.getRenderers(), res);
        if(!methods.containsKey(res)) {
            l.pushNil();
            return 1;
        }
        ClassMethod m = methods.get(res);
        l.pushBoolean(callMethod(plugin.getRenderers(), m, l));
        return 1;
    }
}
