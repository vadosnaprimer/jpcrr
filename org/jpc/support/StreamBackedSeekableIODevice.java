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

public abstract class StreamBackedSeekableIODevice implements SeekableIODevice
{
    private byte[] dataCache;
    private InputStream source;
    private int imageOffset, limit;

    public StreamBackedSeekableIODevice(int cacheSize, int limit)
    {
        dataCache = new byte[4*1024];
        source = null;
        
        this.limit = limit;
        imageOffset = -1;
    }

    public void closeStream() throws IOException
    {
        if (source != null)
            source.close();
    }
    
    protected abstract InputStream resetStream() throws IOException;
    
    protected void seekTo(int offset) throws IOException
    {
        if ((source == null) || (imageOffset > offset))
        {
            closeStream();
            source = resetStream();
            imageOffset = 0;
        }
        
        while (imageOffset < offset)
        {
            int toRead = Math.min(dataCache.length, offset - imageOffset);
            int read = source.read(dataCache, 0, toRead);
            if (read < 0)
                throw new IOException("Seek past end of device");
            
            imageOffset += read;
        }
    }
    
    public void seek(int offset) throws IOException
    {
        if ((offset >= 0) && (offset < limit))
            seekTo(offset);
        else
            throw new IOException("seek offset out of range: "+offset+" not in [0,"+limit+"]");
    }

    public int write(byte[] data, int offset, int length) throws IOException
    {
        System.out.println("Write at pos "+offset);
        throw new IOException("Device read only");
    }

    public int read(byte[] data, int offset, int length) throws IOException
    {
        int toRead = Math.min(data.length - offset, length);
        if (source == null)
            seekTo(0);

        int result = toRead;
        while (toRead > 0)
        {
            int r = source.read(data, offset, toRead);
            if (r < 0)
                return r;

            toRead -= r;
            offset += r;
            imageOffset += r;
        }
                
        return result;
    }

    public int length()
    {
        return limit;
    }

    public boolean readOnly()
    {
	return true;
    }
}
