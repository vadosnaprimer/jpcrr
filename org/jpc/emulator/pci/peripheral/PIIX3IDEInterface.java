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

package org.jpc.emulator.pci.peripheral;

import org.jpc.emulator.pci.*;
import org.jpc.emulator.motherboard.*;
import org.jpc.support.*;
import org.jpc.emulator.HardwareComponent;
import org.jpc.emulator.memory.*;
import java.io.*;

public class PIIX3IDEInterface extends AbstractPCIDevice implements HardwareComponent
{
    private InterruptController irqDevice;
    private IDEChannel[] channels;
    private boolean drivesUpdated;

    private BMDMAIORegion[] bmdmaRegions;

    private BlockDevice[] drives;
    private Magic magic;

    public void dumpStatusPartial(org.jpc.support.StatusDumper output)
    {
        super.dumpStatusPartial(output);
        output.println("\tdrivesUpdated " + drivesUpdated);
        output.println("\tirqDevice <object #" + output.objectNumber(irqDevice) + ">"); if(irqDevice != null) irqDevice.dumpStatus(output);
        for (int i=0; i < channels.length; i++) {
            output.println("\tchannels[" + i + "] <object #" + output.objectNumber(channels[i]) + ">"); if(channels[i] != null) channels[i].dumpStatus(output);
        }
        for (int i=0; i < bmdmaRegions.length; i++) {
            output.println("\tbmdmaRegions[" + i + "] <object #" + output.objectNumber(bmdmaRegions[i]) + ">"); if(bmdmaRegions[i] != null) bmdmaRegions[i].dumpStatus(output);
        }
        if(drives != null)
            for (int i=0; i < drives.length; i++) {
                output.println("\tdrives[" + i + "] <object #" + output.objectNumber(drives[i]) + ">"); if(drives[i] != null) drives[i].dumpStatus(output);
            }
        else
            output.println("\tdrives null");
    }
 
    public void dumpStatus(org.jpc.support.StatusDumper output)
    {
        if(output.dumped(this))
            return;

        output.println("#" + output.objectNumber(this) + ": PIIX3IDEInterface:");
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
        output.dumpBoolean(drivesUpdated);
        output.dumpObject(irqDevice);
        output.dumpInt(channels.length);
        for (int i=0; i < channels.length; i++)
            output.dumpObject(channels[i]);
        output.dumpInt(bmdmaRegions.length);
        for (int i=0; i < bmdmaRegions.length; i++)
            output.dumpObject(bmdmaRegions[i]);
        if(drives != null) {
            output.dumpBoolean(true);
            output.dumpInt(drives.length);
            for (int i=0; i < drives.length; i++) 
                output.dumpObject(drives[i]);
        } else
            output.dumpBoolean(false);
    }

    public PIIX3IDEInterface(org.jpc.support.SRLoader input) throws IOException
    {
        super(input);
        drivesUpdated = input.loadBoolean();
        irqDevice = (InterruptController)(input.loadObject());
        channels = new IDEChannel[input.loadInt()];
        for (int i=0; i < channels.length; i++)
            channels[i] = (IDEChannel)(input.loadObject());
        bmdmaRegions = new BMDMAIORegion[input.loadInt()];
        for (int i=0; i < bmdmaRegions.length; i++)
            bmdmaRegions[i] = (BMDMAIORegion)(input.loadObject());
        boolean drivesPresent = input.loadBoolean();
        if(drivesPresent) {
            drives = new BlockDevice[input.loadInt()];
            for (int i=0; i < drives.length; i++)
                drives[i] = (BlockDevice)(input.loadObject());
        } else
            drives = null;
    }

    public static org.jpc.SRDumpable loadSR(org.jpc.support.SRLoader input, Integer id) throws IOException
    {
        org.jpc.SRDumpable x = new PIIX3IDEInterface(input);
        input.endObject();
        return x;
    }

