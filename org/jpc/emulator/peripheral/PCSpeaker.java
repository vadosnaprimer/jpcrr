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

public class PCSpeaker extends AbstractHardwareComponent implements IOPortCapable
{
    private int dummyRefreshClock;
    private int speakerData;
    private IntervalTimer pit;
    private Clock timeSource;
    private Magic magic;

    private boolean ioportRegistered;

    public PCSpeaker()
    {
        magic = new Magic(Magic.PC_SPEAKER_MAGIC_V1);
        ioportRegistered = false;
    }

    public void dumpState(DataOutput output) throws IOException
    {
        magic.dumpState(output);
        output.writeInt(dummyRefreshClock);
        output.writeInt(speakerData);
    }

    public void dumpStatusPartial(org.jpc.support.StatusDumper output)
    {
        super.dumpStatusPartial(output);
        output.println("\tdummyRefreshClock " + dummyRefreshClock + " speakerData " + speakerData);
        output.println("\tpit <object #" + output.objectNumber(pit) + ">"); if(pit != null) pit.dumpStatus(output);
        output.println("\ttimeSource <object #" + output.objectNumber(timeSource) + ">"); if(timeSource != null) timeSource.dumpStatus(output);
    }
 
    public void dumpStatus(org.jpc.support.StatusDumper output)
    {
        if(output.dumped(this))
            return;

        output.println("#" + output.objectNumber(this) + ": PCSpeaker:");
        dumpStatusPartial(output);
        output.endObject();
    }

    public void loadState(DataInput input) throws IOException
    {
        magic.loadState(input);
        ioportRegistered = false;
        dummyRefreshClock = input.readInt();
        speakerData = input.readInt();
    }

    public int[] ioPortsRequested()
    {
        return new int[]{0x61};
    }

    public int ioPortReadByte(int address)
    {
        int out = pit.getOut(2, timeSource.getTime());
        dummyRefreshClock ^= 1;
        return (speakerData << 1) | (pit.getGate(2) ? 1 : 0) | (out << 5) |
            (dummyRefreshClock << 4);
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
        speakerData = (data >> 1) & 1;
        pit.setGate(2, (data & 1) != 0);
    }
    public void ioPortWriteWord(int address, int data)
    {
        this.ioPortWriteByte(address, data);
        this.ioPortWriteByte(address + 1, data >> 8);
    }
    public void ioPortWriteLong(int address, int data)
    {
        this.ioPortWriteWord(address, data);
        this.ioPortWriteWord(address + 2, data >> 16);
    }

    public boolean initialised()
    {
        return ioportRegistered && (pit != null) && (timeSource != null);
    }

    public void reset()
    {
        pit = null;
        timeSource = null;
        ioportRegistered = false;
    }

    public boolean updated()
    {
        return ioportRegistered && pit.updated() && timeSource.updated();
    }

    public void updateComponent(HardwareComponent component)
    {
        if ((component instanceof IOPortHandler) && component.updated())
        {
            ((IOPortHandler)component).registerIOPortCapable(this);
            ioportRegistered = true;
        }
    }

    public void acceptComponent(HardwareComponent component)
    {
        if ((component instanceof IntervalTimer) &&
            component.initialised()) {
            pit = (IntervalTimer)component;
        }
        if ((component instanceof Clock) &&
            component.initialised()) {
            timeSource = (Clock)component;
        }
        if ((component instanceof IOPortHandler)
            && component.initialised()) {
            ((IOPortHandler)component).registerIOPortCapable(this);
            ioportRegistered = true;
        }
    }
}
