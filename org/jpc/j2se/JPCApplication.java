/*
    JPC: A x86 PC Hardware Emulator for a pure Java Virtual Machine
    Release Version 2.0

    A project from the Physics Dept, The University of Oxford

    Copyright (C) 2007-2009 Isis Innovation Limited

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

    www-jpc.physics.ox.ac.uk
*/

package org.jpc.j2se;

import java.awt.Component;
import java.awt.BorderLayout;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.*;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;
import java.util.jar.*;
import java.util.zip.*;
import java.security.AccessControlException;
import javax.swing.*;
import java.lang.reflect.*;

import org.jpc.*;
import org.jpc.emulator.PC;
import org.jpc.emulator.TraceTrap;
import org.jpc.emulator.pci.peripheral.VGACard;
import org.jpc.emulator.peripheral.FloppyController;
import org.jpc.emulator.peripheral.Keyboard;
import org.jpc.emulator.memory.PhysicalAddressSpace;
import org.jpc.emulator.SRLoader;
import org.jpc.emulator.SRDumper;
import org.jpc.emulator.StatusDumper;
import org.jpc.emulator.Clock;
import org.jpc.diskimages.BlockDevice;
import org.jpc.diskimages.GenericBlockDevice;
import org.jpc.diskimages.ImageLibrary;
import org.jpc.diskimages.DiskImage;
import org.jpc.support.*;

public class JPCApplication
{
    public static int callShowOptionDialog(java.awt.Component parent, Object msg, String title, int oType, 
        int mType, Icon icon, Object[] buttons, Object deflt)
    {
        try {
            return JOptionPane.showOptionDialog(parent, msg, title, oType, mType, icon, buttons, deflt);
        } catch(java.awt.HeadlessException e) {
            //No GUI available.
            System.err.println("MESSAGE: *** " + title + " ***: " + msg.toString());
            for(int i = 0; i < buttons.length; i++)
                if(buttons[i] == deflt)
                    return i;
            return 0;
        }
    }

    public static void errorDialog(Throwable e, String title, java.awt.Component component, String text)
    {
        String message = e.getMessage();
        //Give nicer errors for some internal ones.
        if(e instanceof NullPointerException)
            message = "Internal Error: Null pointer dereference";
        if(e instanceof ArrayIndexOutOfBoundsException)
            message = "Internal Error: Array bounds exceeded";
        if(e instanceof StringIndexOutOfBoundsException)
            message = "Internal Error: String bounds exceeded";
        int i = callShowOptionDialog(null, message, title, JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE, null, new String[]{text, "Save stack trace"}, "Save stack Trace");
        if(i > 0) {
            JPCApplication.saveStackTrace(e, null, text);
        }
    }

    public static void saveStackTrace(Throwable e, java.awt.Component component, String text)
    {
        StackTraceElement[] traceback = e.getStackTrace();
        StringBuffer sb = new StringBuffer();
        sb.append(e.getMessage() + "\n");
        for(int i = 0; i < traceback.length; i++) {
            StackTraceElement el = traceback[i];
            if(el.getClassName().startsWith("sun.reflect."))
                continue; //Clean up the trace a bit.
            if(el.isNativeMethod())
                sb.append(el.getMethodName() + " of " + el.getClassName() + " <native>\n");
            else
                sb.append(el.getMethodName() + " of " + el.getClassName() + " <" + el.getFileName() + ":" +
                    el.getLineNumber() + ">\n");
        }
        String exceptionMessage = sb.toString();

        try {
            String traceFileName = "StackTrace-" + System.currentTimeMillis() + ".text";
            PrintStream stream = new PrintStream(traceFileName, "UTF-8");
            stream.print(exceptionMessage);
            stream.close();
            callShowOptionDialog(component, "Stack trace saved to " + traceFileName + ".", "Stack trace saved", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE, null, new String[]{text}, text);
        } catch(Exception e2) {
            callShowOptionDialog(component, e.getMessage(), "Saving stack trace failed", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE, null, new String[]{text}, text);
        }
    }

    public static void main(String[] args) throws Exception
    {
        try
        {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e)
        {
            System.err.println("Warning: System Look-and-Feel not loaded" + e.getMessage());
        }

        System.out.println("JPC-RR: Rerecording PC emulator based on JPC PC emulator. Release 0.");
        System.out.println("Based on JPC PC emulator.");
        System.out.println("Copyright (C) 2007-2009 Isis Innovation Limited");
        System.out.println("Copyright (C) 2009 H. Ilari Liusvaara");
        System.out.println("JPC-RR is released under GPL Version 2 and comes with absoutely no warranty.");


        String library = ArgProcessor.findVariable(args, "library", null);
        DiskImage.setLibrary(new ImageLibrary(library));
        Plugins pluginManager = new Plugins();

        //Load plugins.
        try {
            String plugins = ArgProcessor.findVariable(args, "plugins", null);
            Map<String, String> plugins2 = PC.parseHWModules(plugins);
            for(Map.Entry<String, String> pluginEntry : plugins2.entrySet()) {
                String pluginClass = pluginEntry.getKey();
                String pluginArgs = pluginEntry.getValue();
                Class<?> plugin;

                //Note that pluginArgs may be null!

                if("".equals(pluginArgs))
                    pluginArgs = null;

                try {
                    plugin = Class.forName(pluginClass);
                } catch(Exception e) {
                    throw new IOException("Unable to find plugin \"" + pluginClass + "\".");
                }

                if(!Plugin.class.isAssignableFrom(plugin)) {
                    throw new IOException("Plugin \"" + pluginClass + "\" is not valid plugin.");
                }
                Plugin c;
                try {
                    boolean x = pluginArgs.equals("");  //Intentionally cause NPE if params is null.
                    x = x & x;    //Silence warning.
                    Constructor<?> cc = plugin.getConstructor(Plugins.class, String.class);
                    c = (Plugin)cc.newInstance(pluginManager, pluginArgs);
                } catch(Exception e) {
                      try {
                          Constructor<?> cc = plugin.getConstructor(Plugins.class);
                          c = (Plugin)cc.newInstance(pluginManager);
                      } catch(Exception f) {
                          throw new IOException("Unable to instantiate plugin \"" + pluginClass + "\".");
                      }
                }
                c.notifyArguments(args);
                pluginManager.registerPlugin(c);
            }
        } catch(Exception e) {
            errorDialog(e, "Plugin Loading failed", null, "Dismiss");
            return;
        }
    }
}
