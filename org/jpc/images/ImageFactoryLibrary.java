package org.jpc.images;

import java.io.*;
import java.util.*;
import org.jpc.diskimages.ImageLibrary;

public class ImageFactoryLibrary implements ImageFactoryBase
{
    public ImageOffer[] getOffers()
    {
        ImageLibrary lib = ImageLibrary.getLibrary();
        List<ImageOffer> offers = new ArrayList<ImageOffer>();

        String[] names = lib.imagesByType(~0x1L);
        for(String imgName : names) {
            ImageOffer o = new ImageOffer();
            o.from = this;
            o.name = imgName;
            o.id = lib.canonicalNameFor(imgName);
            if(o.id == null)
                continue;
            switch(lib.getType(o.id)) {
            case 0:
                o.type = BaseImage.Type.FLOPPY;
                break;
            case 1:
                o.type = BaseImage.Type.HARDDRIVE;
                break;
            case 2:
                o.type = BaseImage.Type.CDROM;
                break;
            case 3:
                o.type = BaseImage.Type.BIOS;
                break;
            }
            offers.add(o);
        }
        ImageOffer[] ret = new ImageOffer[0];
        return offers.toArray(ret);
    }

    public BaseImage lookup(ImageID id) throws IOException
    {
        ImageLibrary lib = ImageLibrary.getLibrary();
        String fileName = lib.lookupFileName(id);
        if(fileName == null)
            throw new IOException("No image with ID " + id + " exists.");
        return JPCRRStandardImageDecoder.readImage(fileName);
    }
};
