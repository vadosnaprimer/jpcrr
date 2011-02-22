package org.jpc.images;

import java.io.*;
import java.util.*;

public class ImageFactorySaved implements ImageFactoryBase
{
    private List<ImageOffer> offers;

    public ImageFactorySaved()
    {
        offers = new ArrayList<ImageOffer>();
    }

    public ImageOffer[] getOffers()
    {
        ImageOffer[] x = new ImageOffer[0];
        return offers.toArray(x);
    }

    public BaseImage lookup(ImageID id) throws IOException
    {
        ImageOffer o = null;
        for(ImageOffer i : offers)
            if(i.id.equals(id))
                o = i;
        if(o == null)
            return null;
        return JPCRRStandardImageDecoder.readImage((String)o.privdata);
    }

    private void addName(String fullPath, String as) throws IOException
    {
        BaseImage.Type[] t = new BaseImage.Type[1];
        ImageID id = JPCRRStandardImageDecoder.readIDFromImage(fullPath, t);
        ImageOffer off = new ImageOffer();
        off.from = this;
        off.name = as;
        off.type = t[0];
        off.id = id;
        off.privdata = fullPath;
        offers.add(off);
    }

    public void addImage(String fullPath, String saveBase) throws IOException
    {
        String fullPathPrefix = saveBase + File.pathSeparator;
        String as;
        if(fullPath.startsWith(fullPathPrefix))
            as = fullPath.substring(fullPathPrefix.length());
        else {
            int index = fullPath.indexOf('/') + 1;
            if(index == 0)
                index = fullPath.indexOf('\\') + 1;
            as = fullPath.substring(index);
        }
        addName(fullPath, as);
    }
};
