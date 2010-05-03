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

package org.jpc.j2se;

import java.io.*;
import javax.swing.*;
import java.lang.reflect.*;

import org.jpc.*;
import org.jpc.diskimages.ImageLibrary;
import org.jpc.diskimages.DiskImage;
import org.jpc.pluginsbase.*;

import static org.jpc.Revision.getRevision;
import static org.jpc.Misc.errorDialog;
import static org.jpc.Misc.callShowOptionDialog;
import static org.jpc.Misc.parseString;

public class JPCApplication
{
    public static Plugin instantiatePlugin(Plugins pluginManager, Class<?> plugin, String arguments) throws IOException
    {
        Constructor<?> cc;

        if(arguments != null) {
            try {
                cc = plugin.getConstructor(Plugins.class, String.class);
            } catch(Exception e) {
                throw new IOException("Plugin \"" + plugin.getName() + "\" does not take arguments.");
            }
        } else {
            try {
                cc = plugin.getConstructor(Plugins.class);
            } catch(Exception e) {
                throw new IOException("Plugin \"" + plugin.getName() + "\" requires arguments.");
            }
        }

        try {
            if(arguments != null)
                return (Plugin)cc.newInstance(pluginManager, arguments);
            else
                return (Plugin)cc.newInstance(pluginManager);
        } catch(InvocationTargetException e) {
            Throwable e2 = e.getCause();
            //If the exception is something unchecked, just pass it through.
            if(e2 instanceof RuntimeException)
                throw (RuntimeException)e2;
            if(e2 instanceof Error) {
                IOException ne =  new IOException("Error while invoking loader: " + e2);
                ne.setStackTrace(e2.getStackTrace());  //Copy stack trace.
                throw ne;
            }
            //Also pass IOException through.
            if(e2 instanceof IOException)
                throw (IOException)e2;
            //What the heck is that?
            IOException ne = new IOException("Unknown exception while invoking loader: " + e2);
            ne.setStackTrace(e2.getStackTrace());  //Copy stack trace.
            throw ne;
        } catch(Exception e) {
            throw new IOException("Failed to invoke plugin \"" + plugin.getName() + "\" constructor.");
        }
    }

    private static void loadPlugin(Plugins pluginManager, String arg) throws IOException
    {
        String moduleString = arg;
        String currentModule;
        int parenDepth = 0;
        int nameEnd = -1;
        int paramsStart = -1;
        int paramsEnd = -1;
        int stringLen = moduleString.length();
        boolean requireNextSep = false;

        for(int i = 0; true; i++) {
            int cp;
            if(i < stringLen)
                cp = moduleString.codePointAt(i);
            else if(parenDepth == 0) {
                if(nameEnd < 0)
                    nameEnd = i - 1;
                currentModule = moduleString.substring(0, i);
                if(i < stringLen ) {
                    moduleString = moduleString.substring(i + 1);
                    if(moduleString.equals(""))
                        throw new IOException("Error in module string: Blank module name not allowed.");
                } else
                    moduleString = "";
                break;
            } else
                throw new IOException("Error in module string: unclosed '('.");
            if(cp >= 0x10000)
                 i++; //Skip the next surrogate.
            if((cp >= 0xD800 && cp < 0xE000) || ((cp & 0xFFFE) == 0xFFFE) || (cp >>> 16) > 16 || cp < 0)
                throw new IOException("Error In module string: invalid Unicode character.");
            if(requireNextSep && cp != ',')
                throw new IOException("Error in module string: Expected ',' after ')' closing parameter list.");
            else if(cp == ',' && i == 0)
                throw new IOException("Error in module string: Blank module name not allowed.");
            else if(cp == '(') {
                if(parenDepth == 0) {
                    paramsStart = i + 1;
                    nameEnd = i - 1;
                }
                parenDepth++;
            } else if(cp == ')') {
                if(parenDepth == 0)
                    throw new IOException("Error in module string: Unpaired ')'.");
                else if(parenDepth == 1) {
                    paramsEnd = i - 1;
                    requireNextSep = true;
                }
                parenDepth--;
            }
        }

        String name = currentModule.substring(0, nameEnd + 1);
        String params = null;
        if(paramsStart >= 0)
            params = currentModule.substring(paramsStart, paramsEnd + 1);

        Class<?> plugin;

        try {
            plugin = Class.forName(name);
        } catch(Exception e) {
            throw new IOException("Unable to find plugin \"" + name + "\".");
        }

        if(!Plugin.class.isAssignableFrom(plugin)) {
            throw new IOException("Plugin \"" + name + "\" is not valid plugin.");
        }
        Plugin c = instantiatePlugin(pluginManager, plugin, params);
        pluginManager.registerPlugin(c);
    }

