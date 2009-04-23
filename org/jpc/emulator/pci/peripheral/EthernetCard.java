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
import org.jpc.support.EthernetOutput;
import org.jpc.emulator.motherboard.IOPortHandler;
import java.io.*;

public class EthernetCard extends AbstractPCIDevice
{
    //Static Device Constants
    private static final int MAX_ETH_FRAME_SIZE = 1514;

    private static final int E8390_CMD = 0x00; // The command register (for all pages) */
    /* Page 0 register offsets. */
    private static final int EN0_CLDALO	= 0x01; // Low byte of current local dma addr  RD */
    private static final int EN0_STARTPG= 0x01; // Starting page of ring bfr WR */
    private static final int EN0_CLDAHI	= 0x02; // High byte of current local dma addr  RD */
    private static final int EN0_STOPPG	= 0x02; // Ending page +1 of ring bfr WR */
    private static final int EN0_BOUNDARY = 0x03; // Boundary page of ring bfr RD WR */
    private static final int EN0_TSR = 0x04; // Transmit status reg RD */
    private static final int EN0_TPSR = 0x04; // Transmit starting page WR */
    private static final int EN0_NCR = 0x05; // Number of collision reg RD */
    private static final int EN0_TCNTLO = 0x05; // Low  byte of tx byte count WR */
    private static final int EN0_FIFO = 0x06; // FIFO RD */
    private static final int EN0_TCNTHI	= 0x06; // High byte of tx byte count WR */
    private static final int EN0_ISR = 0x07; // Interrupt status reg RD WR */
    private static final int EN0_CRDALO = 0x08; // low byte of current remote dma address RD */
    private static final int EN0_RSARLO = 0x08; // Remote start address reg 0 */
    private static final int EN0_CRDAHI = 0x09; // high byte, current remote dma address RD */
    private static final int EN0_RSARHI = 0x09; // Remote start address reg 1 */
    private static final int EN0_RCNTLO = 0x0a; // Remote byte count reg WR */
    private static final int EN0_RCNTHI = 0x0b; // Remote byte count reg WR */
    private static final int EN0_RSR = 0x0c; // rx status reg RD */
    private static final int EN0_RXCR = 0x0c; // RX configuration reg WR */
    private static final int EN0_TXCR = 0x0d; // TX configuration reg WR */
    private static final int EN0_COUNTER0 = 0x0d; // Rcv alignment error counter RD */
    private static final int EN0_DCFG = 0x0e; // Data configuration reg WR */
    private static final int EN0_COUNTER1 = 0x0e; // Rcv CRC error counter RD */
    private static final int EN0_IMR = 0x0f; // Interrupt mask reg WR */
    private static final int EN0_COUNTER2 = 0x0f; // Rcv missed frame error counter RD */
    
    private static final int EN1_PHYS = 0x11;
    private static final int EN1_CURPAG = 0x17;
    private static final int EN1_MULT = 0x18;
	
    /*  Register accessed at EN_CMD, the 8390 base addr.  */
    private static final byte E8390_STOP = (byte)0x01; // Stop and reset the chip */
    private static final byte E8390_START = (byte)0x02; // Start the chip, clear reset */
    private static final byte E8390_TRANS = (byte)0x04; // Transmit a frame */
    private static final byte E8390_RREAD = (byte)0x08; // Remote read */
    private static final byte E8390_RWRITE = (byte)0x10; // Remote write  */
    private static final byte E8390_NODMA = (byte)0x20; // Remote DMA */
    private static final byte E8390_PAGE0 = (byte)0x00; // Select page chip registers */
    private static final byte E8390_PAGE1 = (byte)0x40; // using the two high-order bits */
    private static final byte E8390_PAGE2 = (byte)0x80; // Page 3 is invalid. */
    
