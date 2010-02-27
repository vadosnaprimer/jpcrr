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

public class Joystick extends AbstractHardwareComponent implements IOPortCapable, EventDispatchTarget
{
    private boolean ioportRegistered;
    private Clock clock;
    private long axisAExpiry;
    private long axisBExpiry;
    private long axisCExpiry;
    private long axisDExpiry;

    //Not saved.
    private long axisAHold;
    private long axisBHold;
    private long axisCHold;
    private long axisDHold;
    private boolean buttonA;
    private boolean buttonB;
    private boolean buttonC;
    private boolean buttonD;
    private long axisAHoldV;
    private long axisBHoldV;
    private long axisCHoldV;
    private long axisDHoldV;
    private boolean buttonAV;
    private boolean buttonBV;
    private boolean buttonCV;
    private boolean buttonDV;
    private EventRecorder rec;

    private static final int INITIAL_POS = 10000;

    public void dumpSRPartial(SRDumper output) throws IOException
    {
        super.dumpSRPartial(output);
        output.dumpBoolean(ioportRegistered);
        output.dumpObject(clock);
        output.dumpLong(axisAExpiry);
        output.dumpLong(axisBExpiry);
        output.dumpLong(axisCExpiry);
        output.dumpLong(axisDExpiry);
    }

    public Joystick(SRLoader input) throws IOException
    {
        super(input);
        ioportRegistered = input.loadBoolean();
        clock = (Clock)input.loadObject();
        axisAExpiry = input.loadLong();
        axisBExpiry = input.loadLong();
        axisCExpiry = input.loadLong();
        axisDExpiry = input.loadLong();
    }

    public Joystick() throws IOException
    {
        axisAHold = INITIAL_POS;
        axisBHold = INITIAL_POS;
        axisCHold = INITIAL_POS;
        axisDHold = INITIAL_POS;
        axisAHoldV = INITIAL_POS;
        axisBHoldV = INITIAL_POS;
        axisCHoldV = INITIAL_POS;
        axisDHoldV = INITIAL_POS;
    }

    public void dumpStatusPartial(StatusDumper output)
    {
        super.dumpStatusPartial(output);
        output.println("\tioportRegistered " + ioportRegistered);
        output.println("\tclock <object #" + output.objectNumber(clock) + ">"); if(clock != null) clock.dumpStatus(output);
        output.println("\taxisAExpiry " + axisAExpiry);
        output.println("\taxisBExpiry " + axisBExpiry);
        output.println("\taxisCExpiry " + axisCExpiry);
        output.println("\taxisDExpiry " + axisDExpiry);
    }

    public void dumpStatus(StatusDumper output)
    {
        if(output.dumped(this))
            return;

        output.println("#" + output.objectNumber(this) + ": Joystick:");
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
        int[] ret = new int[1];
        ret[0] = 0x201;
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
        if(address == 0x201) {
            long time = clock.getTime();
            axisAExpiry = time + axisAHold;
            axisBExpiry = time + axisBHold;
            axisCExpiry = time + axisCHold;
            axisDExpiry = time + axisDHold;
        }
    }

    public boolean buttonAState()
    {
        return buttonA;
    }

    public boolean buttonBState()
    {
        return buttonB;
    }

    public boolean buttonCState()
    {
        return buttonC;
    }

    public boolean buttonDState()
    {
        return buttonD;
    }

    public long axisAHoldTime()
    {
        return axisAHold;
    }

    public long axisBHoldTime()
    {
        return axisBHold;
    }

    public long axisCHoldTime()
    {
        return axisCHold;
    }

    public long axisDHoldTime()
    {
        return axisDHold;
    }

    public boolean buttonAStateV()
    {
        return buttonAV;
    }

    public boolean buttonBStateV()
    {
        return buttonBV;
    }

    public boolean buttonCStateV()
    {
        return buttonCV;
    }

    public boolean buttonDStateV()
    {
        return buttonDV;
    }

    public long axisAHoldTimeV()
    {
        return axisAHoldV;
    }

    public long axisBHoldTimeV()
    {
        return axisBHoldV;
    }

    public long axisCHoldTimeV()
    {
        return axisCHoldV;
    }

    public long axisDHoldTimeV()
    {
        return axisDHoldV;
    }

    public void setButtonA(boolean state) throws IOException
    {
        String second = "0";
        if(state)
            second = "1";
        rec.addEvent(0, getClass(), new String[]{"BUTTONA", second});
        buttonAV = state;
    }

    public void setButtonB(boolean state) throws IOException
    {
        String second = "0";
        if(state)
            second = "1";
        rec.addEvent(0L, getClass(), new String[]{"BUTTONB", second});
        buttonBV = state;
    }

    public void setButtonC(boolean state) throws IOException
    {
        String second = "0";
        if(state)
            second = "1";
        rec.addEvent(0L, getClass(), new String[]{"BUTTONC", second});
        buttonCV = state;
    }