    public static void doCommand(Plugins pluginManager, String cmd)
    {
        if(cmd.toLowerCase().startsWith("load ")) {
            try {
                loadPlugin(pluginManager, cmd.substring(5));
            } catch(Exception e) {
                errorDialog(e, "Plugin Loading failed", null, "Dismiss");
            }
        } else if(cmd.toLowerCase().equals("exit")) {
            pluginManager.shutdownEmulator();
        } else if(cmd.toLowerCase().equals("")) {
        } else if(cmd.toLowerCase().startsWith("command ")) {
            try {
                String[] arr = parseString(cmd.substring(8));
                if(arr == null)
                    throw new Exception("No command to send given");
                String rcmd = arr[0];
                String[] rargs = null;
                if(arr.length > 1) {
                    rargs = new String[arr.length - 1];
                    System.arraycopy(arr, 1, rargs, 0, arr.length - 1);
                }
                pluginManager.invokeExternalCommandSynchronous(rcmd, rargs);
            } catch(Exception e) {
                errorDialog(e, "Command sending failed", null, "Dismiss");
            }
        } else if(cmd.toLowerCase().startsWith("call ")) {
            try {
                String[] arr = parseString(cmd.substring(5));
                if(arr == null)
                    throw new Exception("No command to send given");
                String rcmd = arr[0];
                String[] rargs = null;
                if(arr.length > 1) {
                    rargs = new String[arr.length - 1];
                    System.arraycopy(arr, 1, rargs, 0, arr.length - 1);
                }
                Object[] ret = pluginManager.invokeExternalCommandReturn(rcmd, rargs);
                if(ret != null)
                    for(int i = 0; i < ret.length; i++)
                        System.out.println(ret[i].toString());
                else
                    System.out.println("Nothing returned.");
            } catch(Exception e) {
                errorDialog(e, "Command sending failed", null, "Dismiss");
            }
        } else {
            System.err.println("Invalid command");
        }
    }

    public static void main(String[] args) throws Exception
    {
        try
        {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Throwable e) {  //Yes, we need to catch errors too.
            System.err.println("Warning: System Look-and-Feel not loaded" + e.getMessage());
        }

        System.out.println("JPC-RR: Rerecording PC emulator based on JPC PC emulator. Release 10.6");
        System.out.println("Revision: " + getRevision());
        System.out.println("Based on JPC PC emulator.");
        System.out.println("Copyright (C) 2007-2009 Isis Innovation Limited");
        System.out.println("Copyright (C) 2009-2010 H. Ilari Liusvaara");
        System.out.println("JPC-RR is released under GPL Version 2 and comes with absoutely no warranty.");


        String library = ArgProcessor.findVariable(args, "library", null);
        if(library == null) {
            callShowOptionDialog(null, "No library specified (-library foo)", "Disk library missing",
               JOptionPane.OK_OPTION, JOptionPane.WARNING_MESSAGE, null, new String[]{"Quit"}, "Quit");
            return;
        }
        File libraryFile = new File(library);
        if(!libraryFile.isDirectory()) {
            if(!libraryFile.mkdirs()) {
                callShowOptionDialog(null, "Library (" + library + ") does not exist and can't be created",
                   "Disk library error", JOptionPane.OK_OPTION, JOptionPane.WARNING_MESSAGE, null,
                   new String[]{"Quit"}, "Quit");
                return;
            }
        }
        DiskImage.setLibrary(new ImageLibrary(library));
        Plugins pluginManager = new Plugins();
        BufferedReader kbd = new BufferedReader(new InputStreamReader(System.in, "UTF-8"));

        String autoexec = ArgProcessor.findVariable(args, "autoexec", null);
        if(autoexec != null) {
            try {
                BufferedReader kbd2 = new BufferedReader(new InputStreamReader(
                    new FileInputStream(autoexec), "UTF-8"));
                while(true) {
                    String cmd = kbd2.readLine();
                    if(cmd == null)
                        break;
                    doCommand(pluginManager, cmd);
                }
            } catch (Exception e) {
                System.err.println("Failed to load autoexec script: " + e.getMessage());
            }
        }

        while(true) {
            System.out.print("JPC-RR> ");
            System.out.flush();
            String cmd = kbd.readLine();
            doCommand(pluginManager, cmd);
        }
    }
}
