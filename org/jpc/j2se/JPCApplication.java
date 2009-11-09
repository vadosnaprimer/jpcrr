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
import org.jpc.pluginsbase.*;

import static org.jpc.Misc.errorDialog;
import static org.jpc.Misc.callShowOptionDialog;

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
            if(e2 instanceof Error)
                throw new IOException("Error while invoking loader: " + e2);
            //Also pass IOException through.
            if(e2 instanceof IOException)
                throw (IOException)e2;
            //What the heck is that?
            throw new IOException("Unknown exception while invoking loader: " + e2);
        } catch(Exception e) {
            throw new IOException("Failed to invoke plugin \"" + plugin.getName() + "\" constructor.");
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

        System.out.println("JPC-RR: Rerecording PC emulator based on JPC PC emulator. Release 4.");
        System.out.println("Based on JPC PC emulator.");
        System.out.println("Copyright (C) 2007-2009 Isis Innovation Limited");
        System.out.println("Copyright (C) 2009 H. Ilari Liusvaara");
        System.out.println("JPC-RR is released under GPL Version 2 and comes with absoutely no warranty.");


        String library = ArgProcessor.findVariable(args, "library", null);
        if(library == null) {
            callShowOptionDialog(null, "No library specified (-library foo)", "Disk library missing",
               JOptionPane.OK_OPTION, JOptionPane.WARNING_MESSAGE, null, new String[]{"Quit"}, "Quit");
            return;
        }
        DiskImage.setLibrary(new ImageLibrary(library));
        Plugins pluginManager = new Plugins();

        //Load plugins.
        try {
            String plugins = ArgProcessor.findVariable(args, "plugins", null);
            Map<String, Set<String>> plugins2 = PC.parseHWModules(plugins);
            for(Map.Entry<String, Set<String>> pluginEntry : plugins2.entrySet()) {
                for(String pluginArgs : pluginEntry.getValue()) {
                    String pluginClass = pluginEntry.getKey();
                    Class<?> plugin;

                    try {
                        plugin = Class.forName(pluginClass);
                    } catch(Exception e) {
                        throw new IOException("Unable to find plugin \"" + pluginClass + "\".");
                    }

                    if(!Plugin.class.isAssignableFrom(plugin)) {
                        throw new IOException("Plugin \"" + pluginClass + "\" is not valid plugin.");
                    }
                    Plugin c = instantiatePlugin(pluginManager, plugin, pluginArgs);

                    c.notifyArguments(args);
                    pluginManager.registerPlugin(c);
                }
            }
        } catch(Exception e) {
            errorDialog(e, "Plugin Loading failed", null, "Dismiss");
            pluginManager.shutdownEmulator();
            return;
        }
    }
}
