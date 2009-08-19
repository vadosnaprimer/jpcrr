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

import org.jpc.emulator.StatusDumper;
import org.jpc.emulator.SRLoader;
import org.jpc.emulator.SRDumper;
import org.jpc.emulator.AbstractHardwareComponent;
import java.io.*;

/**
 * This class holds the map between ioport addresses and <code>IOPortCapable</code>
 * objects.  Unmapped ports are redirected to an unconnected inner class instance
 * whose data lines float high, and on which writes fail silently.
 * @author Chris Dennis
 */
public class IOPortHandler extends AbstractHardwareComponent implements IOPortCapable
{
    private static final int MAX_IOPORTS = 65536;
    private static final IOPortCapable defaultDevice = new UnconnectedIOPort();
    private IOPortCapable[] ioPortDevice;

    /**
     * Constructs a new <code>IOPortHandler</code> with an initially empty ioport
     * mapping.  All ioports map to the unconnected instance.
     */
    public IOPortHandler()
    {
        ioPortDevice = new IOPortCapable[MAX_IOPORTS];
        for (int i = 0; i < ioPortDevice.length; i++)
            ioPortDevice[i] = defaultDevice;
    }

    public void dumpStatusPartial(StatusDumper output)
    {
        super.dumpStatusPartial(output);
        output.println("\tdefaultDevice <object #" + output.objectNumber(defaultDevice) + ">"); if(defaultDevice != null) defaultDevice.dumpStatus(output);
        for (int i = 0; i < ioPortDevice.length; i++) {
            output.println("\tioPortDevice[" + i + "] <object #" + output.objectNumber(ioPortDevice[i]) + ">"); if(ioPortDevice[i] != null) ioPortDevice[i].dumpStatus(output);
        }
    }

    public void dumpStatus(StatusDumper output)
    {
        if(output.dumped(this))
            return;

        output.println("#" + output.objectNumber(this) + ": IOPortHandler:");
        dumpStatusPartial(output);
        output.endObject();
    }

    public void dumpSRPartial(SRDumper output) throws IOException
    {
        super.dumpSRPartial(output);
        output.specialObject(defaultDevice);
        output.dumpInt(ioPortDevice.length);
        for(int i = 0; i < ioPortDevice.length; i++)
            output.dumpObject(ioPortDevice[i]);
    }

    public IOPortHandler(SRLoader input) throws IOException
    {
        super(input);
        input.specialObject(defaultDevice);
        ioPortDevice = new IOPortCapable[input.loadInt()];
        for(int i = 0; i < ioPortDevice.length; i++)
            ioPortDevice[i] = (IOPortCapable)input.loadObject();
    }

    public int ioPortReadByte(int address)
    {
        return ioPortDevice[address].ioPortReadByte(address);
    }

    public int ioPortReadWord(int address)
    {
        return ioPortDevice[address].ioPortReadWord(address);
    }

    public int ioPortReadLong(int address)
    {
        return ioPortDevice[address].ioPortReadLong(address);
    }

    public void ioPortWriteByte(int address, int data)
    {
        ioPortDevice[address].ioPortWriteByte(address, data);
    }

    public void ioPortWriteWord(int address, int data)
    {
        ioPortDevice[address].ioPortWriteWord(address, data);
    }

    public void ioPortWriteLong(int address, int data)
    {
        ioPortDevice[address].ioPortWriteLong(address, data);
    }

    public int[] ioPortsRequested()
    {
        return null;
    }

    /**
     * Map an <code>IOPortCapable</code> device into this handler.
     * <p>
     * The range of ioports requested by this device are registered with the
     * handler.  Each individual port is registered only if that port is
     * currently unconnected.
     * @param device object to be mapped.
     */
    public void registerIOPortCapable(IOPortCapable device)
    {
        int[] portArray = device.ioPortsRequested();
        if (portArray == null) return;
        for (int port : portArray) {
            if (ioPortDevice[port] == defaultDevice)
                ioPortDevice[port] = device;
        }
    }

    /**
     * Unmap an <code>IOPortCapable</code> device from this handler.
     * <p>
     * Ports are only unmapped from the handler if they are currently to the
     * supplied object.  References to other objects at the addresses claimed
     * are not cleared.
     * @param device object to be unmapped.
     */
    public void deregisterIOPortCapable(IOPortCapable device)
    {
        int[] portArray = device.ioPortsRequested();
        for (int port : portArray) {
            if (ioPortDevice[port] == device)
                ioPortDevice[port] = defaultDevice;
        }
    }

    public void reset()
    {
        ioPortDevice = new IOPortCapable[MAX_IOPORTS];
        for (int i = 0; i < ioPortDevice.length; i++)
            ioPortDevice[i] = defaultDevice;
    }

    public String toString()
    {
        return "IOPort Bus";
    }

    public static class UnconnectedIOPort implements IOPortCapable
    {
        public void dumpSRPartial(SRDumper output) throws IOException
        {
        }

        public UnconnectedIOPort(SRLoader input) throws IOException
        {
            input.objectCreated(this);
        }

        public UnconnectedIOPort()
        {
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

        public void ioPortWriteByte(int address, int data)
        {
        }

        public void ioPortWriteWord(int address, int data)
        {
        }

        public void ioPortWriteLong(int address, int data)
        {
        }

        public int[] ioPortsRequested()
        {
            return null;
        }

        public void dumpStatus(StatusDumper output)
        {
            if(output.dumped(this))
                return;

            output.println("#" + output.objectNumber(this) + ": UnconnectedIOPort:");
            output.endObject();
        }

    }
}
