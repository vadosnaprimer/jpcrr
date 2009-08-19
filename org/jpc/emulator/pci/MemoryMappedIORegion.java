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

package org.jpc.emulator.pci;

import org.jpc.emulator.StatusDumper;
import org.jpc.emulator.memory.AbstractMemory;
import java.io.*;
import org.jpc.emulator.SRLoader;
import org.jpc.emulator.SRDumper;

/**
 * A PCI device compatible <code>IORegion</code> that is mapped into the
 * physical address space of the emulated system.
 * @author Chris Dennis
 */
public abstract class MemoryMappedIORegion extends AbstractMemory implements IORegion
{
    public void dumpStatusPartial(StatusDumper output)
    {
        super.dumpStatusPartial(output);
    }

    public void dumpStatus(StatusDumper output)
    {
        if(output.dumped(this))
            return;

        output.println("#" + output.objectNumber(this) + ": MemoryMappedIORegion:");
        dumpStatusPartial(output);
        output.endObject();
    }

    public void dumpSRPartial(SRDumper output) throws IOException
    {
        super.dumpSRPartial(output);
    }

    public MemoryMappedIORegion(SRLoader input) throws IOException
    {
        super(input);
    }

    public MemoryMappedIORegion()
    {
    }
}
