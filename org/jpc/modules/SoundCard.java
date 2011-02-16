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
import java.util.Arrays;

public class SoundCard  extends AbstractHardwareComponent implements IOPortCapable, TimerResponsive, DMATransferCapable, SoundOutputDevice
{
    private int baseIOAddress;
    private boolean irq2;
    private boolean irq5;
    private boolean irq7;
    private boolean irq10;
    private int dmaMask;
    private int dmaRequested;
    private boolean ioportRegistered;
    private DMAController primaryDMAController;
    private DMAController secondaryDMAController;
    private Timer timer;
    private Clock clock;
    private InterruptController irqController;
    private OutputChannelPCM pcmOutput;

    private boolean irq8Bit;
    private boolean irq16Bit;

    private int mixerIndex;
    private int mixerPrevIndex;
    private int[] mixerRegisters;

    private int dspCommandState;
    private int[] dspOutput;
    private int dspOutputUsed;
    private long dspNextAttention;

    //The following need to be programmed for DMA Engine.
    private int dmaState;                               //DMA_*
    private int samplesLeft;                            //Samples left in block.
    private int origSamplesLeft;                        //Original samples in block.
    private int soundFormat;                            //SNDFMT_*
    private boolean stereo;                             //Stereo On?
    private long interSampleTime;                       //Amount of time between samples.
    private boolean istStereoAdjust;                    //InterSampleTime is affected by stereo.

    private boolean dmaPaused;                          //DMA activity is paused.
    private int dmaPauseLeft;                           //Amount of time left in DMA pause (-1 if indefinite)
    private int dmaActiveMask;                          //Active DMA on these channels.
    private boolean dmaRequest;                         //DMA request in progress.
    private long dmaRequestTime;                        //DMA data request was made at this time.
    private int partialSample;                          //Partial sample data.
    private int partialSampleBytes;                     //Bytes of sample received.
    private int wholeSampleBytes;                       //Bytes to obtain at once (max 4)

    private int byteBuffer;                             //Byte buffer from last time (ADPCM only).
    private int byteBufferSamples;                      //Number of samples in byte buffer.

    private int adpcmReference;                         //ADPCM reference byte.
    private byte adpcmScale;                            //ADPCM scale.

    private long nextSampleTime;                        //DMA sample plays at this time.

    private int dspArgumentRegister;                     //Temporary storage for DSP command argument.
    private int dspNextDMA;                              //Next DMA parameters. -1 if none.
    private int dspLastCommand;                          //Last DSP command.

    private boolean e2Mode;                              //Do E2 write next time DMA is invoked.
    private byte e2Value;                                 //Value to write for E2.
    private int e2Count;                                 //E2 write counter.

    private boolean speakerConnected;

    //FM chups (1).
    private FMTimerCounter[] fmTimers;
    private int fmRegIndex;
    private static final int FM_REG_COUNT = 512;
    private int[] fmRegValues;
    private OutputChannelFM fmOutput;

    private static final int DMA_NONE = 0;               //No DMA in progress.
    private static final int DMA_SINGLE = 1;             //Single-block DMA in progress.
    private static final int DMA_CONTINUOUS = 2;         //Auto-Init DMA in progress.
    private static final int DMA_CONTINUOUS_EXIT = 3;    //Auto-Init DMA last block in progress.

    private static final int SNDFMT_2BIT_ADPCM = 0;               //2-bit ADPCM.
    private static final int SNDFMT_26BIT_ADPCM = 1;              //2.6-bit (6-level) ADPCM.
    private static final int SNDFMT_4BIT_ADPCM = 2;               //4-bit ADPCM.
    private static final int SNDFMT_2BIT_ADPCM_REF = 3;           //2-bit ADPCM /w reference.
    private static final int SNDFMT_26BIT_ADPCM_REF = 4;          //2.6-bit (6-level) ADPCM /w reference.
    private static final int SNDFMT_4BIT_ADPCM_REF = 5;           //4-bit ADPCM /w reference.
    private static final int SNDFMT_8BIT_PCM_SIGNED = 6;          //8-bit PCM (SIGNED)
    private static final int SNDFMT_8BIT_PCM_UNSIGNED = 7;        //8-bit PCM (UNSIGNED)
    private static final int SNDFMT_16BIT_PCM_LE_SIGNED = 8;      //16-bit LE PCM (SIGNED)
    private static final int SNDFMT_16BIT_PCM_LE_UNSIGNED = 9;    //16-bit LE PCM (UNSIGNED)
    private static final int SNDFMT_16BIT_PCM_BE_SIGNED = 10;      //16-bit LE PCM (SIGNED)
    private static final int SNDFMT_16BIT_PCM_BE_UNSIGNED = 11;    //16-bit LE PCM (UNSIGNED)

    private static final byte ADPCM_SCALE_INIT = 0;

    private static final int typeFlag = 0x10;
    private static final int QUEUE_SIZE = 256;
    private static final int DSP_VERSION = 0x404;   //4.04 (early SB16).

    private static final int MIXER_REGISTERS = 25;
    private static final int MIXREG_RESET = 0x00;
    private static final int MIXREG_STATUS = 0x1;
    private static final int MIXREG_DAC = 0x04;
    private static final int MIXREG_MIC = 0x0A;
    private static final int MIXREG_OUTPUT_CONTROL = 0x0E;
    private static final int MIXREG_MASTER = 0x22;
    private static final int MIXREG_FM = 0x26;
    private static final int MIXREG_CD = 0x28;
    private static final int MIXREG_LINEIN = 0x2E;
    private static final int MIXREG_IRQSELECT = 0x80;
    private static final int MIXREG_DMASELECT = 0x81;
    private static final int MIXREG_IRQSTATUS = 0x82;
    private static final int MIXREG_SB16_FIRST = 0x30;
    private static final int MIXREG_LAST = 0x47;

    private static final int MIXER_MASTER_LEFT = 0x00;
    private static final int MIXER_MASTER_RIGHT = 0x01;
    private static final int MIXER_DAC_LEFT = 0x02;
    private static final int MIXER_DAC_RIGHT = 0x03;
    private static final int MIXER_FM_LEFT = 0x04;
    private static final int MIXER_FM_RIGHT = 0x05;
    private static final int MIXER_OUTPUT_GAIN_CONTROL_LEFT = 0x11;
    private static final int MIXER_OUTPUT_GAIN_CONTROL_RIGHT = 0x12;
    private static final int MIXER_TREBLE_LEFT = 0x14;
    private static final int MIXER_TREBLE_RIGHT = 0x15;
    private static final int MIXER_BASS_LEFT = 0x16;
    private static final int MIXER_BASS_RIGHT = 0x17;
    private static final int MIXER_OUTPUT_CONTROL = 24;

    //Following are here for completeness, but are not used.
    private static final int MIXER_CD_LEFT = 0x06;
    private static final int MIXER_CD_RIGHT = 0x07;
    private static final int MIXER_LINEIN_LEFT = 0x08;
    private static final int MIXER_LINEIN_RIGHT = 0x09;
    private static final int MIXER_MIC = 0x0A;
    private static final int MIXER_PCSPEAKER = 0x0B;
    private static final int MIXER_INPUT_CONTROL_LEFT = 0x0D;
    private static final int MIXER_INPUT_CONTROL_RIGHT = 0x0E;
    private static final int MIXER_INPUT_GAIN_CONTROL_LEFT = 0x0F;
    private static final int MIXER_INPUT_GAIN_CONTROL_RIGHT = 0x10;
    private static final int MIXER_AUTOMATIC_GAIN_CONTROL = 0x13;

    private static final int DSPSTATE_WAIT_COMMAND = 0;
    private static final int DSPSTATE_DIRECT_DAC_SAMPLE = 1;
    private static final int DSPSTATE_SILENCE_LOW = 2;
    private static final int DSPSTATE_SILENCE_HIGH = 3;
    private static final int DSPSTATE_TESTWRITE = 4;
    private static final int DSPSTATE_IDWRITE = 5;
    private static final int DSPSTATE_RATE_LOW = 6;
    private static final int DSPSTATE_RATE_HIGH = 7;
    private static final int DSPSTATE_BLOCKSIZE_LOW = 8;
    private static final int DSPSTATE_BLOCKSIZE_HIGH = 9;
    private static final int DSPSTATE_TC = 10;
    private static final int DSPSTATE_OLDDMA_LOW = 11;
    private static final int DSPSTATE_OLDDMA_HIGH = 12;
    private static final int DSPSTATE_NEWDMA_LOW = 13;
    private static final int DSPSTATE_NEWDMA_MID = 14;
    private static final int DSPSTATE_NEWDMA_HIGH = 15;
    private static final int DSPSTATE_DMA_IDENTIFY = 16;

    private static final int ADPCM_4BIT_SCALE_MAX = 3;
    private static final int ADPCM_26BIT_SCALE_MAX = 4;
    private static final int ADPCM_2BIT_SCALE_MAX = 5;
    private static final byte[] ADPCM_4BIT_LEVEL_SHIFT;
    private static final byte[] ADPCM_26BIT_LEVEL_SHIFT;
    private static final byte[] ADPCM_2BIT_LEVEL_SHIFT;
    private static final int[] ADPCM_4BIT_LEVEL_MULT;
    private static final int[] ADPCM_26BIT_LEVEL_MULT;
    private static final int[] ADPCM_2BIT_LEVEL_MULT;
    private static final int[] ADPCM_4BIT_SAMPLE_MULT;
    private static final int[] ADPCM_26BIT_SAMPLE_MULT;
    private static final int[] ADPCM_2BIT_SAMPLE_MULT;

