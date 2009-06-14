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

package org.jpc.emulator.peripheral;

import org.jpc.emulator.motherboard.*;
import org.jpc.support.*;
import org.jpc.emulator.*;
import java.io.*;

public class FloppyController implements IOPortCapable, DMATransferCapable, HardwareComponent {
    /* Will always be a fixed parameter for us */
    private static final int SECTOR_LENGTH = 512;
    private static final int SECTOR_SIZE_CODE = 2; // Sector size code

    /* Floppy disk drive emulation */
    private static final int CONTROL_ACTIVE = 0x01; /* XXX: suppress that */
    private static final int CONTROL_RESET  = 0x02;
    private static final int CONTROL_SLEEP  = 0x04; /* XXX: suppress that */
    private static final int CONTROL_BUSY   = 0x08; /* dma transfer in progress */
    private static final int CONTROL_INTERRUPT   = 0x10;

    private static final int DIRECTION_WRITE   = 0;
    private static final int DIRECTION_READ    = 1;
    private static final int DIRECTION_SCANE   = 2;
    private static final int DIRECTION_SCANL   = 3;
    private static final int DIRECTION_SCANH   = 4;

    private static final int STATE_COMMAND    = 0x00;
    private static final int STATE_STATUS = 0x01;
    private static final int STATE_DATA   = 0x02;
    private static final int STATE_STATE  = 0x03;
    private static final int STATE_MULTI  = 0x10;
    private static final int STATE_SEEK   = 0x20;
    private static final int STATE_FORMAT = 0x40;

    private static final byte CONTROLLER_VERSION = (byte)0x90; /* Intel 82078 Controller */
    private static final int INTERRUPT_LEVEL = 6;
    private static final int DMA_CHANNEL = 2;
    private static final int IOPORT_BASE = 0x3f0;

    private boolean drivesUpdated;

    private Timer resultTimer;
    private Clock clock;

    private int state;
    private boolean dmaEnabled;
    private int currentDrive;
    private int bootSelect;

    /* Command FIFO */
    private byte[] fifo;
    private int dataOffset;
    private int dataLength;
    private int dataState;
    private int dataDirection;
    private int interruptStatus;
    private byte eot; // last wanted sector

    /* State kept only to be returned back */
    /* Timers state */
    private byte timer0;
    private byte timer1;
    /* precompensation */
    private byte preCompensationTrack;
    private byte config;
    private byte lock;
    /* Power down config */
    private byte pwrd;

    /* Drives */
    private FloppyDrive[] drives;

    private InterruptController irqDevice;
    private DMAController dma;

    public FloppyController()
    {
        ioportRegistered = false;
        drives = new FloppyDrive[2];

        config = (byte)0x60; /* Implicit Seek, polling & fifo enabled */
        state = CONTROL_ACTIVE;

        fifo = new byte[SECTOR_LENGTH];
    }

    public void dumpStatusPartial(org.jpc.support.StatusDumper output)
    {
        output.println("\tdrivesUpdated " + drivesUpdated + " state " + state + " dmaEnabled " + dmaEnabled);
        output.println("\tcurrentDrive " + currentDrive + " bootSelect " + bootSelect + " dataOffset " + dataOffset);
        output.println("\tdataLength " + dataLength + " dataState " + dataState + " dataDirection " + dataDirection);
        output.println("\tinterruptStatus " + interruptStatus + " eot " + eot + " timer0 " + timer0);
        output.println("\ttimer1 " + timer1 + " preCompensationTrack " + preCompensationTrack);
        output.println("\tconfig " + config + " lock " + lock + " pwrd " + pwrd);
        output.println("\tresultTimer <object #" + output.objectNumber(resultTimer) + ">"); if(resultTimer != null) resultTimer.dumpStatus(output);
        output.println("\tclock <object #" + output.objectNumber(clock) + ">"); if(clock != null) clock.dumpStatus(output);
        output.println("\tirqDevice <object #" + output.objectNumber(irqDevice) + ">"); if(irqDevice != null) irqDevice.dumpStatus(output);
        output.println("\tdma <object #" + output.objectNumber(dma) + ">"); if(dma != null) dma.dumpStatus(output);
        for (int i=0; i < drives.length; i++) {
            output.println("\tdrives[" + i + "] <object #" + output.objectNumber(drives[i]) + ">"); if(drives[i] != null) drives[i].dumpStatus(output);
        }
        for (int i=0; i < fifo.length; i++) {
            output.println("\tfifo[" + i + "] " + fifo[i]);
        }
    }
 
    public void dumpStatus(org.jpc.support.StatusDumper output)
    {
        if(output.dumped(this))
            return;

        output.println("#" + output.objectNumber(this) + ": FloppyController:");
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
        output.dumpBoolean(drivesUpdated);
        output.dumpObject(resultTimer);
        output.dumpObject(clock);
        output.dumpInt(state);
        output.dumpBoolean(dmaEnabled);
        output.dumpInt(currentDrive);
        output.dumpInt(bootSelect);
        output.dumpArray(fifo);
        output.dumpInt(dataOffset);
        output.dumpInt(dataLength);
        output.dumpInt(dataState);
        output.dumpInt(dataDirection);
        output.dumpInt(interruptStatus);
        output.dumpByte(eot);
        output.dumpByte(timer0);
        output.dumpByte(timer1);
        output.dumpByte(preCompensationTrack);
        output.dumpByte(config);
        output.dumpByte(lock);
        output.dumpByte(pwrd);
        output.dumpInt(drives.length);
        for(int i = 0; i < drives.length; i++)
            output.dumpObject(drives[i]);
        output.dumpObject(irqDevice);
        output.dumpObject(dma);
    }

