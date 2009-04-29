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

package org.jpc.emulator.motherboard;

import org.jpc.emulator.processor.*;
import org.jpc.emulator.HardwareComponent;
import java.io.*;
import org.jpc.support.Magic;

/**
 * i8259 Programmable Interrupt Controller emulation.
 */
public class InterruptController implements IOPortCapable, HardwareComponent
{
    private InterruptControllerElement master;
    private InterruptControllerElement slave;

    private Processor connectedCPU;
    private Magic magic;


    public InterruptController()
    {
        magic = new Magic(Magic.INTERRUPT_CONTROLLER_MAGIC_V1);
        ioportRegistered = false;
        master = new InterruptControllerElement(true);
        slave = new InterruptControllerElement(false);
    }

    public void dumpState(DataOutput output) throws IOException
    {
        magic.dumpState(output);
        master.dumpState(output);
        slave.dumpState(output);
    }

    public void loadState(DataInput input) throws IOException
    {
        magic.loadState(input);
        ioportRegistered = false;
        master.loadState(input);
        slave.loadState(input);
    }

    private void updateIRQ()
    {
        int slaveIRQ, masterIRQ;
        /* first look at slave irq */
        slaveIRQ = slave.getIRQ();
        if (slaveIRQ >= 0) {
            /* if irq request by slave pic, signal Master PIC */
            master.setIRQ(2,1);
            master.setIRQ(2,0);
        }
        /* look at requested IRQ */
        masterIRQ = master.getIRQ();
        if(masterIRQ >= 0) {
            connectedCPU.raiseInterrupt();
        }
    }

    public void setIRQ(int irqNumber, int level)
    {
        switch (irqNumber >>> 3) {
        case 0: //master
            master.setIRQ(irqNumber & 7, level);
            this.updateIRQ();
            break;
        case 1: //slave
            slave.setIRQ(irqNumber & 7, level);
            this.updateIRQ();
            break;
        default:
        }
    }

    public int cpuGetInterrupt()
    {
        int masterIRQ, slaveIRQ;

        /* read the irq from the PIC */

        masterIRQ = master.getIRQ();
        if (masterIRQ >= 0) {
            master.intAck(masterIRQ);
            if (masterIRQ == 2) {
                slaveIRQ = slave.getIRQ();
                if (slaveIRQ >= 0) {
                    slave.intAck(slaveIRQ);
                } else {
                    /* spurious IRQ on slave controller */
                    slaveIRQ = 7;
                }
                this.updateIRQ();
                return slave.irqBase + slaveIRQ;
                //masterIRQ = slaveIRQ + 8;
            } else {
                this.updateIRQ();
                return master.irqBase + masterIRQ;
            }
        } else {
            /* spurious IRQ on host controller */
            masterIRQ = 7;
            this.updateIRQ();
            return master.irqBase + masterIRQ;
        }
    }

    private int intAckRead()
    {
        int ret = master.pollRead(0x00);
        if (ret == 2)
            ret = slave.pollRead(0x80) + 8;
        master.readRegisterSelect = true;

        return ret;
    }

    private class InterruptControllerElement
    {
        private byte lastInterruptRequestRegister; //edge detection
        private byte interruptRequestRegister;
        private byte interruptMaskRegister;
        private byte interruptServiceRegister;

        private int priorityAdd; // highest IRQ priority
        private int irqBase;
        private boolean readRegisterSelect;
        private boolean poll;
        private boolean specialMask;
        private int initState;
        private boolean fourByteInit;
        private byte elcr; //(elcr) PIIX3 edge/level trigger selection
        private byte elcrMask;

        private boolean specialFullyNestedMode;

        private boolean autoEOI;
        private boolean rotateOnAutoEOI;

        private int[] ioPorts;
        private Magic magic2;

        public InterruptControllerElement(boolean master)
        {
            magic2 = new Magic(Magic.INTERRUPT_CONTROLLER_ELEMENT_MAGIC_V1);
            if (master == true) {
                ioPorts = new int[]{0x20, 0x21, 0x4d0};
                elcrMask = (byte)0xf8;
            } else {
                ioPorts = new int[]{0xa0, 0xa1, 0x4d1};
                elcrMask = (byte)0xde;
            }
        }

