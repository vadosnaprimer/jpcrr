/*
    JPC: A x86 PC Hardware Emulator for a pure Java Virtual Machine
    Release Version 2.0

    A project from the Physics Dept, The University of Oxford

    Copyright (C) 2007 Isis Innovation Limited

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

    www.physics.ox.ac.uk/jpc
*/

package org.jpc.emulator.processor;
import java.io.*;

public final class ProcessorException extends RuntimeException implements org.jpc.SRDumpable
{
    private int vector;
    private int errorCode;
    private boolean pointsToSelf;
    private boolean hasErrorCode;

    public void dumpStatusPartial(org.jpc.support.StatusDumper output)
    {
        output.println("\tvector " + vector + " errorCode " + errorCode + " pointsToSelf" + pointsToSelf);
        output.println("\thasErrorCode" + hasErrorCode);
    }

    public void dumpStatus(org.jpc.support.StatusDumper output)
    {
         if(output.dumped(this))
             return;

         output.println("#" + output.objectNumber(this) + ": ProcessorException:");
         dumpStatusPartial(output);
         output.endObject();
    }

    public void dumpSR(org.jpc.support.SRDumper output) throws IOException
    {
        if(output.dumped(this))
            return;
        dumpSRPartial(output);
        output.endObject();
    }

    public void dumpSRPartial(org.jpc.support.SRDumper output) throws IOException
    {
        output.dumpInt(vector);
        output.dumpInt(errorCode);
        output.dumpBoolean(pointsToSelf);
        output.dumpBoolean(hasErrorCode);
    }

    public static org.jpc.SRDumpable loadSR(org.jpc.support.SRLoader input, Integer id) throws IOException
    {
        org.jpc.SRDumpable x = new ProcessorException(input);
        input.endObject();
        return x;
    }

    public ProcessorException(org.jpc.support.SRLoader input) throws IOException
    {
        input.objectCreated(this);
        vector = input.loadInt();
        errorCode = input.loadInt();
        pointsToSelf = input.loadBoolean();
        hasErrorCode = input.loadBoolean();
    }

    public ProcessorException(int vector, int errorCode, boolean pointsToSelf)
    {
        this.vector = vector;
        this.hasErrorCode = true;
        this.errorCode = errorCode;
        this.pointsToSelf = pointsToSelf;
    }

    public ProcessorException(int vector, boolean pointsToSelf)
    {
        this.vector = vector;
        this.hasErrorCode = false;
        this.errorCode = 0;
        this.pointsToSelf = pointsToSelf;
    }

    public int getVector()
    {
        return vector;
    }

    public boolean hasErrorCode()
    {
        return hasErrorCode;
    }

    public int getErrorCode()
    {
            return errorCode;
    }

    public boolean pointsToSelf()
    {
        return pointsToSelf;
    }

    private static final boolean isContributory(int vector)
    {
        switch (vector) {
        case Processor.PROC_EXCEPTION_DE:
        case Processor.PROC_EXCEPTION_TS:
        case Processor.PROC_EXCEPTION_NP:
        case Processor.PROC_EXCEPTION_SS:
        case Processor.PROC_EXCEPTION_GP:
            return true;
        default:
            return false;
        }
    }

    private static final boolean isPageFault(int vector)
    {
        return (vector == Processor.PROC_EXCEPTION_PF);
    }

    public boolean combinesToDoubleFault(int vector)
    {
        //Here we are the "second exception"
        return isContributory(vector) && isContributory(this.getVector()) ||
            isPageFault(vector) && (isContributory(this.getVector()) || isPageFault(this.getVector()));
    }

    public String toString()
    {
        if (hasErrorCode())
            return "Processor Exception: " + getVector() + " [errorcode:0x" + Integer.toHexString(getErrorCode()) + "]";
        else
            return "Processor Exception: " + getVector();
    }
}