    public FloppyController(org.jpc.support.SRLoader input) throws IOException
    {
        input.objectCreated(this);
        drivesUpdated = input.loadBoolean();
        resultTimer = (Timer)(input.loadObject());
        clock = (Clock)(input.loadObject());
        state = input.loadInt();
        dmaEnabled = input.loadBoolean();
        currentDrive = input.loadInt();
        bootSelect = input.loadInt();
        fifo = input.loadArrayByte();
        dataOffset = input.loadInt();
        dataLength = input.loadInt();
        dataState = input.loadInt();
        dataDirection = input.loadInt();
        interruptStatus = input.loadInt();
        eot = input.loadByte();
        timer0 = input.loadByte();
        timer1 = input.loadByte();
        preCompensationTrack = input.loadByte();
        config = input.loadByte();
        lock = input.loadByte();
        pwrd = input.loadByte();
        drives = new FloppyDrive[input.loadInt()];
        for(int i = 0; i < drives.length; i++)
            drives[i] = (FloppyDrive)(input.loadObject());
        irqDevice = (InterruptController)(input.loadObject());
        dma = (DMAController)(input.loadObject());
    }

    public static org.jpc.SRDumpable loadSR(org.jpc.support.SRLoader input, Integer id) throws IOException
    {
        org.jpc.SRDumpable x = new FloppyController(input);
        input.endObject();
        return x;
    }

    public void timerCallback()
    {
        stopTransfer((byte)0x00, (byte)0x00, (byte)0x00);
    }

    public int getDriveType(int number)
    {
        return drives[number].drive;
    }

    public int[] ioPortsRequested()
    {
        return new int[]{IOPORT_BASE + 1, IOPORT_BASE + 2, IOPORT_BASE + 3, IOPORT_BASE + 4, IOPORT_BASE + 5, IOPORT_BASE + 7};
    }

    public int ioPortReadByte(int address)
    {
        switch(address & 0x07) {
        case 0x01:
            return readStatusB();
        case 0x02:
            return readDOR();
        case 0x03:
            return readTape();
        case 0x04:
            return readMainStatus();
        case 0x05:
            return readData();
        case 0x07:
            return readDirection();
        default:
            return 0xff;
        }
    }
    public int ioPortReadWord(int address)
    {
        return (ioPortReadByte(address) & 0xff) | ((ioPortReadByte(address + 1) << 8) & 0xff00);
    }
    public int ioPortReadLong(int address)
    {
        return (ioPortReadWord(address) & 0xffff) | ((ioPortReadWord(address + 2) << 16) & 0xffff0000);
    }

    public void ioPortWriteByte(int address, int data)
    {
        switch(address & 0x07) {
        case 0x02:
            writeDOR(data);
            break;
        case 0x03:
            writeTape(data);
            break;
        case 0x04:
            writeRate(data);
            break;
        case 0x05:
            writeData(data);
            break;
        default:
            break;
        }
    }
    public void ioPortWriteWord(int address, int data)
    {
        ioPortWriteByte(address, data & 0xff);
        ioPortWriteByte(address + 1, (data >>> 8) & 0xff);
    }
    public void ioPortWriteLong(int address, int data)
    {
        ioPortWriteWord(address, data & 0xffff);
        ioPortWriteWord(address + 2, (data >>> 16) & 0xffff);
    }

    private void reset(boolean doIRQ)
    {
        resetIRQ();
        currentDrive = 0;
        dataOffset = 0;
        dataLength = 0;
        dataState = STATE_COMMAND;
        dataDirection = DIRECTION_WRITE;
        drives[0].reset();
        drives[1].reset();
        resetFIFO();
        if (doIRQ)
            raiseIRQ(0xc0);
    }

    private void raiseIRQ(int status)
    {
        if (~(state & CONTROL_INTERRUPT) != 0) {
            irqDevice.setIRQ(INTERRUPT_LEVEL, 1);
            state |= CONTROL_INTERRUPT;
        }
        interruptStatus = status;
    }

    private void resetFIFO()
    {
        dataDirection = DIRECTION_WRITE;
        dataOffset = 0;
        dataState = (dataState & ~STATE_STATE) | STATE_COMMAND;
    }

    private void resetIRQ()
    {
        irqDevice.setIRQ(INTERRUPT_LEVEL, 0);
        state &= ~CONTROL_INTERRUPT;
    }

    private int readStatusB()
    {
        return 0;
    }

    private int readDOR()
    {
        int retval = 0;

        /* Drive motors state indicators */
        if ((getDrive(0).driveFlags & FloppyDrive.MOTOR_ON) != 0)
            retval |= 1 << 5;
        if ((getDrive(1).driveFlags & FloppyDrive.MOTOR_ON) != 0)
            retval |= 1 << 4;
        /* DMA enable */
        retval |= dmaEnabled ?  1 << 3 : 0;
        /* Reset indicator */
        retval |= (state & CONTROL_RESET) == 0 ? 1 << 2 : 0;
        /* Selected drive */
        retval |= currentDrive;

        return retval;
    }

    private int readTape()
    {
        /* Disk boot selection indicator */
        return bootSelect << 2;
        /* Tape indicators: never allowed */
    }

    private int readMainStatus()
    {
        int retval = 0;

        state &= ~(CONTROL_SLEEP | CONTROL_RESET);
        if ((state & CONTROL_BUSY) == 0) {
            /* Data transfer allowed */
            retval |= 0x80;
            /* Data transfer direction indicator */
            if (dataDirection == DIRECTION_READ)
                retval |= 0x40;
        }
        /* Should handle 0x20 for SPECIFY command */
        /* Command busy indicator */
        if ((dataState & STATE_STATE) == STATE_DATA || (dataState & STATE_STATE) == STATE_STATUS)
            retval |= 0x10;

        return retval;
    }