    /* Bits in EN0_ISR - Interrupt status register */
    private static final byte ENISR_RX = (byte)0x01; // Receiver, no error */
    private static final byte ENISR_TX = (byte)0x02; // Transmitter, no error */
    private static final byte ENISR_RX_ERR = (byte)0x04; // Receiver, with error */
    private static final byte ENISR_TX_ERR = (byte)0x08; // Transmitter, with error */
    private static final byte ENISR_OVER = (byte)0x10; // Receiver overwrote the ring */
    private static final byte ENISR_COUNTERS = (byte)0x20; // Counters need emptying */
    private static final byte ENISR_RDC = (byte)0x40; // remote dma complete */
    private static final byte ENISR_RESET = (byte)0x80; // Reset completed */
    private static final byte ENISR_ALL = (byte)0x3f; // Interrupts we will enable */
    
    /* Bits in received packet status byte and EN0_RSR*/
    private static final byte ENRSR_RXOK= (byte)0x01; // Received a good packet */
    private static final byte ENRSR_CRC = (byte)0x02; // CRC error */
    private static final byte ENRSR_FAE = (byte)0x04; // frame alignment error */
    private static final byte ENRSR_FO = (byte)0x08; // FIFO overrun */
    private static final byte ENRSR_MPA = (byte)0x10; // missed pkt */
    private static final byte ENRSR_PHY = (byte)0x20; // physical/multicast address */
    private static final byte ENRSR_DIS = (byte)0x40; // receiver disable. set in monitor mode */
    private static final byte ENRSR_DEF = (byte)0x80; // deferring */
    
    /* Transmitted packet status, EN0_TSR. */
    private static final byte ENTSR_PTX = (byte)0x01; // Packet transmitted without error */
    private static final byte ENTSR_ND = (byte)0x02; // The transmit wasn't deferred. */
    private static final byte ENTSR_COL = (byte)0x04; // The transmit collided at least once. */
    private static final byte ENTSR_ABT = (byte)0x08; // The transmit collided 16 times, and was deferred. */
    private static final byte ENTSR_CRS = (byte)0x10; // The carrier sense was lost. */
    private static final byte ENTSR_FU = (byte)0x20; // A "FIFO underrun" occurred during transmit. */
    private static final byte ENTSR_CDH = (byte)0x40; // The collision detect "heartbeat" signal was lost. */
    private static final byte ENTSR_OWC = (byte)0x80; // There was an out-of-window collision. */

    private static final int NE2000_PMEM_SIZE = (32*1024);
    private static final int NE2000_PMEM_START = (16*1024);
    private static final int NE2000_PMEM_END = (NE2000_PMEM_SIZE+NE2000_PMEM_START);
    private static final int NE2000_MEM_SIZE = NE2000_PMEM_END;



    private static boolean DEBUG = false;

    //Instance (State) Properties
    private byte command;
    private int start;
    private int stop;
    private byte boundary;
    private byte tsr;
    private byte tpsr;
    private short tcnt;
    private short rcnt;
    private int rsar;
    private byte rsr;
    private byte isr;
    private byte dcfg;
    private byte imr;
    private byte phys[]; /* mac address */
    private byte curpag;
    private byte mult[]; /* multicast mask array */
    private int irq;

    EthernetOutput outputDevice;

    private ByteBuffer mem;
    private EthernetIORegion ioRegion;

    public EthernetCard()
    {
	this(null);
    }

    public EthernetCard(EthernetOutput output)
    {
	setIRQIndex(16);

	putConfigByte(0x00, (byte)0xec); // Realtek 8029
	putConfigByte(0x01, (byte)0x10);
	putConfigByte(0x02, (byte)0x29);
	putConfigByte(0x03, (byte)0x80);
	putConfigByte(0x0a, (byte)0x00); // ethernet network controller
	putConfigByte(0x0b, (byte)0x02);
	putConfigByte(0x0e, (byte)0x00); // header_type
	putConfigByte(0x3d, (byte)0x01); // interrupt pin 0

	ioRegion = new EthernetIORegion();
	outputDevice = output;
	mem = new ByteBuffer(NE2000_MEM_SIZE);
	phys = new byte[6];
	mult = new byte[8];

	this.internalReset(); //dodgy ref to this?
    }

