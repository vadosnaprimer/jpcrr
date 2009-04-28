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

import org.jpc.emulator.*;
import java.io.*;
    
public abstract class AbstractPCIDevice extends AbstractHardwareComponent implements PCIDevice
{
    private int deviceNumber;

    private byte[] config;

    private int irq;
    private IRQBouncer irqBouncer;
    private boolean pciRegistered;

    public AbstractPCIDevice()
    {
        pciRegistered = false;
        config = new byte[256];
    }

    public void dumpState(DataOutput output) throws IOException
    {
        output.writeInt(irq);
        output.writeInt(deviceNumber);
        output.writeInt(config.length);
        output.write(config);
    }

    public void loadState(DataInput input) throws IOException
    {
        irq = input.readInt();
        deviceNumber = input.readInt();
        int len = input.readInt();
        config = new byte[len];
        input.readFully(config,0,len);
    }

    //PCI Bus Registering
    public int getCurrentDevFN()
    {
        return deviceNumber;
    }
    public void assignDevFN(int devFN)
    {
        deviceNumber = devFN;
    }

    public boolean autoAssignDevFN()
    {
        return true;
    }

    public void deassignDevFN()
    {
        pciRegistered = false;
        assignDevFN(-1);
    }

    public boolean configWriteByte(int configAddress, byte data) //returns true if device needs remapping
    {
        boolean canWrite;
        switch(0xff & getConfigByte(0xe)) {
        case 0x00:
        case 0x80:
            switch(configAddress) {
            case 0x00:
            case 0x01:
            case 0x02:
            case 0x03:

            case 0x08:
            case 0x09:
            case 0x0a:
            case 0x0b:

            case 0x0e:
                
            case 0x10:
            case 0x11:
            case 0x12:
            case 0x13:
            case 0x14:
            case 0x15:
            case 0x16:
            case 0x17:
            case 0x18:
            case 0x19:
            case 0x1a:
            case 0x1b:
            case 0x1c:
            case 0x1d:
            case 0x1e:
            case 0x1f:
            case 0x20:
            case 0x21:
            case 0x22:
            case 0x23:
            case 0x24:
            case 0x25:
            case 0x26:
            case 0x27:

            case 0x30:
            case 0x31:
            case 0x32:
            case 0x33:

            case 0x3d:
                canWrite = false;
                break;
            default:
                canWrite = true;
                break;
            }
            break;
        default:
        case 0x01:
            switch(configAddress) {
            case 0x00:
            case 0x01:
            case 0x02:
            case 0x03:

            case 0x08:
            case 0x09:
            case 0x0a:
            case 0x0b:

            case 0x0e:

            case 0x38:
            case 0x39:
            case 0x3a:
            case 0x3b:

            case 0x3d:
                canWrite = false;
                break;
            default:
                canWrite = true;
                break;
            }
            break;
        }
        if (canWrite) {
            putConfigByte(configAddress, data);
        }

        if ((configAddress + 1) > PCIBus.PCI_COMMAND && configAddress < (PCIBus.PCI_COMMAND + 2)) {
            /* if the command register is modified, we must modify the mappings */
            return true;
        }
        return false;
    }
    