    public PIIX3IDEInterface()
    {
        magic = new Magic(Magic.PIIX3_IDE_INTERFACE_MAGIC_V1);
        devfnSet = false;
        ioportRegistered = false;
        pciRegistered = false;
        dmaRegistered = false;

        this.assignDevFN(-1);

        this.putConfigByte(0x00, (byte)0x86); // Intel
        this.putConfigByte(0x01, (byte)0x80);
        this.putConfigByte(0x02, (byte)0x10);
        this.putConfigByte(0x03, (byte)0x70);
        this.putConfigByte(0x09, (byte)0x80); // legacy ATA mode
        this.putConfigByte(0x0a, (byte)0x01); // class_sub = PCI_IDE
        this.putConfigByte(0x0b, (byte)0x01); // class_base = PCI_mass_storage
        this.putConfigByte(0x0e, (byte)0x00); // header_type

        channels = new IDEChannel[2];

        bmdmaRegions = new BMDMAIORegion[2];
        //Run BMDMARegion constructors Remember 0=1 and 2=3
        bmdmaRegions[1] = new BMDMAIORegion();
        bmdmaRegions[0] = new BMDMAIORegion(bmdmaRegions[1]);
    }

    public void dumpState(DataOutput output) throws IOException
    {
        magic.dumpState(output);
        channels[0].dumpState(output);
        channels[1].dumpState(output);
        for (int i=0; i < bmdmaRegions.length; i++)
        {
            if (bmdmaRegions[i] != null)
                bmdmaRegions[i].dumpState(output);
        }
    }

    public void loadState(DataInput input) throws IOException
    {
        magic.loadState(input);
        channels[0].loadState(input);
        channels[1].loadState(input);
        bmdmaRegions[0].loadState(input);
        bmdmaRegions[1].loadState(input);
    }

    public void loadIOPorts(IOPortHandler ioportHandler, DataInput input) throws IOException
    {
        drivesUpdated = false;
        devfnSet = true;
        pciRegistered = false;
        ioportRegistered = false;
        dmaRegistered = false;
        loadState(input);
        ioportHandler.registerIOPortCapable(channels[0]);
        ioportHandler.registerIOPortCapable(channels[1]);
        if (!(bmdmaRegions[0].ioPortsRequested()[0] == -1))
            ioportHandler.registerIOPortCapable(bmdmaRegions[0]);
        if (!(bmdmaRegions[1].ioPortsRequested()[0] == -1))
            ioportHandler.registerIOPortCapable(bmdmaRegions[1]);
    }

    public boolean autoAssignDevFN()
    {
        return false;
    }

    public void deassignDevFN()
    {
        System.err.println("Conflict with IDE Interface over PCI Device FN");
    }



    public IORegion[] getIORegions()
    {
        return new IORegion[]{bmdmaRegions[0]};
    }
    public IORegion getIORegion(int index)
    {
        if (index == 4) {
            return bmdmaRegions[0];
        } else {
            return null;
        }
    }


    private boolean ioportRegistered;
    private boolean pciRegistered;
    private boolean dmaRegistered;

    public boolean initialised()
    {
        return ioportRegistered && pciRegistered && dmaRegistered && (irqDevice != null) && (drives != null);
    }

    public void reset()
    {
        devfnSet = false;
        ioportRegistered = false;
        pciRegistered = false;

        this.assignDevFN(-1);

        this.putConfigByte(0x00, (byte)0x86); // Intel
        this.putConfigByte(0x01, (byte)0x80);
        this.putConfigByte(0x02, (byte)0x10);
        this.putConfigByte(0x03, (byte)0x70);
        this.putConfigByte(0x09, (byte)0x80); // legacy ATA mode
        this.putConfigByte(0x0a, (byte)0x01); // class_sub = PCI_IDE
        this.putConfigByte(0x0b, (byte)0x01); // class_base = PCI_mass_storage
        this.putConfigByte(0x0e, (byte)0x00); // header_type

        channels = new IDEChannel[2];

        dmaRegistered = false;
        bmdmaRegions = new BMDMAIORegion[2];
        //Run BMDMARegion constructors Remember 0=1 and 2=3
        bmdmaRegions[1] = new BMDMAIORegion();
        bmdmaRegions[0] = new BMDMAIORegion(bmdmaRegions[1]);

        irqDevice = null;
        drives = null;

        super.reset();
    }

