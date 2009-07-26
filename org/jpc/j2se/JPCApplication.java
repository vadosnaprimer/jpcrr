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
import java.util.zip.*;
import java.security.AccessControlException;
import javax.swing.*;
import java.lang.reflect.*;

import org.jpc.*;
import org.jpc.emulator.PC;
import org.jpc.emulator.TraceTrap;
import org.jpc.emulator.pci.peripheral.VGACard;
import org.jpc.emulator.peripheral.FloppyController;
import org.jpc.emulator.peripheral.Keyboard;
import org.jpc.emulator.memory.PhysicalAddressSpace;
import org.jpc.emulator.SRLoader;
import org.jpc.emulator.SRDumper;
import org.jpc.emulator.StatusDumper;
import org.jpc.emulator.Clock;
import org.jpc.diskimages.BlockDevice;
import org.jpc.diskimages.GenericBlockDevice;
import org.jpc.diskimages.ImageLibrary;
import org.jpc.diskimages.DiskImage;
import org.jpc.support.*;

public class JPCApplication extends JFrame implements ActionListener, Runnable
{
    private static final long serialVersionUID = 8;
    private static final URI JPC_URI = URI.create("http://www-jpc.physics.ox.ac.uk/");
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
    private Plugins vPluginManager;
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
    private JMenuItem saveRAMHex;
    private JMenuItem saveRAMBin;

    protected String[] arguments;

    protected PC pc;

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
        vPluginManager.reconnect(pc);
        this.pc = pc;

