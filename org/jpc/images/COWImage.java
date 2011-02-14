package org.jpc.images;
import java.io.*;
import org.jpc.emulator.SRLoader;
import org.jpc.emulator.SRDumper;
import org.jpc.emulator.StatusDumper;
import org.jpc.emulator.SRDumpable;
import static org.jpc.Misc.errorDialog;

public class COWImage implements SRDumpable
{
    private BaseImage base;
    private byte[][] cowData;
    private boolean readOnlyFlag;
    private boolean permanentReadOnly;
    private boolean useFlag;
    private String name;

    public COWImage(ImageID _base) throws IOException
    {
        base = BaseImageFactory.getImageByID(_base);
        //CD-ROMs can't be written to.
        if(base.getType() == BaseImage.Type.CDROM)
            readOnlyFlag = permanentReadOnly = true;
        else
            cowData = new byte[(int)base.getTotalSectors()][];
        name = "";
    }

    //Get type of image.
    public BaseImage.Type getType()
    {
        return base.getType();
    }

    //Get number of tracks on image.
    public int getTracks()
    {
        return base.getTracks();
    }

    //Get number of sectors on image.
    public int getSectors()
    {
        return base.getSectors();
    }

    //Get number of sides on image.
    public int getSides()
    {
        return base.getSides();
    }

    //Get total number of sectors on image.
    public long getTotalSectors()
    {
        return base.getTotalSectors();
    }

    public void setReadOnly(boolean roflag)
    {
        readOnlyFlag = roflag || permanentReadOnly;
    }

    public boolean isReadOnly()
    {
        return readOnlyFlag;
    }

    public void setUseFlag() throws IOException
    {
        if(useFlag)
            throw new IOException("Trying to use busy disk!");
        useFlag = true;
    }

    public void clearUseFlag()
    {
        useFlag = false;
    }

    public boolean getUseFlag()
    {
        return useFlag;
    }

    public void setName(String _name)
    {
        name = _name;
    }

    public String getName()
    {
        return name;
    }

    //Read sectors.
    public int read(long start, byte[] data, long sectors)
    {
        if(data == null || data.length < BaseImage.SECTOR_SIZE * sectors) {
            System.err.println("Error: Bad request buffer for image read.");
            return -1;
        }
        if(start + sectors > base.getTotalSectors()) {
            System.err.println("Emulated: Media read out of range: " + start + "+" + sectors + ">" +
                base.getTotalSectors() + ".");
            return -1;
        }
        try {
            base.read(start, data, sectors);
        } catch(Exception e) {
                errorDialog(e, "Failed to read from image", null, "Abort request");
                return -1;
        }
        for(long snumber = start; snumber < start + sectors; snumber++)
            if(cowData != null && cowData[(int)snumber] != null)
                System.arraycopy(cowData[(int)snumber], 0, data, (int)(BaseImage.SECTOR_SIZE * (snumber - start)),
                    BaseImage.SECTOR_SIZE);
        return 0;
    }

    //Write sectors.
    public int write(long start, byte[] data, long sectors)
    {
        if(data == null || data.length < BaseImage.SECTOR_SIZE * sectors) {
            System.err.println("Error: Bad request buffer for image read.");
            return -1;
        }
        if(start + sectors > base.getTotalSectors()) {
            System.err.println("Emulated: Media write out of range: " + start + "+" + sectors + ">" +
                base.getTotalSectors() + ".");
            return -1;
        }
        if(readOnlyFlag) {
            System.err.println("Emulated: Attempted writing into read-only media");
            return -1;
        }
        for(long snumber = start; snumber < start + sectors; snumber++) {
            if(cowData[(int)snumber] == null)
                cowData[(int)snumber] = new byte[BaseImage.SECTOR_SIZE];
            System.arraycopy(data, (int)(BaseImage.SECTOR_SIZE * (snumber - start)), cowData[(int)snumber], 0,
                BaseImage.SECTOR_SIZE);
        }
        return 0;
    }

    public ImageID getID()
    {
        return base.getID();
    }

    public void dumpSRPartial(SRDumper output) throws IOException
    {
        System.err.println("Informational: Dumping disk image...");
        output.dumpObject(base.getID());
        long cowEntries = 0;
        if(cowData != null) {
            for(long i = 0; i < cowData.length; i++) {
                if(cowData[(int)i] == null)
                    continue;
                output.dumpBoolean(true);
                output.dumpLong(i);
                output.dumpArray(cowData[(int)i]);
                cowEntries++;
            }
            output.dumpBoolean(false);
        }
        System.err.println("Informational: Disk image dumped (" + cowEntries + " cow entries).");
        output.dumpBoolean(readOnlyFlag);
        output.dumpBoolean(useFlag);
        output.dumpString(name);
    }

    public COWImage(SRLoader input) throws IOException
    {
        input.objectCreated(this);
        ImageID baseID = (ImageID)input.loadObject();
        base = BaseImageFactory.getImageByID(baseID);
        //CD-ROMs can't be written to.
        if(base.getType() == BaseImage.Type.CDROM)
            readOnlyFlag = permanentReadOnly = true;
        else
            cowData = new byte[(int)base.getTotalSectors()][];
        while(!permanentReadOnly && input.loadBoolean()) {
            long snum = input.loadLong();
            cowData[(int)snum] = input.loadArrayByte();
        }
        readOnlyFlag = input.loadBoolean();
        useFlag = input.loadBoolean();
        name = input.loadString();
    }

    public void dumpStatusPartial(StatusDumper output)
    {
        //super.dumpStatusPartial(output); <no superclass>
        output.println("\tbase <object #" + output.objectNumber(base) + ">"); if(base != null) base.dumpStatus(output);
        for(int i = 0; i < cowData.length; i++)
            if(cowData[i] != null) {
                output.println("\tcowData[" + i + "]");
                output.printArray(cowData[i], "cowData[" + i + "]");
            }
        output.println("\treadOnlyFlag " + readOnlyFlag + " useFlag " + useFlag);
        output.println("\tpermanentReadOnly " + permanentReadOnly);
        output.println("\tname " + name);
    }

    //Dump status.
    public void dumpStatus(StatusDumper output)
    {
        if(output.dumped(this))
            return;

        output.println("#" + output.objectNumber(this) + ": Image:");
        dumpStatusPartial(output);
        output.endObject();
    }
};
