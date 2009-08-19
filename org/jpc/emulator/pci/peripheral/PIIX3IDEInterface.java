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

package org.jpc.emulator.pci.peripheral;

import org.jpc.emulator.StatusDumper;
import org.jpc.emulator.pci.*;
import org.jpc.emulator.motherboard.*;
import org.jpc.emulator.DriveSet;
import org.jpc.diskimages.BlockDevice;
import org.jpc.emulator.HardwareComponent;
import org.jpc.emulator.memory.PhysicalAddressSpace;
import org.jpc.emulator.SRLoader;
import org.jpc.emulator.SRDumper;

import java.io.*;

/**
 *
 * @author Chris Dennis
 */
public class PIIX3IDEInterface extends AbstractPCIDevice
{
    private InterruptController irqDevice;
    private IDEChannel[] channels;
    private boolean drivesUpdated;

    private BMDMAIORegion[] bmdmaRegions;

    private BlockDevice[] drives;

    public void dumpStatusPartial(StatusDumper output)
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

    public void dumpStatus(StatusDumper output)
    {
        if(output.dumped(this))
            return;

        output.println("#" + output.objectNumber(this) + ": PIIX3IDEInterface:");
        dumpStatusPartial(output);
        output.endObject();
    }

    public void dumpSRPartial(SRDumper output) throws IOException
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

    public PIIX3IDEInterface(SRLoader input) throws IOException
    {
        super(input);
        drivesUpdated = input.loadBoolean();
        irqDevice = (InterruptController)input.loadObject();
        channels = new IDEChannel[input.loadInt()];
        for (int i=0; i < channels.length; i++)
            channels[i] = (IDEChannel)input.loadObject();
        bmdmaRegions = new BMDMAIORegion[input.loadInt()];
        for (int i=0; i < bmdmaRegions.length; i++)
            bmdmaRegions[i] = (BMDMAIORegion)input.loadObject();
        boolean drivesPresent = input.loadBoolean();
        if(drivesPresent) {
            drives = new BlockDevice[input.loadInt()];
            for (int i=0; i < drives.length; i++)
                drives[i] = (BlockDevice)input.loadObject();
        } else
            drives = null;
    }

    public PIIX3IDEInterface()
    {
        devfnSet = false;
        ioportRegistered = false;
        pciRegistered = false;
        dmaRegistered = false;

        this.assignDeviceFunctionNumber(-1);

        putConfigWord(PCI_CONFIG_VENDOR_ID, (short)0x8086); // Intel
        putConfigWord(PCI_CONFIG_DEVICE_ID, (short)0x7010);
        putConfigByte(0x09, (byte)0x80); // legacy ATA mode
        putConfigWord(PCI_CONFIG_CLASS_DEVICE, (short)0x0101); // PCI IDE
        putConfigByte(PCI_CONFIG_HEADER, (byte)0x00); // header_type


        channels = new IDEChannel[2];

        bmdmaRegions = new BMDMAIORegion[2];
        //Run BMDMARegion constructors Remember 0=1 and 2=3
        bmdmaRegions[1] = new BMDMAIORegion(null, true);
        bmdmaRegions[0] = new BMDMAIORegion(bmdmaRegions[1], true);
    }

    public boolean autoAssignDeviceFunctionNumber()
    {
        return false;
    }

    public void deassignDeviceFunctionNumber()
    {
        System.err.println("Warning: PCI device/function number conflict.");
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

        this.assignDeviceFunctionNumber(-1);

        putConfigWord(PCI_CONFIG_VENDOR_ID, (short)0x8086); // Intel
        putConfigWord(PCI_CONFIG_DEVICE_ID, (short)0x7010);
        putConfigByte(0x09, (byte)0x80); // legacy ATA mode
        putConfigWord(PCI_CONFIG_CLASS_DEVICE, (short)0x0101); // PCI IDE
        putConfigByte(PCI_CONFIG_HEADER, (byte)0x00); // header_type

        channels = new IDEChannel[2];

        dmaRegistered = false;
        bmdmaRegions = new BMDMAIORegion[2];
        //Run BMDMARegion constructors Remember 0=1 and 2=3
        bmdmaRegions[1] = new BMDMAIORegion(null, true);
        bmdmaRegions[0] = new BMDMAIORegion(bmdmaRegions[1], true);

        irqDevice = null;
        drives = null;

        super.reset();
    }

    private boolean devfnSet;

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
            this.assignDeviceFunctionNumber(((PCIDevice)component).getDeviceFunctionNumber() + 1);
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
            bmdmaRegions[0].setAddressSpace((PhysicalAddressSpace)component);
            bmdmaRegions[1].setAddressSpace((PhysicalAddressSpace)component);
        }
    }

    public String toString()
    {
        return "Intel PIIX3 IDE Interface";
    }
}
