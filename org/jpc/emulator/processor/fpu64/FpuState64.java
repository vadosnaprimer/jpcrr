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
import org.jpc.emulator.StatusDumper;

import static org.jpc.emulator.memory.codeblock.optimised.MicrocodeSet.*;

/**
 *
 * @author Jeff Tseng
 */
public class FpuState64 extends FpuState
{

    public final static int FPU_SPECIAL_TAG_NONE = 0;
    public final static int FPU_SPECIAL_TAG_NAN = 1;
    public final static int FPU_SPECIAL_TAG_UNSUPPORTED = 2;
    public final static int FPU_SPECIAL_TAG_INFINITY = 3;
    public final static int FPU_SPECIAL_TAG_DENORMAL = 4;
    public final static int FPU_SPECIAL_TAG_SNAN = 5;

    public final static double UNDERFLOW_THRESHOLD = Math.pow(2.0, -1022.0);

    private final Processor cpu;

    double[] data;
    int[] tag;
    int[] specialTag;

    // status word

    private int statusWord;

    private boolean invalidOperation;
    private boolean denormalizedOperand;
    private boolean zeroDivide;
    private boolean overflow;
    private boolean underflow;
    private boolean precision;
    private boolean stackFault;

    public void dumpStatus(StatusDumper output)
    {
        if(output.dumped(this))
            return;

        output.println("#" + output.objectNumber(this) + ": FpuState64:");
        dumpStatusPartial(output);
        output.endObject();
    }

    public void dumpStatusPartial(StatusDumper output)
    {
        super.dumpStatusPartial(output);
        output.println("\tstatusWord:" + statusWord +
            (invalidOperation ? " INVOP" : "") + (denormalizedOperand ? " DENORM" : "") +
            (zeroDivide ? " DIV0" : "") + (underflow ? " UNDERFLOW" : "") +
            (overflow ? " OVERFLOW" : "") + (precision ? " INEXACT" : "") +
            (stackFault ? " STACKFAULT" : ""));
        for (int i=0; i< data.length; i++)
            output.println("\tData#" + i + " " + data[i]);
        for (int i=0; i< tag.length; i++)
            output.println("\ttag#" + i + " " + tag[i]);
        for (int i=0; i< specialTag.length; i++)
            output.println("\tspecialTag#" + i + " " + specialTag[i]);
        output.println("\tcpu <object #" + output.objectNumber(cpu) + ">"); if(cpu != null) cpu.dumpStatus(output);
    }

    public void dumpSRPartial(SRDumper output) throws IOException
    {
        super.dumpSRPartial(output);
        output.dumpObject(cpu);
        output.dumpArray(data);
        output.dumpArray(tag);
        output.dumpArray(specialTag);
        output.dumpInt(statusWord);
        output.dumpBoolean(invalidOperation);
        output.dumpBoolean(denormalizedOperand);
        output.dumpBoolean(zeroDivide);
        output.dumpBoolean(overflow);
        output.dumpBoolean(underflow);
        output.dumpBoolean(precision);
        output.dumpBoolean(stackFault);
    }

    public FpuState64(SRLoader input) throws IOException
    {
        super(input);
        cpu = (Processor)input.loadObject();
        data = input.loadArrayDouble();
        tag = input.loadArrayInt();
        specialTag = input.loadArrayInt();
        statusWord = input.loadInt();
        invalidOperation = input.loadBoolean();
        denormalizedOperand = input.loadBoolean();
        zeroDivide = input.loadBoolean();
        overflow = input.loadBoolean();
        underflow = input.loadBoolean();
        precision = input.loadBoolean();
        stackFault = input.loadBoolean();
    }

    public boolean getInvalidOperation() { return ((statusWord & 0x01) != 0); }
    public boolean getDenormalizedOperand() { return ((statusWord&0x02) != 0); }
    public boolean getZeroDivide() { return ((statusWord & 0x04) != 0); }
    public boolean getOverflow() { return ((statusWord & 0x08) != 0); }
    public boolean getUnderflow() { return ((statusWord & 0x10) != 0); }
    public boolean getPrecision() { return ((statusWord & 0x20) != 0); }
    public boolean getStackFault() { return ((statusWord & 0x40) != 0); }

    public void setInvalidOperation() { statusWord |= 0x01;}
    public void setDenormalizedOperand() { statusWord |= 0x02;}
    public void setZeroDivide() { statusWord |= 0x04;}
    public void setOverflow() { statusWord |= 0x08;}
    public void setUnderflow() {statusWord |= 0x10;}
    public void setPrecision() { statusWord |= 0x20;}
    public void setStackFault() { statusWord |= 0x40;}

    public boolean getBusy() { return getErrorSummaryStatus(); }

    public boolean getErrorSummaryStatus()
    {
        // (note stack fault is a subset of invalid operation)
        return (((statusWord & 0x3f) & ~maskWord) != 0);
    }

    public void checkExceptions() throws ProcessorException
    {
        if (getErrorSummaryStatus())
            cpu.reportFPUException();
    }

    public void clearExceptions() { statusWord = 0; }

    // control word

    private int maskWord;
    private int precisionControl;
    private int roundingControl;

    public boolean getInvalidOperationMask() { return ((maskWord & 1) != 0); }
    public boolean getDenormalizedOperandMask() { return ((maskWord & 2) != 0);}
    public boolean getZeroDivideMask() { return ((maskWord & 4) != 0); }
    public boolean getOverflowMask() { return ((maskWord & 8) != 0); }
    public boolean getUnderflowMask() { return ((maskWord & 0x10) != 0); }
    public boolean getPrecisionMask() { return ((maskWord & 0x20) != 0); }
    public int getPrecisionControl() { return precisionControl; }
    public int getRoundingControl() { return roundingControl; }

