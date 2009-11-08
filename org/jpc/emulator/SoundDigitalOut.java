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

package org.jpc.emulator;

import java.io.*;

/**
 * This class represents digital sound output.
 *
 * @author Ilari Liusvaara
 */
public class SoundDigitalOut implements SRDumpable, OutputConnector
{
    private static final int OUTPUT_BUFFER_SAMPLES = 1024;
    private int bufferFill;
    private int[] outputTiming;
    private short[] outputLeft;
    private short[] outputRight;
    private long blockNumber;
    //The last sample memory.
    private short lastSampleLeft;
    private short lastSampleRight;
    private long lastSampleTime;
    private long blockTimeBase;
    private short blockBaseLeft;
    private short blockBaseRight;
    private Clock clock;
    private OutputConnectorLocking locking;

    public void dumpSRPartial(SRDumper output) throws IOException
    {
        output.dumpShort(lastSampleLeft);
        output.dumpShort(lastSampleRight);
        output.dumpLong(clock.getTime());
        output.dumpObject(clock);
        //Don't dump others, they are transistent.
    }

    public SoundDigitalOut(SRLoader input) throws IOException
    {
        input.objectCreated(this);
        lastSampleLeft = input.loadShort();
        lastSampleRight = input.loadShort();
        lastSampleTime = input.loadLong();
        blockTimeBase = lastSampleTime;
        clock = (Clock)input.loadObject();
        bufferFill = 0;
        blockNumber = 0;
        outputTiming = new int[OUTPUT_BUFFER_SAMPLES];
        outputLeft = new short[OUTPUT_BUFFER_SAMPLES];
        outputRight = new short[OUTPUT_BUFFER_SAMPLES];
        blockBaseLeft = lastSampleLeft;
        blockBaseRight = lastSampleRight;
        locking = new OutputConnectorLocking();
    }

    public void dumpStatus(StatusDumper output)
    {
        if(output.dumped(this))
            return;

        output.println("#" + output.objectNumber(this) + ": SoundDigitalOut:");
        dumpStatusPartial(output);
        output.endObject();
    }

    public void dumpStatusPartial(StatusDumper output)
    {
        output.println("\tlastSampleTime " + lastSampleTime);
        output.println("\tlastSampleLeft " + lastSampleLeft + " lastSampleRight " + lastSampleRight);
        output.println("\tclock <object #" + output.objectNumber(clock) + ">"); if(clock != null) clock.dumpStatus(output);
    }


    public SoundDigitalOut(Clock _clock)
    {
        lastSampleLeft = 0;
        lastSampleRight = 0;
        lastSampleTime = 0;
        blockTimeBase = lastSampleTime;
        bufferFill = 0;
        blockNumber = 0;
        clock = _clock;
        outputTiming = new int[OUTPUT_BUFFER_SAMPLES];
        outputLeft = new short[OUTPUT_BUFFER_SAMPLES];
        outputRight = new short[OUTPUT_BUFFER_SAMPLES];
        blockBaseLeft = lastSampleLeft;
        blockBaseRight = lastSampleRight;
        locking = new OutputConnectorLocking();
    }

    private void clearBuffer()
    {
        //This has to be done outside synchronized block because otherwise other threads
        //would try to use readBlock(), which would cause deadlock.
        locking.holdOutput();
        synchronized(this) {
            //Clear the buffer.
            blockBaseLeft = outputLeft[bufferFill - 1];
            blockBaseRight = outputRight[bufferFill - 1];
            bufferFill = 0;
            blockNumber++;
            blockTimeBase = lastSampleTime;
        }
    }

    /**
     * Insert stereo sample to digital output.
     *
     * @param time The timing of the sample in nanoseconds.
     * @param leftSample the left channel sample
     * @param rightSample the left channel sample
     *
     */
    public void addSample(long time, short leftSample, short rightSample)
    {
        if(lastSampleTime == time) {
            //Immediate override.
            synchronized(this) {
                outputTiming[bufferFill] = 0;
                outputLeft[bufferFill] = leftSample;
                outputRight[bufferFill] = rightSample;
                ++bufferFill;
            }
            if(bufferFill == OUTPUT_BUFFER_SAMPLES)
                clearBuffer();
        }
        while(lastSampleTime < time) {
            boolean _final;
            synchronized(this) {
                _final = true; //Final sample in run?
                long delta = time - lastSampleTime;
                if(delta > 0x7FFFFFFFL) {
                    delta = 0x7FFFFFFFL;
                    _final = false;
                }
                outputTiming[bufferFill] = (int)delta;
                if(_final) {
                    outputLeft[bufferFill] = leftSample;
                    outputRight[bufferFill] = rightSample;
                } else {
                    outputLeft[bufferFill] = lastSampleLeft;
                    outputRight[bufferFill] = lastSampleRight;
                }
                ++bufferFill;
                lastSampleTime += delta;
            }
            if(bufferFill == OUTPUT_BUFFER_SAMPLES)
                clearBuffer();
        }
        lastSampleLeft = leftSample;
        lastSampleRight = rightSample;
    }

    /**
     * Insert mono sample to digital output.
     *
     * @param time The timing of the sample in nanoseconds.
     * @param sample the sample
     *
     */
    public void addSample(long time, short sample)
    {
        addSample(time, sample, sample);
    }

    public static class Block
    {
        public long timeBase;
        public short baseLeft;
        public short baseRight;
        public long blockNo;
        public int samples;
        public int[] sampleTiming;
        public short[] sampleLeft;
        public short[] sampleRight;
    }

    //This is atomic versus addsample!
    public synchronized void readBlock(Block toFill)
    {
        toFill.timeBase = blockTimeBase;
        toFill.blockNo = blockNumber;
        toFill.samples = bufferFill;
        toFill.baseLeft = blockBaseLeft;
        toFill.baseRight = blockBaseRight;
        if(toFill.samples == 0) {
            toFill.sampleTiming = null;
            toFill.sampleLeft = null;
            toFill.sampleRight = null;
        } else {
            toFill.sampleTiming = new int[bufferFill];
            toFill.sampleLeft = new short[bufferFill];
            toFill.sampleRight = new short[bufferFill];
            System.arraycopy(outputTiming, 0, toFill.sampleTiming, 0, bufferFill);
            System.arraycopy(outputLeft, 0, toFill.sampleLeft, 0, bufferFill);
            System.arraycopy(outputRight, 0, toFill.sampleRight, 0, bufferFill);
        }
    }

    public void subscribeOutput(Object handle)
    {
        locking.subscribeOutput(handle);
    }

    public void unsubscribeOutput(Object handle)
    {
        locking.unsubscribeOutput(handle);
    }

    public boolean waitOutput(Object handle)
    {
        return locking.waitOutput(handle);
    }

    public void releaseOutput(Object handle)
    {
        locking.releaseOutput(handle);
    }

    public void releaseOutputWaitAll(Object handle)
    {
        locking.releaseOutputWaitAll(handle);
    }
}
