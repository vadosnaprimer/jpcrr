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

import org.jpc.support.*;
import org.jpc.emulator.*;
import java.io.*;

/**
 * i8254 Interval Timer emulation.
 */
public class IntervalTimer implements IOPortCapable, HardwareComponent
{
    private static final int RW_STATE_LSB = 1;
    private static final int RW_STATE_MSB = 2;
    private static final int RW_STATE_WORD = 3;
    private static final int RW_STATE_WORD_2 = 4;

    private static final int MODE_INTERRUPT_ON_TERMINAL_COUNT = 0;
    private static final int MODE_HARDWARE_RETRIGGERABLE_ONE_SHOT = 1;
    private static final int MODE_RATE_GENERATOR = 2;
    private static final int MODE_SQUARE_WAVE = 3;
    private static final int MODE_SOFTWARE_TRIGGERED_STROBE = 4;
    private static final int MODE_HARDWARE_TRIGGERED_STROBE = 5;

    private static final int PIT_FREQ = 1193182;

    private TimerChannel[] channels;

    private InterruptController irqDevice;
    private Clock timingSource;

    private boolean madeNewTimer;
    private boolean ioportRegistered;
    private int ioPortBase;
    private int irq;
    private Magic magic;

    static final long scale64(long input, int multiply, int divide)
    {
        //return (BigInteger.valueOf(input).multiply(BigInteger.valueOf(multiply)).divide(BigInteger.valueOf(divide))).longValue();

        long rl = (0xffffffffl & input) * multiply;
        long rh = (input >>> 32) * multiply;

        rh += (rl >> 32);

        long resultHigh = 0xffffffffl & (rh / divide);
        long resultLow = 0xffffffffl & ((((rh % divide) << 32) + (rl & 0xffffffffl)) / divide);

        return (resultHigh << 32) | resultLow;
    }

    public void dumpState(DataOutput output) throws IOException
    {
        magic.dumpState(output);
        output.writeInt(channels.length);
        for (int i =0; i < channels.length; i++)
            channels[i].dumpState(output);
    }

    public void loadState(DataInput input) throws IOException
    {
        magic.loadState(input);
        madeNewTimer = false;
        ioportRegistered = false;
        int len = input.readInt();
        channels = new TimerChannel[len];
        for (int i =0; i < channels.length; i++)
        {
            //if (i>10) System.exit(0);
            channels[i] = new TimerChannel(i);
            channels[i].loadState(input);
        }
    }

    public IntervalTimer(int ioPort, int irq)
    {
        magic = new Magic(Magic.INTERVAL_TIMER_MAGIC_V1);
        this.irq = irq;
        ioPortBase = ioPort;
    }

    public int[] ioPortsRequested()
    {
        return new int[]{ioPortBase, ioPortBase + 1, ioPortBase + 2, ioPortBase + 3};
    }
    public int ioPortReadLong(int address)
    {
        return (ioPortReadWord(address) & 0xffff) |
            ((ioPortReadWord(address + 2) << 16) & 0xffff0000);
    }
    public int ioPortReadWord(int address)
    {
        return (ioPortReadByte(address) & 0xff) |
            ((ioPortReadByte(address + 1) << 8) & 0xff00);
    }
    public int ioPortReadByte(int address)
    {
        return channels[address & 0x3].read();
    }
    public void ioPortWriteLong(int address, int data)
    {
        this.ioPortWriteWord(address, 0xffff & data);
        this.ioPortWriteWord(address + 2, 0xffff & (data >>> 16));
    }
    public void ioPortWriteWord(int address, int data)
    {
        this.ioPortWriteByte(address, 0xff & data);
        this.ioPortWriteByte(address + 1, 0xff & (data >>> 8));
    }
    public void ioPortWriteByte(int address, int data)
    {
        data &= 0xff;
        address &= 0x3;
        if(address == 3) { //writing control word
            int channel = data >>> 6;
            if (channel == 3) { // read back command
                for(channel = 0; channel < 3; channel++) {
                    if(0 != (data & (2 << channel))) // if channel enabled
                        channels[channel].readBack(data);
                }
            } else
                channels[channel].writeControlWord(data);
        } else //writing to a channels counter
            channels[address].write(data);
    }

