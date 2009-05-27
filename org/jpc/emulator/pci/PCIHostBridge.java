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

package org.jpc.emulator.pci;

import org.jpc.emulator.motherboard.*;
import org.jpc.emulator.memory.*;
import org.jpc.emulator.pci.peripheral.*;
import org.jpc.support.*;
import org.jpc.emulator.HardwareComponent;
import java.io.*;

/**
 * Intel i440FX PCI Host Bridge emulation.
 */
public class PCIHostBridge extends AbstractPCIDevice implements IOPortCapable, HardwareComponent
{
    private PCIBus attachedBus;

    private int configRegister;
    private Magic magic;

    public void dumpStatusPartial(org.jpc.support.StatusDumper output)
    {
        super.dumpStatusPartial(output);
        output.println("\tdconfigRegister" + configRegister);
        output.println("\tattachedBus <object #" + output.objectNumber(attachedBus) + ">"); if(attachedBus != null) attachedBus.dumpStatus(output);
    }

    public void dumpStatus(org.jpc.support.StatusDumper output)
    {
        if(output.dumped(this))
            return;

        output.println("#" + output.objectNumber(this) + ": PCIHostBridge:");
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
        super.dumpSRPartial(output);
        output.dumpInt(configRegister);
        output.dumpObject(attachedBus);
    }

    public static org.jpc.SRDumpable loadSR(org.jpc.support.SRLoader input, Integer id) throws IOException
    {
        org.jpc.SRDumpable x = new PCIHostBridge(input);
        input.endObject();
        return x;
    }

    public PCIHostBridge(org.jpc.support.SRLoader input) throws IOException
    {
        super(input);
        configRegister = input.loadInt();
        attachedBus = (PCIBus)(input.loadObject());
    }

    /* Constructors */
    public PCIHostBridge()
    {
        magic = new Magic(Magic.PCI_HOST_BRIDGE_MAGIC_V1);
        ioportRegistered = false;

        assignDevFN(0);

        putConfigByte(0x00, (byte)0x86); // vendor_id
        putConfigByte(0x01, (byte)0x80);
        putConfigByte(0x02, (byte)0x37); // device_id
        putConfigByte(0x03, (byte)0x12);
        putConfigByte(0x08, (byte)0x02); // revision
        putConfigByte(0x0a, (byte)0x00); // class_sub = host2pci
        putConfigByte(0x0b, (byte)0x06); // class_base = PCI_bridge
        putConfigByte(0x0e, (byte)0x00); // header_type
    }

    public void dumpState(DataOutput output) throws IOException
    {
        magic.dumpState(output);
        super.dumpState(output);
        output.writeInt(configRegister);
    }

    public void loadState(DataInput input) throws IOException
    {
        magic.loadState(input);
        super.loadState(input);
        ioportRegistered = false;
        pciRegistered = false;
        configRegister = input.readInt();
    }

    public boolean autoAssignDevFN()
    {
        return false;
    }

    public void deassignDevFN()
    {
        System.err.println("Conflict with Host Bridge over PCI Device FN");
    }

    /* BEGIN PCIDevice Methods */
    //IOPort Registration Aids
    public IORegion[] getIORegions()
    {
        return null;
    }
    public IORegion getIORegion(int index)
    {
        return null;
    }
    /* END AbstractPCIDevice Inherited Methods */

    /* IOPortCapable Functions */
    public int[] ioPortsRequested()
    {
        int i[] = {0xcf8, 0xcf9, 0xcfa, 0xcfb, 0xcfc, 0xcfd, 0xcfe, 0xcff};
        return i;
    }

    public void ioPortWriteByte(int address, int data)
    {
        switch (address) {
        case 0xcfc:
        case 0xcfd:
        case 0xcfe:
        case 0xcff:
            if ((configRegister & (1 << 31)) != 0)
                attachedBus.writePCIDataByte(configRegister | (address & 0x3), (byte)data);
            break;
        default:
        }
    }

