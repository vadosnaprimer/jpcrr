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

import org.jpc.emulator.motherboard.*;
import org.jpc.emulator.*;
import org.jpc.diskimages.BlockDevice;
import org.jpc.diskimages.GenericBlockDevice;

import java.io.*;

/**
 *
 * @author Ilari Liusvaara
 */
public class SoundTest extends AbstractHardwareComponent implements SoundOutputDevice, TimerResponsive
{
    private Clock clock;
    private Timer eventTimer;
    private long timeBase;
    private SoundDigitalOut out;

    public boolean initialised()
    {
        return (clock != null);
    }

    public void reset()
    {
        clock = null;
    }

    public void callback()
    {
        long time = clock.getTime();
        short sampleL = (short)(32000 * Math.sin(2 * 3.1415926535897 * time / 1000000));
        short sampleR = (short)(32000 * Math.sin(2 * 3.1415926535897 * time / 2000000));
        out.addSample(time, sampleL, sampleR);

        //Timer fires in 0.1ms.
        timeBase = timeBase + 100000;
        eventTimer.setExpiry(timeBase + 100000);
    }

    public int getTimerType()
    {
        return 76;
    }

    public void dumpSRPartial(SRDumper output) throws IOException
    {
        super.dumpSRPartial(output);
        output.dumpObject(clock);
        output.dumpObject(eventTimer);
        output.dumpObject(out);
        output.dumpLong(timeBase);
    }

    public SoundTest(SRLoader input) throws IOException
    {
        super(input);
        clock = (Clock)input.loadObject();
        eventTimer = (Timer)input.loadObject();
        out = (SoundDigitalOut)input.loadObject();
        timeBase = input.loadLong();
    }

    public SoundTest()
    {
    }

    public void dumpStatus(StatusDumper output)
    {
    }

    public int requestedSoundChannels()
    {
        return 1;
    }

    public void soundChannelCallback(SoundDigitalOut _out)
    {
        out = _out;
    }

    public void acceptComponent(HardwareComponent component)
    {
        if ((component instanceof Clock) &&
            component.initialised()) {
            clock = (Clock)component;
            //Timer fires in 1ms.
            timeBase = 0;
            eventTimer = clock.newTimer(this);
            eventTimer.setExpiry(100000);
        }
    }

}
