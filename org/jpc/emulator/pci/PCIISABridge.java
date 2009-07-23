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

package org.jpc.emulator.pci;

import org.jpc.emulator.motherboard.InterruptController;
import org.jpc.emulator.HardwareComponent;

import java.io.*;

/**
 * Emulates the PCI-ISA bridge functionality of the Intel 82371SB PIIX3
 * southbridge.
 * <p>
 * The key function of this component is the interfacing between the PCI
 * components and the interrupt controller that forms part of the southbridge.
 * @author Chris Dennis
 */
public class PCIISABridge extends AbstractPCIDevice
{
    private int irqLevels[][];
    private InterruptController irqDevice;

    public void dumpStatusPartial(org.jpc.support.StatusDumper output)
    {
        super.dumpStatusPartial(output);
        output.println("\tirqDevice <object #" + output.objectNumber(irqDevice) + ">"); if(irqDevice != null) irqDevice.dumpStatus(output);
        for (int i=0; i < irqLevels.length; i++) {
            if(irqLevels[i] != null)
                for (int j=0; j < irqLevels[i].length; j++)
                    output.println("\tirqLevels[" + i + "][" + j + "] " + irqLevels[i][j]);
            else
                output.println("\tirqLevels[" + i + "] null");
        }
    }

    public void dumpStatus(org.jpc.support.StatusDumper output)
    {
        if(output.dumped(this))
            return;

        output.println("#" + output.objectNumber(this) + ": PCIISABridge:");
        dumpStatusPartial(output);
        output.endObject();
    }

    public void dumpSRPartial(org.jpc.support.SRDumper output) throws IOException
    {
        super.dumpSRPartial(output);
        output.dumpObject(irqDevice);
        output.dumpInt(irqLevels.length);
        for (int i=0; i < irqLevels.length; i++)
            output.dumpArray(irqLevels[i]);
    }

    public PCIISABridge(org.jpc.support.SRLoader input) throws IOException
    {
        super(input);
        irqDevice = (InterruptController)input.loadObject();
        irqLevels = new int[input.loadInt()][];
        for (int i=0; i < irqLevels.length; i++)
            irqLevels[i] = input.loadArrayInt();
    }

    /**
     * Constructs the (singleton) PCI-ISA bridge instance for attachment to a
     * PCI bus.
     */
    public PCIISABridge()
    {
        irqLevels = new int[4][2];

        putConfigWord(PCI_CONFIG_VENDOR_ID, (short)0x8086); // Intel
        putConfigWord(PCI_CONFIG_DEVICE_ID, (short)0x7000); // 82371SB PIIX3 PCI-to-ISA bridge (Step A1)
        putConfigWord(PCI_CONFIG_CLASS_DEVICE, (short)0x0601); // ISA Bridge
        putConfigByte(PCI_CONFIG_HEADER, (byte)0x80); // PCI_multifunction

        this.internalReset();
    }

    private void internalReset()
    {
        putConfigWord(PCI_CONFIG_COMMAND, (short)0x0007); // master, memory and I/O
        putConfigWord(PCI_CONFIG_STATUS, (short)0x0200); // PCI_status_devsel_medium
        putConfigByte(0x4c, (byte)0x4d);
        putConfigByte(0x4e, (byte)0x03);
        putConfigByte(0x4f, (byte)0x00);
        putConfigByte(0x60, (byte)0x80);
        putConfigByte(0x69, (byte)0x02);
        putConfigByte(0x70, (byte)0x80);
        putConfigByte(0x76, (byte)0x0c);
        putConfigByte(0x77, (byte)0x0c);
        putConfigByte(0x78, (byte)0x02);
        putConfigByte(0x79, (byte)0x00);
        putConfigByte(0x80, (byte)0x00);
        putConfigByte(0x82, (byte)0x00);
        putConfigByte(0xa0, (byte)0x08);
        putConfigByte(0xa2, (byte)0x00);
        putConfigByte(0xa3, (byte)0x00);
        putConfigByte(0xa4, (byte)0x00);
        putConfigByte(0xa5, (byte)0x00);
        putConfigByte(0xa6, (byte)0x00);
        putConfigByte(0xa7, (byte)0x00);
        putConfigByte(0xa8, (byte)0x0f);
        putConfigByte(0xaa, (byte)0x00);
        putConfigByte(0xab, (byte)0x00);
        putConfigByte(0xac, (byte)0x00);
        putConfigByte(0xae, (byte)0x00);
    }

