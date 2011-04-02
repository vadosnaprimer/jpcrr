/*
    JPC-RR: A x86 PC Hardware Emulator
    Release 1

    Copyright (C) 2007-2009 Isis Innovation Limited
    Copyright (C) 2009-2011 H. Ilari Liusvaara

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

public class Joystick extends AbstractHardwareComponent implements IOPortCapable, EventDispatchTarget
{
    private boolean ioportRegistered;
    private Clock clock;
    private long[] axisExpiry;

    //Not saved.
    private long[] axisHold;
    private boolean[] button;
    private long[] axisHoldV;
    private boolean[] buttonV;
    private EventRecorder rec;

    private static final String[] CHAN_IDS;
    private static final int INITIAL_POS = 10000;

    static
    {
        CHAN_IDS = new String[4];
        CHAN_IDS[0] = "A";
        CHAN_IDS[1] = "B";
        CHAN_IDS[2] = "C";
        CHAN_IDS[3] = "D";
    }

    public void dumpSRPartial(SRDumper output) throws IOException
    {
        super.dumpSRPartial(output);
        output.dumpBoolean(ioportRegistered);
        output.dumpObject(clock);
        output.dumpArray(axisExpiry);
    }

    private void createArrays()
    {
        axisExpiry = new long[4];
        axisHold = new long[4];
        axisHoldV = new long[4];
        button = new boolean[4];
        buttonV = new boolean[4];
        for(int i = 0; i < 4; i++)
            axisHold[i] = axisHoldV[i] = INITIAL_POS;
    }

    public Joystick(SRLoader input) throws IOException
    {
        super(input);
        ioportRegistered = input.loadBoolean();
        clock = (Clock)input.loadObject();
        createArrays();
        axisExpiry = input.loadArrayLong();
    }

    public Joystick() throws IOException
    {
        createArrays();
    }

    public void dumpStatusPartial(StatusDumper output)
    {
        super.dumpStatusPartial(output);
        output.println("\tioportRegistered " + ioportRegistered);
        output.println("\tclock <object #" + output.objectNumber(clock) + ">"); if(clock != null) clock.dumpStatus(output);
        output.printArray(axisExpiry, "\taxisExpiry");
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
        if((component instanceof Clock) && component.initialised())
            clock = (Clock)component;

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
            for(int i = 0; i < 4; i++)
                axisExpiry[i] = time + axisHold[i];
        }
    }

    public boolean buttonState(int index, boolean inputEdge)
    {
        if(inputEdge)
            return buttonV[index];
        else
            return button[index];
    }

    public long axisHoldTime(int index, boolean inputEdge)
    {
        if(inputEdge)
            return axisHoldV[index];
        else
            return axisHold[index];
    }

    public void setButton(int index, boolean state) throws IOException
    {
        String second = "0";
        if(state)
            second = "1";
        rec.addEvent(0, getClass(), new String[]{"BUTTON" + CHAN_IDS[index], second});
        buttonV[index] = state;
    }

    public void setAxis(int index, long hold) throws IOException
    {
        rec.addEvent(0L, getClass(), new String[]{"AXIS" + CHAN_IDS[index], "" + hold});
        axisHoldV[index] = hold;
    }

    public int ioPortReadByte(int address)
    {
        if(address == 0x201) {
            int value = 0xF0;
            long time = clock.getTime();
            for(int i = 0; i < 4; i++) {
                if(button[i])
                    value &= ~(1 << (4 + i));
                if(time < axisExpiry[i])
                    value |= (1 << i);
            }
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
        for(int i = 0; i < 4; i++) {
            axisHold[i] = axisHoldV[i] = INITIAL_POS;
            button[i] = buttonV[i] = false;
        }
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
            case 1:
            case 2:
            case 3:
                axisHold[type] = value;
                break;
            case 4:
            case 5:
            case 6:
            case 7:
                button[type - 4] = (value != 0);
                break;
            }
        }
        if(level == EventRecorder.EVENT_STATE_EFFECT || level == EventRecorder.EVENT_STATE_EFFECT_FUTURE) {
            switch(type) {
            case 0:
            case 1:
            case 2:
            case 3:
                axisHoldV[type] = value;
                break;
            case 4:
            case 5:
            case 6:
            case 7:
                buttonV[type - 4] = (value != 0);
                break;
            }
        }
    }
}
