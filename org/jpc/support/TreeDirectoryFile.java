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
import java.nio.charset.*;
import java.nio.*;

public class TreeDirectoryFile extends TreeFile
{
    //We use TreeMap instead of HashMap here because TreeMap is ordered and HashMap is not.
    protected TreeMap entries;
    //Volume name. Very special.
    protected String volumeName;
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

    private static void writeEntryName(byte[] sector, String name, int offset, boolean noExtension) throws IOException
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

    public TreeDirectoryFile(String self)
    {
        super(self);
        entries = new TreeMap();
        cachedPosition = 0;
        cachedKey = null;
        volumeName = null;
    }

    public TreeDirectoryFile(String self, String volume)
    {
        this(self);
        volumeName = volume;
    }

    public void setClusterZeroOffset(int offset)
    {
         doSetClusterZeroOffset(offset);
         Map.Entry entry = entries.firstEntry();
         while(entry != null) {
             ((TreeFile)entry.getValue()).setClusterZeroOffset(offset);
             entry = entries.higherEntry(entry.getKey());
         }
    }

    public void setClusterSize(int size)
    {
         doSetClusterSize(size);
         Map.Entry entry = entries.firstEntry();
         while(entry != null) {
             ((TreeFile)entry.getValue()).setClusterSize(size);
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
                cachedKey = (String)entries.firstKey();
            } catch(Exception e) {
                cachedKey = null;
            }
        }
        while(cachedPosition < 16 * sector) {
            cachedPosition++;
            cachedKey = (String)entries.higherKey(cachedKey);
        }

        for(int i = 0; i < 16; i++) {
            //Write zeroes as entry (at least for intialization).
            for(int j = 0; j < 32; j++)
                data[32 * i + j] = 0;
            if(volumeName != null && sector == 0 && i == 0) {
                //The special volume file.
                writeEntryName(data, volumeName, 32 * i, true);
                data[32 * i + 11] = 2;        //Volume file -A -R -H -S.
                data[32 * i + 22] = 0;        //19900101T000000
                data[32 * i + 23] = 0;   
                data[32 * i + 24] = 33;
                data[32 * i + 25] = 20;
                //Cluster 0 and size 0.
            } else if(cachedKey != null) {
                TreeFile file = (TreeFile)entries.get(cachedKey);
                //Name of entry.
                writeEntryName(data, cachedKey, 32 * i, false);
                //Varous other stuff.
                if(file instanceof TreeDirectoryFile)
                    data[32 * i + 11] = 2;    //Directory file -A -R -H -S.
                else
                    data[32 * i + 11] = 0;    //Regular file -A -R -H -S.
                data[32 * i + 22] = 0;        //19900101T000000
                data[32 * i + 23] = 0;   
                data[32 * i + 24] = 33;
                data[32 * i + 25] = 20;
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
                cachedKey = (String)entries.higherKey(cachedKey);

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
         Map.Entry entry = entries.firstEntry();
         while(entry != null) {
             base = ((TreeFile)entry.getValue()).assignCluster(base);
             entry = entries.higherEntry(entry.getKey());
         }
         return base;
    }

    public TreeFile nextFile()
    {
        Map.Entry entry = entries.firstEntry();
        if(entry == null) {
            if(parent == null)
                return null;
            return parent.nextFile(selfName);
        }
        return (TreeFile)entry.getValue();
    }

    protected TreeFile nextFile(String key)
    {
        Map.Entry entry = entries.higherEntry(key);
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

    public TreeDirectoryFile pathToDirectory(String name, String walked) throws IOException
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
             addFile(new TreeDirectoryFile(walk));
         }

         Object out = entries.get(walk);
         if(out == null)
             throw new IOException("What? Didn't I create " + walked + "???");
         if(!(out instanceof TreeDirectoryFile))
             throw new IOException("Conflicting types for " + walked + ". Was regular file, now should be directory?");
         TreeDirectoryFile out2 = (TreeDirectoryFile)out;
         return out2.pathToDirectory(remaining, walked);
    }

    public static void importTree(File directory, String unixFileName, TreeDirectoryFile rootDir) 
        throws IOException
    {
        TreeDirectoryFile dir = rootDir.pathToDirectory(unixFileName, null);

        if(!directory.isDirectory())
            throw new IOException("Okay, who passed non-directory " + directory.getAbsolutePath() + " to importTree()?");
        File[] objects = directory.listFiles();
        if(objects == null)
            throw new IOException("Can't read directory " + directory.getAbsolutePath() + ".");
        for(int i = 0; i < objects.length; i++) {
            if(objects[i].isDirectory()) {
                if(unixFileName == null)
                    importTree(objects[i], objects[i].getName(), rootDir);
                else
                    importTree(objects[i], unixFileName + "/" + objects[i].getName(), rootDir);
            } else {
                 //It's a regular file.
                 dir.addFile(new TreeRegularFile(objects[i].getName(), objects[i].getAbsolutePath()));
            }
        }
    }

    public static TreeDirectoryFile importTree(String fsPath, String volumeName) throws IOException
    {
        TreeDirectoryFile root = new TreeDirectoryFile("", volumeName);
        TreeDirectoryFile.importTree(new File(fsPath), null, root);
        return root;
    }

    public static void main(String[] args) {
        TreeDirectoryFile root;
        try {
            if(args.length == 1)
                root = TreeDirectoryFile.importTree(args[0], null);
            else if(args.length > 1)
                root = TreeDirectoryFile.importTree(args[0], args[1]);
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
