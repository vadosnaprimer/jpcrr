/*
    JPC-RR: A x86 PC Hardware Emulator
    Release 1

    Copyright (C) 2007-2009 Isis Innovation Limited
    Copyright (C) 2009 H. Ilari Liusvaara

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

package org.jpc.emulator.peripheral;

import org.jpc.emulator.motherboard.*;
import org.jpc.emulator.*;

import javax.sound.midi.*;

import java.io.*;

/**
 *
 * @author Chris Dennis
 * @author Ian Preston
 */
public class PCSpeaker extends AbstractHardwareComponent implements IOPortCapable
{
    private static final int SPEAKER_SAMPLE_RATE = 22050;
    private static final int SPEAKER_MAX_FREQ = SPEAKER_SAMPLE_RATE >> 1;
    private static final int SPEAKER_MIN_FREQ = 10;
    private static final int SPEAKER_OFF = 0, SPEAKER_ON = 2, SPEAKER_PIT_ON = 3, SPEAKER_PIT_OFF = 1;

    private int dummyRefreshClock, mode;
    private IntervalTimer pit;
    private Clock clock;
    private boolean pitInput, ioportRegistered;
    private SoundDigitalOut soundOut;

    public PCSpeaker(SoundDigitalOut out)
    {
        ioportRegistered = false;
        mode = 0;
        pitInput = true;
        soundOut = out;
        out.addSample(0, (short)32767);
    }

    public void dumpStatusPartial(StatusDumper output)
    {
        super.dumpStatusPartial(output);
        output.println("\tdummyRefreshClock " + dummyRefreshClock + " mode " + mode);
        output.println("\tioportRegistered " + ioportRegistered + " pitInput " + pitInput);
        output.println("\tpit <object #" + output.objectNumber(pit) + ">"); if(pit != null) pit.dumpStatus(output);
        output.println("\tclock <object #" + output.objectNumber(clock) + ">"); if(clock != null) clock.dumpStatus(output);
        output.println("\tsoundOut <object #" + output.objectNumber(soundOut) + ">"); if(soundOut != null) soundOut.dumpStatus(output);
    }

    public void dumpStatus(StatusDumper output)
    {
        if(output.dumped(this))
            return;

        output.println("#" + output.objectNumber(this) + ": PCSpeaker:");
        dumpStatusPartial(output);
        output.endObject();
    }

    public void dumpSRPartial(SRDumper output) throws IOException
    {
        super.dumpSRPartial(output);
        output.dumpInt(dummyRefreshClock);
        output.dumpInt(mode);
        output.dumpObject(pit);
        output.dumpObject(clock);
        output.dumpObject(soundOut);
        output.dumpBoolean(ioportRegistered);
        output.dumpBoolean(pitInput);
    }

    public PCSpeaker(SRLoader input) throws IOException
    {
        super(input);
        dummyRefreshClock = input.loadInt();
        mode = input.loadInt();
        pit = (IntervalTimer)input.loadObject();
        clock = (Clock)input.loadObject();
        soundOut = (SoundDigitalOut)input.loadObject();
        ioportRegistered = input.loadBoolean();
        pitInput = input.loadBoolean();
    }

    public int[] ioPortsRequested()
    {
        return new int[]{0x61};
    }

    public int ioPortReadByte(int address)
    {
        int out = pit.getOut(2);
        dummyRefreshClock ^= 1;
        return mode | (out << 5) | (dummyRefreshClock << 4);
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

    public synchronized void ioPortWriteByte(int address, int data)
    {
        pit.setGate(2, (data & 1) != 0);
        mode = data & 3;
        updateSpeaker();
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

    public void setPITInput(boolean in)
    {
        pitInput = in;
        updateSpeaker();
    }

    private void updateSpeaker()
    {
        boolean line;
        if((mode & 2) == 0)
            line = false;    //Speaker off.
        else if((mode & 1) == 0)
            line = true;     //Speaker on and not following PIT.
        else
            line = pitInput; //Following PIT.
        long time = clock.getTime();
        if(line)
            soundOut.addSample(time, (short)32767);
        else
            soundOut.addSample(time, (short)-32768);
    }

    public boolean initialised()
    {
        return ioportRegistered && (pit != null) && (clock != null);
    }

    public void reset()
    {
        pit = null;
        ioportRegistered = false;
        clock = null;
    }

    public void acceptComponent(HardwareComponent component)
    {
        if ((component instanceof IntervalTimer) &&
            component.initialised()) {
            pit = (IntervalTimer)component;
        }
        if ((component instanceof Clock) &&
            component.initialised()) {
            clock = (Clock)component;
        }
        if ((component instanceof IOPortHandler)
            && component.initialised()) {
            ((IOPortHandler)component).registerIOPortCapable(this);
            ioportRegistered = true;
        }
    }
}
