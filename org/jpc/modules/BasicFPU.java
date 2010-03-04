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

package org.jpc.modules;

import org.jpc.emulator.*;
import org.jpc.emulator.processor.Processor;
import org.jpc.emulator.processor.fpu64.FpuState64;
import java.io.*;

public class BasicFPU  extends AbstractHardwareComponent
{
    boolean fpuCreated;

    public void dumpSRPartial(SRDumper output) throws IOException
    {
        super.dumpSRPartial(output);
        output.dumpBoolean(fpuCreated);
    }


    public BasicFPU(SRLoader input) throws IOException
    {
        super(input);
        fpuCreated = input.loadBoolean();
    }

    public BasicFPU() throws IOException
    {
        fpuCreated = false;
    }

    public void dumpStatusPartial(StatusDumper output)
    {
        super.dumpStatusPartial(output);
        output.println("\tfpuCreated " + fpuCreated);

    }

    public void dumpStatus(StatusDumper output)
    {
        if(output.dumped(this))
            return;

        output.println("#" + output.objectNumber(this) + ": BasicFPU:");
        dumpStatusPartial(output);
        output.endObject();
    }

    public boolean initialised()
    {
        return fpuCreated;
    }

    public void acceptComponent(HardwareComponent component)
    {
        if(component instanceof Processor && !fpuCreated) {
            System.err.println("Informational: Creating FPU...");
            FpuState64 fpu = new FpuState64((Processor)component);
            ((Processor)component).setFPU(fpu);
            fpuCreated = true;
        }
    }
}