    private int readData()
    {
        FloppyDrive drive;

        drive = getCurrentDrive();
        state &= ~CONTROL_SLEEP;
        if ((dataState & STATE_STATE) == STATE_COMMAND) {
            System.err.println("fdc >> can't read data in COMMAND state");
            return 0;
        }

        int offset = dataOffset;
        if ((dataState & STATE_STATE) == STATE_DATA) {
            offset %= SECTOR_LENGTH;
            if (offset == 0) {
                int length = Math.min(dataLength - dataOffset, SECTOR_LENGTH);
                drive.read(drive.currentSector(), fifo, length);
            }
        }
        int retval = fifo[offset];
        if (++dataOffset == dataLength) {
            dataOffset = 0;
            /* Switch from transfer mode to status mode
             * then from status mode to command mode
             */
            if ((dataState & STATE_STATE) == STATE_DATA) {
                stopTransfer((byte)0x20, (byte)0x00, (byte)0x00);
            } else {
                resetFIFO();
                resetIRQ();
            }
        }

        return retval;
    }

    private int readDirection()
    {
        int retval = 0;
        if (((getDrive(0).driveFlags & FloppyDrive.REVALIDATE) != 0) || ((getDrive(1).driveFlags & FloppyDrive.REVALIDATE) != 0))
            retval |= 0x80;

        getDrive(0).driveFlags &= ~FloppyDrive.REVALIDATE;
        getDrive(1).driveFlags &= ~FloppyDrive.REVALIDATE;

        return retval;
    }

    private void writeDOR(int data)
    {
        /* Reset mode */
        if (((state & CONTROL_RESET) != 0) && ((data & 0x04) == 0))
            return;

        /* Drive motors state indicators */
        if ((data & 0x20) != 0)
            getDrive(1).start();
        else
            getDrive(1).stop();

        if ((data & 0x10) != 0)
            getDrive(0).start();
        else
            getDrive(0).stop();
        /* DMA enable */

        /* Reset */
        if ((data & 0x04) == 0) {
            if ((state & CONTROL_RESET) == 0) {
                state |= CONTROL_RESET;
            }
        } else {
            if ((state & CONTROL_RESET) != 0) {
                reset(true);
                state &= ~(CONTROL_RESET | CONTROL_SLEEP);
            }
        }
        /* Selected drive */
        currentDrive = data & 1;
    }

    private void writeTape(int data)
    {
        /* Reset mode */
        if ((state & CONTROL_RESET) != 0)
            return;

        /* Disk boot selection indicator */
        bootSelect = (data >>> 2) & 1;
        /* Tape indicators: never allow */
    }

    private void writeRate(int data)
    {
        /* Reset mode */
        if ((state & CONTROL_RESET) != 0)
            return;

        /* Reset: autoclear */
        if ((data & 0x80) != 0) {
            state |= CONTROL_RESET;
            reset(true);
            state &= ~CONTROL_RESET;
        }
        if ((data & 0x40) != 0) {
            state |= CONTROL_SLEEP;
            reset(true);
        }
        //precomp = (data >>> 2) & 0x07;
    }
    private void writeData(int data)
    {
        FloppyDrive drive = getCurrentDrive();

        /* Reset Mode */
        if ((state & CONTROL_RESET) != 0) {
            System.err.println("fdc >> floppy controller in RESET state!");
            return;
        }
        state &= ~CONTROL_SLEEP;
        if ((dataState & STATE_STATE) == STATE_STATUS) {
            System.err.println("fdc >> can't write data in status mode");
            return;
        }
        /* Is it write command time? */
        if ((dataState & STATE_STATE) == STATE_DATA) {
            /* FIFO data write */
            fifo[dataOffset++] = (byte)data;
            if (dataOffset % SECTOR_LENGTH == (SECTOR_LENGTH -1) || dataOffset == dataLength)
                drive.write(drive.currentSector(), fifo, SECTOR_LENGTH);

            /* Switch from transfer mode to status mode
             * then from status mode to command mode
             */
            if ((dataState & STATE_STATE) == STATE_DATA)
                stopTransfer((byte)0x20, (byte)0x00, (byte)0x00);
            return;
        }
        if (dataOffset == 0) {
            /* Command */
            switch (data & 0x5f) {
            case 0x46:
            case 0x4c:
            case 0x50:
            case 0x56:
            case 0x59:
            case 0x5d:
                dataLength = 9;
                enqueue(drive, data);
                return;
            default:
                break;
            }
            switch (data & 0x7f) {
            case 0x45:
            case 0x49:
                dataLength = 9;
                enqueue(drive, data);
                return;
            default:
                break;
            }
            switch (data) {
            case 0x03:
            case 0x0f:
                dataLength = 3;
                enqueue(drive, data);
                return;
            case 0x04:
            case 0x07:
            case 0x12:
            case 0x33:
            case 0x4a:
                dataLength = 2;
                enqueue(drive, data);
                return;
            case 0x08:
                fifo[0] = (byte)(0x20 | (drive.head << 2) | currentDrive);
                fifo[1] = (byte)drive.track;
                setFIFO(2, false);
                resetIRQ();
                interruptStatus = 0xc0;
                return;
            case 0x0e:
                /* Drives position */
                fifo[0] = (byte)getDrive(0).track;
                fifo[1] = (byte)getDrive(1).track;
                fifo[2] = 0;
                fifo[3] = 0;
                /* timers */
                fifo[4] = timer0;
                fifo[5] = dmaEnabled ? (byte)(timer1 << 1) : (byte)0;
                fifo[6] = (byte)drive.lastSector;
                fifo[7] = (byte)((lock << 7) | (drive.perpendicular << 2));
                fifo[8] = config;
                fifo[9] = preCompensationTrack;
                setFIFO(10, false);
                return;
            case 0x10:
                fifo[0] = CONTROLLER_VERSION;
                setFIFO(1,true);
                return;
            case 0x13:
                dataLength = 4;
                enqueue(drive, data);
                return;
            case 0x14:
                lock = 0;
                fifo[0] = 0;
                setFIFO(1, false);
                return;
            case 0x17:
            case 0x8f:
            case 0xcf:
                dataLength = 3;
                enqueue(drive, data);
                return;
            case 0x18:
                fifo[0] = 0x41; /* Stepping 1 */
                setFIFO(1, false);
                return;
            case 0x2c:
                fifo[0] = 0;
                fifo[1] = 0;
                fifo[2] = (byte)getDrive(0).track;
                fifo[3] = (byte)getDrive(1).track;
                fifo[4] = 0;
                fifo[5] = 0;
                fifo[6] = timer0;
                fifo[7] = timer1;
                fifo[8] = (byte)drive.lastSector;
                fifo[9] = (byte)((lock << 7) | (drive.perpendicular << 2));
                fifo[10] = config;
                fifo[11] = preCompensationTrack;
                fifo[12] = pwrd;
                fifo[13] = 0;
                fifo[14] = 0;
                setFIFO(15, true);
                return;
            case 0x42:
                dataLength = 9;
                enqueue(drive, data);
                return;
            case 0x4c:
                dataLength = 18;
                enqueue(drive, data);
                return;
            case 0x4d:
            case 0x8e:
                dataLength = 6;
                enqueue(drive, data);
                return;
            case 0x94:
                lock = 1;
                fifo[0] = 0x10;
                setFIFO(1, true);
                return;
            case 0xcd:
                dataLength = 11;
                enqueue(drive, data);
                return;
            default:
                /* Unknown command */
                unimplemented();
                return;
            }
        }
        enqueue(drive, data);
    }