    public void reset()
    {
        irqDevice = null;
        timingSource = null;
        ioportRegistered = false;
    }

    public int getOut(int channel, long currentTime)
    {
        return channels[channel].getOut(currentTime);
    }

    public boolean getGate(int channel)
    {
        return channels[channel].gate;
    }

    public void setGate(int channel, boolean value)
    {
        channels[channel].setGate(value);
    }

    private InterruptController getInterruptController() { return irqDevice; }
    private Clock getTimingSource() { return timingSource; }

    class TimerChannel implements HardwareComponent
    {
        private int countValue;

        private int outputLatch;
        private int inputLatch;

        private int countLatched;
        private boolean statusLatched;
        private boolean nullCount;

        private boolean gate;

        private int status;
        private int readState;
        private int writeState;
        private int rwMode;
        private int mode;
        private int bcd; /* not supported */

        private long countStartTime;
        /* irq handling */
        private long nextTransitionTimeValue;
        private Timer irqTimer;
        private int irq;
        private Magic magic2;

        public TimerChannel(int index)
        {
            magic2 = new Magic(Magic.TIMER_CHANNEL_MAGIC_V1);
            reset(index);
        }

        public void dumpState(DataOutput output) throws IOException
        {
            magic2.dumpState(output);
            output.writeInt(countValue);
            output.writeInt(outputLatch);
            output.writeInt(inputLatch);
            output.writeInt(countLatched);
            output.writeBoolean(statusLatched);
            output.writeBoolean(gate);
            output.writeInt(status);
            output.writeInt(readState);
            output.writeInt(writeState);
            output.writeInt(rwMode);
            output.writeInt(mode);
            output.writeInt(bcd);
            output.writeLong(countStartTime);
            output.writeLong(nextTransitionTimeValue);
            if (irqTimer == null)
                output.writeInt(0);
            else
            {
                output.writeInt(1);
                irqTimer.dumpState(output);
            }
        }

        public void loadState(DataInput input) throws IOException
        {
            magic2.loadState(input);
            countValue = input.readInt();
            outputLatch = input.readInt();
            inputLatch = input.readInt();
            countLatched = input.readInt();
            statusLatched = input.readBoolean();
            gate = input.readBoolean();
            status = input.readInt();
            readState = input.readInt();
            writeState = input.readInt();
            rwMode = input.readInt();
            mode = input.readInt();
            bcd = input.readInt();
            countStartTime = input.readLong();
            nextTransitionTimeValue = input.readLong();
            int test = input.readInt();
            if(test == 1)
            {
                irqTimer = timingSource.newTimer(this);
                irqTimer.loadState(input);
            }
        }

        public boolean updated() {return true;}

        public void updateComponent(HardwareComponent component) {}

        public int read()
        {
            if (statusLatched) {
                statusLatched = false;
                return status;
            }

            switch (countLatched) {
            case RW_STATE_LSB:
                countLatched = 0;
                return outputLatch & 0xff;
            case RW_STATE_MSB:
                countLatched = 0;
                return (outputLatch >>> 8) & 0xff;
            case RW_STATE_WORD:
                countLatched = RW_STATE_WORD_2;
                return outputLatch & 0xff;
            case RW_STATE_WORD_2:
                countLatched = 0;
                return (outputLatch >>> 8) & 0xff;
            default:
            case 0: //not latched
                switch(readState) {
                default:
                case RW_STATE_LSB:
                    return getCount() & 0xff;
                case RW_STATE_MSB:
                    return (getCount() >>> 8) & 0xff;
                case RW_STATE_WORD:
                    readState = RW_STATE_WORD_2;
                    return getCount() & 0xff;
                case RW_STATE_WORD_2:
                    readState = RW_STATE_WORD;
                    return (getCount() >>> 8) & 0xff;
                }
            }
        }

        public void readBack(int data)
        {
            if (0 == (data & 0x20)) //latch counter value
                latchCount();

            if (0 == (data & 0x10)) //latch counter value
                latchStatus();
        }

        private void latchCount()
        {
            if (0 != countLatched) {
                outputLatch = this.getCount();
                countLatched = rwMode;
            }
        }

        private void latchStatus()
        {
            if (!statusLatched) {

                status = ((getOut(IntervalTimer.this.getTimingSource().getTime()) << 7) | (nullCount ? 0x40 : 0x00) | (rwMode << 4) | (mode << 1) | bcd);
                statusLatched = true;
            }
        }

