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

public interface PCIDevice
{
    public static final int PCI_ROM_SLOT = 6;
    public static final int PCI_NUM_REGIONS = 7;

    public static final int PCI_CLASS_DEVICE = 0x0a;
    public static final int PCI_VENDOR_ID = 0x00;
    public static final int PCI_DEVICE_ID = 0x02;
    public static final int PCI_INTERRUPT_LINE = 0x3c;
    public static final int PCI_INTERRUPT_PIN = 0x3d;

    //PCI Bus Registering
    public int getCurrentDevFN();
    public boolean autoAssignDevFN();
    public void assignDevFN(int devFN);
    public void deassignDevFN();

    public boolean configWriteByte(int configAddress, byte data);
    public boolean configWriteWord(int configAddress, short data);
    public boolean configWriteLong(int configAddress, int data);

    public byte configReadByte(int configAddress);
    public short configReadWord(int configAddress);
    public int configReadLong(int configAddress);

    public byte[] getConfig(int address, int length);
    public void putConfig(int address, byte[] data);

    public byte getConfigByte(int address);
    public short getConfigShort(int address);
    public int getConfigInt(int address);
    public long getConfigLong(int address);

    public void putConfigByte(int address, byte data);
    public void putConfigShort(int address, short data);
    public void putConfigInt(int address, int data);
    public void putConfigLong(int address, long data);

    //IOPort Registration Aids
    public IORegion[] getIORegions();
    public IORegion getIORegion(int index);

    //Interrupt Stuff
    public void setIRQIndex(int irqIndex);
    public int getIRQIndex();
    public void addIRQBouncer(IRQBouncer bouncer);
    public IRQBouncer getIRQBouncer();
}