    private void enqueue(FloppyDrive drive, int data)
    {
        fifo[dataOffset] = (byte)data;
        if (++dataOffset == dataLength) {
            if ((dataState & STATE_FORMAT) != 0) {
                formatSector();
                return;
            }
            switch (fifo[0] & 0x1f) {
            case 0x06:
                startTransfer(DIRECTION_READ);
                return;
            case 0x0c:
                startTransferDelete(DIRECTION_READ);
                return;
            case 0x16:
                stopTransfer((byte)0x20, (byte)0x00, (byte)0x00);
                return;
            case 0x10:
                startTransfer(DIRECTION_SCANE);
                return;
            case 0x19:
                startTransfer(DIRECTION_SCANL);
                return;
            case 0x1d:
                startTransfer(DIRECTION_SCANH);
                return;
            default:
                break;
            }
            switch (fifo[0] & 0x3f) {
            case 0x05:
                startTransfer(DIRECTION_WRITE);
                return;
            case 0x09:
                startTransferDelete(DIRECTION_WRITE);
                return;
            default:
                break;
            }
            switch (fifo[0]) {
            case 0x03:
                timer0 = (byte)((fifo[1] >>> 4) & 0xf);
                timer1 = (byte)(fifo[2] >>> 1);
                dmaEnabled = ((fifo[2] & 1) != 1);
                resetFIFO();
                break;
            case 0x04:
                currentDrive = fifo[1] & 1;
                drive = getCurrentDrive();
                drive.head = ((fifo[1] >>> 2) & 1);
                fifo[0] = (byte)((drive.readOnly << 6) | (drive.track == 0 ? 0x10 : 0x00) | (drive.head << 2) | currentDrive | 0x28);
                setFIFO(1, false);
                break;
            case 0x07:
                currentDrive = fifo[1] & 1;
                drive = getCurrentDrive();
                drive.recalibrate();
                resetFIFO();
                raiseIRQ(0x20);
                break;
            case 0x0f:
                currentDrive = fifo[1] & 1;
                drive = getCurrentDrive();
                drive.start();
                if (fifo[2] <= drive.track)
                    drive.direction = 1;
                else
                    drive.direction = 0;
                resetFIFO();
                if (fifo[2] > drive.maxTrack)
                    raiseIRQ(0x60);
                else {
                    drive.track = fifo[2];
                    raiseIRQ(0x20);
                }
                break;
            case 0x12:
                if ((fifo[1] & 0x80) != 0)
                    drive.perpendicular = fifo[1] & 0x7;
                /* No result back */
                resetFIFO();
                break;
            case 0x13:
                config = fifo[2];
                preCompensationTrack =  fifo[3];
                /* No result back */
                resetFIFO();
                break;
            case 0x17:
                pwrd = fifo[1];
                fifo[0] = fifo[1];
                setFIFO(1, true);
                break;
            case 0x33:
                /* No result back */
                resetFIFO();
                break;
            case 0x42:
                System.err.println("fdc >> treat READ_TRACK command");
                startTransfer(DIRECTION_READ);
                break;
            case 0x4A:
                /* XXX: should set main status register to busy */
                drive.head = (fifo[1] >>> 2) & 1;
                resultTimer.setExpiry(clock.getTime() + (clock.getTickRate()/50));
                break;
            case 0x4C:
                /* RESTORE */
                /* Drives position */
                getDrive(0).track = fifo[3];
                getDrive(1).track = fifo[4];
                /* timers */
                timer0 = fifo[7];
                timer1 = fifo[8];
                drive.lastSector = fifo[9];
                lock = (byte)(fifo[10] >>> 7);
                drive.perpendicular = (fifo[10] >>> 2) & 0xf;
                config = fifo[11];
                preCompensationTrack = fifo[12];
                pwrd = fifo[13];
                resetFIFO();
                break;
            case 0x4D:
                /* FORMAT_TRACK */
                currentDrive = fifo[1] & 1;
                drive = getCurrentDrive();
                dataState |= STATE_FORMAT;
                if ((fifo[0] & 0x80) != 0)
                    dataState |= STATE_MULTI;
                else
                    dataState &= ~STATE_MULTI;
                dataState &= ~STATE_SEEK;
                 drive.bps = fifo[2] > 7 ? 0x4000 : (0x80 << fifo[2]);
                drive.lastSector = fifo[3];

                /* Bochs BIOS is buggy and don't send format informations
                 * for each sector. So, pretend all's done right now...
                 */
                dataState &= ~STATE_FORMAT;
                stopTransfer((byte)0x00, (byte)0x00, (byte)0x00);
                break;
            case (byte)0x8E:
                /* DRIVE_SPECIFICATION_COMMAND */
                if ((fifo[dataOffset - 1] & 0x80) != 0) {
                    /* Command parameters done */
                    if ((fifo[dataOffset - 1] & 0x40) != 0) {
                        fifo[0] = fifo[1];
                        fifo[2] = 0;
                        fifo[3] = 0;
                        setFIFO(4, true);
                    } else
                        resetFIFO();
                } else if (dataLength > 7) {
                    /* ERROR */
                    fifo[0] = (byte)(0x80 | (drive.head << 2) | currentDrive);
                    setFIFO(1, true);
                }
                break;
            case (byte)0x8F:
                /* RELATIVE_SEEK_OUT */
                currentDrive = fifo[1] & 1;
                drive = getCurrentDrive();
                drive.start();
                drive.direction = 0;
                if (fifo[2] + drive.track >= drive.maxTrack)
                    drive.track = drive.maxTrack - 1;
                else
                    drive.track += fifo[2];
                resetFIFO();
                raiseIRQ(0x20);
                break;
            case (byte)0xCD:
                /* FORMAT_AND_WRITE */
                System.err.println("fdc >> treat FORMAT_AND_WRITE command");
                unimplemented();
                break;
            case (byte)0xCF:
                /* RELATIVE_SEEK_IN */
                currentDrive = fifo[1] & 1;
                drive = getCurrentDrive();
                drive.start();
                drive.direction = 1;
                if (fifo[2] > drive.track)
                    drive.track = 0;
                else
                    drive.track -= fifo[2];
                resetFIFO();
                /* Raise Interrupt */
                raiseIRQ(0x20);
                break;
            }
        }
    }

