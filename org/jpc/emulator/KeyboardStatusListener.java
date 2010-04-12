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

package org.jpc.emulator;

import java.io.*;

//These are called in PC exec thread, don't block it for long.
public interface KeyboardStatusListener
{
    //Change input edge status of <scancode> to <pressed>.
    public void keyStatusChange(int scancode, boolean pressed);
    //Change current execution status of <scancode> to <pressed>.
    public void keyExecStatusChange(int scancode, boolean pressed);
    //Reload all input edge and current execution statuses.
    public void keyStatusReload();
    //Bit 2 => CapsLock, Bit 1 => NumLock, Bit 0 => ScrollLock. -1 is unknown.
    public void ledStatusChange(int newstatus);
}
