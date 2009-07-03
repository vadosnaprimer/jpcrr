/*
    JPC: A x86 PC Hardware Emulator for a pure Java Virtual Machine
    Release Version 2.0

    A project from the Physics Dept, The University of Oxford

    Copyright (C) 2007 Isis Innovation Limited
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
 
    Details (including contact information) can be found at: 

    www.physics.ox.ac.uk/jpc
*/

package org.jpc.support;

import java.io.*;
import java.util.*;

public class TreeDirectoryFile extends TreeFile
{
    //We use TreeMap instead of HashMap here because TreeMap is ordered and HashMap is not.
    protected TreeMap<String,TreeFile> entries;
    //Volume name. Very special.
    protected String volumeName;
    //Timestamp for entries.
    private int dosTime;
    //Cached key position and key.
    protected int cachedPosition;
    protected String cachedKey;

    private static byte convertCodepoint(int cpoint) throws IOException
    {
        if(cpoint == 33 || (cpoint >= 35 && cpoint <= 41) || cpoint == 45 || cpoint == 125 || cpoint == 126)
            return (byte)cpoint;                    //Misc.
        if(cpoint >= 48 && cpoint <= 57)               //0-9
            return (byte)cpoint;
        if(cpoint >= 64 && cpoint <= 90)               //@A-Z
            return (byte)cpoint;
        if(cpoint >= 97 && cpoint <= 122)              //a-z
            return (byte)(cpoint - 32);
        if(cpoint >= 94 && cpoint <= 123)              //^_`a-z{
            return (byte)cpoint;
        if(cpoint >= 160 && cpoint <= 255)             //High range.
            return (byte)cpoint;
        throw new IOException("Character " + (char)cpoint + " not allowed in filename.");
    }

    public static void writeEntryName(byte[] sector, String name, int offset, boolean noExtension) throws IOException
    {
        for(int i = 0; i < 11; i++)
            sector[offset + i] = 32;            //Pad with spaces.
        int split = name.indexOf(".");
        if(!noExtension) {
            if(split >= 0 && (split < 1 || split > 8 || name.length() > split + 4) || (split < 0 && name.length() > 8))
                throw new IOException("Illegal file name " + name + ".");
        } else if(name.length() < 1 || name.length() > 11)
            throw new IOException("Illegal file name " + name + ".");

        if(split < 0)
            split = name.length();         //Dirty hack.

        String mainName = name.substring(0, split);
        String extName;
        if(split > 0 && split < name.length())
            extName = name.substring(split + 1);
        else
            extName = "";
        //System.err.println("noExtension=" + noExtension + ", name.length=" + name.length() + " mainName.length=" +
        //    mainName.length() + " extName.length=" + extName.length() + ".");
        for(int i = 0; i < mainName.length(); i++) {
            sector[offset + i] = convertCodepoint(mainName.charAt(i));
        }
        for(int i = 0; i < extName.length(); i++) {
            sector[offset + 8 + i] = convertCodepoint(extName.charAt(i));
        }
    }

    public TreeDirectoryFile(String self, String timestamp) throws IOException
    {
        super(self);
        entries = new TreeMap<String,TreeFile>();
        cachedPosition = 0;
        cachedKey = null;
        volumeName = null;
        if(timestamp != null)
            dosTime = dosFormatTimeStamp(timestamp);
        else
            dosTime = dosFormatTimeStamp("19900101000000");
    }

    public TreeDirectoryFile(String self, String volume, String timestamp) throws IOException
    {
        this(self, timestamp);
        volumeName = volume;
    }

    public void setClusterZeroOffset(int offset)
    {
         doSetClusterZeroOffset(offset);
         Map.Entry<String,TreeFile> entry = entries.firstEntry();
         while(entry != null) {
             entry.getValue().setClusterZeroOffset(offset);
             entry = entries.higherEntry(entry.getKey());
         }
    }

    public void setClusterSize(int size)
    {
         doSetClusterSize(size);
         Map.Entry<String,TreeFile> entry = entries.firstEntry();
         while(entry != null) {
             entry.getValue().setClusterSize(size);
             entry = entries.higherEntry(entry.getKey());
         }
    }