    public void setInvalidOperationMask(boolean value)
    {
        if (value) maskWord |= 1;
        else maskWord &= ~1;
    }

    public void setDenormalizedOperandMask(boolean value)
    {
        if (value) maskWord |= 2;
        else maskWord &= ~2;
    }

    public void setZeroDivideMask(boolean value)
    {
        if (value) maskWord |= 4;
        else maskWord &= ~4;
    }

    public void setOverflowMask(boolean value)
    {
        if (value) maskWord |= 8;
        else maskWord &= ~8;
    }

    public void setUnderflowMask(boolean value)
    {
        if (value) maskWord |= 0x10;
        else maskWord &= ~0x10;
    }

    public void setPrecisionMask(boolean value)
    {
        if (value) maskWord |= 0x20;
        else maskWord &= ~0x20;
    }

    public void setAllMasks(boolean value)
    {
        if (value) maskWord |= 0x3f;
        else maskWord = 0;
    }

    public void setPrecisionControl(int value)
    {
        if (value != FPU_PRECISION_CONTROL_DOUBLE)
            // trying to set precision to other than double
            System.err.println("Warning: Only double-precision math is supported by FPU64 X87 emulator.");

        precisionControl = FPU_PRECISION_CONTROL_DOUBLE;
    }

    public void setRoundingControl(int value)
    {
        if (value != FPU_ROUNDING_CONTROL_EVEN)
            // trying to set directed or truncate rounding
            System.err.println("Warning: Only nearest rounding is supported by FPU64 X87 emulator.");
        roundingControl = FPU_ROUNDING_CONTROL_EVEN;
    }

    // constructor

    public FpuState64(Processor owner)
    {
        cpu = owner;

        data = new double[STACK_DEPTH];
        tag = new int[STACK_DEPTH];
        specialTag = new int[STACK_DEPTH];
        init();
    }

    public void init()
    {
        // tag word (and non-x87 special tags)
        for (int i = 0; i < tag.length; ++i)
            tag[i] = FPU_TAG_EMPTY;
        for (int i = 0; i < specialTag.length; ++i)
            specialTag[i] = FPU_SPECIAL_TAG_NONE;
        // status word
        clearExceptions();
        conditionCode = 0;
        top = 0;
        // control word
        setAllMasks(true);
        infinityControl = false;
        setPrecisionControl(FPU_PRECISION_CONTROL_DOUBLE); // 64 bits default
            // (x87 uses 80-bit precision as default!)
        setRoundingControl(FPU_ROUNDING_CONTROL_EVEN); // default
        lastIP = lastData = lastOpcode = 0;
    }

    public int tagCode(double x)
    {
        if (x == 0.0) return FPU_TAG_ZERO;
        else if (Double.isNaN(x)||Double.isInfinite(x)) return FPU_TAG_SPECIAL;
        else return FPU_TAG_VALID;
    }

    public static boolean isDenormal(double x)
    {
        long n = Double.doubleToRawLongBits(x);
        int exponent = (int)((n >> 52) & 0x7ff);
        if (exponent != 0) return false;
        long fraction = (n & ~(0xfffL << 52));
        if (fraction == 0L) return false;
        return true;
    }

    public static boolean isSNaN(long n)
    {
        // have to determine this based on 64-bit bit pattern,
        // since reassignment might cause Java to rationalize it to infinity
        int exponent = (int)((n >> 52) & 0x7ff);
        if (exponent != 0x7ff) return false;
        long fraction = (n & ~(0xfffL << 52));
        if ((fraction & (1L << 51)) != 0) return false;
        return (fraction != 0L);
    }

    // SNaN's aren't generated internally by x87.  Instead, they are
    // detected when they are read in from memory.  So if you push()
    // from memory, find out before whether it's an SNaN, then push(),
    // then set the tag word accordingly.
    public static int specialTagCode(double x)
    {
        // decode special:  NaN, unsupported, infinity, or denormal
        if (Double.isNaN(x)) return FPU_SPECIAL_TAG_NAN; // QNaN by default
        else if (Double.isInfinite(x)) return FPU_SPECIAL_TAG_INFINITY;
        //else if (Math.abs(x) < UNDERFLOW_THRESHOLD)
        else if (isDenormal(x))
            return FPU_SPECIAL_TAG_DENORMAL;
        else return FPU_SPECIAL_TAG_NONE;
    }

    public void push(double x) throws ProcessorException
    {
        if (--top < 0) top = STACK_DEPTH - 1;
        if (tag[top] != FPU_TAG_EMPTY)
        {
            setInvalidOperation();
            setStackFault();
            conditionCode |= 2; // C1 set to indicate stack overflow
            checkExceptions();
            // if IE is masked, then we just continue and overwrite
        }
        data[top] = x;
        tag[top] = tagCode(x);
        specialTag[top] = specialTagCode(x);
    }

//     public void pushBig(BigDecimal x) throws ProcessorException
//     {
//         push(x.doubleValue());
//     }

    public double pop() throws ProcessorException
    {
        if (tag[top] == FPU_TAG_EMPTY)
        {
            setInvalidOperation();
            setStackFault();
            conditionCode &= ~2; // C1 cleared to indicate stack underflow
            checkExceptions();
            // TODO:  if IE masked, do we just return whatever
            // random contents there are?  That's what it seems
            // from the reference.
        }
        else if (specialTag[top] == FPU_SPECIAL_TAG_SNAN)
        {
            setInvalidOperation();
            checkExceptions();
            return Double.NaN; // QNaN if masked
        }
        double x = data[top];
        tag[top] = FPU_TAG_EMPTY;
        if (++top >= STACK_DEPTH) top = 0;
        return x;
    }

//     public BigDecimal popBig() throws ProcessorException
//     {
//         return new BigDecimal(pop());
//     }

