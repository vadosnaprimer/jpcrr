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
import java.io.*;
import java.util.*;
import java.util.zip.*;
import java.security.AccessControlException;
import javax.swing.*;

import org.jpc.emulator.HardwareComponent;
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
import org.jpc.diskimages.DiskImageSet;
import org.jpc.diskimages.GenericBlockDevice;
import org.jpc.diskimages.ImageLibrary;
import org.jpc.diskimages.DiskImage;
import org.jpc.pluginsaux.PleaseWait;
import org.jpc.pluginsaux.AsyncGUITask;
import org.jpc.pluginsaux.NewDiskDialog;
import org.jpc.pluginsaux.PCConfigDialog;
import org.jpc.pluginsaux.MenuManager;
import org.jpc.pluginsbase.*;
import org.jpc.jrsr.*;
import org.jpc.ArgProcessor;

import static org.jpc.Misc.randomHexes;
import static org.jpc.Misc.errorDialog;
import static org.jpc.Misc.callShowOptionDialog;

public class PCControl extends JFrame implements Plugin, ExternalCommandInterface
{
    private static final long serialVersionUID = 8;
    private Plugins vPluginManager;

    private JFileChooser snapshotFileChooser;
    private ImageLibrary imgLibrary;

    private Set<String> disks;

    protected PC pc;

    private int trapFlags;

    private volatile boolean running;
    private volatile boolean waiting;
    private boolean willCleanup;
    private static final long[] stopTime;
    private static final String[] stopLabel;
    private volatile long imminentTrapTime;
    private boolean shuttingDown;
    private PCConfigDialog configDialog;
    private MenuManager menuManager;

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
        menuManager.disable("Snapshot→Save→Snapshot");
        menuManager.disable("Snapshot→Save→Movie");
        menuManager.disable("Snapshot→Save→Status Dump");
        menuManager.disable("Snapshot→Load→Snapshot");
        menuManager.disable("Snapshot→Load→Snapshot (preserve events)");
        menuManager.disable("Snapshot→Truncate Event Stream");
        menuManager.disable("Snapshot→RAM Dump→Hexadecimal");
        menuManager.disable("Snapshot→RAM Dump→Binary");
        menuManager.enable("File→Stop");
        menuManager.disable("File→Assemble");
        menuManager.disable("File→Start");
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
        menuManager.enable("File→Assemble");
        menuManager.enable("File→Start");
        menuManager.disable("File→Stop");
        menuManager.enable("File→Reset");
        menuManager.enable("Snapshot→Save→Snapshot");
        menuManager.enable("Snapshot→Save→Movie");
        menuManager.enable("Snapshot→Save→Status Dump");
        menuManager.enable("Snapshot→Load→Snapshot");
        menuManager.setEnabled("Snapshot→Load→Snapshot (preserve events)",
            currentProject != null && currentProject.events != null);
        menuManager.setEnabled("Snapshot→Truncate Event Stream",
            currentProject != null && currentProject.events != null);
        menuManager.enable("Snapshot→RAM Dump→Hexadecimal");
        menuManager.enable("Snapshot→RAM Dump→Binary");
        menuManager.enable("Drives→Add image");
        try {
            updateDisks();
        } catch(Exception e) {
            errorDialog(e, "Failed to update disk menus", null, "Dismiss");
        }