    private void setFIFO(int fifoLength, boolean doIRQ)
    {
        dataDirection = DIRECTION_READ;
        dataLength = fifoLength;
        dataOffset = 0;
        dataState = (dataState & ~STATE_STATE) | STATE_STATUS;
        if (doIRQ)
            raiseIRQ(0x00);
    }

    private FloppyDrive getCurrentDrive()
    {
        return getDrive(currentDrive);
    }

    private FloppyDrive getDrive(int driveNumber)
    {
        return drives[driveNumber - bootSelect];
    }

    public void setDrive(org.jpc.support.BlockDevice drive, int i)
    {
        if ((i < 0 ) || (i > drives.length -1))
            return;
        getDrive(i).setDrive(drive);
        getDrive(i).revalidate();
        //do we need to call revalidate() on the drive as well?
    }

    private void unimplemented()
    {
        fifo[0] = (byte)0x80;
        setFIFO(1, false);
    }

    private void startTransfer(int direction)
    {
        currentDrive = fifo[1] & 1;
        FloppyDrive drive = getCurrentDrive();
        byte kt = fifo[2];
        byte kh = fifo[3];
        byte ks = fifo[4];
        boolean didSeek = false;
        switch (drive.seek(0xff & kh, 0xff & kt, 0xff & ks, drive.lastSector)) {
        case 2:
            /* sect too big */
            stopTransfer((byte)0x40, (byte)0x00, (byte)0x00);
            fifo[3] = kt;
            fifo[4] = kh;
            fifo[5] = ks;
            return;
        case 3:
            /* track too big */
            stopTransfer((byte)0x40, (byte)0x80, (byte)0x00);
            fifo[3] = kt;
            fifo[4] = kh;
            fifo[5] = ks;
            return;
        case 4:
            /* No seek enabled */
            stopTransfer((byte)0x40, (byte)0x00, (byte)0x00);
            fifo[3] = kt;
            fifo[4] = kh;
            fifo[5] = ks;
            return;
        case 1:
            didSeek = true;
            break;
        default:
            break;
        }

        dataDirection = direction;
        dataOffset = 0;
        dataState = (dataState & ~STATE_STATE) | STATE_DATA;

        if ((fifo[0] & 0x80) != 0)
            dataState |= STATE_MULTI;
        else
            dataState &= ~STATE_MULTI;
        if (didSeek)
            dataState |= STATE_SEEK;
        else
            dataState &= ~STATE_SEEK;
        if (fifo[5] == 0x00) {
            dataLength = fifo[8];
        } else {
            dataLength = 128 << fifo[5];
            int temp = drive.lastSector - ks + 1;
            if ((fifo[0] & 0x80) != 0)
                temp += drive.lastSector;
            dataLength *= temp;
        }
        eot = fifo[6];
        if (dmaEnabled) {
            int dmaMode = 0;
            dmaMode = dma.getChannelMode(DMA_CHANNEL & 3);
            dmaMode = (dmaMode >>> 2) & 3;
            if (((direction == DIRECTION_SCANE || direction == DIRECTION_SCANL || direction == DIRECTION_SCANH) && dmaMode == 0) ||
                (direction == DIRECTION_WRITE && dmaMode == 2) || (direction == DIRECTION_READ && dmaMode == 1)) {
                /* No access is allowed until DMA transfer has completed */
                state |= CONTROL_BUSY;
                /* Now, we just have to wait for the DMA controller to
                 * recall us...
                 */
                dma.holdDREQ(DMA_CHANNEL & 3);
                return;
            } else {
                System.err.println("fdc >> dma_mode=" + dmaMode + " direction="+ direction);
            }
        }
        /* IO based transfer: calculate len */
        raiseIRQ(0x00);
        return;
    }