    public double ST(int index) throws ProcessorException
    {
        int i = ((top + index) & 0x7);
        if (tag[i] == FPU_TAG_EMPTY)
        {
            // an attempt to read an empty register is technically
            // a "stack underflow"
            setInvalidOperation();
            setStackFault();
            conditionCode &= ~2; // C1 cleared to indicate stack underflow
            checkExceptions();
        }
        else if (specialTag[i] == FPU_SPECIAL_TAG_SNAN)
        {
            setInvalidOperation();
            checkExceptions();
            return Double.NaN; // QNaN if masked
        }
        return data[i];
    }

//     public BigDecimal bigST(int index) throws ProcessorException
//     {
//         return new BigDecimal(ST(index));
//     }

    public int getTag(int index)
    {
        int i = ((top + index) & 0x7);
        return tag[i];
    }

    public int getSpecialTag(int index)
    {
        int i = ((top + index) & 0x7);
        return specialTag[i];
    }

    public void setTagEmpty(int index)
    {
        // used by FFREE
        int i = ((top + index) & 0x7);
        tag[i] = FpuState.FPU_TAG_EMPTY;
    }

    public void setST(int index, double value)
    {
        // FST says that no exception is generated if the destination
        // is a non-empty register, so we don't generate an exception
        // here.  TODO:  check to see if this is a general rule.
        int i = ((top + index) & 0x7);
        data[i] = value;
        tag[i] = tagCode(value);
        specialTag[i] = specialTagCode(value);
    }

//     public void setBigST(int index, BigDecimal value)
//     {
//         setST(index, value.doubleValue());
//     }

    public int getStatus()
    {
        int w = statusWord;
        if (getErrorSummaryStatus()) w |= 0x80;
        if (getBusy()) w |= 0x8000;
        w |= (top << 11);
        w |= ((conditionCode & 0x7) << 8);
        w |= ((conditionCode & 0x8) << 11);
        return w;
    }

    public void setStatus(int w)
    {
        statusWord &= ~0x7f;
        statusWord |= (w & 0x7f);
        top = ((w >> 11) & 0x7);
        conditionCode = ((w >> 8) & 0x7);
        conditionCode |= ((w >>> 14) & 1);
    }

    public int getControl()
    {
        int w = maskWord;
        w |= ((precisionControl & 0x3) << 8);
        w |= ((roundingControl & 0x3) << 10);
        if (infinityControl) w |= 0x1000;
        return w;
    }

    public void setControl(int w)
    {
        maskWord &= ~0x3f;
        maskWord |= (w & 0x3f);
        infinityControl = ((w & 0x1000) != 0);
        setPrecisionControl((w >> 8) & 3);
        setRoundingControl((w >> 10) & 3);
    }

    public int getTagWord()
    {
        int w = 0;
        for (int i = STACK_DEPTH - 1; i >= 0; --i)
            w = ((w << 2) | (tag[i] & 0x3));
        return w;
    }

    public void setTagWord(int w)
    {
        for (int i = 0; i < tag.length; ++i)
        {
            int t = (w & 0x3);
            if (t == FPU_TAG_EMPTY)
            {
                tag[i] = FPU_TAG_EMPTY;
            }
            else
            {
                tag[i] = tagCode(data[i]);
                if (specialTag[i] != FPU_SPECIAL_TAG_SNAN)
                    specialTag[i] = specialTagCode(data[i]);
                // SNaN is sticky, and Java doesn't preserve the bit pattern.
            }
            w >>= 2;
        }
    }

    // STRICTLY SPEAKING, the x87 should preserve the SNaN and QNaN
    // bit pattern; v1 sec 4.8.3.6 of the manual, in fact, says that
    // these bits can be used to store diagnostic information.
    // But Java will probably eliminate all these bits to get a code
    // it understands (which looks like an infinity).  For now we
    // simply don't support using NaN bits in this way.

    public static byte[] doubleToExtended(double x, boolean isSignalNaN)
    {
        byte[] b = new byte[10];
        long fraction = 0;
        int iexp = 0;
        // other special forms?
        if (isSignalNaN)
        {
            fraction = 0xc000000000000000L; // is this right?
        }
        else
        {
            long n = Double.doubleToRawLongBits(x);
            fraction = (n & ~(0xfff << 52));
            iexp = ((int)(n >> 52) & 0x7ff);
            boolean sgn = ((n & (1 << 63)) != 0);
            // insert implicit 1
            fraction |= (1 << 52);
            fraction <<= 11;
            // re-bias exponent
            iexp += (16383 - 1023);
            if (sgn) iexp |= 0x8000;
        }
        for (int i = 0; i < 8; ++i)
        {
            b[i] = (byte)fraction;
            fraction >>>= 8;
        }
        b[8] = (byte)iexp;
        b[9] = (byte)(iexp >>> 8);
        return b;
    }

    public static int specialTagCode(byte[] b)
    {
        long fraction = 0;
        for (int i = 7; i >= 0; --i)
        {
            long w = ((long)b[i] & 0xff);
            fraction |= w;
            fraction <<= 8;
        }
        int iexp = (((int)b[8] & 0xff) | (((int)b[9] & 0x7f) << 8));
        boolean integ = ((b[7] & 0x80) != 0); // explicit integer bit

        if (iexp == 0)
        {
            if (integ)
            {
                // "pseudo-denormals" - treated like a normal denormal
                return FPU_SPECIAL_TAG_DENORMAL;
            }
            else
            {
                // normal denormals
                return FPU_SPECIAL_TAG_DENORMAL;
            }
        }
        else if (iexp == 0x7fff)
        {
            if (fraction == 0L)
            {
                // "pseudo-infinity"
                return FPU_SPECIAL_TAG_UNSUPPORTED;
            }
            else if (integ)
            {
                if ((fraction << 1) == 0)
                {
                    // infinity
                    return FPU_SPECIAL_TAG_INFINITY;
                }
                else
                {
                    // NaN's
                    if ((fraction >>> 62) == 0) return FPU_SPECIAL_TAG_SNAN;
                    else return FPU_SPECIAL_TAG_NAN;
                }
            }
            else
            {
                // pseudo-NaN
                return FPU_SPECIAL_TAG_UNSUPPORTED;
            }
        }
        else
        {
            if (integ)
            {
                // normal float
                return FPU_SPECIAL_TAG_NONE;
            }
            else
            {
                // "unnormal"
                return FPU_SPECIAL_TAG_UNSUPPORTED;
            }
        }
    }