        pc.getTraceTrap().clearTrapTime();
        pc.getTraceTrap().getAndClearTrapActive();
    }

    private String diskNameByIdx(int idx)
    {
        return pc.getDisks().lookupDisk(idx).getName();
    }

    private void updateDisks() throws Exception
    {
        for(String x : disks)
            menuManager.removeMenuItem(x);

        disks.clear();

        menuManager.enable("Drives→fda→<Empty>");
        menuManager.enable("Drives→fdb→<Empty>");
        menuManager.setEnabled("Drives→CD-ROM→<Empty>", pc.getCDROMIndex() >= 0);

        DiskImageSet imageSet = pc.getDisks();
        int[] floppies = imageSet.diskIndicesByType(BlockDevice.Type.FLOPPY);
        int[] cdroms = imageSet.diskIndicesByType(BlockDevice.Type.CDROM);

        for(int i = 0; i < floppies.length; i++) {
            String name = diskNameByIdx(floppies[i]);
            menuManager.addMenuItem("Drives→fda→" + name, this, "menuChangeDisk", new Object[]{new Integer(0),
                new Integer(floppies[i])}, true);
            menuManager.addMenuItem("Drives→fdb→" + name, this, "menuChangeDisk", new Object[]{new Integer(1),
                 new Integer(floppies[i])}, true);
            menuManager.addSelectableMenuItem("Drives→Write Protect→" + name, this, "menuWriteProtect",
                 new Object[]{new Integer(floppies[i])}, true, imageSet.lookupDisk(floppies[i]).isReadOnly());
            disks.add("Drives→fda→" + name);
            disks.add("Drives→fdb→" + name);
            disks.add("Drives→Write Protect→" + name);
        }

        for(int i = 0; i < cdroms.length; i++) {
            String name = diskNameByIdx(cdroms[i]);
            menuManager.addMenuItem("Drives→CD-ROM→" + name, this, "menuChangeDisk", new Object[]{new Integer(1),
                 new Integer(cdroms[i])}, pc.getCDROMIndex() >= 0);
            disks.add("Drives→CD-ROM→" + name);
        }
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

    private void startExternal()
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

    private void stopExternal()
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
        } else if("pc-start".equals(cmd) && args == null && !running) {
            startExternal();
            return true;
        } else if("pc-stop".equals(cmd) && args == null) {
            stopExternal();
            return true;
        } else if("sendevent".equals(cmd) && currentProject.events != null && args != null) {
            String[] rargs = null;
            if(args.length > 1) {
                rargs = new String[args.length - 1];
                System.arraycopy(args, 1, rargs, 0, args.length - 1);
            }
            try {
                Class <? extends HardwareComponent> x = Class.forName(args[0]).asSubclass(HardwareComponent.class);
                currentProject.events.addEvent(0L, x, rargs);
            } catch(Exception e) {
                System.err.println("Error adding event: " + e.getMessage());
            }
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
        configDialog = new PCConfigDialog();

        menuManager = new MenuManager();

        menuManager.addMenuItem("File→Assemble", this, "menuAssemble", null, true);
        menuManager.addMenuItem("File→Start", this, "menuStart", null, false);
        menuManager.addMenuItem("File→Stop", this, "menuStop", null, false);
        menuManager.addMenuItem("File→Reset", this, "menuReset", null, false);
        menuManager.addMenuItem("File→Quit", this, "menuQuit", null, true);
        menuManager.addSelectableMenuItem("Breakpoints→Trap VRetrace Start", this, "menuVRetraceStart", null, true,
            false);
        menuManager.addSelectableMenuItem("Breakpoints→Trap VRetrace End", this, "menuVRetraceEnd", null, true,
            false);
        menuManager.addMenuItem("Snapshot→Save→Snapshot", this, "menuSave", new Object[]{new Boolean(false)}, false);
        menuManager.addMenuItem("Snapshot→Save→Movie", this, "menuSave", new Object[]{new Boolean(true)}, false);
        menuManager.addMenuItem("Snapshot→Save→Status Dump", this, "menuStatusDump", null, false);
        menuManager.addMenuItem("Snapshot→Load→Snapshot", this, "menuLoad", new Object[]{new Boolean(false)}, true);
        menuManager.addMenuItem("Snapshot→Load→Snapshot (preserve events)", this, "menuLoad",
            new Object[]{new Boolean(true)}, false);
        menuManager.addMenuItem("Snapshot→RAM Dump→Hexadecimal", this, "menuRAMDump", new Object[]{new Boolean(false)},
            false);
        menuManager.addMenuItem("Snapshot→RAM Dump→Binary", this, "menuRAMDump", new Object[]{new Boolean(true)},
            false);
        menuManager.addMenuItem("Snapshot→Truncate Event Stream", this, "menuTruncate", null, false);

        for(int i = 0; i < stopLabel.length; i++) {
            menuManager.addSelectableMenuItem("Breakpoints→Timed Stops→" + stopLabel[i], this, "menuTimedStop",
                null, true, (i == 0));
        }
        imminentTrapTime = -1;

        menuManager.addMenuItem("Drives→fda→<Empty>", this, "menuChangeDisk", new Object[]{new Integer(0),
            new Integer(-1)}, false);
        menuManager.addMenuItem("Drives→fdb→<Empty>", this, "menuChangeDisk", new Object[]{new Integer(1),
            new Integer(-1)}, false);
        menuManager.addMenuItem("Drives→CD-ROM→<Empty>", this, "menuChangeDisk", new Object[]{new Integer(2),
            new Integer(-1)}, false);
        menuManager.addMenuItem("Drives→Add image", this, "menuAddDisk", null, false);


        disks = new HashSet<String>();
        currentProject = new PC.PCFullStatus();
        this.pc = null;
        this.vPluginManager = manager;

        setJMenuBar(menuManager.getMainBar());

        try
        {
            setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        }
        catch (AccessControlException e)
        {
            System.err.println("Error: Not able to add some components to frame: " + e.getMessage());
        }

        snapshotFileChooser = new JFileChooser(System.getProperty("user.dir"));

        getContentPane().validate();
        setBounds(150, 150, 720, 50);
        validate();
        setVisible(true);
    }

    public void menuAssemble(String i, Object[] args)
    {
        (new Thread(new AssembleTask())).start();
    }

    public void menuStart(String i, Object[] args)
    {
        start();
    }

    public void menuStop(String i, Object[] args)
    {
        stop();
    }

    public void menuReset(String i, Object[] args)
    {
        reset();
    }

    public void menuQuit(String i, Object[] args)
    {
        vPluginManager.shutdownEmulator();
    }

    public void menuVRetraceStart(String i, Object[] args)
    {
        trapFlags ^= TraceTrap.TRACE_STOP_VRETRACE_START;
    }

    public void menuVRetraceEnd(String i, Object[] args)
    {
        trapFlags ^= TraceTrap.TRACE_STOP_VRETRACE_END;
    }

    public void menuTimedStop(String i, Object[] args)
    {
        for(int j = 0; j < stopLabel.length; j++) {
            String label = "Breakpoints→Timed Stops→" + stopLabel[j];
            if(i.equals(label)) {
                this.imminentTrapTime = stopTime[j];
                menuManager.select(label);
            } else
                menuManager.unselect(label);
        }
    }

    public void menuSave(String i, Object[] args)
    {
        (new Thread(new SaveStateTask(((Boolean)args[0]).booleanValue()))).start();
    }

    public void menuStatusDump(String i, Object[] args)
    {
        (new Thread(new StatusDumpTask())).start();
    }

    public void menuLoad(String i, Object[] args)
    {
        (new Thread(new LoadStateTask(((Boolean)args[0]).booleanValue()))).start();
    }

    public void menuRAMDump(String i, Object[] args)
    {
        (new Thread(new RAMDumpTask(((Boolean)args[0]).booleanValue()))).start();
    }

    public void menuTruncate(String i, Object[] args)
    {
        currentProject.events.truncateEventStream();
    }

    public void menuChangeDisk(String i, Object[] args)
    {
        changeFloppy(((Integer)args[0]).intValue(), ((Integer)args[1]).intValue());
    }

    public void menuWriteProtect(String i, Object[] args)
    {
        int disk = ((Integer)args[0]).intValue();
        writeProtect(disk, menuManager.isSelected(i));
        DiskImageSet imageSet = pc.getDisks();
        menuManager.setSelected(i, imageSet.lookupDisk(disk).isReadOnly());
    }

    public void menuAddDisk(String i, Object[] args)
    {
        (new Thread(new AddDiskTask())).start();
    }

    public void setSize(Dimension d)
    {
        super.setSize(new Dimension(720, 400));
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
        return null;
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
            PC.DiskChanger changer = (PC.DiskChanger)pc.getComponent(PC.DiskChanger.class);
            changer.changeFloppyDisk(drive, image);
        } catch (Exception e) {
            System.err.println("Error: Failed to change disk");
            errorDialog(e, "Failed to change disk", null, "Dismiss");
        }
    }

    private void writeProtect(int image, boolean state)
    {
        try
        {
            PC.DiskChanger changer = (PC.DiskChanger)pc.getComponent(PC.DiskChanger.class);
            changer.wpFloppyDisk(image, state);
        } catch (Exception e) {
            System.err.println("Error: Failed to change floppy write protect");
            errorDialog(e, "Failed to write (un)protect floppy", null, "Dismiss");
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
        boolean canceled;

        public AssembleTask()
        {
            pw = new PleaseWait("Assembling PC...");
            canceled = false;
        }

        protected void runPrepare()
        {
            PCControl.this.setEnabled(false);
            configDialog.popUp();
        }

        protected void runFinish()
        {
            if(caught == null && !canceled) {
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
            if(!canceled)
                pw.popDown();
            if(caught != null) {
                errorDialog(caught, "PC Assembly failed", PCControl.this, "Dismiss");
            }
            PCControl.this.setEnabled(true);
        }

        protected void runTask()
        {
            PC.PCHardwareInfo hw = configDialog.waitClose();
            if(hw == null) {
                canceled = true;
                return;
            }
            try {
                SwingUtilities.invokeAndWait(new Thread() { public void run() {  pw.popUp(); }});
            } catch(Exception e) {
            }

            try {
                pc = PC.createPC(hw);
            } catch(Exception e) {
                 caught = e;
            }
        }
    }

    private class AddDiskTask extends AsyncGUITask
    {
        Exception caught;
        boolean canceled;
        NewDiskDialog dd;

        public AddDiskTask()
        {
            canceled = false;
            dd = new NewDiskDialog();
            PCControl.this.setEnabled(false);
        }

        protected void runPrepare()
        {
        }

        protected void runFinish()
        {
            if(caught != null) {
                errorDialog(caught, "Adding disk failed", PCControl.this, "Dismiss");
            }
            PCControl.this.setEnabled(true);
            try {
                updateDisks();
            } catch(Exception e) {
                errorDialog(e, "Failed to update disk menus", null, "Dismiss");
            }
        }

        protected void runTask()
        {
            NewDiskDialog.Response res = dd.waitClose();
            if(res == null) {
                canceled = true;
                return;
            }
            try {
                DiskImage img;
                pc.getDisks().addDisk(img = new DiskImage(res.diskFile, false));
                img.setName(res.diskName);
            } catch(Exception e) {
                caught = e;
            }
        }
    }
}
