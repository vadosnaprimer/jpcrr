/*
    JPC-RR: A x86 PC Hardware Emulator
    Release 1

    Copyright (C) 2007-2009 Isis Innovation Limited
    Copyright (C) 2009-2010 H. Ilari Liusvaara

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

package org.jpc.modules;

import org.jpc.emulator.*;
import org.jpc.modulesaux.*;
import org.jpc.emulator.motherboard.*;
import java.io.*;

public class FMCard  extends AbstractHardwareComponent implements IOPortCapable, TimerResponsive, SoundOutputDevice
{
    private boolean ioportRegistered;
    private Timer timer;
    private Clock clock;
    private FMChip fmChip;
    private int fmIndex;
    private long fmNextAttention;
    private SoundDigitalOut fmOutput;

    private int lastStatus;  //Not saved.

    public static final long TIME_NEVER = 0x7FFFFFFFFFFFFFFFL;

    public void dumpSRPartial(SRDumper output) throws IOException
    {
        super.dumpSRPartial(output);
        output.dumpBoolean(ioportRegistered);
        output.dumpObject(clock);
        output.dumpObject(timer);
        output.dumpObject(fmChip);
        output.dumpObject(fmOutput);
        output.dumpInt(fmIndex);
        output.dumpLong(fmNextAttention);
    }


    public FMCard(SRLoader input) throws IOException
    {
        super(input);
        ioportRegistered = input.loadBoolean();
        clock = (Clock)input.loadObject();
        timer = (Timer)input.loadObject();
        fmChip = (FMChip)input.loadObject();
        fmOutput = (SoundDigitalOut)input.loadObject();
        fmIndex = input.loadInt();
        fmNextAttention = input.loadLong();
        lastStatus = -1;
    }

    public FMCard() throws IOException
    {
        fmChip = new FMChip();
        fmOutput = null;
        fmIndex = 0;
        fmNextAttention = TIME_NEVER;
        lastStatus = -1;
    }

    public void dumpStatusPartial(StatusDumper output)
    {
        super.dumpStatusPartial(output);
        output.println("\tioportRegistered " + ioportRegistered);
        output.println("\tclock <object #" + output.objectNumber(clock) + ">"); if(clock != null) clock.dumpStatus(output);
        output.println("\ttimer <object #" + output.objectNumber(timer) + ">"); if(timer != null) timer.dumpStatus(output);
        output.println("\tfmIndex " + fmIndex);
        output.println("\tfmNextAttention " + fmNextAttention);
        output.println("\tfmChip <object #" + output.objectNumber(fmChip) + ">"); if(fmChip != null) fmChip.dumpStatus(output);
        output.println("\tfmOutput <object #" + output.objectNumber(fmOutput) + ">"); if(fmOutput != null) fmOutput.dumpStatus(output);
    }

    public void dumpStatus(StatusDumper output)
    {
        if(output.dumped(this))
            return;

        output.println("#" + output.objectNumber(this) + ": FMCard:");
        dumpStatusPartial(output);
        output.endObject();
    }

    public boolean initialised()
    {
        return ((clock != null) && ioportRegistered);
    }

    public void acceptComponent(HardwareComponent component)
    {
        if((component instanceof Clock) && component.initialised()) {
            clock = (Clock)component;
            timer = clock.newTimer(this);
        }

        if((component instanceof IOPortHandler) && component.initialised()) {
            ((IOPortHandler)component).registerIOPortCapable(this);
            ioportRegistered = true;
        }

    }

    public int[] ioPortsRequested()
    {
        int[] ret;
        ret = new int[4];
        ret[0] = 0x388;
        ret[1] = 0x389;
        ret[2] = 0x38A;
        ret[3] = 0x38B;
        return ret;
    }

    public int getTimerType()
    {
        return 37;
    }

    public void ioPortWriteWord(int address, int data)
    {
        ioPortWriteByte(address, data & 0xFF);
        ioPortWriteByte(address + 1, (data >>> 8) & 0xFF);
    }

    public void ioPortWriteLong(int address, int data)
    {
        ioPortWriteByte(address, data & 0xFF);
        ioPortWriteByte(address + 1, (data >>> 8) & 0xFF);
        ioPortWriteByte(address + 2, (data >>> 16) & 0xFF);
        ioPortWriteByte(address + 3, (data >>> 24) & 0xFF);
    }

    public int ioPortReadWord(int address)
    {
        return (ioPortReadByte(address) | (ioPortReadByte(address + 1) << 8));
    }

    public int ioPortReadLong(int address)
    {
        return ioPortReadByte(address) | (ioPortReadByte(address + 1) << 8) |
            (ioPortReadByte(address + 2) << 16) | (ioPortReadByte(address + 3) << 24);
    }

    public void ioPortWriteByte(int address, int data)
    {
        ioWrite(address - 0x388, data);
    }

    public int ioPortReadByte(int address)
    {
        return ioRead(address - 0x388);
    }

    public int requestedSoundChannels()
    {
        return 1;
    }

    public void soundChannelCallback(SoundDigitalOut out)
    {
        if(fmOutput == null) {
            fmChip.setOutput(out);
            fmOutput = out;
        }
    }

    //Do I/O write. Possible offsets are 0-7 and 10-15.
    public void ioWrite(int offset, int dataByte)
    {
        switch(offset) {
        case 0:
            fmIndex = dataByte;
            return;
        case 1:
            writeFM(fmIndex, dataByte);
            return;
        case 2:
            fmIndex = 256 + dataByte;
            return;
        case 3:
            writeFM(fmIndex, dataByte);
            return;
        }
    }

    //Do I/O read. Possible offsets are 0-7 and 10-15.
    public int ioRead(int offset)
    {
        switch(offset) {
        case 0:
        case 2:
            return readFMStatus();
        case 1:
        case 3:
            return 0;  //Lets do like opl.cpp does...
        default:
            return -1;  //Not readable.
        }
    }

    public void reset()
    {
        fmIndex = 0;
        if(clock != null)
            fmChip.resetCard(clock.getTime());
        else
            fmChip.resetCard(0);
    }

    //Read FM synth #1 status register.
    private final int readFMStatus()
    {
        int status;
        status = fmChip.status(clock.getTime());
        lastStatus = status;
        return status;
    }

    //Write FM synth #1 data register.
    private final void writeFM(int reg, int data)
    {
        fmChip.write(clock.getTime(), reg, data);
        updateTimer();
    }

    //Recompute value for timer expiry.
    private final void updateTimer()
    {
        long nextTime = TIME_NEVER;
        long tmp = fmChip.nextAttention(clock.getTime());
        if(tmp < nextTime)
            nextTime = tmp;
        if(nextTime != TIME_NEVER) {
            if(timer != null)
                timer.setExpiry(nextTime);
        } else
            if(timer != null)
                timer.disable();
    }

    public void callback()
    {
        long timeNow = clock.getTime();
        boolean runAny = true;
        while(runAny) {
            runAny = false;
            long tmp = fmChip.nextAttention(clock.getTime());
            if(tmp <= timeNow) {
                fmChip.attention(clock.getTime());
                runAny = true;
            }
            if(runAny)
                updateTimer();
        }
    }
}
