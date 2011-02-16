package org.jpc.modulesaux;

import java.io.IOException;
import org.jpc.emulator.SRDumpable;
import org.jpc.emulator.SRDumper;
import org.jpc.emulator.SRLoader;
import org.jpc.emulator.StatusDumper;
import static org.jpc.modules.SoundCard.TIME_NEVER;

//Timer counter.
public class FMTimerCounter implements SRDumpable
{
    private long expiresAt;
    private boolean masked;
    private boolean expired;
    private boolean enabled;
    private int regValue;
    private int regNumber;
    private int maskMask;
    private int controlMask;
    private int statusMask;
    private long cycleDuration;

    private static final int REG4_IRQ_RESET = 0x80;

    public FMTimerCounter(int mmask, int cmask, int smask, long cduration, int rnumber)
    {
        expiresAt = TIME_NEVER;
        regNumber = rnumber;
        maskMask = mmask;
        controlMask = cmask;
        statusMask = smask;
        cycleDuration = cduration;
    }

    public FMTimerCounter(SRLoader input) throws IOException
    {
        input.objectCreated(this);
        expiresAt = input.loadLong();
        masked = input.loadBoolean();
        expired = input.loadBoolean();
        enabled = input.loadBoolean();
        regValue = input.loadInt();
        regNumber = input.loadInt();
        maskMask = input.loadInt();
        controlMask = input.loadInt();
        statusMask = input.loadInt();
        cycleDuration = input.loadLong();
    }

    public void dumpSRPartial(SRDumper output) throws IOException
    {
        output.dumpLong(expiresAt);
        output.dumpBoolean(masked);
        output.dumpBoolean(expired);
        output.dumpBoolean(enabled);
        output.dumpInt(regValue);
        output.dumpInt(regNumber);
        output.dumpInt(maskMask);
        output.dumpInt(controlMask);
        output.dumpInt(statusMask);
        output.dumpLong(cycleDuration);
    }

    //Dump instance variables.
    public void dumpStatusPartial(StatusDumper out)
    {
        out.println("\texpiresAt " + expiresAt);
        out.println("\tmasked " + masked);
        out.println("\texpired " + expired);
        out.println("\tenabled " + enabled);
        out.println("\tregValue " + regValue);
        out.println("\tregNumber " + regNumber);
        out.println("\tmaskMask " + maskMask);
        out.println("\tcontrolMask " + controlMask);
        out.println("\tcycleDuration " + cycleDuration);
    }

    public void dumpStatus(StatusDumper output)
    {
        if(output.dumped(this))
           return;

        output.println("#" + output.objectNumber(this) + ": TimerCounter:");
        dumpStatusPartial(output);
        output.endObject();
    }

    public int readPartialStatus(long ts)
    {
        return expired ? statusMask : 0;
    }

    public boolean writeRegister(long ts, int reg, int data)
    {
        if(reg == 4) {
            //Timer control register.
            if((data & REG4_IRQ_RESET) != 0) {
                expired = false;
                recomputeExpiry(ts, true);
                return true;
            }
            if((data & maskMask) != 0) {
               masked = true;
               expired = false;
            } else
                masked = false;
            if((data & controlMask) != 0)
                 recomputeExpiry(ts, false);
            else {
                enabled = false;
                expiresAt = TIME_NEVER;
            }
        } else if(reg == regNumber) {
            regValue = data;
        }
        return (reg == 4 || reg == regNumber);
    }

    private void recomputeExpiry(long ts, boolean irqreset)
    {
        long cyclesNow = ts / cycleDuration;
        long cyclesAtExpiry = cyclesNow + (256 - regValue);
        if(!irqreset || enabled) {
            expiresAt = cyclesAtExpiry * cycleDuration;
            enabled = true;
        } else
            expiresAt = TIME_NEVER;
    }

    public void reset()
    {
        expired = enabled = masked = false;
        expiresAt = TIME_NEVER;
        regValue = 0;
    }

    public long getExpires()
    {
        return expiresAt;
    }

    public boolean service(long ts)
    {
       if(ts >= expiresAt) {
           expiresAt = TIME_NEVER;
           if(!masked)
               expired = true;
           return true;
       }
       return false;
    }
}