    public void dumpState(DataOutput output) throws IOException
    {
        output.writeByte(command);
        output.writeInt(start);
        output.writeInt(stop);
        output.writeByte(boundary);
        output.writeByte(tsr);
        output.writeByte(tpsr);
        output.writeShort(tcnt);
        output.writeShort(rcnt);
        output.writeInt(rsar);
        output.writeByte(rsr);
        output.writeByte(isr);
        output.writeByte(dcfg);
        output.writeByte(imr);
        output.writeInt(phys.length);
        output.write(phys);
        output.writeByte(curpag);
        output.writeInt(mult.length);
        output.write(mult);
        output.writeInt(irq);
        byte[] temp = new byte[mem.size()];
        output.writeInt(mem.size());
        mem.get(0, temp, 0, temp.length);
        output.write(temp);
        ioRegion.dumpState(output);
        //dump output device
        //apparently this is another whole kettle of fish... so let's ignore it
    }

    public void loadState(DataInput input) throws IOException
    {
        command = input.readByte();
        start = input.readInt();
        stop = input.readInt();
        boundary = input.readByte();
        tsr = input.readByte();
        tpsr = input.readByte();
        tcnt = input.readShort();
        rcnt = input.readShort();
        rsar = input.readInt();
        rsr = input.readByte();
        isr = input.readByte();
        dcfg = input.readByte();
        imr = input.readByte();
        int len = input.readInt();
        phys = new byte[len];
        input.readFully(phys,0,len);
        curpag = input.readByte();
        len = input.readInt();
        mult = new byte[len];
        input.readFully(mult,0,len);
        irq = input.readInt();
        len = input.readInt();
        mem = new ByteBuffer(len);
        byte[] buffer = new byte[len];
        input.readFully(buffer,0,len);
        mem.set(buffer,0,0,buffer.length);
        ioRegion.loadState(input);       
        //load output device
        //apparently this is another whole kettle of fish... so let's ignore it
    }

    public void loadIOPorts(IOPortHandler ioportHandler, DataInput input) throws IOException
    {
        loadState(input);
        ioportHandler.registerIOPortCapable(ioRegion);
    }

    public void reset()
    {
	putConfigByte(0x00, (byte)0xec); // Realtek 8029
	putConfigByte(0x01, (byte)0x10);
	putConfigByte(0x02, (byte)0x29);
	putConfigByte(0x03, (byte)0x80);
	putConfigByte(0x0a, (byte)0x00); // ethernet network controller
	putConfigByte(0x0b, (byte)0x02);
	putConfigByte(0x0e, (byte)0x00); // header_type
	putConfigByte(0x3d, (byte)0x01); // interrupt pin 0

	mem = new ByteBuffer(NE2000_MEM_SIZE);
	phys = new byte[6];
	mult = new byte[8];

	internalReset();

	super.reset();
    }

    private void internalReset()
    {
	this.setISR(ENISR_RESET);
	mem.set(0x0e, (byte)0x57);
	mem.set(0x0f, (byte)0x57);

	for (int i = 15; i >=0; i--) 
        {
	    mem.set(2*i, mem.get(i));
	    mem.set(2*i+1, mem.get(i));
	}
    }

    private void updateIRQ()
    {
	int isr = this.getISR() & 
	    this.getIMR();
	if (isr != 0)
	    this.getIRQBouncer().setIRQ(this, 0, 1);
	else
	    this.getIRQBouncer().setIRQ(this, 0, 0);
    }

    private int canReceive(){return 0;}

    //PCIDevice Methods
    //IOPort Registration Aids
    public IORegion[] getIORegions()
    {
	return new IORegion[]{ioRegion};
    }
    public IORegion getIORegion(int index)
    {
	if (index == 0)
	    return ioRegion;
	else
	    return null;
    }

