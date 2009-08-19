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

package org.jpc.emulator;

import java.io.*;

/**
 * This class provides default implementations of the <code>HardwareComponent</code>
 * methods.  The default implementations are all empty.
 * @author Chris Dennis
 */
public abstract class AbstractHardwareComponent implements HardwareComponent
{
    public boolean initialised()
    {
        return true;
    }

    public void acceptComponent(HardwareComponent component)
    {
    }

    public void reset()
    {
    }

    public void dumpStatusPartial(StatusDumper output)
    {
    }

    public void dumpStatus(StatusDumper output)
    {
        if(output.dumped(this))
            return;

        output.println("#" + output.objectNumber(this) + ": AbstractHardwareComponent:");
        dumpStatusPartial(output);
        output.endObject();
    }

    public void dumpSRPartial(SRDumper output) throws IOException
    {
    }

    public AbstractHardwareComponent(SRLoader input) throws IOException
    {
        input.objectCreated(this);
    }

    public AbstractHardwareComponent()
    {
    }
}
