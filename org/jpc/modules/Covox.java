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
import org.jpc.emulator.motherboard.*;
import java.io.*;

public class Covox extends AbstractHardwareComponent implements IOPortCapable, SoundOutputDevice
{
    private int baseIOAddress;
    private boolean ioportRegistered;
    private Clock clock;
    private SoundDigitalOut pcmOutput;

    private int outputRegister;
    private boolean delayedLoad;

    public void dumpSRPartial(SRDumper output) throws IOException
    {
        super.dumpSRPartial(output);
        output.dumpInt(baseIOAddress);
        output.dumpBoolean(ioportRegistered);
        output.dumpObject(clock);
        output.dumpObject(pcmOutput);
        output.dumpInt(outputRegister);
        output.dumpBoolean(delayedLoad);
    }

    public Covox(SRLoader input) throws IOException
    {
        super(input);
        baseIOAddress = input.loadInt();
        ioportRegistered = input.loadBoolean();
        clock = (Clock)input.loadObject();
        pcmOutput = (SoundDigitalOut)input.loadObject();
        outputRegister = input.loadInt();
        delayedLoad = input.loadBoolean();
    }

    public Covox(String parameters) throws IOException
    {
        if(parameters.charAt(0) == '-') {
            delayedLoad = true;
            parameters = parameters.substring(1);
        }
        try {
            baseIOAddress = Integer.parseInt(parameters);
            if(baseIOAddress < 0 || baseIOAddress > 65532)
                throw new NumberFormatException("Number out of range");
        } catch(Exception e) {
            throw new IOException("Bad port number '" + parameters + "'");
        }
        outputRegister = 128;
    }

    public Covox() throws IOException
    {
        this("956");
    }

    public void dumpStatusPartial(StatusDumper output)
    {
        super.dumpStatusPartial(output);
        output.println("\tbaseIOAddress " + baseIOAddress);
        output.println("\tioportRegistered " + ioportRegistered);
        output.println("\tclock <object #" + output.objectNumber(clock) + ">"); if(clock != null) clock.dumpStatus(output);
        output.println("\tpcmOutput <object #" + output.objectNumber(pcmOutput) + ">"); if(pcmOutput != null) pcmOutput.dumpStatus(output);
        output.println("\toutputRegister " + outputRegister);
        output.println("\tdelayedLoad " + delayedLoad);
    }

    public void dumpStatus(StatusDumper output)
    {
        if(output.dumped(this))
            return;

        output.println("#" + output.objectNumber(this) + ": Covox:");
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
        }

        if((component instanceof IOPortHandler) && component.initialised()) {
            ((IOPortHandler)component).registerIOPortCapable(this);
            ioportRegistered = true;
        }

    }

    public int[] ioPortsRequested()
    {
        int[] ret = new int[4];
        for(int i = 0; i < 4; i++)
            ret[i] = baseIOAddress + i;
        return ret;
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
        address -= baseIOAddress;
        switch(address) {
        case 0:   //Data port.
            outputRegister = data;
            if(!delayedLoad)
                pcmOutput.addSample(clock.getTime(), (short)((data << 8) - 32768), (short)((data << 8) - 32768));
            break;
        case 2:   //Control port.
            if(delayedLoad && (data & 0x08) != 0) {
                pcmOutput.addSample(clock.getTime(), (short)((outputRegister << 8) - 32768), (short)((outputRegister << 8) - 32768));
            }
            break;
        }
    }

    public int ioPortReadByte(int address)
    {
        address -= baseIOAddress;
        switch(address) {
        case 0:   //Data port.
            return outputRegister;
        case 1:   //Status port.
            return 0x7F;
        default:
            return -1;
        }
    }

    public int requestedSoundChannels()
    {
        return 1;
    }

    public void soundChannelCallback(SoundDigitalOut out)
    {
        pcmOutput = out;
    }

    public void reset()
    {
    }
}
