/*
    JPC: A x86 PC Hardware Emulator for a pure Java Virtual Machine
    Release Version 2.0

    A project from the Physics Dept, The University of Oxford

    Copyright (C) 2007-2009 Isis Innovation Limited

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

    www-jpc.physics.ox.ac.uk
*/

package org.jpc.j2se;

import java.awt.Component;
import java.awt.BorderLayout;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.*;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;
import java.util.jar.*;
import java.util.logging.*;
import java.util.zip.*;
import java.security.AccessControlException;
import javax.swing.*;

import org.jpc.emulator.PC;
import org.jpc.emulator.TraceTrap;
import org.jpc.emulator.pci.peripheral.VGACard;
import org.jpc.emulator.peripheral.FloppyController;
import org.jpc.emulator.peripheral.Keyboard;
import org.jpc.support.*;

public class JPCApplication extends JFrame implements PCControl, ActionListener, Runnable
{
    private static final Logger LOGGING = Logger.getLogger(JPCApplication.class.getName());
    private static final URI JPC_URI = URI.create("http://www-jpc.physics.ox.ac.uk/");
    private static final String IMAGES_PATH = "resources/images/";
    private static final int MONITOR_WIDTH = 720;
    private static final int MONITOR_HEIGHT = 400 + 100;
    private static final String[] DEFAULT_ARGS =
    {
        "-fda", "mem:resources/images/floppy.img",
        "-hda", "mem:resources/images/dosgames.img",
        "-boot", "fda"
    };
    private static final String ABOUT_US =
            "JPC-RR: Rerecording PC emulator based on JPC PC emulator\n\n" + 
            "JPC: Developed since August 2005 in Oxford University's Subdepartment of Particle Physics.\n\n" +
            "For more information about JPC visit website at:\n" + JPC_URI.toASCIIString();
    private static final String LICENCE_HTML =
            "JPC-RR is released under GPL Version 2 and comes with absoutely no warranty<br/><br/>";
    private static JEditorPane LICENCE;
    private VirtualKeyboard vKeyboard;
    private JCheckBoxMenuItem stopVRetraceStart, stopVRetraceEnd;

    static
    {
        ClassLoader context = Thread.currentThread().getContextClassLoader();
        URL licence = context.getResource("resources/licence.html");
        if (licence != null)
        {
            try
            {
                LICENCE = new JEditorPane(licence);
            } catch (IOException e)
            {
                LICENCE = new JEditorPane("text/html", LICENCE_HTML);
            }
        } else
        {
            LICENCE = new JEditorPane("text/html", LICENCE_HTML);
        }
        LICENCE.setEditable(false);
    }

    private JFileChooser snapshotFileChooser;
    private JMenuItem loadSnapshot;
    private JMenuItem saveSnapshot;
    private JMenuItem saveStatus;
    private ImageLibrary imgLibrary;
    private JMenuItem changeFloppyA, changeFloppyB, changeCDROM;

    protected String[] arguments;

    protected PC pc;
    protected final PCMonitor monitor;

    private JScrollPane monitorPane;
    private JMenuItem mStart, mStop, mReset, mAssemble;

    private volatile boolean running;
    private Thread runner;
    private boolean willCleanup;
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
        monitor.reconnect(pc);
        Keyboard keyboard = (Keyboard) pc.getComponent(Keyboard.class);
        vKeyboard.reconnect(keyboard);
        this.pc = pc; 

        getMonitorPane().setViewportView(monitor);
        monitor.validate();
        monitor.requestFocus();
        mStart.setEnabled(true);
        mStop.setEnabled(false);
        mReset.setEnabled(true);
        stopVRetraceStart.setEnabled(true);
        stopVRetraceEnd.setEnabled(true);
        saveStatus.setEnabled(true);
        saveSnapshot.setEnabled(true);
        changeFloppyA.setEnabled(true);
        changeFloppyB.setEnabled(true);
        if(pc.getCDROMIndex() < 0)
            changeCDROM.setEnabled(false);
        else
            changeCDROM.setEnabled(true);
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
        running = false;
        monitor = new PCMonitor();
        this.willCleanup = false;
        monitorPane = new JScrollPane(monitor);
        getContentPane().add("Center", monitorPane);
        if (monitor != null)
            monitor.setFrame(monitorPane);