    private static final int DSPCMD_SC_DMA_8_1 = 0x14;
    private static final int DSPCMD_SC_DMA_8_2 = 0x15;
    private static final int DSPCMD_SC_DMA_8_3 = 0x91;
    private static final int DSPCMD_AI_DMA_8_1 = 0x1C;
    private static final int DSPCMD_AI_DMA_8_2 = 0x90;
    private static final int DSPCMD_SC_ADPCM_2 = 0x16;
    private static final int DSPCMD_SC_ADPCM_2_REF = 0x17;
    private static final int DSPCMD_SC_ADPCM_26 = 0x76;
    private static final int DSPCMD_SC_ADPCM_26_REF = 0x77;
    private static final int DSPCMD_SC_ADPCM_4 = 0x74;
    private static final int DSPCMD_SC_ADPCM_4_REF = 0x75;
    private static final int DSPCMD_GENERIC_DMA_0 = 0xB0;
    private static final int DSPCMD_GENERIC_DMA_1 = 0xB1;
    private static final int DSPCMD_GENERIC_DMA_2 = 0xB2;
    private static final int DSPCMD_GENERIC_DMA_3 = 0xB3;
    private static final int DSPCMD_GENERIC_DMA_4 = 0xB4;
    private static final int DSPCMD_GENERIC_DMA_5 = 0xB5;
    private static final int DSPCMD_GENERIC_DMA_6 = 0xB6;
    private static final int DSPCMD_GENERIC_DMA_7 = 0xB7;
    private static final int DSPCMD_GENERIC_DMA_8 = 0xB8;
    private static final int DSPCMD_GENERIC_DMA_9 = 0xB9;
    private static final int DSPCMD_GENERIC_DMA_A = 0xBA;
    private static final int DSPCMD_GENERIC_DMA_B = 0xBB;
    private static final int DSPCMD_GENERIC_DMA_C = 0xBC;
    private static final int DSPCMD_GENERIC_DMA_D = 0xBD;
    private static final int DSPCMD_GENERIC_DMA_E = 0xBE;
    private static final int DSPCMD_GENERIC_DMA_F = 0xBF;
    private static final int DSPCMD_GENERIC_DMA_G = 0xC0;
    private static final int DSPCMD_GENERIC_DMA_H = 0xC1;
    private static final int DSPCMD_GENERIC_DMA_I = 0xC2;
    private static final int DSPCMD_GENERIC_DMA_J = 0xC3;
    private static final int DSPCMD_GENERIC_DMA_K = 0xC4;
    private static final int DSPCMD_GENERIC_DMA_L = 0xC5;
    private static final int DSPCMD_GENERIC_DMA_M = 0xC6;
    private static final int DSPCMD_GENERIC_DMA_N = 0xC7;
    private static final int DSPCMD_GENERIC_DMA_O = 0xC8;
    private static final int DSPCMD_GENERIC_DMA_P = 0xC9;
    private static final int DSPCMD_GENERIC_DMA_Q = 0xCA;
    private static final int DSPCMD_GENERIC_DMA_R = 0xCB;
    private static final int DSPCMD_GENERIC_DMA_S = 0xCC;
    private static final int DSPCMD_GENERIC_DMA_T = 0xCD;
    private static final int DSPCMD_GENERIC_DMA_U = 0xCE;
    private static final int DSPCMD_GENERIC_DMA_V = 0xCF;

    private static final int DSPCMD_DIRECT_DAC = 0x10;
    private static final int DSPCMD_CONTINUE_DMA_AI = 0x45;
    private static final int DSPCMD_CONTINUE_DMA_AI_16 = 0x47;
    private static final int DSPCMD_SET_TC = 0x40;
    private static final int DSPCMD_SET_OUTPUT_RATE = 0x41;
    private static final int DSPCMD_SET_INPUT_RATE = 0x42;
    private static final int DSPCMD_SET_BLOCKSIZE = 0x48;
    private static final int DSPCMD_SILENCE = 0x80;
    private static final int DSPCMD_PAUSE_DMA = 0xD0;
    private static final int DSPCMD_SPEAKER_ON = 0xD1;
    private static final int DSPCMD_SPEAKER_OFF = 0xD3;
    private static final int DSPCMD_CONTINUE_DMA = 0xD4;
    private static final int DSPCMD_PAUSE_DMA_16 = 0xD5;
    private static final int DSPCMD_CONTINUE_DMA_16 = 0xD6;
    private static final int DSPCMD_SPEAKER_STATUS = 0xD8;
    private static final int DSPCMD_EXIT_DMA_16 = 0xD9;
    private static final int DSPCMD_EXIT_DMA = 0xDA;
    private static final int DSPCMD_DSP_IDENTIFY = 0xE0;
    private static final int DSPCMD_DSP_VERSION = 0xE1;
    private static final int DSPCMD_DMA_IDENTIFY = 0xE2;
    private static final int DSPCMD_COPYRIGHT = 0xE3;
    private static final int DSPCMD_WRITE_TEST = 0xE4;
    private static final int DSPCMD_READ_TEST = 0xE8;
    private static final int DSPCMD_RAISE_8BIT_IRQ = 0xF2;
    private static final int DSPCMD_UNDOCUMENTED1 = 0xF8;

    private static final byte[] E2_MAGIC = {-106, -91, 105, 90};

    private static final int FM_CHIPS = 1;
    private static final String copyright = "COPYRIGHT (C) CREATIVE TECHNOLOGY LTD, 1992.";
    public static final long TIME_NEVER = 0x7FFFFFFFFFFFFFFFL;

    String lastMessage;       //Not saved.
    int portRepeats;          //Not saved.
    boolean soundDebuggingEnabled;   //Not saved.

    static
    {
        //These tables are as ones used in DosBox.
        ADPCM_4BIT_LEVEL_SHIFT = new byte[] { -1,  0,  0,  0,  0,  1,  1,  1, -1,  0,  0,  0,  0,  1,  1,  1 };
        ADPCM_26BIT_LEVEL_SHIFT = new byte[] { -1,  0,  0,  1,  -1,  0,  0,  1 };
        ADPCM_2BIT_LEVEL_SHIFT = new byte[] { -1,  1, -1,  1 };
        ADPCM_4BIT_LEVEL_MULT = new int[] { 1, 1, 2, 4 };
        ADPCM_26BIT_LEVEL_MULT = new int[] { 1, 1, 2, 4, 5};
        ADPCM_2BIT_LEVEL_MULT = new int[] { 1, 1, 2, 4, 8, 16};
        ADPCM_4BIT_SAMPLE_MULT = new int[] { 1, 3, 5, 7, 9, 11, 13, 15, -1, -3, -5, -7, -9, -11, -13, -15 };
        ADPCM_26BIT_SAMPLE_MULT = new int[] { 1, 3, 5, 7, -1, -3, -5, -7 };
        ADPCM_2BIT_SAMPLE_MULT = new int[] { 1, 3, -1, -3 };
    }

    public void dumpSRPartial(SRDumper output) throws IOException
    {
        super.dumpSRPartial(output);
        output.dumpInt(baseIOAddress);
        output.dumpBoolean(irq2);
        output.dumpBoolean(irq5);
        output.dumpBoolean(irq7);
        output.dumpBoolean(irq10);
        output.dumpInt(dmaMask);
        output.dumpInt(dmaRequested);
        output.dumpBoolean(ioportRegistered);
        output.dumpObject(clock);
        output.dumpObject(primaryDMAController);
        output.dumpObject(secondaryDMAController);
        output.dumpObject(irqController);
        output.dumpObject(timer);
        output.dumpObject(pcmOutput);

        output.dumpBoolean(irq8Bit);
        output.dumpBoolean(irq16Bit);

        output.dumpInt(mixerIndex);
        output.dumpInt(mixerPrevIndex);

        output.dumpInt(dspCommandState);
        output.dumpArray(dspOutput);
        output.dumpInt(dspOutputUsed);
        output.dumpArray(mixerRegisters);
        output.dumpLong(dspNextAttention);

        output.dumpBoolean(speakerConnected);

        output.dumpObject(fmOutput);
        output.dumpInt(fmTimers.length);
        for(int i = 0; i < fmTimers.length; i++)
            output.dumpObject(fmTimers[i]);
        output.dumpInt(fmRegIndex);
        output.dumpArray(fmRegValues);

        output.dumpInt(dmaState);
        output.dumpInt(samplesLeft);
        output.dumpInt(origSamplesLeft);
        output.dumpInt(soundFormat);
        output.dumpBoolean(stereo);
        output.dumpLong(interSampleTime);
        output.dumpBoolean(istStereoAdjust);
        output.dumpInt(dmaActiveMask);
        output.dumpBoolean(dmaRequest);
        output.dumpLong(dmaRequestTime);
        output.dumpInt(partialSample);
        output.dumpInt(partialSampleBytes);
        output.dumpInt(wholeSampleBytes);
        output.dumpInt(byteBuffer);
        output.dumpInt(byteBufferSamples);
        output.dumpInt(adpcmReference);
        output.dumpByte(adpcmScale);
        output.dumpLong(nextSampleTime);
        output.dumpBoolean(dmaPaused);
        output.dumpInt(dmaPauseLeft);
        output.dumpInt(dspArgumentRegister);
        output.dumpInt(dspNextDMA);
        output.dumpInt(dspLastCommand);

        output.dumpBoolean(e2Mode);
        output.dumpByte(e2Value);
        output.dumpInt(e2Count);
    }


    public SoundCard(SRLoader input) throws IOException
    {
        super(input);
        baseIOAddress = input.loadInt();
        irq2 = input.loadBoolean();
        irq5 = input.loadBoolean();
        irq7 = input.loadBoolean();
        irq10 = input.loadBoolean();
        dmaMask = input.loadInt();
        dmaRequested = input.loadInt();
        ioportRegistered = input.loadBoolean();
        clock = (Clock)input.loadObject();
        primaryDMAController = (DMAController)input.loadObject();
        secondaryDMAController = (DMAController)input.loadObject();
        irqController = (InterruptController)input.loadObject();
        timer = (Timer)input.loadObject();
        pcmOutput = (OutputChannelPCM)input.loadObject();

        irq8Bit = input.loadBoolean();
        irq16Bit = input.loadBoolean();

        mixerIndex = input.loadInt();
        mixerPrevIndex = input.loadInt();

        dspCommandState = input.loadInt();
        dspOutput = input.loadArrayInt();
        dspOutputUsed = input.loadInt();
        mixerRegisters = input.loadArrayInt();
        dspNextAttention = input.loadLong();

        speakerConnected = input.loadBoolean();

        fmOutput = (OutputChannelFM)input.loadObject();
        fmTimers = new FMTimerCounter[input.loadInt()];
        for(int i = 0; i < fmTimers.length; i++)
            fmTimers[i] = (FMTimerCounter)input.loadObject();
        fmRegIndex = input.loadInt();
        fmRegValues = input.loadArrayInt();

        dmaState = input.loadInt();
        samplesLeft = input.loadInt();
        origSamplesLeft = input.loadInt();
        soundFormat = input.loadInt();
        stereo = input.loadBoolean();
        interSampleTime = input.loadLong();
        istStereoAdjust = input.loadBoolean();
        dmaActiveMask = input.loadInt();
        dmaRequest = input.loadBoolean();
        dmaRequestTime = input.loadLong();
        partialSample = input.loadInt();
        partialSampleBytes = input.loadInt();
        wholeSampleBytes = input.loadInt();
        byteBuffer = input.loadInt();
        byteBufferSamples = input.loadInt();
        adpcmReference = input.loadInt();
        adpcmScale = input.loadByte();
        nextSampleTime = input.loadLong();
        dmaPaused = input.loadBoolean();
        dmaPauseLeft = input.loadInt();
        dspArgumentRegister = input.loadInt();
        dspNextDMA = input.loadInt();
        dspLastCommand = input.loadInt();

        e2Mode = input.loadBoolean();
        e2Value = input.loadByte();
        e2Count = input.loadInt();
    }

