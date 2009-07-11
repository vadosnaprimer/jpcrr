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


package org.jpc.emulator.processor;

import java.io.*;

/**
 *
 * @author Chris Dennis
 */
public class ModeSwitchException extends RuntimeException implements org.jpc.SRDumpable
{
    private static final long serialVersionUID = 3;
    public static final ModeSwitchException PROTECTED_MODE_EXCEPTION = new ModeSwitchException();
    public static final ModeSwitchException REAL_MODE_EXCEPTION = new ModeSwitchException();
    public static final ModeSwitchException VIRTUAL8086_MODE_EXCEPTION = new ModeSwitchException();

    public void dumpStatusPartial(org.jpc.support.StatusDumper output)
    {
        //super.dumpStatusPartial(output); <no superclass 20090704>
        output.println("\t" + toString());
    }

    public void dumpStatus(org.jpc.support.StatusDumper output)
    {
        if(output.dumped(this))
            return;

        output.println("#" + output.objectNumber(this) + ": ModeSwitchException:");
        dumpStatusPartial(output);
        output.endObject();
    }

    public void dumpSR(org.jpc.support.SRDumper output) throws IOException
    {
        if(output.dumped(this))
            return;
        dumpSRPartial(output);
        output.endObject();
    }

    public void dumpSRPartial(org.jpc.support.SRDumper output) throws IOException
    {
        if (this == REAL_MODE_EXCEPTION)
            output.dumpByte((byte)0);
        else if (this == PROTECTED_MODE_EXCEPTION)
            output.dumpByte((byte)1);
        else if (this == VIRTUAL8086_MODE_EXCEPTION)
            output.dumpByte((byte)2);
        else
            throw new IOException("Illegal mode switch saving ModeSwitchException.");
    }

    public static org.jpc.SRDumpable loadSR(org.jpc.support.SRLoader input, Integer id) throws IOException
    {
        int type = input.loadByte();
        switch(type) {
        case 0:
            input.objectCreated(REAL_MODE_EXCEPTION);
            input.endObject();
            return REAL_MODE_EXCEPTION;
        case 1:
            input.objectCreated(PROTECTED_MODE_EXCEPTION);
            input.endObject();
            return PROTECTED_MODE_EXCEPTION;
        case 2:
            input.objectCreated(VIRTUAL8086_MODE_EXCEPTION);
            input.endObject();
            return VIRTUAL8086_MODE_EXCEPTION;
        default:
            throw new IOException("Illegal mode switch loading ModeSwitchException.");
        }
    }

    private ModeSwitchException()
    {
    }

    public String toString()
    {
        if (this == REAL_MODE_EXCEPTION)
            return "Switched to REAL mode";
        else if (this == PROTECTED_MODE_EXCEPTION)
            return "Switched to PROTECTED mode";
        else if (this == VIRTUAL8086_MODE_EXCEPTION)
            return "Switched to VIRTUAL 8086 mode";
        else
            return "Switched to unknown mode";
    }
}
