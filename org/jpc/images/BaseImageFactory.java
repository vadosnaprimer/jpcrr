package org.jpc.images;
import java.io.*;
import static org.jpc.diskimages.DiskImage.getLibrary;
import org.jpc.diskimages.ImageLibrary;
import org.jpc.diskimages.DiskImage;

class BaseImageFactory
{
    static BaseImage getImageByID(ImageID id) throws IOException
    {
        String fileName = getLibrary().lookupFileName(id);
        if(fileName == null)
            throw new IOException("No image with ID " + id + " exists.");
        return JPCRRStandardImageDecoder.readImage(fileName);
    }
}