    public SoundCard(String parameters) throws IOException
    {
        char mode = 0;
        int tmp = 0;
        int irq = 5;
        baseIOAddress = 0x220;
        int lowDMA = 1;
        int highDMA = 5;

        interSampleTime = 50000;   //Something even reasonably sane.
        dspNextDMA = -1 ;          //No, don't program DMA on first DSP command.

        fmOutput = null;
        fmTimers = new FMTimerCounter[2];
        fmTimers[0] = new FMTimerCounter(0x40, 0x01, 0xC0, 80000, 2);
        fmTimers[1] = new FMTimerCounter(0x20, 0x02, 0xA0, 320000, 3);
        fmRegValues = new int[FM_REG_COUNT];
        fmRegIndex = 0;

        dspNextAttention = TIME_NEVER;

        dspOutput = new int[QUEUE_SIZE];
        mixerRegisters = new int[MIXER_REGISTERS];
        mixerPrevIndex = 128;
        mixerIndex = 128;

        dspCommandState = DSPSTATE_WAIT_COMMAND;

        for(int i = 0; i < parameters.length() + 1; i++) {
            char ch = 0;
            if(i < parameters.length())
                ch = parameters.charAt(i);

            if(ch >= '0' && ch <= '9' && mode != 0) {
                tmp = tmp * 10 + (ch - '0');
                continue;
            } else if(ch >= '0' && ch <= '9' && mode == 0)
                throw new IOException("Soundcard: Invalid spec '" + parameters + "'.");
            if(mode == 'A')
                if(tmp > 65516)
                    throw new IOException("Soundcard: Bad I/O port " + tmp + ".");
                else
                    baseIOAddress = tmp;
            else if(mode == 'I')
                if(tmp != 2 && tmp != 5 && tmp != 7 && tmp != 10)
                    throw new IOException("Soundcard: Bad IRQ " + tmp + ".");
                else
                    irq = tmp;
            else if(mode == 'D')
                if(tmp != 0 && tmp != 1 && tmp != 3)
                    throw new IOException("Soundcard: Bad low DMA " + tmp + ".");
                else
                     lowDMA = tmp;
            else if(mode == 'H')
                if(tmp == 0)
                    highDMA = 0;   //Disable high DMA.
                else if(tmp != 5 && tmp != 6 || tmp != 7)
                    throw new IOException("Soundcard: Bad high DMA " + tmp + ".");
                else
                     highDMA = tmp;
            else if(mode > 0 || i > 0)
                throw new IOException("Soundcard: Invalid setting type '" + mode + "'.");
            if(ch == 0)
                break;
            mode = ch;
            tmp = 0;
        }
        if(irq == 2)
            irq2 = true;
        if(irq == 5)
            irq5 = true;
        if(irq == 7)
            irq7 = true;
        if(irq == 10)
            irq10 = true;
        if(highDMA > 0)
            dmaMask = (1 << lowDMA) | (1 << highDMA);
        else
            dmaMask = (1 << lowDMA);
        dmaRequested = 0;
        resetMixer();
    }

    private final void grabDMAChannels()
    {
        int toReq = dmaMask & ~dmaRequested;
        if(primaryDMAController == null)
            toReq &= 0xF0;
        if(secondaryDMAController == null)
            toReq &= 0x0F;
        if((toReq & 1) != 0 && primaryDMAController != null)
            primaryDMAController.registerChannel(0, this);
        if((toReq & 2) != 0 && primaryDMAController != null)
            primaryDMAController.registerChannel(1, this);
        if((toReq & 8) != 0 && primaryDMAController != null)
            primaryDMAController.registerChannel(3, this);
        if((toReq & 32) != 0 && secondaryDMAController != null)
            secondaryDMAController.registerChannel(1, this);
        if((toReq & 64) != 0 && secondaryDMAController != null)
            secondaryDMAController.registerChannel(2, this);
        if((toReq & 128) != 0 && secondaryDMAController != null)
            secondaryDMAController.registerChannel(3, this);
        dmaRequested |= toReq;
        dmaEngineUpdateDMADREQ();
    }

    public SoundCard() throws IOException
    {
        this("");
    }

    public void dumpStatusPartial(StatusDumper output)
    {
        super.dumpStatusPartial(output);
        output.println("\tbaseIOAddress " + baseIOAddress);
        output.println("\tirq8Bit " + irq8Bit);
        output.println("\tirq16Bit " + irq16Bit);
        output.println("\tirq2 " + irq2);
        output.println("\tirq5 " + irq5);
        output.println("\tirq7 " + irq7);
        output.println("\tirq10 " + irq10);
        output.println("\tdmaMask " + dmaMask);
        output.println("\tdmaRequested " + dmaRequested);
        output.println("\tioportRegistered " + ioportRegistered);
        output.println("\tirqController <object #" + output.objectNumber(irqController) + ">"); if(irqController != null) irqController.dumpStatus(output);
        output.println("\tprimaryDMAController <object #" + output.objectNumber(primaryDMAController) + ">"); if(primaryDMAController != null) primaryDMAController.dumpStatus(output);
        output.println("\tsecondaryDMAController <object #" + output.objectNumber(secondaryDMAController) + ">"); if(secondaryDMAController != null) secondaryDMAController.dumpStatus(output);
        output.println("\tclock <object #" + output.objectNumber(clock) + ">"); if(clock != null) clock.dumpStatus(output);
        output.println("\ttimer <object #" + output.objectNumber(timer) + ">"); if(timer != null) timer.dumpStatus(output);
        output.println("\tpcmOutput <object #" + output.objectNumber(pcmOutput) + ">"); if(pcmOutput != null) pcmOutput.dumpStatus(output);

        output.println("\tmixerIndex " + mixerIndex);
        output.println("\tmixerPrevIndex " + mixerPrevIndex);
        output.printArray(mixerRegisters, "mixerRegisters");

        output.println("\tspeakerConnected " + speakerConnected);

        for(int i = 0; i < fmTimers.length; i++) {
            output.println("\tfmTimers[i] <object #" + output.objectNumber(fmTimers[i]) + ">"); if(fmTimers[i] != null) fmTimers[i].dumpStatus(output);
        }
        output.println("\tfmRegIndex " + fmRegIndex);
        output.println("\tfmRegValues:");
        output.printArray(fmRegValues, "fmRegValues");
        output.println("\tfmOutput <object #" + output.objectNumber(fmOutput) + ">"); if(fmOutput != null) fmOutput.dumpStatus(output);

        output.printArray(dspOutput, "dspOutput");
        output.println("\tdspOutputUsed " + dspOutputUsed);
        output.println("\tdspCommandState " + dspCommandState);
        output.println("\tdspNextAttention " + dspNextAttention);

        output.println("\tdmaState " + dmaState);
        output.println("\tsamplesLeft " + samplesLeft);
        output.println("\torigSamplesLeft " + origSamplesLeft);
        output.println("\tsoundFormat " + soundFormat);
        output.println("\tstereo " + stereo);
        output.println("\tinterSampleTime " + interSampleTime);
        output.println("\tistStereoAdjust " + istStereoAdjust);
        output.println("\tdmaActiveMask " + dmaActiveMask);
        output.println("\tdmaRequest " + dmaRequest);
        output.println("\tdmaRequestTime " + dmaRequestTime);
        output.println("\tpartialSample " + partialSample);
        output.println("\tpartialSampleBytes " + partialSampleBytes);
        output.println("\twholeSampleBytes " + wholeSampleBytes);
        output.println("\tbyteBuffer " + byteBuffer);
        output.println("\tbyteBufferSamples " + byteBufferSamples);
        output.println("\tadpcmReference " + adpcmReference);
        output.println("\tadpcmScale " + adpcmScale);
        output.println("\tnextSampleTime " + nextSampleTime);
        output.println("\tdmaPaused " + dmaPaused);
        output.println("\tdmaPauseLeft " + dmaPauseLeft);
        output.println("\tdspArgumentRegister " + dspArgumentRegister);
        output.println("\tdspNextDMA " + dspNextDMA);
        output.println("\tdspLastCommand " + dspLastCommand);

        output.println("\te2Mode " + e2Mode);
        output.println("\te2Value " + e2Value);
        output.println("\te2Count " + e2Count);
    }

    public void dumpStatus(StatusDumper output)
    {
        if(output.dumped(this))
            return;

        output.println("#" + output.objectNumber(this) + ": SoundCard:");
        dumpStatusPartial(output);
        output.endObject();
    }

    public void DEBUGOPTION_soundcard_transfer_debugging(boolean _state)
    {
        soundDebuggingEnabled = _state;
    }



    public boolean initialised()
    {
        return ((irqController != null) && (clock != null) && (primaryDMAController != null) && (secondaryDMAController != null) && ioportRegistered);
    }

    public void acceptComponent(HardwareComponent component)
    {
        if((component instanceof InterruptController) && component.initialised()) {
            irqController = (InterruptController)component;
        }
        if((component instanceof DMAController) && ((DMAController)component).isPrimary() && component.initialised()) {
            primaryDMAController = (DMAController)component;
            grabDMAChannels();
        }
        if((component instanceof DMAController) && !((DMAController)component).isPrimary() && component.initialised()) {
            secondaryDMAController = (DMAController)component;
            grabDMAChannels();
        }
        if((component instanceof Clock) && component.initialised()) {
            clock = (Clock)component;
            timer = clock.newTimer(this);
        }

        if((component instanceof IOPortHandler) && component.initialised() && !ioportRegistered) {
            ((IOPortHandler)component).registerIOPortCapable(this);
            ioportRegistered = true;
        }

    }