    private void stopTransfer(byte status0, byte status1, byte status2)
    {
        FloppyDrive drive = getCurrentDrive();

        fifo[0] = (byte)(status0 | (drive.head << 2) | currentDrive);
        fifo[1] = status1;
        fifo[2] = status2;
        fifo[3] = (byte)drive.track;
        fifo[4] = (byte)drive.head;
        fifo[5] = (byte)drive.sector;
        fifo[6] = SECTOR_SIZE_CODE;
        dataDirection = DIRECTION_READ;
        if ((state & CONTROL_BUSY) != 0) {
            dma.releaseDREQ(DMA_CHANNEL & 3);
            state &= ~CONTROL_BUSY;
        }
        setFIFO(7, true);
    }

    private void startTransferDelete(int direction)
    {
        stopTransfer((byte)0x60, (byte)0x00, (byte)0x00);
    }

    private void formatSector()
    {
        System.err.println("Cannot Format Sector");
    }

    private static int memcmp(byte[] a1, byte[] a2, int offset, int length)
    {
        for (int i = 0; i < length; i++) {
            if (a1[i] != a2[i + offset])
                return a1[i] - a2[i + offset];
        }
        return 0;
    }

    public int transferHandler(int nchan, int pos, int size)
    {
        byte status0 = 0x00, status1 = 0x00, status2 = 0x00;

        if ((state & CONTROL_BUSY) == 0)
            return 0;

        FloppyDrive drive = getCurrentDrive();

        if ((dataDirection == DIRECTION_SCANE) || (dataDirection == DIRECTION_SCANL) || (dataDirection == DIRECTION_SCANH))
            status2 = 0x04;
        size = Math.min(size, dataLength);
        if (drive.device == null) {
            if (dataDirection == DIRECTION_WRITE)
                stopTransfer((byte)0x60, (byte)0x00, (byte)0x00);
            else
                stopTransfer((byte)0x40, (byte)0x00, (byte)0x00);
            return 0;
        }

        int relativeOffset = dataOffset % SECTOR_LENGTH;
        int startOffset;
        for (startOffset = dataOffset; dataOffset < size;) {
            int length = Math.min(size - dataOffset, SECTOR_LENGTH - relativeOffset);
            if ((dataDirection != DIRECTION_WRITE) || (length < SECTOR_LENGTH) || (relativeOffset != 0)) {
                /* READ & SCAN commands and realign to a sector for WRITE */
                if (drive.read(drive.currentSector(), fifo, 1) < 0) {
                    /* Sure, image size is too small... */
                    for (int i = 0; i < Math.min(fifo.length, SECTOR_LENGTH); i++)
                        fifo[i] = (byte)0x00;
                }
            }
            switch (dataDirection) {
            case DIRECTION_READ:
                /* READ commands */
                dma.writeMemory (nchan, fifo, relativeOffset,
                                 dataOffset, length);
                break;
            case DIRECTION_WRITE:
                /* WRITE commands */
                dma.readMemory (nchan, fifo, relativeOffset,
                                dataOffset, length);
                if (drive.write(drive.currentSector(), fifo, 1) < 0) {
                    stopTransfer((byte)0x60, (byte)0x00, (byte)0x00);
                    return length;
                }
                break;
            default:
                /* SCAN commands */
                {
                    byte[] tempBuffer = new byte[SECTOR_LENGTH];
                    dma.readMemory (nchan, tempBuffer, 0,
                                    dataOffset, length);
                    int ret = memcmp(tempBuffer, fifo, relativeOffset, length);
                    if (ret == 0) {
                        status2 = 0x08;
                        length = dataOffset - startOffset;
                        if (dataDirection == DIRECTION_SCANE || dataDirection == DIRECTION_SCANL || dataDirection == DIRECTION_SCANH)
                            status2 = 0x08;
                        if ((dataState & STATE_SEEK) != 0)
                            status0 |= 0x20;
                        dataLength -= length;
                        //    if (dataLength == 0)
                        stopTransfer(status0, status1, status2);
                        return length;

                    }
                    if ((ret < 0 && dataDirection == DIRECTION_SCANL) || (ret > 0 && dataDirection == DIRECTION_SCANH)) {
                        status2 = 0x00;

                        length = dataOffset - startOffset;
                        if (dataDirection == DIRECTION_SCANE || dataDirection == DIRECTION_SCANL || dataDirection == DIRECTION_SCANH)
                            status2 = 0x08;
                        if ((dataState & STATE_SEEK) != 0)
                            status0 |= 0x20;
                        dataLength -= length;
                        //    if (dataLength == 0)
                        stopTransfer(status0, status1, status2);

                        return length;
                    }
                }
                break;
            }
            dataOffset += length;
            relativeOffset = dataOffset % SECTOR_LENGTH;
            if (relativeOffset == 0) {
                /* Seek to next sector */
                /* XXX: drive.sect >= drive.last_sect should be an
                   error in fact */
                if ((drive.sector >= drive.lastSector) ||
                    (drive.sector == eot)) {
                    drive.sector = 1;
                    if ((dataState & STATE_MULTI) != 0) {
                        if ((drive.head == 0) && ((drive.flags & FloppyDrive.DOUBLE_SIDES) != 0)) {
                            drive.head = 1;
                        } else {
                            drive.head = 0;
                            drive.track++;
                            if ((drive.flags & FloppyDrive.DOUBLE_SIDES) == 0)
                                break;
                        }
                    } else {
                        drive.track++;
                        break;
                    }
                } else {
                    drive.sector++;
                }
            }
        }

        int length = dataOffset - startOffset;
        if (dataDirection == DIRECTION_SCANE || dataDirection == DIRECTION_SCANL || dataDirection == DIRECTION_SCANH)
            status2 = 0x08;
        if ((dataState & STATE_SEEK) != 0)
            status0 |= 0x20;
        dataLength -= length;
        //    if (dataLength == 0)
        stopTransfer(status0, status1, status2);

        return length;
    }


