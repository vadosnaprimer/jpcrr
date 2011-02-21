package org.jpc.images;
import java.io.*;
import java.util.*;
import static org.jpc.diskimages.DiskImage.getLibrary;
import org.jpc.diskimages.ImageLibrary;
import org.jpc.diskimages.DiskImage;

public class BaseImageFactory
{
    public static BaseImage getImageByID(ImageID id) throws IOException
    {
        String fileName = getLibrary().lookupFileName(id);
        if(fileName == null)
            throw new IOException("No image with ID " + id + " exists.");
        return JPCRRStandardImageDecoder.readImage(fileName);
    }

    public static BaseImage getImageByName(String name) throws IOException
    {
        String fileName = getLibrary().searchFileName(name);
        if(fileName == null)
            throw new IOException("No image with name '" + name + "' exists.");
        return JPCRRStandardImageDecoder.readImage(fileName);
    }

    public static ImageID getIDByName(String name) throws IOException
    {
        if(name == null)
            return null;
        ImageID id = getLibrary().canonicalNameFor(name);
        if(id == null)
            throw new IOException("No image with name '" + name + "' exists.");
        return id;
    }

    private static String describeMask(long mask)
    {
        List<String> x = new ArrayList<String>();
        String[] y = new String[1];
        if((mask & 1) != 0)
            x.add("empty");
        if((mask & 2) != 0)
            x.add("floppy");
        if((mask & 4) != 0)
            x.add("HDD");
        if((mask & 8) != 0)
            x.add("CD-ROM");
        if((mask & 16) != 0)
            x.add("BIOS");
        y = x.toArray(y);
        String s = "";
        for(int i = 0; i < y.length; i++) {
            String sep = "";
            if(i < y.length - 2)
                sep = ", ";
            if(i == y.length - 2)
                sep = " nor ";
            s = s + y[i] + sep;
        }
        return s;
    }

    //If mask has bit 0 set, the returned set has empty string.
    //If mask has bit 1 set, the returned set has all available floppy images.
    //If mask has bit 2 set, the returned set has all available hard drive images.
    //If mask has bit 3 set, the returned set has all available CD-ROM images.
    //If mask has bit 4 set, the returned set has all available BIOS images.
    //If Returned set would have no entries, exception is thrown.
    public static String[] getNamesByType(long mask) throws IOException
    {
        if(mask == 0)
            throw new IOException("BUG: getNamesByType(0)");
        String[] ret = getLibrary().imagesByType(mask);
        if(ret == null || ret.length == 0)
            throw new IOException("No " + describeMask(mask) + "(mask=" + mask + ") images available");
        return ret;
    }
}