    class EthernetIORegion implements IOPortIORegion
    {
	private int address;
	
	public EthernetIORegion()
	{
	    address = -1;
	}

        public void dumpState(DataOutput output) throws IOException
        {
            output.writeInt(address);
        }

        public void loadState(DataInput input) throws IOException
        {
            address = input.readInt();
        }

        public void acceptComponent(org.jpc.emulator.HardwareComponent component) {}

        public boolean initialised() {return true;}

        public void updateComponent(org.jpc.emulator.HardwareComponent component) {}

        public boolean updated() {return true;}

        public void reset(){}

	//IORegion Methods
	public int getAddress()
	{
	    return address;
	}
	public long getSize()
	{
	    return 0x100;
	}
	public int getType()
	{
	    return PCI_ADDRESS_SPACE_IO;
	}
	public int getRegionNumber()
	{
	    return 0;
	}
	public void setAddress(int address)
	{
	    this.address = address;
	}


	//IOPortCapable Methods
	public void ioPortWriteByte(int address, int data)
	{
	    switch(address - this.getAddress()) {
	    case 0x00:
	    case 0x01:
	    case 0x02:
	    case 0x03:
	    case 0x04:
	    case 0x05:
	    case 0x06:
	    case 0x07:
	    case 0x08:
	    case 0x09:
	    case 0x0a:
	    case 0x0b:
	    case 0x0c:
	    case 0x0d:
	    case 0x0e:
	    case 0x0f:
		EthernetCard.this.ioPortWrite(address, (byte)data);
		break;
	    case 0x10:
		// May do a 16 bit write, so must only narrow to short
		EthernetCard.this.asicIOPortWriteByte(address, (short)data);
		break;
	    case 0x1f:
		//this.resetIOPortWrite(address); //end of reset pulse
		break;
	    default:
		break;
	    }
	}

	public void ioPortWriteWord(int address, int data)
	{
	    switch(address - this.getAddress()) {
	    case 0x10:
	    case 0x11:
		EthernetCard.this.asicIOPortWriteWord(address, (short)data);
		break;
	    default:
		// should do two byte access
		break;
	    }
	}
	public void ioPortWriteLong(int address, int data)
	{
	    switch(address - this.getAddress()) {
	    case 0x10:
	    case 0x11:
	    case 0x12:
	    case 0x13:
		EthernetCard.this.asicIOPortWriteLong(address, data);
		break;
	    default:
		break;
	    }
	}

	public int ioPortReadByte(int address)
	{
	    switch(address - this.getAddress()) {
	    case 0x00:
	    case 0x01:
	    case 0x02:
	    case 0x03:
	    case 0x04:
	    case 0x05:
	    case 0x06:
	    case 0x07:
	    case 0x08:
	    case 0x09:
	    case 0x0a:
	    case 0x0b:
	    case 0x0c:
	    case 0x0d:
	    case 0x0e:
	    case 0x0f:
		return EthernetCard.this.ioPortRead(address);
	    case 0x10:
		return 0xffff & EthernetCard.this.asicIOPortReadByte(address);
	    case 0x1f:
		return EthernetCard.this.resetIOPortRead(address);
	    default:
		return (byte)0xff;
	    }
	}
	public int ioPortReadWord(int address)
	{
	    switch(address - this.getAddress()) {
	    case 0x10:
	    case 0x11:
		return EthernetCard.this.asicIOPortReadWord(address);
	    default:
		return (short)0xffff; //should do two byte access
	    }
	}
	public int ioPortReadLong(int address)
	{
	    switch(address - this.getAddress()) {
	    case 0x10:
	    case 0x11:
	    case 0x12:
	    case 0x13:
		return EthernetCard.this.asicIOPortReadLong(address);
	    default:
		return (int)0xffffffff;
	    }
	}
	
