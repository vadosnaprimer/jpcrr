package org.jpc.bus;

class ImageService
{
    public ImageService(Bus _bus)
    {
        _bus.setCommandHandler(this, "doLsDisks", "lsdisks");
        _bus.setCommandHandler(this, "doLookupImage", "lookup-image");
        _bus.setCommandHandler(this, "listImagesByType", "image-list-by-type");
        _bus.setCommandHandler(this, "doImageinfo", "imageinfo");
    }


};