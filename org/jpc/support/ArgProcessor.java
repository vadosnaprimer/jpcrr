/*
    JPC: A x86 PC Hardware Emulator for a pure Java Virtual Machine
    Release Version 2.0

    A project from the Physics Dept, The University of Oxford

    Copyright (C) 2007 Isis Innovation Limited

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

    Details (including contact information) can be found at:

    www.physics.ox.ac.uk/jpc
*/

package org.jpc.support;

import java.io.*;
import java.net.*;
import java.util.*;

public class ArgProcessor
{
    public static String findArg(String[] args, String key, String defaultValue)
    {
        if (key.startsWith("-"))
            key = key.substring(1);

        for (int i=0; i<args.length-1; i++)
        {
            String arg = args[i];
            if (arg.startsWith("-"))
                arg = arg.substring(1);

            if (arg.equalsIgnoreCase(key))
                return args[i+1];
        }

        return defaultValue;
    }

    public static boolean argExists(String[] args, String key)
    {
        if (key.startsWith("-"))
            key = key.substring(1);

        for (int i=0; i<args.length; i++)
        {
            String arg = args[i];
            if (arg.startsWith("-"))
                arg = arg.substring(1);

            if (arg.equalsIgnoreCase(key))
                return true;
        }

        return false;
    }

    public static boolean checkForFlag(String[] args, String flag)
    {
        for (int i=0; i<args.length; i++)
        {
            if (!args[i].startsWith("-"))
                continue;
            if (args[i].substring(1).toLowerCase().equals(flag.toLowerCase()))
                return true;
        }

        return false;
    }

    public static String scanArgs(String[] args, String key, String defaultValue)
    {
        for (int i=0; i<args.length-1; i++)
        {
            if (!args[i].startsWith("-"))
                continue;
            if (!args[i].substring(1).toLowerCase().equals(key.toLowerCase()))
                continue;
            return args[i+1];
        }

        return defaultValue;
    }

    public static int extractIntArg(String[] args, String key, int defaultValue)
    {
        try
        {
            return Integer.parseInt(scanArgs(args, key, null));
        }
        catch (Exception e)
        {
            return defaultValue;
        }
    }

    public static int[] decodeIntegerList(String list)
    {
        return decodeIntegerList(list, ',');
    }

    public static int[] decodeIntegerList(String list, char delimeter)
    {
        int[] result = new int[0];

        try
        {
            StringTokenizer tokens = new StringTokenizer(list, ""+delimeter);

            Vector pp = new Vector();
            while (tokens.hasMoreTokens())
            {
                try
                {
                    pp.add(Integer.valueOf(tokens.nextToken().trim()));
                }
                catch (Exception e){}
            }

            result = new int[pp.size()];
            for (int i=0; i<result.length; i++)
                result[i] = ((Integer) pp.elementAt(i)).intValue();
        }
        catch (Exception e) {}

        return result;
    }
}