    public static double extendedToDouble(byte[] b)
    {
        long fraction = 0;
        for (int i = 7; i >= 0; --i)
        {
            long w = ((long)b[i] & 0xff);
            fraction |= w;
            fraction <<= 8;
        }
        int iexp = (((int)b[8] & 0xff) | (((int)b[9] & 0x7f) << 8));
        boolean sgn = ((b[9] & 0x80) != 0);
        boolean integ = ((b[7] & 0x80) != 0); // explicit integer bit

        if (iexp == 0)
        {
            if (integ)
            {
                // "pseudo-denormals" - treat exponent as value 1 and
                // mantissa as the same
                // (http://www.ragestorm.net/downloads/387intel.txt)
                iexp = 1;
            }
            // now treat as a normal denormal (from denormal).
            // actually, given that min unbiased exponent is -16383 for
            // extended, and only -1023 for double, a denormalized
            // extended is pretty much zero in double!
            return 0.0;
        }
        else if (iexp == 0x7fff)
        {
            if (fraction == 0L)
            {
                // "pseudo-infinity":  if #IA masked, return QNaN
                // more technically, sign bit should be set to indicate
                // "QNaN floating-point indefinite"
                return Double.NaN;
            }
            else if (integ)
            {
                if ((fraction << 1) == 0)
                {
                    return (sgn) ? Double.NEGATIVE_INFINITY :
                                   Double.POSITIVE_INFINITY;
                }
                else
                {
                    // a conventional NaN
                    return Double.NaN;
                }
            }
            else
            {
                // pseudo-NaN
                return Double.NaN;
            }
        }
        else
        {
            if (integ)
            {
                // normal float:  decode
                iexp += 1023 - 16383; // rebias for double format
                fraction >>>= 11; // truncate rounding (is this the right way?)
                if (iexp > 0x7ff)
                {
                    // too big an exponent
                    return (sgn) ? Double.NEGATIVE_INFINITY :
                                   Double.POSITIVE_INFINITY;
                }
                else if (iexp < 0)
                {
                    // denormal (from normal)
                    fraction >>>= (- iexp);
                    iexp = 0;
                }
                fraction &= ~(0xfffL << 52); // this cuts off explicit 1
                fraction |= (((long)iexp & 0x7ff) << 52);
                if (sgn) fraction |= (1 << 63);
                return Double.longBitsToDouble(fraction);
            }
            else
            {
                // "unnormal":  if #IA masked, return QNaN FP indefinite
                return Double.NaN;
            }
        }
    }

    //These are transistent, no need to save/load.
    int reg0, reg1, reg2;
    long reg0l;
    boolean protmode;
    double freg0, freg1;

    private static final double L2TEN = Math.log(10)/Math.log(2);
    private static final double L2E = 1/Math.log(2);
    private static final double LOG2 = Math.log(2)/Math.log(10);
    private static final double LN2 = Math.log(2);
    private static final double POS0 = Double.longBitsToDouble(0x0l);