	public int[] ioPortsRequested()
	{
	    int addr = this.getAddress();
	    int[] temp = new int[32];
	    for (int i = 0; i < 32; i++)
		temp[i] = addr + i;
	    return temp;
	}

        public void timerCallback() {}
    }
    
    private void ioPortWrite(int address, byte data)
    {
	address &= 0xf;
	if(address == E8390_CMD) {
	    /* control register */
	    this.setCommand(data);
	    if (0 != (data & E8390_START)) {
		this.andISR((byte)~ENISR_RESET);
		/* test specific case: zero length transfer */
		if((0 != (data & (E8390_RREAD | E8390_RWRITE)))
		   && (this.getRCNT() == 0)) { // check operators
		    this.orISR(ENISR_RDC);
		    this.updateIRQ();
		}
		if (0 != (data & E8390_TRANS)) {
		    this.getEthernetOutput().sendPacket(null, 0, 0);
		    /* signal end of transfer */
		    this.setTSR(ENTSR_PTX);
		    this.orISR(ENISR_TX);
		    this.updateIRQ();
		}
	    }
	} else {
	    int page = this.getCommand() >> 6;
	    int offset = address | (page << 4);
	    switch(offset) {
	    case EN0_STARTPG:
		this.setStart(data << 8);
		break;
	    case EN0_STOPPG:
		this.setStop(data << 8);
		break;
	    case EN0_BOUNDARY:
		this.setBoundary(data);
		break;
	    case EN0_IMR:
		this.setIMR(data);
		this.updateIRQ();
		break;
	    case EN0_TPSR:
		this.setTPSR(data);
		break;
	    case EN0_TCNTLO:
		this.setTCNT((short)((this.getTCNT() & 0xff00) | data));
		break;
	    case EN0_TCNTHI:
		this.setTCNT((short)((this.getTCNT() & 0x00ff) | (data << 8)));
		break;
	    case EN0_RSARLO:
		this.setRSAR((this.getRSAR() & 0xff00) | data);
		break;
	    case EN0_RSARHI:
		this.setRSAR((this.getRSAR() & 0x00ff) | (data << 8));
		break;
	    case EN0_RCNTLO:
		this.setRCNT((short)((this.getRCNT() & 0xff00) | data));
		break;
	    case EN0_RCNTHI:
		this.setRCNT((short)((this.getRCNT() & 0x00ff) | (data << 8)));
		break;
	    case EN0_DCFG:
		this.setDCfg(data);
		break;
	    case EN0_ISR:
		this.andISR((byte)~(data & 0x7f));
		this.updateIRQ();
		break;
	    case EN1_PHYS:
	    case EN1_PHYS + 1:
	    case EN1_PHYS + 2:
	    case EN1_PHYS + 3:
	    case EN1_PHYS + 4:
	    case EN1_PHYS + 5:
		this.setPhysical(offset - EN1_PHYS, data);
		break;
	    case EN1_CURPAG:
		this.setCurrentPage(data);
		break;
	    case EN1_MULT:
	    case EN1_MULT + 1:
	    case EN1_MULT + 2:
	    case EN1_MULT + 3:
	    case EN1_MULT + 4:
	    case EN1_MULT + 5:
	    case EN1_MULT + 6:
	    case EN1_MULT + 7:
		this.setMulticast(offset - EN1_MULT, data);
		break;
	    }
	}
    }

    private void asicIOPortWriteByte(int address, short data)
    {
	if (this.getRCNT() == 0)
	    return;
	if (0 != (this.getDCfg() & 0x01)) {
	    /* 16 bit access */
	    this.memoryWriteWord(this.getRSAR(), data);
	    this.dmaUpdate(2);
	} else {
	    /* 8 bit access */
	    this.memoryWriteByte(this.getRSAR(), (byte)data);
	    this.dmaUpdate(1);
	}
    }
    private void asicIOPortWriteWord(int address, short data)
    {
	if (this.getRCNT() == 0)
	    return;
	if (0 != (this.getDCfg() & 0x01)) {
	    /* 16 bit access */
	    this.memoryWriteWord(this.getRSAR(), data);
	    this.dmaUpdate(2);
	} else {
	    /* 8 bit access */
	    this.memoryWriteByte(this.getRSAR(), (byte)data);
	    this.dmaUpdate(1);
	}
    }
    private void asicIOPortWriteLong(int address, int data)
    {
	if (this.getRCNT() == 0)
	    return;
	this.memoryWriteLong(this.getRSAR(), data);
	this.dmaUpdate(4);
    }

