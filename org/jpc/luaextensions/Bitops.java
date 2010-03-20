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

import java.util.*;

import org.jpc.plugins.LuaPlugin;

//Locking this class is used for preventing termination and when terminating.
public class Bitops extends LuaPlugin.LuaResource
{
    private static long BIT_MASK = 0xFFFFFFFFFFFFL;
    private static long BIT_HIGH = 0x800000000000L;
    private static long BITS = 48;

    public void destroy()
    {
    }

    private Bitops(LuaPlugin plugin)
    {
        super(plugin);
    }

    private static int intLuaCBBitwise(Lua l, int op)
    {
        long res = 0;
        int i = 1;

        if(op == 0 || op == 3)
            res = BIT_MASK;

        while(l.type(i) == Lua.TNUMBER) {
            long arg = (long)(l.checkNumber(i)) & BIT_MASK;
            if(op == 0)
                res = res & ~arg;                    //NONE.
            else if(op == 1)
                res = res | arg;                     //ANY.
            else if(op == 2)
                res = res ^ arg;                     //PARITY.
            else if(op == 3)
                res = res & arg;                     //ALL.
            else if(op == 4)
                res = (res + arg) & BIT_MASK;        //ADD.
            else if(op == 5)
                res = (res - arg) & BIT_MASK;        //ADDNEG.
            else if(op == 6 && i % 2 == 1)
                res = (res + arg) & BIT_MASK;        //ADDALT.
            else if(op == 6 && i % 2 == 0)
                res = (res - arg) & BIT_MASK;       //ADDALT.
            i++;
        }
        if(l.type(i) != Lua.TNONE) {
            l.error("All bitwise operation arguments must be numbers");
            return 0;
        }

        l.pushNumber((double)res);
        return 1;
    }

    public static int luaCB_none(Lua l, LuaPlugin plugin)
    {
        return intLuaCBBitwise(l, 0);
    }

    public static int luaCB_any(Lua l, LuaPlugin plugin)
    {
        return intLuaCBBitwise(l, 1);
    }

    public static int luaCB_parity(Lua l, LuaPlugin plugin)
    {
        return intLuaCBBitwise(l, 2);
    }

    public static int luaCB_all(Lua l, LuaPlugin plugin)
    {
        return intLuaCBBitwise(l, 3);
    }

    public static int luaCB_add(Lua l, LuaPlugin plugin)
    {
        return intLuaCBBitwise(l, 4);
    }

    public static int luaCB_addneg(Lua l, LuaPlugin plugin)
    {
        return intLuaCBBitwise(l, 5);
    }

    public static int luaCB_addalt(Lua l, LuaPlugin plugin)
    {
        return intLuaCBBitwise(l, 6);
    }

    private static int intLuaCBShift(Lua l, int op)
    {
        long res = 0;
        long shift = 0;

        l.pushNil();
        l.pushNil();
        res = (long)l.checkNumber(1) & BIT_MASK;
        shift = (long)l.checkNumber(2);

        if(shift < 0 || shift >= BITS)
            l.error("Invalid shift count: " + shift);

        if(op == 0)
            res = res << shift;
        else if(op == 1)
            res = res >>> shift;
        else if(op == 2) {
            for(int i = 0; i < shift; i++) {
                 boolean neg = ((res & BIT_HIGH) != 0);
                 res = res >>> 1;
                 if(neg)
                     res = res | BIT_HIGH;
            }
        } else if(op == 3)
            res = (res >>> (BITS - shift)) | (res << shift);
        else if(op == 4)
            res = (res << (BITS - shift)) | (res >>> shift);

        l.pushNumber((double)(res & BIT_MASK));
        return 1;
    }

    public static int luaCB_lshift(Lua l, LuaPlugin plugin)
    {
        return intLuaCBShift(l, 0);
    }

    public static int luaCB_rshift(Lua l, LuaPlugin plugin)
    {
        return intLuaCBShift(l, 1);
    }

    public static int luaCB_arshift(Lua l, LuaPlugin plugin)
    {
        return intLuaCBShift(l, 2);
    }

    public static int luaCB_rol(Lua l, LuaPlugin plugin)
    {
        return intLuaCBShift(l, 3);
    }

    public static int luaCB_ror(Lua l, LuaPlugin plugin)
    {
        return intLuaCBShift(l, 4);
    }

    public static int luaCB_bswap2(Lua l, LuaPlugin plugin)
    {
        long res = 0;

        l.pushNil();
        res = (long)l.checkNumber(1) & BIT_MASK;

        res = (res & ~0xFFFFL) | ((res & 0xFF) << 8) | ((res & 0xFF00) >> 8);

        l.pushNumber((double)(res & BIT_MASK));
        return 1;
    }

    public static int luaCB_bswap3(Lua l, LuaPlugin plugin)
    {
        long res = 0;

        l.pushNil();
        res = (long)l.checkNumber(1) & BIT_MASK;

        res = (res & ~0xFF00FFL) | ((res & 0xFF) << 16) | ((res & 0xFF0000) >> 16);

        l.pushNumber((double)(res & BIT_MASK));
        return 1;
    }

    public static int luaCB_bswap4(Lua l, LuaPlugin plugin)
    {
        long res = 0;

        l.pushNil();
        res = (long)l.checkNumber(1) & BIT_MASK;

        res = (res & ~0xFFFFFFFFL) | ((res & 0xFF) << 24) | ((res & 0xFF00) << 8) |
            ((res & 0xFF0000) >> 8) | ((res & 0xFF000000L) >> 24);

        l.pushNumber((double)(res & BIT_MASK));
        return 1;
    }

    public static int luaCB_bswap5(Lua l, LuaPlugin plugin)
    {
        long res = 0;

        l.pushNil();
        res = (long)l.checkNumber(1) & BIT_MASK;

        res = (res & ~0xFFFFFFFFL) | ((res & 0xFF) << 32) | ((res & 0xFF00) << 16) |
            ((res & 0xFF000000L) >> 16) | ((res & 0xFF00000000L) >> 32);

        l.pushNumber((double)(res & BIT_MASK));
        return 1;
    }

    public static int luaCB_bswap6(Lua l, LuaPlugin plugin)
    {
        long res = 0;

        l.pushNil();
        res = (long)l.checkNumber(1) & BIT_MASK;

        res = (res & ~0xFFFFFFFFL) | ((res & 0xFF) << 40) | ((res & 0xFF00) << 24) |
            ((res & 0xFF000000L) << 8) | ((res & 0xFF00000000L) >> 8) |
            ((res & 0xFF0000000000L) >> 24) | ((res & 0xFF000000000000L) >> 40);

        l.pushNumber((double)(res & BIT_MASK));
        return 1;
    }

    public static int luaCB_tohex(Lua l, LuaPlugin plugin)
    {
        long res = 0;

        l.pushNil();
        res = (long)l.checkNumber(1) & BIT_MASK;

        l.push((new Formatter()).format("%012X", res).toString());
        return 1;
    }
}
