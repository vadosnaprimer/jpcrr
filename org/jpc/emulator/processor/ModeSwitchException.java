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


package org.jpc.emulator.processor;

import org.jpc.emulator.memory.*;
import java.io.*;

public class ModeSwitchException extends RuntimeException implements org.jpc.SRDumpable
{
    public static final int REAL_MODE = 0;
    public static final int PROTECTED_MODE = 1;
    public static final int VIRTUAL8086_MODE = 2;

    public static final ModeSwitchException PROTECTED_MODE_EXCEPTION = new ModeSwitchException(PROTECTED_MODE);
    public static final ModeSwitchException REAL_MODE_EXCEPTION = new ModeSwitchException(REAL_MODE);
    public static final ModeSwitchException VIRTUAL8086_MODE_EXCEPTION = new ModeSwitchException(VIRTUAL8086_MODE);

    private int mode;

    public void dumpSR(org.jpc.support.SRDumper output) throws IOException
    {
        if(output.dumped(this))
            return;
        dumpSRPartial(output);
        output.endObject();
    }

    public void dumpSRPartial(org.jpc.support.SRDumper output) throws IOException
    {
        output.dumpInt(mode);
    }

    public static org.jpc.SRDumpable loadSR(org.jpc.support.SRLoader input, Integer id) throws IOException
    {
        org.jpc.SRDumpable x = new ModeSwitchException(input);
        input.endObject();
        return x;
    }

    public ModeSwitchException(org.jpc.support.SRLoader input) throws IOException
    {
        input.objectCreated(this);
        mode = input.loadInt();
    }

    public ModeSwitchException(int mode)
    {
        this.mode = mode;
    }

    public int getNewMode()
    {
        return mode;
    }

    public String toString()
    {
        if (mode == REAL_MODE)
            return "Switched to REAL mode";
        if (mode == PROTECTED_MODE)
            return "Switched to PROTECTED mode";
        if (mode == VIRTUAL8086_MODE)
            return "Switched to VIRTUAL 8086 mode";

        return "Switched to unknown mode "+mode;
    }
}
