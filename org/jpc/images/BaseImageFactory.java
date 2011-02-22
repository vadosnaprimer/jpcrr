package org.jpc.images;
import java.io.*;
import java.util.*;

public class BaseImageFactory
{
    private static List<ImageFactoryBase> factories;
    private static String savePathBase;
    private static ImageFactorySaved saver;

    static
    {
        factories = new ArrayList<ImageFactoryBase>();
        savePathBase = ".";
        saver = new ImageFactorySaved();
        factories.add(saver);
        //factories.add(new ImageFactoryLibrary());
    }

    public static void addFactory(ImageFactoryBase factory)
    {
        factories.add(factory);
    }

    public static String getPathForNewImage(String imageName)
    {
        String mangledImageName = imageName.replaceAll("\\\\", "/").replaceAll("/", File.separator);
        return savePathBase + File.separator + mangledImageName;
    }

    public static void addNewImage(String fileNameUsed) throws IOException
    {
        saver.addImage(fileNameUsed, savePathBase);
    }

    public static void setSavePath(String path)
    {
        savePathBase = path;
    }

    public static BaseImage getImageByID(ImageID id) throws IOException
    {
        ImageOffer matching = null;
        for(ImageFactoryBase f : factories)
            for(ImageOffer o : f.getOffers())
                if(o.id.equals(id))
                    matching = o;
        if(matching == null)
            throw new IOException("No image with ID " + id + " exists.");
        return matching.from.lookup(matching.id);
    }

    public static BaseImage getImageByName(String name, BaseImage.Type expected) throws IOException
    {
        ImageOffer matching = null;
        for(ImageFactoryBase f : factories)
            for(ImageOffer o : f.getOffers())
                if(o.name.equals(name) && (expected == null || o.type == expected))
                    matching = o;
        if(matching == null)
            throw new IOException("No image with name '" + name + "' exists.");
        return matching.from.lookup(matching.id);
    }

    public static ImageID getIDByName(String name, BaseImage.Type expected) throws IOException
    {
        ImageOffer matching = null;
        for(ImageFactoryBase f : factories)
            for(ImageOffer o : f.getOffers())
                if(o.name.equals(name) && (expected == null || o.type == expected))
                    matching = o;
        if(matching == null)
            return null;
        return matching.id;
    }

    //If mask has bit 0 set, the returned set has empty string.
    //If mask has bit 1 set, the returned set has all available floppy images.
    //If mask has bit 2 set, the returned set has all available hard drive images.
    //If mask has bit 3 set, the returned set has all available CD-ROM images.
    //If mask has bit 4 set, the returned set has all available BIOS images.
    //If Returned set would have no entries, exception is thrown.
    public static String[] getNamesByType(long mask) throws IOException
    {
        Set<String> nameSet = new TreeSet<String>();
        if(mask == 0)
            throw new IOException("BUG: getNamesByType(0)");

        if((mask & 1) != 0)
            nameSet.add("");
        for(ImageFactoryBase f : factories) {
            ImageOffer[] offs = f.getOffers();
            for(ImageOffer o : offs)
                if((o.type == BaseImage.Type.FLOPPY && (mask & 2) != 0) ||
                    (o.type == BaseImage.Type.HARDDRIVE && (mask & 4) != 0) ||
                    (o.type == BaseImage.Type.CDROM && (mask & 8) != 0) ||
                    (o.type == BaseImage.Type.BIOS && (mask & 16) != 0))
                    nameSet.add(o.name);
        }

        String[] ret = new String[nameSet.size()];
        if(ret == null || ret.length == 0)
            throw new IOException("No " + describeMask(mask) + "(mask=" + mask + ") images available");
        int i = 0;
        for(String x : nameSet)
            ret[i++] = x;
        return ret;
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
}
