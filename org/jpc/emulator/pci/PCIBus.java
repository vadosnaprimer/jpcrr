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
import org.jpc.emulator.*;
import java.io.*;

public class PCIBus implements HardwareComponent
{
    private int busNumber;
    private int devFNMinimum;
    private boolean updated;

    //openpic_t openpic; // Neccessary?

    private PCIDevice devices[];

    private PCIISABridge isaBridge;
    private IOPortHandler ioportHandler;
    private PhysicalAddressSpace memory;

    //pci_mem_base?
    private int pciIRQIndex;
    private int pciIRQLevels[][];

    public static final int PCI_COMMAND = 0x04;

    final static private int PCI_VENDOR_ID = 0x00;
    final static private int PCI_DEVICE_ID = 0x02;

    final static private int PCI_COMMAND_IO = 0x1;
    final static private int PCI_COMMAND_MEMORY = 0x2;
    final static private int PCI_CLASS_DEVICE = 0x0a;
    final static private int PCI_INTERRUPT_LINE = 0x3c;
    final static private int PCI_INTERRUPT_PIN = 0x3d;
    final static private int PCI_MIN_GNT = 0x3e;
    final static private int PCI_MAX_LAT = 0x3f;

    final static private int PCI_DEVICES_MAX = 64;
    public static final int PCI_IRQ_WORDS = ((PCI_DEVICES_MAX + 31) / 32);

    public PCIBus()
    {
        busNumber = 0;
        pciIRQIndex = 0;
        devices = new PCIDevice[256];
        pciIRQLevels = new int[4][PCI_IRQ_WORDS];
        devFNMinimum = 8;
    }

    public void dumpState(DataOutput output) throws IOException
    {
        output.writeInt(busNumber);
        output.writeInt(devFNMinimum);
        output.writeInt(pciIRQIndex);
        output.writeInt(pciIRQLevels.length);
        output.writeInt(pciIRQLevels[0].length);
        for (int i=0; i < pciIRQLevels.length; i++)
            for (int j=0; j < pciIRQLevels[0].length; j++)
                output.writeInt(pciIRQLevels[i][j]);
        output.writeInt(biosIOAddress);
        output.writeInt(biosMemoryAddress);
    }

    public void loadState(DataInput input) throws IOException
    {
        updated = false;
        devices = new PCIDevice[256];
        busNumber  = input.readInt();
        devFNMinimum  = input.readInt();
        pciIRQIndex  = input.readInt();
        int len1  = input.readInt();
        int len2 = input.readInt();
        pciIRQLevels = new int[len1][len2];
        for (int i=0; i < pciIRQLevels.length; i++)
            for (int j=0; j < pciIRQLevels[0].length; j++)
                pciIRQLevels[i][j] = input.readInt();
        biosIOAddress = input.readInt();
        biosMemoryAddress = input.readInt();
    }

    public boolean registerDevice(PCIDevice device)
    {
        if (pciIRQIndex >= PCI_DEVICES_MAX) {
            return false;
        }

        /*
        if (device instanceof PCIISABridge)
            devFNMinimum = 8;
        else
            devFNMinimum = 16;
        */

        if (device.autoAssignDevFN()) {
            int devFN = findFreeDevFN();
            if (0 <= devFN) {
                device.assignDevFN(devFN);
            }
        } else {
            PCIDevice oldDevice = devices[device.getCurrentDevFN()];
            if (oldDevice != null) {
                System.err.println("Trying to temporarily unregister a pci device, this may not work.");
                oldDevice.deassignDevFN();
            }
        }

        device.setIRQIndex(pciIRQIndex++);
        this.addDevice(device);

        IRQBouncer bouncer = isaBridge.makeBouncer(device);
        device.addIRQBouncer(bouncer);
        return this.registerPCIIORegions(device);
    }

    private int findFreeDevFN()
    {
            for(int i = devFNMinimum; i < 256; i += 8) {
                if (null == devices[i])
                    return i;
            }
            return -1;
    }