    private byte ioPortRead(int address)
    {
	address &= 0xf;
	if (address == E8390_CMD) {
	    return this.getCommand();
	}
	int page = this.getCommand() >> 6;
	int offset = address | (page << 4);
	switch (offset) {
	case EN0_TSR:
	    return this.getTSR();
	case EN0_BOUNDARY:
	    return this.getBoundary();
        case EN0_ISR:
	    return this.getISR();
	case EN0_RSARLO:
	    return (byte)(this.getRSAR() & 0x00ff);
	case EN0_RSARHI:
	    return (byte)(this.getRSAR() >> 8);
        case EN1_PHYS:
	case EN1_PHYS + 1:
	case EN1_PHYS + 2:
	case EN1_PHYS + 3:
	case EN1_PHYS + 4:
	case EN1_PHYS + 5:
	    return this.getPhysical(offset - EN1_PHYS);
        case EN1_CURPAG:
	    return this.getCurrentPage();
        case EN1_MULT:
	case EN1_MULT + 1:
	case EN1_MULT + 2:
	case EN1_MULT + 3:
	case EN1_MULT + 4:
	case EN1_MULT + 5:
	case EN1_MULT + 6:
	case EN1_MULT + 7:
	    return this.getMulticast(offset - EN1_MULT);
        case EN0_RSR:
	    return this.getRSR();
	default:
	    return 0x00;
	}
    }

    private short asicIOPortReadByte(int address)
    {
	short ret;

	if (0 != (this.getDCfg() & 0x01)) {
	    /* 16 bit access */
	    ret = this.memoryReadWord(this.getRSAR());
	    this.dmaUpdate(2);
	} else {
	    /* 8 bit access */
	    ret = (short)this.memoryReadByte(this.getRSAR());
	    ret &= 0xff;
	    this.dmaUpdate(1);
	}
	return ret;
    }
    private short asicIOPortReadWord(int address)
    {
	short ret;
	if (0 != (this.getDCfg() & 0x01)) {
	    /* 16 bit access */
	    ret = this.memoryReadWord(this.getRSAR());
	    this.dmaUpdate(2);
	} else {
	    /* 8 bit access */
	    ret = (short)this.memoryReadByte(this.getRSAR());
	    ret &= 0xff;
	    this.dmaUpdate(1);
	}
	return ret;
    }
    private int asicIOPortReadLong(int address)
    {
	int ret = this.memoryReadLong(this.getRSAR());
	this.dmaUpdate(4);
	return ret;
    }

    private byte resetIOPortRead(int address)
    {
	this.internalReset();
	return 0x00;
    }

    private void dmaUpdate(int length)
    {
	this.setRSAR(this.getRSAR() + length);
	if (this.getRSAR() == this.getStop())
	    this.setRSAR(this.getStart());

	if (this.getRCNT() <= length) {
	    this.setRCNT((short)0);
	    /* signal end of transfer */
	    this.orISR(ENISR_RDC);
	    this.updateIRQ();
	} else {
	    this.setRCNT((short)(this.getRCNT() - length));
	}
    }