    private boolean devfnSet;

    public boolean updated()
    {
        return ioportRegistered && pciRegistered && dmaRegistered && irqDevice.updated() && drivesUpdated;
    }

    public void updateComponent(HardwareComponent component)
    {
        if ((component instanceof IOPortHandler) && irqDevice.updated() && drivesUpdated)
        {
            //Run IDEChannel Constructors
            //            channels[0] = new IDEChannel(14, irqDevice, 0x1f0, 0x3f6, new BlockDevice[]{drives[0], drives[1]}, bmdmaRegions[0]);
            //            channels[1] = new IDEChannel(15, irqDevice, 0x170, 0x376, new BlockDevice[]{drives[2], drives[3]}, bmdmaRegions[1]);
            channels[0].setDrives(new BlockDevice[]{drives[0], drives[1]});
            channels[1].setDrives(new BlockDevice[]{drives[2], drives[3]});
            ((IOPortHandler)component).registerIOPortCapable(channels[0]);
            ((IOPortHandler)component).registerIOPortCapable(channels[1]);
            ioportRegistered = true;
        }

        if ((component instanceof PCIBus) && component.updated() && !pciRegistered && devfnSet) {
            pciRegistered = ((PCIBus)component).registerDevice(this);
        }

        if ((component instanceof PCIISABridge) && component.updated()) {
            this.assignDevFN(((PCIDevice)component).getCurrentDevFN() + 1);
            devfnSet = true;
        }

        if ((component instanceof DriveSet) && component.updated())
        {
            //            drives = new BlockDevice[4];
            drives[0] = ((DriveSet)component).getHardDrive(0);
            drives[1] = ((DriveSet)component).getHardDrive(1);
            drives[2] = ((DriveSet)component).getHardDrive(2);
            drives[3] = ((DriveSet)component).getHardDrive(3);
            drivesUpdated = true;
        }

        if (component instanceof PhysicalAddressSpace)
        {
            dmaRegistered = true;
            bmdmaRegions[0].setAddressSpace((Memory)component);
            bmdmaRegions[1].setAddressSpace((Memory)component);
        }
    }

    public void acceptComponent(HardwareComponent component)
    {
        if ((component instanceof InterruptController) && component.initialised())
            irqDevice = (InterruptController)component;

        if ((component instanceof IOPortHandler) && component.initialised()
            && (irqDevice != null) && (drives != null)) {
            //Run IDEChannel Constructors
            channels[0] = new IDEChannel(14, irqDevice, 0x1f0, 0x3f6, new BlockDevice[]{drives[0], drives[1]}, bmdmaRegions[0]);
            channels[1] = new IDEChannel(15, irqDevice, 0x170, 0x376, new BlockDevice[]{drives[2], drives[3]}, bmdmaRegions[1]);
            ((IOPortHandler)component).registerIOPortCapable(channels[0]);
            ((IOPortHandler)component).registerIOPortCapable(channels[1]);
            ioportRegistered = true;
        }

        if ((component instanceof PCIBus) && component.initialised() && !pciRegistered && devfnSet) {
            pciRegistered = ((PCIBus)component).registerDevice(this);
        }

        if ((component instanceof PCIISABridge) && component.initialised()) {
            this.assignDevFN(((PCIDevice)component).getCurrentDevFN() + 1);
            devfnSet = true;
        }

        if ((component instanceof DriveSet)
            && component.initialised()) {
            drives = new BlockDevice[4];
            drives[0] = ((DriveSet)component).getHardDrive(0);
            drives[1] = ((DriveSet)component).getHardDrive(1);
            drives[2] = ((DriveSet)component).getHardDrive(2);
            drives[3] = ((DriveSet)component).getHardDrive(3);
        }

        if (component instanceof PhysicalAddressSpace) {
            dmaRegistered = true;
            bmdmaRegions[0].setAddressSpace((Memory)component);
            bmdmaRegions[1].setAddressSpace((Memory)component);
        }
    }

    public String toString()
    {
        return "Intel PIIX3 IDE Interface";
    }
}