    private void setIRQ(PCIDevice device, int irqNumber, int level)
    {
        irqNumber = this.slotGetPIRQ(device, irqNumber);
        int irqIndex = device.getIRQIndex();
        int shift = (irqIndex & 0x1f);
        int p = irqLevels[irqNumber][irqIndex >> 5];
        irqLevels[irqNumber][irqIndex >> 5] = (p & ~(1 << shift)) | (level << shift);

        /* now we change the pic irq level according to the piix irq mappings */
        int picIRQ = this.configReadByte(0x60 + irqNumber); //short/int/long?
        if (picIRQ < 16) {
            /* the pic level is the logical OR of all the PCI irqs mapped to it */
            int picLevel = 0;
            for (int i = 0; i < 4; i++) {
                if (picIRQ == this.configReadByte(0x60 + i))
                    picLevel |= getIRQLevel(i);
            }
            irqDevice.setIRQ(picIRQ, picLevel);
        }
    }


    private int getIRQLevel(int irqNumber)
    {
        for(int i = 0; i < PCIBus.PCI_IRQ_WORDS; i++) {
            if (irqLevels[irqNumber][i] != 0) {
                return 1;
            }
        }
        return 0;
    }

    IRQBouncer makeBouncer(PCIDevice device)
    {
        return new DefaultIRQBouncer(this);
    }

    int slotGetPIRQ(PCIDevice device, int irqNumber)
    {
        int slotAddEnd;
        slotAddEnd = (device.getDeviceFunctionNumber() >> 3);
        return (irqNumber + slotAddEnd) & 0x3;
    }

    public static class DefaultIRQBouncer implements IRQBouncer
    {
        private PCIISABridge upperBackref;

        public void dumpSRPartial(org.jpc.support.SRDumper output) throws IOException
        {
            output.dumpObject(upperBackref);
        }

        public DefaultIRQBouncer(org.jpc.support.SRLoader input) throws IOException
        {
            input.objectCreated(this);
            upperBackref = (PCIISABridge)input.loadObject();
        }

        DefaultIRQBouncer(PCIISABridge backref)
        {
            upperBackref = backref;
        }

        public void dumpStatusPartial(org.jpc.support.StatusDumper output)
        {
            //super.dumpStatusPartial(output); <no superclass 20090704>
            output.println("\tupperBackref <object #" + output.objectNumber(upperBackref) + ">"); if(upperBackref != null) upperBackref.dumpStatus(output);
        }

        public void dumpStatus(org.jpc.support.StatusDumper output)
        {
            if(output.dumped(this))
                return;

            output.println("#" + output.objectNumber(this) + ": DefaultIRQBouncer:");
            dumpStatusPartial(output);
            output.endObject();
        }

        public void setIRQ(PCIDevice device, int irqNumber, int level)
        {
            upperBackref.setIRQ(device, irqNumber, level);
        }
    }

    public IORegion getIORegion(int index)
    {
        return null;
    }

    public IORegion[] getIORegions()
    {
        return null;
    }

    public void reset()
    {
        irqDevice = null;

        putConfigWord(PCI_CONFIG_VENDOR_ID, (short)0x8086); // Intel
        putConfigWord(PCI_CONFIG_DEVICE_ID, (short)0x7000); // 82371SB PIIX3 PCI-to-ISA bridge (Step A1)
        putConfigWord(PCI_CONFIG_CLASS_DEVICE, (short)0x0601); // ISA Bridge
        putConfigByte(PCI_CONFIG_HEADER, (byte)0x80); // PCI_multifunction
        internalReset();

        super.reset();
    }

    public boolean initialised()
    {
        return (irqDevice != null) && super.initialised();
    }

    public void acceptComponent(HardwareComponent component)
    {
        if ((component instanceof InterruptController) && component.initialised())
            irqDevice = (InterruptController)component;

        super.acceptComponent(component);
    }

    public String toString()
    {
        return "Intel 82371SB PIIX3 PCI ISA Bridge";
    }
}
