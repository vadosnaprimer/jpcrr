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

package org.jpc.modulesaux;

import org.jpc.emulator.*;
import org.jpc.output.*;
import java.io.*;
import static org.jpc.modules.SoundCard.TIME_NEVER;

public class FMChip implements SRDumpable
{
    private long timer1ExpiresAt;
    private long timer2ExpiresAt;
    private boolean timer1Masked;
    private boolean timer2Masked;
    private boolean timer1Expired;
    private boolean timer2Expired;
    private boolean timer1Enabled;
    private boolean timer2Enabled;
    private int reg2Value;
    private int reg3Value;
    private OutputChannelFM output;

    private static final int REG1_WAVEFORM_SELECT = 0x20;
    private static final int REG4_IRQ_RESET = 0x80;
    private static final int REG4_TIMER1_MASK = 0x40;
    private static final int REG4_TIMER2_MASK = 0x20;
    private static final int REG4_TIMER2_CONTROL = 0x02;
    private static final int REG4_TIMER1_CONTROL = 0x01;

    public void dumpStatus(StatusDumper output)
    {
        if(output.dumped(this))
            return;

        output.println("#" + output.objectNumber(this) + ": FMChip:");
        dumpStatusPartial(output);
        output.endObject();
    }

    //Read Status register value.
    public int status(long time)
    {
        int value = 0;   //Bit 1 and 2 low for OPL3.
        if(timer1Expired)
            value |= 0xC0;
        if(timer2Expired)
            value |= 0xA0;
        return value;
    }

    public void setOutput(OutputChannelFM out)
    {
        output = out;
    }

    //Compute next time this chip needs attention.
    public long nextAttention(long time)
    {
        long next = TIME_NEVER;
        if(timer1ExpiresAt < next)
            next = timer1ExpiresAt;
        if(timer2ExpiresAt < next)
            next = timer2ExpiresAt;
        return next;
    }

    //Ok, this chip wants attention and gets it.
    public void attention(long time)
    {
        if(timer1ExpiresAt <= time) {
            timer1ExpiresAt = TIME_NEVER;
            if(!timer1Masked)
                timer1Expired = true;
        }
        if(timer2ExpiresAt <= time) {
            timer2ExpiresAt = TIME_NEVER;
            if(!timer2Masked)
                timer2Expired = true;
        }
    }

    private final void computeExpirations(long time, int mask)
    {
        if((mask & 1) != 0) {
            long timer1CyclesNow = time / 80000;
            long timer1CyclesAtExpiry = timer1CyclesNow + (256 - reg2Value);
            if((mask & 4) == 0 || timer1Enabled) {
                timer1ExpiresAt = timer1CyclesAtExpiry * 80000;
                timer1Enabled = true;
            } else
                timer1ExpiresAt = TIME_NEVER;
        }
        if((mask & 2) != 0) {
            long timer2CyclesNow = time / 320000;
            long timer2CyclesAtExpiry = timer2CyclesNow + (256 - reg3Value);
            if((mask & 4) == 0 || timer2Enabled) {
                timer2ExpiresAt = timer2CyclesAtExpiry * 320000;
                timer2Enabled = true;
            } else
                timer2ExpiresAt = TIME_NEVER;
        }
    }

    //Write to register.
    public void write(long timeStamp, int reg, int data)
    {
        int origReg = reg;
        if(reg == 2) {
            reg2Value = data;
        } else if(reg == 3) {
            reg3Value = data;
        } else if(reg == 4) {
             if((data & REG4_IRQ_RESET) != 0) {
                 timer1Expired = false;
                 timer2Expired = false;
                 computeExpirations(timeStamp, 7);
                 return;
             }

             if((data & REG4_TIMER1_MASK) != 0) {
                 timer1Masked = true;
                 timer1Expired = false;
             } else {
                 timer1Masked = false;
             }

             if((data & REG4_TIMER2_MASK) != 0) {
                 timer2Masked = true;
                 timer2Expired = false;
             } else {
                 timer2Masked = false;
             }

             if((data & REG4_TIMER1_CONTROL) != 0) {
                 computeExpirations(timeStamp, 1);
             } else {
                 timer1Enabled = false;
                 timer1ExpiresAt = TIME_NEVER;
             }

             if((data & REG4_TIMER2_CONTROL) != 0) {
                 computeExpirations(timeStamp, 2);
             } else {
                 timer2Enabled = false;
                 timer2ExpiresAt = TIME_NEVER;
             }
        } else {
            //Just dump the raw output data.
            output.addFrameWrite(timeStamp, (short)reg, (byte)data);
        }
    }

    //Reset the chip.
    public void resetCard(long timeStamp)
    {
        timer1Expired = false;
        timer2Expired = false;
        timer1Enabled = false;
        timer2Enabled = false;
        timer1Masked = false;
        timer2Masked = false;
        timer1ExpiresAt = TIME_NEVER;
        timer2ExpiresAt = TIME_NEVER;
        reg2Value = 0;
        reg3Value = 0;
        if(output != null)
            output.addFrameReset(timeStamp);
    }

    //Save instance variables.
    public void dumpSRPartial(SRDumper out) throws IOException
    {
        out.dumpBoolean(timer1Enabled);
        out.dumpBoolean(timer2Enabled);
        out.dumpBoolean(timer1Expired);
        out.dumpBoolean(timer2Expired);
        out.dumpBoolean(timer1Masked);
        out.dumpBoolean(timer2Masked);
        out.dumpLong(timer1ExpiresAt);
        out.dumpLong(timer2ExpiresAt);
        out.dumpInt(reg2Value);
        out.dumpInt(reg3Value);
        out.dumpObject(output);
    }

    //Load instance variables.
    public FMChip(SRLoader input) throws IOException
    {
        input.objectCreated(this);
        timer1Enabled = input.loadBoolean();
        timer2Enabled = input.loadBoolean();
        timer1Expired = input.loadBoolean();
        timer2Expired = input.loadBoolean();
        timer1Masked = input.loadBoolean();
        timer2Masked = input.loadBoolean();
        timer1ExpiresAt = input.loadLong();
        timer2ExpiresAt = input.loadLong();
        reg2Value = input.loadInt();
        reg3Value = input.loadInt();
        output = (OutputChannelFM)input.loadObject();
    }

    //Constructor.
    public FMChip()
    {
        resetCard(0);
    }

    //Dump instance variables.
    public void dumpStatusPartial(StatusDumper out)
    {
        out.println("\tTimer1Enabled " + timer1Enabled);
        out.println("\tTimer2Enabled " + timer2Enabled);
        out.println("\tTimer1Expired " + timer1Expired);
        out.println("\tTimer2Expired " + timer2Expired);
        out.println("\tTimer1Masked " + timer1Masked);
        out.println("\tTimer2Masked " + timer2Masked);
        out.println("\tTimer1ExpiresAt " + timer1ExpiresAt);
        out.println("\tTimer2ExpiresAt " + timer2ExpiresAt);
        out.println("\tReg2Value " + reg2Value);
        out.println("\tReg3Value " + reg3Value);
        out.println("\toutput <object #" + out.objectNumber(output) + ">"); if(output != null) output.dumpStatus(out);
    }
}