        public void write(int data)
        {
            switch(writeState) {
            default:
            case RW_STATE_LSB:
                nullCount = true;
                loadCount(0xff & data);
                break;
            case RW_STATE_MSB:
                nullCount = true;
                loadCount((0xff & data) << 8);
                break;
            case RW_STATE_WORD:
                //null count setting is delayed until after second byte is written
                inputLatch = data;
                writeState = RW_STATE_WORD_2;
                break;
            case RW_STATE_WORD_2:
                nullCount = true;
                loadCount((0xff & inputLatch) | ((0xff & data) << 8));
                writeState = RW_STATE_WORD;
                break;
            }
        }

        public void writeControlWord(int data)
        {
            int access = (data >>> 4) & 3;

            if (access == 0) {
                latchCount(); //counter latch command
            } else {
                nullCount = true;

                rwMode = access;
                readState = access;
                writeState = access;

                mode = (data >>> 1) & 7;
                bcd = (data & 1);
                /* XXX: update irq timer ? */
            }
        }

        public void setGate(boolean value)
        {
            switch(mode) {
            default:
            case MODE_INTERRUPT_ON_TERMINAL_COUNT:
            case MODE_SOFTWARE_TRIGGERED_STROBE:
                /* XXX: just disable/enable counting */
                break;
            case MODE_HARDWARE_RETRIGGERABLE_ONE_SHOT:
            case MODE_HARDWARE_TRIGGERED_STROBE:
                if (!gate && value) {
                    /* restart counting on rising edge */
                    countStartTime = IntervalTimer.this.getTimingSource().getTime();
                    irqTimerUpdate(countStartTime);
                }
                break;
            case MODE_RATE_GENERATOR:
            case MODE_SQUARE_WAVE:
                if (!gate && value) {
                    /* restart counting on rising edge */
                    countStartTime = IntervalTimer.this.getTimingSource().getTime();
                    irqTimerUpdate(countStartTime);
                }
                /* XXX: disable/enable counting */
                break;
            }
            this.gate = value;
        }

        private int getCount()
        {
            long now = scale64(IntervalTimer.this.getTimingSource().getTime() - countStartTime, PIT_FREQ, (int)IntervalTimer.this.getTimingSource().getTickRate());

            switch(mode) {
            case MODE_INTERRUPT_ON_TERMINAL_COUNT:
            case MODE_HARDWARE_RETRIGGERABLE_ONE_SHOT:
            case MODE_SOFTWARE_TRIGGERED_STROBE:
            case MODE_HARDWARE_TRIGGERED_STROBE:
                return (int)((countValue - now) & 0xffffl);
            case MODE_SQUARE_WAVE:
                return (int)(countValue - ((2 * now) % countValue));
            case MODE_RATE_GENERATOR:
            default:
                return (int)(countValue - (now % countValue));
            }
        }
        private int getOut(long currentTime)
        {
            long now = scale64(currentTime - countStartTime, PIT_FREQ, (int)IntervalTimer.this.getTimingSource().getTickRate());
            switch(mode) {
            default:
            case MODE_INTERRUPT_ON_TERMINAL_COUNT:
                if (now >= countValue)
                    return 1;
                else
                    return 0;
            case MODE_HARDWARE_RETRIGGERABLE_ONE_SHOT:
                if (now < countValue)
                    return 1;
                else
                    return 0;
            case MODE_RATE_GENERATOR:
                if (((now % countValue) == 0) && (now != 0)) {
                    return 1;
                } else
                    return 0;
            case MODE_SQUARE_WAVE:
                if ((now % countValue) < ((countValue + 1) >>> 1))
                    return 1;
                else
                    return 0;
            case MODE_SOFTWARE_TRIGGERED_STROBE:
            case MODE_HARDWARE_TRIGGERED_STROBE:
                if (now == countValue)
                    return 1;
                else
                    return 0;
            }
        }