    public boolean configWriteWord(int configAddress, short data) //returns true if device needs remapping
    {
        int modAddress = configAddress;
        for (int i = 0; i < 2; i++) {
            boolean canWrite;
            switch(0xff & getConfigByte(0xe)) {
            case 0x00:
            case 0x80:
                switch(modAddress) {
                case 0x00:
                case 0x01:
                case 0x02:
                case 0x03:
                    
                case 0x08:
                case 0x09:
                case 0x0a:
                case 0x0b:
                    
                case 0x0e:
                    
                case 0x10:
                case 0x11:
                case 0x12:
                case 0x13:
                case 0x14:
                case 0x15:
                case 0x16:
                case 0x17:
                case 0x18:
                case 0x19:
                case 0x1a:
                case 0x1b:
                case 0x1c:
                case 0x1d:
                case 0x1e:
                case 0x1f:
                case 0x20:
                case 0x21:
                case 0x22:
                case 0x23:
                case 0x24:
                case 0x25:
                case 0x26:
                case 0x27:
                    
                case 0x30:
                case 0x31:
                case 0x32:
                case 0x33:
                    
                case 0x3d:
                    canWrite = false;
                    break;
                default:
                    canWrite = true;
                    break;
                }
                break;
            default:
            case 0x01:
                switch(modAddress) {
                case 0x00:
                case 0x01:
                case 0x02:
                case 0x03:
                    
                case 0x08:
                case 0x09:
                case 0x0a:
                case 0x0b:
                    
                case 0x0e:
                    
                case 0x38:
                case 0x39:
                case 0x3a:
                case 0x3b:
                    
                case 0x3d:
                    canWrite = false;
                    break;
                default:
                    canWrite = true;
                    break;
                }
                break;
            }
            if (canWrite) {
                putConfigByte(modAddress, (byte)data);
            }
            modAddress++;
            data >>>= 8;
        }
        
        if ((modAddress) > PCIBus.PCI_COMMAND && configAddress < (PCIBus.PCI_COMMAND + 2)) {
            // if the command register is modified, we must modify the mappings 
            return true;
        }
        return false;
    }

    public boolean configWriteLong(int configAddress, int data)
    {
        if (((configAddress >= 0x10 && configAddress < (0x10 + 4 *6)) || (configAddress >= 0x30 && configAddress < 0x34))) {
            IORegion r;
            int regionIndex;
            
            if (configAddress >= 0x30) {
                regionIndex = PCI_ROM_SLOT;
            } else {
                regionIndex = (configAddress - 0x10) >>> 2;
            }
            r = getIORegion(regionIndex);
            
            if (r != null) {                
                if (regionIndex == PCI_ROM_SLOT)
                    data &= (~(r.getSize() - 1)) | 1;
                else {
                    data &= ~(r.getSize() - 1);
                    data |= r.getType();
                }
                putConfigInt(configAddress, data);
                return true;
            }
        }

        int modAddress = configAddress;
        for (int i = 0; i < 4; i++) {
            boolean canWrite;
            switch(0xff & getConfigByte(0xe)) {
            case 0x00:
            case 0x80:
                switch(modAddress) {
                case 0x00:
                case 0x01:
                case 0x02:
                case 0x03:
                    
                case 0x08:
                case 0x09:
                case 0x0a:
                case 0x0b:
                    
                case 0x0e:
                    
                case 0x10:
                case 0x11:
                case 0x12:
                case 0x13:
                case 0x14:
                case 0x15:
                case 0x16:
                case 0x17:
                case 0x18:
                case 0x19:
                case 0x1a:
                case 0x1b:
                case 0x1c:
                case 0x1d:
                case 0x1e:
                case 0x1f:
                case 0x20:
                case 0x21:
                case 0x22:
                case 0x23:
                case 0x24:
                case 0x25:
                case 0x26:
                case 0x27:
                    
                case 0x30:
                case 0x31:
                case 0x32:
                case 0x33:
                    
                case 0x3d:
                    canWrite = false;
                    break;
                default:
                    canWrite = true;
                    break;
                }
                break;
            default:
            case 0x01:
                switch(modAddress) {
                case 0x00:
                case 0x01:
                case 0x02:
                case 0x03:
                    
                case 0x08:
                case 0x09:
                case 0x0a:
                case 0x0b:
                    
                case 0x0e:
                    
                case 0x38:
                case 0x39:
                case 0x3a:
                case 0x3b:
                    
                case 0x3d:
                    canWrite = false;
                    break;
                default:
                    canWrite = true;
                    break;
                }
                break;
            }
            if (canWrite) {
                putConfigByte(modAddress, (byte)data);
            }
            modAddress++;
            data = data >>> 8;
        }

        if (modAddress > PCIBus.PCI_COMMAND && configAddress < (PCIBus.PCI_COMMAND + 2)) {
            /* if the command register is modified, we must modify the mappings */
            return true;
        }
        return false;
    }

