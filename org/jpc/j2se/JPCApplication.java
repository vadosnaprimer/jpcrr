/*
    JPC: A x86 PC Hardware Emulator for a pure Java Virtual Machine
    Release Version 2.0

    A project from the Physics Dept, The University of Oxford

    Copyright (C) 2007 Isis Innovation Limited

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

    Details (including contact information) can be found at:

    www.physics.ox.ac.uk/jpc
*/

package org.jpc.j2se;

import java.util.*;
import java.util.zip.*;
import java.util.jar.*;
import java.io.*;
import java.beans.*;
import java.awt.*;
import java.text.*;
import java.net.*;
import java.awt.color.*;
import java.awt.image.*;
import java.awt.event.*;
import java.awt.Desktop;
import java.awt.Toolkit;

import javax.swing.*;
import javax.swing.event.*;

import org.jpc.emulator.processor.*;
import org.jpc.emulator.*;
import org.jpc.support.*;
import org.jpc.emulator.motherboard.*;
import org.jpc.emulator.memory.*;
import org.jpc.emulator.memory.codeblock.*;
import org.jpc.emulator.peripheral.*;
import org.jpc.emulator.pci.peripheral.*;


public class JPCApplication extends PCMonitorFrame
{
    public static final int WIDTH = 720;
    public static final int HEIGHT = 400 + 100;
    private static final String[] defaultArgs = { "-fda", "mem:floppy.img", "-hda", "mem:dosgames.img", "-boot", "fda"};
//     private static final String[] defaultArgs = { "-hda", "mem:linux.img", "-boot", "hda"};
    private static final String aboutUsText =
        "JPC: Developed since August 2005 in Oxford University's Subdepartment of Particle Physics.\n\n" +
        "For more information visit our website at:\nhttp://www-jpc.physics.ox.ac.uk";
    private static final  String defaultLicence =
        "JPC is released under GPL Version 2 and comes with absoutely no warranty<br/><br/>" +
        "See www-jpc.physics.ox.ac.uk for more details";


    private boolean running = false;
    private JMenuItem aboutUs, gettingStarted;
    private JMenuItem saveStatusDump, saveSR, loadSR;
    private JMenuItem changeFloppyA, changeFloppyB;
    private JCheckBoxMenuItem stopVRetraceStart, stopVRetraceEnd;

    private JEditorPane licence, instructionsText;
    private JScrollPane monitorPane;

    private static JFileChooser floppyImageChooser, snapshotChooser;


    public JPCApplication(String[] args, PC pc) throws Exception
    {
        super("JPC - " + ArgProcessor.findArg(args, "hda" , null), pc, args);

        JMenuBar bar = getJMenuBar();

        JMenu breakpoints = new JMenu("Breakpoints");
        stopVRetraceStart = new JCheckBoxMenuItem("Trap VRetrace start");
        stopVRetraceStart.addActionListener(this);
        breakpoints.add(stopVRetraceStart);
        stopVRetraceEnd = new JCheckBoxMenuItem("Trap VRetrace end");
        stopVRetraceEnd.addActionListener(this);
        breakpoints.add(stopVRetraceEnd);
        bar.add(breakpoints);


        JMenu snap = new JMenu("Snapshot");
        saveStatusDump = snap.add("Save Status Dump");
        saveStatusDump.addActionListener(this);
        saveSR = snap.add("Save Snapshot (SR)");
        saveSR.addActionListener(this);
        loadSR = snap.add("load Snapshot (SR)");
        loadSR.addActionListener(this);
        bar.add(snap);

        JMenu drives = new JMenu("Drives");
        changeFloppyA = drives.add("Change Floppy A");
        changeFloppyA.addActionListener(this);
        changeFloppyB = drives.add("Change Floppy B");
        changeFloppyB.addActionListener(this);
        bar.add(drives);

        JMenu help = new JMenu("Help");
        gettingStarted = help.add("Getting Started");
        gettingStarted.addActionListener(this);
        aboutUs = help.add("About JPC");
        aboutUs.addActionListener(this);
        bar.add(help);

        floppyImageChooser =  new JFileChooser(System.getProperty("user.dir"));
        floppyImageChooser.setApproveButtonText("Load Floppy Drive Image");
        snapshotChooser = new JFileChooser(System.getProperty("user.dir"));
        snapshotChooser.setApproveButtonText("Load JPC Snapshot");

        try
        {
            licence = new JEditorPane(ClassLoader.getSystemResource("resource/licence.html"));
        }
        catch (Exception e)
        {
            e.printStackTrace();
            try
            {
                licence = new JEditorPane("text/html", defaultLicence);
            }
            catch (Exception f) {}
        }
        licence.setEditable(false);
        getMonitorPane().setViewportView(licence);

        getContentPane().add("South", new KeyTypingPanel(monitor));

        getContentPane().validate();
    }

    protected synchronized void start()
    {
        saveSR.setEnabled(false);
        loadSR.setEnabled(false);
        saveStatusDump.setEnabled(false);
        super.start();
        getMonitorPane().setViewportView(monitor);
        monitor.validate();
        monitor.requestFocus();
    }

