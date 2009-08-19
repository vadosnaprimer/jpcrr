/*
    JPC: A x86 PC Hardware Emulator for a pure Java Virtual Machine
    Release Version 2.0

    A project from the Physics Dept, The University of Oxford

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

    Details (including contact information) can be found at:

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
    private byte[] outputBuffer;
    private long blockNumber;
    //The last sample memory.
    private short lastSampleLeft;
    private short lastSampleRight;
    private long lastSampleTime;
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
        clock = (Clock)input.loadObject();
        bufferFill = 0;
        blockNumber = 0;
        outputBuffer = new byte[8 * OUTPUT_BUFFER_SAMPLES];
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
        bufferFill = 0;
        blockNumber = 0;
        clock = _clock;
        outputBuffer = new byte[8 * OUTPUT_BUFFER_SAMPLES];
        locking = new OutputConnectorLocking();
    }

    private void encodeSample(byte[] buffer, int offset, long delta, short left, short right)
    {
        int _left = (int)left + 32768;
        int _right = (int)right + 32768;

        buffer[offset + 0] = (byte)((delta >>> 24) & 0xFF);
        buffer[offset + 1] = (byte)((delta >>> 16) & 0xFF);
        buffer[offset + 2] = (byte)((delta >>> 8) & 0xFF);
        buffer[offset + 3] = (byte)(delta & 0xFF);
        buffer[offset + 4] = (byte)((_left >>> 8) & 0xFF);
        buffer[offset + 5] = (byte)(_left & 0xFF);
        buffer[offset + 6] = (byte)((_right >>> 8) & 0xFF);
        buffer[offset + 7] = (byte)(_right & 0xFF);
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
                encodeSample(outputBuffer, bufferFill * 8, 0, leftSample, rightSample);
                ++bufferFill;
            }
            if(bufferFill == OUTPUT_BUFFER_SAMPLES) {
                //This has to be done outside synchronized block because otherwise other threads
                //would try to use readBlock(), which would cause deadlock.
                locking.holdOutput();
                synchronized(this) {
                    //Clear the buffer.
                    bufferFill = 0;
                    blockNumber++;
                }
            }
        }
        while(lastSampleTime < time) {
            synchronized(this) {
                boolean _final = true; //Final sample in run?
                long delta = time - lastSampleTime;
                if(delta > 0xFFFFFFFFL) {
                    delta = 0xFFFFFFFFL;
                    _final = false;
                }
                if(_final)
                    encodeSample(outputBuffer, bufferFill * 8, delta, leftSample, rightSample);
                else
                    encodeSample(outputBuffer, bufferFill * 8, delta, lastSampleLeft, lastSampleRight);

                ++bufferFill;
                lastSampleTime += delta;
            }
            if(bufferFill == OUTPUT_BUFFER_SAMPLES) {
                //This has to be done outside synchronized block because otherwise other threads
                //would try to use readBlock(), which would cause deadlock.
                locking.holdOutput();
                synchronized(this) {
                    //Clear the buffer.
                    bufferFill = 0;
                    blockNumber++;
                }
            }
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
        public long blockNo;
        public int samples;
        public byte[] sampleData;
    }

    //This is atomic versus addsample!
    public synchronized void readBlock(Block toFill)
    {
        toFill.blockNo = blockNumber;
        toFill.samples = bufferFill;
        if(toFill.samples == 0)
            toFill.sampleData = null;
        else {
            toFill.sampleData = new byte[8 * bufferFill];
            System.arraycopy(outputBuffer, 0, toFill.sampleData, 0, 8 * bufferFill);
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
}
