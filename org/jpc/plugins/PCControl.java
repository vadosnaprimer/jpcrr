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
import org.jpc.pluginsaux.AuthorsDialog;
import org.jpc.pluginsaux.PCConfigDialog;
import org.jpc.pluginsaux.MenuManager;
import org.jpc.pluginsbase.*;
import org.jpc.jrsr.*;
import org.jpc.ArgProcessor;

import static org.jpc.Misc.randomHexes;
import static org.jpc.Misc.errorDialog;
import static org.jpc.Misc.callShowOptionDialog;
import static org.jpc.Misc.moveWindow;

public class PCControl extends JFrame implements Plugin
{
    private static long PROFILE_ALWAYS = 0;
    private static long PROFILE_NO_PC = 1;
    private static long PROFILE_HAVE_PC = 2;
    private static long PROFILE_STOPPED = 4;
    private static long PROFILE_RUNNING = 8;
    private static long PROFILE_EVENTS = 16;
    private static long PROFILE_CDROM = 32;

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
        long profile = PROFILE_HAVE_PC | PROFILE_RUNNING;
        if(currentProject != null && currentProject.events != null);
            profile |= PROFILE_EVENTS;
        if(pc.getCDROMIndex() >= 0)
            profile |= PROFILE_CDROM;

        menuManager.setProfile(profile);

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


        long profile = PROFILE_STOPPED;
        if(pc != null)
            profile |= PROFILE_HAVE_PC;
        else
            profile |= PROFILE_NO_PC;
        if(currentProject != null && currentProject.events != null);
            profile |= PROFILE_EVENTS;
        if(pc.getCDROMIndex() >= 0)
            profile |= PROFILE_CDROM;

        menuManager.setProfile(profile);

        try {
            updateDisks();
        } catch(Exception e) {
            errorDialog(e, "Failed to update disk menus", null, "Dismiss");
        }

        if(pc != null) {
            pc.getTraceTrap().clearTrapTime();
            pc.getTraceTrap().getAndClearTrapActive();
        }
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

        if(pc == null)
            return;

        DiskImageSet imageSet = pc.getDisks();
        int[] floppies = imageSet.diskIndicesByType(BlockDevice.Type.FLOPPY);
        int[] cdroms = imageSet.diskIndicesByType(BlockDevice.Type.CDROM);

        for(int i = 0; i < floppies.length; i++) {
            String name = diskNameByIdx(floppies[i]);
            menuManager.addMenuItem("Drives→fda→" + name, this, "menuChangeDisk", new Object[]{new Integer(0),
                new Integer(floppies[i])}, PROFILE_HAVE_PC);
            menuManager.addMenuItem("Drives→fdb→" + name, this, "menuChangeDisk", new Object[]{new Integer(1),
                 new Integer(floppies[i])}, PROFILE_HAVE_PC);
            menuManager.addSelectableMenuItem("Drives→Write Protect→" + name, this, "menuWriteProtect",
                 new Object[]{new Integer(floppies[i])}, imageSet.lookupDisk(floppies[i]).isReadOnly(),
                 PROFILE_HAVE_PC);
            disks.add("Drives→fda→" + name);
            disks.add("Drives→fdb→" + name);
            disks.add("Drives→Write Protect→" + name);
        }