    public static class FloppyDrive implements org.jpc.SRDumpable
    {
        static final int DRIVE_144  = 0x00;   // 1.44 MB 3"5 drive
        static final int DRIVE_288  = 0x01;   // 2.88 MB 3"5 drive
        static final int DRIVE_120  = 0x02;   // 1.2  MB 5"25 drive
        static final int DRIVE_NONE = 0x03;   // No drive connected

        static final int MOTOR_ON   = 0x01; // motor on/off
        static final int REVALIDATE = 0x02; // Revalidated

        static final int DOUBLE_SIDES  = 0x01;

        BlockDevice device;
        int drive;
        int driveFlags;
        int perpendicular;
        int head;
        int track;
        int sector;
        int direction;
        int readWrite;
        int flags;
        int lastSector;
        int maxTrack;
        int bps;
        int readOnly;

        public void dumpSR(org.jpc.support.SRDumper output) throws IOException
        {
            if(output.dumped(this))
                return;
            dumpSRPartial(output);
            output.endObject();
        }

        public void dumpSRPartial(org.jpc.support.SRDumper output) throws IOException
        {
            output.dumpObject(device);
            output.dumpInt(drive);
            output.dumpInt(driveFlags);
            output.dumpInt(perpendicular);
            output.dumpInt(head);
            output.dumpInt(track);
            output.dumpInt(sector);
            output.dumpInt(direction);
            output.dumpInt(readWrite);
            output.dumpInt(flags);
            output.dumpInt(lastSector);
            output.dumpInt(maxTrack);
            output.dumpInt(bps);
            output.dumpInt(readOnly);
        }

        public FloppyDrive(org.jpc.support.SRLoader input) throws IOException
        {
            input.objectCreated(this);
            device = (BlockDevice)(input.loadObject());
            drive = input.loadInt();
            driveFlags = input.loadInt();
            perpendicular = input.loadInt();
            head = input.loadInt();
            track = input.loadInt();
            sector = input.loadInt();
            direction = input.loadInt();
            readWrite = input.loadInt();
            flags = input.loadInt();
            lastSector = input.loadInt();
            maxTrack = input.loadInt();
            bps = input.loadInt();
            readOnly = input.loadInt();
        }

        public static org.jpc.SRDumpable loadSR(org.jpc.support.SRLoader input, Integer id) throws IOException
        {
            org.jpc.SRDumpable x = new FloppyDrive(input);
            input.endObject();
            return x;
        }

        public FloppyDrive(BlockDevice device)
        {
            this.device = device;
            drive = 2;   //Claim it's 1440KiB drive.
            driveFlags = 0;
            perpendicular = 0;
            lastSector = 0;
            maxTrack = 0;
        }

        public void dumpStatusPartial(org.jpc.support.StatusDumper output)
        {
            output.println("\tdrive " + drive + " driveFlags " + driveFlags + " perpendicular " + perpendicular);
            output.println("\thead " + head + " track " + track + " sector " + sector + " direction " + direction);
            output.println("\treadWrite " + readWrite + " flags " + flags + " lastSector " + lastSector);
            output.println("\tmaxTrack " + maxTrack + " bps " + bps + " readOnly " + readOnly);
            output.println("\tdevice <object #" + output.objectNumber(device) + ">"); if(device != null) device.dumpStatus(output);
        }

        public void dumpStatus(org.jpc.support.StatusDumper output)
        {
            if(output.dumped(this))
                return;

            output.println("#" + output.objectNumber(this) + ": FloppyDrive:");
            dumpStatusPartial(output);
            output.endObject();
        }

        public void setDrive(org.jpc.support.BlockDevice drive)
        {
            device = drive;
        }

        public void start()
        {
            driveFlags |= MOTOR_ON;
        }
        public void stop()
        {
            driveFlags &= ~MOTOR_ON;
        }
        public int seek(int seekHead, int seekTrack, int seekSector, int enableSeek)
        {
            if ((seekTrack > maxTrack) || (seekHead != 0 && (flags & DOUBLE_SIDES) == 0))
                return 2;

            if (seekSector > lastSector)
                return 3;

            int fileSector = calculateSector(seekHead, seekTrack, seekSector, lastSector);
            if (fileSector != currentSector()) {
                if (enableSeek == 0)
                    return 4;

                head = seekHead;
                if (track != seekTrack) {
                    track = seekTrack;
                    sector = seekSector;
                    return 1;
                }

                sector = seekSector;
            }
            return 0;
        }

        public int currentSector()
        {
            return calculateSector(head, track, sector, lastSector);
        }

