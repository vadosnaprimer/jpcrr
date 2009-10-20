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


package org.jpc.emulator.processor.fpu64;

// import java.math.BigDecimal;
import org.jpc.emulator.processor.*;
import java.io.*;
import org.jpc.emulator.SRLoader;
import org.jpc.emulator.SRDumper;
import org.jpc.emulator.SRDumpable;
import org.jpc.emulator.StatusDumper;

/**
 *
 * @author Jeff Tseng
 */
public abstract class FpuState implements SRDumpable
{
    // stack depth (common to all x87 FPU's)
    public final static int STACK_DEPTH = 8;

    public static final int FPU_PRECISION_CONTROL_SINGLE = 0;
    public static final int FPU_PRECISION_CONTROL_DOUBLE = 2;
    public static final int FPU_PRECISION_CONTROL_EXTENDED = 3;

    public static final int FPU_ROUNDING_CONTROL_EVEN = 0;
    public static final int FPU_ROUNDING_CONTROL_DOWN = 1;
    public static final int FPU_ROUNDING_CONTROL_UP = 2;
    public static final int FPU_ROUNDING_CONTROL_TRUNCATE = 3;

    public static final int FPU_TAG_VALID = 0;
    public static final int FPU_TAG_ZERO = 1;
    public static final int FPU_TAG_SPECIAL = 2;
    public static final int FPU_TAG_EMPTY = 3;

    // x87 access
    public abstract void init();

    //FPU core
    //-1 => invalid. Bit 0 => update reg0, Bit 1 => update reg1, Bit 2 => update reg2, Bit3 => update reg0l
    //Bit 4 => was two-part microcode.
    public abstract int doFPUOp(int op, int nextOp, Segment seg, int addr, int reg0, int reg1, int reg2, long reg0l);
    public abstract void setProtectedMode(boolean pmode);
    public abstract int getReg0();
    public abstract int getReg1();
    public abstract int getReg2();
    public abstract long getReg0l();

    public void dumpStatus(StatusDumper output)
    {
        if(output.dumped(this))
            return;

        output.println("#" + output.objectNumber(this) + ": FpuState:");
        dumpStatusPartial(output);
        output.endObject();
    }

    public void dumpStatusPartial(StatusDumper output)
    {
    }

    public void dumpSRPartial(SRDumper output) throws IOException
    {
    }

    public FpuState()
    {
    }

    public FpuState(SRLoader input) throws IOException
    {
        input.objectCreated(this);
    }
}
