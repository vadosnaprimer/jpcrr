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

package org.jpc.support;

import java.io.*;

public class RemoteBlockDeviceImpl implements Runnable
{
    private DataInputStream in;
    private DataOutputStream out;
    private BlockDevice target;

    private byte[] buffer;
    
    public RemoteBlockDeviceImpl(InputStream in, OutputStream out, BlockDevice target)
    {
        this.target = target;
        this.in = new DataInputStream(in);
        this.out = new  DataOutputStream(out);
        buffer = new byte[1024];

        new Thread(this).start();
    }

    public void run()
    {
        while (true)
        {
            try
            {
                byte methodType = (byte) in.read();

                switch (methodType)
                {
                case 1:
                    long sectorNumber = in.readLong();
                    int toRead = Math.min(in.readInt(), buffer.length/512);
                    int result = target.read(sectorNumber, buffer, toRead);
                    
                    out.writeByte(0);
                    out.writeInt(result);
                    out.writeInt(toRead*512);
                    out.write(buffer, 0, toRead*512);
                    break;
                case 2:
                    long writesectorNumber = in.readLong();
                    int toWrite = Math.min(in.readInt(), buffer.length);
                    in.read(buffer, 0, toWrite);
                    int writeresult = target.write(writesectorNumber, buffer, toWrite);
                    
                    out.writeByte(0);
                    out.writeInt(writeresult);
                    break;
                case 3:
                    long totalSectors = target.getTotalSectors();
                    out.writeLong(totalSectors);
                    break;
                case 4:
                    int cylinders = target.cylinders();
                    out.writeInt(cylinders);
                    break;
                case 5:
                    int heads = target.heads();
                    out.writeInt(heads);
                    break;
                case 6:
                    int sectors = target.sectors();
                    out.writeInt(sectors);
                    break;
                case 7:
                    int type = target.type();
                    out.writeInt(type);
                    break;
                case 8:
                    boolean inserted = target.inserted();
                    out.writeBoolean(inserted);
                    break;
                case 9:
                    boolean locked = target.locked();
                    out.writeBoolean(locked);
                    break;
                case 10:
                    boolean readOnly = target.readOnly();
                    out.writeBoolean(readOnly);
                    break;
                case 11:
                    boolean setlock = in.readBoolean();
                    target.setLock(setlock);
                    break;
                case 12:
                    target.close();
                    break;
                default:
                    System.out.println("Warning - Socket closed due to protocol error");
                    return;
                }
                
                out.flush();
            }
            catch (Exception e) 
            {
                e.printStackTrace();

                System.exit(0);
            }
        }
    }
}
    
    