    protected synchronized void stopNoWait()
    {
        super.stopNoWait();
        saveSR.setEnabled(true);
        loadSR.setEnabled(true);
        saveStatusDump.setEnabled(true);
    }

    private void load(String loadString, JFileChooser fileChooser, boolean reboot)
    {
        int load = 0;
        if (reboot)
            load = JOptionPane.showOptionDialog(this, "Selecting " + loadString + " now will cause JPC to reboot. Are you sure you want to continue?", "Warning", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE, null, new String[] {"Continue","Cancel"}, "Continue");
        else
            load = JOptionPane.showOptionDialog(this, "Selecting " + loadString + " now will lose the current state of JPC. Are you sure you want to continue?", "Warning", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE, null, new String[] {"Continue","Cancel"}, "Continue");

        System.out.println("load = " + load);

        if (load == 0)
        {
            if (running)
                stop();

            int returnVal = 0;
            if (fileChooser != null)
                returnVal = fileChooser.showDialog(this, null);

            if (returnVal == 0)
            {
                try
                {
                    if (fileChooser == null)
                    {
                        JarFile jarFile = new JarFile("JPC.jar");
                        InputStream in = jarFile.getInputStream(jarFile.getEntry(loadString));
                        File outFile = File.createTempFile(loadString, null);
                        outFile.deleteOnExit();
                        OutputStream out = new FileOutputStream(outFile);

                        byte[] buffer = new byte[2048];
                        while (true)
                        {
                            int r = in.read(buffer);
                            if (r < 0)
                                break;
                            out.write(buffer, 0, r);
                        }

                        in.close();
                        out.close();
                        jarFile.close();

                        SeekableIODevice ioDevice = new FileBackedSeekableIODevice(outFile.getPath());
                        pc.getDrives().setHardDrive(0, new RawBlockDevice(ioDevice));

                        setTitle("JPC - " + loadString);
                    }
                }
                catch (IndexOutOfBoundsException e)
                {
                    //there were too many files in the directory tree selected
                    System.out.println("too many files");
                    JOptionPane.showMessageDialog(this, "The directory you selected contains too many files. Try selecting a directory with fewer contents.", "Error loading directory", JOptionPane.ERROR_MESSAGE, null);
                    return;
                }
                catch (Exception e)
                {
                    System.err.println(e);
                }
            }

            monitor.stopUpdateThread();
            if (reboot)
                pc.reset();
            monitor.revalidate();
            monitor.requestFocus();

            if (reboot)
                reset();
        }
    }

    private void loadSnapShotSR()
    {
        int load = 0;
        load = JOptionPane.showOptionDialog(this, "Selecting this now will lose the current state of JPC. Are you sure you want to continue?", "Warning", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE, null, new String[] {"Continue","Cancel"}, "Continue");
        if(load != 0)
            return;

         int returnVal = 0;
         returnVal = snapshotChooser.showDialog(this, null);

         if (returnVal == 0)
         {
            try
            {
                File file = snapshotChooser.getSelectedFile();
                System.out.println("Loading a snapshot of JPC");
                ZipFile zip2 = new ZipFile(file);
                ZipEntry entry = zip2.getEntry("HardwareSavestateSR");
                DataInput zip = new DataInputStream(zip2.getInputStream(entry));
                org.jpc.support.SRLoader loader = new org.jpc.support.SRLoader(zip);
                pc = (PC)(loader.loadObject());
                monitor.reconnect(pc);
                zip2.close();
                System.out.println("done");
                getMonitorPane().setViewportView(monitor);
                monitor.validate();
                monitor.requestFocus();
                stopVRetraceStart.setSelected(false);
                stopVRetraceEnd.setSelected(false);
            } catch (IndexOutOfBoundsException e)
            {
                //there were too many files in the directory tree selected
                System.out.println("too many files");
                JOptionPane.showMessageDialog(this, "The directory you selected contains too many files. Try selecting a directory with fewer contents.", "Error loading directory", JOptionPane.ERROR_MESSAGE, null);
                return;
            }
            catch (Exception e)
            {
                System.err.println(e);
                e.printStackTrace();
            }
        }

        monitor.stopUpdateThread();
        monitor.revalidate();
        monitor.requestFocus();
}

    private void saveSnapShotSR()
    {
        if (running)
            stop();
        int returnVal = snapshotChooser.showDialog(this, "Save JPC Snapshot (SR)");
        File file = snapshotChooser.getSelectedFile();

        if (returnVal == 0)
            try
            {
                DataOutputStream out = new DataOutputStream(new FileOutputStream(file));
                ZipOutputStream zip2 = new ZipOutputStream(out);

                System.out.println("Savestating...\n");
                ZipEntry entry = new ZipEntry("HardwareSavestateSR");
                zip2.putNextEntry(entry);
                DataOutput zip = new DataOutputStream(zip2);
                org.jpc.support.SRDumper dumper = new org.jpc.support.SRDumper(zip);
                dumper.dumpObject(pc);
                zip2.closeEntry();
                //monitor.saveState(zip2);
                zip2.close();
                System.out.println("Savestate complete; " + dumper.dumpedObjects() + " objects dumped.\n");
            }
            catch (Exception e)
            {
                System.err.println(e);
                e.printStackTrace();
            }
    }