    public int[] ioPortsRequested()
    {
        int[] ret;
        ret = new int[20];
        for(int i = 0; i < 16; i++)
            ret[i] = baseIOAddress + i;
        ret[ret.length - 4] = 0x388;
        ret[ret.length - 3] = 0x389;
        ret[ret.length - 2] = 0x38A;
        ret[ret.length - 1] = 0x38B;
        return ret;
    }

    public int getTimerType()
    {
        return 36;
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
        if(address >= baseIOAddress && address < baseIOAddress + 16)
            if((address - baseIOAddress & ~1) != 8)
                ioWrite(address - baseIOAddress, data);
            else
                ioWrite(address - baseIOAddress - 8, data);
        else if(address >= 0x388 && address < 0x38C)
            ioWrite(address - 0x388, data);
    }

    public int ioPortReadByte(int address)
    {
        int value = -1;
        if(address >= baseIOAddress && address < baseIOAddress + 16)
            if((address - baseIOAddress & ~1) != 8)
                value = ioRead(address - baseIOAddress);
            else
                value = ioRead(address - baseIOAddress - 8);
        else if(address >= 0x388 && address < 0x38C)
            value = ioRead(address - 0x388);

        return value;
    }

    public int handleTransfer(DMAController.DMAChannel channel, int position, int size)
    {
        byte[] buf = new byte[1];
        int avail = size - position;

        //Some weird command has DMA writing some mysterious values.
        if(e2Mode) {
            buf[0] = e2Value;
            channel.writeMemory(buf, 0, position, 1);
            e2Mode = false;
            dmaEngineUpdateDMADREQ();
            return position + 1;
        }

        while(avail > 0 && partialSampleBytes < wholeSampleBytes) {
            channel.readMemory(buf, 0, position, 1);
            partialSample |= (((int)buf[0] & 0xFF) << (8 * partialSampleBytes));
            position++;
            partialSampleBytes++;
            avail--;
        }
        dmaEngineUpdateDMADREQ();
        if(partialSampleBytes == wholeSampleBytes)
            dmaEngineSampleAvailable();
        return position;
    }

    private boolean isOPL3()
    {
        return ((fmRegValues[0x105] & 0x01) != 0);
    }

    //Read from fm chip I/O space (ports 0-3).
    public int readFMIOSpace(long ts, int port)
    {
        port &= 0x01;        //0 and 2 are the same, as are 1 and 3.
        if(port == 0) {      //register select / status port.
            int value = 0;   //Bit 1 and 2 low for OPL3.
            for(FMTimerCounter c : fmTimers)
                value |= c.readPartialStatus(ts);
            return value;
        } else {             //Data port.
            System.err.println("FMChip: Attempted to read the data register.");
            return 0;         //Let's do like opl.cpp does.
        }
    }

    //Write into fm chip I/O space (ports 0-3).
    public void writeFMIOSpace(long ts, int port, int data)
    {
        port &= 0x03;
        data &= 0xFF;
        boolean handled = false;
        switch(port) {
        case 0:
            fmRegIndex = data;
            break;
        case 2:
            //If OPL3 mode is enabled, select into extended registers (exception: register 5, which always selects
            //into extended registers).
            fmRegIndex = data + ((data == 5 || isOPL3()) ? 0x100 : 0);
            break;
        case 1:
        case 3:
            for(FMTimerCounter c : fmTimers)
                handled |= c.writeRegister(ts, fmRegIndex, data);
            if(!handled) {
                //Just dump the raw output data.
                fmOutput.addFrameWrite(ts, (short)fmRegIndex, (byte)data);
                fmRegValues[fmRegIndex] = data;
            }
            break;
        }
    }


    public int requestedSoundChannels()
    {
        return 2;
    }

    public void soundChannelCallback(Output out, String name)
    {
        if(pcmOutput == null)
            pcmOutput = new OutputChannelPCM(out, name);
        else if(fmOutput == null)
            fmOutput = new OutputChannelFM(out, name);
        recomputeVolume(0);  //These always happen at zero time.
    }

    //Set IRQ line status.
    private final void setIRQ(int status)
    {
        if(irq2)
            irqController.setIRQ(2, status);
        if(irq5)
            irqController.setIRQ(5, status);
        if(irq7)
            irqController.setIRQ(7, status);
        if(irq10)
            irqController.setIRQ(10, status);
    }

    //Read DMA Select register.
    private final int readDMARegister()
    {
        return dmaMask;
    }

    //Write DMA Select register.
    private final void writeDMARegister(int value)
    {
        value = value & 0xEB;   //Mask invalid channels.
        dmaMask = value;
        grabDMAChannels();
    }

    //Read IRQ select register.
    private final int readIRQRegister()
    {
        int value = 0xF0;
        if(irq2)
            value |= 1;
        if(irq5)
            value |= 2;
        if(irq7)
            value |= 4;
        if(irq10)
            value |= 8;
        return value;
    }

    //Write IRQ select register.
    private final void writeIRQRegister(int value)
    {
        irq2 = ((value & 1) != 0);
        irq5 = ((value & 2) != 0);
        irq7 = ((value & 4) != 0);
        irq10 = ((value & 8) != 0);
    }

    //Reqd IRQ Status register value.
    private final int readIRQStatus()
    {
        return typeFlag | (irq8Bit ? 1 : 0) | (irq16Bit ? 2 : 0);
    }

    //Set or clear 8 Bit requesting IRQ status.
    private final void set8BitIRQ(boolean status)
    {
        boolean oldActive = irq8Bit || irq16Bit;
        irq8Bit = status;
        boolean newActive = irq8Bit || irq16Bit;
        if(oldActive != newActive)
            setIRQ(newActive ? 1 : 0);
    }

    //Set or clear 16 Bit requesting IRQ status.
    private final void set16BitIRQ(boolean status)
    {
        boolean oldActive = irq8Bit || irq16Bit;
        irq16Bit = status;
        boolean newActive = irq8Bit || irq16Bit;
        if(oldActive != newActive)
            setIRQ(newActive ? 1 : 0);
    }

    private final void writeMessage(String message)
    {
        writeMessage(message, false);
    }

    private final void writeMessage(String message, boolean priority)
    {
        if(!priority) {
            if(!soundDebuggingEnabled)
                return;
            else
                message = "Informational: " + message;
        }
        if(message.equals(lastMessage))
            portRepeats++;
        else {
            if(portRepeats > 1)
                System.err.println("<Last message repeted " + portRepeats + " times.>");
            System.err.println(message);
            lastMessage = message;
            portRepeats = 1;
        }
    }

    //Do I/O write. Possible offsets are 0-7 and 10-15.
    public void ioWrite(int offset, int dataByte)
    {
        switch(offset) {
        case 0:
        case 1:
        case 2:
        case 3:
            writeFMIOSpace(clock.getTime(), offset, dataByte);
            updateTimer();
            return;
        case 4:
            mixerPrevIndex = mixerIndex;
            if(!validMixerRegister(mixerPrevIndex, 0))
                mixerPrevIndex |= 0x80;
            mixerIndex = dataByte;
            return;
        case 5:
            writeMixer(mixerIndex, dataByte);
            return;
        case 6:
            writeMessage("SB: Resetting card.");
            doReset(dataByte);
            return;
        case 12:
            dspWrite(dataByte);
            return;
        case 7:
        case 8:
        case 9:
        case 10:
        case 11:
        case 13:
        case 14:
        case 15:
        default:
            writeMessage("SB: Attempted write to port " + offset + ".");
            //Not writable.
            return;
        }
    }

    //Do I/O read. Possible offsets are 0-7 and 10-15.
    public int ioRead(int offset)
    {
        int tmp;
        switch(offset) {
        case 0:
        case 2:
        case 1:
        case 3:
            return readFMIOSpace(clock.getTime(), offset);
        case 4:
            return mixerIndex;
        case 5:
            tmp = readMixer(mixerIndex);
            return tmp;
        case 10:
            tmp = dspRead();
            return tmp;
        case 12:
            return writeBufferStatus();
        case 13:
            //This is undocumented. What it does?
            return 255;
        case 14:
            set8BitIRQ(false);
            tmp = dspDataAvailableStatus();
        case 15:
            set16BitIRQ(false);
            return -1;
        case 6:
        case 7:
        case 8:
        case 9:
        case 11:
        default:
            writeMessage("SB: Attempted read from port " + offset + ".");
            return 255;  //Not readable.
        }
    }

    public void resetCard()
    {
        irq8Bit = false;
        irq16Bit = false;
        mixerIndex = 128;
        mixerPrevIndex = 128;
        dspOutputUsed = 0;
        dspLastCommand = 0;
        setIRQ(0);
        speakerConnected = false;
        dspCommandState = DSPSTATE_WAIT_COMMAND;
        resetMixer();
        dmaEngineKillTransfer();
        e2Mode = false;
        e2Value = (byte)0xAA;
        e2Count = 0;
        for(FMTimerCounter c : fmTimers)
            c.reset();
        fmRegIndex = 0;
        Arrays.fill(fmRegValues, 0);
        if(fmOutput != null)
            fmOutput.addFrameReset((clock != null) ? clock.getTime() : 0);
    }

    public void reset()
    {
        ioportRegistered = false;
        resetCard();
    }

    private final int writeBufferStatus()
    {
        return 127;
    }

    private final int dspDataAvailableStatus()
    {
        return 127 | ((dspOutputUsed > 0) ? 128 : 0);
    }

    //Mode >=0 is read, <=0 is write.
    private final boolean validMixerRegister(int reg, int mode)
    {
        if(reg == MIXREG_IRQSELECT || reg == MIXREG_DMASELECT || reg == MIXREG_DAC || reg == MIXREG_MIC || reg == MIXREG_OUTPUT_CONTROL)
            return true;
        if(reg == MIXREG_MASTER || reg == MIXREG_FM || reg == MIXREG_CD || reg == MIXREG_LINEIN)
            return true;
        if(reg == MIXREG_IRQSTATUS && mode >= 0)
            return true;
        if(reg < 0 || reg > MIXREG_LAST)
            return false;
        if(reg == MIXREG_RESET)
            return (mode <= 0);
        if(reg == MIXREG_STATUS)
            return (mode >= 0);
        if(reg >= MIXREG_SB16_FIRST)
            return true;
        return false;
    }

