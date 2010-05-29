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
import org.jpc.diskimages.ImageLibrary;
import org.jpc.diskimages.ImageMaker;
import org.jpc.diskimages.DiskImage;
import org.jpc.pluginsbase.*;

import static org.jpc.Revision.getRevision;
import static org.jpc.Misc.errorDialog;
import static org.jpc.Misc.callShowOptionDialog;
import static org.jpc.Misc.parseString;

public class JPCApplication
{
    public static Plugin instantiatePlugin(Plugins pluginManager, Class<?> plugin, String arguments) throws IOException
    {
        Constructor<?> cc;

        if(arguments != null) {
            try {
                cc = plugin.getConstructor(Plugins.class, String.class);
            } catch(Exception e) {
                throw new IOException("Plugin \"" + plugin.getName() + "\" does not take arguments.");
            }
        } else {
            try {
                cc = plugin.getConstructor(Plugins.class);
            } catch(Exception e) {
                throw new IOException("Plugin \"" + plugin.getName() + "\" requires arguments.");
            }
        }

        try {
            if(arguments != null)
                return (Plugin)cc.newInstance(pluginManager, arguments);
            else
                return (Plugin)cc.newInstance(pluginManager);
        } catch(InvocationTargetException e) {
            Throwable e2 = e.getCause();
            //If the exception is something unchecked, just pass it through.
            if(e2 instanceof RuntimeException)
                throw (RuntimeException)e2;
            if(e2 instanceof Error) {
                IOException ne =  new IOException("Error while invoking loader: " + e2);
                ne.setStackTrace(e2.getStackTrace());  //Copy stack trace.
                throw ne;
            }
            //Also pass IOException through.
            if(e2 instanceof IOException)
                throw (IOException)e2;
            //What the heck is that?
            IOException ne = new IOException("Unknown exception while invoking loader: " + e2);
            ne.setStackTrace(e2.getStackTrace());  //Copy stack trace.
            throw ne;
        } catch(Exception e) {
            throw new IOException("Failed to invoke plugin \"" + plugin.getName() + "\" constructor.");
        }
    }

    private static void loadPlugin(Plugins pluginManager, String arg) throws IOException
    {
        String moduleString = arg;
        String currentModule;
        int parenDepth = 0;
        int nameEnd = -1;
        int paramsStart = -1;
        int paramsEnd = -1;
        int stringLen = moduleString.length();
        boolean requireNextSep = false;

        for(int i = 0; true; i++) {
            int cp;
            if(i < stringLen)
                cp = moduleString.codePointAt(i);
            else if(parenDepth == 0) {
                if(nameEnd < 0)
                    nameEnd = i - 1;
                currentModule = moduleString.substring(0, i);
                if(i < stringLen ) {
                    moduleString = moduleString.substring(i + 1);
                    if(moduleString.equals(""))
                        throw new IOException("Error in module string: Blank module name not allowed.");
                } else
                    moduleString = "";
                break;
            } else
                throw new IOException("Error in module string: unclosed '('.");
            if(cp >= 0x10000)
                 i++; //Skip the next surrogate.
            if((cp >= 0xD800 && cp < 0xE000) || ((cp & 0xFFFE) == 0xFFFE) || (cp >>> 16) > 16 || cp < 0)
                throw new IOException("Error In module string: invalid Unicode character.");
            if(requireNextSep && cp != ',')
                throw new IOException("Error in module string: Expected ',' after ')' closing parameter list.");
            else if(cp == ',' && i == 0)
                throw new IOException("Error in module string: Blank module name not allowed.");
            else if(cp == '(') {
                if(parenDepth == 0) {
                    paramsStart = i + 1;
                    nameEnd = i - 1;
                }
                parenDepth++;
            } else if(cp == ')') {
                if(parenDepth == 0)
                    throw new IOException("Error in module string: Unpaired ')'.");
                else if(parenDepth == 1) {
                    paramsEnd = i - 1;
                    requireNextSep = true;
                }
                parenDepth--;
            }
        }

        String name = currentModule.substring(0, nameEnd + 1);
        String params = null;
        if(paramsStart >= 0)
            params = currentModule.substring(paramsStart, paramsEnd + 1);

        Class<?> plugin;

        try {
            plugin = Class.forName(name);
        } catch(Exception e) {
            throw new IOException("Unable to find plugin \"" + name + "\".");
        }

        if(!Plugin.class.isAssignableFrom(plugin)) {
            throw new IOException("Plugin \"" + name + "\" is not valid plugin.");
        }
        Plugin c = instantiatePlugin(pluginManager, plugin, params);
        pluginManager.registerPlugin(c);
    }

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
            ImageMaker.ParsedImage pimg = new ImageMaker.ParsedImage(fileName);
            String typeString;
            switch(pimg.typeCode) {
            case 0:
                typeString = "floppy    ";
                break;
            case 1:
                typeString = "HDD       ";
                break;
            case 2:
                typeString = "CD-ROM    ";
                break;
            case 3:
                typeString = "BIOS      ";
                break;
            default:
                typeString = "<Unknown> ";
                break;
            }
            if(brief) {
                out.println("" + (new ImageLibrary.ByteArray(pimg.diskID)) + " " + typeString + " " + origName);
                return;
            }

