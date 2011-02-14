package org.jpc.images;
import java.io.*;
import static org.jpc.diskimages.DiskImage.getLibrary;
import org.jpc.diskimages.ImageLibrary;
import org.jpc.diskimages.DiskImage;

class BaseImageFactory
{
    static BaseImage getImageByID(ImageID id) throws IOException
    {
        return new DiskImage(id);
    }

    static byte[] getBIOSByID(ImageID id) throws IOException
    {
        throw new IOException("BIOS Image with ID of " + id.toString() + " not found");
    }
}
