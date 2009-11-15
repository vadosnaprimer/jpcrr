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
import java.nio.charset.*;
import java.nio.*;
import java.util.*;
import static org.jpc.Misc.tempname;
import static org.jpc.Misc.errorDialog;

public class ImageLibrary
{
    private String directoryPrefix;

    public static class ByteArray
    {
        private byte[] content;

        public ByteArray(byte[] array)
        {
            content = array;
        }

        public byte[] toByteArray()
        {
            return content;
        }

        public int hashCode()
        {
            if(content == null)
                return 1;
            //Assume contents are well-distributed.
            if(content.length > 3) {
                return 256 * (256 * (256 * content[0] + content[1]) + content[2]) + content[3];
            } else if(content.length == 3) {
                return 256 * (256 * content[0] + content[1]) + content[2];
            } else if(content.length == 2) {
                return 256 * content[0] + content[1];
            } else if(content.length == 1) {
                return content[0];
            } else {
                return 0;
            }
        }

        public boolean equals(Object o) {
            if(o == null)
                return false;
            if(this.getClass() != o.getClass())
                return false;
            ByteArray o2 = (ByteArray)o;
            if(content == null && o2.content == null)
                return true;
            if(content == null && o2.content != null)
                return false;
            if(content != null && o2.content == null)
                return false;
            if(content.length != o2.content.length)
                return false;
            for(int i = 0; i < content.length; i++)
                if(content[i] != o2.content[i])
                    return false;
            return true;
        }

        public String toString()
        {
            if(content == null)
                return "(null)";
            StringBuffer buf = new StringBuffer(2 * content.length);
            for(int i = 0; i < content.length; i++) {
                int b = (int)content[i] & 0xFF;
                buf.append(Character.forDigit(b / 16, 16));
                buf.append(Character.forDigit(b % 16, 16));
            }
            return buf.toString();
        }
    }

    HashMap<ByteArray, String> idToFile;
    HashMap<String, ByteArray> fileToID;

    public ImageLibrary()
    {
        idToFile = new HashMap<ByteArray, String>();
        fileToID = new HashMap<String, ByteArray>();
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
                    RandomAccessFile r = new RandomAccessFile(fileName, "r");
                    ByteArray id = getIdentifierForImageAsArray(r, fileName);
                    insertFileName(id, fileName, imageName);
                    r.close();
                }
            } catch(IOException e) {
                System.err.println("Can't load \"" + imageFile.getName() + "\": " + e.getMessage());
            }
        }
    }

    public ImageLibrary(String libraryDirName) throws IOException
    {
        idToFile = new HashMap<ByteArray, String>();
        fileToID = new HashMap<String, ByteArray>();

        File f = new File(libraryDirName);
        if(!f.exists())
            throw new IOException("Libary directory \"" + libraryDirName + "\" does not exist.");
        if(!f.isDirectory())
            throw new IOException("Libary directory \"" + libraryDirName + "\" is not directory.");

        directoryPrefix = f.getAbsolutePath() + File.separator;
        recursiveHandleDirectory("", "", f);
    }

    public String lookupFileName(String res)
    {
        if(!fileToID.containsKey(res))
            return null;
        return directoryPrefix + res;
    }

    public String lookupFileName(byte[] resource)
    {
        ByteArray res = new ByteArray(resource);
        if(!idToFile.containsKey(res)) {
            //System.err.println("Error: Unsuccessful lookup on " + res.toString() + ".");
            //try { throw new Exception(""); } catch(Exception e) { e.printStackTrace(); }
            return null;
        }
        return idToFile.get(res);
    }

    private boolean validHexChar(char x)
    {
        switch(x) {
        case '0': case '1': case '2': case '3':
        case '4': case '5': case '6': case '7':
        case '8': case '9': case 'A': case 'B':
        case 'C': case 'D': case 'E': case 'F':
        case 'a': case 'b': case 'c': case 'd':
        case 'e': case 'f':
            return true;
        default:
            return false;
        }
    }

    public String searchFileName(String resource)
    {
        String out = null;
        boolean nameOK = true;
        if((resource.length() & 1) == 0) {
            byte[] bytes = new byte[resource.length() / 2];
            for(int i = 0; i < resource.length() / 2; i++) {
                char ch1 = resource.charAt(2 * i);
                char ch2 = resource.charAt(2 * i);
                if(!validHexChar(ch1)) nameOK = false;
                if(!validHexChar(ch2)) nameOK = false;
                bytes[i] = (byte)(Character.digit(resource.charAt(2 * i), 16) * 16 +
                    Character.digit(resource.charAt(2 * i + 1), 16));
            }
            if(nameOK)
                out = lookupFileName(bytes);
            if(out != null)
                return out;
        }

        return lookupFileName(resource);
    }

    public byte[] canonicalNameFor(String resource)
    {
        if(resource == null)
            return null;
        if(fileToID.containsKey(resource)) {
            //Its by object name.
            return fileToID.get(resource).toByteArray();
        }
        if((resource.length() & 1) != 0)
            return null;
        byte[] bytes = new byte[resource.length() / 2];
        for(int i = 0; i < resource.length() / 2; i++)
            bytes[i] = (byte)(Character.digit(resource.charAt(2 * i), 16) * 16 +
                Character.digit(resource.charAt(2 * i + 1), 16));
        ByteArray _bytes = new ByteArray(bytes);
        if(!idToFile.containsKey(_bytes))
            return null;
        return bytes;   //The name is canonical.
    }

    public void insertFileName(ByteArray resource, String fileName, String imageName)
    {
        idToFile.put(resource, fileName);
        fileToID.put(imageName, resource);
        System.err.println("Notice: " + imageName + " -> " + resource.toString() + " -> " + fileName + ".");
    }

    public static byte[] getIdentifierForImage(RandomAccessFile image, String fileName) throws IOException
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
        return id;
    }

    public static ByteArray getIdentifierForImageAsArray(RandomAccessFile image, String fileName) throws IOException
    {
        return new ByteArray(getIdentifierForImage(image, fileName));
    }

    public static byte getTypeForImage(RandomAccessFile image, String fileName) throws IOException
    {
        byte[] typehdr = new byte[1];
        image.seek(21);
        if(image.read(typehdr) < 1) {
            throw new IOException(fileName + " is not image file.");
        }
        return typehdr[0];
    }
}
