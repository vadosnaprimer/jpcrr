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

package org.jpc;

import org.jpc.emulator.*;
import java.io.*;

public class SoundOutTest extends AbstractHardwareComponent implements TimerResponsive, SoundOutputDevice
{
     private SoundDigitalOut soundOut;
     private Clock clock;
     private Timer timer;

     private long sampleNumber;
     private static final long SAMPLE_CLOCKS = 22675;
     private static final long SAMPLE_SUB_CLOCKS = 325;
     private static final long SAMPLE_SUB_MODULO = 441;

     public SoundOutTest(SRLoader input) throws IOException
     {
         super(input);
         soundOut = (SoundDigitalOut)input.loadObject();
         clock = (Clock)input.loadObject();
         timer = (Timer)input.loadObject();
         sampleNumber = input.loadLong();
     }

     public void dumpSRPartial(SRDumper output) throws IOException
     {
         super.dumpSRPartial(output);
         output.dumpObject(soundOut);
         output.dumpObject(clock);
         output.dumpObject(timer);
         output.dumpLong(sampleNumber);
     }

     public SoundOutTest(String args)
     {
         sampleNumber = 0;
     }

     public void soundChannelCallback(SoundDigitalOut out)
     {
         soundOut = out;
     }

     public int requestedSoundChannels()
     {
         return 1;
     }

     public void callback()
     {
         short leftVolume = (short)(1000 * sampleNumber), rightVolume = (short)(750 * sampleNumber);

         soundOut.addSample(sampleTime(sampleNumber), leftVolume, rightVolume);
         sampleNumber++;
         if(sampleNumber % 10000 == 0)
             System.err.println("Informative: SampleNumber = " + sampleNumber + ".");
         timer.setExpiry(sampleTime(sampleNumber));
     }

     private long sampleTime(long sample)
     {
         return sample * SAMPLE_CLOCKS + (SAMPLE_SUB_CLOCKS * sample / SAMPLE_SUB_MODULO);
     }

     public int getTimerType()
     {
         return 18;
     }

    public boolean initialised()
    {
        return(clock != null);
    }

    public void reset()
    {
        clock = null;
    }

    public void acceptComponent(HardwareComponent component)
    {
        if ((component instanceof Clock) &&
            component.initialised()) {
            clock = (Clock)component;
            timer = clock.newTimer(this);
            timer.setExpiry(0);
        }
    }

    public void dumpStatus(StatusDumper output)
    {
        if(output.dumped(this))
            return;

        output.println("#" + output.objectNumber(this) + ": SoundOutTest:");
        dumpStatusPartial(output);
        output.endObject();
    }

    public void dumpStatusartial(StatusDumper output)
    {
    }
}