    public void ioPortWriteWord(int address, int data)
    {
        switch(address) {
        case 0xcfc:
        case 0xcfd:
        case 0xcfe:
        case 0xcff:
            if ((configRegister & (1 << 31)) != 0)
                attachedBus.writePCIDataWord(configRegister | (address & 0x3), (short)data);
            break;
        default:
        }
    }

    public void ioPortWriteLong(int address, int data)
    {
        switch(address) {
        case 0xcf8:
        case 0xcf9:
        case 0xcfa:
        case 0xcfb:
            configRegister = data;
            break;
        case 0xcfc:
        case 0xcfd:
        case 0xcfe:
        case 0xcff:
            if ((configRegister & (1 << 31)) != 0)
                attachedBus.writePCIDataLong(configRegister | (address & 0x3), data);
            break;
        default:
        }
    }

    public int ioPortReadByte(int address)
    {
        switch(address) {
        case 0xcfc:
        case 0xcfd:
        case 0xcfe:
        case 0xcff:
            if ((configRegister & (1 << 31)) == 0)
                return 0xff;
            else
                return 0xff & attachedBus.readPCIDataByte(configRegister | (address & 0x3));

        default:
            return 0xff;
        }
    }

    public int ioPortReadWord(int address)
    {
        switch(address) {
        case 0xcfc:
        case 0xcfd:
        case 0xcfe:
        case 0xcff:
            if ((configRegister & (1 << 31)) == 0)
                return 0xffff;
            else
                return 0xffff & attachedBus.readPCIDataWord(configRegister | (address & 0x3));
        default:
            return 0xffff;
        }
    }

    public int ioPortReadLong(int address)
    {
        switch(address) {
        case 0xcf8:
        case 0xcf9:
        case 0xcfa:
        case 0xcfb:
            return configRegister;
        case 0xcfc:
        case 0xcfd:
        case 0xcfe:
        case 0xcff:
            if ((configRegister & (1 << 31)) == 0)
                return 0xffffffff;
            else
                return attachedBus.readPCIDataLong(configRegister | (address & 0x3));
        default:
            return 0xffffffff;
        }
    }

    /* END IOPortCapable Methods */

    private boolean ioportRegistered;
    private boolean pciRegistered;

    public boolean initialised()
    {
        return ioportRegistered && pciRegistered;
    }

    public void reset()
    {
        attachedBus = null;
        pciRegistered = false;
        ioportRegistered = false;

        assignDevFN(0);

        putConfigByte(0x00, (byte)0x86); // vendor_id
        putConfigByte(0x01, (byte)0x80);
        putConfigByte(0x02, (byte)0x37); // device_id
        putConfigByte(0x03, (byte)0x12);
        putConfigByte(0x08, (byte)0x02); // revision
        putConfigByte(0x0a, (byte)0x00); // class_sub = host2pci
        putConfigByte(0x0b, (byte)0x06); // class_base = PCI_bridge
        putConfigByte(0x0e, (byte)0x00); // header_type
    }

    public void acceptComponent(HardwareComponent component)
    {
        if ((component instanceof PCIBus) && component.initialised() && !pciRegistered) {
            attachedBus = (PCIBus)component;
            pciRegistered = attachedBus.registerDevice(this);
        }

        if ((component instanceof IOPortHandler)
            && component.initialised()) {
            ((IOPortHandler)component).registerIOPortCapable(this);
            ioportRegistered = true;
        }
    }

    public boolean updated()
    {
        return ioportRegistered && pciRegistered;
    }

    public void updateComponent(HardwareComponent component)
    {
        if ((component instanceof PCIBus) && component.updated() && !pciRegistered)
        {
            //            attachedBus = (PCIBus)component;
            pciRegistered = attachedBus.registerDevice(this);
        }

        if ((component instanceof IOPortHandler) && component.updated())
        {
            ((IOPortHandler)component).registerIOPortCapable(this);
            ioportRegistered = true;
        }
    }

    public String toString()
    {
        return "Intel i440FX PCI-Host Bridge";
    }
}
