package org.jpc.bus;

import java.io.*;
import java.util.*;
import org.jpc.images.*;
import org.jpc.mkfs.*;
import javax.swing.JOptionPane;
import static org.jpc.Misc.errorDialog;
import static org.jpc.Misc.castToString;
import static org.jpc.Misc.castToInt;
import static org.jpc.Misc.callShowOptionDialog;

class ImageService
{
    public ImageService(Bus _bus)
    {
        _bus.setCommandHandler(this, "doLsdisks", "lsdisks");
        _bus.setCommandHandler(this, "doImageinfo", "imageinfo");
        _bus.setCommandHandler(this, "doImageinfo2", "image-information");
        _bus.setCommandHandler(this, "doLibrary", "library");
        _bus.setCommandHandler(this, "doMakeImage", "make-image");
    }

    private static void printImageInfo(PrintStream out, String origName, boolean brief, boolean abs)
    {
        try {
            BaseImage pimg;
            if(!abs)
                pimg = BaseImageFactory.getImageByName(origName, null);
            else
                pimg = JPCRRStandardImageDecoder.readImage(origName);
            String typeString;
            switch(pimg.getType()) {
            case FLOPPY:
                typeString = "floppy    ";
                break;
            case HARDDRIVE:
                typeString = "HDD       ";
                break;
            case CDROM:
                typeString = "CD-ROM    ";
                break;
            case BIOS:
                typeString = "BIOS      ";
                break;
            default:
                typeString = "<Unknown> ";
                break;
            }
            if(brief) {
                out.println("" + pimg.getID() + " " + typeString + " " + origName);
                return;
            }

            out.println("Name               : " + origName);
            out.println("Type               : " + typeString);
            if(pimg.getType() == BaseImage.Type.FLOPPY || pimg.getType() == BaseImage.Type.HARDDRIVE) {
                out.println("Tracks             : " + pimg.getTracks());
                out.println("Sides              : " + pimg.getSides());
                out.println("Sectors            : " + pimg.getSectors());
                out.println("Total sectors      : " + pimg.getTotalSectors());
            } else if(pimg.getType() == BaseImage.Type.CDROM) {
                out.println("Total sectors      : " + pimg.getTotalSectors());
            } else if(pimg.getType() == BaseImage.Type.BIOS) {
                out.println("Image Size         : " + pimg.getTotalSectors());
            }

            out.println("Claimed Disk ID    : " + pimg.getID());
            out.println("Calculated Disk ID : " + DiskIDAlgorithm.computeIDForDisk(pimg));
            List<String> comments = pimg.getComments();
            if(comments != null) {
                out.println("");
                out.println("Comments section:");
                out.println("");
                for(String x : comments)
                    out.println(x);
            }
        } catch(IOException e) {
            errorDialog(e, "Failed to read image", null, "Quit");
        }
    }

    public void doLibrary(BusRequest req, String cmd, Object[] args) throws IllegalArgumentException, IOException
    {
        if(args == null || args.length != 1)
            throw new IllegalArgumentException("Command takes an argument");
        String library = castToString(args[0]);
        File libraryFile = new File(library);
        if(!libraryFile.isDirectory()) {
            if(!libraryFile.mkdirs()) {
                callShowOptionDialog(null, "Library (" + library + ") does not exist and can't be created",
                   "Disk library error", JOptionPane.OK_OPTION, JOptionPane.WARNING_MESSAGE, null,
                   new String[]{"Dismiss"}, "Dismiss");
                return;
            }
        }
        BaseImageFactory.addFactory(new ImageFactoryLocalDirectory(library));
        BaseImageFactory.setSavePath(library);
        req.doReturn();
    }

    public void doLsdisks(BusRequest req, String cmd, Object[] args) throws IllegalArgumentException, IOException,
        UnsupportedEncodingException
    {
        if(args != null && args.length > 1)
            throw new IllegalArgumentException("Command has an optional argument");
        String file = (args != null && args.length == 1) ? castToString(args[0]) : null;
        PrintStream output = System.out;
        boolean doClose = false;
        if(file != null) {
            OutputStream outb = new BufferedOutputStream(new FileOutputStream(file));
            output = new PrintStream(outb, false, "UTF-8");
            doClose = true;
        }

        String[] images = BaseImageFactory.getNamesByType(~0x1L);
        for(String i : images)
            printImageInfo(output, i, true, false);

        if(doClose)
           output.close();
        req.doReturn();
    }

    public void doImageinfo(BusRequest req, String cmd, Object[] args) throws IllegalArgumentException, IOException,
        UnsupportedEncodingException
    {
        if(args == null || args.length < 1 || args.length > 2)
            throw new IllegalArgumentException("Command needs 1 or 2 arguments");
        String image = castToString(args[0]);
        String file = (args.length == 2) ? castToString(args[1]) : null;
        PrintStream output = System.out;
        boolean doClose = false;
        if(file != null) {
            OutputStream outb = new BufferedOutputStream(new FileOutputStream(file));
            output = new PrintStream(outb, false, "UTF-8");
            doClose = true;
        }

        printImageInfo(output, image, false, false);

        if(doClose)
           output.close();
        req.doReturn();
    }

