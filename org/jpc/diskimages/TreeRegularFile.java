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

package org.jpc.diskimages;

import java.io.*;
import java.util.*;
import java.security.*;

public class TreeRegularFile extends TreeFile
{
    int size;
    RandomAccessFile cFile;
    String lookup;
    String md5;

    public TreeRegularFile(String self, String lookupPath) throws IOException
    {
        super(self);
        File file = new File(lookupPath);
        if(!file.isFile())
            throw new IOException("Expected " + lookupPath + " to be regular file, but it isn't.");
        size = (int)file.length();
        cFile = null;
        lookup = lookupPath;
    }

    public void setClusterZeroOffset(int offset)
    {
        doSetClusterZeroOffset(offset);
    }

    public void setClusterSize(int size)
    {
        doSetClusterSize(size);
    }

    public void readSector(int sector, byte[] data) throws IOException
    {
        if(cFile == null) {
            byte[] buffer = new byte[1024];
            int len = 1;
            int read = 0;
            cFile = new RandomAccessFile(lookup, "r");
            MessageDigest md;
            try {
                md = MessageDigest.getInstance("MD5");
            } catch(NoSuchAlgorithmException e) {
                throw new IOException("MD5 not supported by JRE?");
            }
            len = cFile.read(buffer);
            while(len > 0 && read < size) {
                read += len;
                md.update(buffer, 0, len);
                len = cFile.read(buffer);
            }

            if(read < size)
                throw new IOException("Can't read from " + lookup + ".");

            md5 = (new ImageLibrary.ByteArray(md.digest())).toString();
        }

        int expected = 512;
        if(sector == size / 512)
            expected = size % 512;
        else if(sector > size / 512)
            expected = 0;

        if(expected > 0) {
            cFile.seek(512 * sector);
            if(cFile.read(data, 0, expected) < expected) {
                throw new IOException("Can't read from " + lookup + ".");
            }
        }
        for(int i = expected; i < 512; i++)
            data[i] = 0;
    }

    public void readSectorEnd()
    {
        try {
            cFile.close();
        } catch(Exception e) {
            //Swallow it.
        }
        cFile = null;
    }

    public int assignCluster(int base)
    {
        setStartCluster(base);
        return base + getSizeInClusters();
    }

    public TreeFile nextFile()
    {
        return parent.nextFile(selfName);
    }

    protected TreeFile nextFile(String key)
    {
        return null;    //Trying to transverse file!
    }

    public void addFile(TreeFile newFile) throws Exception
    {
        throw new Exception("What you think I am? A subdirectory?");
    }

    public int getSize()
    {
        return size;
    }

    private String nformatwidth(int number, int width)
    {
         String x = (new Integer(number)).toString();
         while(x.length() < width)
             x = " " + x;
         return x;
    }

    public List<String> getComments(String prefix, String timestamp)
    {
        List<String> l = new ArrayList<String>();

        l.add("Entry: " + timestamp + " " + md5 + " " + nformatwidth(size, 10) + " " +prefix);

        return l;
    }
};