    private void saveStatusDump()
    {
        int returnVal = snapshotChooser.showDialog(this, "Save Status dump");
        File file = snapshotChooser.getSelectedFile();

        if (returnVal == 0)
            try
            {
                PrintStream out = new PrintStream(new FileOutputStream(file));
                org.jpc.support.StatusDumper sd = new org.jpc.support.StatusDumper(out);
                pc.dumpStatus(sd);
                System.err.println("Dumped " + sd.dumpedObjects() + " objects");
            }
            catch (Exception e)
            {
                System.err.println(e);
                e.printStackTrace();
            }
    }

    private void showAboutUs()
    {
        Object[] buttons = {"Visit our Website", "Ok"};
        int i =JOptionPane.showOptionDialog(this, aboutUsText, "JPC info", JOptionPane.YES_NO_OPTION, JOptionPane.INFORMATION_MESSAGE, null, buttons, buttons[1]);
        if (i == 0)
        {
            Desktop desktop = null;
            if (Desktop.isDesktopSupported())
            {
                desktop = Desktop.getDesktop();
                try
                {
                    desktop.browse(new URI("http://www-jpc.physics.ox.ac.uk"));
                }
                catch (Exception e)
                {
                    System.err.println(e);
                }
            }
        }
    }

    private void changeFloppy(int i)
    {
        int returnVal = floppyImageChooser.showDialog(this, "Load Floppy Drive Image");
        File file = floppyImageChooser.getSelectedFile();

        if (returnVal == 0)
            try
            {
                BlockDevice device = null;
                Class blockClass = Class.forName("org.jpc.support.FileBackedSeekableIODevice");
                SeekableIODevice ioDevice = (SeekableIODevice)(blockClass.newInstance());
                ioDevice.configure(file.getPath());
                device = new RawBlockDevice(ioDevice);
                pc.setFloppy(device, i);
            }
            catch (Exception e)
            {
                System.err.println(e);
            }
    }

    public void actionPerformed(ActionEvent evt)
    {
        super.actionPerformed(evt);

        if (evt.getSource() == doubleSize)
        {
            if (doubleSize.isSelected())
                setBounds(100, 100, (WIDTH*2)+20, (HEIGHT*2)+70);
            else
                setBounds(100, 100, WIDTH+20, HEIGHT+70);
        }
        else if (evt.getSource() == saveStatusDump)
            saveStatusDump();
        else if (evt.getSource() == saveSR)
            saveSnapShotSR();
        else if (evt.getSource() == loadSR)
            loadSnapShotSR();
        else if (evt.getSource() == changeFloppyA)
            changeFloppy(0);
        else if (evt.getSource() == changeFloppyB)
            changeFloppy(1);
        else if (evt.getSource() == gettingStarted)
        {
            stop();
            getMonitorPane().setViewportView(licence);
        }
        else if (evt.getSource() == aboutUs)
            showAboutUs();
        else if (evt.getSource() == stopVRetraceStart)
            pc.getTraceTrap().setTrapFlag(TraceTrap.TRACE_STOP_VRETRACE_START, stopVRetraceStart.isSelected());
        else if (evt.getSource() == stopVRetraceEnd)
            pc.getTraceTrap().setTrapFlag(TraceTrap.TRACE_STOP_VRETRACE_END, stopVRetraceEnd.isSelected());
    }

    private static class ImageFileFilter extends javax.swing.filechooser.FileFilter
    {
        public boolean accept(File f)
        {
            if (f.isDirectory())
                return true;

            String extension = getExtension(f);
            if ((extension != null) && (extension.equals("img")))
                return true;
            return false;
        }

        private String getExtension(File f)
        {
            String ext = null;
            String s = f.getName();
            int i = s.lastIndexOf('.');

            if (i > 0 &&  i < s.length() - 1)
            {
                ext = s.substring(i+1).toLowerCase();
            }
            return ext;
        }

        public String getDescription()
        {
            return "Shows disk image files and directories";
        }
    }


    public static void main(String[] args) throws Exception
    {
        try
        {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        }
        catch (Exception e) {}

        if (args.length == 0)
            args = defaultArgs;

        PC pc = PC.createPC(args, new VirtualClock());
        JPCApplication app = new JPCApplication(args, pc);
        String pngDump = ArgProcessor.scanArgs(args, "dumpvideo", null);
        if(pngDump != null)
            app.setPNGSave(new PNGSaver(pngDump));

        app.setBounds(100, 100, WIDTH+20, HEIGHT+70);
        try
        {
            app.setIconImage(Toolkit.getDefaultToolkit().getImage(ClassLoader.getSystemResource("resource/jpcicon.png")));
        }
        catch (Exception e) {}

        app.validate();
        app.setVisible(true);
    }
}
