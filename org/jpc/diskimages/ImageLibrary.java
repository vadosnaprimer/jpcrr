/*
    JPC-RR: A x86 PC Hardware Emulator
    Release 1

    Copyright (C) 2007-2009 Isis Innovation Limited
    Copyright (C) 2009-2010 H. Ilari Liusvaara

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

import org.jpc.images.ImageID;
import java.io.*;
import java.util.*;

public class ImageLibrary
{
    private String directoryPrefix;

    public String getPathPrefix()
    {
        return directoryPrefix;
    }
    HashMap<ImageID, String> idToFile;
    HashMap<String, ImageID> fileToID;
    HashMap<ImageID, Byte> idToType;

    public ImageLibrary()
    {
        idToFile = new HashMap<ImageID, String>();
        fileToID = new HashMap<String, ImageID>();
        idToType = new HashMap<ImageID, Byte>();
    }

    private void recursiveHandleDirectory(String prefix, String pathPrefix, File directory)
    {
        File[] fileList = directory.listFiles();
        for(int i = 0; i < fileList.length; i++) {
            File imageFile = fileList[i];
            String fileName = directoryPrefix + pathPrefix + imageFile.getName();
            String imageName = prefix + imageFile.getName();
            try {
                if(imageFile.isDirectory())
                    recursiveHandleDirectory(prefix + imageFile.getName() + "/",
                        pathPrefix + imageFile.getName() + File.separator, imageFile);
                else if(imageFile.isFile()) {
                    insertFileName(null, fileName, imageName);
                }
            } catch(IOException e) {
                System.err.println("Can't load \"" + imageFile.getName() + "\": " + e.getMessage());
            }
        }
    }

    public ImageLibrary(String libraryDirName) throws IOException
    {
        idToFile = new HashMap<ImageID, String>();
        fileToID = new HashMap<String, ImageID>();
        idToType = new HashMap<ImageID, Byte>();

        long ts1 = System.currentTimeMillis();

        File f = new File(libraryDirName);
        if(!f.exists())
            throw new IOException("Libary directory \"" + libraryDirName + "\" does not exist.");
        if(!f.isDirectory())
            throw new IOException("Libary directory \"" + libraryDirName + "\" is not directory.");

        directoryPrefix = f.getAbsolutePath() + File.separator;
        recursiveHandleDirectory("", "", f);
        long ts2 = System.currentTimeMillis();

        System.err.println("Loaded " + fileToID.size() + " ROMs in " + (ts2 - ts1) + "ms.");

    }

    public String lookupFileName(ImageID resource)
    {
        if(!idToFile.containsKey(resource))
            return null;
        return idToFile.get(resource);
    }

    public ImageID canonicalNameFor(String resource)
    {
        if(resource == null)
            return null;
        if(fileToID.containsKey(resource)) {
            //Its by object name.
            return fileToID.get(resource);
        }
        try {
            ImageID id = new ImageID(resource);
            if(!idToFile.containsKey(id))
                return null;
            return id;
        } catch(IllegalArgumentException e) {
        }
        return null;
    }

    private void insertFileName(ImageID resource, String fileName, String imageName) throws IOException
    {
        RandomAccessFile r = new RandomAccessFile(fileName, "r");
        ImageID id = getIdentifierForImage(r, fileName);
        if(resource == null)
            resource = id;
        idToFile.put(resource, fileName);
        fileToID.put(imageName, resource);
        byte tByte = getTypeForImage(r, fileName);
        idToType.put(id, new Byte(tByte));
        r.close();
        System.err.println("Notice: " + imageName + " -> " + resource.toString() + " -> " + fileName + ".");
    }

    private static ImageID getIdentifierForImage(RandomAccessFile image, String fileName) throws IOException
    {
        byte[] rawhdr = new byte[21];
        image.seek(0);
        if(image.read(rawhdr) < 21 || rawhdr[0] != 73 || rawhdr[1] != 77 || rawhdr[2] != 65 || rawhdr[3] != 71 ||
                rawhdr[4] != 69) {
            throw new IOException(fileName + " is not image file.");
        }
        byte[] id = new byte[16];
        for(int i = 0; i < 16; i++)
            id[i] = rawhdr[i + 5];
        return new ImageID(id);
    }

    private static byte getTypeForImage(RandomAccessFile image, String fileName) throws IOException
    {
        byte[] typehdr = new byte[1];
        image.seek(21);
        if(image.read(typehdr) < 1) {
            throw new IOException(fileName + " is not image file.");
        }
        return typehdr[0];
    }

    public byte getType(ImageID id)
    {
        return idToType.get(id);
    }

    //type is bitmask. Bit 0 is blank, bit 1 is floppes, bit 2 is HDDs, bit3 is CDROMs, Bit 4 is BIOS
    public String[] imagesByType(long type)
    {
        String[] ret = new String[10];
        int entries = 0;

        if((type & 1) != 0) {
            if(entries == ret.length)
                ret = Arrays.copyOf(ret, 2 * ret.length);
            ret[entries++] = "";
        }

        for(Map.Entry<String, ImageID> x : fileToID.entrySet()) {
            byte iType = idToType.get(x.getValue()).byteValue();

            if((type & (2 << iType)) != 0) {
                if(entries == ret.length)
                    ret = Arrays.copyOf(ret, 2 * ret.length);
                ret[entries++] = x.getKey();
            }
        }

        if(entries == 0)
            return null;

        ret = Arrays.copyOf(ret, entries);
        Arrays.sort(ret, null);
        return ret;
    }
}
