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

package org.jpc;

import java.io.*;
import java.nio.*;
import java.nio.charset.*;
import java.util.*;
import javax.swing.*;

import org.jpc.diskimages.ImageLibrary;
import org.jpc.jrsr.UnicodeInputStream;

import static org.jpc.Exceptions.classes;
import static org.jpc.emulator.memory.codeblock.optimised.MicrocodeSet.*;
import static org.jpc.Revision.getRevision;
import static org.jpc.Revision.getRelease;

public class Misc
{
    private static boolean renameOverSupported;
    public static String emuname;

    public static String getEmuname()
    {
        if(emuname != null)
            return "[" + emuname + "]";
        else
            return "";
    }

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

    public static char charForHexValue(int x)
    {
        switch(x) {
        case 0:  return '0';
        case 1:  return '1';
        case 2:  return '2';
        case 3:  return '3';
        case 4:  return '4';
        case 5:  return '5';
        case 6:  return '6';
        case 7:  return '7';
        case 8:  return '8';
        case 9:  return '9';
        case 10: return 'a';
        case 11: return 'b';
        case 12: return 'c';
        case 13: return 'd';
        case 14: return 'e';
        case 15: return 'f';
        }
        return 'X';
    }

    public static String arrayToString(byte[] array) throws IOException
    {
        if(array == null)
            return null;
        StringBuffer buff = new StringBuffer(2 * array.length);
        for(int i = 0; i < array.length; i++) {
            buff.append(charForHexValue(((int)array[i] & 0xFF) >>> 4));
            buff.append(charForHexValue(array[i] & 0xF));
        }
        return buff.toString();
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

    public static boolean isLinefeed(int ch)
    {
        if(ch == 10)
            return true;
        if(ch == 13)
            return true;
        if(ch == 28)
            return true;
        if(ch == 29)
            return true;
        if(ch == 30)
            return true;
        if(ch == 0x85)
            return true;
        if(ch == 0x2029)
            return true;
        return false;
    }

    public static boolean isspace(char ch)
    {
        return isspace((int)ch);
    }

    public static boolean isspace(int ch)
    {
        if(ch == 12)
            return true;
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

    public static String componentEscape(String in)
    {
        boolean needEscape = false;
        boolean needParens = false;
        Stack<Integer> parens = new Stack<Integer>();
        Stack<Integer> parens2 = new Stack<Integer>();

        int strlen = in.length();
        for(int i = 0; i < strlen; i++) {
            char ch = in.charAt(i);
            if(isspace(ch))
                needParens = true;
            if(ch == '\\') {
                needEscape = true;
            } if(ch == '(') {
                needParens = true;
                parens.push(new Integer(i));
            } else if(ch == ')') {
                if(!parens.empty())
                    parens.pop();
                else
                    needEscape = true;
            }
        }

        if(!parens.empty())
            needEscape = true;

        //Copy the paren stack to another to reverse it.
        while(!parens.empty())
            parens2.push(parens.pop());

        if(!needEscape && !needParens)
            return in;

        StringBuilder out = new StringBuilder();
        if(needParens)
            out.append('(');

        if(needEscape) {
            int parenDepth = 0;
            for(int i = 0; i < strlen; i++) {
                char ch = in.charAt(i);
                if(ch == '\\') {
                    out.append("\\\\");
                } else if(!parens2.empty() && parens2.peek().intValue() == i) {
                    out.append("\\(");
                    parens2.pop();
                } else if(ch == '(') {
                    out.append("(");
                    parenDepth++;
                } else if(ch == ')') {
                    if(parenDepth > 0) {
                        out.append(")");
                        parenDepth--;
                    } else
                        out.append("\\)");
                } else
                    out.append(ch);
            }
        } else
            out.append(in);

        if(needParens)
            out.append(')');

        return out.toString();
    }

    public static String castToString(Object obj) throws NumberFormatException
    {
        if(obj == null)
            throw new NumberFormatException("Trying to cast null into string");
        return obj.toString();
    }

    public static long parseHexToNumber(String str) throws NumberFormatException
    {
        int l = str.length();
        long v = 0;
        long old_v = 0;
        if(l == 2)
            throw new NumberFormatException("Bad number '0x'");
        for(int i = 2; i < l; i++) {
            old_v = v;
            char c = str.charAt(i);
            if(c == '0') v = 16 * v + 0;
            else if(c == '1') v = 16 * v + 1;
            else if(c == '2') v = 16 * v + 2;
            else if(c == '3') v = 16 * v + 3;
            else if(c == '4') v = 16 * v + 4;
            else if(c == '5') v = 16 * v + 5;
            else if(c == '6') v = 16 * v + 6;
            else if(c == '7') v = 16 * v + 7;
            else if(c == '8') v = 16 * v + 8;
            else if(c == '9') v = 16 * v + 9;
            else if(c == 'a' || c == 'A') v = 16 * v + 10;
            else if(c == 'b' || c == 'B') v = 16 * v + 11;
            else if(c == 'c' || c == 'C') v = 16 * v + 12;
            else if(c == 'd' || c == 'D') v = 16 * v + 13;
            else if(c == 'e' || c == 'E') v = 16 * v + 14;
            else if(c == 'f' || c == 'F') v = 16 * v + 15;
            else throw new NumberFormatException("Bad number '" + str + "'");
            if(old_v > v) throw new NumberFormatException("Number '" + str + "' is too large");
        }
        return v;
    }

    public static long parseDecToNumber(String str) throws NumberFormatException
    {
        int l = str.length();
        long v = 0;
        long old_v = 0;
        int i = 0;
        boolean neg = false;
        if(l == 0 || (l == 1 && str.charAt(0) == '-'))
            throw new NumberFormatException("Bad number '" + str + "'");
        if(str.charAt(0) == '-') {
            neg = true;
            i = 1;
        }
        for(; i < l; i++) {
            old_v = v;
            char c = str.charAt(i);
            if(c == '0') v = 10 * v + 0;
            else if(c == '1') v = 10 * v + 1;
            else if(c == '2') v = 10 * v + 2;
            else if(c == '3') v = 10 * v + 3;
            else if(c == '4') v = 10 * v + 4;
            else if(c == '5') v = 10 * v + 5;
            else if(c == '6') v = 10 * v + 6;
            else if(c == '7') v = 10 * v + 7;
            else if(c == '8') v = 10 * v + 8;
            else if(c == '9') v = 10 * v + 9;
            else throw new NumberFormatException("Bad number '" + str + "'");
            if(old_v > v) throw new NumberFormatException("Number '" + str + "' is too large");
        }
        return neg ? -v : v;
    }

    public static long parseStringToNumber(String str) throws NumberFormatException
    {
        if(str.length() > 2 && str.substring(0, 2).equals("0x"))
            return parseHexToNumber(str);
        else
            return parseDecToNumber(str);
    }

    public static byte castToByte(Object obj) throws NumberFormatException
    {
        if(obj == null)
            throw new NumberFormatException("Trying to cast null into number");
        Class oClass = obj.getClass();
        if(oClass == Byte.class)
            return ((Byte)obj).byteValue();
        String value = obj.toString();
        long val = parseStringToNumber(value);
        if(val < -128 || val > 255)
            throw new NumberFormatException("Number " + val + " out of range for byte");
        return (byte)val;
    }

    public static short castToShort(Object obj) throws NumberFormatException
    {
        if(obj == null)
            throw new NumberFormatException("Trying to cast null into number");
        Class oClass = obj.getClass();
        if(oClass == Short.class)
            return ((Short)obj).shortValue();
        String value = obj.toString();
        long val = parseStringToNumber(value);
        if(val < -32768 || val > 65535)
            throw new NumberFormatException("Number " + val + " out of range for short");
        return (short)val;
    }

    public static int castToInt(Object obj) throws NumberFormatException
    {
        if(obj == null)
            throw new NumberFormatException("Trying to cast null into number");
        Class oClass = obj.getClass();
        if(oClass == Integer.class)
            return ((Integer)obj).intValue();
        String value = obj.toString();
        long val = parseStringToNumber(value);
        if(val < -2147483648 || val > 4294967295L)
            throw new NumberFormatException("Number " + val + " out of range for int");
        return (int)val;
    }

    public static long castToLong(Object obj) throws NumberFormatException
    {
        if(obj == null)
            throw new NumberFormatException("Trying to cast null into number");
        Class oClass = obj.getClass();
        if(oClass == Long.class)
            return ((Long)obj).longValue();
        String value = obj.toString();
        return parseStringToNumber(value);
    }

    public static boolean castToBoolean(Object obj) throws NumberFormatException
    {
        if(obj == null)
            throw new NumberFormatException("Trying to cast null into boolean");
        String value = obj.toString();
        if(value.equals("0") || value.equalsIgnoreCase("no") || value.equalsIgnoreCase("off") ||
            value.equalsIgnoreCase("false"))
            return false;
        if(value.equals("1") || value.equalsIgnoreCase("yes") || value.equalsIgnoreCase("on") ||
            value.equalsIgnoreCase("true"))
            return true;
        throw new NumberFormatException("Invalid boolean '" + value + "'");
    }

    public static String componentUnescape(String in) throws IOException
    {
        if(in.indexOf('\\') < 0)
            return in;   //No escapes.
        StringBuilder out = new StringBuilder();
        boolean escapeActive = false;
        int strlen = in.length();
        for(int i = 0; i < strlen; i++) {
            char ch = in.charAt(i);
            if(escapeActive) {
                out.append(ch);
                escapeActive = false;
            } else if(ch == '\\') {
                escapeActive = true;
            } else
                out.append(ch);
        }
        if(escapeActive)
            throw new IOException("Invalid escaped string: unexpected end of string after \\");
        return out.toString();
    }

    public static String[] nextParseLine(UnicodeInputStream in) throws IOException
    {
        String[] ret = null;

        String parseLine = "";
        while(parseLine != null && "".equals(parseLine))
            parseLine = in.readLine();
        if(parseLine == null)
            return null;

        ret = parseString(parseLine);

        if(ret == null)
            return nextParseLine(in);

        return ret;
    }

    public static String[] parseString(String parseLine) throws IOException
    {
        ArrayList<String> ret = new ArrayList<String>();
        boolean escapeActive = false;

        if(parseLine == null)
            return null;

        //System.err.println("Line: \"" + parseLine + "\".");
        int parenDepth = 0;
        int lastSplitStart = 0;
        int strlen = parseLine.length();

        for(int i = 0; i < strlen; i++) {
            String component = null;
            char ch = parseLine.charAt(i);
            if(escapeActive) {
                escapeActive = false;
            } else if(ch == '\\') {
                escapeActive = true;
            } else if(ch == '(') {
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
                    throw new IOException("Unbalanced ) in component line \"" + parseLine + "\".");
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
                component = componentUnescape(parseLine.substring(lastSplitStart, i));
                lastSplitStart = i + 1;
            }

            if(component != null && !component.equals(""))
                ret.add(component);
        }
        if(parenDepth > 0)
            throw new IOException("Unbalanced ( in component line \"" + parseLine + "\".");
        String component = componentUnescape(parseLine.substring(lastSplitStart));
        if(component != null && !component.equals(""))
            ret.add(component);

        if(!ret.isEmpty())
            return (String[])ret.toArray(new String[ret.size()]);
        else
            return null;
    }

    public static boolean hasParensInserted(String in)
    {
        return (in.charAt(0) == '(');
    }

    public static String encodeLine(String[] components)
    {
        String s = "";
        boolean lastParen = true; //Hack to supress initial space.
        for(int i = 0; i < components.length; i++) {
            String escaped = componentEscape(components[i]);
            boolean thisParen = hasParensInserted(escaped);
            if(!lastParen && !thisParen)
                s = s + " ";
            s = s + escaped;
            lastParen = thisParen;
        }
        return s;
    }

    public static int callShowOptionDialog(java.awt.Component parent, Object msg, String title, int oType,
        int mType, Icon icon, Object[] buttons, Object deflt)
    {
        try {
            return JOptionPane.showOptionDialog(parent, msg, title, oType, mType, icon, buttons, deflt);
        } catch(Throwable e) {   //Catch errors too!
            //No GUI available.
            System.err.println("MESSAGE: *** " + title + " ***: " + msg.toString());
            for(int i = 0; i < buttons.length; i++)
                if(buttons[i] == deflt)
                    return i;
            return 0;
        }
    }


    public static String messageForException(Throwable e, boolean chase)
    {
        boolean supressClass = false;
        while(chase && e.getCause() != null)
            e = e.getCause();
        String message = e.getMessage();
        Class<?> eClass = e.getClass();
        while(eClass != null) {
            if(classes.containsKey(eClass.getName())) {
                if(message != null && !message.equals("") && !message.equals("null"))
                    message = classes.get(eClass.getName()) + " (" + message + ")";
                else
                    message = classes.get(eClass.getName());
                if(eClass == e.getClass())
                    supressClass = true;
                break;
            }
            eClass = eClass.getSuperclass();
        }

        if(!supressClass)
            if(message != null && !message.equals("") && !message.equals("null"))
                message = message + " [" + e.getClass().getName() + "]";
            else
                message = message + "<no description available> [" + e.getClass().getName() + "]";
        return message;
    }

    public static void errorDialog(Throwable e, String title, java.awt.Component component, String text)
    {
        String message = messageForException(e, true);
        int i = callShowOptionDialog(null, message, title, JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE, null, new String[]{text, "Save stack trace"}, "Save stack Trace");
        if(i > 0) {
            saveStackTrace(e, null, text);
        }
    }

    public static String formatStackTrace(StackTraceElement[] traceback)
    {
        StringBuffer sb = new StringBuffer();
        if(traceback != null && traceback.length == 0) {
            return "No Stack frame information available.\n";
        }

        if(traceback != null)
            for(StackTraceElement el : traceback) {
                if(el.getClassName().startsWith("sun.reflect."))
                    continue; //Clean up the trace a bit.
                if(el.isNativeMethod())
                    sb.append(el.getMethodName() + " of " + el.getClassName() + " <native>\n");
                else if(el.getFileName() != null)
                    sb.append(el.getMethodName() + " of " + el.getClassName() + " <" + el.getFileName() + ":" +
                        el.getLineNumber() + ">\n");
                else
                    sb.append(el.getMethodName() + " of " + el.getClassName() + " <no location available>\n");
        }
        return sb.toString();
    }

    public static void saveStackTrace(Throwable e, java.awt.Component component, String text)
    {
        StringBuffer sb = new StringBuffer();
        sb.append("Exception trace generated on '" + (new Date()).toString()  + "' by version '" + getRevision() + "' (release " + getRelease() + ").\n\n");

        while(true) {
            sb.append(messageForException(e, false) + "\n");
            sb.append(formatStackTrace(e.getStackTrace()));
            if(e.getCause() != null) {
                e = e.getCause();
                sb.append("\nCaused By:\n\n");
            } else
                break;
        }
        String exceptionMessage = sb.toString();

        try {
            ByteBuffer buf;
            buf = Charset.forName("UTF-8").newEncoder().encode(CharBuffer.wrap(exceptionMessage));
            byte[] buf2 = new byte[buf.remaining()];
            buf.get(buf2);

            String traceFileName = "StackTrace-" + System.currentTimeMillis() + ".text";
            OutputStream stream = new FileOutputStream(traceFileName);
            stream.write(buf2);
            stream.close();
            callShowOptionDialog(component, "Stack trace saved to " + traceFileName + ".", "Stack trace saved", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE, null, new String[]{text}, text);
        } catch(Exception e2) {
            callShowOptionDialog(component, e.getMessage(), "Saving stack trace failed", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE, null, new String[]{text}, text);
        }
    }

    public static void doCrashDump(OutputStream out) throws Exception
    {
        StringBuffer sb = new StringBuffer();
        sb.append("Crash trace generated on '" + (new Date()).toString()  + "' by version '" + getRevision() + "' (release " + getRelease() + ").\n\n");
        Map<Thread, StackTraceElement[]> traces = Thread.getAllStackTraces();
        for(Map.Entry<Thread, StackTraceElement[]> thread : traces.entrySet()) {
            sb.append("Thread #" + thread.getKey().getId() + "(" + thread.getKey().getName() + "):\n");
            sb.append(formatStackTrace(thread.getValue()));
            sb.append("\n");
        }
        String dump = sb.toString();

        ByteBuffer buf;
        buf = Charset.forName("UTF-8").newEncoder().encode(CharBuffer.wrap(dump));
        byte[] buf2 = new byte[buf.remaining()];
        buf.get(buf2);
        out.write(buf2);
    }

    public static Map<String, String> parseStringToComponents(String string) throws IOException
    {
        Map<String,String> ret = new HashMap<String, String>();
        while(!string.equals("")) {
            int i = string.indexOf(',');
            String element;
            if(i < 0) {
                element = string;
                string = "";
            } else {
                element = string.substring(0, i);
                string = string.substring(i + 1);
            }
            int j = element.indexOf('=');
            if(j < 0)
                throw new IOException("Bad string element: \"" + element + "\"");
            String key = element.substring(0, j);
            String value = element.substring(j + 1);
            ret.put(key, value);
        }
        return ret;
    }

    public static Map<String, String> parseStringsToComponents(String[] string) throws IOException
    {
        Map<String,String> ret = new HashMap<String, String>();
        if(string == null)
            return ret;
        for(String element : string) {
            int j = element.indexOf('=');
            if(j < 0)
                throw new IOException("Bad string element: \"" + element + "\"");
            String key = element.substring(0, j);
            String value = element.substring(j + 1);
            ret.put(key, value);
        }
        return ret;
    }


    public static InputStream openStream(String name, String defaultName)
    {
        InputStream ret = null;
        if(name != null) {
            try {
                ret = new FileInputStream(name);
            } catch(Exception e) {
                ret = ClassLoader.getSystemResourceAsStream(name);
           }
           if(ret != null)
               return ret;
        }
        if(defaultName != null) {
            System.err.println("Error: Can't open '" + name + "' falling back to default of '" + defaultName + "'.");
            ret = ClassLoader.getSystemResourceAsStream(defaultName);
        }
        if(ret == null)
            System.err.println("Error: Can't open '" + name + "' nor default fallback.");
        return ret;
    }

    public static void renameFile(File src, File dest) throws IOException
    {
        if(!src.exists())
            return;
        if(renameOverSupported) {
            System.err.println("Informational: Renaming file...");
            if(!src.renameTo(dest))
                throw new IOException("Failed to rename '" + src.getAbsolutePath() + "' to '" + dest.getAbsolutePath() + "'.");
        } else {
            System.err.println("Informational: Copying & deleting file...");
            FileInputStream srch = new FileInputStream(src);
            FileOutputStream desth = new FileOutputStream(dest);
            byte[] copyBuffer = new byte[1024];
            int r = 0;
            while((r = srch.read(copyBuffer)) >= 0)
                desth.write(copyBuffer, 0, r);
            srch.close();
            desth.close();
            src.delete();
        }
    }

    public static void probeRenameOver(boolean forceFalse)
    {
        File file1 = null;
        File file2 = null;
        try {
            if(forceFalse)
                throw new IOException("Rename-over forced off");
            String name1 = randomHexes(24);
            String name2 = randomHexes(24);
            RandomAccessFile fh1 = new RandomAccessFile(name1, "rw");
            RandomAccessFile fh2 = new RandomAccessFile(name2, "rw");
            fh1.close();
            fh2.close();
            file1 = new File(name1);
            file2 = new File(name2);
            if(!file1.renameTo(file2))
                throw new IOException("Rename-over test failed");
            file1.delete();
            file2.delete();
        } catch(IOException e) {
            System.err.println("Informational: Probing if rename-over works...no: " + e.getMessage());
            System.err.println("Notice: Using copy & delete for file overwrites.");
            if(file1 != null)
                file1.delete();
            if(file2 != null)
                file2.delete();
            return;
        }
        System.err.println("Informational: Probing if rename-over works...yes.");
        System.err.println("Notice: Using rename-over for file overwrites.");
        renameOverSupported = true;
    }

    public static void moveWindow(JFrame window, int x, int y, int w, int h)
    {
        final int x2 = x;
        final int y2 = y;
        final int w2 = w;
        final int h2 = h;
        final JFrame window2 = window;

        if(!SwingUtilities.isEventDispatchThread())
            try {
                SwingUtilities.invokeAndWait(new Thread() { public void run() {
                    window2.setBounds(x2, y2, w2, h2); }});
            } catch(Exception e) {
            }
        else
            window2.setBounds(x2, y2, w2, h2);
    }

    public static boolean isFPUOp(int op)
    {
        switch(op) {
        case FWAIT:
        case FLOAD0_ST0:
        case FLOAD0_STN:
        case FLOAD0_MEM_SINGLE:
        case FLOAD0_MEM_DOUBLE:
        case FLOAD0_MEM_EXTENDED:
        case FLOAD0_REG0:
        case FLOAD0_REG0L:
        case FLOAD0_1:
        case FLOAD0_L2TEN:
        case FLOAD0_L2E:
        case FLOAD0_PI:
        case FLOAD0_LOG2:
        case FLOAD0_LN2:
        case FLOAD0_POS0:
        case FLOAD1_ST0:
        case FLOAD1_STN:
        case FLOAD1_MEM_SINGLE:
        case FLOAD1_MEM_DOUBLE:
        case FLOAD1_MEM_EXTENDED:
        case FLOAD1_REG0:
        case FLOAD1_REG0L:
        case FLOAD1_POS0:
        case FSTORE0_ST0:
        case FSTORE0_STN:
        case FSTORE0_MEM_SINGLE:
        case FSTORE0_MEM_DOUBLE:
        case FSTORE0_MEM_EXTENDED:
        case FSTORE0_REG0:
        case FSTORE1_ST0:
        case FSTORE1_STN:
        case FSTORE1_MEM_SINGLE:
        case FSTORE1_MEM_DOUBLE:
        case FSTORE1_MEM_EXTENDED:
        case FSTORE1_REG0:
        case LOAD0_FPUCW:
        case STORE0_FPUCW:
        case LOAD0_FPUSW:
        case STORE0_FPUSW:
        case FPOP:
        case FPUSH:
        case FADD:
        case FMUL:
        case FCOM:
        case FUCOM:
        case FCOMI:
        case FUCOMI:
        case FSUB:
        case FDIV:
        case FCHS:
        case FABS:
        case FXAM:
        case F2XM1:
        case FYL2X:
        case FPTAN:
        case FPATAN:
        case FXTRACT:
        case FPREM1:
        case FDECSTP:
        case FINCSTP:
        case FPREM:
        case FYL2XP1:
        case FSQRT:
        case FSINCOS:
        case FRNDINT:
        case FSCALE:
        case FSIN:
        case FCOS:
        case FRSTOR_94:
        case FRSTOR_108:
        case FSAVE_94:
        case FSAVE_108:
        case FFREE:
        case FBCD2F:
        case FF2BCD:
        case FLDENV_14:
        case FLDENV_28:
        case FSTENV_14:
        case FSTENV_28:
        case FCMOVB:
        case FCMOVE:
        case FCMOVBE:
        case FCMOVU:
        case FCMOVNB:
        case FCMOVNE:
        case FCMOVNBE:
        case FCMOVNU:
        case FCHOP:
        case FCLEX:
        case FINIT:
        case FCHECK0:
        case FCHECK1:
        case FXSAVE:
            return true;
        default:
            return false;
        }
    }
}