        for(int i = 0; i < cdroms.length; i++) {
            String name = diskNameByIdx(cdroms[i]);
            menuManager.addMenuItem("Drives→CD-ROM→" + name, this, "menuChangeDisk", new Object[]{new Integer(1),
                 new Integer(cdroms[i])}, PROFILE_HAVE_PC | PROFILE_CDROM);
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

    public boolean eci_state_save(String filename)
    {
        if(!running)
            (new Thread(new SaveStateTask(filename, false))).start();
        return !running;
    }

    public boolean eci_movie_save(String filename)
    {
        if(!running)
            (new Thread(new SaveStateTask(filename, true))).start();
        return !running;
    }

    public boolean eci_state_load(String filename)
    {
        if(!running)
            (new Thread(new LoadStateTask(filename, LoadStateTask.MODE_NORMAL))).start();
        return !running;
    }

    public boolean eci_state_load_noevents(String filename)
    {
        if(!running)
            (new Thread(new LoadStateTask(filename, LoadStateTask.MODE_PRESERVE))).start();
        return !running;
    }

    public boolean eci_movie_load(String filename)
    {
        if(!running)
            (new Thread(new LoadStateTask(filename, LoadStateTask.MODE_MOVIEONLY))).start();
        return !running;
    }

    public boolean eci_pc_assemble()
    {
        if(!running)
            (new Thread(new AssembleTask())).start();
        return !running;
    }

    public boolean eci_ram_dump_text(String filename)
    {
        if(!running)
            (new Thread(new RAMDumpTask(filename, false))).start();
        return !running;
    }

    public boolean eci_ram_dump_binary(String filename)
    {
        if(!running)
            (new Thread(new RAMDumpTask(filename, true))).start();
        return !running;
    }

    public void eci_trap_vretrace_start_on()
    {
        trapFlags |= TraceTrap.TRACE_STOP_VRETRACE_START;
    }

    public void eci_trap_vretrace_start_off()
    {
        trapFlags &= ~TraceTrap.TRACE_STOP_VRETRACE_START;
    }

    public void eci_trap_vretrace_end_on()
    {
        trapFlags |= TraceTrap.TRACE_STOP_VRETRACE_END;
    }

    public void eci_trap_vretrace_end_off()
    {
        trapFlags &= ~TraceTrap.TRACE_STOP_VRETRACE_END;
    }

    public void eci_trap_timed_disable()
    {
        this.imminentTrapTime = -1;
    }

    public void eci_trap_timed(Long time)
    {
        this.imminentTrapTime = time.longValue();
    }

    public void eci_pc_start()
    {
        startExternal();
    }

    public void eci_pc_stop()
    {
        stopExternal();
    }

    public void eci_pccontrol_setwinpos(Integer x, Integer y)
    {
        moveWindow(this, x.intValue(), y.intValue(), 720, 50);
    }

    public void eci_sendevent(String clazz, String[] rargs)
    {
        if(currentProject.events != null) {
            try {
                Class <? extends HardwareComponent> x = Class.forName(clazz).asSubclass(HardwareComponent.class);
                currentProject.events.addEvent(0L, x, rargs);
            } catch(Exception e) {
                System.err.println("Error adding event: " + e.getMessage());
            }
        }
    }

    public void eci_memory_read(Long address, Integer size)
    {
        if(currentProject.pc != null) {
            long addr = address.longValue();
            long _size = size.intValue();
            long ret = 0;
            PhysicalAddressSpace addrSpace;
            if(addr < 0 || addr > 0xFFFFFFFFL || (_size != 1 && _size != 2 && _size != 4))
                return;

            addrSpace = (PhysicalAddressSpace)currentProject.pc.getComponent(PhysicalAddressSpace.class);
            if(_size == 1)
                ret = (long)addrSpace.getByte((int)addr) & 0xFF;
            else if(_size == 2)
                ret = (long)addrSpace.getWord((int)addr) & 0xFFFF;
            else if(_size == 4)
                ret = (long)addrSpace.getDoubleWord((int)addr) & 0xFFFFFFFFL;

            vPluginManager.returnValue(ret);
        }
    }

    public void eci_memory_write(Long address, Long value, Integer size)
    {
        if(currentProject.pc != null) {
            long addr = address.longValue();
            long _size = size.intValue();
            long _value = value.longValue();
            PhysicalAddressSpace addrSpace;
            if(addr < 0 || addr > 0xFFFFFFFFL || (_size != 1 && _size != 2 && _size != 4))
                return;

            addrSpace = (PhysicalAddressSpace)currentProject.pc.getComponent(PhysicalAddressSpace.class);
            if(_size == 1)
                addrSpace.setByte((int)addr, (byte)_value);
            else if(_size == 2)
                addrSpace.setWord((int)addr, (short)_value);
            else if(_size == 4)
                addrSpace.setDoubleWord((int)addr, (int)_value);
        }
    }

    public PCControl(Plugins manager) throws Exception
    {
        super("JPC-RR");
        running = false;
        this.willCleanup = false;
        shuttingDown = false;
        configDialog = new PCConfigDialog();

        menuManager = new MenuManager();

        menuManager.setProfile(PROFILE_NO_PC | PROFILE_STOPPED);

        menuManager.addMenuItem("File→Assemble", this, "menuAssemble", null, PROFILE_STOPPED);
        menuManager.addMenuItem("File→Start", this, "menuStart", null, PROFILE_STOPPED | PROFILE_HAVE_PC);
        menuManager.addMenuItem("File→Stop", this, "menuStop", null, PROFILE_RUNNING);
        menuManager.addMenuItem("File→Change Run Authors", this, "menuChangeAuthors", null, PROFILE_HAVE_PC);
        menuManager.addMenuItem("File→Reset", this, "menuReset", null, PROFILE_HAVE_PC);
        menuManager.addMenuItem("File→Quit", this, "menuQuit", null, PROFILE_ALWAYS);
        menuManager.addSelectableMenuItem("Breakpoints→Trap VRetrace Start", this, "menuVRetraceStart", null, false,
            PROFILE_ALWAYS);
        menuManager.addSelectableMenuItem("Breakpoints→Trap VRetrace End", this, "menuVRetraceEnd", null, false,
            PROFILE_ALWAYS);
        menuManager.addMenuItem("Snapshot→Save→Snapshot", this, "menuSave", new Object[]{new Boolean(false)},
            PROFILE_HAVE_PC | PROFILE_STOPPED);
        menuManager.addMenuItem("Snapshot→Save→Movie", this, "menuSave", new Object[]{new Boolean(true)},
            PROFILE_HAVE_PC | PROFILE_STOPPED);
        menuManager.addMenuItem("Snapshot→Save→Status Dump", this, "menuStatusDump", null,
            PROFILE_HAVE_PC | PROFILE_STOPPED);
        menuManager.addMenuItem("Snapshot→Load→Snapshot", this, "menuLoad",
            new Object[]{new Integer(LoadStateTask.MODE_NORMAL)}, PROFILE_STOPPED);
        menuManager.addMenuItem("Snapshot→Load→Snapshot (preserve events)", this, "menuLoad",
            new Object[]{new Integer(LoadStateTask.MODE_PRESERVE)}, PROFILE_STOPPED | PROFILE_EVENTS);
        menuManager.addMenuItem("Snapshot→Load→Movie", this, "menuLoad",
            new Object[]{new Integer(LoadStateTask.MODE_MOVIEONLY)}, PROFILE_STOPPED);
        menuManager.addMenuItem("Snapshot→RAM Dump→Hexadecimal", this, "menuRAMDump", new Object[]{new Boolean(false)},
            PROFILE_HAVE_PC | PROFILE_STOPPED);
        menuManager.addMenuItem("Snapshot→RAM Dump→Binary", this, "menuRAMDump", new Object[]{new Boolean(true)},
            PROFILE_HAVE_PC | PROFILE_STOPPED);
        menuManager.addMenuItem("Snapshot→Truncate Event Stream", this, "menuTruncate", null,
            PROFILE_STOPPED | PROFILE_EVENTS);

        for(int i = 0; i < stopLabel.length; i++) {
            menuManager.addSelectableMenuItem("Breakpoints→Timed Stops→" + stopLabel[i], this, "menuTimedStop",
                null, (i == 0), PROFILE_ALWAYS);
        }
        imminentTrapTime = -1;

        menuManager.addMenuItem("Drives→fda→<Empty>", this, "menuChangeDisk", new Object[]{new Integer(0),
            new Integer(-1)}, PROFILE_HAVE_PC);
        menuManager.addMenuItem("Drives→fdb→<Empty>", this, "menuChangeDisk", new Object[]{new Integer(1),
            new Integer(-1)}, PROFILE_HAVE_PC);
        menuManager.addMenuItem("Drives→CD-ROM→<Empty>", this, "menuChangeDisk", new Object[]{new Integer(2),
            new Integer(-1)}, PROFILE_HAVE_PC | PROFILE_CDROM);
        menuManager.addMenuItem("Drives→Add image", this, "menuAddDisk", null, PROFILE_HAVE_PC);

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
        menuManager.setSelected("Breakpoints→Trap VRetrace Start",
            (trapFlags & TraceTrap.TRACE_STOP_VRETRACE_START) == TraceTrap.TRACE_STOP_VRETRACE_START);
    }

    public void menuVRetraceEnd(String i, Object[] args)
    {
        trapFlags ^= TraceTrap.TRACE_STOP_VRETRACE_END;
        menuManager.setSelected("Breakpoints→Trap VRetrace End",
            (trapFlags & TraceTrap.TRACE_STOP_VRETRACE_END) == TraceTrap.TRACE_STOP_VRETRACE_END);
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
        (new Thread(new LoadStateTask(((Integer)args[0]).intValue()))).start();
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

    public void menuChangeAuthors(String i, Object[] args)
    {
        (new Thread(new ChangeAuthorsTask())).start();
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

    private String prettyPrintTime(long ts)
    {
        String s = "";

        if(ts >= 1000000000)
            s = s + "" + (ts / 1000000000) + " ";
        if(ts >= 100000000)
            s = s + "" + (ts % 1000000000 / 100000000);
        if(ts >= 10000000)
            s = s + "" + (ts % 100000000 / 10000000);
        if(ts >= 1000000)
            s = s + "" + (ts % 10000000 / 1000000) + " ";
        if(ts >= 100000)
            s = s + "" + (ts % 1000000 / 100000);
        if(ts >= 10000)
           s = s + ""  + (ts % 100000 / 10000);
        if(ts >= 1000)
            s = s + "" + (ts % 10000 / 1000) + " ";
        if(ts >= 100)
            s = s + "" + (ts % 1000 / 100);
        if(ts >= 10)
            s = s + "" + (ts % 100 / 10);
        s = s + ""     + (ts % 10);
        return s;
    }

    protected synchronized void stopNoWait()
    {
        running = false;
        vPluginManager.pcStopped();
        Clock sysClock = (Clock)pc.getComponent(Clock.class);
        System.err.println("Notice: PC emulation stopped (at time sequence value " +
            prettyPrintTime(sysClock.getTime()) + ")");
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
        int _mode;
        long oTime;
        private static final int MODE_NORMAL = 1;
        private static final int MODE_PRESERVE = 2;
        private static final int MODE_MOVIEONLY = 3;

        public LoadStateTask(int mode)
        {
            oTime = System.currentTimeMillis();
            choosen = null;
            _mode = mode;
            pw = new PleaseWait("Loading savestate...");
        }

        public LoadStateTask(String name, int mode)
        {
            this(mode);
            choosen = new File(name);
        }

        protected void runPrepare()
        {
            PCControl.this.setEnabled(false);
            if(choosen == null) {
                int returnVal = 0;
                if(_mode == MODE_PRESERVE)
                    returnVal = snapshotFileChooser.showDialog(PCControl.this, "LOAD JPC-RR Snapshot (PE)");
                else if(_mode == MODE_MOVIEONLY)
                    returnVal = snapshotFileChooser.showDialog(PCControl.this, "LOAD JPC-RR Snapshot (MO)");
                else
                    returnVal = snapshotFileChooser.showDialog(PCControl.this, "LOAD JPC-RR Snapshot");
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
            PCControl.this.vPluginManager.signalCommandCompletion();
        }

        protected void runTask()
        {
            if(choosen == null)
                return;

            try {
                System.err.println("Informational: Loading a snapshot of JPC-RR");
                long times1 = System.currentTimeMillis();
                JRSRArchiveReader reader = new JRSRArchiveReader(choosen.getAbsolutePath());

                PC.PCFullStatus fullStatus = PC.loadSavestate(reader, (_mode == MODE_PRESERVE) ?
                    currentProject.events : null, (_mode == MODE_MOVIEONLY));
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
            PCControl.this.vPluginManager.signalCommandCompletion();
        }

        protected void runTask()
        {
            if(choosen == null)
                return;

            JRSRArchiveWriter writer = null;

            try {
                System.err.println("Informational: Savestating...");
                long times1 = System.currentTimeMillis();
                choosen.renameTo(new File(choosen.getAbsolutePath() + ".backup"));
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
            PCControl.this.vPluginManager.signalCommandCompletion();
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
            PCControl.this.vPluginManager.signalCommandCompletion();
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
            try {
                configDialog.popUp();
            } catch(Exception e) {
                caught = e;
            }
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
            PCControl.this.vPluginManager.signalCommandCompletion();
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
            PCControl.this.vPluginManager.signalCommandCompletion();
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

    private class ChangeAuthorsTask extends AsyncGUITask
    {
        Exception caught;
        boolean canceled;
        AuthorsDialog ad;

        public ChangeAuthorsTask()
        {
            int authors = 0;
            int headers = 0;
            String[] authorNames = null;
            canceled = false;

            if(currentProject != null && currentProject.extraHeaders != null) {
                headers = currentProject.extraHeaders.length;
                for(int i = 0; i < headers; i++)
                    if(currentProject.extraHeaders[i][0].equals("AUTHORS"))
                        authors += (currentProject.extraHeaders[i].length - 1);
            }
            if(authors > 0) {
                int j = 0;
                authorNames = new String[authors];
                for(int i = 0; i < headers; i++) {
                    if(currentProject.extraHeaders[i][0].equals("AUTHORS")) {
                        System.arraycopy(currentProject.extraHeaders[i], 1, authorNames, j,
                            currentProject.extraHeaders[i].length - 1);
                        j += (currentProject.extraHeaders[i].length - 1);
                    }
                }
            }

            ad = new AuthorsDialog(authorNames);
            PCControl.this.setEnabled(false);
        }

        protected void runPrepare()
        {
        }

        protected void runFinish()
        {
            if(caught != null) {
                errorDialog(caught, "Changing authors failed", PCControl.this, "Dismiss");
            }
            PCControl.this.setEnabled(true);
            PCControl.this.vPluginManager.signalCommandCompletion();
        }

        protected void runTask()
        {
            AuthorsDialog.Response res = ad.waitClose();
            if(res == null) {
                canceled = true;
                return;
            }
            try {
                int newAuthors = 0;
                int oldAuthors = 0;
                int headers = 0;
                if(currentProject != null && currentProject.extraHeaders != null) {
                    headers = currentProject.extraHeaders.length;
                    for(int i = 0; i < headers; i++)
                        if(currentProject.extraHeaders[i][0].equals("AUTHORS"))
                            oldAuthors++;
                }
                if(res.authors != null) {
                    for(int i = 0; i < res.authors.length; i++)
                        if(res.authors[i] != null)
                            newAuthors++;
                }
                if(headers == oldAuthors && newAuthors == 0) {
                    //Remove all extra headers.
                    currentProject.extraHeaders = null;
                    return;
                }

                String[][] newHeaders = new String[headers + newAuthors - oldAuthors][];
                int writePos = 0;

                //Copy the non-authors headers.
                if(currentProject != null && currentProject.extraHeaders != null) {
                    for(int i = 0; i < headers; i++)
                        if(!currentProject.extraHeaders[i][0].equals("AUTHORS"))
                            newHeaders[writePos++] = currentProject.extraHeaders[i];
                }
                if(res.authors != null)
                    for(int i = 0; i < res.authors.length; i++)
                        if(res.authors[i] != null)
                            newHeaders[writePos++] = new String[]{"AUTHORS", res.authors[i]};
                currentProject.extraHeaders = newHeaders;
            } catch(Exception e) {
                caught = e;
            }
        }
    }
}