    //Write Mixer data register.
    private final void writeMixer(int reg, int data)
    {
        if(reg == MIXREG_IRQSELECT) {
            writeIRQRegister(data);
            return;
        }
        if(reg == MIXREG_DMASELECT) {
            writeDMARegister(data);
            return;
        }
        if(reg == MIXREG_RESET) {
            resetMixer();
            return;
        }
        if(reg == MIXREG_DAC) {
            mixerRegisters[MIXER_DAC_LEFT] = (data & 0xF0) | 0x08;
            mixerRegisters[MIXER_DAC_RIGHT] = ((data & 0xF) << 4) | 0x08;
            recomputeVolume(clock.getTime());
            return;
        }
        if(reg == MIXREG_MIC) {
            mixerRegisters[MIXER_MIC] = (data & 0x07) << 5;
            return;
        }
        if(reg == MIXREG_OUTPUT_CONTROL) {
            mixerRegisters[MIXER_OUTPUT_CONTROL] = data;
            return;
        }
        if(reg == MIXREG_MASTER) {
            mixerRegisters[MIXER_MASTER_LEFT] = (data & 0xF0) | 0x08;
            mixerRegisters[MIXER_MASTER_RIGHT] = ((data & 0xF) << 4) | 0x08;
            recomputeVolume(clock.getTime());
            return;
        }
        if(reg == MIXREG_FM) {
            mixerRegisters[MIXER_FM_LEFT] = (data & 0xF0) | 0x08;
            mixerRegisters[MIXER_FM_RIGHT] = ((data & 0xF) << 4) | 0x08;
            recomputeVolume(clock.getTime());
            return;
        }
        if(reg == MIXREG_CD) {
            mixerRegisters[MIXER_CD_LEFT] = (data & 0xF0) | 0x08;
            mixerRegisters[MIXER_CD_RIGHT] = ((data & 0xF) << 4) | 0x08;
            return;
        }
        if(reg == MIXREG_LINEIN) {
            mixerRegisters[MIXER_LINEIN_LEFT] = (data & 0xF0) | 0x08;
            mixerRegisters[MIXER_LINEIN_RIGHT] = ((data & 0xF) << 4) | 0x08;
            return;
        }
        if(!validMixerRegister(reg, -1))
            return; //Bad register.

        mixerRegisters[reg - MIXREG_SB16_FIRST] = data;
        recomputeVolume(clock.getTime());
    }

    //Read Mixer data register.
    private final int readMixer(int reg)
    {
        if(reg == MIXREG_IRQSELECT)
            return readIRQRegister();
        if(reg == MIXREG_DMASELECT)
            return readDMARegister();
        if(reg == MIXREG_IRQSTATUS)
            return readIRQStatus();
        if(reg == MIXREG_STATUS)
            return mixerPrevIndex;
        if(reg == MIXREG_DAC)
            return (mixerRegisters[MIXER_DAC_LEFT] & 0xF0) | ((mixerRegisters[MIXER_DAC_RIGHT] & 0xF0) >> 4);
        if(reg == MIXREG_MIC)
            return (mixerRegisters[MIXER_MIC] >> 5);
        if(reg == MIXREG_OUTPUT_CONTROL)
            return mixerRegisters[MIXER_OUTPUT_CONTROL];
        if(reg == MIXREG_MASTER)
            return (mixerRegisters[MIXER_MASTER_LEFT] & 0xF0) | ((mixerRegisters[MIXER_MASTER_RIGHT] & 0xF0) >> 4);
        if(reg == MIXREG_FM)
            return (mixerRegisters[MIXER_FM_LEFT] & 0xF0) | ((mixerRegisters[MIXER_FM_RIGHT] & 0xF0) >> 4);
        if(reg == MIXREG_CD)
            return (mixerRegisters[MIXER_CD_LEFT] & 0xF0) | ((mixerRegisters[MIXER_CD_RIGHT] & 0xF0) >> 4);
        if(reg == MIXREG_LINEIN)
            return (mixerRegisters[MIXER_LINEIN_LEFT] & 0xF0) | ((mixerRegisters[MIXER_LINEIN_RIGHT] & 0xF0) >> 4);
        if(!validMixerRegister(reg, 1))
            return -1; //Bad register.

        return mixerRegisters[reg - MIXREG_SB16_FIRST];
    }

    private final void resetMixer()
    {
        mixerRegisters[MIXER_MASTER_LEFT] = 0xC0;
        mixerRegisters[MIXER_MASTER_RIGHT] = 0xC0;
        mixerRegisters[MIXER_DAC_LEFT] = 0xC0;
        mixerRegisters[MIXER_DAC_RIGHT] = 0xC0;
        mixerRegisters[MIXER_FM_LEFT] = 0xC0;
        mixerRegisters[MIXER_FM_RIGHT] = 0xC0;
        mixerRegisters[MIXER_CD_LEFT] = 0x00;
        mixerRegisters[MIXER_CD_RIGHT] = 0x00;
        mixerRegisters[MIXER_LINEIN_LEFT] = 0x00;
        mixerRegisters[MIXER_LINEIN_RIGHT] = 0x00;
        mixerRegisters[MIXER_MIC] = 0x00;
        mixerRegisters[MIXER_PCSPEAKER] = 0x00;
        mixerRegisters[MIXER_OUTPUT_CONTROL] = 0x1F;
        mixerRegisters[MIXER_INPUT_CONTROL_LEFT] = 0x15;
        mixerRegisters[MIXER_INPUT_CONTROL_RIGHT] = 0x0B;
        mixerRegisters[MIXER_INPUT_GAIN_CONTROL_LEFT] = 0x00;
        mixerRegisters[MIXER_INPUT_GAIN_CONTROL_RIGHT] = 0x00;
        mixerRegisters[MIXER_OUTPUT_GAIN_CONTROL_LEFT] = 0x00;
        mixerRegisters[MIXER_OUTPUT_GAIN_CONTROL_RIGHT] = 0x00;
        mixerRegisters[MIXER_AUTOMATIC_GAIN_CONTROL] = 0x00;
        mixerRegisters[MIXER_TREBLE_LEFT] = 0x80;
        mixerRegisters[MIXER_TREBLE_RIGHT] = 0x80;
        mixerRegisters[MIXER_BASS_LEFT] = 0x80;
        mixerRegisters[MIXER_BASS_RIGHT] = 0x80;
        mixerRegisters[MIXER_OUTPUT_CONTROL] = 0;
        if(clock != null)
            recomputeVolume(clock.getTime());
        else
            recomputeVolume(0);
    }

    private final void recomputeVolume(long timeStamp)
    {
        int denominator = 255 * 255;
        int masterLeft = mixerRegisters[MIXER_MASTER_LEFT];
        int masterRight = mixerRegisters[MIXER_MASTER_RIGHT];
        int dacLeft = mixerRegisters[MIXER_DAC_LEFT];
        int dacRight = mixerRegisters[MIXER_DAC_RIGHT];
        int fmLeft = mixerRegisters[MIXER_FM_LEFT];
        int fmRight = mixerRegisters[MIXER_FM_RIGHT];
        int gainLeft = 1 << (mixerRegisters[MIXER_OUTPUT_GAIN_CONTROL_LEFT] >> 6);
        int gainRight = 1 << (mixerRegisters[MIXER_OUTPUT_GAIN_CONTROL_RIGHT] >> 6);

        int pcmAmpLeft = masterLeft * dacLeft * gainLeft;
        int pcmAmpRight = masterRight * dacRight * gainRight;
        int fmAmpLeft = masterLeft * fmLeft * gainLeft;
        int fmAmpRight = masterRight * fmRight * gainRight;
        if(pcmOutput != null)
            pcmOutput.addFrameVolumeChange(timeStamp, pcmAmpLeft, denominator, pcmAmpRight, denominator);
        if(fmOutput != null)
            fmOutput.addFrameVolumeChange(timeStamp, fmAmpLeft, denominator, fmAmpRight, denominator);
    }

    //Send PCM sample, taking mixer into account.
    private final void sendPCMSample(long timestamp, short left, short right)
    {
        pcmOutput.addFrameSampleStereo(timestamp, left, right);
    }

    //Read from DSP.
    private final int dspRead()
    {
        int value = dspOutput[0];
        if(dspOutputUsed > 1)
            System.arraycopy(dspOutput, 1, dspOutput, 0, dspOutputUsed - 1);
        dspOutputUsed--;
        return value;
    }

    //Write to reset register.
    private final void doReset(int resetValue)
    {
        resetCard();
        dspOutput[0] = 0xAA;
        dspOutputUsed = 1;
    }

    //Recompute value for timer expiry.
    private final void updateTimer()
    {
        long nextTime = TIME_NEVER;
        nextTime = Math.min(nextTime, dspNextAttention);
        for(FMTimerCounter c : fmTimers)
            nextTime = Math.min(nextTime, c.getExpires());
        if(timer != null)
            if(nextTime != TIME_NEVER)
                timer.setExpiry(nextTime);
            else
                timer.disable();
    }

    public void callback()
    {
        long timeNow = clock.getTime();
        boolean runAny = true;
        while(runAny) {
            runAny = false;
            if(dspNextAttention <= timeNow) {
                dspAttention(dspNextAttention);
                runAny = true;
            }
            for(FMTimerCounter c : fmTimers)
                runAny |= c.service(timeNow);
            if(runAny)
                updateTimer();
        }
    }

    //Put byte into DSP output queue.
    private final void dspOutputPut(int value)
    {
        dspOutput[dspOutputUsed++] = value;
    }

    private final String interpretMode(int mode)
    {
        if(mode == 0)
            return "NONE";
        else if(mode == 1)
            return "SC";
        else if(mode == 2)
            return "AI";
        else if(mode == 3)
            return "AI_EXIT";
        else
            return "UNKNOWN(" + mode + ")";
    }

