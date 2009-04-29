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
import org.jpc.emulator.memory.*;
import java.io.*;
import org.jpc.support.Magic;


public class BMDMAIORegion implements IOPortIORegion
{
    public static final int BM_STATUS_DMAING = 0x01;
    private static final int BM_STATUS_ERROR = 0x02;
    private static final int BM_STATUS_INT = 0x04;
    private static final int BM_CMD_START = 0x01;
    private static final int BM_CMD_READ = 0x08;
    
    private int baseAddress;
    private long size;
    private BMDMAIORegion next;
    
    private byte command;
    private byte status;
    private int address;
    /* current transfer state */
    private IDEChannel.IDEState ideDevice;
    private int ideDMAFunction;
    
    private Memory physicalMemory;
    private Magic magic;

    public BMDMAIORegion(BMDMAIORegion next)
    {
        magic = new Magic(Magic.BM_DMA_IO_REGION_MAGIC_V1);
        this.baseAddress = -1;
        this.next = next;
    }
    
    public void dumpState(DataOutput output) throws IOException
    {
        magic.dumpState(output);
        output.writeInt(baseAddress);
        output.writeLong(size);
        output.writeByte(command);
        output.writeByte(status);
        output.writeInt(address);
        output.writeInt(ideDMAFunction);
        if (ideDevice != null)
            ideDevice.dumpState(output);
    }

    public void loadState(DataInput input) throws IOException
    {
        magic.loadState(input);
        baseAddress = input.readInt();
        size = input.readLong();
        command = input.readByte();
        status = input.readByte();
        address = input.readInt();
        ideDMAFunction = input.readInt();
        //ideDevice.loadState(input);
    }

    public void acceptComponent(org.jpc.emulator.HardwareComponent component) {}

    public boolean initialised() {return true;}

    public void updateComponent(org.jpc.emulator.HardwareComponent component) {}

    public boolean updated() {return true;}

    public void reset(){}

    public void setAddressSpace(Memory memory)
    {
        physicalMemory = memory;
    }

    public void writeMemory(int address, byte[] buffer, int offset, int length)
    {
        physicalMemory.copyContentsFrom(address, buffer, offset, length);
    }

    public void setIDEDevice(IDEChannel.IDEState device)
    {
        this.ideDevice = device;
    }

    public void setDMAFunction(int function)
    {
        ideDMAFunction = function;
    }

    public void setIRQ()
    {
        status |= BM_STATUS_INT;
    }

    public int getAddress()
    {
        return baseAddress;
    }
    public long getSize()
    {
        return 0x10;
    }
    public int getType()
    {
        return PCI_ADDRESS_SPACE_IO;
    }
    
    public byte getStatus()
    {
        return status;
    }

    public int getRegionNumber()
    {
        return 4;
    }
    
    public void setAddress(int address)
    {
        this.baseAddress = address;
        if (next != null) next.setAddress(address + 8);          
    }
    
    public void ioPortWriteByte(int address, int data)
    {
        if ((address - this.baseAddress) > 7)
            next.ioPortWriteByte(address, data);
        switch(address - this.baseAddress) {
        case 0:
            this.writeCommand(data);
            return;
        case 2:
            this.writeStatus(data);
            return;
        default:
        }
    }
    public void ioPortWriteWord(int address, int data)
    {
        this.ioPortWriteByte(address, 0xff & data);
        this.ioPortWriteByte(address + 1, 0xff & (data >>> 8));
    }
    public void ioPortWriteLong(int address, int data)
    {
        if ((address - this.baseAddress) > 7)
            next.ioPortWriteLong(address, data);
        switch(address - this.baseAddress) {
        case 4:
        case 5:
        case 6:
        case 7:
            this.writeAddress(data);
            return;
        default:
            this.ioPortWriteWord(address, 0xffff & data);
            this.ioPortWriteWord(address + 2, data >>> 16);    
        }
    }
    
    public int ioPortReadByte(int address)
    {
        if ((address - this.baseAddress) > 7)
            return next.ioPortReadByte(address);
        switch(address - this.baseAddress) {
        case 0:
            return this.command;
        case 2:
            return this.status;
        default:
            return 0xff;
        }
    }
    public int ioPortReadWord(int address)
    {
        return (ioPortReadByte(address) & 0xff)
            | (0xff00 & (ioPortReadByte(address + 1) << 8));
    }
    public int ioPortReadLong(int address)
    {
        if ((address - this.baseAddress) > 7)
            return next.ioPortReadLong(address);
        switch (address - this.baseAddress) {
        case 4:
        case 5:
        case 6:
        case 7:
            return this.address;
        default:
            return (ioPortReadWord(address) & 0xffff)
                | (0xffff0000 & (ioPortReadWord(address + 1) << 8));
        }
    }
    
    public int[] ioPortsRequested()
    {
        return new int[]{baseAddress, baseAddress + 2,
                         baseAddress + 4, baseAddress + 5,
                         baseAddress + 6, baseAddress + 7,
                         baseAddress + 8, baseAddress + 10,
                         baseAddress + 12, baseAddress + 13,
                         baseAddress + 14, baseAddress + 15};
    }
    
    private void writeCommand(int data)
    {
        if ((data & BM_CMD_START) == 0) {
            /* XXX: do it better */
            this.status &= ~BM_STATUS_DMAING;
            this.command = (byte)(data & 0x09);
        } else {
            this.status |= BM_STATUS_DMAING;
            this.command = (byte)(data & 0x09);
            /* start dma transfer if possible */
            if (this.ideDMAFunction != IDEChannel.IDEState.IDF_NONE)
                this.ideDMALoop();
        }
    }
    
    private void writeStatus(int data)
    {
        status = (byte)((data & 0x60) | (status & 1) | (status & ~data & 0x06));
    }
    
    private void writeAddress(int data)
    {
        this.address = data & ~3;
    }
    
    public void ideDMALoop()
    {
        int currentAddress = this.address;
        /* at most one page to avoid hanging if erroneous parameters */
        for (int i = 0 ; i < 512; i++) {
            int prdAddress = physicalMemory.getDoubleWord(currentAddress);
            int prdSize = physicalMemory.getDoubleWord(currentAddress + 4);
            int length = prdSize & 0xfffe;
            if (length == 0)
                length = 0x10000;
            while (length > 0) {
                int lengthOne = this.ideDevice.dmaCallback(ideDMAFunction, prdAddress, length);
                if (lengthOne == 0) {
                    /* end of transfer */
                    this.status &= ~BM_STATUS_DMAING;
                    this.status |= BM_STATUS_INT;
                    this.ideDMAFunction = IDEChannel.IDEState.IDF_NONE;
                    this.ideDevice = null;
                    return;
                }
                prdAddress += lengthOne;
                length -= lengthOne;
            }
            /* end of transfer */
            if ((prdSize & 0x80000000) != 0)
                break;
            currentAddress += 8;
        }
        /* end of transfer */
        this.status &= ~BM_STATUS_DMAING;
        this.status |= BM_STATUS_INT;
        this.ideDMAFunction = IDEChannel.IDEState.IDF_NONE;
        this.ideDevice = null;
    }

    public void timerCallback() {}
}