        public void dumpState(DataOutput output) throws IOException
        {
            magic2.dumpState(output);
            output.writeByte(lastInterruptRequestRegister);
            output.writeByte(interruptRequestRegister);
            output.writeByte(interruptMaskRegister);
            output.writeByte(interruptServiceRegister);
            output.writeInt(priorityAdd);
            output.writeInt(irqBase);
            output.writeBoolean(readRegisterSelect);
            output.writeBoolean(poll);
            output.writeBoolean(specialMask);
            output.writeInt(initState);
            output.writeBoolean(autoEOI);
            output.writeBoolean(rotateOnAutoEOI);
            output.writeBoolean(specialFullyNestedMode);
            output.writeBoolean(fourByteInit);
            output.writeByte(elcr);
            output.writeByte(elcrMask);
            output.writeInt(ioPorts.length);
            for (int i=0; i< ioPorts.length; i++)
                output.writeInt(ioPorts[i]);
        }

        public void loadState(DataInput input) throws IOException
        {
            magic2.loadState(input);
            lastInterruptRequestRegister = input.readByte();
            interruptRequestRegister = input.readByte();
            interruptMaskRegister = input.readByte();
            interruptServiceRegister = input.readByte();
            priorityAdd = input.readInt();
            irqBase = input.readInt();
            readRegisterSelect = input.readBoolean();
            poll = input.readBoolean();
            specialMask = input.readBoolean();
            initState = input.readInt();
            autoEOI = input.readBoolean();
            rotateOnAutoEOI = input.readBoolean();
            specialFullyNestedMode = input.readBoolean();
            fourByteInit = input.readBoolean();
            elcr = input.readByte();
            elcrMask = input.readByte();
            int len = input.readInt();
            ioPorts = new int[len];
            for (int i=0; i< len; i++)
                ioPorts[i] = input.readInt();
        }

        /* BEGIN IOPortCapable Methods */
        public int[] ioPortsRequested()
        {
            return ioPorts;
        }

        public byte ioPortRead(int address)
        {
            if(poll) {
                poll = false;
                return (byte)this.pollRead(address);
            }
            
            if ((address & 1) == 0) {
                if (readRegisterSelect) {
                    return interruptServiceRegister;
                }

                return interruptRequestRegister;
            }

            return interruptMaskRegister;
        }

        public byte elcrRead()
        {
            return elcr;
        }

        public boolean ioPortWrite(int address, byte data) //t/f updateIRQ
        {
            int priority, command, irq;
            address &= 1;
            if (address == 0) {
                if (0 != (data & 0x10)) 
                {
                    /* init */
                    this.reset();
                    connectedCPU.clearInterrupt();

                    initState = 1;
                    fourByteInit = ((data & 1) != 0);
                    if (0 != (data & 0x02))
                        System.err.println("single mode not supported");
                    if (0 != (data & 0x08))
                        System.err.println("level sensitive irq not supported");
                } 
                else if (0 != (data & 0x08)) 
                {
                    if (0 != (data & 0x04))
                        poll = true;
                    if (0 != (data & 0x02))
                        readRegisterSelect = ((data & 0x01) != 0);
                    if (0 != (data & 0x40))
                        specialMask = (((data >>> 5) & 1) != 0);
                } 
                else 
                {
                    command = data >>> 5;
                    switch(command) {
                    case 0:
                    case 4:
                        rotateOnAutoEOI = ((command >>> 2) != 0);
                        break;
                    case 1: // end of interrupt
                    case 5:
                        priority = this.getPriority(interruptServiceRegister);
                        if (priority != 8) {
                            irq = (priority + priorityAdd) & 7;
                            interruptServiceRegister = (byte)(interruptServiceRegister & ~(1 << irq));
                            if (command == 5)
                                priorityAdd = (irq + 1) & 7;
                            return true;
                        }
                        break;
                    case 3:
                        irq = data & 7;
                        interruptServiceRegister = (byte)(interruptServiceRegister & ~(1 << irq));
                        return true;
                    case 6:
                        priorityAdd = (data + 1) & 7;
                        return true;
                    case 7:
                        irq = data & 7;
                        interruptServiceRegister = (byte)(interruptServiceRegister & ~(1 << irq));
                        priorityAdd = (irq + 1) & 7;
                        return true;
                    default:
                        /* no operation */
                        break;
                    }
                }
            } 
            else 
            {
                switch(initState) 
                {
                case 0:
                    /* normal mode */
                    interruptMaskRegister = data;
                    return true;
                case 1:
                    irqBase = data & 0xf8;
                    initState = 2;
                    break;
                case 2:
                    if (fourByteInit) {
                        initState = 3;
                    } else {
                        initState = 0;
                    }
                    break;
                case 3:
                    specialFullyNestedMode = (((data >>> 4) & 1) != 0);
                    autoEOI = (((data >>> 1) & 1) != 0);
                    initState = 0;
                    break;
                }
            }
            return false;
        }