    public void doImageinfo2(BusRequest req, String cmd, Object[] args) throws IllegalArgumentException, IOException,
        UnsupportedEncodingException
    {
        if(args == null || args.length < 1 || args.length > 2)
            throw new IllegalArgumentException("Command needs 1 or 2 arguments");
        String image = castToString(args[0]);
        String file = (args.length == 2) ? castToString(args[1]) : null;
        PrintStream output = System.out;
        boolean doClose = false;
        if(file != null) {
            OutputStream outb = new BufferedOutputStream(new FileOutputStream(file));
            output = new PrintStream(outb, false, "UTF-8");
            doClose = true;
        }

        printImageInfo(output, image, false, true);

        if(doClose)
           output.close();
        req.doReturn();
    }

    public void doMakeImage(BusRequest req, String cmd, Object[] args) throws IllegalArgumentException, IOException
    {
        if(args == null || args.length < 3)
            throw new IllegalArgumentException("Command needs at least 3 arguments");
        String imageName = castToString(args[0]);
        String imageFile = castToString(args[1]);
        String imageType = castToString(args[2]);
        int sides = -1;
        int tracks = -1;
        int sectors = -1;
        BaseImage.Type imageType2 = null;
        String volumeLabel = null;
        String timeStamp = null;
        boolean fromDirectory = false;
        boolean diskType = false;

        if(imageType.equals("FLOPPY")) {
            imageType2 = BaseImage.Type.FLOPPY;
            diskType = true;
        } else if(imageType.equals("HARDDRIVE")) {
            imageType2 = BaseImage.Type.HARDDRIVE;
            diskType = true;
        } else if(imageType.equals("CDROM"))
            imageType2 = BaseImage.Type.CDROM;
        else if(imageType.equals("BIOS"))
            imageType2 = BaseImage.Type.BIOS;
        else if(imageType.equals("IMAGE"))
            imageType2 = null;
        else
            throw new IllegalArgumentException("Invalid type of image");

        File fSrc = new File(imageFile);
        if(fSrc.isDirectory())
            fromDirectory = true;
        else if(!fSrc.isFile())
            throw new IllegalArgumentException("'" + imageFile + "' is neither file nor directory");

        if(fromDirectory && !diskType)
            throw new IllegalArgumentException("Making image from directory only allowed with floppy or HDD images");

        for(int i = 3; i < args.length; i++) {
            String arg = castToString(args[i]);
            if(arg.startsWith("sides=")) {
                if(!diskType)
                    throw new IllegalArgumentException("sides= only allowed with floppy and HDD images");
                sides = castToInt(arg.substring(6));
                if(imageType2 == BaseImage.Type.FLOPPY && sides < 1 || sides > 2)
                    throw new IllegalArgumentException("Floppies can have 1 or 2 sides");
                if(imageType2 == BaseImage.Type.HARDDRIVE && sides < 1 || sides > 16)
                    throw new IllegalArgumentException("Hard drives can have from 1 to 16 sides");
            } else if(arg.startsWith("tracks=")) {
                if(!diskType)
                    throw new IllegalArgumentException("tracks= only allowed with floppy and HDD images");
                tracks = castToInt(arg.substring(7));
                if(imageType2 == BaseImage.Type.FLOPPY && tracks < 1 || tracks > 256)
                    throw new IllegalArgumentException("Floppies can have from 1 to 256 tracks");
                if(imageType2 == BaseImage.Type.HARDDRIVE && tracks < 2 || tracks > 1024)
                    throw new IllegalArgumentException("Hard drives can have from 2 to 1024 tracks");
            } else if(arg.startsWith("sectors=")) {
                if(!diskType)
                    throw new IllegalArgumentException("sectors= only allowed with floppy and HDD images");
                tracks = castToInt(arg.substring(8));
                if(imageType2 == BaseImage.Type.FLOPPY && sectors < 1 || sectors > 255)
                    throw new IllegalArgumentException("Floppies can have from 1 to 255 sectors");
                if(imageType2 == BaseImage.Type.HARDDRIVE && sectors < 1 || sectors > 63)
                    throw new IllegalArgumentException("Hard drives can have from 1 to 63 sectors");
            } else if(arg.startsWith("volumelabel=")) {
                if(!fromDirectory)
                    throw new IllegalArgumentException("volumelabel= only allowed when making from directory");
                volumeLabel = arg.substring(12);
                if(checkVolumeLabel(volumeLabel) != 0)
                    throw new IllegalArgumentException("Invalid volume label");
            } else if(arg.startsWith("timestamp=")) {
                if(!fromDirectory)
                    throw new IllegalArgumentException("timestamp= only allowed when making from directory");
                timeStamp = arg.substring(10);
                if(!checkTimeStamp(timeStamp))
                    throw new IllegalArgumentException("Invalid timestamp");
            }
        }
        if(sides < 0 && tracks < 0 && sectors < 0)
            sides = tracks = sectors = 0;
        else if(sides < 0 || tracks < 0 || sectors < 0)
            throw new IllegalArgumentException("If any of sides/tracks/sectors is specified, all must be");

        if(sides == 0 && !fromDirectory) {
            sides = guessSides(fSrc.length(), imageType2);
            tracks = guessTracks(fSrc.length(), imageType2);
            sectors = guessSectors(fSrc.length(), imageType2);
            if(sides < 0 || tracks < 0 || sectors < 0)
                throw new IllegalArgumentException("Can't guess geometry for image, specify manually");
            System.err.println("Informational: Guessed geometry: " + sides + " side(s), " + tracks + " track(s), " +
                sectors + " sector(s).");
        }

        req.doReturnL(_doMakeImage(imageName, fSrc, sides, tracks, sectors, volumeLabel, timeStamp, imageType2));
    }