            out.println("Name               : " + origName);
            out.println("File name          : " + fileName);
            out.println("Type               : " + typeString);
            if(pimg.typeCode == 0 || pimg.typeCode == 1) {
                out.println("Tracks             : " + pimg.tracks);
                out.println("Sides              : " + pimg.sides);
                out.println("Sectors            : " + pimg.sectors);
                out.println("Total sectors      : " + pimg.totalSectors);
                out.println("Primary extent size: " + pimg.sectorsPresent);
                out.println("Storage Method     : " + pimg.method);
                int actualSectors = 0;

                for(int i = 0; i < pimg.totalSectors; i++) {
                    if(i < pimg.sectorOffsetMap.length && pimg.sectorOffsetMap[i] > 0)
                        actualSectors++;
                }
                out.println("Sectors present    : " + actualSectors);
            } else if(pimg.typeCode == 2) {
                out.println("Total sectors      : " + pimg.totalSectors);
            } else if(pimg.typeCode == 3) {
                out.println("Image Size         : " + pimg.rawImage.length);
            }

            out.println("Claimed Disk ID    : " + (new ImageLibrary.ByteArray(pimg.diskID)));
            List<String> comments = pimg.comments;
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


    public static void doCommand(Plugins pluginManager, String cmd) throws IOException
    {
        if(cmd.toLowerCase().startsWith("load ")) {
            try {
                loadPlugin(pluginManager, cmd.substring(5));
            } catch(Exception e) {
                errorDialog(e, "Plugin Loading failed", null, "Dismiss");
            }
        } else if(cmd.toLowerCase().equals("exit")) {
            pluginManager.shutdownEmulator();
        } else if(cmd.toLowerCase().equals("")) {
        } else if(cmd.toLowerCase().startsWith("command ")) {
            try {
                String[] arr = parseString(cmd.substring(8));
                if(arr == null)
                    throw new Exception("No command to send given");
                String rcmd = arr[0];
                String[] rargs = null;
                if(arr.length > 1) {
                    rargs = new String[arr.length - 1];
                    System.arraycopy(arr, 1, rargs, 0, arr.length - 1);
                }
                pluginManager.invokeExternalCommandSynchronous(rcmd, rargs);
            } catch(Exception e) {
                errorDialog(e, "Command sending failed", null, "Dismiss");
            }
        } else if(cmd.toLowerCase().startsWith("call ")) {
            try {
                String[] arr = parseString(cmd.substring(5));
                if(arr == null)
                    throw new Exception("No command to send given");
                String rcmd = arr[0];
                String[] rargs = null;
                if(arr.length > 1) {
                    rargs = new String[arr.length - 1];
                    System.arraycopy(arr, 1, rargs, 0, arr.length - 1);
                }
                Object[] ret = pluginManager.invokeExternalCommandReturn(rcmd, rargs);
                if(ret != null)
                    for(int i = 0; i < ret.length; i++)
                        System.out.println(ret[i].toString());
                else
                    System.out.println("Nothing returned.");
            } catch(Exception e) {
                errorDialog(e, "Command sending failed", null, "Dismiss");
            }
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
            System.err.println("Invalid command");
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

        System.out.println("JPC-RR: Rerecording PC emulator based on JPC PC emulator. Release 10.9");
        System.out.println("Revision: " + getRevision());
        System.out.println("Based on JPC PC emulator.");
        System.out.println("Copyright (C) 2007-2009 Isis Innovation Limited");
        System.out.println("Copyright (C) 2009-2010 H. Ilari Liusvaara");
        System.out.println("JPC-RR is released under GPL Version 2 and comes with absoutely no warranty.");

        //Probe if rename-over is supported.
        Misc.probeRenameOver(ArgProcessor.findFlag(args, "-norenames"));

        Plugins pluginManager = new Plugins();
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
                    doCommand(pluginManager, cmd);
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
                doCommand(pluginManager, cmd);
            } catch (Exception e) {
                errorDialog(e, "Command execution failed", null, "Dismiss");
            }
        }
    }
}