    private boolean registerPCIIORegions(PCIDevice device)
    {
        IORegion[] regions = device.getIORegions();

        if (regions == null) return true;

        boolean ret = true;
        for (int i = 0; i < regions.length; i++) {
            IORegion region = regions[i];
            if (PCIDevice.PCI_NUM_REGIONS <= region.getRegionNumber()) {
                ret = false;
                continue;
            }
            region.setAddress(-1);

            if (region.getRegionNumber() == PCIDevice.PCI_ROM_SLOT)
                device.putConfigInt(0x30, region.getType());
            else
                device.putConfigInt(0x10 + region.getRegionNumber() * 4, region.getType());
        }
        return ret;
    }

    private void updateMappings(PCIDevice device)
    {
        int lastAddress, newAddress, configOffset;
        
        IORegion[] regions = device.getIORegions();
        if (regions == null) return;
        short command = device.getConfigShort(PCI_COMMAND);
        for(int i = 0; i < regions.length; i++) 
        {
            IORegion r = regions[i];
            if (null == r)
                continue;

            if (PCIDevice.PCI_NUM_REGIONS <= r.getRegionNumber())
                continue;

            if (PCIDevice.PCI_ROM_SLOT == r.getRegionNumber())
                configOffset = 0x30;
            else
                configOffset = 0x10 + r.getRegionNumber() * 4;
                        
            if (r instanceof IOPortIORegion) {
                if (0 != (command & PCI_COMMAND_IO)) {
                    newAddress = device.getConfigInt(configOffset);
                    newAddress &= ~(r.getSize() - 1);
                    lastAddress = newAddress + (int)r.getSize() - 1;
                    
                    if (lastAddress <= (0xffffffffl & newAddress) || 0 == newAddress || 0x10000 <= (0xffffffffl & lastAddress))
                        newAddress = -1;
                }
                else 
                    newAddress = -1;
            } else if (r instanceof MemoryMappedIORegion) {
                if (0 != (command & PCI_COMMAND_MEMORY)) {
                    newAddress = device.getConfigInt(configOffset);
                    if (PCIDevice.PCI_ROM_SLOT == r.getRegionNumber()
                        && (0 == (newAddress & 1))) {
                        newAddress = -1;
                    } else {
                        newAddress &= ~(r.getSize() - 1);
                        lastAddress = newAddress + (int)r.getSize() - 1;
                        if (lastAddress <= newAddress || 0 == newAddress || -1 == lastAddress)
                            newAddress = -1;
                    } 
                }
                else 
                    newAddress = -1;
            } else {
                throw new IllegalStateException("Unknown IORegion Type");
            }

            if (r.getAddress() != newAddress) 
            {
                if (r.getAddress() != -1) 
                {
                    if (r instanceof IOPortIORegion) {
                        int deviceClass;
                        deviceClass = device.getConfigByte(0x0a) | (device.getConfigByte(0x0b) << 8);
                        if (0x0101 == deviceClass && 4 == r.getSize()) 
                        {
                            //r.unmap(); must actually be partial
                            System.err.println("Supposed to partially unmap");
                            ioportHandler.deregisterIOPortCapable((IOPortIORegion)r);
                        } else {
                            //r.unmap();
                            ioportHandler.deregisterIOPortCapable((IOPortIORegion)r);
                        }
                    } else if (r instanceof MemoryMappedIORegion) {
                        memory.unmap(r.getAddress(), (int)r.getSize());
                    }
                }

                r.setAddress(newAddress);
                if (r.getAddress() != -1) 
                {
                    if (r instanceof IOPortIORegion) 
                    {
                        IOPortIORegion pr = (IOPortIORegion) r;
                        ioportHandler.registerIOPortCapable((IOPortIORegion)r);
                    }
                    else if (r instanceof MemoryMappedIORegion) 
                    {
                        MemoryMappedIORegion mmap = (MemoryMappedIORegion) r;
                        memory.mapMemoryRegion(mmap, r.getAddress(), (int)r.getSize());
                    }
                }
            }
        }
    }

    private void addDevice(PCIDevice device)
    {
        devices[device.getCurrentDevFN()] = device;
    }

    //PCIHostBridge shifted functionality
    private PCIDevice validPCIDataAccess(int address)
    {
        int busNumber = (address >>> 16) & 0xff;
        if (0 != busNumber)
            return null;
        return this.devices[(address >>> 8) & 0xff];
    }

