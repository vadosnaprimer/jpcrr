package org.jpc.images;

import java.io.*;
import java.util.*;
import org.jpc.emulator.StatusDumper;

//BaseImage with some common ops implemented.
abstract class AbstractBaseImage implements BaseImage
{
    BaseImage.Type type;
    int tracks;
    int sides;
    int sectors;
    int totalSectors;
    ImageID id;
    String name;
    List<String> comments;

    public AbstractBaseImage(BaseImage.Type _type, int _tracks, int _sides, int _sectors,
        int _totalSectors, ImageID _id, String _name, List<String> _comments)
    {
        type = _type;
        tracks = _tracks;
        sides = _sides;
        sectors = _sectors;
        totalSectors = _totalSectors;
        id = _id;
        name = _name;
        comments = _comments;
    }

    public Type getType()
    {
        return type;
    }

    public int getTracks()
    {
        return tracks;
    }

    public int getSectors()
    {
        return sectors;
    }

    public int getSides()
    {
        return sides;
    }

    public int getTotalSectors()
    {
        return totalSectors;
    }

    public ImageID getID()
    {
        return id;
    }

    public abstract boolean nontrivialContents(int sector) throws IOException;
    public abstract boolean read(int start, byte[] data, int sectors) throws IOException;

    void dumpStatusPartial(StatusDumper output)
    {
        if(type == BaseImage.Type.HARDDRIVE)
            output.println("\ttype HDD");
        if(type == BaseImage.Type.CDROM)
            output.println("\ttype CDROM");
        if(type == BaseImage.Type.FLOPPY)
            output.println("\ttype FLOPPY");
        if(type == BaseImage.Type.BIOS)
            output.println("\ttype BIOS");
        output.println("\ttracks " + tracks + " sides " + sides + " sectors " + sectors);
        output.println("\tTotalsectors " + totalSectors + " name " + name);
        output.println("\tid <object #" + output.objectNumber(id) + ">"); if(id != null) id.dumpStatus(output);
    }

    public void dumpStatus(StatusDumper output)
    {
        if(output.dumped(this))
            return;

        output.println("#" + output.objectNumber(this) + ": AbstractBaseImage:");
        dumpStatusPartial(output);
        output.endObject();
    }

    public List<String> getComments()
    {
        return comments;
    }

}
