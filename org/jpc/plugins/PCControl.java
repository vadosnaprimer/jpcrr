/*
    JPC-RR: A x86 PC Hardware Emulator
    Release 1

    Copyright (C) 2007-2009 Isis Innovation Limited
    Copyright (C) 2009 H. Ilari Liusvaara

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

package org.jpc.plugins;

import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.*;
import java.util.zip.*;
import java.security.AccessControlException;
import javax.swing.*;

import org.jpc.emulator.PC;
import org.jpc.emulator.EventRecorder;
import org.jpc.emulator.TraceTrap;
import org.jpc.emulator.peripheral.FloppyController;
import org.jpc.emulator.memory.PhysicalAddressSpace;
import org.jpc.emulator.SRLoader;
import org.jpc.emulator.SRDumper;
import org.jpc.emulator.StatusDumper;
import org.jpc.emulator.Clock;
import org.jpc.emulator.DriveSet;
import org.jpc.diskimages.BlockDevice;
import org.jpc.diskimages.GenericBlockDevice;
import org.jpc.diskimages.ImageLibrary;
import org.jpc.diskimages.DiskImage;
import org.jpc.pluginsaux.PleaseWait;
import org.jpc.pluginsaux.AsyncGUITask;
import org.jpc.pluginsaux.DiskImageChooser;
import org.jpc.pluginsbase.*;
import org.jpc.jrsr.*;

import static org.jpc.Misc.randomHexes;
import static org.jpc.Misc.errorDialog;
import static org.jpc.Misc.callShowOptionDialog;

public class PCControl extends JFrame implements ActionListener, RunnerPlugin, ExternalCommandInterface
{
    private static final long serialVersionUID = 8;
    private Plugins vPluginManager;
    private JCheckBoxMenuItem stopVRetraceStart, stopVRetraceEnd;

    private JFileChooser snapshotFileChooser;
    private JMenuItem loadSnapshot;
    private JMenuItem loadSnapshotP;
    private JMenuItem saveSnapshot;
    private JMenuItem truncateEvents;
    private JMenuItem saveMovie;
    private JMenuItem saveStatus;
    private ImageLibrary imgLibrary;
    private JMenuItem changeFloppyA, changeFloppyB, changeCDROM;
    private JMenuItem saveRAMHex;
    private JMenuItem saveRAMBin;

    protected String[] arguments;

    protected PC pc;

    private int trapFlags;

    private JScrollPane monitorPane;
    private JMenuItem mAssemble, mStart, mStop, mReset;

    private volatile boolean running;
    private volatile boolean waiting;
    private boolean willCleanup;
    private static final long[] stopTime;
    private static final String[] stopLabel;
    private JMenuItem[] timedStops;
    private volatile long imminentTrapTime;
    private boolean shuttingDown;

    private PC.PCFullStatus currentProject;

    static
    {
        stopTime = new long[] {-1, 0, 1000, 2000, 5000, 10000, 20000, 50000, 100000, 200000, 500000, 1000000, 2000000,
            5000000, 10000000, 20000000, 50000000, 100000000, 200000000, 500000000, 1000000000, 2000000000,
            5000000000L, 10000000000L, 20000000000L, 50000000000L};
        stopLabel = new String[] {"(unbounded)", "(singlestep)", "1µs", "2µs", "5µs", "10µs", "20µs", "50µs", "100µs",
            "200µs", "500µs","1ms", "2ms", "5ms", "10ms", "20ms", "50ms", "100ms", "200ms", "500ms", "1s", "2s", "5s",
            "10s", "20s", "50s"};
    }

    public boolean systemShutdown()
    {
        if(!running || pc == null)
            return true;
        //We are running. Do the absolute minimum since we are running in very delicate context.
        shuttingDown = true;
        stop();
        return true;
    }

    public void reconnect(PC pc)
    {
        pcStopping();  //Do the equivalent effects.
    }


    private void setTrapFlags()
    {
        pc.getTraceTrap().setTrapFlags(trapFlags);
    }

    public void pcStarting()
    {
        saveSnapshot.setEnabled(false);
        truncateEvents.setEnabled(false);
        saveMovie.setEnabled(false);
        loadSnapshot.setEnabled(false);
        loadSnapshotP.setEnabled(false);
        saveRAMHex.setEnabled(false);
        saveRAMBin.setEnabled(false);
        mStop.setEnabled(true);
        mAssemble.setEnabled(false);
        mStart.setEnabled(false);
        if (running)
            return;

        setTrapFlags();

        Clock sysClock = (Clock)pc.getComponent(Clock.class);
        long current = sysClock.getTime();
        if(imminentTrapTime > 0) {
            pc.getTraceTrap().setTrapTime(current + imminentTrapTime);
        } else if(imminentTrapTime == 0) {
            //Hack: We set trace trap to trap immediately. It comes too late to abort next instruction, but
            //early enough to abort one after that.
            pc.getTraceTrap().setTrapTime(current);
        }
        if(currentProject.events != null)
            currentProject.events.setPCRunStatus(true);
    }

    public void pcStopping()
    {
        if(currentProject.events != null)
            currentProject.events.setPCRunStatus(false);
        if(shuttingDown)
            return;   //Don't mess with UI when shutting down.
        loadSnapshot.setEnabled(true);
        loadSnapshotP.setEnabled((currentProject.events != null));
        mAssemble.setEnabled(true);
        mStart.setEnabled(true);
        mStop.setEnabled(false);
        mReset.setEnabled(true);
        saveStatus.setEnabled(true);
        saveSnapshot.setEnabled(true);
        truncateEvents.setEnabled((currentProject.events != null));
        saveMovie.setEnabled(true);
        saveRAMHex.setEnabled(true);
        saveRAMBin.setEnabled(true);
        changeFloppyA.setEnabled(true);
        changeFloppyB.setEnabled(true);
        if(pc.getCDROMIndex() < 0)
            changeCDROM.setEnabled(false);
        else
            changeCDROM.setEnabled(true);
        pc.getTraceTrap().clearTrapTime();
        pc.getTraceTrap().getAndClearTrapActive();
    }

    public void main()
    {
        boolean wasRunning = false;
        while(true) {   //We will be killed by JVM.
            //Wait for us to become runnable again.
            while(!running || pc == null) {
                if(wasRunning && pc != null)
                    pc.stop();
                wasRunning = running;
                try {
                    synchronized(this) {
                        waiting = true;
                        wait();
                        waiting = false;
                    }
                } catch(Exception e) {
                }
            }
            //
            if(!wasRunning)
                pc.start();
            wasRunning = running;

            try {
                pc.execute();
                if(pc.getHitTraceTrap()) {
                    if(pc.getAndClearTripleFaulted())
                        callShowOptionDialog(this, "CPU shut itself down due to triple fault. Rebooting the system.", "Triple fault!", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE, null, new String[]{"Dismiss"}, "Dismiss");
                    if(!willCleanup)
                        SwingUtilities.invokeAndWait(new Thread() { public void run() { stopNoWait(); }});
                    running = false;
                }
            } catch (Exception e) {
                errorDialog(e, "Hardware emulator internal error", this, "Dismiss");
                try {
                    SwingUtilities.invokeAndWait(new Thread() { public void run() { stopNoWait(); }});
                } catch (Exception f) {
                }
            }
        }
    }


    public void connectPC(PC pc)
    {
        currentProject.pc = pc;
        vPluginManager.reconnect(pc);
        this.pc = pc;
    }

    public void notifyArguments(String[] args)
    {
        this.arguments = args;
    }

    public void startExternal()
    {
        if(pc != null && !running)
            if(!SwingUtilities.isEventDispatchThread())
                try {
                    SwingUtilities.invokeAndWait(new Thread() { public void run() { PCControl.this.start(); }});
                } catch(Exception e) {
                }
            else
                start();
    }

    public void stopExternal()
    {
        if(pc != null && running)
            if(!SwingUtilities.isEventDispatchThread())
                try {
                    SwingUtilities.invokeAndWait(new Thread() { public void run() { PCControl.this.stop(); }});
                } catch(Exception e) {
                }
            else
                stop();
    }

    public boolean invokeCommand(String cmd, String[] args)
    {
        if("state-save".equals(cmd) && args.length == 1 && !running) {
            (new Thread(new SaveStateTask(args[0], false))).start();
            return true;
        } else if("movie-save".equals(cmd) && args.length == 1 && !running) {
            (new Thread(new SaveStateTask(args[0], true))).start();
            return true;
        } else if("state-load".equals(cmd) && args.length == 1 && !running) {
            (new Thread(new LoadStateTask(args[0], false))).start();
            return true;
        } else if("state-load-noevents".equals(cmd) && args.length == 1 && !running) {
            (new Thread(new LoadStateTask(args[0], true))).start();
            return true;
        } else if("pc-assemble".equals(cmd) && args == null && !running) {
            (new Thread(new AssembleTask())).start();
            return true;
        } else if("ram-dump-text".equals(cmd) && args.length == 1 && !running) {
            (new Thread(new RAMDumpTask(args[0], false))).start();
            return true;
        } else if("ram-dump-binary".equals(cmd) && args.length == 1 && !running) {
            (new Thread(new RAMDumpTask(args[0], true))).start();
            return true;
        } else if("trap-vretrace-start-on".equals(cmd) && args == null) {
            trapFlags |= TraceTrap.TRACE_STOP_VRETRACE_START;
            return true;
        } else if("trap-vretrace-start-off".equals(cmd) && args == null) {
            trapFlags &= ~TraceTrap.TRACE_STOP_VRETRACE_START;
            return true;
        } else if("trap-vretrace-end-on".equals(cmd) && args == null) {
            trapFlags |= TraceTrap.TRACE_STOP_VRETRACE_END;
            return true;
        } else if("trap-vretrace-end-off".equals(cmd) && args == null) {
            trapFlags &= ~TraceTrap.TRACE_STOP_VRETRACE_END;
            return true;
        } else if("trap-timed-disable".equals(cmd) && args == null) {
            this.imminentTrapTime = -1;
            return true;
        } else if("trap-timed".equals(cmd) && args.length == 1) {
            try {
                this.imminentTrapTime = Long.parseLong(args[0]);
            } catch(Exception e) { return false; }
            return true;
        }
        return false;
    }

    public PCControl(Plugins manager) throws Exception
    {
        super("JPC-RR");
        running = false;
        this.willCleanup = false;
        shuttingDown = false;

        currentProject = new PC.PCFullStatus();

        this.pc = null;
        this.vPluginManager = manager;

        JMenuBar bar = new JMenuBar();

        JMenu file = new JMenu("File");
        (mAssemble = file.add("Assemble")).addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e)
                {
                    (new Thread(PCControl.this.new AssembleTask())).start();
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

        for(int i = 0; i < timedStops.length; i++) {
            timedStops[i] = new JCheckBoxMenuItem(stopLabel[i]);
            timedStops[i].addActionListener(this);
            timedStops[i].setSelected(false);
            timed.add(timedStops[i]);
        }
        timedStops[0].setSelected(true);
        imminentTrapTime = -1;

        bar.add(breakpoints);

        JMenu snap = new JMenu("Snapshot");
        JMenu snapSave = new JMenu("Save");
        (saveSnapshot = snapSave.add("Snapshot")).addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent ev)
            {
                (new Thread(PCControl.this.new SaveStateTask(false))).start();
            }
        });
        (saveMovie = snapSave.add("Movie")).addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent ev)
            {
                (new Thread(PCControl.this.new SaveStateTask(true))).start();
            }
        });
        (saveStatus = snapSave.add("Status Dump")).addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent ev)
            {
                (new Thread(PCControl.this.new StatusDumpTask())).start();
            }
        });
        snap.add(snapSave);

        JMenu snapLoad = new JMenu("Load");
        (loadSnapshot = snapLoad.add("Load Snapshot")).addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent ev)
            {
                (new Thread(PCControl.this.new LoadStateTask(false))).start();
            }
        });
        (loadSnapshotP = snapLoad.add("Load Snapshot (preserve events)")).addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent ev)
            {
                (new Thread(PCControl.this.new LoadStateTask(true))).start();
            }
        });
        snap.add(snapLoad);

        JMenu snapRAMDump = new JMenu("RAM dump");
        (saveRAMBin = snapRAMDump.add("Binary")).addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e)
                {
                    (new Thread(PCControl.this.new RAMDumpTask(true))).start();
                }
            });
        (saveRAMHex = snapRAMDump.add("Hexadecimal")).addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e)
                {
                    (new Thread(PCControl.this.new RAMDumpTask(false))).start();
                }
            });
        snap.add(snapRAMDump);

        (truncateEvents = snap.add("Truncate Event Stream")).addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent ev)
            {
                currentProject.events.truncateEventStream();
            }
        });


        bar.add(snap);

        loadSnapshotP.setEnabled(false);
        saveSnapshot.setEnabled(false);
        truncateEvents.setEnabled(false);
        saveMovie.setEnabled(false);
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

        getContentPane().validate();
        setBounds(150, 150, 720, 50);
        validate();
        setVisible(true);
    }

    public void setSize(Dimension d)
    {
        super.setSize(new Dimension(720, 400));
        getMonitorPane().setPreferredSize(new Dimension(720, 400));
    }

    public synchronized void start()
    {
        vPluginManager.pcStarted();
        running = true;
        notifyAll();
    }

    protected synchronized void stopNoWait()
    {
        running = false;
        vPluginManager.pcStopped();
        Clock sysClock = (Clock)pc.getComponent(Clock.class);
        System.err.println("Notice: PC emulation stopped (at time sequence value " + sysClock.getTime() + ")");
    }

    public void stop()
    {
        willCleanup = true;
        pc.getTraceTrap().doPotentialTrap(TraceTrap.TRACE_STOP_IMMEDIATE);
        running = false;
        while(!waiting)
            ;
        willCleanup = false;
        stopNoWait();
    }

    public JScrollPane getMonitorPane()
    {
        return monitorPane;
    }

    protected void reset()
    {
        pc.reboot();
    }

    public synchronized boolean isRunning()
    {
        return running;
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
            System.err.println("Error: Failed to change floppy disk");
            errorDialog(e, "Failed to change floppy", null, "Dismiss");
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
            System.err.println("Error: Failed to change CD-ROM");
            errorDialog(e, "Failed to change CD-ROM", null, "Dismiss");
        }

    }

    private class LoadStateTask extends AsyncGUITask
    {
        File choosen;
        Exception caught;
        PleaseWait pw;
        boolean preserve;
        long oTime;

        public LoadStateTask(boolean eventLock)
        {
            oTime = System.currentTimeMillis();
            choosen = null;
            preserve = eventLock;
            pw = new PleaseWait("Loading savestate...");
        }

        public LoadStateTask(String name, boolean eventLock)
        {
            this(eventLock);
            choosen = new File(name);
        }

        protected void runPrepare()
        {
            PCControl.this.setEnabled(false);
            if(choosen == null) {
                int returnVal = snapshotFileChooser.showDialog(PCControl.this, preserve ? "Load JPC-RR Snapshot (PE)" :
                    "Load JPC-RR Snapshot");
                choosen = snapshotFileChooser.getSelectedFile();

                if (returnVal != 0)
                    choosen = null;
            }
            pw.popUp();
        }

        protected void runFinish()
        {
            if(caught == null) {
                try {
                    connectPC(pc = currentProject.pc);
                    System.err.println("Informational: Loadstate done");
                } catch(Exception e) {
                    caught = e;
                }
            }
            pw.popDown();
            if(caught != null) {
                errorDialog(caught, "Load savestate failed", PCControl.this, "Dismiss");
            }
            PCControl.this.setEnabled(true);
            System.err.println("Total save time: " + (System.currentTimeMillis() - oTime) + "ms.");
        }

        protected void runTask()
        {
            if(choosen == null)
                return;

            try {
                System.err.println("Informational: Loading a snapshot of JPC-RR");
                long times1 = System.currentTimeMillis();
                JRSRArchiveReader reader = new JRSRArchiveReader(choosen.getAbsolutePath());

                PC.PCFullStatus fullStatus = PC.loadSavestate(reader, preserve ? currentProject.events : null);
                if(currentProject.projectID != null && fullStatus.projectID.equals(currentProject.projectID))
                    if(currentProject.rerecords > fullStatus.rerecords)
                        fullStatus.rerecords = currentProject.rerecords + 1;
                    else
                        fullStatus.rerecords++;
                else
                    fullStatus.rerecords++;

                currentProject = fullStatus;

                reader.close();
                long times2 = System.currentTimeMillis();
                System.err.println("Informational: Loadstate complete (" + (times2 - times1) + "ms).");
            } catch(Exception e) {
                 caught = e;
            }
        }
    }

    private class SaveStateTask extends AsyncGUITask
    {
        File choosen;
        Exception caught;
        boolean movieOnly;
        PleaseWait pw;
        long oTime;

        public SaveStateTask(boolean movie)
        {
            oTime = System.currentTimeMillis();
            choosen = null;
            movieOnly = movie;
            pw = new PleaseWait("Saving savestate...");
        }

        public SaveStateTask(String name, boolean movie)
        {
            this(movie);
            choosen = new File(name);
        }

        protected void runPrepare()
        {
            PCControl.this.setEnabled(false);
            if(choosen == null) {
                int returnVal = snapshotFileChooser.showDialog(PCControl.this, movieOnly ? "Save JPC-RR Movie" :
                    "Save JPC-RR Snapshot");
                choosen = snapshotFileChooser.getSelectedFile();

                if (returnVal != 0)
                    choosen = null;
            }
            pw.popUp();
        }

        protected void runFinish()
        {
            pw.popDown();
            if(caught != null) {
                errorDialog(caught, "Saving savestate failed", PCControl.this, "Dismiss");
            }
            PCControl.this.setEnabled(true);
            System.err.println("Total save time: " + (System.currentTimeMillis() - oTime) + "ms.");
        }

        protected void runTask()
        {
            if(choosen == null)
                return;

            JRSRArchiveWriter writer = null;

            try {
                System.err.println("Informational: Savestating...");
                long times1 = System.currentTimeMillis();
                writer = new JRSRArchiveWriter(choosen.getAbsolutePath());
                PC.saveSavestate(writer, currentProject, movieOnly);
                writer.close();
                long times2 = System.currentTimeMillis();
                System.err.println("Informational: Savestate complete (" + (times2 - times1) + "ms).");
            } catch(Exception e) {
                 if(writer != null)
                     try { writer.rollback(); } catch(Exception f) {}
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

        public StatusDumpTask(String name)
        {
            this();
            choosen = new File(name);
        }

        protected void runPrepare()
        {
            PCControl.this.setEnabled(false);
            if(choosen == null) {
                int returnVal = snapshotFileChooser.showDialog(PCControl.this, "Save Status dump");
                choosen = snapshotFileChooser.getSelectedFile();

                if (returnVal != 0)
                    choosen = null;
            }
            pw.popUp();
        }

        protected void runFinish()
        {
            pw.popDown();
            if(caught != null) {
                errorDialog(caught, "Status dump failed", PCControl.this, "Dismiss");
            }
            PCControl.this.setEnabled(true);
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

        public RAMDumpTask(String name, boolean binFlag)
        {
            this(binFlag);
            choosen = new File(name);
        }

        protected void runPrepare()
        {
            PCControl.this.setEnabled(false);
            if(choosen == null) {
                int returnVal;
                if(binary)
                    returnVal = snapshotFileChooser.showDialog(PCControl.this, "Save RAM dump");
                else
                    returnVal = snapshotFileChooser.showDialog(PCControl.this, "Save RAM hexdump");
                choosen = snapshotFileChooser.getSelectedFile();

                if (returnVal != 0)
                    choosen = null;
            }
            pw.popUp();
        }

        protected void runFinish()
        {
            pw.popDown();
            if(caught != null) {
                errorDialog(caught, "RAM dump failed", PCControl.this, "Dismiss");
            }
            PCControl.this.setEnabled(true);
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
            PCControl.this.setEnabled(false);
            pw.popUp();
        }

        protected void runFinish()
        {
            if(caught == null) {
                try {
                    currentProject.projectID = randomHexes(24);
                    currentProject.rerecords = 0;
                    currentProject.events = new EventRecorder();
                    currentProject.events.attach(pc, null);
                    currentProject.savestateID = null;
                    currentProject.extraHeaders = null;
                    connectPC(pc);
                } catch(Exception e) {
                    caught = e;
                }
            }
            pw.popDown();
            if(caught != null) {
                errorDialog(caught, "PC Assembly failed", PCControl.this, "Dismiss");
            }
            PCControl.this.setEnabled(true);
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
            trapFlags ^= TraceTrap.TRACE_STOP_VRETRACE_START;
        else if (evt.getSource() == stopVRetraceEnd)
            trapFlags ^= TraceTrap.TRACE_STOP_VRETRACE_END;
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
}
