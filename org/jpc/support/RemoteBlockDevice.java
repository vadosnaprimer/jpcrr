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
import java.net.*;

public class RemoteBlockDevice implements BlockDevice
{
    private DataInputStream in;
    private DataOutputStream out;
    
    public void configure(String spec) throws Exception
    {
	String server = spec.substring(4);
	int port = 6666;
	int colon = server.indexOf(":");
	if (colon >= 0) {
	    port = Integer.parseInt(server.substring(colon+1));
	    server = server.substring(0, colon);
	}
	
	Socket sock = new Socket(server, port);
        this.in = new DataInputStream(sock.getInputStream());
        this.out = new  DataOutputStream(sock.getOutputStream());

    }

    public RemoteBlockDevice(InputStream in, OutputStream out)
    {
	this.in = new DataInputStream(in);
	this.out = new DataOutputStream(out);
    }

    public void dumpChanges(DataOutput output) throws IOException
    {}

    public void loadChanges(DataInput input) throws IOException
    {}

    public synchronized void close()
    {
	try
        {
            out.write(12);
            out.flush();
        }
        catch (Exception e) {e.printStackTrace();}
    }

    public synchronized int read(long sectorNumber, byte[] buffer, int size) 
    {
        try
        {
            //          System.out.println("trying to read " + sectorNumber);
            out.write(1);
            out.writeLong(sectorNumber);
            out.writeInt(size);
            out.flush();

            if (in.read() != 0)
                throw new IOException("Read failed");
            
            int result = in.readInt();
            int toRead = in.readInt();
            in.read(buffer, 0, toRead);
            
            return result;
        }
        catch (Exception e) {e.printStackTrace();}
        return -1;
    }

    public synchronized int write(long sectorNumber, byte[] buffer, int size)
    {
        try
        {
            //          System.out.println("trying to write " + sectorNumber);
            out.write(2);
            out.writeLong(sectorNumber);
            out.writeInt(size*512);
            out.write(buffer,0,size*512);
            out.flush();

            if (in.read() != 0)
                throw new IOException("Write failed");
            
            int result = in.readInt();
  
            return result;
        }
        catch (Exception e) {e.printStackTrace();}
        return -1;
    }

    public synchronized boolean inserted()
    {
      try
        {
            out.write(8);
            out.flush();

            boolean result = in.readBoolean();
            return result;
        }
        catch (Exception e) {e.printStackTrace();}
        return false;
    }

    public synchronized boolean locked()
    {
     try
        {
            out.write(9);
            out.flush();

            boolean result = in.readBoolean();
            return result;
        }
        catch (Exception e) {e.printStackTrace();}
        return false; 
    }

    public synchronized boolean readOnly()
    {
     try
        {
            out.write(10);
            out.flush();

            boolean result = in.readBoolean();
            return result;
        }
        catch (Exception e) {e.printStackTrace();}
        return false; 
    }

    public synchronized void setLock(boolean locked)
    {
     try
        {
            out.write(11);
            out.writeBoolean(locked);
            out.flush();
        }
        catch (Exception e) {e.printStackTrace();}
      }

    public synchronized long getTotalSectors()
    {
        try
        {
            out.write(3);
            out.flush();

            long result = in.readLong();
            return result;
        }
        catch (Exception e) {e.printStackTrace();}
        return -1;
    }

    public synchronized int cylinders()
    {
        try
        {
            out.write(4);
            out.flush();

            int result = in.readInt();
            return result;
        }
        catch (Exception e) {e.printStackTrace();}
        return -1;
    }

    public synchronized int heads()
    {
        try
        {
            out.write(5);
            out.flush();

            int result = in.readInt();
            return result;
        }
        catch (Exception e) {e.printStackTrace();}
        return -1;
    }

    public synchronized int sectors()
    {
       try
        {
            out.write(6);
            out.flush();

            int result = in.readInt();
            return result;
        }
        catch (Exception e) {e.printStackTrace();}
        return -1;
    }

    public synchronized int type()
    {
        try
        {
            out.write(7);
            out.flush();

            int result = in.readInt();
            return result;
        }
        catch (Exception e) {e.printStackTrace();}
        return -1;
    }

    public synchronized String getImageFileName()
    {
        return "Remote device";
    }

//     public static void main(String[] args) throws Exception
//     {
//         PipedOutputStream out1 = new PipedOutputStream();
//         PipedInputStream in1 = new PipedInputStream(out1);

//         PipedOutputStream out2 = new PipedOutputStream();
//         PipedInputStream in2 = new PipedInputStream(out2);
                
//         RemoteBlockDevice remote = new RemoteBlockDevice(in1, out2);
        
//         RemoteBlockDeviceImpl remoteImpl = new RemoteBlockDeviceImpl(in2, out1, new TreeBlockDevice(new File(args[0])));
        
//         byte[] buffer = new byte[512];
//         for (int i=0;i<5;i++)
//         System.out.println(remote.read(63, buffer, 1));
        
//     }
}
