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
import org.jpc.images.COWImage;
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

    private BMDMAIORegion[] bmdmaRegions;

    private COWImage[] images;

    public void dumpStatusPartial(StatusDumper output)
    {
        super.dumpStatusPartial(output);
        output.println("\tirqDevice <object #" + output.objectNumber(irqDevice) + ">"); if(irqDevice != null) irqDevice.dumpStatus(output);
        for (int i=0; i < channels.length; i++) {
            output.println("\tchannels[" + i + "] <object #" + output.objectNumber(channels[i]) + ">"); if(channels[i] != null) channels[i].dumpStatus(output);
        }
        for (int i=0; i < bmdmaRegions.length; i++) {
            output.println("\tbmdmaRegions[" + i + "] <object #" + output.objectNumber(bmdmaRegions[i]) + ">"); if(bmdmaRegions[i] != null) bmdmaRegions[i].dumpStatus(output);
        }
        if(images != null)
            for (int i=0; i < images.length; i++) {
                output.println("\timages[" + i + "] <object #" + output.objectNumber(images[i]) + ">"); if(images[i] != null) images[i].dumpStatus(output);
            }
        else
            output.println("\timages null");
    }

    public boolean hasCD()
    {
        return (images[2] == null);
    }

    public void swapCD(COWImage img) throws IOException
    {
        if(images[2] != null)
            throw new IOException("Trying to swap CD-ROM with no CD-ROM drive");
        channels[1].setDrive(img, 0);
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
        output.dumpObject(irqDevice);
        output.dumpInt(channels.length);
        for (int i=0; i < channels.length; i++)
            output.dumpObject(channels[i]);
        output.dumpInt(bmdmaRegions.length);
        for (int i=0; i < bmdmaRegions.length; i++)
            output.dumpObject(bmdmaRegions[i]);
        if(images != null) {
            output.dumpBoolean(true);
            output.dumpInt(images.length);
            for (int i=0; i < images.length; i++)
                output.dumpObject(images[i]);
        } else
            output.dumpBoolean(false);
    }

    public PIIX3IDEInterface(SRLoader input) throws IOException
    {
        super(input);
        irqDevice = (InterruptController)input.loadObject();
        channels = new IDEChannel[input.loadInt()];
        for (int i=0; i < channels.length; i++)
            channels[i] = (IDEChannel)input.loadObject();
        bmdmaRegions = new BMDMAIORegion[input.loadInt()];
        for (int i=0; i < bmdmaRegions.length; i++)
            bmdmaRegions[i] = (BMDMAIORegion)input.loadObject();
        boolean drivesPresent = input.loadBoolean();
        if(drivesPresent) {
            images = new COWImage[input.loadInt()];
            for (int i=0; i < images.length; i++)
                images[i] = (COWImage)input.loadObject();
        } else
            images = null;
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
        return ioportRegistered && pciRegistered && dmaRegistered && (irqDevice != null) && (images != null);
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
        images = null;

        super.reset();
    }

    private boolean devfnSet;

    public void acceptComponent(HardwareComponent component)
    {
        if((component instanceof InterruptController) && component.initialised())
            irqDevice = (InterruptController)component;

        if((component instanceof IOPortHandler) && component.initialised() && !ioportRegistered
            && (irqDevice != null) && (images != null)) {
            //Run IDEChannel Constructors
            channels[0] = new IDEChannel(14, irqDevice, 0x1f0, 0x3f6, new COWImage[]{images[0], images[1]}, bmdmaRegions[0], false);
            channels[1] = new IDEChannel(15, irqDevice, 0x170, 0x376, new COWImage[]{images[2], images[3]}, bmdmaRegions[1], true);
            ((IOPortHandler)component).registerIOPortCapable(channels[0]);
            ((IOPortHandler)component).registerIOPortCapable(channels[1]);
            ioportRegistered = true;
        }

        if((component instanceof PCIBus) && component.initialised() && !pciRegistered && devfnSet)
            pciRegistered = ((PCIBus)component).registerDevice(this);

        if((component instanceof PCIISABridge) && component.initialised()) {
            this.assignDeviceFunctionNumber(((PCIDevice)component).getDeviceFunctionNumber() + 1);
            devfnSet = true;
        }

        if((component instanceof DriveSet) && component.initialised()) {
            images = new COWImage[4];
            images[0] = ((DriveSet)component).getHardDrive(0);
            images[1] = ((DriveSet)component).getHardDrive(1);
            images[2] = ((DriveSet)component).getHardDrive(2);
            images[3] = ((DriveSet)component).getHardDrive(3);
        }

        if(component instanceof PhysicalAddressSpace) {
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
