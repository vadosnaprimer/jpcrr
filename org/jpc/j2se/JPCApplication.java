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
import java.util.List;
import java.lang.reflect.*;

import org.jpc.*;
import org.jpc.bus.*;
import org.jpc.images.JPCRRStandardImageDecoder;
import org.jpc.images.BaseImage;
import org.jpc.images.BaseImageFactory;

import static org.jpc.Revision.getRevision;
import static org.jpc.Revision.getRelease;
import static org.jpc.Misc.errorDialog;
import static org.jpc.Misc.callShowOptionDialog;
import static org.jpc.Misc.parseString;

public class JPCApplication
{
    public static void doCommand(Bus bus, String cmd) throws IOException
    {
        if(!cmd.toLowerCase().equals("")) {
            String[] ret = bus.executeStringCommand(cmd);
            if(ret == null || ret.length == 0)
                System.err.println("=> (No return value)");
            else
                for(String x : ret)
                    System.err.println("=> " + x);
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

        System.out.println("JPC-RR: Rerecording PC emulator based on JPC PC emulator. Release " + getRelease());
        System.out.println("Revision: " + getRevision());
        System.out.println("Based on JPC PC emulator.");
        System.out.println("Copyright (C) 2007-2009 Isis Innovation Limited");
        System.out.println("Copyright (C) 2009-2010 H. Ilari Liusvaara");
        System.out.println("JPC-RR is released under GPL Version 2 and comes with absoutely no warranty.");

        //Probe if rename-over is supported.
        Misc.probeRenameOver(ArgProcessor.findFlag(args, "-norenames"));

        Bus bus = new Bus();
        BufferedReader kbd = new BufferedReader(new InputStreamReader(System.in, "UTF-8"));

        boolean noautoexec = ArgProcessor.findFlag(args, "-noautoexec");
        String autoexec = ArgProcessor.findVariable(args, "autoexec", null);
        if(autoexec != null && !noautoexec) {
            try {
                BufferedReader kbd2 = new BufferedReader(new InputStreamReader(
                    new FileInputStream(autoexec), "UTF-8"));
                while(true) {
                    String cmd = kbd2.readLine();
                    if(cmd == null)
                        break;
                    System.err.println("Autoexec command: " + cmd);
                    doCommand(bus, cmd);
                }
            } catch (Exception e) {
                System.err.println("Failed to load autoexec script: " + e.getMessage());
            }
        }

        while(true) {
            System.out.print("JPC-RR> ");
            System.out.flush();
            String cmd = kbd.readLine();
            try {
                if(cmd != null)
                    doCommand(bus, cmd);
                else
                    synchronized(kbd) {
                        kbd.wait();
                    }
            } catch (Exception e) {
                errorDialog(e, "Command execution failed", null, "Dismiss");
            }
        }
    }
}