    private final String interpretFormat(int format)
    {
        switch(format) {
        case SNDFMT_2BIT_ADPCM:
            return "ADPCM_2_BIT";
        case SNDFMT_26BIT_ADPCM:
            return "ADPCM_2.6_BIT";
        case SNDFMT_4BIT_ADPCM:
            return "ADPCM_4_BIT";
        case SNDFMT_2BIT_ADPCM_REF:
            return "ADPCM_2_BIT_REF";
        case SNDFMT_26BIT_ADPCM_REF:
            return "ADPCM_2.6_BIT_REF";
        case SNDFMT_4BIT_ADPCM_REF:
            return "ADPCM_4_BIT_REF";
        case SNDFMT_8BIT_PCM_SIGNED:
            return "PCM_8BIT_SIGNED";
        case SNDFMT_8BIT_PCM_UNSIGNED:
            return "PCM_8BIT_PCM_UNSIGNED";
        case SNDFMT_16BIT_PCM_LE_SIGNED:
            return "PCM_16BIT_LE_SIGNED";
        case SNDFMT_16BIT_PCM_LE_UNSIGNED:
            return "PCM_16BIT_LE_UNSIGNED";
        case SNDFMT_16BIT_PCM_BE_SIGNED:
            return "PCM_16BIT_BE_SIGNED";
        case SNDFMT_16BIT_PCM_BE_UNSIGNED:
            return "PCM_16BIT_BE_UNSIGNED";
        default:
            return "UNKNOWN(" + format + ")";
        }
    }

    //Kick-start DMA transfer.
    private final void dmaEngineStartTransfer(int mode, int samples, int format, boolean stereoFlag)
    {
        long timeNow = clock.getTime();
        dmaState = mode;
        samplesLeft = origSamplesLeft = samples;
        soundFormat = format;
        stereo = stereoFlag;
        partialSample = 0;
        partialSampleBytes = 0;
        wholeSampleBytes = 0;
        byteBuffer = 0;
        byteBufferSamples = 0;
        dmaPaused = false;
        dspNextAttention = nextSampleTime = timeNow;
        updateTimer();
        writeMessage("SBDSP: Starting DMA: mode=" + interpretMode(mode) + " samples=" + samples + " format=" +
            interpretFormat(format) + " stereoFlag=" + stereoFlag);
    }

    //Kill DMA transfer.
    private final void dmaEngineKillTransfer()
    {
        int activeChannels = dmaActiveMask;
        dmaState = DMA_NONE;
        dmaPaused = false;
        partialSampleBytes = wholeSampleBytes = partialSample = 0;
        dmaEngineUpdateDMADREQ();
        dspNextAttention = TIME_NEVER;
        updateTimer();
        if(activeChannels != 0)
            writeMessage("SBDSP: Killed DMA transfer.");
    }

    //End DMA transfer.
    private final void dmaEngineEndTransfer()
    {
        if(dmaState == DMA_CONTINUOUS) {
            writeMessage("SBDSP: Exiting DMA transfer.");
            dmaState = DMA_CONTINUOUS_EXIT;
        }
    }

    private final void dmaEnginePauseTransfer()
    {
        dmaPaused = true;
        dmaPauseLeft = -1;
        dspNextAttention = TIME_NEVER;
        updateTimer();
        writeMessage("SBDSP: Pausing DMA transfer.");
    }

    private final void dmaEnginePauseTransfer(int samples)
    {
        dmaPaused = true;
        dmaPauseLeft = samples;
        writeMessage("SBDSP: Pausing DMA transfer for " + samples + " samples.");
    }

    private final void dmaEngineContinueTransfer()
    {
        dmaPaused = false;
        dspNextAttention = nextSampleTime = clock.getTime();
        updateTimer();
        writeMessage("SBDSP: Continuing DMA transfer.");
    }

    private static final boolean is16Bit(int fmt)
    {
        switch(fmt) {
        case SNDFMT_16BIT_PCM_LE_SIGNED:
        case SNDFMT_16BIT_PCM_LE_UNSIGNED:
        case SNDFMT_16BIT_PCM_BE_SIGNED:
        case SNDFMT_16BIT_PCM_BE_UNSIGNED:
            return true;
        default:
            return false;
        }
    }

    //Ok, one sample down. Handle us to be recalled if needed.
    private final void dmaEngineNextSample()
    {
        //Clear remainants of preseent sample.
        partialSample = 0;
        partialSampleBytes = 0;
        wholeSampleBytes = 0;

       if(dmaPaused && dmaPauseLeft > 0) {
           --dmaPauseLeft;
           if(dmaPauseLeft == 0) {
               writeMessage("SBDSP: Continuing DMA transfer after timed pause.");
               set8BitIRQ(true);
               dmaPaused = false;
           }
       }

       if(dmaState == DMA_NONE || dmaPaused) {
            if(dmaPaused)
                writeMessage("Halting paused transfer.");
            dspNextAttention = TIME_NEVER;
            updateTimer();
            return;
        }

        if(samplesLeft > 0)
            samplesLeft -= (stereo ? 2 : 1);

        if(samplesLeft <= 0) {
             //Block run out.
            if(is16Bit(soundFormat))
                set16BitIRQ(true);
            else
                set8BitIRQ(true);
            dspProgramNextDMA();
            if(dmaState == DMA_CONTINUOUS) {
                writeMessage("SBDSP: DMA transfer auto-reinitialized.");
                samplesLeft = origSamplesLeft;
            } else {
                //DMA mode exit.
                writeMessage("SBDSP: DMA transfer ended.");
                dmaState = DMA_NONE;
                dspNextAttention = TIME_NEVER;
                updateTimer();
                return;
            }
        }

        long timeNow = clock.getTime();
        long ist = interSampleTime;
        //Some modes output at half rate in stereo mode.
        if(stereo && istStereoAdjust)
            ist *= 2;
        if(dmaRequestTime == timeNow) {
            //Timely, schedule by supposed time.
            nextSampleTime = nextSampleTime + ist;
        } else {
            //Late. Schecdule by current time.
            nextSampleTime = timeNow + ist;
        }
        dspNextAttention = nextSampleTime;
        updateTimer();
    }

    //Update DMA engine DMA requests.
    private final void dmaEngineUpdateDMADREQ()
    {
        if(!dmaRequest && partialSampleBytes < wholeSampleBytes) {
            dmaRequest = true;
            dmaRequestTime = clock.getTime();
        }


        boolean highDMAAvailable = is16Bit(soundFormat) && ((dmaMask & 0xF0) != 0) && !e2Mode;
        int dmaProg = dmaMask & ~dmaActiveMask;
        boolean dmaActive = (partialSampleBytes < wholeSampleBytes) || e2Mode;

        if(highDMAAvailable) {
            for(int i = 0; i < 4; i++)
                if(secondaryDMAController != null && dmaActive && ((((dmaProg >> 4) >> i) & 1) != 0)) {
                    dmaActiveMask |= ((1 << 4) << i);
                    secondaryDMAController.holdDmaRequest(i);
                }
        }
        for(int i = 0; i < 4; i++)
            if(primaryDMAController != null && dmaActive && (((dmaProg >> i) & 1) != 0)) {
                dmaActiveMask |= (1 << i);
                primaryDMAController.holdDmaRequest(i);
            }

        if(partialSampleBytes == wholeSampleBytes && !e2Mode) {
            //Drop all transfer requests.
            dmaProg = dmaActiveMask;

            dmaRequest = false;
            for(int i = 0; i < 4; i++) {
                if(secondaryDMAController != null && (((dmaProg >> 4) >> i) & 1) != 0) {
                    secondaryDMAController.releaseDmaRequest(i);
                    dmaActiveMask &= ~((1 << 4) << i);
                }
                if(primaryDMAController != null && ((dmaProg >> i) & 1) != 0) {
                    primaryDMAController.releaseDmaRequest(i);
                    dmaActiveMask &= ~(1 << i);
                }
            }
        }
    }

    private final void dmaEngineAttention()
    {
        if(clock.getTime() < nextSampleTime)
            return;   //Not yet.
        //DMA will set next attention.
        dspNextAttention = TIME_NEVER;
        updateTimer();

        //Update DREQ if not requested yet.
        if(!dmaRequest) {
            //Count the bytes.
            int bytes = 0;
            switch(soundFormat) {
            case SNDFMT_8BIT_PCM_SIGNED:
            case SNDFMT_8BIT_PCM_UNSIGNED:
                bytes += (stereo ? 2 : 1);
                break;
            case SNDFMT_16BIT_PCM_LE_SIGNED:
            case SNDFMT_16BIT_PCM_LE_UNSIGNED:
            case SNDFMT_16BIT_PCM_BE_SIGNED:
            case SNDFMT_16BIT_PCM_BE_UNSIGNED:
                bytes += (stereo ? 4 : 2);
                break;
            case SNDFMT_2BIT_ADPCM:
            case SNDFMT_26BIT_ADPCM:
            case SNDFMT_4BIT_ADPCM:
                //ADPCM is always mono.
                if(byteBufferSamples == 0)
                    bytes += 1;
                break;
            case SNDFMT_2BIT_ADPCM_REF:
            case SNDFMT_26BIT_ADPCM_REF:
            case SNDFMT_4BIT_ADPCM_REF:
                //ADPCM is always mono.
                bytes += 1;  //Reference
                if(byteBufferSamples == 0);
                    bytes += 1;
                break;
            }
            partialSampleBytes = 0;
            partialSample = 0;
            if(!dmaPaused) {
                wholeSampleBytes = bytes;
                dmaEngineUpdateDMADREQ();
                if(bytes == 0) {
                    //The DMA engine can't handle requests for 0 bytes, so call sample available
                    //immediately to keep the loop running.
                    dmaEngineSampleAvailable();
                }
            } else {
                wholeSampleBytes = 0;
                dmaEngineSampleAvailable();
            }
        }
    }

    //Give attention to DSP.
    private final void dspAttention(long time)
    {
        dmaEngineAttention();
    }

    private final int extractBufferByte()
    {
        int value = partialSample & 0xFF;
        partialSample = partialSample >>> 8;
        partialSampleBytes--;
        wholeSampleBytes--;
        return value;
    }

    private final int nonRefFormatFor(int fmt)
    {
        switch(fmt) {
        case SNDFMT_2BIT_ADPCM_REF:
            return SNDFMT_2BIT_ADPCM;
        case SNDFMT_26BIT_ADPCM_REF:
            return SNDFMT_26BIT_ADPCM;
        case SNDFMT_4BIT_ADPCM_REF:
            return SNDFMT_4BIT_ADPCM;
        default:
            return fmt;
        }
    }

    private final boolean hasReference(int fmt)
    {
        switch(fmt) {
        case SNDFMT_2BIT_ADPCM_REF:
        case SNDFMT_26BIT_ADPCM_REF:
        case SNDFMT_4BIT_ADPCM_REF:
            return true;
        default:
            return false;
        }
    }

