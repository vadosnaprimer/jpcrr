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
import java.nio.charset.*;
import java.nio.*;
import java.util.*;

public class ImageLibrary
{
    protected static final char[] hex;

    static 
    {
        hex = new char[16];
        for(int i = 0; i < 10; i++)
            hex[i] = (char)(48 + i);
        for(int i = 0; i < 6; i++)
            hex[i + 10] = (char)(65 + i);
    }

    static class ByteHolder
    {
        public byte value;
    }

    public static class ByteArray
    {
        private byte[] content;

        public ByteArray(byte[] array)
        {
            content = array;
        }

        public int hashCode()
        {
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
            if(content.length != o2.content.length)
                return false;
            for(int i = 0; i < content.length; i++)
                if(content[i] != o2.content[i])
                    return false;
            return true;
        }

        public String toString()
        {
            StringBuffer buf = new StringBuffer(2 * content.length);
            for(int i = 0; i < content.length; i++) {
                int b = (int)content[i] & 0xFF;
                buf.append(hex[b / 16]);
                buf.append(hex[b % 16]);
            }
            return buf.toString();
        }
    }

    HashMap libraryMap;
    HashMap nameMap;

    private int parseHex(char ch)
    {
        if(ch >= '0' && ch <= '9')
            return ch - '0';
        if(ch >= 'A' && ch <= 'F')
            return ch - 'A' + 10;
        if(ch >= 'a' && ch <= 'f')
            return ch - 'a' + 10;
        return -1;
    }

    public ImageLibrary()
    {
        libraryMap = new HashMap();
        nameMap = new HashMap();
    }

    private static String decodeDiskName(String encoded)
    {
        int elen = encoded.length();
        StringBuffer sb = new StringBuffer(elen);
        for(int i = 0; i < elen; i++) {
            char ch = encoded.charAt(i);
            if(ch == '%') {
                //Special!
                ch = encoded.charAt(++i);
                if(ch == 'a')
                    sb.append(":");
                else if(ch == 'b')
                    sb.append("%");
            } else {
                sb.append(ch);
            }
        }
        return sb.toString();
    }

    private static String encodeDiskName(String decoded)
    {
        int dlen = decoded.length();
        StringBuffer sb = new StringBuffer(2 * dlen);
        for(int i = 0; i < dlen; i++) {
            char ch = decoded.charAt(i);
            if(ch == '%') {
                //Special!
                sb.append("%b");
            } else if(ch == ':') {
                sb.append("%a");
            } else {
                sb.append(ch);
            }
        }
        return sb.toString();
    }

    public ImageLibrary(String libraryFilName) throws IOException
    {
        libraryMap = new HashMap();
        nameMap = new HashMap();
        InputStream is = new FileInputStream(libraryFilName);
        Reader r = new InputStreamReader(is, Charset.forName("UTF-8"));
        BufferedReader br = new BufferedReader(r);
        String line;

        while((line = br.readLine()) != null) {
            if(line.charAt(0) == '#' || line.length() == 0)
                continue;   //Comment.
            String[] components = line.split(":", 3);
            if(components.length < 3 || !components[0].matches("^([0-9A-Fa-f][0-9A-Fa-f])+$")) {
                System.err.println("Bad library line: \"" + line + "\". Ignored.");
                continue;
            }
            int l = components[0].length() / 2;
            byte[] parsed = new byte[l];
            for(int i = 0; i < l; i++)
                parsed[i] = (byte)(parseHex(components[0].charAt(2 * i)) * 16 + parseHex(components[0].charAt(2 * i + 1)));
             libraryMap.put(new ByteArray(parsed), components[2]);
             String name = decodeDiskName(components[1]);
             libraryMap.put(name, components[2]);
             nameMap.put(new ByteArray(parsed), name);
        }
    }

    public String lookupFileName(String res)
    {
        if(!libraryMap.containsKey(res))
            return null;
        return (String)(libraryMap.get(res));
    }

    public String lookupFileName(byte[] resource)
    {
        ByteArray res = new ByteArray(resource);
        if(!libraryMap.containsKey(res))
            return null;
        return (String)(libraryMap.get(res));
    }

    public void insertFileName(byte[] resource, String FileName, String diskName)
    {
        ByteArray arr = new ByteArray(resource); 
        libraryMap.put(arr, FileName);
        libraryMap.put(diskName, FileName);
        nameMap.put(arr, diskName);
    }

