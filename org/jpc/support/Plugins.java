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

package org.jpc.support;

import java.util.*;
import org.jpc.emulator.*;
import org.jpc.*;

public class Plugins
{
    private Set<Plugin> plugins;
    private boolean manualShutdown;
    private boolean shutDown;

    //Create plugin manager.
    public Plugins()
    {
        plugins = new HashSet<Plugin>();
        Runtime.getRuntime().addShutdownHook(new ShutdownHook());
        manualShutdown = true;
        shutDown = false;
    }

    //Shut down and exit the emulator program.
    public void shutdownEmulator()
    {
        if(shutDown)
            return;

        for(Plugin plugin : plugins) {
            System.err.println("Informational: Shutting down " + plugin.getClass().getName() + "...");
            plugin.systemShutdown();
            System.err.println("Informational: Shut down " + plugin.getClass().getName() + ".");
        }
        shutDown = true;
        if(manualShutdown)
            System.exit(0);
    }

    //Signal reconnect event to all plugins.
    public void reconnect(PC pc)
    {
        for(Plugin plugin : plugins) {
            System.err.println("Informational: Reconnecting " + plugin.getClass().getName() + "...");
            plugin.reconnect(pc);
            System.err.println("Informational: Reconnected " + plugin.getClass().getName() + "...");
        }
    }

    //Signal pc stop event to all plugins.
    public void pcStopped()
    {
        for(Plugin plugin : plugins) {
            plugin.pcStopping();
        }
    }

    //Signal pc start event to all plugins.
    public void pcStarted()
    {
        for(Plugin plugin : plugins) {
            plugin.pcStarting();
        }
    }

    //Try to start the PC.
    public void startPC()
    {
        for(Plugin plugin : plugins) {
            if(plugin instanceof RunnerPlugin)
                ((RunnerPlugin)plugin).startExternal();
        }
    }

    //Try to stop the PC.
    public void stopPC()
    {
        for(Plugin plugin : plugins) {
            if(plugin instanceof RunnerPlugin)
                ((RunnerPlugin)plugin).stopExternal();
        }
    }

    //Add new plugin and invoke main thread for it.
    public void registerPlugin(Plugin plugin)
    {
        plugins.add(plugin);
        (new PluginThread(plugin)).start();
    }

    private class ShutdownHook extends Thread
    {
        public void run()
        {
            manualShutdown = false;
            shutdownEmulator();
        }
    }

    private class PluginThread extends Thread
    {
        private Plugin plugin;

        public PluginThread(Plugin _plugin)
        {
            plugin = _plugin;
        }

        public void run()
        {
            plugin.main();
        }
    }

}
