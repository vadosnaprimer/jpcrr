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

public class ArrayBackedSeekableIODevice implements SeekableIODevice
{
    private String fileName;
    private byte[] imageData;
    private int imageOffset;

    private boolean readOnly;

    public ArrayBackedSeekableIODevice()
    {
    }

    public void configure(String spec)
    {
	fileName = spec;
	imageOffset = 0;
	readOnly = false;

	InputStream in = null;
	try {
	    byte[] buffer = new byte[1024];
	    ByteArrayOutputStream bout = new ByteArrayOutputStream();
	    in = getClass().getResourceAsStream("/"+spec);
	    
	    while (true) {
		int read = in.read(buffer);
		if (read < 0)
		    break;
		bout.write(buffer, 0, read);
	    }
           
	    imageData = bout.toByteArray();
	} catch (Exception e) {
	    System.out.println("ArrayBackedSeekableIODevice: could not load file");
	    e.printStackTrace();
	} finally {
	    try {
		in.close();
	    } catch (Exception e) {}
	}
    }

    public ArrayBackedSeekableIODevice(String file)
    {
       fileName = file;
       imageOffset = 0;
       readOnly = false;

       InputStream in = null;
       try 
       {
           byte[] buffer = new byte[1024];
           ByteArrayOutputStream bout = new ByteArrayOutputStream();
           in = getClass().getResourceAsStream("/"+file);
           
           while (true) 
           {
               int read = in.read(buffer);
               if (read < 0)
                   break;
               bout.write(buffer, 0, read);
           }
           
           imageData = bout.toByteArray();
       } 
       catch (Exception e) 
       {
           System.out.println("ArrayBackedSeekableIODevice: could not load file");
           e.printStackTrace();
       }
       finally 
       {
           try 
           {
               in.close();
           } 
           catch (Exception e) {}
       }
    }

    public ArrayBackedSeekableIODevice(String name, byte[] imageData)
    {
        fileName = name;
        imageOffset = 0;
        this.imageData = imageData;
    }

    public void seek(int offset) throws IOException
    {
        if ((offset >= 0) && (offset < imageData.length))
            imageOffset = offset;
        else
            throw new IOException("seek offset out of range: "+offset+" not in [0,"+imageData.length+"]");
    }

    public int write(byte[] data, int offset, int length) throws IOException
    {
        int count = 0;
        try
        {
            for (int i = offset; i < offset + length; i++)
            {
                imageData[imageOffset] = data[i];
                imageOffset++;
                count++;
            }
        }
        catch (ArrayIndexOutOfBoundsException e)
        {
            throw new IOException("write out of range");
        }
        finally
        {
            return count;
        }

    }

    public int read(byte[] data, int offset, int length) throws IOException
    {
        int count = 0;
        try
        {
            for (int i = offset; i < offset + length; i++)
            {
                data[i] = imageData[imageOffset];
                imageOffset++;
                count++;
            }
        }
        catch (ArrayIndexOutOfBoundsException e)
        {
            throw new IOException("read out of range");
        }
        finally
        {
            return count;
        }
    }

    public int length()
    {
        return imageData.length;
    }

    public boolean readOnly()
    {
	return readOnly;
    }

    public String toString()
    {
        return fileName;
    }
}
