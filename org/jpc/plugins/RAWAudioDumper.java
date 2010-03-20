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

package org.jpc.plugins;

import java.io.*;
import java.util.*;
import org.jpc.emulator.*;
import org.jpc.pluginsbase.Plugins;
import org.jpc.pluginsbase.Plugin;
import static org.jpc.Misc.errorDialog;
import static org.jpc.Misc.parseStringToComponents;

public class RAWAudioDumper implements Plugin
{
    private OutputStream stream;
    private String soundName;
    private volatile SoundDigitalOut soundOut;
    private volatile Clock clock;
    private volatile boolean signalCheck;
    private volatile boolean shuttingDown;
    private volatile boolean shutDown;
    private Thread worker;
    private volatile boolean pcRunStatus;
    private volatile boolean requestExtraSave;
    private volatile boolean firstInSegment;
    private volatile long internalTime;
    private volatile long lastInternalTimeUpdate;
    private volatile long lastSampleWritten;

    public RAWAudioDumper(Plugins pluginManager, String args) throws IOException
    {
        Map<String, String> params = parseStringToComponents(args);
        String fileName = params.get("file");
        soundName = params.get("src");
        if(fileName == null)
            throw new IOException("File name (file) required for RAWAudioDumper");
        if(soundName == null)
            throw new IOException("Sound name (src) required for RAWAudioDumper");
        System.err.println("Notice: Filename: " + fileName + " soundtrack: " + soundName + ".");
        stream = new FileOutputStream(fileName);
        shuttingDown = false;
        shutDown = false;
        pcRunStatus = false;
        internalTime = 0;
        lastInternalTimeUpdate = 0;
        lastSampleWritten = 0;
        firstInSegment = true;

        String initialGap = params.get("offset");
        if(initialGap != null) {
            try {
                long offset = Long.parseLong(initialGap);
                if(offset <= 0)
                    throw new IOException("Invalid offset to RAWAudioDumper (must be >0)");
                dumpSilence(offset);
                internalTime = offset;
            } catch(NumberFormatException e) {
                throw new IOException("Invalid offset to RAWAudioDumper (must be numeric >0)");
            }
        }
    }

    public boolean systemShutdown()
    {
        if(pcRunStatus)
            return false;   //Don't shut down until after PC.

        shuttingDown = true;
        if(worker != null) {
            worker.interrupt();
            synchronized(this) {
                notifyAll();
                while(!shutDown)
                    try {
                        wait();
                    } catch(Exception e) {
                    }
            }
        }
        return true;
    }

    public void reconnect(PC pc)
    {
        SoundDigitalOut previousOutput = soundOut;

        //Bump it a little.
        soundOut = null;
        worker.interrupt();
        while(!signalCheck)
            ;

        if(previousOutput != null)
             previousOutput.unsubscribeOutput(this);

        synchronized(this) {
            if(pc != null) {
                clock = (Clock)pc.getComponent(Clock.class);
                soundOut = pc.getSoundOut(soundName);
                if(soundOut == null)
                    System.err.println("Warning: No such audio output \"" + soundName + "\".");
            } else {
                soundOut = null;
                clock = null;
            }

            if(soundOut != null)
                soundOut.subscribeOutput(this);

            notifyAll();
        }
    }

    public void pcStarting()
    {
        pcRunStatus = true;
        lastInternalTimeUpdate = clock.getTime();
    }

    public void pcStopping()
    {
        pcRunStatus = false;
        long update = clock.getTime();
        internalTime += (update - lastInternalTimeUpdate);
        lastInternalTimeUpdate = update;

        //Request extra save so tail is dumped too.
        synchronized(this) {
            requestExtraSave = true;
            worker.interrupt();
            while(requestExtraSave)
                try {
                    wait();
                } catch(Exception e) {
                }
        }
    }

    private void dumpSilence(long length)
    {
        long finalTime = lastSampleWritten + length;
        int blocks = (int)((length + 0xFFFFFFFEL) / 0xFFFFFFFFL);
        byte[] array = new byte[8 * blocks + 8];
        //All samples at zero levlel.
        for(int i = 0; i <= blocks; i++) {
            array[8 * i + 4] = array[8 * i + 6] = -128;
            array[8 * i + 5] = array[8 * i + 7] = 0;
        }
        //First sample is offset zero, zero level.
        array[0] = 0;
        array[1] = 0;
        array[2] = 0;
        array[3] = 0;
        for(int i = 1; i < blocks; i++) {
            //Offset maximum.
            array[8 * i + 0] = -1;
            array[8 * i + 1] = -1;
            array[8 * i + 2] = -1;
            array[8 * i + 3] = -1;
            lastSampleWritten += 0xFFFFFFFFL;
        }
        long remainder = finalTime - lastSampleWritten;
        if(remainder <= 0)
            throw new IllegalStateException("Fuckup in RAW dumper: Negative or zero remainder.");
        array[8 * blocks + 0] = (byte)(remainder >>> 24);
        array[8 * blocks + 1] = (byte)(remainder >>> 16);
        array[8 * blocks + 2] = (byte)(remainder >>> 8);
        array[8 * blocks + 3] = (byte)(remainder);
        try {
            if(stream != null)
                stream.write(array);
        } catch(IOException e) {
            System.err.println("Warning: Failed to save audio frame!");
            errorDialog(e, "Failed to save audio frame", null, "Dismiss");
        }
        lastSampleWritten += remainder;
        firstInSegment = true;
    }