    public int doFPUOp(int op, int nextOp, Segment seg0, int addr0, int _reg0, int _reg1, int _reg2, long _reg0l)
    {
         reg0 = _reg0;
         reg1 = _reg1;
         reg2 = _reg2;
         reg0l = _reg0l;

        switch(op) {
        case FWAIT: checkExceptions(); return 0;
        case FLOAD0_ST0:
            freg0 = ST(0);
            validateOperand(freg0);
            return 0;
        case FLOAD0_STN:
            freg0 = ST(nextOp);
            validateOperand(freg0);
            return 16;
        case FLOAD0_MEM_SINGLE: {
            //     0x7f800001 thru 0x7fbfffff // SNaN Singalling
            //     0x7fc00000 thru 0x7fffffff // QNaN Quiet
            //     0xff800001 thru 0xffbfffff // SNaN Signalling
            //     0xffc00000 thru 0xffffffff // QNaN Quiet
            int n = seg0.getDoubleWord(addr0);
            freg0 = Float.intBitsToFloat(n);
            if ((Double.isNaN(freg0)) && ((n & (1 << 22)) == 0))
                setInvalidOperation();
            validateOperand(freg0);
        }   return 0;
        case FLOAD0_MEM_DOUBLE: {
            long n = seg0.getQuadWord(addr0);
            freg0 = Double.longBitsToDouble(n);
            if ((Double.isNaN(freg0)) && ((n & (0x01l << 51)) == 0))
                setInvalidOperation();
            validateOperand(freg0);
        }   return 0;
        case FLOAD0_MEM_EXTENDED:{
            byte[] b = new byte[10];
            for (int i=0; i<10; i++)
                b[i] = seg0.getByte(addr0 + i);
            freg0 = FpuState64.extendedToDouble(b);
            if ((Double.isNaN(freg0)) && ((Double.doubleToLongBits(freg0) & (0x01l << 51)) == 0))
                setInvalidOperation();
            validateOperand(freg0);}
            return 0;
        case FLOAD0_REG0:
            freg0 = (double) reg0;
            validateOperand(freg0);
            return 0;
        case FLOAD0_REG0L:
            freg0 = (double) reg0l;
            validateOperand(freg0);
            return 0;
        case FLOAD0_1:
            freg0 = 1.0;
//          validateOperand(freg0);
            return 0;
        case FLOAD0_L2TEN:
            freg0 = L2TEN;
//          validateOperand(freg0);
            return 0;
        case FLOAD0_L2E:
            freg0 = L2E;
//          validateOperand(freg0);
            return 0;
        case FLOAD0_PI:
            freg0 = Math.PI;
//          validateOperand(freg0);
            return 0;
        case FLOAD0_LOG2:
            freg0 = LOG2;
//          validateOperand(freg0);
            return 0;
        case FLOAD0_LN2:
            freg0 = LN2;
//          validateOperand(freg0);
            return 0;
        case FLOAD0_POS0:
            freg0 = POS0;
//          validateOperand(freg0);
            return 0;
        case FLOAD1_POS0:
            freg1 = POS0;
            return 0;
        case FCLEX:
            checkExceptions();
            clearExceptions();
            return 0;
        case FLOAD1_ST0:
            freg1 = ST(0);
            validateOperand(freg1);
            return 0;
        case FLOAD1_STN:
            freg1 = ST(nextOp);
            validateOperand(freg1);
            return 16;
        case FLOAD1_MEM_SINGLE: {
            int n = seg0.getDoubleWord(addr0);
            freg1 = Float.intBitsToFloat(n);
            if ((Double.isNaN(freg1)) && ((n & (1 << 22)) == 0))
                setInvalidOperation();
            validateOperand(freg1);
        }   return 0;
        case FLOAD1_MEM_DOUBLE: {
            long n = seg0.getQuadWord(addr0);
            freg1 = Double.longBitsToDouble(n);
            if ((Double.isNaN(freg1)) && ((n & (0x01l << 51)) == 0))
                setInvalidOperation();
            validateOperand(freg1);
        }   return 0;
        case FLOAD1_REG0:
            freg1 = (double) reg0;
            validateOperand(freg1);
            return 0;
        case FLOAD1_REG0L:
            freg1 = (double) reg0l;
            validateOperand(freg1);
            return 0;

        case FSTORE0_ST0: setST(0, freg0); return 0;
        case FSTORE0_STN: setST(nextOp, freg0); return 16;
        case FSTORE0_MEM_SINGLE: {
            int n = Float.floatToRawIntBits((float) freg0);
            seg0.setDoubleWord(addr0, n);
        }   return 0;
        case FSTORE0_MEM_DOUBLE: {
            long n = Double.doubleToRawLongBits(freg0);
            seg0.setQuadWord(addr0, n);
        }   return 0;
        case FSTORE0_REG0: reg0 = (int) freg0; return 1;
        case FSTORE0_MEM_EXTENDED:{
            byte[] b = FpuState64.doubleToExtended(freg0, false);
            for (int i=0; i<10; i++)
                seg0.setByte(addr0+i, b[i]);}
            return 0;

        case FSTORE1_ST0: setST(0, freg1); return 0;
        case FSTORE1_STN: setST(nextOp, freg1); return 16;
        case FSTORE1_MEM_SINGLE: {
            int n = Float.floatToRawIntBits((float) freg1);
            seg0.setDoubleWord(addr0, n);
        }   return 0;
        case FSTORE1_MEM_DOUBLE: {
            long n = Double.doubleToRawLongBits(freg1);
            seg0.setQuadWord(addr0, n);
        }   return 0;
        case FSTORE1_REG0: reg0 = (int) freg1; return 1;

        case STORE0_FPUCW: setControl(reg0); return 0;
        case LOAD0_FPUCW: reg0 = getControl(); return 1;

        case STORE0_FPUSW: setStatus(reg0); return 0;
        case LOAD0_FPUSW: reg0 = getStatus(); return 1;

        case FCOM: {
            int newcode = 0xd;
            if (Double.isNaN(freg0) || Double.isNaN(freg1))
                setInvalidOperation();
            else {
                if (freg0 > freg1) newcode = 0;
                else if (freg0 < freg1) newcode = 1;
                else newcode = 8;
            }
            conditionCode &= 2;
            conditionCode |= newcode;
        } return 0;
        case FCOMI: {
            int newcode = 0xd;
            if (Double.isNaN(freg0) || Double.isNaN(freg1))
                setInvalidOperation();
            else {
                if (freg0 > freg1) newcode = 0;
                else if (freg0 < freg1) newcode = 1;
                else newcode = 8;
            }
            conditionCode &= 2;
            conditionCode |= newcode;
        } return 0;
        case FUCOM: {
            int newcode = 0xd;
            if (!(Double.isNaN(freg0) || Double.isNaN(freg1))) {
                if (freg0 > freg1) newcode = 0;
                else if (freg0 < freg1) newcode = 1;
                else newcode = 8;
            }
            conditionCode &= 2;
            conditionCode |= newcode;
        } return 0;
        case FUCOMI:
            int newcode = 0xd;
            if (!(Double.isNaN(freg0) || Double.isNaN(freg1))) {
                if (freg0 > freg1) newcode = 0;
                else if (freg0 < freg1) newcode = 1;
                else newcode = 8;
            }
            conditionCode &= 2;
            conditionCode |= newcode;
            return 0;
        case FPOP: pop(); return 0;
        case FPUSH: push(freg0); return 0;

        case FCHS: freg0 = -freg0; return 0;
        case FABS: freg0 = Math.abs(freg0); return 0;
        case FXAM:
            int result = FpuState64.specialTagCode(ST(0));
            conditionCode = result; //wrong
            return 0;
        case F2XM1: //2^x -1
            setST(0,Math.pow(2.0,ST(0))-1);
            return 0;
        case FADD: {
            if ((freg0 == Double.NEGATIVE_INFINITY && freg1 == Double.POSITIVE_INFINITY) || (freg0 == Double.POSITIVE_INFINITY && freg1 == Double.NEGATIVE_INFINITY))
                setInvalidOperation();
            freg0 = freg0 + freg1;
        } return 0;

        case FMUL: {
            if ((Double.isInfinite(freg0) && (freg1 == 0.0)) || (Double.isInfinite(freg1) && (freg0 == 0.0)))
                setInvalidOperation();
            freg0 = freg0 * freg1;
        } return 0;

        case FSUB: {
            if ((freg0 == Double.NEGATIVE_INFINITY && freg1 == Double.NEGATIVE_INFINITY) || (freg0 == Double.POSITIVE_INFINITY && freg1 == Double.POSITIVE_INFINITY))
                setInvalidOperation();
            freg0 = freg0 - freg1;
        } return 0;
        case FDIV: {
            if (((freg0 == 0.0) && (freg1 == 0.0)) || (Double.isInfinite(freg0) && Double.isInfinite(freg1)))
                setInvalidOperation();
            if ((freg1 == 0.0) && !Double.isNaN(freg0) && !Double.isInfinite(freg0))
                setZeroDivide();
            freg0 = freg0 / freg1;
        } return 0;


        case FSQRT: {
            if (freg0 < 0)
                setInvalidOperation();
            freg0 = Math.sqrt(freg0);
        } return 0;

        case FSIN: {
            if (Double.isInfinite(freg0))
                setInvalidOperation();
            if ((freg0 > Long.MAX_VALUE) || (freg0 < Long.MIN_VALUE))
                conditionCode |= 4; // set C2
            else
                freg0 = Math.sin(freg0);
        } return 0;

        case FCOS: {
            if (Double.isInfinite(freg0))
                setInvalidOperation();
            if ((freg0 > Long.MAX_VALUE) || (freg0 < Long.MIN_VALUE))
                conditionCode |= 4; // set C2
            else
                freg0 = Math.cos(freg0);
        } return 0;
        case FFREE: {
                setTagEmpty(reg0);
            } return 0;
        case FBCD2F: {
            long n = 0;
            long decade = 1;
            for (int i = 0; i < 9; i++) {
                byte b = seg0.getByte(addr0 + i);
                n += (b & 0xf) * decade;
                decade *= 10;
                n += ((b >> 4) & 0xf) * decade;
                decade *= 10;
            }
            byte sign = seg0.getByte(addr0 + 9);
            double m = (double)n;
            if (sign < 0)
                m *= -1.0;
            freg0 = m;
        } return 0;

        case FF2BCD: {
            long n = (long)Math.abs(freg0);
            long decade = 1;
            for (int i = 0; i < 9; i++) {
                int val = (int) ((n % (decade * 10)) / decade);
                byte b = (byte) val;
                decade *= 10;
                val = (int) ((n % (decade * 10)) / decade);
                b |= (val << 4);
                seg0.setByte(addr0 + i, b);
            }
            seg0.setByte(addr0 + 9,  (freg0 < 0) ? (byte)0x80 : (byte)0x00);
        } return 0;

        case FSTENV_14: //TODO: add required fpu methods
            System.err.println("Warning: Using incomplete microcode: FSTENV_14");
            seg0.setWord(addr0, (short) getControl());
            seg0.setWord(addr0 + 2, (short) getStatus());
            seg0.setWord(addr0 + 4, (short) getTagWord());
            seg0.setWord(addr0 + 6, (short) 0 /* fpu.getIP()  offset*/);
            seg0.setWord(addr0 + 8, (short) 0 /* (selector & 0xFFFF)*/);
            seg0.setWord(addr0 + 10, (short) 0 /* operand pntr offset*/);
            seg0.setWord(addr0 + 12, (short) 0 /* operand pntr selector & 0xFFFF*/);
        return 0;
        case FLDENV_14: //TODO: add required fpu methods
            System.err.println("Warning: Using incomplete microcode: FLDENV_14");
            setControl(seg0.getWord(addr0));
            setStatus(seg0.getWord(addr0 + 2));
            setTagWord(seg0.getWord(addr0 + 4));
            //fpu. seg0.setWord(addr0 + 6, (short) 0 /* fpu.getIP()  offset*/);
            //fpu. seg0.setWord(addr0 + 8, (short) 0 /* (selector & 0xFFFF)*/);
            //fpu. seg0.setWord(addr0 + 10, (short) 0 /* operand pntr offset*/);
            //fpu. seg0.setWord(addr0 + 12, (short) 0 /* operand pntr selector & 0xFFFF*/);
        return 0;
        case FSTENV_28: //TODO: add required fpu methods
            System.err.println("Warning: Using incomplete microcode: FSTENV_28");
            if (seg0 == null)
                System.err.println("Error: FSTENV_28 to NULL segment. Aieee...");
            seg0.setDoubleWord(addr0, getControl() & 0xffff);
            seg0.setDoubleWord(addr0 + 4, getStatus() & 0xffff);
            seg0.setDoubleWord(addr0 + 8, getTagWord() & 0xffff);
            seg0.setDoubleWord(addr0 + 12, 0 /* fpu.getIP() */);
            seg0.setDoubleWord(addr0 + 16, 0 /* ((opcode  << 16) & 0x7FF ) + (selector & 0xFFFF)*/);
            seg0.setDoubleWord(addr0 + 20, 0 /* operand pntr offset*/);
            seg0.setDoubleWord(addr0 + 24, 0 /* operand pntr selector & 0xFFFF*/);
        return 0;
        case FLDENV_28: //TODO: add required fpu methods
            System.err.println("Warning: Using incomplete microcode: FLDENV_28");
            setControl(seg0.getDoubleWord(addr0));
            setStatus(seg0.getDoubleWord(addr0 + 4));
            setTagWord(seg0.getDoubleWord(addr0 + 8));
            //fpu.setIP(seg0.getDoubleWord(addr0 + 12)); /* fpu.getIP() */
            //fpu. seg0.getDoubleWord(addr0 + 16, 0 /* ((opcode  << 16) & 0x7FF ) + (selector & 0xFFFF)*/);
            //fpu. seg0.getDoubleWord(addr0 + 20, 0 /* operand pntr offset*/);
            //fpu. seg0.getDoubleWord(addr0 + 24, 0 /* operand pntr selector & 0xFFFF*/);
        return 0;
        case FPATAN: freg0 = Math.atan2(freg1, freg0); return 0;
        case FPREM: {
            int d = Math.getExponent(freg0) - Math.getExponent(freg1);
            if (d < 64) {
                // full remainder
                conditionCode &= ~4; // clear C2
                freg0 = freg0 % freg1;
                // compute least significant bits -> C0 C3 C1
                long i = (long)Math.rint(freg0 / freg1);
                conditionCode &= 4;
                if ((i & 1) != 0) conditionCode |= 2;
                if ((i & 2) != 0) conditionCode |= 8;
                if ((i & 4) != 0) conditionCode |= 1;
            } else {
                // partial remainder
                conditionCode |= 4; // set C2
                int n = 63; // implementation dependent in manual
                double f = Math.pow(2.0, (double)(d - n));
                double z = (freg0 / freg1) / f;
                double qq = (z < 0) ? Math.ceil(z) : Math.floor(z);
                freg0 = freg0 - (freg1 * qq * f);
            }
        } return 0;
        case FPREM1: {
            int d = Math.getExponent(freg0) - Math.getExponent(freg1);
            if (d < 64) {
                // full remainder
                conditionCode &= ~4; // clear C2
                double z = Math.IEEEremainder(freg0, freg1);
                // compute least significant bits -> C0 C3 C1
                long i = (long)Math.rint(freg0 / freg1);
                conditionCode &= 4;
                if ((i & 1) != 0) conditionCode |= 2;
                if ((i & 2) != 0) conditionCode |= 8;
                if ((i & 4) != 0) conditionCode |= 1;
                setST(0, z);
            } else {
                // partial remainder
                conditionCode |= 4; // set C2
                int n = 63; // implementation dependent in manual
                double f = Math.pow(2.0, (double)(d - n));
                double z = (freg0 / freg1) / f;
                double qq = (z < 0) ? Math.ceil(z) : Math.floor(z);
                freg0 = freg0 - (freg1 * qq * f);
            }
        } return 0;

        case FPTAN: {
            if ((freg0 > Math.pow(2.0, 63.0)) || (freg0 < -1.0*Math.pow(2.0, 63.0))) {
                if (Double.isInfinite(freg0))
                    setInvalidOperation();
                conditionCode |= 4;
            } else {
                conditionCode &= ~4;
                freg0 = Math.tan(freg0);
            }
        } return 0;
        case FSCALE: freg0 = Math.scalb(freg0, (int) freg1); return 0;
         case FSINCOS: {
             freg1 = Math.sin(freg0);
             freg0 = Math.cos(freg0);
         } return 0;
        case FXTRACT: {
            int e = Math.getExponent(freg0);
            freg1 = (double) e;
            freg0 = Math.scalb(freg0, -e);
        } return 0;
         case FYL2X: {
             if (freg0 < 0)
                 setInvalidOperation();
             else if  (Double.isInfinite(freg0)) {
                 if (freg1 == 0)
                    setInvalidOperation();
                 else if (freg1 > 0)
                     freg1 = freg0;
                 else
                     freg1 = -freg0;
             } else if ((freg0 == 1) && (Double.isInfinite(freg1)))
                 setInvalidOperation();
             else if (freg0 == 0) {
                 if (freg1 == 0)
                    setInvalidOperation();
                 else if (!Double.isInfinite(freg1))
                     setZeroDivide();
                 else
                     freg1 = -freg1;
             } else if (Double.isInfinite(freg1)) {
                 if (freg0 < 1)
                     freg1 = -freg1;
             } else
                freg1 = freg1 * Math.log(freg0)/LN2;
             freg0 = freg1;
         } return 0;
         case FYL2XP1: {
             if (freg0 == 0) {
                 if (Double.isInfinite(freg1))
                     setInvalidOperation();
                 else freg1 = 0;
             } else if (Double.isInfinite(freg1)) {
                if (freg0 < 0)
                    freg1 = -freg1;
             } else
                freg1 = freg1 * Math.log(freg0 + 1.0)/LN2;
             freg0 = freg1;
         } return 0;


        case FRNDINT: {
            if (Double.isInfinite(freg0))
                return 0; // preserve infinities

            switch(getRoundingControl()) {
            case FpuState.FPU_ROUNDING_CONTROL_EVEN:
                freg0 = Math.rint(freg0);
                break;
            case FpuState.FPU_ROUNDING_CONTROL_DOWN:
                freg0 = Math.floor(freg0);
                break;
            case FpuState.FPU_ROUNDING_CONTROL_UP:
                freg0 = Math.ceil(freg0);
                break;
            case FpuState.FPU_ROUNDING_CONTROL_TRUNCATE:
                freg0 = Math.signum(freg0) * Math.floor(Math.abs(freg0));
                break;
            default:
                System.err.println("Critical error: Invalid rounding control type.");
                throw new IllegalStateException("Invalid rounding control value");
            }
            reg0 = (int)freg0;
            reg0l = (long)freg0;
        } return 9;

        case FCHECK0: checkResult(freg0); return 0;
        case FCHECK1: checkResult(freg1); return 0;

        case FINIT: init(); return 0;

        case FSAVE_94: {
            System.err.println("Warning: Using incomplete microcode: FSAVE_94");
            seg0.setWord(addr0, (short) getControl());
            seg0.setWord(addr0 + 2, (short) getStatus());
            seg0.setWord(addr0 + 4, (short) getTagWord());
            seg0.setWord(addr0 + 6, (short) 0 /* fpu.getIP()  offset*/);
            seg0.setWord(addr0 + 8, (short) 0 /* (selector & 0xFFFF)*/);
            seg0.setWord(addr0 + 10, (short) 0 /* operand pntr offset*/);
            seg0.setWord(addr0 + 12, (short) 0 /* operand pntr selector & 0xFFFF*/);

            for (int i = 0; i < 8; i++) {
                byte[] extended = FpuState64.doubleToExtended(ST(i), false /* this is WRONG!!!!!!! */);
                for (int j = 0; j < 10; j++)
                    seg0.setByte(addr0 + 14 + j + (10 * i), extended[j]);
            }
            init();
         } return 0;
         case FRSTOR_94: {
            System.err.println("Warning: Using incomplete microcode: FRSTOR_94");
            setControl(seg0.getWord(addr0));
            setStatus(seg0.getWord(addr0 + 2));
            setTagWord(seg0.getWord(addr0 + 4));
//                  seg0.setWord(addr0 + 6, (short) 0 /* fpu.getIP()  offset*/);
//                  seg0.setWord(addr0 + 8, (short) 0 /* (selector & 0xFFFF)*/);
//                  seg0.setWord(addr0 + 10, (short) 0 /* operand pntr offset*/);
//                  seg0.setWord(addr0 + 12, (short) 0 /* operand pntr selector & 0xFFFF*/);

//                  for (int i = 0; i < 8; i++) {
//                      byte[] extended = FpuState64.doubleToExtended(fpu.ST(i), false /* this is WRONG!!!!!!! */);
//                      for (int j = 0; j < 10; j++)
//                          seg0.setByte(addr0 + 14 + j + (10 * i), extended[j]);
//                  }
         } return 0;
         case FSAVE_108: {
             System.err.println("Warning: Using incomplete microcode: FSAVE_108");
             seg0.setDoubleWord(addr0, getControl() & 0xffff);
             seg0.setDoubleWord(addr0 + 4, getStatus() & 0xffff);
             seg0.setDoubleWord(addr0 + 8, getTagWord() & 0xffff);
             seg0.setDoubleWord(addr0 + 12, 0 /* fpu.getIP() */);
             seg0.setDoubleWord(addr0 + 16, 0 /* opcode + selector*/);
             seg0.setDoubleWord(addr0 + 20, 0 /* operand pntr */);
             seg0.setDoubleWord(addr0 + 24, 0 /* more operand pntr */);

             for (int i = 0; i < 8; i++) {
                 byte[] extended = FpuState64.doubleToExtended(ST(i), false /* this is WRONG!!!!!!! */);
                 for (int j = 0; j < 10; j++)
                     seg0.setByte(addr0 + 28 + j + (10 * i), extended[j]);
             }
             init();
         } return 0;
         case FRSTOR_108: {
             System.err.println("Warning: Using incomplete microcode: FRSTOR_108");
             setControl(seg0.getDoubleWord(addr0));
             setStatus(seg0.getDoubleWord(addr0 + 4));
             setTagWord(seg0.getDoubleWord(addr0 + 8));
//                   seg0.setDoubleWord(addr0 + 12, 0 /* fpu.getIP() */);
//                   seg0.setDoubleWord(addr0 + 16, 0 /* opcode + selector*/);
//                   seg0.setDoubleWord(addr0 + 20, 0 /* operand pntr */);
//                   seg0.setDoubleWord(addr0 + 24, 0 /* more operand pntr */);

//                   for (int i = 0; i < 8; i++) {
//                       byte[] extended = FpuState64.doubleToExtended(fpu.ST(i), false /* this is WRONG!!!!!!! */);
//                       for (int j = 0; j < 10; j++)
//                           seg0.setByte(addr0 + 28 + j + (10 * i), extended[j]);
//                   }
         } return 0;
//               case FXSAVE:
//                  //check aligned to 16bit boundary
//
//                  seg0.setDoubleWord(addr0 +2, cpu.fpu.);
//                  return 0;
        default:
            return -1;
        }
    }