        public void elcrWrite(byte data)
        {
            elcr = (byte)(data & elcrMask);
        }
        /* END IOPortCapable Methods */

        private int pollRead(int address)
        {
            int ret = this.getIRQ();
            if (ret < 0) {
                InterruptController.this.updateIRQ();
                return 0x07;
            }
            
            if (0 != (address >>> 7)) {
                InterruptController.this.masterPollCode();
            }
            interruptRequestRegister = (byte)(interruptRequestRegister & ~(1 << ret));
            interruptServiceRegister = (byte)(interruptServiceRegister & ~(1 << ret));
            if (0 != (address >>> 7) || ret != 2)
                InterruptController.this.updateIRQ();
            return ret;
        }

        public void setIRQ(int irqNumber, int level)
        {

            int mask;
            mask = (1 << irqNumber);
            if(0 != (elcr & mask)) {
                /* level triggered */
                if (0 != level) {
                    interruptRequestRegister = (byte)(interruptRequestRegister | mask);
                    lastInterruptRequestRegister = (byte)(lastInterruptRequestRegister | mask);
                } else {
                    interruptRequestRegister = (byte)(interruptRequestRegister & ~mask);
                    lastInterruptRequestRegister = (byte)(lastInterruptRequestRegister & ~mask);
                }
            } else {
                /* edge triggered */
                if (0 != level) {
                    if ((lastInterruptRequestRegister & mask) == 0) {
                        interruptRequestRegister = (byte)(interruptRequestRegister | mask);
                    }
                    lastInterruptRequestRegister = (byte)(lastInterruptRequestRegister | mask);
                } else {
                    lastInterruptRequestRegister = (byte)(lastInterruptRequestRegister & ~mask);
                }
            }
        }

        private int getPriority(int mask)
        {
            if ((0xff & mask) == 0) {
                return 8;
            }
            int priority = 0;
            while ((mask & (1 << ((priority + priorityAdd) & 7))) == 0) {
                priority++;
            }
            return priority;
        }

        public int getIRQ()
        {
            int mask, currentPriority, priority;
            
            mask = interruptRequestRegister & ~interruptMaskRegister;
            priority = this.getPriority(mask);
            if (priority == 8) {
                return -1;
            }
            /* compute current priority. If special fully nested mode on
               the master, the IRQ coming from the slave is not taken into
               account for the priority computation. */
            mask = interruptServiceRegister;
            if (specialFullyNestedMode && this.isMaster()) {
                mask &= ~(1 << 2);
            }
            currentPriority = this.getPriority(mask);

            if (priority < currentPriority) {
                /* higher priority found: an irq should be generated */
                return (priority + priorityAdd) & 7;
            } else {
                return -1;
            }
        }

        private void intAck(int irqNumber)
        {
            if (autoEOI) {
                if (rotateOnAutoEOI)
                    priorityAdd = (irqNumber + 1) & 7;
            } else {
                interruptServiceRegister = (byte)(interruptServiceRegister | (1 << irqNumber));
            }
            /* We don't clear a level sensitive interrupt here */
            if (0 == (elcr & (1 << irqNumber)))
                interruptRequestRegister = (byte)(interruptRequestRegister & ~(1 << irqNumber));
        }

