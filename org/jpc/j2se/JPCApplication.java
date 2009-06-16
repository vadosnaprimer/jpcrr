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
    private static final String aboutUsText =
        "JPC-RR: developed based on original JPC source since April 2009 by H. Ilari Liusvaara.\n\n" + 
        "JPC: Developed since August 2005 in Oxford University's Subdepartment of Particle Physics.\n\n" +
        "For more information about JPC (NOT JPC-RR) visit our website at:\nhttp://www-jpc.physics.ox.ac.uk";
        
    private static final  String defaultLicence =
        "JPC-RR is released under GPL Version 2 and comes with absoutely no warranty<br/><br/>";


    private boolean running = false;
    private JMenuItem aboutUs, gettingStarted;
    private JMenuItem saveStatusDump, saveSR, loadSR;
    private JMenuItem changeFloppyA, changeFloppyB;
    private JCheckBoxMenuItem stopVRetraceStart, stopVRetraceEnd;

    private JEditorPane licence, instructionsText;
    private JScrollPane monitorPane;

    private ImageLibrary imgLibrary;

    private static JFileChooser floppyImageChooser, snapshotChooser;


    public JPCApplication(String[] args, PC pc) throws Exception
    {
        super("JPC-RR", pc, args);

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
        aboutUs = help.add("About JPC-RR");
        aboutUs.addActionListener(this);
        bar.add(help);

        floppyImageChooser =  new JFileChooser(System.getProperty("user.dir"));
        floppyImageChooser.setApproveButtonText("Load Floppy Drive Image");
        snapshotChooser = new JFileChooser(System.getProperty("user.dir"));
        snapshotChooser.setApproveButtonText("Load JPC-RR Snapshot");

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

    private class LoadStateTask extends AsyncGUITask
    {
        File choosen;
        Exception caught;
        PleaseWait pw;

        public LoadStateTask()
        {
            choosen = null;
            pw = new PleaseWait("Loading savestate...");
        }

        protected void runPrepare()
        {
            JPCApplication.this.setEnabled(false);
            int returnVal = snapshotChooser.showDialog(JPCApplication.this, "Load JPC-RR Snapshot");
            choosen = snapshotChooser.getSelectedFile();

            if (returnVal != 0)
                choosen = null; 
            pw.popUp();
        }

        protected void runFinish()
        {
            if(caught == null) { 
                try {
                    monitor.reconnect(pc);
                    System.out.println("Loadstate done");
                    getMonitorPane().setViewportView(monitor);
                    monitor.validate();
                    monitor.requestFocus();
                    stopVRetraceStart.setSelected(false);
                    stopVRetraceEnd.setSelected(false);
                    monitor.stopUpdateThread();
                    monitor.revalidate();
                    monitor.requestFocus();
                } catch(Exception e) {
                    caught = e;
                }
            }
            pw.popDown();
            if(caught != null) {
                errorDialog(caught, "Savestate failed", JPCApplication.this, "Dismiss");
            }
            JPCApplication.this.setEnabled(true);
        }

        protected void runTask()
        {
            if(choosen == null)
                return;

            try {
                System.out.println("Loading a snapshot of JPC-RR");
                ZipFile zip2 = new ZipFile(choosen);
                ZipEntry entry = zip2.getEntry("HardwareSavestateSR");
                if(entry == null)
                    throw new IOException("Not a savestate file.");
                DataInput zip = new DataInputStream(zip2.getInputStream(entry));
                org.jpc.support.SRLoader loader = new org.jpc.support.SRLoader(zip);
                pc = (PC)(loader.loadObject());
                zip2.close();
            } catch(Exception e) {
                 caught = e;
            }
        }
    }

    private class SaveStateTask extends AsyncGUITask
    {
        File choosen;
        Exception caught;
        PleaseWait pw;

        public SaveStateTask()
        {
            choosen = null;
            pw = new PleaseWait("Saving savestate...");
        }

        protected void runPrepare()
        {
            JPCApplication.this.setEnabled(false);
            int returnVal = snapshotChooser.showDialog(JPCApplication.this, "Save JPC-RR Snapshot");
            choosen = snapshotChooser.getSelectedFile();

            if (returnVal != 0)
                choosen = null; 
            pw.popUp();
        }

        protected void runFinish()
        {
            pw.popDown();
            if(caught != null) {
                errorDialog(caught, "Savestate failed", JPCApplication.this, "Dismiss");
            }
            JPCApplication.this.setEnabled(true);
        }

        protected void runTask()
        {
            if(choosen == null)
                return;

            try {
                DataOutputStream out = new DataOutputStream(new FileOutputStream(choosen));
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
            } catch(Exception e) {
                 caught = e;
            }
        }
    }

    private class StatusDumpTask extends AsyncGUITask
    {
        File choosen;
        Exception caught;
        PleaseWait pw;

        public StatusDumpTask()
        {
            choosen = null;
            pw = new PleaseWait("Saving status dump...");
        }

        protected void runPrepare()
        {
            JPCApplication.this.setEnabled(false);
            int returnVal = snapshotChooser.showDialog(JPCApplication.this, "Save Status dump");
            choosen = snapshotChooser.getSelectedFile();

            if (returnVal != 0)
                choosen = null; 
            pw.popUp();
        }

        protected void runFinish()
        {
            pw.popDown();
            if(caught != null) {
                errorDialog(caught, "Status dump failed", JPCApplication.this, "Dismiss");
            }
            JPCApplication.this.setEnabled(true);
        }

        protected void runTask()
        {
            if(choosen == null)
                return;

            try {
                PrintStream out = new PrintStream(new FileOutputStream(choosen));
                org.jpc.support.StatusDumper sd = new org.jpc.support.StatusDumper(out);
                pc.dumpStatus(sd);
                System.err.println("Dumped " + sd.dumpedObjects() + " objects");
            } catch(Exception e) {
                 caught = e;
            }
        }
    }

    protected void doLoadState()
    {
        (new Thread(new LoadStateTask())).start();
    }

    protected void doSaveState()
    {
        (new Thread(new SaveStateTask())).start();
    }

    protected void doDumpState()
    {
        (new Thread(new StatusDumpTask())).start();
    }


    private void showAboutUs()
    {
        Object[] buttons = {"Dismiss"};
        int i =JOptionPane.showOptionDialog(this, aboutUsText, "JPC-RR info", JOptionPane.YES_NO_OPTION, JOptionPane.INFORMATION_MESSAGE, null, buttons, buttons[1]);
    }

    private void changeFloppy(int drive, int image)
    {
        try
        {
            DiskImage img = pc.getDisks().lookupDisk(image);
            BlockDevice device = new GenericBlockDevice(img, BlockDevice.TYPE_FLOPPY);
            pc.setFloppy(device, drive);
        } catch (Exception e) {
            System.err.println(e);
        }
    }

    private class diskChangeTask extends AsyncGUITask
    {
        int index;
        
        public diskChangeTask(int i)
        {
            index = i;
        }

        protected void runPrepare()
        {
            JPCApplication.this.setEnabled(false);
        }

        protected void runFinish()
        {
            JPCApplication.this.setEnabled(true);
        }

        protected void runTask()
        {
            int doIt = DiskImageChooser.chooseDisk(BlockDevice.TYPE_FLOPPY, imgLibrary);
            if(doIt < -1)
                return;    //Canceled.
            changeFloppy(index, doIt);
        }
    }

    private void changeFloppy(int i)
    {
        (new Thread(new diskChangeTask(i))).start();
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
            doDumpState();
        else if (evt.getSource() == saveSR)
            doSaveState();
        else if (evt.getSource() == loadSR)
            doLoadState();
        else if (evt.getSource() == changeFloppyA)
            changeFloppy(0);
        else if (evt.getSource() == changeFloppyB)
            changeFloppy(1);
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

    public static void errorDialog(Throwable e, String title, java.awt.Component component, String text)
    {
        int i = JOptionPane.showOptionDialog(null, e.getMessage(), title, JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE, null, new String[]{text, "Save stack trace"}, text);
        if(i > 0) {
            JPCApplication.saveStackTrace(e, null, text);
        }
    }

    public static void saveStackTrace(Throwable e, java.awt.Component component, String text)
    {
        StackTraceElement[] traceback = e.getStackTrace();
        StringBuffer sb = new StringBuffer();
        sb.append(e.getMessage() + "\n");
        for(int i = 0; i < traceback.length; i++) {
            StackTraceElement el = traceback[i];
            if(el.isNativeMethod())
                sb.append(el.getMethodName() + " of " + el.getClassName() + " <native>\n");
            else
                sb.append(el.getMethodName() + " of " + el.getClassName() + " <" + el.getFileName() + ":" + 
                    el.getLineNumber() + ">\n");
        }
        String exceptionMessage = sb.toString();
        
        try {
            String traceFileName = "StackTrace-" + System.currentTimeMillis() + ".text";
            PrintStream stream = new PrintStream(traceFileName, "UTF-8");
            stream.print(exceptionMessage);
            stream.close();
            JOptionPane.showOptionDialog(component, "Stack trace saved to " + traceFileName + ".", "Stack trace saved", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE, null, new String[]{text}, text);
        } catch(Exception e2) {
            JOptionPane.showOptionDialog(component, e.getMessage(), "Saving stack trace failed", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE, null, new String[]{text}, text);
        }
    }

    public static void main(String[] args) throws Exception
    {
        try
        {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        }
        catch (Exception e) {}

        String library = ArgProcessor.scanArgs(args, "library", null);
        ImageLibrary _library = new ImageLibrary(library);
        DiskImage.setLibrary(_library);
        PC pc;
        try {
            pc = PC.createPC(args, new VirtualClock());
        } catch(Exception e) {
            errorDialog(e, "PC initialization failed", null, "Quit");
            return;
        }
        JPCApplication app = new JPCApplication(args, pc);
        app.imgLibrary = _library;

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
