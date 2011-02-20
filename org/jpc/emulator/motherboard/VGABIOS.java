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

package org.jpc.emulator.motherboard;

import java.io.*;
import org.jpc.emulator.*;
import org.jpc.images.ImageID;

/**
 * This class provides a <code>Bios</code> implementation for the VGA Bios.  The
 * VGA Bios is loaded at address <code>0xc0000</code>.
 * <p>
 * IO ports <code>0x500-0x503</code> are registered for debugging output.  Byte
 * writes cause ASCII characters to be written to standard output, and word
 * writes indicate a BIOS panic at the written value line number.
 * @author Chris Dennis
 */
public class VGABIOS extends Bios implements IOPortCapable
{
    private boolean ioportRegistered;

    /**
     * Loads the vga bios from the resource <code>image</code>.
     * @param image vga bios resource name.
     * @throws java.io.IOException propogated from the resource load.
     * @throws java.util.MissingResourceException propogated from the resource load
     */
    public VGABIOS(ImageID image) throws IOException
    {
        super(image);
        ioportRegistered = false;
    }

    public void dumpStatusPartial(StatusDumper output)
    {
        super.dumpStatusPartial(output);
        output.println("\tioportRegistered " + ioportRegistered);
    }

    public void dumpStatus(StatusDumper output)
    {
        if(output.dumped(this))
            return;

        output.println("#" + output.objectNumber(this) + ": VGABIOS:");
        dumpStatusPartial(output);
        output.endObject();
    }

    public void dumpSRPartial(SRDumper output) throws IOException
    {
        super.dumpSRPartial(output);
        output.dumpBoolean(ioportRegistered);
    }

    public VGABIOS(SRLoader input) throws IOException
    {
        super(input);
        ioportRegistered = input.loadBoolean();
    }

    public int[] ioPortsRequested()
    {
        return new int[]{0x500, 0x501, 0x502, 0x503};
    }

    public void ioPortWriteByte(int address, int data)
    {
        switch (address) {
            /* LGPL VGA-BIOS Messages */
            case 0x500:
            case 0x503:
                print(data);
                break;
            default:
        }
    }

    public void ioPortWriteWord(int address, int data)
    {
        switch (address) {
            /* Bochs BIOS Messages */
            case 0x501:
            case 0x502:
                System.err.println("Emulated CRITICAL: panic in vgabios at line " + data + ".");
        }
    }

    public int ioPortReadByte(int address)
    {
        return 0xff;
    }

    public int ioPortReadWord(int address)
    {
        return 0xffff;
    }

    public int ioPortReadLong(int address)
    {
        return 0xffffffff;
    }

    public void ioPortWriteLong(int address, int data)
    {
    }

    protected int loadAddress()
    {
        return 0xc0000;
    }

    public boolean initialised()
    {
        return super.initialised() && ioportRegistered;
    }

    public void acceptComponent(HardwareComponent component)
    {
        super.acceptComponent(component);

        if ((component instanceof IOPortHandler) && component.initialised()) {
            ((IOPortHandler) component).registerIOPortCapable(this);
            ioportRegistered = true;
        }
    }

    public void reset()
    {
        super.reset();
        ioportRegistered = false;
    }
}
