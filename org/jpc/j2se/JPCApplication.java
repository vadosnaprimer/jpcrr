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


public class JPCApplication extends JFrame implements ActionListener, Runnable
{
    public static final int WIDTH = 720;
    public static final int HEIGHT = 400 + 100;
    private static final String aboutUsText =
        "JPC-RR: developed based on original JPC source since April 2009 by H. Ilari Liusvaara.\n\n" + 
        "JPC: Developed since August 2005 in Oxford University's Subdepartment of Particle Physics.\n\n" +
        "For more information about JPC (NOT JPC-RR) visit our website at:\nhttp://www-jpc.physics.ox.ac.uk";
        
    private static final  String defaultLicence =
        "JPC-RR is released under GPL Version 2 and comes with absoutely no warranty<br/><br/>";

    protected PC pc;
    protected PCMonitor monitor;
    protected String[] arguments;

    protected JMenuItem quit, stop, start, reset, assemble;
    protected JCheckBoxMenuItem doubleSize;

    private JScrollPane monitorPane;

    private Thread runner;

    private boolean running = false;
    private boolean willCleanup;
    private VirtualKeyboard vKeyboard;
    private JMenuItem aboutUs, gettingStarted;
    private JMenuItem saveStatusDump, saveSR, loadSR;
    private JMenuItem changeFloppyA, changeFloppyB;
    private JCheckBoxMenuItem stopVRetraceStart, stopVRetraceEnd;

    private JEditorPane licence, instructionsText;

    private ImageLibrary imgLibrary;

    private static JFileChooser floppyImageChooser, snapshotChooser;
    private static final long[] stopTime;
    private static final String[] stopLabel;
    private JMenuItem[] timedStops;
    private long imminentTrapTime;

    static
    {
        stopTime = new long[] {-1, 1000, 2000, 5000, 10000, 20000, 50000, 100000, 200000, 500000, 1000000, 2000000, 5000000,
            10000000, 20000000, 50000000, 100000000, 200000000, 500000000, 1000000000, 2000000000, 5000000000L, 
            10000000000L, 20000000000L, 50000000000L};
        stopLabel = new String[] {"(unbounded)", "1µs", "2µs", "5µs", "10µs", "20µs", "50µs", "100µs", "200µs", "500µs", 
            "1ms", "2ms", "5ms", "10ms", "20ms", "50ms", "100ms", "200ms", "500ms", "1s", "2s", "5s", "10s", "20s", "50s"};
    }


    public void connectPC(PC pc)
    {
        monitor.reconnect(pc.getGraphicsCard().getDigitalOut());
        vKeyboard.reconnect(pc.getKeyboard());

        getMonitorPane().setViewportView(monitor);
        monitor.validate();
        monitor.requestFocus();

        start.setEnabled(true);
        stop.setEnabled(false);
        reset.setEnabled(true);
        stopVRetraceStart.setEnabled(true);
        stopVRetraceEnd.setEnabled(true);
        saveStatusDump.setEnabled(true);
        saveSR.setEnabled(true);
        changeFloppyA.setEnabled(true);
        changeFloppyB.setEnabled(true);
        stopVRetraceStart.setSelected(false);
        stopVRetraceEnd.setSelected(false);
        for(int i = 0; i < timedStops.length; i++) {
            timedStops[i].setSelected(false);
            timedStops[i].setEnabled(true);
        }
        timedStops[0].setSelected(true);
        this.imminentTrapTime = -1;
}