        private long getNextTransitionTime(long currentTime)
        {
            long nextTime;

            long now = scale64(currentTime - countStartTime, PIT_FREQ, (int)IntervalTimer.this.getTimingSource().getTickRate());
            switch(mode) {
            default:
            case MODE_INTERRUPT_ON_TERMINAL_COUNT:
            case MODE_HARDWARE_RETRIGGERABLE_ONE_SHOT:
                {
                    if (now < countValue)
                        nextTime = countValue;
                    else
                        return -1;
                }
                break;
            case MODE_RATE_GENERATOR:
                {
                    long base = (now / countValue) * countValue;
                    if ((now - base) == 0 && now != 0)
                        nextTime = base + countValue;
                    else
                        nextTime = base + countValue + 1;
                }
                break;
            case MODE_SQUARE_WAVE:
                {
                    long base = (now / countValue) * countValue;
                    long period2 = ((countValue + 1) >>> 1);
                    if ((now - base) < period2)
                        nextTime = base + period2;
                    else
                        nextTime = base + countValue;
                }
                break;
            case MODE_SOFTWARE_TRIGGERED_STROBE:
            case MODE_HARDWARE_TRIGGERED_STROBE:
                {
                    if (now < countValue)
                        nextTime = countValue;
                    else if (now == countValue)
                        nextTime = countValue + 1;
                    else
                        return -1;
                }
                break;
            }
            /* convert to timer units */
            nextTime = countStartTime + scale64(nextTime, (int)IntervalTimer.this.getTimingSource().getTickRate(), PIT_FREQ);

            /* fix potential rounding problems */
            /* XXX: better solution: use a clock at PIT_FREQ Hz */
            if (nextTime <= currentTime)
                nextTime = currentTime + 1;
            return nextTime;
        }


        private void loadCount(int value)
        {
            nullCount = false;
            if (value == 0)
                value = 0x10000;
            countStartTime = IntervalTimer.this.getTimingSource().getTime();
            countValue = value;
            this.irqTimerUpdate(countStartTime);
        }

        private void irqTimerUpdate(long currentTime)
        {
            if (irqTimer == null)
                return;
            long expireTime = this.getNextTransitionTime(currentTime);
            int irqLevel = this.getOut(currentTime);
            IntervalTimer.this.getInterruptController().setIRQ(irq, irqLevel);
            nextTransitionTimeValue = expireTime;
            if (expireTime != -1)
                irqTimer.setExpiry(expireTime);
            else
                irqTimer.setStatus(false);
        }

        public void timerCallback()
        {
            this.irqTimerUpdate(nextTransitionTimeValue);
        }

        public void reset(int i)
        {
            mode = MODE_SQUARE_WAVE;
            gate = (i != 2);
            loadCount(0);
            nullCount = true;
        }

        private void setIRQTimer(Timer object)
        { irqTimer = object; }

        public void setIRQ(int irq)
        { this.irq = irq; }

        public boolean initialised() {return true;}
        public void acceptComponent(HardwareComponent component){}
        public void reset(){}
    }

    public boolean initialised()
    {
        return ((irqDevice != null) && (timingSource != null))
            && ioportRegistered;
    }

    public boolean updated()
    {
        return (irqDevice.updated() && timingSource.updated()) && ioportRegistered;
    }

    public void updateComponent(HardwareComponent component)
    {
        if (component instanceof IOPortHandler)
        {
            ((IOPortHandler)component).registerIOPortCapable(this);
            ioportRegistered = true;
        }
        if (this.updated() && !madeNewTimer)
        {
            channels[0].setIRQTimer(timingSource.newTimer(channels[0]));
            madeNewTimer = true;
        }
    }

    public void acceptComponent(HardwareComponent component)
    {
        if ((component instanceof InterruptController)
            && component.initialised())
            irqDevice = (InterruptController)component;
        if ((component instanceof Clock)
            && component.initialised())
            timingSource = (Clock)component;

        if ((component instanceof IOPortHandler)
            && component.initialised()) {
            ((IOPortHandler)component).registerIOPortCapable(this);
            ioportRegistered = true;
        }

        if (this.initialised() && (channels == null)) {
            channels = new TimerChannel[3];
            for (int i = 0; i < channels.length; i++) {
                channels[i] = new TimerChannel(i);
            }
            channels[0].setIRQTimer(timingSource.newTimer(channels[0]));
            channels[0].setIRQ(irq);
        }
    }

    public void timerCallback() {}

    public String toString()
    {
        return "Intel i8254 Interval Timer";
    }
}

