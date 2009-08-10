/*
    JPC: A x86 PC Hardware Emulator for a pure Java Virtual Machine
    Release Version 2.0

    A project from the Physics Dept, The University of Oxford

    Copyright (C) 2007 Isis Innovation Limited
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

    Details (including contact information) can be found at:

    www.physics.ox.ac.uk/jpc
*/

package org.jpc;

import java.io.*;
import org.jpc.diskimages.ImageLibrary;
import org.jpc.jrsr.JRSRArchiveReader;
import org.jpc.support.UTFInputLineStream;

public class Misc
{
    public static String randomHexes(int bytes)
    {
        java.security.SecureRandom prng = new java.security.SecureRandom();
        byte[] rnd = new byte[bytes];
        prng.nextBytes(rnd);
        StringBuffer buf = new StringBuffer(2 * rnd.length);
        for(int i = 0; i < rnd.length; i++) {
            int b = (int)rnd[i] & 0xFF;
            buf.append(Character.forDigit(b / 16, 16));
            buf.append(Character.forDigit(b % 16, 16));
        }
        return buf.toString();
    }

    public static String tempname(String prefix)
    {
        //As we don't create files atomically, we need to be unpredictable.
        return prefix + "." + randomHexes(12);
    }

    public static String arrayToString(byte[] array) throws IOException
    {   
        if(array == null)
            return null;
        return (new ImageLibrary.ByteArray(array)).toString();
    }

    public static byte[] stringToArray(String name) throws IOException
    {   
        if(name == null)
            return null;

        if((name.length() % 2) != 0)
            throw new IOException("Trying to transform odd-length string into byte array");
        int l = name.length() / 2;
        byte[] parsed = new byte[l];
        for(int i = 0; i < l; i++)
            parsed[i] = (byte)(Character.digit(name.charAt(2 * i), 16) * 16 +
                Character.digit(name.charAt(2 * i + 1), 16));

        return parsed;
    }

    public static boolean isspace(char ch)
    {
        if(ch == 32)
            return true;
        if(ch == 9)
            return true;
        if(ch == 0x1680)
            return true;
        if(ch == 0x180E)
            return true;
        if(ch >= 0x2000 && ch <= 0x200A)
            return true;
        if(ch == 0x2028)
            return true;
        if(ch == 0x205F)
            return true;
        if(ch == 0x3000)
            return true;
        return false;
    }

    public static String[] nextParseLine(UTFInputLineStream in) throws IOException
    {
        String[] ret = null;

        String parseLine = "";
        while(parseLine != null && "".equals(parseLine))
            parseLine = in.readLine();
        if(parseLine == null)
            return null;

        //System.err.println("Line: \"" + parseLine + "\".");
            
        int parenDepth = 0;
        int lastSplitStart = 0;
        int strlen = parseLine.length();

        for(int i = 0; i < strlen; i++) {
            String component = null;
            char ch = parseLine.charAt(i);
            if(ch == '(') {
                if(parenDepth > 0)
                    parenDepth++;
                else if(parenDepth == 0) {
                    //Split here.
                    component = parseLine.substring(lastSplitStart, i);
                    lastSplitStart = i + 1;
                    parenDepth++;
                }
            } else if(ch == ')') {
                if(parenDepth == 0)
                    throw new IOException("Unbalanced ) in initialization segment line \"" + parseLine + "\".");
                else if(parenDepth == 1) {
                    //Split here.
                    component = parseLine.substring(lastSplitStart, i);
                    lastSplitStart = i + 1;
                    parenDepth--;
                } else
                    parenDepth--;
            } else if(parenDepth == 0 && isspace(ch)) {
                //Split here.
                //System.err.println("Splitting at point " + i + ".");
                component = parseLine.substring(lastSplitStart, i);
                lastSplitStart = i + 1;
            }

            if(component != null && !component.equals(""))
                if(ret != null) {
                    String[] ret2 = new String[ret.length + 1];
                    System.arraycopy(ret, 0, ret2, 0, ret.length);
                    ret2[ret.length] = component;
                    ret = ret2;
                } else
                    ret = new String[]{component};
        }
        if(parenDepth > 0)
            throw new IOException("Unbalanced ( in initialization segment line \"" + parseLine + "\".");
        String component = parseLine.substring(lastSplitStart);
        if(component != null && !component.equals(""))
            if(ret != null) {
                String[] ret2 = new String[ret.length + 1];
                System.arraycopy(ret, 0, ret2, 0, ret.length);
                ret2[ret.length] = component;
                ret = ret2;
            } else
                ret = new String[]{component};

        return ret;
    }
}