        this.pc = null;
        this.arguments = args;

        JMenuBar bar = new JMenuBar();

        JMenu file = new JMenu("File");
        (mAssemble = file.add("Assemble")).addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e)
                {
                    (new Thread(JPCApplication.this.new AssembleTask())).start();
                }
            });
        (mStart = file.add("Start")).addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e)
                {
                    start();
                }
            });
        (mStop = file.add("Stop")).addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e)
                {
                    stop();
                }
            });
        (mReset = file.add("Reset")).addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e)
                {
                    reset();
                }
            });
        file.addSeparator();
        file.add("Quit").addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e)
                {
                    System.exit(0);
                }
            });

        bar.add(file);

        mStop.setEnabled(false);
        mStart.setEnabled(false);
        mReset.setEnabled(false);

        setJMenuBar(bar);

        try
        {
            setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        }
        catch (AccessControlException e)
        {
            LOGGING.log(Level.WARNING, "Not able to add some components to frame.", e);
        }
        snapshotFileChooser = new JFileChooser(System.getProperty("user.dir"));

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

        bar.add(breakpoints);

        stopVRetraceStart.setEnabled(false);
        stopVRetraceEnd.setEnabled(false);

        JMenu snap = new JMenu("Snapshot");
        (saveSnapshot = snap.add("Save Snapshot")).addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent ev)
            {
                (new Thread(JPCApplication.this.new SaveStateTask())).start();
            }
        });
        (loadSnapshot = snap.add("Load Snapshot")).addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent ev)
            {
                (new Thread(JPCApplication.this.new LoadStateTask())).start();
            }
        });
        (saveStatus = snap.add("Save Status Dump")).addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent ev)
            {
                (new Thread(JPCApplication.this.new StatusDumpTask())).start();
            }
        });
        bar.add(snap);

        saveSnapshot.setEnabled(false);
        saveStatus.setEnabled(false);

        JMenu drivesMenu = new JMenu("Drives");
        changeFloppyA = drivesMenu.add("Change Floppy A");
        changeFloppyA.addActionListener(this);
        changeFloppyB = drivesMenu.add("Change Floppy B");
        changeFloppyB.addActionListener(this);
        changeCDROM = drivesMenu.add("Change CD-ROM");
        changeCDROM.addActionListener(this);
        bar.add(drivesMenu);

        changeFloppyA.setEnabled(false);
        changeFloppyB.setEnabled(false);
        changeCDROM.setEnabled(false);

        JMenu help = new JMenu("Help");
        help.add("Getting Started").addActionListener(new ActionListener()
        {

            public void actionPerformed(ActionEvent evt)
            {
                JFrame help = new JFrame("JPC-RR - Getting Started");
                help.setIconImage(Toolkit.getDefaultToolkit().getImage(ClassLoader.getSystemResource("resources/icon.png")));
                help.getContentPane().add("Center", new JScrollPane(LICENCE));
                help.setBounds(300, 200, MONITOR_WIDTH + 20, MONITOR_HEIGHT - 70);
                help.setVisible(true);
                getContentPane().validate();
                getContentPane().repaint();
            }
        });
        help.add("About JPC-RR").addActionListener(new ActionListener()
        {

            public void actionPerformed(ActionEvent evt)
            {
                Object[] buttons =
                {
                    "Visit our Website", "Ok"
                };
                if (JOptionPane.showOptionDialog(JPCApplication.this, ABOUT_US, "About JPC-RR", JOptionPane.YES_NO_OPTION, JOptionPane.INFORMATION_MESSAGE, null, buttons, buttons[1]) == 0)
                {
                    if (Desktop.isDesktopSupported())
                    {
                        try
                        {
                            Desktop.getDesktop().browse(JPC_URI);
                        } catch (IOException e)
                        {
                            LOGGING.log(Level.INFO, "Couldn't find or launch the default browser.", e);
                        } catch (UnsupportedOperationException e)
                        {
                            LOGGING.log(Level.INFO, "Browse action not supported.", e);
                        } catch (SecurityException e)
                        {
                            LOGGING.log(Level.INFO, "Browse action not permitted.", e);
                        }
                    }
                }
            }
        });
        bar.add(help);

        setSize(monitor.getPreferredSize());
        LICENCE.setPreferredSize(monitor.getPreferredSize());
        getMonitorPane().setViewportView(LICENCE);
        getContentPane().validate();
    }

    public void setSize(Dimension d)
    {
        super.setSize(new Dimension(monitor.getPreferredSize().width, d.height + 60));
        getMonitorPane().setPreferredSize(new Dimension(monitor.getPreferredSize().width + 2, monitor.getPreferredSize().height + 2));
    }

    public synchronized void start()
    {
        saveSnapshot.setEnabled(false);
        loadSnapshot.setEnabled(false);
        mStop.setEnabled(true);
        mStart.setEnabled(false);
        mReset.setEnabled(false);
        if (running)
            return;
        monitor.startUpdateThread();
        stopVRetraceStart.setEnabled(false);
        stopVRetraceEnd.setEnabled(false);
        for(int i = 0; i < timedStops.length; i++) {
            timedStops[i].setEnabled(false);
            timedStops[i].setSelected(false);
        }
        timedStops[0].setSelected(true);

        Clock sysClock = (Clock)pc.getComponent(Clock.class);
        long current = sysClock.getTime();
        if(imminentTrapTime > 0) {
            pc.getTraceTrap().setTrapTime(current + imminentTrapTime);
        }

        running = true;
        runner = new Thread(this, "PC Execute");
        runner.start();
        getMonitorPane().setViewportView(monitor);
        monitor.validate();
        monitor.requestFocus();
    }

    protected synchronized void stopNoWait()
    {
        running = false;
        runner = null;
        mStop.setEnabled(false);
        mStart.setEnabled(true);
        mReset.setEnabled(true);
        saveSnapshot.setEnabled(true);
        loadSnapshot.setEnabled(true);
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

    public synchronized void stop()
    {
        willCleanup = true;
        pc.getTraceTrap().doPotentialTrap(TraceTrap.TRACE_STOP_IMMEDIATE);
        running = false;
        while((runner != null) && runner.isAlive())
        {
            try
            {
                runner.join();
            }
            catch (InterruptedException e) {}
        }
        willCleanup = false;
        stopNoWait();
    }

    public JScrollPane getMonitorPane()
    {
        return monitorPane;
    }

    protected void reset()
    {
        pc.reset();
    }

    public void setPNGSave(PNGSaver save) 
    {
        monitor.setPNGSave(save);
    }

    public synchronized boolean isRunning()
    {
        return running;
    }

    public void run()
    {
        pc.start();
        try
        {
            long totalExec = 0;
            while (running)
            {
                pc.execute();
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
            LOGGING.log(Level.INFO, "PC Stopped");
        }
    }

    private void changeFloppy(int drive, int image)
    {
        try
        {
            DiskImage img = pc.getDisks().lookupDisk(image);
            BlockDevice device = new GenericBlockDevice(img, BlockDevice.Type.FLOPPY);
            DriveSet drives = (DriveSet)pc.getComponent(DriveSet.class);
            FloppyController fdc = (FloppyController)pc.getComponent(FloppyController.class);
            fdc.changeDisk(device, drive);
        } catch (Exception e) {
            System.err.println(e);
            e.printStackTrace();
        }
    }

    private void changeFloppy(int i)
    {
        int doIt = DiskImageChooser.chooseDisk(BlockDevice.Type.FLOPPY, imgLibrary);
        if(doIt < -1)
            return;    //Canceled.
        changeFloppy(i, doIt);
    }

    private void changeCDROM()
    {
        int doIt = DiskImageChooser.chooseDisk(BlockDevice.Type.CDROM, imgLibrary);
        if(doIt < -1)
            return;    //Canceled.
        try
        {
            DiskImage img = pc.getDisks().lookupDisk(doIt);
            DriveSet drives = (DriveSet)pc.getComponent(DriveSet.class);
            int index = pc.getCDROMIndex();
            if(index < 0)
                throw new IOException("PC has no CD-ROM drive!");
            ((GenericBlockDevice)drives.getHardDrive(index)).configure(img);
        } catch (Exception e) {
            System.err.println(e);
            e.printStackTrace();
        }

    }


    private void saveStatusDump(File file) throws IOException
    {
        PrintStream out = new PrintStream(new FileOutputStream(file));
        org.jpc.support.StatusDumper sd = new org.jpc.support.StatusDumper(out);
        pc.dumpStatus(sd);
        System.err.println("Dumped " + sd.dumpedObjects() + " objects");
        out.flush();
    }

    private static final Iterator<String> getResources(String directory)
    {
        ClassLoader context = Thread.currentThread().getContextClassLoader();

        List<String> resources = new ArrayList<String>();

        ClassLoader cl = JPCApplication.class.getClassLoader();
        if (!(cl instanceof URLClassLoader))
            throw new IllegalStateException();
        URL[] urls = ((URLClassLoader) cl).getURLs();

        int slash = directory.lastIndexOf("/");
        String dir = directory.substring(0, slash + 1);
        for (int i=0; i<urls.length; i++)
        {
            if (!urls[i].toString().endsWith(".jar"))
                continue;
            try
            {
                JarInputStream jarStream = new JarInputStream(urls[i].openStream());
                while (true)
                {
                    ZipEntry entry = jarStream.getNextEntry();
                    if (entry == null)
                        break;
                    if (entry.isDirectory())
                        continue;

                    String name = entry.getName();
                    slash = name.lastIndexOf("/");
                    String thisDir = "";
                    if (slash >= 0)
                        thisDir = name.substring(0, slash + 1);

                    if (!dir.equals(thisDir))
                        continue;
                    resources.add(name);
                }

                jarStream.close();
            }
            catch (IOException e) { e.printStackTrace();}
        }
        InputStream stream = context.getResourceAsStream(directory);
        try
        {
            if (stream != null)
            {
                Reader r = new InputStreamReader(stream);
                StringBuilder sb = new StringBuilder();
                char[] buffer = new char[1024];
                try
                {
                    while (true)
                    {
                        int length = r.read(buffer);
                        if (length < 0)
                        {
                            break;
                        }
                        sb.append(buffer, 0, length);
                    }
                } finally
                {
                    r.close();
                }

                for (String s : sb.toString().split("\n"))
                {
                    if (context.getResource(directory + s) != null)
                    {
                        resources.add(s);
                    }
                }
            }
        }
        catch (IOException e)
        {
            LOGGING.log(Level.INFO, "Exception reading images directory stream", e);
        }

        return resources.iterator();
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
            int returnVal = snapshotFileChooser.showDialog(JPCApplication.this, "Load JPC-RR Snapshot");
            choosen = snapshotFileChooser.getSelectedFile();

            if (returnVal != 0)
                choosen = null; 
            pw.popUp();
        }

        protected void runFinish()
        {
            if(caught == null) { 
                try {
                    connectPC(pc);
                    getMonitorPane().setViewportView(monitor);
                    System.out.println("Loadstate done");
                } catch(Exception e) {
                    caught = e;
                }
            }
            pw.popDown();
            if(caught != null) {
                errorDialog(caught, "Load savestate failed", JPCApplication.this, "Dismiss");
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

                ZipEntry entry = zip2.getEntry("constructors.manifest");
                if(entry == null)
                    throw new IOException("Not a savestate file.");
                DataInput manifest = new DataInputStream(zip2.getInputStream(entry));
                if(!org.jpc.support.SRLoader.checkConstructorManifest(manifest))
                    throw new IOException("Wrong savestate version");

                entry = zip2.getEntry("savestate.SR");
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
            int returnVal = snapshotFileChooser.showDialog(JPCApplication.this, "Save JPC-RR Snapshot");
            choosen = snapshotFileChooser.getSelectedFile();

            if (returnVal != 0)
                choosen = null; 
            pw.popUp();
        }

        protected void runFinish()
        {
            pw.popDown();
            if(caught != null) {
                errorDialog(caught, "Saving savestate failed", JPCApplication.this, "Dismiss");
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
                ZipEntry entry = new ZipEntry("savestate.SR");
                zip2.putNextEntry(entry);
                DataOutput zip = new DataOutputStream(zip2);
                org.jpc.support.SRDumper dumper = new org.jpc.support.SRDumper(zip);
                dumper.dumpObject(pc);
                zip2.closeEntry();

                entry = new ZipEntry("constructors.manifest");
                zip2.putNextEntry(entry);
                zip = new DataOutputStream(zip2);
                dumper.writeConstructorManifest(zip);
                zip2.closeEntry();

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
            int returnVal = snapshotFileChooser.showDialog(JPCApplication.this, "Save Status dump");
            choosen = snapshotFileChooser.getSelectedFile();

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


    public void actionPerformed(ActionEvent evt)
    {
        if (evt.getSource() == stopVRetraceStart)
            pc.getTraceTrap().setTrapFlag(TraceTrap.TRACE_STOP_VRETRACE_START, stopVRetraceStart.isSelected());
        else if (evt.getSource() == stopVRetraceEnd)
            pc.getTraceTrap().setTrapFlag(TraceTrap.TRACE_STOP_VRETRACE_END, stopVRetraceEnd.isSelected());
        else if (evt.getSource() == changeFloppyA)
            changeFloppy(0);
        else if (evt.getSource() == changeFloppyB)
            changeFloppy(1);
        else if (evt.getSource() == changeCDROM)
            changeCDROM();
        for(int i = 0; i < timedStops.length; i++) {
            if(evt.getSource() == timedStops[i]) {
                this.imminentTrapTime = stopTime[i];
                for(int j = 0; j < timedStops.length; j++)
                    timedStops[j].setSelected(false);
                timedStops[i].setSelected(true);
            }
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

    private static BlockDevice createHardDiskBlockDevice(String spec) throws IOException
    {
        if(spec == null)
            return null;
        DiskImage image = new DiskImage(spec, false);
        GenericBlockDevice newDevice = new GenericBlockDevice(image, BlockDevice.Type.HARDDRIVE);
        return newDevice;
    }

    private static BlockDevice createCdRomBlockDevice(String spec) throws IOException
    {
        DiskImage image = new DiskImage(spec, false);
        GenericBlockDevice newDevice = new GenericBlockDevice(image, BlockDevice.Type.CDROM);
        return newDevice;
    }

    public static void main(String[] args) throws Exception
    {
        try
        {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e)
        {
            LOGGING.log(Level.INFO, "System Look-and-Feel not loaded", e);
        }

        if (args.length == 0)
        {
            ClassLoader cl = JPCApplication.class.getClassLoader();
            if (cl instanceof URLClassLoader)
            {
                for (URL url : ((URLClassLoader) cl).getURLs())
                {
                    InputStream in = url.openStream();
                    try
                    {
                        JarInputStream jar = new JarInputStream(in);
                        Manifest manifest = jar.getManifest();
                        if (manifest == null)
                        {
                            continue;
                        }
                        String defaultArgs = manifest.getMainAttributes().getValue("Default-Args");
                        if (defaultArgs == null)
                        {
                            continue;
                        }
                        args = defaultArgs.split("\\s");
                        break;
                    }
                    catch (IOException e)
                    {
                        System.err.println("Not a JAR file " + url);
                    }
                    finally
                    {
                        try
                        {
                            in.close();
                        } catch (IOException e) {}
                    }
                }
            }

            if (args.length == 0)
            {
                LOGGING.log(Level.INFO, "No configuration specified, using defaults");
                args = DEFAULT_ARGS;
            }
            else
            {
                LOGGING.log(Level.INFO, "Using configuration specified in manifest");
            }
        }
        else
        {
            LOGGING.log(Level.INFO, "Using configuration specified on command line");
        }

        if (ArgProcessor.findVariable(args, "compile", "yes").equalsIgnoreCase("no"))
            PC.compile = false;

        String library = ArgProcessor.findVariable(args, "library", null);
        ImageLibrary _library;
        DiskImage.setLibrary(_library = new ImageLibrary(library));
        final JPCApplication app = new JPCApplication(args);
        VirtualKeyboard vKeyboard = new VirtualKeyboard();
        app.vKeyboard = vKeyboard;

        String pngDump = ArgProcessor.findVariable(args, "dumpvideo", null);
        if(pngDump != null) {
            app.setPNGSave(new PNGSaver(pngDump));
        }

        app.setBounds(100, 100, MONITOR_WIDTH + 20, MONITOR_HEIGHT + 70);
        try
        {
            app.setIconImage(Toolkit.getDefaultToolkit().getImage(ClassLoader.getSystemResource("resources/icon.png")));
        } catch (Exception e) {}

        app.validate();
        app.setVisible(true);
    }
}