    public int getSize()
    {
        int extra = 0;
        if(volumeName != null)
            extra = 1;
        return 32 * entries.size() + extra;
    }

    public int dosFormatTimeStamp(String stamp) throws IOException
    {
        if(stamp.length() != 14)
            throw new IOException("Invalid timestamp " + stamp + ".");
        try {
            long nstamp = Long.parseLong(stamp, 10);
            if(nstamp < 19800101000000L || nstamp > 21071231235959L)
                throw new IOException("Invalid timestamp " + stamp + ".");
            int year = (int)(nstamp / 10000000000L) - 1980;
            int month = (int)(nstamp % 10000000000L / 100000000L);
            int day = (int)(nstamp % 100000000L / 1000000L);
            int hour = (int)(nstamp % 1000000L / 10000L);
            int minute = (int)(nstamp % 10000L / 100L);
            int second = (int)(nstamp % 100L) / 2;
            if(month < 1 || month > 12 || day < 0 || day > 31 || hour > 23 || minute > 59 || second > 59)
                throw new IOException("Invalid timestamp " + stamp + ".");
            if(day == 30 && (month == 2 || month == 4 || month == 6 || month == 9 || month == 11))
                throw new IOException("Invalid timestamp " + stamp + ".");
            if(day == 29 && month == 2 && (year % 4 != 0 || year == 120))
                throw new IOException("Invalid timestamp " + stamp + ".");
            return year * 33554432 + month * 2097152 + day * 65536 + hour * 2048 + minute * 32 + second; 
        } catch(NumberFormatException e) {
            throw new IOException("Invalid timestamp " + stamp + ".");
        }
    }

    public void readSector(int sector, byte[] data) throws IOException
    {
        int extraEntries = 0;
        if(volumeName != null)
            extraEntries++;
        if(sector >= (entries.size() + extraEntries + 15) / 16) {  //16 entries per sector.
            for(int i = 0; i < 512; i++)
                data[i] = 0;
            return;
        }

        if(cachedKey == null || cachedPosition > 16 * sector) {
            //Cache is unusable.
            cachedPosition = extraEntries;
            try {
                cachedKey = entries.firstKey();
            } catch(Exception e) {
                cachedKey = null;
            }
        }
        while(cachedPosition < 16 * sector) {
            cachedPosition++;
            cachedKey = entries.higherKey(cachedKey);
        }

        for(int i = 0; i < 16; i++) {
            //Write zeroes as entry (at least for intialization). Also write the time.
            for(int j = 0; j < 32; j++)
                data[32 * i + j] = 0;
            data[32 * i + 22] = (byte)(dosTime & 0xFF);
            data[32 * i + 23] = (byte)((dosTime >> 8) & 0xFF);
            data[32 * i + 24] = (byte)((dosTime >> 16) & 0xFF);
            data[32 * i + 25] = (byte)((dosTime >> 24) & 0xFF);

            if(volumeName != null && sector == 0 && i == 0) {
                //The special volume file.
                writeEntryName(data, volumeName, 32 * i, true);
                data[32 * i + 11] = 8;        //Volume file -A -R -H -S.
                //Cluster 0 and size 0.
            } else if(cachedKey != null) {
                TreeFile file = entries.get(cachedKey);
                //Name of entry.
                writeEntryName(data, cachedKey, 32 * i, false);
                //Varous other stuff.
                if(file instanceof TreeDirectoryFile) {
                    data[32 * i + 11] = 16;    //Directory file -A -R -H -S.
                } else {
                    data[32 * i + 11] = 0;    //Regular file -A -R -H -S.
                }
                int size = file.getSize();
                int cluster = file.getStartCluster();
                if(size == 0)  cluster = 0;     //Handle empty files.
                data[32 * i + 26] = (byte)(cluster & 0xFF);
                data[32 * i + 27] = (byte)((cluster >>> 8) & 0xFF);
                data[32 * i + 28] = (byte)(size & 0xFF);
                data[32 * i + 29] = (byte)((size >>> 8) & 0xFF);
                data[32 * i + 30] = (byte)((size >>> 16) & 0xFF);
                data[32 * i + 31] = (byte)((size >>> 24) & 0xFF);

                cachedPosition++;
                cachedKey = entries.higherKey(cachedKey);
            }
        }
    }

