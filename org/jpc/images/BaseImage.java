package org.jpc.images;
import org.jpc.emulator.StatusDumper;
import org.jpc.emulator.SRDumpable;
import java.io.*;

public interface BaseImage
{
    //Sector size.
    public static int SECTOR_SIZE = 512;
    //Image types.
    public static enum Type {HARDDRIVE, CDROM, FLOPPY};
    //Get type of image.
    public Type getType();
    //Get number of tracks on image.
    public int getTracks();
    //Get number of sectors on image.
    public int getSectors();
    //Get number of sides on image.
    public int getSides();
    //Get total number of sectors on image.
    public long getTotalSectors();
    //Read sectors.
    public void read(long start, byte[] data, long sectors) throws IOException;
    //Dump status.
    public void dumpStatus(StatusDumper output);
    //Get ID.
    public ImageID getID();
};