    private final int byteBufferSamplesADPCM(int fmt)
    {
        switch(fmt) {
        case SNDFMT_2BIT_ADPCM:
            return 4;
        case SNDFMT_26BIT_ADPCM:
            return 3;
        case SNDFMT_4BIT_ADPCM:
            return 2;
        default:
            return 0;
        }
    }


    private final void dmaEngineSampleAvailable()
    {
        int sampleL = 0, sampleR = 0;
        int levelShift = 0;
        long timeNow = clock.getTime();


        //There's no data in DMA paused mode. Just schedule next sample.
        if(dmaPaused) {
            dmaEngineNextSample();
            return;
        }

        //Load Refrence byte if needed.
        if(hasReference(soundFormat)) {
            adpcmReference = extractBufferByte();
            adpcmScale = ADPCM_SCALE_INIT;
            soundFormat = nonRefFormatFor(soundFormat);
        }

        //Load byte buffer if needed in ADPCM modes.
        switch(soundFormat) {
        case SNDFMT_2BIT_ADPCM:
        case SNDFMT_26BIT_ADPCM:
        case SNDFMT_4BIT_ADPCM:
            if(byteBufferSamples == 0) {
                byteBuffer = extractBufferByte();
//              adpcmScale = ADPCM_SCALE_INIT;    //Um, what?
                byteBufferSamples = byteBufferSamplesADPCM(soundFormat);
            }
        }

        switch(soundFormat) {
        case SNDFMT_2BIT_ADPCM:
	    dmaEngineADPCMDecode((byteBuffer >>> 6) & 3);
            byteBuffer = byteBuffer << 2;
            byteBufferSamples--;
            sampleL = sampleR = 256 * (adpcmReference - 128);
            break;
        case SNDFMT_26BIT_ADPCM:
	    dmaEngineADPCMDecode((byteBuffer >>> 5) & 7);
            byteBuffer = byteBuffer << 3;
            byteBufferSamples--;
            sampleL = sampleR = 256 * (adpcmReference - 128);
            break;
        case SNDFMT_4BIT_ADPCM:
	    dmaEngineADPCMDecode((byteBuffer >>> 4) & 0xF);
            byteBuffer = byteBuffer << 4;
            byteBufferSamples--;
            sampleL = sampleR = 256 * (adpcmReference - 128);
            break;
        case SNDFMT_8BIT_PCM_UNSIGNED:
            levelShift = 32768;
        case SNDFMT_8BIT_PCM_SIGNED:
            //Let's exploit integer overflows!
            sampleL = 256 * extractBufferByte();
            if(stereo)
                sampleR = 256 * extractBufferByte();
            else
                sampleR = sampleL;
            break;
        case SNDFMT_16BIT_PCM_LE_UNSIGNED:
            levelShift = 32768;
        case SNDFMT_16BIT_PCM_LE_SIGNED:
            //Let's exploit integer overflows!
            sampleL = extractBufferByte();
            sampleL |= (extractBufferByte() << 8);
            if(stereo) {
                sampleR = extractBufferByte();
                sampleR |= (extractBufferByte() << 8);
            } else
                sampleR = sampleL;
            break;
        case SNDFMT_16BIT_PCM_BE_UNSIGNED:
            levelShift = 32768;
        case SNDFMT_16BIT_PCM_BE_SIGNED:
            //Let's exploit integer overflows!
            sampleL = (extractBufferByte() << 8);
            sampleL |= extractBufferByte();
            if(stereo) {
                sampleR = (extractBufferByte() << 8);
                sampleR |= extractBufferByte();
            } else
                sampleR = sampleL;
            break;
        }

        sendPCMSample(timeNow, (short)(sampleL - levelShift), (short)(sampleR - levelShift));
        dmaEngineNextSample();
    }

    //Write byte to DSP.
    private final void dspWrite(int command)
    {
        int blockSampleArg = origSamplesLeft - 1;

        if(dspCommandState == DSPSTATE_WAIT_COMMAND) {
            switch(command) {

            case DSPCMD_SC_DMA_8_1:
            case DSPCMD_SC_DMA_8_2:
            case DSPCMD_SC_DMA_8_3:
                dspArgumentRegister = 0;
                dspCommandState = DSPSTATE_OLDDMA_LOW;
                break;
            case DSPCMD_AI_DMA_8_1:
            case DSPCMD_AI_DMA_8_2:
                dspArgumentRegister = 65536 | ((blockSampleArg & 0xFF) << 8) | ((blockSampleArg & 0xFF00) >> 8);
                dspNextDMA = dspArgumentRegister;
                break;
            case DSPCMD_SC_ADPCM_2:
                dspArgumentRegister = 2;
                dspCommandState = DSPSTATE_OLDDMA_LOW;
                break;
            case DSPCMD_SC_ADPCM_2_REF:
                dspArgumentRegister = 3;
                dspCommandState = DSPSTATE_OLDDMA_LOW;
                break;
            case DSPCMD_SC_ADPCM_26:
                dspArgumentRegister = 4;
                dspCommandState = DSPSTATE_OLDDMA_LOW;
                break;
            case DSPCMD_SC_ADPCM_26_REF:
                dspArgumentRegister = 5;
                dspCommandState = DSPSTATE_OLDDMA_LOW;
                break;
            case DSPCMD_SC_ADPCM_4:
                dspArgumentRegister = 6;
                dspCommandState = DSPSTATE_OLDDMA_LOW;
                break;
            case DSPCMD_SC_ADPCM_4_REF:
                dspArgumentRegister = 7;
                dspCommandState = DSPSTATE_OLDDMA_LOW;
                break;
            case DSPCMD_GENERIC_DMA_0: case DSPCMD_GENERIC_DMA_1:  case DSPCMD_GENERIC_DMA_2: case DSPCMD_GENERIC_DMA_3:
            case DSPCMD_GENERIC_DMA_4: case DSPCMD_GENERIC_DMA_5:  case DSPCMD_GENERIC_DMA_6: case DSPCMD_GENERIC_DMA_7:
            case DSPCMD_GENERIC_DMA_8: case DSPCMD_GENERIC_DMA_9:  case DSPCMD_GENERIC_DMA_A: case DSPCMD_GENERIC_DMA_B:
            case DSPCMD_GENERIC_DMA_C: case DSPCMD_GENERIC_DMA_D:  case DSPCMD_GENERIC_DMA_E: case DSPCMD_GENERIC_DMA_F:
            case DSPCMD_GENERIC_DMA_G: case DSPCMD_GENERIC_DMA_H:  case DSPCMD_GENERIC_DMA_I: case DSPCMD_GENERIC_DMA_J:
            case DSPCMD_GENERIC_DMA_K: case DSPCMD_GENERIC_DMA_L:  case DSPCMD_GENERIC_DMA_M: case DSPCMD_GENERIC_DMA_N:
            case DSPCMD_GENERIC_DMA_O: case DSPCMD_GENERIC_DMA_P:  case DSPCMD_GENERIC_DMA_Q: case DSPCMD_GENERIC_DMA_R:
            case DSPCMD_GENERIC_DMA_S: case DSPCMD_GENERIC_DMA_T:  case DSPCMD_GENERIC_DMA_U: case DSPCMD_GENERIC_DMA_V:
                dspArgumentRegister = (command - 0xB0);
                dspCommandState = DSPSTATE_NEWDMA_LOW;
                break;
            case DSPCMD_DIRECT_DAC:
                dspCommandState = DSPSTATE_DIRECT_DAC_SAMPLE;
                break;
            case DSPCMD_SET_BLOCKSIZE:
                dspCommandState = DSPSTATE_BLOCKSIZE_LOW;
                break;
            case DSPCMD_SET_TC:
                dspCommandState = DSPSTATE_TC;
                break;
            case DSPCMD_SET_INPUT_RATE:
            case DSPCMD_SET_OUTPUT_RATE:
                dspCommandState = DSPSTATE_RATE_LOW;
                break;
            case DSPCMD_SPEAKER_ON:
                speakerConnected = true;
                recomputeVolume(clock.getTime());
                break;
            case DSPCMD_SPEAKER_OFF:
                speakerConnected = false;
                recomputeVolume(clock.getTime());
                break;
            case DSPCMD_SPEAKER_STATUS:
                if(speakerConnected)
                    dspOutputPut(0xFF);
                else
                    dspOutputPut(0x00);
                break;
            case DSPCMD_PAUSE_DMA:
            case DSPCMD_PAUSE_DMA_16:
                dmaEnginePauseTransfer();
                break;
            case DSPCMD_CONTINUE_DMA:
            case DSPCMD_CONTINUE_DMA_16:
            case DSPCMD_CONTINUE_DMA_AI:
            case DSPCMD_CONTINUE_DMA_AI_16:
                dmaEngineContinueTransfer();
                break;
            case DSPCMD_SILENCE:
                dspCommandState = DSPSTATE_SILENCE_LOW;
                dspArgumentRegister = 0;
                break;
            case DSPCMD_EXIT_DMA:
            case DSPCMD_EXIT_DMA_16:
                dmaEngineEndTransfer();
                break;
            case DSPCMD_DSP_IDENTIFY:
                 dspCommandState = DSPSTATE_IDWRITE;
                 break;
            case DSPCMD_DSP_VERSION:
                dspOutputUsed = 0;
                dspOutputPut((DSP_VERSION >>> 8) & 0xFF);
                dspOutputPut(DSP_VERSION & 0xFF);
                break;
            case DSPCMD_DMA_IDENTIFY:
                dspCommandState = DSPSTATE_DMA_IDENTIFY;
                break;
            case DSPCMD_COPYRIGHT:
                dspOutputUsed = 0;
                for(int k = 0; k < copyright.length(); k++)
                    dspOutputPut((int)copyright.charAt(k));
                break;
            case DSPCMD_WRITE_TEST:
                dspCommandState = DSPSTATE_TESTWRITE;
                break;
            case DSPCMD_READ_TEST:
                dspOutputUsed = 0;
                dspOutputPut(dspLastCommand);
                break;
            case DSPCMD_RAISE_8BIT_IRQ:
                set8BitIRQ(true);
                break;
            case DSPCMD_UNDOCUMENTED1:
                //Weird undocumented command.
                dspOutputUsed = 0;
                dspOutputPut(0);
                break;
            default:
                writeMessage("SBDSP: Received unknown command " + command + ".");
            }
        } else if(dspCommandState == DSPSTATE_DIRECT_DAC_SAMPLE) {
            writeMessage("SBDSP: Setting direct output to " + command + ".");
            sendPCMSample(clock.getTime(), (short)(256 * command - 32768), (short)(256 * command - 32768));
            dspCommandState = DSPSTATE_WAIT_COMMAND;
        } else if(dspCommandState == DSPSTATE_SILENCE_LOW) {
            dspArgumentRegister = command;
            dspCommandState = DSPSTATE_SILENCE_HIGH;
        } else if(dspCommandState == DSPSTATE_SILENCE_HIGH) {
            dspArgumentRegister |= (command << 8);
            dmaEnginePauseTransfer(dspArgumentRegister + 1);
            dspCommandState = DSPSTATE_WAIT_COMMAND;
        } else if(dspCommandState == DSPSTATE_TESTWRITE) {
            writeMessage("SBDSP: Writing test register: data=" + command + ".");
            dspLastCommand = command;
            dspCommandState = DSPSTATE_WAIT_COMMAND;
        } else if(dspCommandState == DSPSTATE_IDWRITE) {
            dspOutputUsed = 0;
            writeMessage("SBDSP: Doing ID write: data=" + command + ".");
            dspOutputPut(~command);
            dspCommandState = DSPSTATE_WAIT_COMMAND;
        } else if(dspCommandState == DSPSTATE_BLOCKSIZE_LOW) {
            dspArgumentRegister = command;
            dspCommandState = DSPSTATE_BLOCKSIZE_HIGH;
        } else if(dspCommandState == DSPSTATE_BLOCKSIZE_HIGH) {
            dspArgumentRegister |= (command << 8);
            writeMessage("SBDSP: Setting block size to " + (dspArgumentRegister + 1) + " samples.");
            origSamplesLeft = dspArgumentRegister + 1;
            dspCommandState = DSPSTATE_WAIT_COMMAND;
        } else if(dspCommandState == DSPSTATE_RATE_LOW) {
            dspArgumentRegister = command << 8;
            dspCommandState = DSPSTATE_RATE_HIGH;
        } else if(dspCommandState == DSPSTATE_RATE_HIGH) {
            dspArgumentRegister |= command;
            writeMessage("SBDSP: Setting rate to " + (dspArgumentRegister) + "Hz.");
            interSampleTime = 1000000000 / dspArgumentRegister;
            istStereoAdjust = false;
            dspCommandState = DSPSTATE_WAIT_COMMAND;
        } else if(dspCommandState == DSPSTATE_TC) {
            writeMessage("SBDSP: Setting time constant to " + command + "(" + (1000000 / (256 - command)) + "Hz mono/" + (500000 / (256 - command)) + "Hz stereo).");
            interSampleTime = 1000 * (256 - command);
            istStereoAdjust = true;
            dspCommandState = DSPSTATE_WAIT_COMMAND;
        } else if(dspCommandState == DSPSTATE_OLDDMA_LOW) {
            dspArgumentRegister = (dspArgumentRegister << 8) | command;
            dspCommandState = DSPSTATE_OLDDMA_HIGH;
        } else if(dspCommandState == DSPSTATE_NEWDMA_LOW) {
            dspArgumentRegister = (dspArgumentRegister << 8) | command;
            dspCommandState = DSPSTATE_NEWDMA_MID;
        } else if(dspCommandState == DSPSTATE_NEWDMA_MID) {
            dspArgumentRegister = (dspArgumentRegister << 8) | command;
            dspCommandState = DSPSTATE_NEWDMA_HIGH;
        } else if(dspCommandState == DSPSTATE_OLDDMA_HIGH) {
            dspArgumentRegister = (dspArgumentRegister << 8) | command;
            dspCommandState = DSPSTATE_WAIT_COMMAND;
            dspNextDMA = dspArgumentRegister;
        } else if(dspCommandState == DSPSTATE_NEWDMA_HIGH) {
            dspArgumentRegister = (dspArgumentRegister << 8) | command;
            dspCommandState = DSPSTATE_WAIT_COMMAND;
            dspArgumentRegister |= 0x20000000;
            dspNextDMA = dspArgumentRegister;
        } else if(dspCommandState == DSPSTATE_DMA_IDENTIFY) {
            //writeMessage("Old DMA identification is " + e2Value + ".");
            for(int i = 0; i < 8; i++)
                if(((command >>> i) & 1) != 0) {
                    if(((E2_MAGIC[e2Count % 4] >>> i) & 1) != 0) {
                        e2Value -= (byte)(1 << i);
                    } else {
                        e2Value += (byte)(1 << i);
                    }
                }
            e2Value += E2_MAGIC[e2Count % 4];
            writeMessage("Sending DMA identification " + e2Value + ".");
            e2Count++;
            e2Mode = true;
            dmaEngineUpdateDMADREQ();
            dspCommandState = DSPSTATE_WAIT_COMMAND;
        }

        //If no DMA, try to program it immediately. Some games rely on halting DMA to
        //be able to program it again immediately.
        if(dmaState == DMA_NONE || (dmaPaused && dmaPauseLeft < 0))
            dspProgramNextDMA();
    }

