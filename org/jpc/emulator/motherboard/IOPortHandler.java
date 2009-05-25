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

package org.jpc.emulator.motherboard;

import org.jpc.emulator.HardwareComponent;
import java.io.*;
import org.jpc.support.Magic;

/**
 * Class for storing the I/O port map, and handling the required redirection.
 */
public class IOPortHandler implements IOPortCapable, HardwareComponent
{
    private static final int MAX_IOPORTS = 65536;
    private Magic magic;

    IOPortCapable[] ioPortDevice;

    private static final IOPortCapable defaultDevice;
    static {
        defaultDevice = new UnconnectedIOPort();
    }

    public IOPortHandler()
    {
        magic = new Magic(Magic.IO_PORT_HANDLER_MAGIC_V1);
        ioPortDevice = new IOPortCapable[MAX_IOPORTS];
        for (int i = 0; i < ioPortDevice.length; i++)
            ioPortDevice[i] = defaultDevice;
    }

    public void dumpState(DataOutput output) throws IOException
    {
        magic.dumpState(output);
    }

    public void dumpStatusPartial(org.jpc.support.StatusDumper output)
    {
        output.println("\tdefaultDevice <object #" + output.objectNumber(defaultDevice) + ">"); if(defaultDevice != null) defaultDevice.dumpStatus(output);
        for (int i = 0; i < ioPortDevice.length; i++) {
            output.println("\tioPortDevice[" + i + "] <object #" + output.objectNumber(ioPortDevice[i]) + ">"); if(ioPortDevice[i] != null) ioPortDevice[i].dumpStatus(output);
        }
    }
 
    public void dumpStatus(org.jpc.support.StatusDumper output)
    {
        if(output.dumped(this))
            return;

        output.println("#" + output.objectNumber(this) + ": IOPortHandler:");
        dumpStatusPartial(output);
        output.endObject();
    }

    public void loadState(DataInput input) throws IOException
    {
        magic.loadState(input);
        reset();
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

    public void registerIOPortCapable(IOPortCapable device)
    {
        int[] portArray = device.ioPortsRequested();
        if (portArray==null) return;
        for(int i = 0; i < portArray.length; i++) {
            int port = portArray[i];
            if (ioPortDevice[port] == defaultDevice
                || ioPortDevice[port] == device) {
                ioPortDevice[port] = device;
            }
        }
    }

    public void deregisterIOPortCapable(IOPortCapable device)
    {
        int[] portArray = device.ioPortsRequested();
        for(int i = 0; i < portArray.length; i++) {
            int port = portArray[i];
            ioPortDevice[port] = defaultDevice;
        }
    }

    public String map()
    {
        String tempString = "";
        tempString += "IO Port Handler:\n";
        tempString += "Registered Ports:\n";
        for (int i = 0; i < MAX_IOPORTS; i++) {
            if (ioPortDevice[i] == defaultDevice) continue;
            tempString += "Port: 0x" + Integer.toHexString(0xffff & i) + " - ";
            tempString += ioPortDevice[i].getClass().getName() + "\n";
        }
        return tempString;
    }

    public void reset()
    {
        ioPortDevice = new IOPortCapable[MAX_IOPORTS];
        for (int i = 0; i < ioPortDevice.length; i++)
            ioPortDevice[i] = defaultDevice;
    }

    public boolean initialised()
    {
        return true;
    }

    public void acceptComponent(HardwareComponent component)
    {
    }

    public String toString()
    {
        return "IOPort Bus";
    }

    static class UnconnectedIOPort implements IOPortCapable
    {
        private Magic magic2;

        public UnconnectedIOPort()
        {
            magic2 = new Magic(Magic.UNCONNECTED_IO_PORT_MAGIC_V1);
        }

        public int ioPortReadByte(int address)
        {
            //if (address != 0x80)
            //System.out.println("RB IO[0x" + Integer.toHexString(0xffff & address) + "]");
            return 0xff;
        }
        public int ioPortReadWord(int address)
        {
            //if (address != 0x80)
            //System.out.println("RW IO[0x" + Integer.toHexString(0xffff & address) + "]");
            return 0xffff;
        }
        public int ioPortReadLong(int address)
        {
            //if (address != 0x80)
            //System.out.println("RL IO[0x" + Integer.toHexString(0xffff & address) + "]");
            return 0xffffffff;
        }

        public void ioPortWriteByte(int address, int data)
        {
            //if (address != 0x80)
            //System.out.println("WB IO[0x" + Integer.toHexString(0xffff & address) + "]");
        }

        public void ioPortWriteWord(int address, int data)
        {
            //if (address != 0x80)
            //System.out.println("WW IO[0x" + Integer.toHexString(0xffff & address) + "]");
        }
        public void ioPortWriteLong(int address, int data)
        {
            //if (address != 0x80)
            //System.out.println("WL IO[0x" + Integer.toHexString(0xffff & address) + "]");
        }

        public int[] ioPortsRequested()
        {
            return null;
        }

        public void dumpStatus(org.jpc.support.StatusDumper output)
        {
            if(output.dumped(this))
                return;

            output.println("#" + output.objectNumber(this) + ": UnconnectedIOPort:");
            output.endObject();
        }

        public void timerCallback() {}
        public void dumpState(DataOutput output) throws IOException
        {
            magic2.dumpState(output);
        }
        public void loadState(DataInput input) throws IOException
        {
            magic2.loadState(input);
        }
        public void reset() {}
        public void updateComponent(org.jpc.emulator.HardwareComponent component) {}
        public boolean updated() {return true;}
        public void acceptComponent(org.jpc.emulator.HardwareComponent component) {}
        public boolean initialised() {return true;}
    }

    public void updateComponent(org.jpc.emulator.HardwareComponent component) {}
    public boolean updated() {return true;}
    public void timerCallback() {}
}
