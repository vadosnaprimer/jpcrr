package org.jpc.images;

import java.io.*;

public interface ImageFactoryBase
{
    public ImageOffer[] getOffers();
    public BaseImage lookup(ImageID id) throws IOException;
};
