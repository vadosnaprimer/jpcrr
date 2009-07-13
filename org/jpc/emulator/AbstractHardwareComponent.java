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

    public void dumpStatusPartial(org.jpc.support.StatusDumper output)
    {
    }

    public void dumpStatus(org.jpc.support.StatusDumper output)
    {
        if(output.dumped(this))
            return;

        output.println("#" + output.objectNumber(this) + ": AbstractHardwareComponent:");
        dumpStatusPartial(output);
        output.endObject();
    }

    public void dumpSRPartial(org.jpc.support.SRDumper output) throws IOException
    {
    }

    public AbstractHardwareComponent(org.jpc.support.SRLoader input) throws IOException
    {
        input.objectCreated(this);
    }

    public AbstractHardwareComponent()
    {
    }
}