        private int calculateSector(int head, int track, int sector, int lastSector)
        {
           if((flags & DOUBLE_SIDES) != 0) 
               return ((((0xff & track) * 2) + (0xff & head)) * (0xff & lastSector)) + (0xff & sector) - 1;
           else
               return ((((0xff & track) * 1) + (0xff & head)) * (0xff & lastSector)) + (0xff & sector) - 1;
        }

        public void recalibrate()
        {
            head = 0;
            track = 0;
            sector = 1;
            direction = 1;
            readWrite = 0;
        }
        public int read(int sector, byte[] buffer, int length)
        {
            return device.read(0xffffffffl & sector, buffer, length);
        }
        public int write(int sector, byte[] buffer, int length)
        {
            return device.write(0xffffffffl & sector, buffer, length);
        }

        public void reset()
        {
            stop();
            recalibrate();
        }

        public void revalidate()
        {
            driveFlags &= ~REVALIDATE;
            if (device != null && device.inserted()) {
                if (device.heads() == 1) {
                    flags &= ~DOUBLE_SIDES;
                } else {
                    flags |= DOUBLE_SIDES;
                }
                maxTrack = device.cylinders();
                lastSector = (byte)device.sectors();
                readOnly = device.readOnly() ? 0x1 : 0x0;
                drive = 2;  //1440KiB, but we don't really care.
            } else {
                lastSector = 0;
                maxTrack = 0;
                flags &= ~DOUBLE_SIDES;
            }
            driveFlags |= REVALIDATE;
        }

        public String toString()
        {
            if((flags | DOUBLE_SIDES) != 0)
                return "Floppy, " + maxTrack + " by " + lastSector + " by 2.";
            else
                return "Floppy, " + maxTrack + " by " + lastSector + " by 1.";
        }
    }

    private boolean ioportRegistered;

    public void reset()
    {
        irqDevice = null;
        clock = null;
        resultTimer = null;
        dma = null;
        //Really Empty?
        ioportRegistered = false;
        fifo = new byte[SECTOR_LENGTH];
        config = (byte)0x60; /* Implicit Seek, polling & fifo enabled */
        drives = new FloppyDrive[2];
        state = CONTROL_ACTIVE;
    }

    public boolean initialised()
    {
        return ((irqDevice != null) && (clock != null) && (dma != null) && (drives[0] != null) && ioportRegistered);
    }

    public void acceptComponent(HardwareComponent component)
    {
        if ((component instanceof InterruptController) && component.initialised())
            irqDevice = (InterruptController)component;

        if ((component instanceof Clock) && component.initialised()) {
            clock = (Clock)component;
            resultTimer = clock.newTimer(this);
        }

        if ((component instanceof IOPortHandler) && component.initialised()) {
            ((IOPortHandler)component).registerIOPortCapable(this);
            ioportRegistered = true;
        }

        if ((component instanceof DMAController) && component.initialised()) {
            if (((DMAController)component).isFirst()) {
                if (DMA_CHANNEL != -1) {
                    dma = (DMAController)component;
                    dmaEnabled = true;
                    dma.registerChannel(DMA_CHANNEL & 3, this);
                }
            }
        }

        if(drives[0] == null) {
            drives[0] = new FloppyDrive(new GenericBlockDevice(BlockDevice.TYPE_FLOPPY));
            drives[1] = new FloppyDrive(new GenericBlockDevice(BlockDevice.TYPE_FLOPPY));
        }

        if (initialised()) {
            reset(false);
            for (int i = 0; i < 2; i++)
                if (drives[i] != null) drives[i].revalidate();
        }
    }

    public boolean updated()
    {
        return (irqDevice.updated() && clock.updated() && dma.updated() &&
                drivesUpdated && ioportRegistered);
    }

    public void updateComponent(HardwareComponent component)
    {
        //        if ((component instanceof Clock)  && component.updated())
        //        {
            //            clock = (Clock)component;
        //            resultTimer = clock.newTimer(this);
        //        }
        if ((component instanceof IOPortHandler) && component.updated())
        {
            ((IOPortHandler)component).registerIOPortCapable(this);
            ioportRegistered = true;
        }
        if ((component instanceof DMAController) && component.updated())
        {
            if (((DMAController)component).isFirst())
            {
                if (DMA_CHANNEL != -1)
                {
                    //dma = (DMAController)component;
                    dmaEnabled = true;
                    dma.registerChannel(DMA_CHANNEL & 3, this);
                }
            }
        }

/*
        if(drives[0] == null) {
            drives[0] = new FloppyDrive(new GenericBlockDevice(BlockDevice.TYPE_FLOPPY));
            drives[1] = new FloppyDrive(new GenericBlockDevice(BlockDevice.TYPE_FLOPPY));
        }
*/
        //        if (initialised())
        //        {
        //            reset(false);
        //            for (int i = 0; i < 2; i++)
        //                if (drives[i] != null) drives[i].revalidate();
        //        }
    }

    public String toString()
    {
        if(drives[0] != null && drives[1] != null)
            return "Intel 82078 Floppy Controller [A:" + drives[0].toString() + ", B:" + drives[1].toString() + "]";
        else if(drives[0] != null && drives[1] == null)
            return "Intel 82078 Floppy Controller [A:" + drives[0].toString() + "]";
        else if(drives[0] == null && drives[1] != null)
            return "Intel 82078 Floppy Controller [B:" + drives[1].toString() + "]";
        else if(drives[0] == null && drives[1] == null)
            return "Intel 82078 Floppy Controller [<no drives>]";
        return "Intel 82078 Floppy Controller [CAN'T HAPPEN]";
    }
}