    public byte configReadByte(int configAddress)
    {
        return getConfigByte(configAddress);
    }

    public short configReadWord(int configAddress)
    {
        return getConfigShort(configAddress);
    }

    public int configReadLong(int configAddress)
    {
        return getConfigInt(configAddress);
    }

    public byte getConfigByte(int address)
    {
        return config[address];
    }

    public short getConfigShort(int address)
    {
        int result = 0xFF & getConfigByte(address+1);
        result <<= 8;
        result |= (0xFF & getConfigByte(address));
        return (short)result;
    }

    public int getConfigInt(int address)
    {
        int result = 0xFFFF & getConfigShort(address+2);
        result <<= 16;
        result |= (0xFFFF & getConfigShort(address));
        return result;
    }

    public long getConfigLong(int address)
    {
        long result = 0xffffffffl & getConfigInt(address+4);
        result <<= 32;
        result |= (0xffffffffl & getConfigInt(address));
        return result;
    }

    public byte[] getConfig(int address, int length)
    {
        byte[] temp = new byte[length];
        System.arraycopy(config, address, temp, 0, length);
        return temp;
    }

    public void putConfigByte(int address, byte data)
    {
        config[address] = data;
    }

    public void putConfigShort(int address, short data)
    {
        putConfigByte(address, (byte)data);
        address++;
        data >>= 8;
        putConfigByte(address, (byte)data);
    }

    public void putConfigInt(int address, int data)
    {
        putConfigByte(address, (byte)data);
        address++;
        data >>= 8;
        putConfigByte(address, (byte)data);
        address++;
        data >>= 8;
        putConfigByte(address, (byte)data);
        address++;
        data >>= 8;
        putConfigByte(address, (byte)data);
    }

    public void putConfigLong(int address, long data)
    {
        putConfigByte(address, (byte)data);
        address++;
        data >>= 8;
        putConfigByte(address, (byte)data);
        address++;
        data >>= 8;
        putConfigByte(address, (byte)data);
        address++;
        data >>= 8;
        putConfigByte(address, (byte)data);
        address++;
        data >>= 8;
        putConfigByte(address, (byte)data);
        address++;
        data >>= 8;
        putConfigByte(address, (byte)data);
        address++;
        data >>= 8;
        putConfigByte(address, (byte)data);
        address++;
        data >>= 8;
        putConfigByte(address, (byte)data);
    }

    public void putConfig(int address, byte[] data)
    {
        System.arraycopy(data, 0, config, address, data.length);
    }

    public void setIRQIndex(int irqIndex)
    {
        irq = irqIndex;
    }

    public int getIRQIndex()
    {
        return irq;
    }

    public void addIRQBouncer(IRQBouncer bouncer)
    {
        irqBouncer = bouncer;
    }

    public IRQBouncer getIRQBouncer()
    {
        return irqBouncer;
    }

    public abstract IORegion[] getIORegions();

    public abstract IORegion getIORegion(int index);

    public boolean initialised()
    {
        return pciRegistered;
    }

    public void reset() 
    {
        pciRegistered = false;
    }

    public void acceptComponent(HardwareComponent component)
    {
        if ((component instanceof PCIBus) && component.initialised() && !pciRegistered) 
            pciRegistered = ((PCIBus)component).registerDevice(this);
    }

    public void updateComponent(org.jpc.emulator.HardwareComponent component) 
    {
        if ((component instanceof PCIBus) && component.updated() && !pciRegistered) 
            pciRegistered = ((PCIBus)component).registerDevice(this);
    }

    public boolean updated() 
    {
        return initialised();
    }
}