    public JPCApplication(String[] args) throws Exception
    {
        super("JPC-RR");
        monitor = new PCMonitor();
        this.pc = null;
        this.arguments = args;
        this.imminentTrapTime = -1;
        this.willCleanup = false;

        monitorPane = new JScrollPane(monitor);
        getContentPane().add("Center", monitorPane);

        JMenuBar bar = new JMenuBar();

        JMenu file = new JMenu("File");
        assemble = file.add("Assemble PC");
        assemble.addActionListener(this);
        start = file.add("Start");
        start.addActionListener(this);
        start.setAccelerator(KeyStroke.getKeyStroke("control S"));
        stop = file.add("Stop");
        stop.addActionListener(this);
        stop.setAccelerator(KeyStroke.getKeyStroke("control shift S"));
        reset = file.add("Reset");
        reset.addActionListener(this);
        file.addSeparator();
        doubleSize = new JCheckBoxMenuItem("Double Size");
        file.add(doubleSize);
        doubleSize.addActionListener(this);
        file.addSeparator();
        quit = file.add("Quit");
        quit.addActionListener(this);
        bar.add(file);

        setJMenuBar(bar);
        setBounds(100, 100, monitor.WIDTH + 20, monitor.HEIGHT + 100);

        stop.setEnabled(false);
        start.setEnabled(false);
        reset.setEnabled(false);

        JMenu breakpoints = new JMenu("Breakpoints");
        stopVRetraceStart = new JCheckBoxMenuItem("Trap VRetrace start");
        stopVRetraceStart.addActionListener(this);
        breakpoints.add(stopVRetraceStart);
        stopVRetraceEnd = new JCheckBoxMenuItem("Trap VRetrace end");
        stopVRetraceEnd.addActionListener(this);
        breakpoints.add(stopVRetraceEnd);
        timedStops = new JCheckBoxMenuItem[stopLabel.length];
        JMenu timed = new JMenu("Timed stops");
        breakpoints.add(timed);
        bar.add(breakpoints);

        stopVRetraceStart.setEnabled(false);
        stopVRetraceEnd.setEnabled(false);
        for(int i = 0; i < timedStops.length; i++) {
            timedStops[i] = new JCheckBoxMenuItem(stopLabel[i]);
            timedStops[i].addActionListener(this);
            timedStops[i].setSelected(false);
            timedStops[i].setEnabled(false);
            timed.add(timedStops[i]);
        }

        JMenu snap = new JMenu("Snapshot");
        saveStatusDump = snap.add("Save Status Dump");
        saveStatusDump.addActionListener(this);
        saveSR = snap.add("Save Snapshot (SR)");
        saveSR.addActionListener(this);
        loadSR = snap.add("load Snapshot (SR)");
        loadSR.addActionListener(this);
        bar.add(snap);

        saveStatusDump.setEnabled(false);
        saveSR.setEnabled(false);


        JMenu drives = new JMenu("Drives");
        changeFloppyA = drives.add("Change Floppy A");
        changeFloppyA.addActionListener(this);
        changeFloppyB = drives.add("Change Floppy B");
        changeFloppyB.addActionListener(this);
        bar.add(drives);

        changeFloppyA.setEnabled(false);
        changeFloppyB.setEnabled(false);

        JMenu help = new JMenu("Help");
        aboutUs = help.add("About JPC-RR");
        aboutUs.addActionListener(this);
        bar.add(help);

        floppyImageChooser =  new JFileChooser(System.getProperty("user.dir"));
        floppyImageChooser.setApproveButtonText("Load Floppy Drive Image");
        snapshotChooser = new JFileChooser(System.getProperty("user.dir"));
        snapshotChooser.setApproveButtonText("Load JPC-RR Snapshot");

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

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
        getContentPane().validate();
    }

    public void setPNGSave(PNGSaver save) 
    {
        monitor.setPNGSave(save);
    }

    public JScrollPane getMonitorPane()
    {
        return monitorPane;
    }

    protected synchronized void start()
    {
        saveSR.setEnabled(false);
        loadSR.setEnabled(false);
        saveStatusDump.setEnabled(false);
        stop.setEnabled(true);
        start.setEnabled(false);
        reset.setEnabled(false);
        stopVRetraceStart.setEnabled(false);
        stopVRetraceEnd.setEnabled(false);
        for(int i = 0; i < timedStops.length; i++) {
            timedStops[i].setEnabled(false);
            timedStops[i].setSelected(false);
        }
        timedStops[0].setSelected(true);

        long current = pc.getSystemClock().getTime();
        if(imminentTrapTime > 0) {
            pc.getTraceTrap().setTrapTime(current + imminentTrapTime);
        }

        int p = Math.max(Thread.currentThread().getThreadGroup().getMaxPriority()-4, Thread.MIN_PRIORITY+1);

        if (!running) {
            monitor.startUpdateThread(p);
            running = true;
            runner = new Thread(this, "PC Execute");
            runner.setPriority(p);
            runner.start();
        }
        getMonitorPane().setViewportView(monitor);
        monitor.validate();
        monitor.requestFocus();
    }

