/*
    JPC-RR: A x86 PC Hardware Emulator
    Release 1

    Copyright (C) 2007-2009 Isis Innovation Limited
    Copyright (C) 2009-2010 H. Ilari Liusvaara

    This program is free software; you can redistribute it and/or modify
    it under the terms of the GNU General Public License version 2 as published by
    the Free Software Foundation.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License along
    with this program; if not, write to the Free Software Foundation, Inc.,
    51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.

    Based on JPC x86 PC Hardware emulator,
    A project from the Physics Dept, The University of Oxford

    Details about original JPC can be found at:

    www-jpc.physics.ox.ac.uk

*/

package org.jpc.j2se;

import java.io.*;
import javax.swing.*;
import java.util.List;
import java.lang.reflect.*;

import org.jpc.*;
import org.jpc.bus.*;
import org.jpc.diskimages.ImageLibrary;
import org.jpc.diskimages.ImageMaker;
import org.jpc.images.JPCRRStandardImageDecoder;
import org.jpc.images.BaseImage;
import org.jpc.diskimages.DiskImage;

import static org.jpc.Revision.getRevision;
import static org.jpc.Revision.getRelease;
import static org.jpc.Misc.errorDialog;
import static org.jpc.Misc.callShowOptionDialog;
import static org.jpc.Misc.parseString;

public class JPCApplication
{
    private static void doListImages(ImageLibrary lib, String restOfCommand) throws IOException
    {
        PrintStream out = System.out;
        boolean doClose = false;
        if(restOfCommand != null) {
            OutputStream outb = new BufferedOutputStream(new FileOutputStream(restOfCommand));
            out = new PrintStream(outb, false, "UTF-8");
            doClose = true;
        }

       //Get present images of any tyype.
       String[] images = lib.imagesByType(~0x1L);
       for(String i : images)
           printImageInfo(out, lib, i, true);

       if(doClose)
           out.close();
    }


    public static void printImageInfo(PrintStream out, ImageLibrary lib, String origName, boolean brief) throws IOException
    {
        String fileName = lib.searchFileName(origName);
        if(fileName == null) {
            System.err.println("No image named '" + origName + "' exists.");
            return;
        }
        try {
            BaseImage pimg = JPCRRStandardImageDecoder.readImage(fileName);
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
            out.println("File name          : " + fileName);
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


    private static void doImageInfo(ImageLibrary lib, String restOfCommand) throws IOException
    {
        PrintStream out = System.out;
        boolean doClose = false;
        int sIndex = restOfCommand.indexOf(" ");
        if(sIndex > 0) {
            String outName = restOfCommand.substring(0, sIndex);
            restOfCommand = restOfCommand.substring(sIndex + 1);
            OutputStream outb = new BufferedOutputStream(new FileOutputStream(outName));
            out = new PrintStream(outb, false, "UTF-8");
            doClose = true;
        }

        printImageInfo(out, lib, restOfCommand, false);

       if(doClose)
           out.close();
    }


    public static void doCommand(Bus bus, String cmd) throws IOException
    {
        if(cmd.toLowerCase().equals("")) {
        } else if(cmd.toLowerCase().startsWith("library ")) {
            String library = cmd.substring(8);
            File libraryFile = new File(library);
            if(!libraryFile.isDirectory()) {
                if(!libraryFile.mkdirs()) {
                    callShowOptionDialog(null, "Library (" + library + ") does not exist and can't be created",
                       "Disk library error", JOptionPane.OK_OPTION, JOptionPane.WARNING_MESSAGE, null,
                       new String[]{"Dismiss"}, "Dismiss");
                   return;
                }
            }
            DiskImage.setLibrary(new ImageLibrary(library));
        } else if(cmd.toLowerCase().equals("lsdisks") || cmd.toLowerCase().startsWith("lsdisks ")) {
            String rest = null;
            if(cmd.length() > 8)
                rest = cmd.substring(8);
            ImageLibrary lib = DiskImage.getLibrary();
            if(lib == null) {
                System.err.println("No library loaded");
                return;
            }
            try {
               doListImages(lib, rest);
            } catch(Exception e) {
                errorDialog(e, "Failed to lisk known images", null, "Dismiss");
            }
        } else if(cmd.toLowerCase().startsWith("diskinfo ")) {
            String rest = cmd.substring(9);
            ImageLibrary lib = DiskImage.getLibrary();
            if(lib == null) {
                System.err.println("No library loaded");
                return;
            }
            try {
               doImageInfo(lib, rest);
            } catch(Exception e) {
                errorDialog(e, "Failed to get information for image", null, "Dismiss");
            }
        } else {
            String[] ret = bus.executeStringCommand(cmd);
            if(ret == null || ret.length == 0)
                System.err.println("=> (No return value)");
            else
                for(String x : ret)
                    System.err.println("=> " + x);
        }
    }

    public static void main(String[] args) throws Exception
    {
        try
        {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Throwable e) {  //Yes, we need to catch errors too.
            System.err.println("Warning: System Look-and-Feel not loaded" + e.getMessage());
        }

        if(args != null && args.length > 0 && "-imagemaker".equals(args[0])) {
            String[] args2 = new String[args.length - 1];
            System.arraycopy(args, 1, args2, 0, args.length - 1);
            ImageMaker.main(args2);
            return;
        }

        System.out.println("JPC-RR: Rerecording PC emulator based on JPC PC emulator. Release " + getRelease());
        System.out.println("Revision: " + getRevision());
        System.out.println("Based on JPC PC emulator.");
        System.out.println("Copyright (C) 2007-2009 Isis Innovation Limited");
        System.out.println("Copyright (C) 2009-2010 H. Ilari Liusvaara");
        System.out.println("JPC-RR is released under GPL Version 2 and comes with absoutely no warranty.");

        //Probe if rename-over is supported.
        Misc.probeRenameOver(ArgProcessor.findFlag(args, "-norenames"));

        Bus bus = new Bus();
        BufferedReader kbd = new BufferedReader(new InputStreamReader(System.in, "UTF-8"));

        boolean noautoexec = ArgProcessor.findFlag(args, "-noautoexec");
        String autoexec = ArgProcessor.findVariable(args, "autoexec", null);
        if(autoexec != null && !noautoexec) {
            try {
                BufferedReader kbd2 = new BufferedReader(new InputStreamReader(
                    new FileInputStream(autoexec), "UTF-8"));
                while(true) {
                    String cmd = kbd2.readLine();
                    if(cmd == null)
                        break;
                    System.err.println("Autoexec command: " + cmd);
                    doCommand(bus, cmd);
                }
            } catch (Exception e) {
                System.err.println("Failed to load autoexec script: " + e.getMessage());
            }
        }

        while(true) {
            System.out.print("JPC-RR> ");
            System.out.flush();
            String cmd = kbd.readLine();
            try {
                if(cmd != null)
                    doCommand(bus, cmd);
                else
                    synchronized(kbd) {
                        kbd.wait();
                    }
            } catch (Exception e) {
                errorDialog(e, "Command execution failed", null, "Dismiss");
            }
        }
    }
}