        mAssemble.setEnabled(true);
        mStart.setEnabled(true);
        mStop.setEnabled(false);
        mReset.setEnabled(true);
        stopVRetraceStart.setEnabled(true);
        stopVRetraceEnd.setEnabled(true);
        saveStatus.setEnabled(true);
        saveSnapshot.setEnabled(true);
        saveRAMHex.setEnabled(true);
        saveRAMBin.setEnabled(true);
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
        this.willCleanup = false;
        monitorPane = new JScrollPane(LICENCE);
        getContentPane().add("Center", monitorPane);

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
                    vPluginManager.shutdownEmulator();
                }
            });

        bar.add(file);

        mAssemble.setEnabled(true);
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
            System.err.println("Error: Not able to add some components to frame: " + e.getMessage());
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
        (saveRAMBin = snap.add("RAM dump (binary)")).addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e)
                {
                    (new Thread(JPCApplication.this.new RAMDumpTask(true))).start();
                }
            });
        (saveRAMHex = snap.add("RAM dump (hexdump)")).addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e)
                {
                    (new Thread(JPCApplication.this.new RAMDumpTask(false))).start();
                }
            });

        bar.add(snap);

        saveSnapshot.setEnabled(false);
        saveStatus.setEnabled(false);
        saveRAMHex.setEnabled(false);
        saveRAMBin.setEnabled(false);

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
                    "Ok"
                };
                callShowOptionDialog(JPCApplication.this, ABOUT_US, "About JPC-RR", JOptionPane.YES_NO_OPTION, JOptionPane.INFORMATION_MESSAGE, null, buttons, buttons[0]);
            }
        });
        bar.add(help);

        LICENCE.setPreferredSize(new Dimension(720, 400));
        getMonitorPane().setViewportView(LICENCE);
        getContentPane().validate();
    }

    public void setSize(Dimension d)
    {
        super.setSize(new Dimension(720, 400));
        getMonitorPane().setPreferredSize(new Dimension(720, 400));
    }

    public synchronized void start()
    {
        vPluginManager.pcStarted();
        saveSnapshot.setEnabled(false);
        loadSnapshot.setEnabled(false);
        saveRAMHex.setEnabled(false);
        saveRAMBin.setEnabled(false);
        mStop.setEnabled(true);
        mAssemble.setEnabled(false);
        mStart.setEnabled(false);
        mReset.setEnabled(false);
        if (running)
            return;
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
    }

    protected synchronized void stopNoWait()
    {
        running = false;
        runner = null;
        mStop.setEnabled(false);
        mAssemble.setEnabled(true);
        mStart.setEnabled(true);
        mReset.setEnabled(true);
        saveSnapshot.setEnabled(true);
        loadSnapshot.setEnabled(true);
        saveRAMHex.setEnabled(true);
        saveRAMBin.setEnabled(true);
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
        vPluginManager.pcStopped();

        Clock sysClock = (Clock)pc.getComponent(Clock.class);
        System.err.println("Notice: PC emulation stopped (at time sequence value " + sysClock.getTime() + ")");
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

    public synchronized boolean isRunning()
    {
        return running;
    }

    public void run()
    {
        pc.start();
        try
        {
            while (running)
            {
                pc.execute();
                if(pc.getHitTraceTrap()) {
                    if(pc.getAndClearTripleFaulted())
                        callShowOptionDialog(this, "CPU shut itself down due to triple fault. Rebooting the system.", "Triple fault!", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE, null, new String[]{"Dismiss"}, "Dismiss");
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
        }
    }

    private void changeFloppy(int drive, int image)
    {
        try
        {
            DiskImage img = pc.getDisks().lookupDisk(image);
            BlockDevice device = new GenericBlockDevice(img, BlockDevice.Type.FLOPPY);
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
                    System.err.println("Informational: Loadstate done");
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
                System.err.println("Informational: Loading a snapshot of JPC-RR");
                ZipFile zip2 = new ZipFile(choosen);

                ZipEntry entry = zip2.getEntry("constructors.manifest");
                if(entry == null)
                    throw new IOException("Not a savestate file.");
                DataInput manifest = new DataInputStream(zip2.getInputStream(entry));
                if(!SRLoader.checkConstructorManifest(manifest))
                    throw new IOException("Wrong savestate version");

                entry = zip2.getEntry("savestate.SR");
                if(entry == null)
                    throw new IOException("Not a savestate file.");
                DataInput zip = new DataInputStream(zip2.getInputStream(entry));
                SRLoader loader = new SRLoader(zip);
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

                System.err.println("Informational: Savestating...");
                ZipEntry entry = new ZipEntry("savestate.SR");
                zip2.putNextEntry(entry);
                DataOutput zip = new DataOutputStream(zip2);
                SRDumper dumper = new SRDumper(zip);
                dumper.dumpObject(pc);
                zip2.closeEntry();

                entry = new ZipEntry("constructors.manifest");
                zip2.putNextEntry(entry);
                zip = new DataOutputStream(zip2);
                dumper.writeConstructorManifest(zip);
                zip2.closeEntry();

                zip2.close();
                System.err.println("Informational: Savestate complete; " + dumper.dumpedObjects() + " objects dumped.");
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
                OutputStream outb = new BufferedOutputStream(new FileOutputStream(choosen));
                PrintStream out = new PrintStream(outb, false, "UTF-8");
                StatusDumper sd = new StatusDumper(out);
                pc.dumpStatus(sd);
                out.flush();
                outb.flush();
                System.err.println("Informational: Dumped " + sd.dumpedObjects() + " objects");
            } catch(Exception e) {
                 caught = e;
            }
        }
    }

    private class RAMDumpTask extends AsyncGUITask
    {
        File choosen;
        Exception caught;
        PleaseWait pw;
        boolean binary;

        public RAMDumpTask(boolean binFlag)
        {
            choosen = null;
            pw = new PleaseWait("Saving RAM dump...");
            binary = binFlag;
        }

        protected void runPrepare()
        {
            JPCApplication.this.setEnabled(false);
            int returnVal;
            if(binary)
                returnVal = snapshotFileChooser.showDialog(JPCApplication.this, "Save RAM dump");
            else
                returnVal = snapshotFileChooser.showDialog(JPCApplication.this, "Save RAM hexdump");
            choosen = snapshotFileChooser.getSelectedFile();

            if (returnVal != 0)
                choosen = null;
            pw.popUp();
        }

        protected void runFinish()
        {
            pw.popDown();
            if(caught != null) {
                errorDialog(caught, "RAM dump failed", JPCApplication.this, "Dismiss");
            }
            JPCApplication.this.setEnabled(true);
        }

        protected void runTask()
        {
            if(choosen == null)
                return;

            try {
                OutputStream outb = new BufferedOutputStream(new FileOutputStream(choosen));
                byte[] pagebuf = new byte[4096];
                PhysicalAddressSpace addr = (PhysicalAddressSpace)pc.getComponent(PhysicalAddressSpace.class);
                int lowBound = addr.findFirstRAMPage(0);
                int firstUndumped = 0;
                int highBound = 0;
                int present = 0;
                while(lowBound >= 0) {
                    for(; firstUndumped < lowBound; firstUndumped++)
                        dumpPage(outb, firstUndumped, null);
                    addr.readRAMPage(firstUndumped++, pagebuf);
                    dumpPage(outb, lowBound, pagebuf);
                    present++;
                    highBound = lowBound + 1;
                    lowBound = addr.findFirstRAMPage(++lowBound);
                }
                outb.flush();
                System.err.println("Informational: Dumped machine RAM (" + highBound + " pages examined, " + 
                    present + " pages present).");
            } catch(Exception e) {
                 caught = e;
            }
        }

        private byte charForHex(int hvalue)
        {
            if(hvalue < 10)
                return (byte)(hvalue + 48);
            else if(hvalue > 9 && hvalue < 16)
                return (byte)(hvalue + 55);
            else
                System.err.println("Unknown hex value: " + hvalue + ".");
            return 90;
        }

        private void dumpPage(OutputStream stream, int pageNo, byte[] buffer) throws IOException
        {
            int pageBufSize;
            pageNo = pageNo & 0xFFFFF;   //Cut page numbers out of range.
            if(!binary && buffer == null)
                return;  //Don't dump null pages in non-binary mode.
            if(binary)
                pageBufSize = 4096;      //Binary page buffer is 4096 bytes.
            else
                pageBufSize = 14592;     //Hexdump page buffer is 14592 bytes.
            byte[] outputPage = new byte[pageBufSize];
            if(buffer != null && binary) {
                System.arraycopy(buffer, 0, outputPage, 0, 4096);
            } else if(buffer != null) {   //Hex mode
                for(int i = 0; i < 256; i++) {
                    for(int j = 0; j < 57; j++) {
                        if(j < 5)
                            outputPage[57 * i + j] = charForHex((pageNo >>> (4 * (4 - j))) & 0xF);
                        else if(j == 5)
                            outputPage[57 * i + j] = charForHex(i / 16);
                        else if(j == 6)
                            outputPage[57 * i + j] = charForHex(i % 16);
                        else if(j == 7)
                            outputPage[57 * i + j] = 48;
                        else if(j == 56)
                            outputPage[57 * i + j] = 10;
                        else if(j % 3 == 2)
                            outputPage[57 * i + j] = 32;
                        else if(j % 3 == 0)
                            outputPage[57 * i + j] = charForHex(((int)buffer[16 * i + j / 3 - 3] & 0xFF) / 16);
                        else if(j % 3 == 1)
                            outputPage[57 * i + j] = charForHex(buffer[16 * i + j / 3 - 3] & 0xF);
                        else
                            System.err.println("Error: dumpPage: unhandled j = " + j + ".");
                    }
                }
            }
            stream.write(outputPage);
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
                pc = PC.createPC(PC.parseArgs(arguments));
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

    public static int callShowOptionDialog(java.awt.Component parent, Object msg, String title, int oType, 
        int mType, Icon icon, Object[] buttons, Object deflt)
    {
        try {
            return JOptionPane.showOptionDialog(parent, msg, title, oType, mType, icon, buttons, deflt);
        } catch(java.awt.HeadlessException e) {
            //No GUI available.
            System.err.println("MESSAGE: *** " + title + " ***: " + msg.toString());
            for(int i = 0; i < buttons.length; i++)
                if(buttons[i] == deflt)
                    return i;
            return 0;
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
        int i = callShowOptionDialog(null, message, title, JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE, null, new String[]{text, "Save stack trace"}, "Save stack Trace");
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
            if(el.getClassName().startsWith("sun.reflect."))
                continue; //Clean up the trace a bit.
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
            callShowOptionDialog(component, "Stack trace saved to " + traceFileName + ".", "Stack trace saved", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE, null, new String[]{text}, text);
        } catch(Exception e2) {
            callShowOptionDialog(component, e.getMessage(), "Saving stack trace failed", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE, null, new String[]{text}, text);
        }
    }

    public static void main(String[] args) throws Exception
    {
        try
        {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e)
        {
            System.err.println("Warning: System Look-and-Feel not loaded" + e.getMessage());
        }

        String library = ArgProcessor.findVariable(args, "library", null);
        DiskImage.setLibrary(new ImageLibrary(library));
        final JPCApplication app = new JPCApplication(args);
        Plugins pluginManager = new Plugins();
        app.vPluginManager = pluginManager;

        //Load plugins.
        try {
            String plugins = ArgProcessor.findVariable(args, "plugins", null);
            Map<String, String> plugins2 = PC.parseHWModules(plugins);
            for(Map.Entry<String, String> pluginEntry : plugins2.entrySet()) {
                String pluginClass = pluginEntry.getKey();
                String pluginArgs = pluginEntry.getValue();
                Class<?> plugin;

                //Note that pluginArgs may be null!

                if("".equals(pluginArgs))
                    pluginArgs = null;

                try {
                    plugin = Class.forName(pluginClass);
                } catch(Exception e) {
                    throw new IOException("Unable to find plugin \"" + pluginClass + "\".");
                }

                if(!Plugin.class.isAssignableFrom(plugin)) {
                    throw new IOException("Plugin \"" + pluginClass + "\" is not valid plugin.");
                }
                Plugin c;
                try {
                    boolean x = pluginArgs.equals("");  //Intentionally cause NPE if params is null.
                    x = x & x;    //Silence warning.
                    Constructor<?> cc = plugin.getConstructor(Plugins.class, String.class);
                    c = (Plugin)cc.newInstance(pluginManager, pluginArgs);
                } catch(Exception e) {
                      try {
                          Constructor<?> cc = plugin.getConstructor(Plugins.class);
                          c = (Plugin)cc.newInstance(pluginManager);
                      } catch(Exception f) {
                          throw new IOException("Unable to instantiate plugin \"" + pluginClass + "\".");
                      }
                }
                pluginManager.registerPlugin(c);
            }
        } catch(Exception e) {
            errorDialog(e, "Plugin Loading failed", app, "Dismiss");
            return;
        }

        app.setBounds(100, 100, 720, 400);
        try
        {
            app.setIconImage(Toolkit.getDefaultToolkit().getImage(ClassLoader.getSystemResource("resources/icon.png")));
        } catch (Exception e) {}

        app.validate();
        app.setVisible(true);
    }
}
