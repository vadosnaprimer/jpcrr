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

package org.jpc.emulator.motherboard;

import org.jpc.emulator.processor.*;
import org.jpc.emulator.memory.*;
import org.jpc.emulator.HardwareComponent;
import java.io.*;

public class GateA20Handler implements IOPortCapable, HardwareComponent
{
    private Processor cpu;
    private PhysicalAddressSpace physicalAddressSpace;
    private LinearAddressSpace linearAddressSpace;
    private boolean ioportRegistered;

    public GateA20Handler()
    {
        ioportRegistered = false;
        cpu = null;
        physicalAddressSpace = null;
    }

    public void dumpStatusPartial(org.jpc.support.StatusDumper output)
    {
        output.println("\tioportRegistered " + ioportRegistered);
        output.println("\tcpu <object #" + output.objectNumber(cpu) + ">"); if(cpu != null) cpu.dumpStatus(output);
        output.println("\tphysicalAddressSpace <object #" + output.objectNumber(physicalAddressSpace) + ">"); if(physicalAddressSpace != null) physicalAddressSpace.dumpStatus(output);
        output.println("\tlinearAddressSpace <object #" + output.objectNumber(linearAddressSpace) + ">"); if(linearAddressSpace != null) linearAddressSpace.dumpStatus(output);
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
        output.dumpBoolean(ioportRegistered);
        output.dumpObject(cpu);
        output.dumpObject(physicalAddressSpace);
        output.dumpObject(linearAddressSpace);
    }

    public static org.jpc.SRDumpable loadSR(org.jpc.support.SRLoader input, Integer id) throws IOException
    {
        org.jpc.SRDumpable x = new GateA20Handler(input);
        input.endObject();
        return x;
    }

    public GateA20Handler(org.jpc.support.SRLoader input) throws IOException
    {
        input.objectCreated(this);
        ioportRegistered = input.loadBoolean();
        cpu = (Processor)(input.loadObject());
        physicalAddressSpace = (PhysicalAddressSpace)(input.loadObject());
        linearAddressSpace = (LinearAddressSpace)(input.loadObject());
    }

    public void dumpStatus(org.jpc.support.StatusDumper output)
    {
        if(output.dumped(this))
            return;

        output.println("#" + output.objectNumber(this) + ": GateA20Handler:");
        dumpStatusPartial(output);
        output.endObject();
    }

    private void setGateA20State(boolean value)
    {
        physicalAddressSpace.setGateA20State(value);
    }

    public void ioPortWriteByte(int address, int data)
    {
        setGateA20State((data & 0x02) != 0);
        if ((data & 0x01) != 0)
            cpu.reset();
    }
    public void ioPortWriteWord(int address, int data)
    {
        ioPortWriteByte(address, data);
    }
    public void ioPortWriteLong(int address, int data)
    {
        ioPortWriteWord(address, data);
    }

    public int ioPortReadByte(int address)
    {
        return physicalAddressSpace.getGateA20State() ? 0x02 : 0x00;
    }
    public int ioPortReadWord(int address)
    {
        return ioPortReadByte(address) | 0xff00;
    }
    public int ioPortReadLong(int address)
    {
        return ioPortReadWord(address) | 0xffff0000;
    }

    public int[] ioPortsRequested()
    {
        return new int[] {0x92};
    }

    public boolean initialised()
    {
        return ioportRegistered && (cpu != null) && (physicalAddressSpace != null) && (linearAddressSpace != null);
    }

    public boolean updated()
    {
        return ioportRegistered && cpu.updated() && physicalAddressSpace.updated() && linearAddressSpace.updated();
    }

    public void updateComponent(HardwareComponent component)
    {
        if (component instanceof IOPortHandler)
        {
            ((IOPortHandler)component).registerIOPortCapable(this);
            ioportRegistered = true;
        }
    }

    public void acceptComponent(HardwareComponent component)
    {
        if ((component instanceof IOPortHandler) && component.initialised())
        {
            ((IOPortHandler)component).registerIOPortCapable(this);
            ioportRegistered = true;
        }

        if (component instanceof PhysicalAddressSpace)
            physicalAddressSpace = (PhysicalAddressSpace)component;

        if (component instanceof LinearAddressSpace)
            linearAddressSpace = (LinearAddressSpace)component;

        if ((component instanceof Processor) && component.initialised())
            cpu = (Processor) component;
    }

    public void timerCallback() {}

    public void reset()
    {
        ioportRegistered = false;
        physicalAddressSpace = null;
        linearAddressSpace = null;
        cpu = null;
    }
}