    private void dumpTail(boolean finalInSegment)
    {
        long offset = internalTime - lastInternalTimeUpdate;
        long internalTimeNow = clock.getTime() + offset;

        if(finalInSegment) {
            firstInSegment = true;
            if(internalTimeNow > lastSampleWritten) {
                if(soundOut == null)
                    System.err.println("Notice: Dumping silence due to no input (" + lastSampleWritten + " -> " +
                        internalTimeNow + ").");
                else
                    System.err.println("Notice: Gap in audio timestream (" + lastSampleWritten + " -> " +
                        internalTimeNow + ").");
                dumpSilence(internalTimeNow - lastSampleWritten);
            }
        }
    }

    private void readAndDumpFrame(boolean finalInSegment)
    {
        if(clock == null)
            return;   //Outside time.

        long offset = internalTime - lastInternalTimeUpdate;
        long internalTimeNow = clock.getTime() + offset;

        SoundDigitalOut.Block frameData = new SoundDigitalOut.Block();
        if(soundOut != null) {
            soundOut.readBlock(frameData);
            if(frameData.samples == 0) {
                dumpTail(finalInSegment);
                return;  //Empty blocks are not interesting.
            }

            long localTimeBase = (frameData.timeBase - lastInternalTimeUpdate) + internalTime;
            if(localTimeBase > lastSampleWritten) {
                System.err.println("Notice: Gap in audio timestream (" + lastSampleWritten + " -> " +
                    localTimeBase + ").");
                dumpSilence(localTimeBase - lastSampleWritten);
            }
            if(localTimeBase != lastSampleWritten)
                firstInSegment = true;  //Segment break.

            long startTime = lastSampleWritten;

            long[] localSampleTime = new long[frameData.samples + 1];
            short[] sampleLeft = new short[frameData.samples + 1];
            short[] sampleRight = new short[frameData.samples + 1];
            long timeParse = localTimeBase;
            int samplesNeeded = 0;
            short previousLeft = frameData.baseLeft;
            short previousRight = frameData.baseRight;

            for(int i = 0; i < frameData.samples; i++) {
                long localTime = timeParse;
                localTime += frameData.sampleTiming[i];
                short left = frameData.sampleLeft[i];
                short right = frameData.sampleRight[i];

                if(firstInSegment && localTime > lastSampleWritten) {
                    //Force segment break.
                    localSampleTime[samplesNeeded] = lastSampleWritten;
                    sampleLeft[samplesNeeded] = previousLeft;
                    sampleRight[samplesNeeded] = previousRight;
                    samplesNeeded++;
                    firstInSegment = false;
                }
                if(firstInSegment && localTime == lastSampleWritten) {
                    //Write the break.
                    localSampleTime[samplesNeeded] = localTime;
                    sampleLeft[samplesNeeded] = left;
                    sampleRight[samplesNeeded] = right;
                    samplesNeeded++;
                    firstInSegment = false;
                } else if(localTime < lastSampleWritten) {
                    //Skip samples in past.
                } else if(localTime > internalTimeNow) {
                    System.err.println("Warning: Audio sample from future (" + localTime + ">" +
                        internalTimeNow + ").");
                } else {
                    //Normal sample write.
                    localSampleTime[samplesNeeded] = localTime;
                    sampleLeft[samplesNeeded] = left;
                    sampleRight[samplesNeeded] = right;
                    samplesNeeded++;
                }

                timeParse = localTime;
                previousLeft = left;
                previousRight = right;
            }

            if(samplesNeeded == 0) {
                dumpTail(finalInSegment);
                return;  //Empty blocks are not interesting.
            }

            byte[] buffer = new byte[8 * samplesNeeded];
            for(int i = 0; i < samplesNeeded; i++) {
                long pos = localSampleTime[i] - lastSampleWritten;
                buffer[8 * i + 0] = (byte)(pos >>> 24);
                buffer[8 * i + 1] = (byte)(pos >>> 16);
                buffer[8 * i + 2] = (byte)(pos >>> 8);
                buffer[8 * i + 3] = (byte)pos;
                buffer[8 * i + 4] = (byte)(sampleLeft[i] >>> 8);
                buffer[8 * i + 5] = (byte)sampleLeft[i];
                buffer[8 * i + 6] = (byte)(sampleRight[i] >>> 8);
                buffer[8 * i + 7] = (byte)sampleRight[i];
                lastSampleWritten = localSampleTime[i];
            }

            try {
                if(stream != null)
                    stream.write(buffer);
            } catch(IOException e) {
                System.err.println("Warning: Failed to save audio frame!");
                errorDialog(e, "Failed to save audio frame", null, "Dismiss");
            }

            System.err.println("Notice: Dumped audio block (" + startTime + " -> " +
                lastSampleWritten + ").");

            firstInSegment = false;
        }

        dumpTail(finalInSegment);

    }

    public void main()
    {
        worker = Thread.currentThread();
        while(!shuttingDown) {
            if(requestExtraSave) {
                synchronized(this) {
                    readAndDumpFrame(true);
                    requestExtraSave = false;
                    notifyAll();
                }
            } else if(soundOut == null && !shuttingDown) {
                synchronized(this) {
                    signalCheck = true;
                    try {
                        wait();
                    } catch(Exception e) {
                    }
                    signalCheck = false;
                }
            } else if(!shuttingDown) {
                //Since this waits, we can't synchornize.
                if(soundOut.waitOutput(this)) {
                    readAndDumpFrame(false);
                    soundOut.releaseOutput(this);
                }
            }
        }

        try {
            if(stream != null) {
                stream.flush();
                stream.close();
            }
        } catch(IOException e) {
            System.err.println("Warning: Failed to close audio stream!");
            errorDialog(e, "Failed to close audio stream", null, "Dismiss");
        }

        synchronized(this) {
            shutDown = true;
            notifyAll();
        }
    }
}