    private int checkVolumeLabel(String text)
    {
        if(text.length() > 11)
            return 1;
        for(int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if(c == 34 || c == 124)
                return 2;
            if(c >= 33 && c <= 41)
                continue;
            if(c == 45)
                continue;
            if(c >= 48 && c <= 57)
                continue;
            if(c >= 64 && c <= 90)
                continue;
            if(c >= 94 && c <= 126)
                continue;
            if(c >= 160 && c <= 255)
                continue;
            return 2;
        }
        return 0;
    }

    private boolean checkTimeStamp(String text)
    {
        try {
            TreeDirectoryFile.dosFormatTimeStamp(text);
            return true;
        } catch(Exception e) {
            return false;
        }
    }

    private long sideSectors(long len)
    {
        if(len < 320 * 1024)
            return len / 512;
        else
            return len / 1024;
    }

    private int guessSectors(long len, BaseImage.Type type)
    {
        if(type != BaseImage.Type.FLOPPY)
            return -1;
        long sideSects = sideSectors(len);
        int baseTracks = 80;
        if(sideSects < 720)
            baseTracks = 40;
        for(int i = 0; i < 4; i++) {
            if(((int)sideSects % (baseTracks + i)) == 0)
                return (int)sideSects / (baseTracks + i);
        }
        return -1;
    }

    private int guessTracks(long len, BaseImage.Type type)
    {
        if(type != BaseImage.Type.FLOPPY)
            return -1;
        long sideSects = sideSectors(len);
        int baseTracks = 80;
        if(sideSects < 720)
            baseTracks = 40;
        for(int i = 0; i < 4; i++) {
            if(((int)sideSects % (baseTracks + i)) == 0)
                return baseTracks + i;
        }
        return -1;
    }

    private int guessSides(long len, BaseImage.Type type)
    {
        if(type != BaseImage.Type.FLOPPY)
            return -1;
        if(len < 320 * 1024)
            return 1;
        else
            return 2;
    }

    private ImageID _doMakeImage(String name, File source, int sides, int tracks, int sectors, String label,
        String timestamp, BaseImage.Type type) throws IOException
    {
        BaseImage input;
        if(type == BaseImage.Type.CDROM || type == BaseImage.Type.BIOS) {
            //Read the image.
            if(!source.isFile())
                throw new IOException("CD/BIOS images can only be made out of regular files");
            input = new FileRawDiskImage(source.getAbsolutePath(), 0, 0, 0, type);
        } else if(type == BaseImage.Type.FLOPPY || type == BaseImage.Type.HARDDRIVE) {
            if(source.isFile()) {
                input = new FileRawDiskImage(source.getAbsolutePath(), sides, tracks, sectors, type);
            } else if(source.isDirectory()) {
                TreeDirectoryFile root = TreeDirectoryFile.importTree(source.getAbsolutePath(), label, timestamp);
                input = new TreeRawDiskImage(root, label, type, sides, tracks, sectors);
            } else
                throw new IOException("Source is neither regular file nor directory");
        } else if(type == null) {
            if(!source.isFile())
                throw new IOException("Didn't I check that this JPC-RR image is a regular file?");
            input = JPCRRStandardImageDecoder.readImage(source);
        } else
            throw new IOException("BUG: Invalid image type code " + type);
        String finalName = BaseImageFactory.getPathForNewImage(name);
        ImageID id = JPCRRStandardImageEncoder.writeImage(finalName, input);
        BaseImageFactory.addNewImage(finalName);
        return id;
    }
};