    public void writePCIDataByte(int address, byte data)
    {
        PCIDevice device = this.validPCIDataAccess(address);
        if(null == device) return;

        if (device.configWriteByte(address & 0xff, data))
            this.updateMappings(device);
    }
    public void writePCIDataWord(int address, short data)
    {
        PCIDevice device = this.validPCIDataAccess(address);        
        if(null == device) return;
        
        if (device.configWriteWord(address & 0xff, data))
            this.updateMappings(device);
    }
    public void writePCIDataLong(int address, int data)
    {
        PCIDevice device = this.validPCIDataAccess(address);
        if(null == device) return;
        
        if (device.configWriteLong(address & 0xff, data))
            this.updateMappings(device);
    }

    public byte readPCIDataByte(int address)
    {
        PCIDevice device = this.validPCIDataAccess(address);
        if(null == device) return (byte)0xff;

        return device.configReadByte(address & 0xff);
    }
    public short readPCIDataWord(int address)
    {
        PCIDevice device = this.validPCIDataAccess(address);
        if (null == device) return (short)0xffff;

        return device.configReadWord(address & 0xff);
    }
    public int readPCIDataLong(int address)
    {
        PCIDevice device = this.validPCIDataAccess(address);
        if (null == device) return (int)0xffffffff;

        return device.configReadLong(address & 0xff);
    }

    private int getBusNumber()
    {
        return busNumber;
    }

    //Bad BIOS?  These methods help initialise Bus (also useful to use unconnected bus)
    private static final byte[] pciIRQs = new byte[]{11, 9, 11, 9};
    private int biosIOAddress;
    private int biosMemoryAddress;

    public void biosInit()
    {
        biosIOAddress = 0xc000;
        biosMemoryAddress = 0xf0000000;
        byte elcr[] = new byte[2];

        /* activate IRQ mappings */
        elcr[0] = 0x00;
        elcr[1] = 0x00;
        for(int i = 0; i < 4; i++) {
            byte irq = pciIRQs[i];
            /* set to trigger level */
            elcr[irq >> 3] |= (1 << (irq & 7));
            /* activate irq remapping in PIIX */
            this.configWriteByte(isaBridge, 0x60 + i, irq);
        }


        ioportHandler.ioPortWriteByte(0x4d0, elcr[0]); // setup io master
        ioportHandler.ioPortWriteByte(0x4d1, elcr[1]); // setup io slave

        for(int devFN = 0; devFN < 256; devFN++) {
            PCIDevice device = devices[devFN];
            if (device != null)
                biosInitDevice(device);
        }
    }

    private final void biosInitDevice(PCIDevice device)
    {
        int deviceClass = 0xffff & configReadWord(device, PCI_CLASS_DEVICE);
        int vendorID = 0xffff & configReadWord(device, PCI_VENDOR_ID);
        int deviceID = 0xffff & configReadWord(device, PCI_DEVICE_ID);

        switch(deviceClass) {
        case 0x0101:
            if ((0xffff & vendorID) == 0x8086 && (0xffff & deviceID) == 0x7010) {
                /* PIIX3 IDE */
                this.configWriteWord(device, 0x40, (short)0x8000);
                this.configWriteWord(device, 0x42, (short)0x8000);
                this.defaultIOMap(device);
            } else {
                /* IDE: we map it as in ISA mode */
                this.setIORegionAddress(device, 0, 0x1f0);
                this.setIORegionAddress(device, 1, 0x3f4);
                this.setIORegionAddress(device, 2, 0x170);
                this.setIORegionAddress(device, 3, 0x374);
            }
            break;
        case 0x0300:
            if (vendorID != 0x1234) {
                this.defaultIOMap(device);
                break;
            }
            /* VGA: map frame buffer to default Bochs VBE address */
            this.setIORegionAddress(device, 0, 0xe0000000);
            break;
        case 0x0800:
            /* PIC */
            if (vendorID == 0x1014) {
                /* IBM */
                if (deviceID == 0x0046 || deviceID == 0xffff) {
                    /* MPIC & MPIC2 */
                    this.setIORegionAddress(device, 0, 0x80800000 + 0x00040000);
                }
            }
            break;
        case 0xff00:
            if (vendorID == 0x0106b && (deviceID == 0x0017 || deviceID == 0x0022)) {
                /* macio bridge */
                this.setIORegionAddress(device, 0, 0x80800000);
            }
            break;
        default:
            this.defaultIOMap(device);
            break;
        }

        /* map the interrupt */
        int pin = configReadByte(device, PCI_INTERRUPT_PIN);
        if (pin != 0) {
            pin = isaBridge.slotGetPIRQ(device, pin - 1);
            this.configWriteByte(device, PCI_INTERRUPT_LINE, pciIRQs[pin]);

        }

    }

