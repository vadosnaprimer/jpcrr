package org.jpc.images;
import org.jpc.emulator.StatusDumper;
import org.jpc.emulator.SRDumpable;
import java.io.*;
import java.util.*;

public interface BaseImage
{
    //Sector size (For disk drives, for BIOS it is 1 byte).
    public static int SECTOR_SIZE = 512;
    //Image types.
    public static enum Type {HARDDRIVE, CDROM, FLOPPY, BIOS};
    //Get type of image.
    public Type getType();
    //Get number of tracks on image.
    public int getTracks();
    //Get number of sectors on image.
    public int getSectors();
    //Get number of sides on image.
    public int getSides();
    //Get total number of sectors on image.
    public int getTotalSectors();
    //Return true if given sector might be non-zero. false if given sector is definitely all zeroes.
    public boolean nontrivialContents(int sector) throws IOException;
    //Read sectors. May return false if all read bytes are zeroes.
    public boolean read(int start, byte[] data, int sectors) throws IOException;
    //Dump status.
    public void dumpStatus(StatusDumper output);
    //Get ID.
    public ImageID getID() throws IOException;
    //Get comments.
    public List<String> getComments();
};
