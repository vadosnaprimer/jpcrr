package org.jpc.bus;

import java.io.*;
import java.util.*;
import org.jpc.images.*;
import org.jpc.diskimages.ImageLibrary;
import javax.swing.JOptionPane;
import static org.jpc.Misc.errorDialog;
import static org.jpc.Misc.castToString;
import static org.jpc.Misc.callShowOptionDialog;

class ImageService
{
    public ImageService(Bus _bus)
    {
        _bus.setCommandHandler(this, "doLsdisks", "lsdisks");
        _bus.setCommandHandler(this, "doImageinfo", "imageinfo");
        _bus.setCommandHandler(this, "doLibrary", "library");
    }

    private static void printImageInfo(PrintStream out, String origName, boolean brief)
    {
        try {
            BaseImage pimg = BaseImageFactory.getImageByName(origName, null);
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
        ImageLibrary.setLibrary(new ImageLibrary(library));
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
            printImageInfo(output, i, true);

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

        printImageInfo(output, image, false);

        if(doClose)
           output.close();
        req.doReturn();
    }
};