    public void readSectorEnd()
    {
        //No resources to free.
    }

    public int assignCluster(int base)
    {
         setStartCluster(base);
         base += getSizeInClusters();
         if(base < 2)
             base = 2;   //Special case for root directory.
         Map.Entry<String,TreeFile> entry = entries.firstEntry();
         while(entry != null) {
             base = entry.getValue().assignCluster(base);
             entry = entries.higherEntry(entry.getKey());
         }
         return base;
    }

    public TreeFile nextFile()
    {
        Map.Entry<String,TreeFile> entry = entries.firstEntry();
        if(entry == null) {
            if(parent == null)
                return null;
            return parent.nextFile(selfName);
        }
        return entry.getValue();
    }

    protected TreeFile nextFile(String key)
    {
        Map.Entry<String, TreeFile> entry = entries.higherEntry(key);
        if(entry == null) {
            if(parent == null)
                return null;
            return parent.nextFile(selfName);
        }
        return (TreeFile)entry.getValue();
    }

    public void addFile(TreeFile newFile) throws IOException
    {
         entries.put(newFile.getSelfName(), newFile);
         newFile.parentTo(this);
    }

    public TreeDirectoryFile pathToDirectory(String name, String walked, String timestamp) throws IOException
    {
         if(name == null || name == "")
             return this;

         int split = name.indexOf('/');
         String remaining;
         String walk;
         if(split == -1) {
             walk = name;
             remaining = "";
         } else {
             walk = name.substring(0, split - 1);
             remaining = name.substring(split + 1);
         }
         if(walked != null && walked != "")
             walked = walked + "/" + walk;
         else
             walked = walk;


         if(!entries.containsKey(walk)) {
             addFile(new TreeDirectoryFile(walk, timestamp));
         }

         Object out = entries.get(walk);
         if(out == null)
             throw new IOException("What? Didn't I create " + walked + "???");
         if(!(out instanceof TreeDirectoryFile))
             throw new IOException("Conflicting types for " + walked + ". Was regular file, now should be directory?");
         TreeDirectoryFile out2 = (TreeDirectoryFile)out;
         return out2.pathToDirectory(remaining, walked, timestamp);
    }

    public static void importTree(File directory, String unixFileName, TreeDirectoryFile rootDir, String timestamp) 
        throws IOException
    {
        TreeDirectoryFile dir = rootDir.pathToDirectory(unixFileName, null, timestamp);

        if(!directory.isDirectory())
            throw new IOException("Okay, who passed non-directory " + directory.getAbsolutePath() + " to importTree()?");
        File[] objects = directory.listFiles();
        if(objects == null)
            throw new IOException("Can't read directory " + directory.getAbsolutePath() + ".");
        for(int i = 0; i < objects.length; i++) {
            if(objects[i].isDirectory()) {
                if(unixFileName == null)
                    importTree(objects[i], objects[i].getName(), rootDir, timestamp);
                else
                    importTree(objects[i], unixFileName + "/" + objects[i].getName(), rootDir, timestamp);
            } else {
                 //It's a regular file.
                 dir.addFile(new TreeRegularFile(objects[i].getName(), objects[i].getAbsolutePath()));
            }
        }
    }

    public static TreeDirectoryFile importTree(String fsPath, String volumeName, String timestamp) throws IOException
    {
        TreeDirectoryFile root = new TreeDirectoryFile("", volumeName, timestamp);
        TreeDirectoryFile.importTree(new File(fsPath), null, root, timestamp);
        return root;
    }

    public static void main(String[] args) {
        TreeDirectoryFile root;
        try {
            if(args.length == 1)
                root = TreeDirectoryFile.importTree(args[0], null, null);
            else if(args.length > 1)
                root = TreeDirectoryFile.importTree(args[0], args[1], null);
            else
                throw new IOException("Usage error");
        } catch(IOException e) {
            e.printStackTrace();
            return;
        }
        
        root.assignCluster(-30);

        TreeFile iterator = root;
        while(iterator != null) {
            System.out.println("File \"" + iterator.getSelfName() + "\" assigned cluster range " + 
                iterator.getStartCluster() + "-" + iterator.getEndCluster());
            iterator = iterator.nextFile();
        }
    }
};
