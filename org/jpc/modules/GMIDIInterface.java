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
import org.jpc.output.*;
import org.jpc.modulesaux.*;
import org.jpc.emulator.motherboard.*;
import java.io.*;

public class GMIDIInterface  extends AbstractHardwareComponent implements IOPortCapable, SoundOutputDevice,
    TimerResponsive
{
    private int baseIOAddress;
    private int irq;
    private boolean byteWaiting;
    private Clock clock;
    private Timer offlineTimer;
    private InterruptController irqController;
    private OutputChannelGMIDI midiOutput;
    private boolean ioportRegistered;
    private boolean gmidiDebuggingEnabled;    //Not saved.

    public String STATUS_MIDI_UART;

    private byte readStatus()
    {
        if(gmidiDebuggingEnabled)
            System.err.println("Debug: GMIDI: Status register read (bytewaiting=" + byteWaiting + ").");
        return (byte)(byteWaiting ? 0x3f : 0xbf);
    }

    private byte readData()
    {
        if(gmidiDebuggingEnabled)
            System.err.println("Debug: GMIDI: Data register read (returning ACK).");
        irqController.setIRQ(irq, 0);
        byteWaiting = false;
        return (byte)0xFE;
    }

    private void writeCommand(byte cmd)
    {
        if(cmd == (byte)0xFF) {
            //RESET.
            if(gmidiDebuggingEnabled)
                System.err.println("Debug: GMIDI: RESET command received.");
            irqController.setIRQ(irq, 1);
            byteWaiting = true;
        } else if(cmd == 0x3F) {
            if(gmidiDebuggingEnabled)
                System.err.println("Debug: GMIDI: Switch to UART mode command received.");
            //Some games expect ACK for switch to UART mode?
            irqController.setIRQ(irq, 1);
            byteWaiting = true;
        } else {
            if(gmidiDebuggingEnabled)
                System.err.println("Debug: GMIDI: Unknown command (" + cmd + ") received.");
        }
    }

    private void writeData(byte data)
    {
        if(gmidiDebuggingEnabled)
            System.err.println("Debug: GMIDI: Written data (" + data + ") received.");
        midiOutput.addFrameData(clock.getTime(), data);
        STATUS_MIDI_UART = "online";
        offlineTimer.setExpiry(clock.getTime() + 200000000);
    }

    public void ioPortWriteWord(int address, int data)
    {
        ioPortWriteByte(address, data & 0xFF);
        ioPortWriteByte(address, (data >>> 8) & 0xFF);
    }

    public void ioPortWriteLong(int address, int data)
    {
        ioPortWriteByte(address, data & 0xFF);
        ioPortWriteByte(address, (data >>> 8) & 0xFF);
        ioPortWriteByte(address, (data >>> 16) & 0xFF);
        ioPortWriteByte(address, (data >>> 24) & 0xFF);
    }

    public int ioPortReadWord(int address)
    {
        return (ioPortReadByte(address) | (ioPortReadByte(address) << 8));
    }

    public int ioPortReadLong(int address)
    {
        return ioPortReadByte(address) | (ioPortReadByte(address) << 8) |
            (ioPortReadByte(address) << 16) | (ioPortReadByte(address) << 24);
    }

    public void ioPortWriteByte(int address, int data)
    {
        if(address == baseIOAddress)
            writeData((byte)data);
        else if(address == baseIOAddress + 1)
            writeCommand((byte)data);
        else
            System.err.println("Emulated: GMIDI: Write to unhandled port (offset=" + (address - baseIOAddress) + ").");
    }

    public int ioPortReadByte(int address)
    {
        if(address == baseIOAddress)
            return readData();
        else if(address == baseIOAddress + 1)
            return readStatus();
        else {
            System.err.println("Emulated: GMIDI: Read from unhandled port (offset=" + (address - baseIOAddress) + ").");
            return 0xFF;
        }
    }

    public int requestedSoundChannels()
    {
        return 1;
    }

    public void reset()
    {
        ioportRegistered = false;
    }

    public void dumpSRPartial(SRDumper output) throws IOException
    {
        super.dumpSRPartial(output);
        output.dumpInt(baseIOAddress);
        output.dumpInt(irq);
        output.dumpBoolean(byteWaiting);
        output.dumpObject(clock);
        output.dumpObject(irqController);
        output.dumpObject(midiOutput);
        output.dumpBoolean(ioportRegistered);
        output.dumpObject(offlineTimer);
        output.dumpString(STATUS_MIDI_UART);
    }

    public GMIDIInterface(SRLoader input) throws IOException
    {
        super(input);
        baseIOAddress = input.loadInt();
        irq = input.loadInt();
        byteWaiting = input.loadBoolean();
        clock = (Clock)input.loadObject();
        irqController = (InterruptController)input.loadObject();
        midiOutput = (OutputChannelGMIDI)input.loadObject();
        ioportRegistered = input.loadBoolean();
        if(!input.objectEndsHere()) {
            offlineTimer = (Timer)input.loadObject();
            STATUS_MIDI_UART = input.loadString();
        } else {
            offlineTimer = clock.newTimer(this);
            STATUS_MIDI_UART = "offline";
        }
    }

    public GMIDIInterface(String parameters) throws IOException
    {
        char mode = 0;
        int tmp = 0;
        irq = 9;
        baseIOAddress = 0x330;
        byteWaiting = false;
        ioportRegistered = false;
        gmidiDebuggingEnabled = false;
        STATUS_MIDI_UART = "offline";

        for(int i = 0; i < parameters.length() + 1; i++) {
            char ch = 0;
            if(i < parameters.length())
                ch = parameters.charAt(i);

            if(ch >= '0' && ch <= '9' && mode != 0) {
                tmp = tmp * 10 + (ch - '0');
                continue;
            } else if(ch >= '0' && ch <= '9' && mode == 0)
                throw new IOException("GMIDIInterface: Invalid spec '" + parameters + "'.");
            if(mode == 'A')
                if(tmp > 65534)
                    throw new IOException("GMIDIInterface: Bad I/O port " + tmp + ".");
                else
                    baseIOAddress = tmp;
            else if(mode == 'I')
                if(tmp > 15)
                    throw new IOException("GMIDIInterface: Bad IRQ " + tmp + ".");
                else
                    irq = tmp;
            else if(mode > 0 || i > 0)
                throw new IOException("Soundcard: Invalid setting type '" + mode + "'.");
            if(ch == 0)
                break;
            mode = ch;
            tmp = 0;
        }
    }

    public GMIDIInterface() throws IOException
    {
        this("");
    }

    public void dumpStatusPartial(StatusDumper output)
    {
        super.dumpStatusPartial(output);
        output.println("\tbaseIOAddress " + baseIOAddress + " irq " + irq + " byteWaiting " + byteWaiting);
        output.println("\tioportRegistered " + ioportRegistered + " STATUS_MIDI_UART " + STATUS_MIDI_UART);
        output.println("\tmidiOutput <object #" + output.objectNumber(midiOutput) + ">"); if(midiOutput != null) midiOutput.dumpStatus(output);
        output.println("\tirqController <object #" + output.objectNumber(irqController) + ">"); if(irqController != null) irqController.dumpStatus(output);
        output.println("\tclock <object #" + output.objectNumber(clock) + ">"); if(clock != null) clock.dumpStatus(output);
        output.println("\tofflineTimer <object #" + output.objectNumber(offlineTimer) + ">"); if(offlineTimer != null) offlineTimer.dumpStatus(output);
    }

    public void dumpStatus(StatusDumper output)
    {
        if(output.dumped(this))
            return;

        output.println("#" + output.objectNumber(this) + ": GMIDIInterface:");
        dumpStatusPartial(output);
        output.endObject();
    }

    public void DEBUGOPTION_gmidi_transfer_debugging(boolean _state)
    {
        gmidiDebuggingEnabled = _state;
    }

    public void soundChannelCallback(Output out, String name)
    {
        midiOutput = new OutputChannelGMIDI(out, name);
    }

    public int[] ioPortsRequested()
    {
        int[] ret;
        ret = new int[2];
        for(int i = 0; i < 2; i++)
            ret[i] = baseIOAddress + i;
        return ret;
    }

    public boolean initialised()
    {
        return ((irqController != null) && (clock != null) && ioportRegistered);
    }

    public void acceptComponent(HardwareComponent component)
    {
        if((component instanceof InterruptController) && component.initialised())
            irqController = (InterruptController)component;
        if((component instanceof Clock) && component.initialised()) {
            clock = (Clock)component;
            offlineTimer = clock.newTimer(this);
        }
        if((component instanceof IOPortHandler) && component.initialised() && !ioportRegistered) {
            ((IOPortHandler)component).registerIOPortCapable(this);
            ioportRegistered = true;
        }
    }

    public int getTimerType()
    {
        return 63;
    }

    public void callback()
    {
        STATUS_MIDI_UART = "offline";
    }
}
