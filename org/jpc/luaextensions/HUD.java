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
import java.util.*;
import java.lang.reflect.*;
import org.jpc.hud.HUDRenderer;
import org.jpc.hud.RenderObject;
import org.jpc.hud.Bitmap;
import org.jpc.plugins.LuaPlugin;
import org.jpc.bitmaps.DecodedBitmap;
import org.jpc.bitmaps.BitmapDecoder;

public class HUD extends LuaPlugin.LuaResource
{
    static Map<String, Class<?>> classes;

    static
    {
        classes = new HashMap<String, Class<?>>();
    }

    public void destroy()
    {
    }

    private HUD(LuaPlugin plugin)
    {
        super(plugin);
    }

    private static void lookupClass(String name)
    {
        String cName = "org.jpc.hud.objects." + name;
        try {
            classes.put(name, Class.forName(cName));
        } catch(Exception e) {
        }
    }

    private static boolean callMethod(Object[] renderers, Class<?> cm, Lua l, int flags, LuaPlugin plugin)
    {
        RenderObject o = createObject(cm, l, plugin);
        if(o == null)
            return false;
        for(Object r : renderers)
            ((HUDRenderer)r).addObject(flags, o);
        return true;
    }

    private static RenderObject createObject(Class<?> cm, Lua l, LuaPlugin plugin)
    {
        Constructor[] cl = cm.getDeclaredConstructors();
        for(Constructor c : cl) {
            RenderObject o = createWithConstructor(c, l, plugin);
            if(o != null)
                return o;
        }
        return null;
    }

    private static RenderObject createWithConstructor(Constructor c, Lua l, LuaPlugin plugin)
     {
        Class<?>[] paramTypes = c.getParameterTypes();

        Object[] parameterArray = new Object[paramTypes.length];
        int index = 3;
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
            else /* Assume this is an object */
                if(luaType == Lua.TUSERDATA) {
                    Object u = l.toUserdata(p).getUserdata();
                    Object or = plugin.resolveWrappedObject(l, u);
                    if(!(or instanceof ObjectWrapper))
                        l.error("Bad HUD parameter #" + index + " (expected wrapped object)");
                    Object o = ((ObjectWrapper)or).get();
                    if(!(param.isAssignableFrom(o.getClass())))
                        l.error("Bad HUD parameter #" + index + " (wrong type of wrapped object)");
                    parameterArray[sIndex++] = o;
                } else
                    l.error("Bad HUD parameter #" + index + " (expected userdata)");
            index++;
        }

        try {
            return (RenderObject)c.newInstance(parameterArray);
        } catch(Exception e) {
            return null;
        }
    }

    public static int luaCB_HUD(Lua l, LuaPlugin plugin)
    {
        l.pushNil();
        l.pushNil();
        l.pushNil();
        String res = l.checkString(1);
        //Some methods are special.
        if(res.equals("left_gap")) {
            int flags = l.checkInt(2);
            int amount = l.checkInt(3);
            for(Object i : plugin.getRenderers())
                ((HUDRenderer)i).setLeftGap(flags, amount);
        } else if(res.equals("top_gap")) {
            int flags = l.checkInt(2);
            int amount = l.checkInt(3);
            for(Object i : plugin.getRenderers())
                ((HUDRenderer)i).setTopGap(flags, amount);
        } else if(res.equals("right_gap")) {
            int flags = l.checkInt(2);
            int amount = l.checkInt(3);
            for(Object i : plugin.getRenderers())
                ((HUDRenderer)i).setRightGap(flags, amount);
        } else if(res.equals("bottom_gap")) {
            int flags = l.checkInt(2);
            int amount = l.checkInt(3);
            for(Object i : plugin.getRenderers())
                ((HUDRenderer)i).setBottomGap(flags, amount);
        } else {
            if(!classes.containsKey(res))
                lookupClass(res);
            if(!classes.containsKey(res)) {
                l.pushNil();
                return 1;
            }
            Class<?> c = classes.get(res);
            int flags = l.checkInt(2);
            l.pushBoolean(callMethod(plugin.getRenderers(), c, l, flags, plugin));
            return 1;
        }
        l.pushBoolean(true);
        return 1;
    }

    public static int luaCB_LoadBitmap(Lua l, LuaPlugin plugin)
    {
        //Dimensions.
        int w;
        int h;
        //Raw bitmap data.
        byte[] bitmapR;
        byte[] bitmapG;
        byte[] bitmapB;
        byte[] bitmapRA;
        //Final bitmaps.
        int[] bitmapC;
        int[] bitmapA;

        l.pushNil();
        String name = l.checkString(1);

        try {
            DecodedBitmap d = BitmapDecoder.decode(name);
            w = d.getW();
            h = d.getH();
            bitmapR = d.getR();
            bitmapG = d.getG();
            bitmapB = d.getB();
            bitmapRA = d.getA();
        } catch(IOException e) {
            l.pushNil();
            l.pushString("IOException: " + e.getMessage());
            return 2;
        }

        //Translate bitmaps and create the object.
        bitmapC = new int[w * h];
        bitmapA = new int[w * h];
        for(int i = 0; i < w * h; i++) {
            bitmapC[i] = (((int)bitmapR[i] & 0xFF) << 16) | (((int)bitmapG[i] & 0xFF) << 8) | ((int)bitmapB[i] & 0xFF);
            bitmapA[i] = ((int)bitmapRA[i] & 0xFF) * 256 / 255;
        }
        plugin.generateLuaClass(l, new ObjectWrapper(plugin, new Bitmap(bitmapC, bitmapA, w, h)));
        return 1;
    }

}
