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

public class RemoteBlockDeviceServer 
{
    public static void main(String[] args) throws Exception
    {
        DriveSet set = DriveSet.buildFromArgs(args);
        
        int port = 6666;
        try
        {
            port = Integer.parseInt(ArgProcessor.findArg(args, "port", "6666"));
        }
        catch (Exception e) {}

        ServerSocket inputsock = new ServerSocket(port);
        Socket ss = inputsock.accept();
        InputStream in = ss.getInputStream();

        OutputStream out = ss.getOutputStream();
        
        RemoteBlockDeviceImpl impl = new RemoteBlockDeviceImpl(in, out, set.getBootDevice());

        System.out.println("Remote server accepted connection to "+set.getBootDevice()+" on port "+port);
    }
}