    private void memoryWriteByte(int address, byte data)
    {
	if (address < 32 ||
	    (address >= NE2000_PMEM_START && address < NE2000_MEM_SIZE)) {
	    mem.set(address, data);
	}
    }
    private void memoryWriteWord(int address, short data)
    {
	address &= ~1;
	if (address < 32 ||
	    (address >= NE2000_PMEM_START && address < NE2000_MEM_SIZE)) {
	    mem.setShort(address, data);
	}
    }
    private void memoryWriteLong(int address, int data)
    {
	address &= ~1;
	if (address < 32 ||
	    (address >= NE2000_PMEM_START && address < NE2000_MEM_SIZE)) {
	    mem.setInt(address, data);
	}
    }

    private byte memoryReadByte(int address)
    {
	if (address < 32 ||
	    (address >= NE2000_PMEM_START && address < NE2000_MEM_SIZE)) {
	    return mem.get(address);
	} else {
	    return (byte)0xff;
	}
    }
    private short memoryReadWord(int address)
    {
	address &= ~1;
	if (address < 32 ||
	    (address >= NE2000_PMEM_START && address < NE2000_MEM_SIZE)) {
	    return mem.getShort(address);
	} else {
	    return (short)0xffff;
	}
    }
    private int memoryReadLong(int address)
    {
	address &= ~1;
	if (address < 32 ||
	    (address >= NE2000_PMEM_START && address < NE2000_MEM_SIZE)) {
	    return mem.getInt(address);
	} else {
	    return (int)0xffffffff;
	}
    }

    private byte getCommand()
    {
	return command;
    }
    private void setCommand(byte command)
    {
	this.command = command;
    }

    private int getStart()
    {
	return start;
    }
    private void setStart(int start)
    {
	this.start = start;
    }

    private int getStop()
    {
	return stop;
    }
    private void setStop(int stop)
    {
	this.stop = stop;
    }

    private byte getBoundary()
    {
	return boundary;
    }
    private void setBoundary(byte boundary)
    {
	this.boundary = boundary;
    }

    private byte getTSR()
    {
	return tsr;
    }
    private void setTSR(byte tsr)
    {
	this.tsr = tsr;
    }

    private byte getTPSR()
    {
	return tpsr;
    }
    private void setTPSR(byte tpsr)
    {
	this.tpsr = tpsr;
    }

    private short getTCNT()
    {
	return tcnt;
    }
    private void setTCNT(short tcnt)
    {
	this.tcnt = tcnt;
    }

    private short getRCNT()
    {
	return rcnt;
    }
    private void setRCNT(short rcnt)
    {
	this.rcnt = rcnt;
    }

    private int getRSAR()
    {
	return rsar;
    }
    private void setRSAR(int rsar)
    {
	this.rsar = rsar;
    }

    private byte getRSR()
    {
	return rsr;
    }
    private void setRSR(byte rsr)
    {
	this.rsr = rsr;
    }

    private byte getISR()
    {
	return isr;
    }
    private void setISR(byte isr)
    {
	this.isr = isr;
    }
    private void andISR(byte mask)
    {
	this.setISR((byte)(this.getISR() & mask));
    }
    private void orISR(byte mask)
    {
	this.setISR((byte)(this.getISR() | mask));
    }

    private byte getDCfg()
    {
	return dcfg;
    }
    private void setDCfg(byte dcfg)
    {
	this.dcfg = dcfg;
    }

    private byte getIMR()
    {
	return imr;
    }
    private void setIMR(byte imr)
    {
	this.imr = imr;
    }

    private byte getPhysical(int address)
    {
	return phys[address];
    }
    private void setPhysical(int address, byte data)
    {
	phys[address] = data;
    }

    private byte getCurrentPage()
    {
	return curpag;
    }
    private void setCurrentPage(byte currentPage)
    {
	this.curpag = currentPage;
    }

    private byte getMulticast(int address)
    {
	return mult[address];
    }
    private void setMulticast(int address, byte data)
    {
	mult[address] = data;
    }

    private EthernetOutput getEthernetOutput()
    {
	return this.outputDevice;
    }

    public void testPacket()
    {
	this.setIMR((byte)0xff);
	this.orISR(ENISR_RX);
	this.updateIRQ();
    }
}
    