    private final void dspProgramNextDMA()
    {
        int samples;
        boolean oldStereoFlag = ((mixerRegisters[MIXER_OUTPUT_CONTROL] & 0x2) != 0);
        if(dspNextDMA == -1)
            return;   //No Next DMA.

        //Set number of samples.
        samples = 1 + (((dspNextDMA & 0xFF) << 8) | ((dspNextDMA >> 8) & 0xFF));
        dspNextDMA = dspNextDMA >>> 16;
        if(dspNextDMA < 0x2000) {
        switch(dspNextDMA) {
            case 0:         //8 Bits, Single cycle.
                dmaEngineStartTransfer(DMA_SINGLE, samples, SNDFMT_8BIT_PCM_UNSIGNED, oldStereoFlag);
                break;
            case 1:         //8 Bits, Auto-init.
                dmaEngineStartTransfer(DMA_CONTINUOUS, samples, SNDFMT_8BIT_PCM_UNSIGNED, oldStereoFlag);
                break;
            case 2:         //2 Bit ADPCM, single cycle.
                dmaEngineStartTransfer(DMA_SINGLE, samples, SNDFMT_2BIT_ADPCM, false);
                break;
            case 3:         //2 Bit ADPCM, single cycle, reference.
                dmaEngineStartTransfer(DMA_SINGLE, samples, SNDFMT_2BIT_ADPCM_REF, false);
                break;
            case 4:         //2.6 Bit ADPCM, single cycle.
                dmaEngineStartTransfer(DMA_SINGLE, samples, SNDFMT_26BIT_ADPCM, false);
                break;
            case 5:         //2.6 Bit ADPCM, single cycle, reference.
                dmaEngineStartTransfer(DMA_SINGLE, samples, SNDFMT_26BIT_ADPCM_REF, false);
                break;
            case 6:         //4 Bit ADPCM, single cycle.
                dmaEngineStartTransfer(DMA_SINGLE, samples, SNDFMT_4BIT_ADPCM, false);
                break;
            case 7:         //4 Bit ADPCM, single cycle, reference.
                dmaEngineStartTransfer(DMA_SINGLE, samples, SNDFMT_4BIT_ADPCM_REF, false);
                break;
            default:
                writeMessage("Trying to program unknown DMA mode " + dspNextDMA + ".");
            }
        } else if((dspNextDMA & 0x2000) != 0) {
            boolean bits16 = ((dspNextDMA & 0x1000) == 0);
            boolean autoinit = ((dspNextDMA & 0x400) != 0);
            boolean signed = ((dspNextDMA & 0x10) != 0);
            boolean stereoFlag = ((dspNextDMA & 0x20) != 0);
            int mode = (autoinit ? DMA_CONTINUOUS : DMA_SINGLE);
            if(bits16)
                if(signed)
                    dmaEngineStartTransfer(mode, samples, SNDFMT_16BIT_PCM_LE_SIGNED, stereoFlag);
                else
                    dmaEngineStartTransfer(mode, samples, SNDFMT_16BIT_PCM_LE_UNSIGNED, stereoFlag);
            else
                if(signed)
                    dmaEngineStartTransfer(mode, samples, SNDFMT_8BIT_PCM_SIGNED, stereoFlag);
                else
                    dmaEngineStartTransfer(mode, samples, SNDFMT_8BIT_PCM_UNSIGNED, stereoFlag);
        } else
                writeMessage("Trying to program unknown DMA mode " + dspNextDMA + ".");

        //DMA command processed.
        dspNextDMA = -1;
    }

    private final void dmaEngineADPCMDecode(int sample)
    {
        int scaleMax = 0;
        byte[] levelShift = null;
        int[] levelMult = null;
        int[] sampleMult = null;

        if(soundFormat == SNDFMT_2BIT_ADPCM) {
            scaleMax = ADPCM_2BIT_SCALE_MAX;
            levelShift = ADPCM_2BIT_LEVEL_SHIFT;
            levelMult = ADPCM_2BIT_LEVEL_MULT;
            sampleMult = ADPCM_2BIT_SAMPLE_MULT;
        } else if(soundFormat == SNDFMT_26BIT_ADPCM) {
            scaleMax = ADPCM_26BIT_SCALE_MAX;
            levelShift = ADPCM_26BIT_LEVEL_SHIFT;
            levelMult = ADPCM_26BIT_LEVEL_MULT;
            sampleMult = ADPCM_26BIT_SAMPLE_MULT;
        } else if(soundFormat == SNDFMT_4BIT_ADPCM) {
            scaleMax = ADPCM_4BIT_SCALE_MAX;
            levelShift = ADPCM_4BIT_LEVEL_SHIFT;
            levelMult = ADPCM_4BIT_LEVEL_MULT;
            sampleMult = ADPCM_4BIT_SAMPLE_MULT;
        }

        int index = adpcmScale;
        if(index > scaleMax) {
            writeMessage("Error: SB: ADPCM level out of range.", true);
            index = scaleMax;
        }

        byte adjust = levelShift[sample];
        if((index == 0 && adjust < 0) || (index == scaleMax && adjust > 0))
            adjust = 0;
        adpcmScale += adjust;

        adpcmReference += (levelMult[index] * sampleMult[sample] / ((index == 0) ? 2 : 1));
        if(adpcmReference < 0)
            adpcmReference = 0;
        if(adpcmReference > 255)
            adpcmReference = 255;
    }
}
