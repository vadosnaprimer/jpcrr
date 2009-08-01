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

import org.jpc.emulator.*;

public interface Plugin
{
    // NOTE! systemShutdown() is called in its some thread with Java Virtual
    // Machine possibly shutting down. Thus, it needs to be extremely careful what it
    // does. It is meant for writing headers for dump files and flushing them to disk.
    // Pretty much everything else should be left to Operating System to clean up.
    // In practicular, touching GUI is UNSAFE! No need to kill off the main plugin
    // threads. They will be killed off anyway.
    //
    // The thread systemShutdown() is invoked in is either dedicated thread or thread
    // that called shutdownEmulator(). Thus synchronizing with main plugin thread is safe
    // if this plugin didn't invoke shutdownEmulator(). In case it did, no need to
    // synchronize.
    //
    public void systemShutdown();  //System is shutting down.
    public void reconnect(PC pc);  //pc == null to disconnect.
    public void main();
    public void pcStopping();      //PC is stopping
    public void pcStarting();      //PC is starting again.
    public void notifyArguments(String[] args);
}