    protected synchronized void stopNoWait()
    {
        running = false;
        runner = null;
        stop.setEnabled(false);
        start.setEnabled(true);
        reset.setEnabled(true);
        monitor.stopUpdateThread();
        saveSR.setEnabled(true);
        loadSR.setEnabled(true);
        saveStatusDump.setEnabled(true);
        stopVRetraceStart.setEnabled(true);
        stopVRetraceEnd.setEnabled(true);
        for(int i = 0; i < timedStops.length; i++) {
            timedStops[i].setEnabled(true);
            timedStops[0].setSelected(false);
        }
        timedStops[0].setSelected(true);
        this.imminentTrapTime = -1;
        pc.getTraceTrap().clearTrapTime();
        pc.getTraceTrap().getAndClearTrapActive();
    }

    protected synchronized void stop()
    {
        willCleanup = true;
        pc.getTraceTrap().doPotentialTrap(TraceTrap.TRACE_STOP_IMMEDIATE);
        running = false;
        boolean succeeded = false;
        while(!succeeded) {
            try
            {
                runner.join();
                succeeded = true;
            }
            catch (Throwable t){}
        }
        willCleanup = false;
        stopNoWait();
    }

    protected void reset()
    {
        pc.reset();
    }

    public void run()
    {
        pc.start();
        long execCount = 0;
        boolean exitNow = false;
        try
        {
            while (running) {
                execCount += pc.execute();
                if(pc.getHitTraceTrap()) {
                    if(pc.getAndClearTripleFaulted())
                        JOptionPane.showOptionDialog(this, "CPU shut itself down due to triple fault. Rebooting the system.", "Triple fault!", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE, null, new String[]{"Dismiss"}, "Dismiss");
                    if(!willCleanup)
                        SwingUtilities.invokeAndWait(new Thread() { public void run() { stopNoWait(); }});
                    break;
                }
            }
        }
        catch (Exception e)
        {
            JPCApplication.errorDialog(e, "Hardware emulator internal error", this, "Dismiss");
        }
        finally
        {
            pc.stop();
            System.err.println("PC Stopped");
        }
    }

    private class AssembleTask extends AsyncGUITask
    {
        Exception caught;
        PleaseWait pw;

        public AssembleTask()
        {
            pw = new PleaseWait("Assembling PC...");
        }

        protected void runPrepare()
        {
            JPCApplication.this.setEnabled(false);
            pw.popUp();
        }

        protected void runFinish()
        {
            if(caught == null) { 
                try {
                    connectPC(pc);
                } catch(Exception e) {
                    caught = e;
                }
            }
            pw.popDown();
            if(caught != null) {
                errorDialog(caught, "PC Assembly failed", JPCApplication.this, "Dismiss");
            }
            JPCApplication.this.setEnabled(true);
        }

        protected void runTask()
        {
            try {
                pc = PC.createPC(PC.parseArgs(arguments), new VirtualClock());
            } catch(Exception e) {
                 caught = e;
            }
        }
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
                    connectPC(pc);
                    System.out.println("Loadstate done");
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

    protected void doAssemble()
    {
        (new Thread(new AssembleTask())).start();
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
        if (evt.getSource() == quit)
            System.exit(0);
        else if (evt.getSource() == stop)
            stop();
        else if (evt.getSource() == start)
            start();
        else if (evt.getSource() == reset)
            reset();
        else if (evt.getSource() == doubleSize)
            monitor.setDoubleSize(doubleSize.isSelected());
        else if (evt.getSource() == doubleSize)
        {
            if (doubleSize.isSelected())
                setBounds(100, 100, (WIDTH*2)+20, (HEIGHT*2)+70);
            else
                setBounds(100, 100, WIDTH+20, HEIGHT+70);
        }
        else if (evt.getSource() == assemble)
            doAssemble();
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
        for(int i = 0; i < timedStops.length; i++) {
            if(evt.getSource() == timedStops[i]) {
                this.imminentTrapTime = stopTime[i];
                for(int j = 0; j < timedStops.length; j++)
                    timedStops[j].setSelected(false);
                timedStops[i].setSelected(true);
            }
        }
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
        String message = e.getMessage();
        //Give nicer errors for some internal ones.
        if(e instanceof NullPointerException)
            message = "Internal Error: Null pointer dereference";  
        if(e instanceof ArrayIndexOutOfBoundsException)
            message = "Internal Error: Array bounds exceeded";
        if(e instanceof StringIndexOutOfBoundsException)
            message = "Internal Error: String bounds exceeded";
        int i = JOptionPane.showOptionDialog(null, message, title, JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE, null, new String[]{text, "Save stack trace"}, text);
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
        JPCApplication app = new JPCApplication(args);
        VirtualKeyboard keyboard = new VirtualKeyboard();
        app.vKeyboard = keyboard;
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