    public void setProtectedMode(boolean pmode)
    {
        protmode = pmode;
    }

    public int getReg0()
    {
        return reg0;
    }
    public int getReg1()
    {
        return reg1;
    }

    public int getReg2()
    {
        return reg2;
    }

    public long getReg0l()
    {
        return reg0l;
    }

    private void checkResult(double x) throws ProcessorException
    {
        // 1. check for numeric overflow or underflow.
        if (Double.isInfinite(x)) {
            // overflow
            // NOTE that this will also flag cases where the inputs
            // were also infinite.  TODO:  find out whether, for
            // instance, multipling inf by finite in x87 will also
            // set the overflow flag.
            setOverflow();
            if(!protmode)
        checkExceptions();
        }

        // for underflow, FST handles it separately (and before the store)

        // if destination is a register, then the result gets biased
        // and stored (is this the Java rule as well?)

        // and how can we trap rounding action?  is it possible that
        // something got rounded all the way to zero?

        // 2. check for inexact result exceptions.
    }

    private void validateOperand(double x) throws ProcessorException
    {
        // 1. check for SNaN.  set IE, throw if not masked.
        //    (actually, this check is already done with the operand
        //    get() method---and SNaN isn't transmitted in the
        //    Java double format.
        // 2. check for denormal operand.  set DE, throw if not masked.
        long n = Double.doubleToRawLongBits(x);
        if (((n >> 52) & 0x7ff) == 0 && ((n & 0xfffffffffffffL) != 0)) {
            setDenormalizedOperand();
            if(!protmode)
        checkExceptions();
        }
    }
}