    public void setButtonD(boolean state) throws IOException
    {
        String second = "0";
        if(state)
            second = "1";
        rec.addEvent(0L, getClass(), new String[]{"BUTTOND", second});
        buttonDV = state;
    }

    public void setAxisA(long hold) throws IOException
    {
        rec.addEvent(0L, getClass(), new String[]{"AXISA", "" + hold});
        axisAHoldV = hold;
    }

    public void setAxisB(long hold) throws IOException
    {
        rec.addEvent(0L, getClass(), new String[]{"AXISB", "" + hold});
        axisBHoldV = hold;
    }

    public void setAxisC(long hold) throws IOException
    {
        rec.addEvent(0L, getClass(), new String[]{"AXISC", "" + hold});
        axisCHoldV = hold;
    }

    public void setAxisD(long hold) throws IOException
    {
        rec.addEvent(0L, getClass(), new String[]{"AXISD", "" + hold});
        axisDHoldV = hold;
    }

    public int ioPortReadByte(int address)
    {
        if(address == 0x201) {
            int value = 0xF0;
            long time = clock.getTime();
            if(buttonA)
                value &= ~0x10;
            if(buttonB)
                value &= ~0x20;
            if(buttonC)
                value &= ~0x40;
            if(buttonD)
                value &= ~0x80;
            if(time < axisAExpiry)
                value |= 0x01;
            if(time < axisBExpiry)
                value |= 0x02;
            if(time < axisCExpiry)
                value |= 0x04;
            if(time < axisDExpiry)
                value |= 0x08;
            return value;
        } else
            return -1;
    }

    public void reset()
    {
        //Nothing to do.
    }

    public void setEventRecorder(EventRecorder recorder)
    {
        rec = recorder;
    }

    public void startEventCheck()
    {
        axisAHold = 10000;
        axisBHold = 10000;
        axisCHold = 10000;
        axisDHold = 10000;
        buttonA = false;
        buttonB = false;
        buttonC = false;
        buttonD = false;

        axisAHoldV = 10000;
        axisBHoldV = 10000;
        axisCHoldV = 10000;
        axisDHoldV = 10000;
        buttonAV = false;
        buttonBV = false;
        buttonCV = false;
        buttonDV = false;
    }

    public void endEventCheck() throws IOException
    {
        //Nothing to do.
    }

    public long getEventTimeLowBound(long stamp, String[] args) throws IOException
    {
        return -1;  //No constraints.
    }

    public void doEvent(long timeStamp, String[] args, int level) throws IOException
    {
        int type = -1;
        long value = 0;

        if(args == null || args.length != 2) {
            throw new IOException("Joystick events must have two elements");
        }
        try {
            value = Long.parseLong(args[1]);
        } catch(Exception e) {
            throw new IOException("Can't parse numeric argument to joystick event");
        }

        if("AXISA".equals(args[0]))
            type = 0;
        else if("AXISB".equals(args[0]))
            type = 1;
        else if("AXISC".equals(args[0]))
            type = 2;
        else if("AXISD".equals(args[0]))
            type = 3;
        else if("BUTTONA".equals(args[0]))
            type = 4;
        else if("BUTTONB".equals(args[0]))
            type = 5;
        else if("BUTTONC".equals(args[0]))
            type = 6;
        else if("BUTTOND".equals(args[0]))
            type = 7;
        else
            throw new IOException("Bad joystick event type '" + args[0] + "'");

        if((type & 4) != 0) {
            if(value != 0 && value != 1)
                throw new IOException("Bad joystick event value '" + args[1] + "' for button event");
        } else {
            if(value < 0)
                throw new IOException("Bad joystick event value '" + args[1] + "' for axis event");
        }

        if(level == EventRecorder.EVENT_STATE_EFFECT || level == EventRecorder.EVENT_EXECUTE) {
            switch(type) {
            case 0:
                axisAHold = value;
                break;
            case 1:
                axisBHold = value;
                break;
            case 2:
                axisCHold = value;
                break;
            case 3:
                axisDHold = value;
                break;
            case 4:
                buttonA = (value != 0);
                break;
            case 5:
                buttonB = (value != 0);
                break;
            case 6:
                buttonC = (value != 0);
                break;
            case 7:
                buttonD = (value != 0);
                break;
            }
        }
        if(level == EventRecorder.EVENT_STATE_EFFECT || level == EventRecorder.EVENT_STATE_EFFECT_FUTURE) {
            switch(type) {
            case 0:
                axisAHoldV = value;
                break;
            case 1:
                axisBHoldV = value;
                break;
            case 2:
                axisCHoldV = value;
                break;
            case 3:
                axisDHoldV = value;
                break;
            case 4:
                buttonAV = (value != 0);
                break;
            case 5:
                buttonBV = (value != 0);
                break;
            case 6:
                buttonCV = (value != 0);
                break;
            case 7:
                buttonDV = (value != 0);
                break;
            }
        }
    }
}