    private void defaultIOMap(PCIDevice device)
    {
        IORegion[] regions = device.getIORegions();
        if (regions == null) return;
        for (int i=0; i < regions.length; i++) {
            if (regions[i] == null) continue;
            if (regions[i] instanceof IOPortIORegion) {
                int paddr = biosIOAddress;
                paddr = (int)((paddr + regions[i].getSize() - 1) &
                    ~(regions[i].getSize() - 1));
                this.setIORegionAddress(device, regions[i].getRegionNumber(), paddr);
                biosIOAddress += regions[i].getSize();
            } else if (regions[i] instanceof MemoryMappedIORegion) {
                int paddr = biosMemoryAddress;
                paddr = (int)((paddr + regions[i].getSize() - 1) &
                    ~(regions[i].getSize() - 1));
                this.setIORegionAddress(device, regions[i].getRegionNumber(), paddr);
                biosMemoryAddress += regions[i].getSize();
            }
        }            
    }

    private void configWriteByte(PCIDevice device, int address, byte data)
    {
        address |= (getBusNumber() << 16) | (device.getCurrentDevFN() << 8);
        writePCIDataByte(address, data);
    }
    private byte configReadByte(PCIDevice device, int address)
    {
        address |= (getBusNumber() << 16) | (device.getCurrentDevFN() << 8);
        return readPCIDataByte(address);
    }
    private void configWriteWord(PCIDevice device, int address, short data)
    {
        address |= (getBusNumber() << 16) | (device.getCurrentDevFN() << 8);
        writePCIDataWord(address, data);
    }
    private short configReadWord(PCIDevice device, int address)
    {
        address |= (getBusNumber() << 16) | (device.getCurrentDevFN() << 8);
        return readPCIDataWord(address);
    }
    private void configWriteLong(PCIDevice device, int address, int data)
    {
        address |= (getBusNumber() << 16) | (device.getCurrentDevFN() << 8);
        writePCIDataLong(address, data);
    }
    private void setIORegionAddress(PCIDevice device, int regionNumber, int address)
    {
        int offset;
        if (regionNumber == PCIDevice.PCI_ROM_SLOT) {
            offset = 0x30;
        } else {
            offset = 0x10 + regionNumber * 4;
        }

        this.configWriteLong(device, offset, address);

        /* enable memory mappings */
        IORegion region = device.getIORegion(regionNumber);
        if (region == null) return;
        short command = configReadWord(device, PCI_COMMAND);
        if (region.getRegionNumber() == PCIDevice.PCI_ROM_SLOT)
            command |= 0x2;
        else if (region instanceof IOPortIORegion)
            command |= 0x1;
        else
            command |= 0x2;
        configWriteWord(device, PCI_COMMAND, (short)command);
    }

    public void reset()
    {
        isaBridge = null;
        ioportHandler = null;
        memory = null;

        pciIRQIndex = 0;
        devices = new PCIDevice[256];
        pciIRQLevels = new int[4][PCI_IRQ_WORDS];
    }

    public boolean initialised()
    {
        return ((isaBridge != null) && (ioportHandler != null) && (memory != null));
    }

    public void timerCallback() {}

    public void acceptComponent(HardwareComponent component)
    {
        if (component instanceof PCIISABridge)
            isaBridge = (PCIISABridge)component;
        if ((component instanceof IOPortHandler)
            && component.initialised())
            ioportHandler = (IOPortHandler)component;       
        if ((component instanceof PhysicalAddressSpace)
            && component.initialised()) {
            memory = (PhysicalAddressSpace)component;
        }
        if (component instanceof VGACard)
            updateMappings((VGACard) component);
    }

    public boolean updated()
    {
        return updated;
    }

    public void updateComponent(HardwareComponent component)
    {
        if ((component instanceof VGACard) && component.updated())
        {
            updateMappings((VGACard) component);
            updated = true;
        }
    }
}

