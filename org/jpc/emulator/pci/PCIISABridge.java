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

import org.jpc.emulator.motherboard.InterruptController;
import org.jpc.emulator.HardwareComponent;
import java.io.*;
import org.jpc.support.Magic;

/**
 * Intel 82371SB PIIX3 PCI ISA Bridge emulation.
 */
public class PCIISABridge extends AbstractPCIDevice implements HardwareComponent
{
    private int irqLevels[][];
    private InterruptController irqDevice;
    private Magic magic;

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
        output.dumpObject(irqDevice);
        output.dumpInt(irqLevels.length);
        for (int i=0; i < irqLevels.length; i++)
            output.dumpArray(irqLevels[i]);
    }

    public PCIISABridge()
    {
        magic = new Magic(Magic.PCI_ISA_BRIDGE_MAGIC_V1);
        irqLevels = new int[4][2];

        putConfigByte(0x00, (byte)0x86); // Intel
        putConfigByte(0x01, (byte)0x80);
        putConfigByte(0x02, (byte)0x00); // 82371SB PIIX3 PCI-to-ISA bridge (Step A1)
        putConfigByte(0x03, (byte)0x70);
        putConfigByte(0x0a, (byte)0x01); // class_sub = PCI_ISA
        putConfigByte(0x0b, (byte)0x06); // class_base = PCI_bridge
        putConfigByte(0x0e, (byte)0x80); // header_type = PCI_multifunction, generic
        this.internalReset();
    }

    public void dumpState(DataOutput output) throws IOException
    {
        magic.dumpState(output);
        super.dumpState(output);
        output.writeInt(irqLevels.length);
        output.writeInt(irqLevels[0].length);
        for (int i=0; i< irqLevels.length; i++)
            for (int j=0; j < irqLevels[0].length; j++)
                output.writeInt(irqLevels[i][j]);
    }

    public void loadState(DataInput input) throws IOException
    {
        magic.loadState(input);
        super.reset();
        super.loadState(input);
        int len = input.readInt();
        int width = input.readInt();
        irqLevels = new int[len][width];
        for (int i=0; i<len; i++)
            for (int j=0; j< width; j++)
                irqLevels[i][j] = input.readInt();
    }

    private void internalReset()
    {
        putConfigByte(0x04, (byte)0x07); // master, memory and I/O
        putConfigByte(0x05, (byte)0x00);
        putConfigByte(0x06, (byte)0x00);
        putConfigByte(0x07, (byte)0x02); // PCI_status_devsel_medium
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

    public void setIRQ(PCIDevice device, int irqNumber, int level)
    {
        irqNumber = this.slotGetPIRQ(device, irqNumber);
        int irqIndex = device.getIRQIndex();
        int shift = (irqIndex & 0x1f);
        int p = irqLevels[irqNumber][irqIndex >> 5];
        irqLevels[irqNumber][irqIndex >> 5] = (p & ~(1 << shift)) | (level << shift);

        /* now we change the pic irq level according to the piix irq mappings */
        int picIRQ = this.getConfigByte(0x60 + irqNumber); //short/int/long?
        if (picIRQ < 16) {
            /* the pic level is the logical OR of all the PCI irqs mapped to it */
            int picLevel = 0;
            for (int i = 0; i < 4; i++) {
                if (picIRQ == this.getConfigByte(0x60 + i))
                    picLevel |= getIRQLevel(i);
            }
            this.getInterruptController().setIRQ(picIRQ, picLevel);
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

    public IRQBouncer makeBouncer(PCIDevice device)
    {
        return new DefaultIRQBouncer(this);
    }

    public int slotGetPIRQ(PCIDevice device, int irqNumber)
    {
        int slotAddEnd;
        slotAddEnd = (device.getCurrentDevFN() >> 3);
        return (irqNumber + slotAddEnd) & 0x3;
    }

    static class DefaultIRQBouncer implements IRQBouncer
    {
        private PCIISABridge attachedISABridge;

        public void dumpSR(org.jpc.support.SRDumper output) throws IOException
        {
            if(output.dumped(this))
                return;
            dumpSRPartial(output);
            output.endObject();
        }

        public void dumpSRPartial(org.jpc.support.SRDumper output) throws IOException
        {
            output.dumpObject(attachedISABridge);
        }

        public void dumpStatusPartial(org.jpc.support.StatusDumper output)
        {
            output.println("\tattachedISABridge <object #" + output.objectNumber(attachedISABridge) + ">"); if(attachedISABridge != null) attachedISABridge.dumpStatus(output);
        }
 
        public void dumpStatus(org.jpc.support.StatusDumper output)
        {
            if(output.dumped(this))
                return;

            output.println("#" + output.objectNumber(this) + ": DefaultIRQBouncer:");
            dumpStatusPartial(output);
            output.endObject();
        }


        public DefaultIRQBouncer(PCIISABridge isaBridge)
        {
            attachedISABridge = isaBridge;
        }

        public void setIRQ(PCIDevice device, int irqNumber, int level)
        {
            attachedISABridge.setIRQ(device, irqNumber, level);
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

    public InterruptController getInterruptController()
    {
        return irqDevice;
    }

    public void reset()
    {
        irqDevice = null;

        putConfigByte(0x00, (byte)0x86); // Intel
        putConfigByte(0x01, (byte)0x80);
        putConfigByte(0x02, (byte)0x00); // 82371SB PIIX3 PCI-to-ISA bridge (Step A1)
        putConfigByte(0x03, (byte)0x70);
        putConfigByte(0x0a, (byte)0x01); // class_sub = PCI_ISA
        putConfigByte(0x0b, (byte)0x06); // class_base = PCI_bridge
        putConfigByte(0x0e, (byte)0x80); // header_type = PCI_multifunction, generic
        internalReset();

        super.reset();
    }

    public boolean initialised()
    {
        return (irqDevice != null) && super.initialised();
    }

    public void acceptComponent(HardwareComponent component)
    {
        if ((component instanceof InterruptController)
            && component.initialised())
            irqDevice = (InterruptController)component;

        super.acceptComponent(component);
    }

    public boolean updated()
    {
        return (irqDevice.updated()) && super.updated();
    }

    public void updateComponent(HardwareComponent component)
    {
        //        if ((component instanceof InterruptController)
        //            && component.updated())
        //            irqDevice = (InterruptController)component;

        super.acceptComponent(component);
    }

    public String toString()
    {
        return "Intel 82371SB PIIX3 PCI ISA Bridge";
    }
}