    private static String tempname(String prefix)
    {
        //As we don't create files atomically, we need to be unpredictable.
        java.security.SecureRandom prng = new java.security.SecureRandom();
        byte[] rnd = new byte[12];
        prng.nextBytes(rnd);
        StringBuffer buf = new StringBuffer(2 * rnd.length + 1);
        buf.append('.');
        for(int i = 0; i < rnd.length; i++) {
            int b = (int)rnd[i] & 0xFF;
            buf.append(hex[b / 16]);
            buf.append(hex[b % 16]);
        }
        return prefix + buf.toString();
    }

    public void writeLibrary(String libraryFileName) throws IOException
    {
        //Generate the filename for tempfile and open.
        File fileObj = new File(tempname(libraryFileName));
        while(fileObj.exists())
             fileObj = new File(tempname(libraryFileName));
        fileObj.deleteOnExit();    //This should fail to actually delete file since file gets renamed away.
        PrintStream out = new PrintStream(fileObj.getPath(), "UTF-8");

        //Dump all library entries.
        Set entries = libraryMap.entrySet();
        Iterator itt = entries.iterator();
        while (itt.hasNext())
        {
            try {
                Map.Entry entry = (Map.Entry)itt.next();
                ByteArray key = (ByteArray)entry.getKey();
                String value = (String)entry.getValue();
                String description = (String)nameMap.get(key);
                out.println(key.toString() + ":" + encodeDiskName(description) + ":" + value);
            } catch(ClassCastException e) {
                //These are the string lookup entries. Ignore them.
            }
        }
        out.close();

        //Use atomic move-over.
        fileObj.renameTo(new File(libraryFileName));
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

    public static byte getTypeForImage(RandomAccessFile image, String fileName) throws IOException
    {
        byte[] typehdr = new byte[1];
        image.seek(21);
        if(image.read(typehdr) < 1) {
            throw new IOException(fileName + " is not image file.");
        }
        return typehdr[0];
    }

    public static String getNameForImage(RandomAccessFile image, String fileName) throws IOException
    {
        byte[] namehdr = new byte[2];
        image.seek(22);
        if(image.read(namehdr) < 2) {
            throw new IOException(fileName + " is not image file.");
        }
        int length = ((int)namehdr[0] & 0xFF) * 256 + ((int)namehdr[1] & 0xFF);
        if(length == 0)
            return "";
        namehdr = new byte[length];
        if(image.read(namehdr) < length) {
            throw new IOException(fileName + " is not image file.");
        }
        return Charset.forName("UTF-8").newDecoder().decode(ByteBuffer.wrap(namehdr)).toString();
    }

    public static void main(String[] args)
    {
        if(args.length < 2) {
            System.err.println("Syntax: java org.jpc.support.ImageLibrary <libraryname> <image>...");
            return;
        }
        ImageLibrary lib;    
        File libFile = new File(args[0]);
        if(libFile.exists()) {
            try {
                lib = new ImageLibrary(args[0]);
            } catch(IOException e) {
                e.printStackTrace();
                return;
            }
        } else {
            lib = new ImageLibrary();
        }

        for(int i = 1; i < args.length; i++) {
            byte[] identifier;
            String name;
            byte typeCode;
            try {
                RandomAccessFile imageFile = new RandomAccessFile(args[i], "r");
                identifier = ImageLibrary.getIdentifierForImage(imageFile, args[i]);
                typeCode = ImageLibrary.getTypeForImage(imageFile, args[i]);
                name = ImageLibrary.getNameForImage(imageFile, args[i]);
            } catch(IOException e) {
                e.printStackTrace();
                return;
            }
            String typeString;
            switch(typeCode) {
            case 0:
                typeString = "floppy";
                break;
            case 1:
                typeString = "HDD";
                break;
            case 2:
                typeString = "CD-ROM";
                break;
            case 3:
                typeString = "BIOS";
                break;
            default:
                typeString = "<Unknown>";
                break;
            }
            System.out.println("Adding " + args[i] + " (" + typeString + " Image ID " + 
                (new ByteArray(identifier)).toString() + ")");
            File file = new File(args[i]);
            String fileName = file.getAbsolutePath();
            lib.insertFileName(identifier, fileName, name);
        }

        try {
           lib.writeLibrary(args[0]);
        } catch(IOException e) {
            e.printStackTrace();
            return;
        }
    } 
}