        private boolean isMaster()
        {
            if (InterruptController.this.master == this)
                return true;
            else
                return false;
        }
        private void reset()
        {
            //zero all variables except elcrMask
            lastInterruptRequestRegister = (byte)0x0;
            interruptRequestRegister = (byte)0x0;
            interruptMaskRegister = (byte)0x0;
            interruptServiceRegister = (byte)0x0;
            
            priorityAdd = 0;
            irqBase = 0x0;
            readRegisterSelect = false;
            poll = false;
            specialMask = false;
            autoEOI = false;
            rotateOnAutoEOI = false;

            specialFullyNestedMode = false;

            initState = 0;
            fourByteInit = false;

            elcr = (byte)0x0; //(elcr) PIIX3 edge/level trigger selection
        }

        public String toString()
        {
            if (isMaster()) {
                return (InterruptController.this).toString() + ": [Master Element]";
            } else {
                return (InterruptController.this).toString() + ": [Slave  Element]";
            }
        }
    }


    /* BEGIN IOPortCapable Defined Methods */
    public int[] ioPortsRequested()
    {
        int[] masterIOPorts = master.ioPortsRequested();
        int[] slaveIOPorts = slave.ioPortsRequested();

        int[] temp = new int[masterIOPorts.length + slaveIOPorts.length];
        System.arraycopy(masterIOPorts, 0, temp, 0, masterIOPorts.length);
        System.arraycopy(slaveIOPorts, 0, temp, masterIOPorts.length, slaveIOPorts.length);

        return temp;
    }

    public int ioPortReadByte(int address)
    {
        switch (address) {
        case 0x20:
        case 0x21:
            return 0xff & master.ioPortRead(address);
        case 0xa0:
        case 0xa1:
            return 0xff & slave.ioPortRead(address);
        case 0x4d0:
            return 0xff & master.elcrRead();
        case 0x4d1:
            return 0xff & slave.elcrRead();
        default:
        }
        return 0;
    }
    public int ioPortReadWord(int address)
    {
        return (0xff & ioPortReadByte(address)) |
            (0xff00 & (ioPortReadByte(address + 1) << 8));
    }
    public int ioPortReadLong(int address)
    {
        return (0xffff & ioPortReadWord(address)) |
            (0xffff0000 & (ioPortReadWord(address + 2) << 16));
    }

    public void ioPortWriteByte(int address, int data)
    {
        switch (address) {
        case 0x20:
        case 0x21:
            if (master.ioPortWrite(address, (byte)data))
                this.updateIRQ();
            break;
        case 0xa0:
        case 0xa1:
            if (slave.ioPortWrite(address, (byte)data))
                this.updateIRQ();
            break;
        case 0x4d0:
            master.elcrWrite((byte)data);
            break;
        case 0x4d1:
            slave.elcrWrite((byte)data);
            break;
        default:
        }
    }
    public void ioPortWriteWord(int address, int data)
    {
        this.ioPortWriteByte(address, data);
        this.ioPortWriteByte(address + 1, data >>> 8);
    }
    public void ioPortWriteLong(int address, int data)
    {
        this.ioPortWriteWord(address, data);
        this.ioPortWriteWord(address + 2, data >>> 16);
    }

    /* END IOPortCapable Defined Methods */

    private void masterPollCode()
    {
        master.interruptServiceRegister = (byte)(master.interruptServiceRegister & ~(1 << 2));
        master.interruptRequestRegister = (byte)(master.interruptRequestRegister & ~(1 << 2));
    }

    private boolean ioportRegistered;

    public void reset()
    {
        master.reset();
        slave.reset();

        ioportRegistered = false;
        connectedCPU = null;
    }
    public boolean initialised()
    {
        return ((connectedCPU != null) && ioportRegistered);
    }
     
   public boolean updated()
    {
        return (ioportRegistered);
    }

    public void updateComponent(HardwareComponent component)
    {
        if (component instanceof IOPortHandler) 
        {
            ((IOPortHandler)component).registerIOPortCapable(this);
            ioportRegistered = true;
        }
    }

    public void acceptComponent(HardwareComponent component)
    {
        if (component instanceof Processor)
            connectedCPU = (Processor)component;
        if ((component instanceof IOPortHandler)
            && component.initialised()) {
            ((IOPortHandler)component).registerIOPortCapable(this);
            ioportRegistered = true;
        }
    }

    public void timerCallback() {}

    public String toString()
    {
        return "Intel i8259 Programmable Interrupt Controller";
    }
}

