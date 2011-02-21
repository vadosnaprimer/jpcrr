package org.jpc.images;
import java.io.*;
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
        ImageID id = getLibrary().canonicalNameFor(name);
        if(id == null)
            throw new IOException("No image with name '" + name + "' exists.");
        return id;
    }
}
