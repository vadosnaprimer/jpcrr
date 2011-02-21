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

package org.jpc.plugins;

import java.awt.Dimension;
import java.io.*;
import java.util.*;
import java.lang.reflect.*;
import java.security.AccessControlException;
import javax.swing.*;
import java.awt.dnd.*;
import java.awt.datatransfer.*;
import javax.swing.border.EtchedBorder;

import org.jpc.emulator.HardwareComponent;
import org.jpc.emulator.PC;
import org.jpc.emulator.EventRecorder;
import org.jpc.emulator.TraceTrap;
import org.jpc.emulator.DriveSet;
import org.jpc.emulator.DisplayController;
import org.jpc.emulator.memory.PhysicalAddressSpace;
import org.jpc.emulator.pci.peripheral.VGACard;
import org.jpc.emulator.StatusDumper;
import org.jpc.emulator.Clock;
import org.jpc.emulator.VGADigitalOut;
import org.jpc.emulator.PCHardwareInfo;
import org.jpc.emulator.DiskImageSet;
import org.jpc.images.COWImage;
import org.jpc.plugins.RAWDumper;
import org.jpc.pluginsaux.PleaseWait;
import org.jpc.pluginsaux.BreakpointsMenu;
import org.jpc.pluginsaux.AsyncGUITask;
import org.jpc.pluginsaux.NewDiskDialog;
import org.jpc.pluginsaux.AuthorsDialog;
import org.jpc.pluginsaux.PCConfigDialog;
import org.jpc.pluginsaux.MenuManager;
import org.jpc.pluginsaux.PCMonitorPanel;
import org.jpc.pluginsaux.PCMonitorPanelEmbedder;
import org.jpc.pluginsaux.ImportDiskImage;
import org.jpc.images.BaseImage;
import org.jpc.Misc;
import org.jpc.bus.*;
import org.jpc.jrsr.*;

import static org.jpc.Misc.randomHexes;
import static org.jpc.Misc.errorDialog;
import static org.jpc.Misc.callShowOptionDialog;
import static org.jpc.Misc.castToString;
import static org.jpc.Misc.castToInt;
import static org.jpc.Misc.castToLong;
import static org.jpc.Misc.moveWindow;
import static org.jpc.Misc.parseStringsToComponents;
import static org.jpc.Misc.nextParseLine;
import static org.jpc.Misc.renameFile;

public class PCControl implements PCMonitorPanelEmbedder
{
    private static long PROFILE_ALWAYS = 0;
    private static long PROFILE_NO_PC = 1;
    private static long PROFILE_HAVE_PC = 2;
    private static long PROFILE_STOPPED = 4;
    private static long PROFILE_RUNNING = 8;
    private static long PROFILE_EVENTS = 16;
    private static long PROFILE_CDROM = 32;
    private static long PROFILE_DUMPING = 64;
    private static long PROFILE_NOT_DUMPING = 128;
    private static long PROFILE_HAVE_HDA = 256;
    private static long PROFILE_HAVE_HDB = 512;
    private static long PROFILE_HAVE_HDC = 1024;
    private static long PROFILE_HAVE_HDD = 2048;
    private static String SAVESTATE_LABEL = "Savestating...";
    private static String LOADSTATE_LABEL = "Loadstating...";
    private static String RAMDUMP_LABEL = "Dumping RAM...";
    private static String IMAGEDUMP_LABEL = "Dumping Image...";
    private static String STATUSDUMP_LABEL = "Dumping status...";
    private static String ASSEMBLE_LABEL = "Assembling system...";
    private static String ADDDISK_LABEL = "Adding new disk...";
    private static String CHANGEAUTHORS_LABEL = "Changing run authors...";

    private static final long serialVersionUID = 8;
    private Bus bus;

    private JFrame window;
    private JFileChooser snapshotFileChooser;
    private JFileChooser otherFileChooser;
    private DropTarget dropTarget;
    private LoadstateDropTarget loadstateDropTarget;
    private RAWDumper dumper;
    private BreakpointsMenu breakpointsMenu;

    private Set<String> disks;

    protected PC pc;

    private volatile long profile;
    private volatile boolean running;
    private volatile boolean waiting;
    private boolean uncompressedSave;
    private boolean shuttingDown;
    private int nativeWidth;
    private int nativeHeight;
    private PCConfigDialog configDialog;
    private MenuManager menuManager;
    private Map<String, List<String[]> > extraActions;
    private PCMonitorPanel panel;
    private JLabel statusBar;
    private volatile int currentResolutionWidth;
    private volatile int currentResolutionHeight;
    private volatile Runnable taskToDo;
    private volatile String taskLabel;
    private boolean cycleDone;
    private Map<String, Class<?>> debugInClass;
    private Map<String, Boolean> debugState;

    private PC.PCFullStatus currentProject;

    class LoadstateDropTarget implements DropTargetListener
    {
        public void dragEnter(DropTargetDragEvent e)         {}
        public void dragOver(DropTargetDragEvent e)          {}
        public void dragExit(DropTargetEvent e)              {}
        public void dropActionChanged(DropTargetDragEvent e) {}

        public void drop(DropTargetDropEvent e)
        {
            if(running) {
                e.rejectDrop();
                return;
            }
            e.acceptDrop(DnDConstants.ACTION_COPY);
            int i = 0;
            for(DataFlavor f : e.getCurrentDataFlavors()) {
                try {
                    Transferable t = e.getTransferable();
                    Object d = t.getTransferData(f);
                    if(f.isMimeTypeEqual("text/uri-list") && d.getClass() == String.class) {
                        String url = (String)d;
                        if(url.indexOf(10) >= 0) {
                            callShowOptionDialog(window, "Hey, only single file at time!",
                                "DnD error", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE, null,
                                new String[]{"Dismiss"}, "Dismiss");
                            e.dropComplete(false);
                            return;
                        }
                        e.dropComplete(handleURLDropped(url));
                        return;
                    }
                } catch(Exception ex) {
                    errorDialog(ex, "Failed to get DnD data", null, "Dismiss");
                    e.dropComplete(false);
                    return;
                }
            }
            for(DataFlavor f : e.getCurrentDataFlavors()) {
                i = 0;
                try {
                    i++;
                    Transferable t = e.getTransferable();
                    Object d = t.getTransferData(f);
                    System.err.println("Notice: Format #" + i + ":" + d.getClass().getName() + "(" + f + ")");
                } catch(Exception ex) {
                    System.err.println("Notice: Format #" + i + ": <ERROR>(" + f + ")");
                }
            }
            callShowOptionDialog(window, "Can't recognize file to load from drop (debugging information dumped to console).",
                "DnD error", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE, null,
                new String[]{"Dismiss"}, "Dismiss");
            e.dropComplete(false);
        }
    }

    private boolean handleURLDropped(String url)
    {
        if(!url.startsWith("file:///")) {
            callShowOptionDialog(window, "Can't load remote resource.",
                "DnD error", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE, null,
                new String[]{"Dismiss"}, "Dismiss");
            return false;
        }
        url = url.substring(7);
        setTask(new LoadStateTask(url, LoadStateTask.MODE_NORMAL, null), LOADSTATE_LABEL);
        return true;
    }

    public boolean systemShutdown()
    {
        if(running && pc != null) {
            //We are running. Do the absolute minimum since we are running in very delicate context.
            shuttingDown = true;
            stop();
            while(running);
        }
        if(panel != null) {
            panel.exitMontorPanelThread();
            try {
                bus.executeCommandSynchronous("remove-renderer", new Object[]{panel.getRenderer()});
            } catch(Exception e) {
            }
        }
        if(!bus.isShuttingDown())
            window.dispose();
        return true;
    }

    public void reconnect(String cmd, Object[] args)
    {
        pcStopping("pc-stop", null);  //Do the equivalent effects.
        updateStatusBar();
        updateDebug();
    }

    public void notifySizeChange(int w, int h)
    {
        final int w2 = w;
        final int h2 = h;

        SwingUtilities.invokeLater(new Runnable() { public void run() {
            window.pack();
            Dimension d = window.getSize();
            nativeWidth = d.width;
            nativeHeight = d.height;
            currentResolutionWidth = w2;
            currentResolutionHeight = h2;
            updateStatusBarEventThread();
        }});
    }

    public void notifyFrameReceived(int w, int h)
    {
        currentResolutionWidth = w;
        currentResolutionHeight = h;
        updateStatusBar();
    }

    public void pcStarting(String cmd, Object[] args)
    {
        profile = PROFILE_HAVE_PC | PROFILE_RUNNING | (profile & (PROFILE_DUMPING | PROFILE_NOT_DUMPING));
        if(currentProject != null && currentProject.events != null);
            profile |= PROFILE_EVENTS;
        if(pc.getHasCDROM())
            profile |= PROFILE_CDROM;

        menuManager.setProfile(profile);

        if (running)
            return;

        pc.getTraceTrap().setTrapFlags(breakpointsMenu.getTrapFlags());

        Clock sysClock = (Clock)pc.getComponent(Clock.class);
        long current = sysClock.getTime();
        long imminentTrapTime = breakpointsMenu.getTrapDuration();
        if(imminentTrapTime >= 0)
            pc.getTraceTrap().setTrapTime(current + imminentTrapTime);
        if(currentProject.events != null)
            currentProject.events.setPCRunStatus(true);
    }

    public void pcStopping(String cmd, Object[] args)
    {
        if(currentProject.events != null)
            currentProject.events.setPCRunStatus(false);
        if(shuttingDown)
            return;   //Don't mess with UI when shutting down.


        profile = PROFILE_STOPPED | (profile & (PROFILE_DUMPING | PROFILE_NOT_DUMPING));
        if(pc != null)
            profile |= PROFILE_HAVE_PC;
        else
            profile |= PROFILE_NO_PC;
        if(currentProject != null && currentProject.events != null);
            profile |= PROFILE_EVENTS;
        if(pc.getHasCDROM())
            profile |= PROFILE_CDROM;

        menuManager.setProfile(profile);
        updateStatusBar();

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
        DriveSet driveset = pc.getDrives();
        int[] floppies = imageSet.diskIndicesByType(BaseImage.Type.FLOPPY);
        int[] cdroms = imageSet.diskIndicesByType(BaseImage.Type.CDROM);

        for(int i = 0; i < floppies.length; i++) {
            String name = diskNameByIdx(floppies[i]);
            menuManager.addMenuItem("Drives→fda→" + name, this, "menuChangeDisk", new Object[]{new Integer(0),
                new Integer(floppies[i])}, PROFILE_HAVE_PC);
            menuManager.addMenuItem("Drives→fdb→" + name, this, "menuChangeDisk", new Object[]{new Integer(1),
                 new Integer(floppies[i])}, PROFILE_HAVE_PC);
            menuManager.addMenuItem("Drives→dump→" + name, this, "menuDumpDisk", new Object[]{
                 new Integer(floppies[i])}, PROFILE_HAVE_PC);
            menuManager.addSelectableMenuItem("Drives→Write Protect→" + name, this, "menuWriteProtect",
                 new Object[]{new Integer(floppies[i])}, imageSet.lookupDisk(floppies[i]).isReadOnly(),
                 PROFILE_HAVE_PC);
            disks.add("Drives→fda→" + name);
            disks.add("Drives→fdb→" + name);
            disks.add("Drives→Write Protect→" + name);
            disks.add("Drives→dump→" + name);

            COWImage dev;
            DriveSet drives = pc.getDrives();
            profile = profile & ~(PROFILE_HAVE_HDA | PROFILE_HAVE_HDB | PROFILE_HAVE_HDC | PROFILE_HAVE_HDD);
            profile = profile | ((drives.getHardDrive(0) != null) ? PROFILE_HAVE_HDA : 0);
            profile = profile | ((drives.getHardDrive(1) != null) ? PROFILE_HAVE_HDB : 0);
            profile = profile | ((drives.getHardDrive(2) != null) ? PROFILE_HAVE_HDC : 0);
            profile = profile | ((drives.getHardDrive(3) != null) ? PROFILE_HAVE_HDD : 0);
            menuManager.setProfile(profile);
        }

        for(int i = 0; i < cdroms.length; i++) {
            String name = diskNameByIdx(cdroms[i]);
            menuManager.addMenuItem("Drives→CD-ROM→" + name, this, "menuChangeDisk", new Object[]{new Integer(1),
                 new Integer(cdroms[i])}, PROFILE_HAVE_PC | PROFILE_CDROM);
            disks.add("Drives→CD-ROM→" + name);
        }
    }

    private synchronized boolean setTask(Runnable task, String label)
    {
        boolean run = running;
        if(run || taskToDo != null)
            return false;   //Can't do tasks with PC running or existing task.
        taskToDo = task;
        taskLabel = label;
        notifyAll();
        updateStatusBar();
        return true;
    }

    public void main()
    {
        boolean wasRunning = false;
        while(true) {   //We will be killed by JVM.
            //Wait for us to become runnable again.
            while((!running || pc == null) && taskToDo == null) {
                if(!running && wasRunning && pc != null)
                    pc.stop();
                wasRunning = running;
                try {
                    synchronized(this) {
                        if((running && pc != null) || taskToDo != null)
                            continue;
                        waiting = true;
                        notifyAll();
                        wait();
                        waiting = false;
                    }
                } catch(Exception e) {
                }
            }
            //
            if(running && !wasRunning)
                pc.start();
            wasRunning = running;

            if(taskToDo != null) {
                taskToDo.run();
                taskToDo = null;
                updateStatusBar();
                continue;
            }

            try {
                pc.execute();
                if(pc.getHitTraceTrap()) {
                    if(pc.getAndClearTripleFaulted())
                        callShowOptionDialog(window, "CPU shut itself down due to triple fault. Rebooting the system.",
                            "Triple fault!", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE, null,
                            new String[]{"Dismiss"}, "Dismiss");
                    if(shuttingDown)
                        stopNoWait();
                    else
                        SwingUtilities.invokeAndWait(new Thread() { public void run() { stopNoWait(); }});
                    running = false;
                    doCycle(pc);
                }
            } catch (Exception e) {
                running = false;
                doCycle(pc);
                errorDialog(e, "Hardware emulator internal error", window, "Dismiss");
                try {
                    if(shuttingDown)
                        stopNoWait();
                    else
                        SwingUtilities.invokeAndWait(new Thread() { public void run() { stopNoWait(); }});
                    SwingUtilities.invokeAndWait(new Thread() { public void run() { stopNoWait(); }});
                } catch (Exception f) {
                }
            }
        }
    }


    public void connectPC(PC pc)
    {
        currentProject.pc = pc;
        bus.invokeEvent("pc-change", new Object[]{pc});
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

    public String projectIDMangleFileName(String name)
    {
        String ID = (currentProject != null && currentProject.projectID != null) ? currentProject.projectID : "";
        return name.replaceAll("\\|", ID);
    }

    public void saveload(BusRequest req, String cmd, Object[] args) throws IllegalArgumentException
    {
        if(args == null || args.length != 1)
            throw new IllegalArgumentException("Command takes an argument");
        String filename = castToString(args[0]);
        boolean success = false;
        Runnable task = null;
        String taskname = null;
        if(SAVESTATE_CMD.equals(cmd)) {
            task = new SaveStateTask(projectIDMangleFileName(filename), false, req);
            taskname = SAVESTATE_LABEL;
        } else if(STATEDUMP_CMD.equals(cmd)) {
            task = new StatusDumpTask(filename, req);
            taskname = STATUSDUMP_LABEL;
        } else if(SAVESTATE_MOVIE_CMD.equals(cmd)) {
            task = new SaveStateTask(projectIDMangleFileName(filename), true, req);
            taskname = SAVESTATE_LABEL;
        } else if(LOADSTATE_CMD.equals(cmd)) {
            task = new LoadStateTask(projectIDMangleFileName(filename), LoadStateTask.MODE_NORMAL, req);
            taskname = LOADSTATE_LABEL;
        } else if(LOADSTATE_NOEVENTS_CMD.equals(cmd)) {
            task = new LoadStateTask(projectIDMangleFileName(filename), LoadStateTask.MODE_PRESERVE, req);
            taskname = LOADSTATE_LABEL;
        } else if(LOADSTATE_MOVIE_CMD.equals(cmd)) {
            task = new LoadStateTask(projectIDMangleFileName(filename), LoadStateTask.MODE_MOVIEONLY, req);
            taskname = LOADSTATE_LABEL;
        } else if(RAMDUMP_TEXT_CMD.equals(cmd)) {
            task = new RAMDumpTask(filename, false, req);
            taskname = RAMDUMP_LABEL;
        } else if(RAMDUMP_BINARY_CMD.equals(cmd)) {
            task = new RAMDumpTask(filename, true, req);
            taskname = RAMDUMP_LABEL;
        }
        if(task == null) {
            req.doReturnL(false);
            return;
        }
        if(!setTask(task, taskname))
            req.doReturnL(false);
    }


    public void imageDump(BusRequest req, String cmd, Object[] args) throws IllegalArgumentException
    {
        if(args == null || args.length != 2)
            throw new IllegalArgumentException("Command takes two arguments");
        String filename = castToString(args[0]);
        int index = castToInt(args[1]);
        if(!setTask(new ImageDumpTask(filename, index, req), IMAGEDUMP_LABEL))
            req.doReturnL(false);
    }

    public void startReq(BusRequest req, String cmd, Object[] args) throws IllegalArgumentException
    {
        startExternal();
        req.doReturn();
    }

    public void stopReq(BusRequest req, String cmd, Object[] args) throws IllegalArgumentException
    {
        stopExternal();
        req.doReturn();
    }


    public void setWinPos(BusRequest req, String cmd, Object[] args) throws IllegalArgumentException
    {
        if(args == null || args.length != 2)
            throw new IllegalArgumentException("Command takes two arguments");
        moveWindow(window, castToInt(args[0]), castToInt(args[1]), nativeWidth, nativeHeight);
        req.doReturn();
    }

    private boolean sendeventCommon(Long timeMin, String clazz, String[] rargs)
    {
        if(timeMin > 0)
            System.err.println("Event to: '" + clazz + "' (with low bound of " + timeMin + "):");
        else
            System.err.println("Event to: '" + clazz + "':");
        for(int i = 0; i < rargs.length; i++) {
            System.err.println("rargs[" + i + "]: '"  + rargs[i] + "'.");
        }
        if(currentProject.events != null) {
            try {
                Class <? extends HardwareComponent> x = Class.forName(clazz).asSubclass(HardwareComponent.class);
                currentProject.events.addEvent(timeMin, x, rargs);
                return true;
            } catch(Exception e) {
                errorDialog(e, "Failed to send event!", null, "dismiss");
                System.err.println("Error adding event: " + e.getMessage());
                return false;
            }
        }
        return false;
    }

    public void sendevent(BusRequest req, String cmd, Object[] args) throws IllegalArgumentException
    {
        if(args == null || args.length < 1)
            throw new IllegalArgumentException("Command takes at least one argument");
        String clazz = castToString(args[0]);
        String[] args2 = new String[args.length - 1];
        for(int i = 1; i < args.length; i++) {
            args2[i - 1] = castToString(args[i]);
        }
        req.doReturnL(sendeventCommon(0L, clazz, args2));
    }

    public void sendeventLB(BusRequest req, String cmd, Object[] args) throws IllegalArgumentException
    {
        if(args == null || args.length < 2)
            throw new IllegalArgumentException("Command takes at least two arguments");
        long bound = castToLong(args[0]);
        String clazz = castToString(args[1]);
        String[] args2 = new String[args.length - 2];
        for(int i = 2; i < args.length; i++) {
            args2[i - 2] = castToString(args[i]);
        }
        req.doReturnL(sendeventCommon(bound, clazz, args2));
    }

    public PCControl(Bus _bus, String[] args) throws Exception
    {
        this(_bus);

        UnicodeInputStream file = null;
        Map<String, String> params = parseStringsToComponents(args);
        Set<String> used = new HashSet<String>();
        String extramenu = params.get("extramenu");
        String uncompress = params.get("uncompressedsave");
        if(uncompress != null)
            uncompressedSave = true;
        if(extramenu == null)
            return;
        try {
            file = new UTF8InputStream(new FileInputStream(extramenu), false);

            while(true) {
                boolean exists = false;
                String[] line = nextParseLine(file);
                if(line == null)
                    break;
                if(line.length < 3 || line[0].charAt(0) == '→') {
                    System.err.println("Warning: Bad extra menu item '" + line[0] + "'.");
                    continue;
                }
                if(line[0].length() == 0 || line[0].charAt(line[0].length() - 1) == '→') {
                    System.err.println("Warning: Bad extra menu item '" + line[0] + "'.");
                    continue;
                }
                if(line[0].indexOf("→→") >= 0) {
                    System.err.println("Warning: Bad extra menu item '" + line[0] + "'.");
                    continue;
                }
                if(used.contains(line[0]))
                    exists = true;

                KeyStroke stroke = null;
                if(!line[1].equals("<>")) {
                    stroke = KeyStroke.getKeyStroke(line[1]);
                    if(stroke == null) {
                        System.err.println("Warning: Bad keystroke '" + line[1] + "'.");

                    }
                }

                String[] lineCommand = Arrays.copyOfRange(line, 2, line.length);
                used.add(line[0]);
                List<String[]> commandList = extraActions.get(line[0]);
                if(commandList == null)
                    extraActions.put(line[0], commandList = new ArrayList<String[]>());
                commandList.add(lineCommand);

                if(!exists)
                    menuManager.addMenuItem("Extra→" + line[0], this, "menuExtra", new String[]{line[0]}, PROFILE_ALWAYS,
                        stroke);
            }
            file.close();
        } catch(IOException e) {
            errorDialog(e, "Failed to load extra menu defintions", null, "dismiss");
            if(file != null)
                file.close();
        }
        window.setJMenuBar(menuManager.getMainBar());
    }

    private final static String SAVESTATE_CMD = "save-state";
    private final static String STATEDUMP_CMD = "save-dump";
    private final static String SAVESTATE_MOVIE_CMD = "save-movie";
    private final static String LOADSTATE_CMD = "load-state";
    private final static String LOADSTATE_NOEVENTS_CMD = "load-rewind";
    private final static String LOADSTATE_MOVIE_CMD = "load-movie";
    private final static String RAMDUMP_TEXT_CMD = "save-ram-text";
    private final static String RAMDUMP_BINARY_CMD = "save-ram-binary";

    public PCControl(Bus _bus) throws Exception
    {
        bus = _bus;
        bus.setShutdownHandler(this, "systemShutdown");
        bus.setEventHandler(this, "reconnect", "pc-change");
        bus.setEventHandler(this, "pcStarting", "pc-start");
        bus.setEventHandler(this, "pcStopping", "pc-stop");
        bus.setCommandHandler(this, "setWinPos", "pccontrol-setwinpos");
        bus.setCommandHandler(this, "imageDump", "save-image");
        bus.setCommandHandler(this, "startReq", "pc-start");
        bus.setCommandHandler(this, "stopReq", "pc-stop");
        bus.setCommandHandler(this, "sendevent", "sendevent");
        bus.setCommandHandler(this, "sendeventLB", "sendevent-lowbound");
        bus.setCommandHandler(this, "saveload", SAVESTATE_CMD);
        bus.setCommandHandler(this, "saveload", STATEDUMP_CMD);
        bus.setCommandHandler(this, "saveload", SAVESTATE_MOVIE_CMD);
        bus.setCommandHandler(this, "saveload", LOADSTATE_CMD);
        bus.setCommandHandler(this, "saveload", LOADSTATE_MOVIE_CMD);
        bus.setCommandHandler(this, "saveload", LOADSTATE_NOEVENTS_CMD);
        bus.setCommandHandler(this, "saveload", RAMDUMP_BINARY_CMD);
        bus.setCommandHandler(this, "saveload", RAMDUMP_TEXT_CMD);

        window = new JFrame("JPC-RR" + Misc.getEmuname());

        running = false;
        shuttingDown = false;

        debugInClass = new HashMap<String, Class<?>>();
        debugState = new HashMap<String, Boolean>();

        configDialog = new PCConfigDialog();
        extraActions = new HashMap<String, List<String[]> >();
        menuManager = new MenuManager();

        menuManager.setProfile(profile = (PROFILE_NO_PC | PROFILE_STOPPED | PROFILE_NOT_DUMPING));

        menuManager.addMenuItem("System→Assemble", this, "menuAssemble", null, PROFILE_STOPPED);
        menuManager.addMenuItem("System→Start", this, "menuStart", null, PROFILE_STOPPED | PROFILE_HAVE_PC);
        menuManager.addMenuItem("System→Stop", this, "menuStop", null, PROFILE_RUNNING);
        menuManager.addMenuItem("System→Reset", this, "menuReset", null, PROFILE_HAVE_PC);
        menuManager.addMenuItem("System→Start dumping", this, "menuStartDump", null, PROFILE_STOPPED | PROFILE_NOT_DUMPING);
        menuManager.addMenuItem("System→Stop dumping", this, "menuStopDump", null, PROFILE_STOPPED | PROFILE_DUMPING);
        menuManager.addMenuItem("System→Quit", this, "menuQuit", null, PROFILE_ALWAYS);
        menuManager.addMenuItem("Snapshot→Change Run Authors", this, "menuChangeAuthors", null, PROFILE_HAVE_PC);
        menuManager.addMenuItem("Snapshot→Save Snapshot", this, "menuSave", new Object[]{new Boolean(false)},
            PROFILE_HAVE_PC | PROFILE_STOPPED);
        menuManager.addMenuItem("Snapshot→Save Movie", this, "menuSave", new Object[]{new Boolean(true)},
            PROFILE_HAVE_PC | PROFILE_STOPPED);
        menuManager.addMenuItem("Snapshot→Save Status Dump", this, "menuStatusDump", null,
            PROFILE_HAVE_PC | PROFILE_STOPPED);
        menuManager.addMenuItem("Snapshot→Load Snapshot", this, "menuLoad",
            new Object[]{new Integer(LoadStateTask.MODE_NORMAL)}, PROFILE_STOPPED);
        menuManager.addMenuItem("Snapshot→Load Snapshot (preserve events)", this, "menuLoad",
            new Object[]{new Integer(LoadStateTask.MODE_PRESERVE)}, PROFILE_STOPPED | PROFILE_EVENTS);
        menuManager.addMenuItem("Snapshot→Load Movie", this, "menuLoad",
            new Object[]{new Integer(LoadStateTask.MODE_MOVIEONLY)}, PROFILE_STOPPED);
        menuManager.addMenuItem("Snapshot→RAM Dump (hex)", this, "menuRAMDump", new Object[]{new Boolean(false)},
            PROFILE_HAVE_PC | PROFILE_STOPPED);
        menuManager.addMenuItem("Snapshot→RAM Dump (bin)", this, "menuRAMDump", new Object[]{new Boolean(true)},
            PROFILE_HAVE_PC | PROFILE_STOPPED);
        menuManager.addMenuItem("Snapshot→Truncate Event Stream", this, "menuTruncate", null,
            PROFILE_STOPPED | PROFILE_EVENTS);

        menuManager.addMenuItem("Drives→fda→<Empty>", this, "menuChangeDisk", new Object[]{new Integer(0),
            new Integer(-1)}, PROFILE_HAVE_PC);
        menuManager.addMenuItem("Drives→fdb→<Empty>", this, "menuChangeDisk", new Object[]{new Integer(1),
            new Integer(-1)}, PROFILE_HAVE_PC);
        menuManager.addMenuItem("Drives→CD-ROM→<Empty>", this, "menuChangeDisk", new Object[]{new Integer(2),
            new Integer(-1)}, PROFILE_HAVE_PC | PROFILE_CDROM);
        menuManager.addMenuItem("Drives→Add image", this, "menuAddDisk", null, PROFILE_HAVE_PC);
        menuManager.addMenuItem("Drives→Import Image", this, "menuImport", null, PROFILE_ALWAYS);
        menuManager.addMenuItem("Drives→dump→HDA", this, "menuDumpDisk", new Object[]{
              new Integer(-1)}, PROFILE_HAVE_PC | PROFILE_HAVE_HDA);
        menuManager.addMenuItem("Drives→dump→HDB", this, "menuDumpDisk", new Object[]{
              new Integer(-2)}, PROFILE_HAVE_PC | PROFILE_HAVE_HDB);
        menuManager.addMenuItem("Drives→dump→HDC", this, "menuDumpDisk", new Object[]{
              new Integer(-3)}, PROFILE_HAVE_PC | PROFILE_HAVE_HDC);
        menuManager.addMenuItem("Drives→dump→HDD", this, "menuDumpDisk", new Object[]{
              new Integer(-4)}, PROFILE_HAVE_PC | PROFILE_HAVE_HDD);
        menuManager.addMenuItem("Debug→Hacks→NO_FPU", this, "menuNOFPU", null, PROFILE_HAVE_PC);
        menuManager.addMenuItem("Debug→Hacks→VGA_DRAW", this, "menuVGADRAW", null, PROFILE_HAVE_PC);
        menuManager.addMenuItem("Debug→Hacks→VGA_SCROLL_2", this, "menuVGASCROLL2", null, PROFILE_HAVE_PC);
        menuManager.addMenuItem("Debug→Show frame rate", this, "menuFramerate", null, PROFILE_HAVE_PC);
        menuManager.addMenuItem("Debug→Show CRTC register", this, "menuShowCRTC", null, PROFILE_HAVE_PC);

        disks = new HashSet<String>();
        currentProject = new PC.PCFullStatus();
        this.pc = null;

        panel = new PCMonitorPanel(this, bus);
        loadstateDropTarget  = new LoadstateDropTarget();
        dropTarget = new DropTarget(panel.getMonitorPanel(), loadstateDropTarget);

        statusBar = new JLabel("");
        statusBar.setBorder(new EtchedBorder(EtchedBorder.LOWERED));
        panel.startThread();

        window.getContentPane().add("Center", panel.getMonitorPanel());
        window.getContentPane().add("South", statusBar);
        JMenuBar bar = menuManager.getMainBar();
        for(JMenu menu : panel.getMenusNeeded())
            bar.add(menu);
        breakpointsMenu = new BreakpointsMenu(bus);
        bar.add(breakpointsMenu.getMenu());
        window.setJMenuBar(bar);

        try {
            window.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        } catch (AccessControlException e) {
            System.err.println("Error: Not able to add some components to frame: " + e.getMessage());
        }

        snapshotFileChooser = new JFileChooser(System.getProperty("user.dir"));
        otherFileChooser = new JFileChooser(System.getProperty("user.dir"));

        window.getContentPane().validate();
        window.validate();
        window.pack();
        Dimension d = window.getSize();
        nativeWidth = d.width;
        nativeHeight = d.height;
        updateStatusBarEventThread();

        (new Thread(new Runnable(){ public void run() { main(); }}, "PC execution thread")).start();
        window.setVisible(true);
    }

    public void sendMessage(String msg)
    {
        try {
            bus.executeCommandSynchronous("luaplugin-sendmessage", new Object[]{msg});
        } catch(Exception e) {
        }
    }

    private String debugShowName(String name)
    {
        name = name.substring(12);
        StringBuffer buf = new StringBuffer();
        for(int i = 0; i < name.length(); i++)
            if(name.charAt(i) == '_')
                buf.append(' ');
            else
                buf.append(name.charAt(i));
        return buf.toString();
    }

    private void addDebug(String name, Class<?> clazz)
    {
        if(debugInClass.get(name) != null)
            return;
        debugInClass.put(name, clazz);
        debugState.put(name, false);
        try {
             menuManager.addSelectableMenuItem("Debug→" + debugShowName(name), this, "menuDEBUGOPTION",
                 new Object[]{name}, false, PROFILE_HAVE_PC);
        } catch(Exception e) {
        }
    }

    public void menuDEBUGOPTION(String i, Object[] args)
    {
        String name = (String)args[0];
        String mName = "Debug→" + debugShowName(name);
        debugState.put(name, !debugState.get(name));
        setDebugOption(name);
        menuManager.setSelected(mName, debugState.get(name));
    }

    private void setDebugOption(String name)
    {
        try {
            debugInClass.get(name).getDeclaredMethod(name, boolean.class).invoke(pc.getComponent(
                 debugInClass.get(name)), debugState.get(name));
        } catch(Exception e) {
e.printStackTrace();
        }
    }

    private void setDebugOptions()
    {
         for(Map.Entry<String, Class<?>> opt : debugInClass.entrySet())
             setDebugOption(opt.getKey());
    }

    private void updateDebug()
    {
         setDebugOptions();
         for(HardwareComponent c : pc.allComponents()) {
             Class<?> cl = c.getClass();
             for(Method m : cl.getDeclaredMethods()) {
                 Class<?>[] p = m.getParameterTypes();
                 if(!m.getName().startsWith("DEBUGOPTION_"))
                     continue;
                 if(p.length != 1 || p[0] != boolean.class)
                     continue;
                 addDebug(m.getName(), cl);
             }
         }
    }

    public void notifyRenderer(org.jpc.pluginsaux.HUDRenderer r)
    {
        try {
            bus.executeCommandSynchronous("add-renderer", new Object[]{r});
        } catch(Exception e) {
        }
    }

    private void updateStatusBar()
    {
        if(bus.isShuttingDown())
            return;  //Too much of deadlock risk.
        SwingUtilities.invokeLater(new Runnable() { public void run() { updateStatusBarEventThread(); }});
    }

    private void updateStatusBarEventThread()
    {
        String text1;
        if(currentProject.pc != null && taskToDo == null) {
            long timeNow = ((Clock)currentProject.pc.getComponent(Clock.class)).getTime();
            long timeEnd = currentProject.events.getLastEventTime();
            text1 = " Time: " + (timeNow / 1000000) + "ms, movie length: " + (timeEnd / 1000000) + "ms";
            if(currentResolutionWidth > 0 && currentResolutionHeight > 0)
                text1 = text1 + ", resolution: " + currentResolutionWidth + "*" + currentResolutionHeight;
            else
                text1 = text1 + ", resolution: <No valid signal>";
            if(currentProject.events.isAtMovieEnd())
                text1 = text1 + " (At movie end)";
        } else if(taskToDo != null)
            text1 = taskLabel;
        else
            text1 = " NO PC CONNECTED";

        statusBar.setText(text1);
    }

    public void menuExtra(String i, Object[] args)
    {
        final List<String[]> commandList = extraActions.get(args[0]);
        if(commandList == null) {
            System.err.println("Warning: Called extra menu with unknown entry '" + args[0] + "'.");
            return;
        }

        //Run the functions on seperate thread to avoid deadlocking.
        (new Thread(new Runnable() { public void run() { menuExtraThreadFunc(commandList); }}, "Extra action thread")).start();
    }

    private void menuExtraThreadFunc(List<String[]> actions)
    {
        Object[] ret = null;
        int rindex = 0;
        try {
            for(String[] i : actions) {
                if(i.length == 1) {
                    ret = bus.executeCommandSynchronous(i[0], null);
                } else {
                    String[] rest = Arrays.copyOfRange(i, 1, i.length, String[].class);
                    ret = bus.executeCommandSynchronous(i[0], rest);
                }
                if(ret == null)
                    System.err.println(i[0] + " => (no return value)");
                else
                    for(Object r : ret)
                       if(r != null)
                           System.err.println(i[0] + "#" + (++rindex) + " => " + r.toString());
                       else
                           System.err.println(i[0] + "#" + (++rindex) + " => <null>");
            }
        } catch(Exception e) {
            errorDialog(e, "Error running actions", null, "Dismiss");
        }
    }

    public void menuAssemble(String i, Object[] args)
    {
        setTask(new AssembleTask(), ASSEMBLE_LABEL);
    }

    public void menuStart(String i, Object[] args)
    {
        start();
    }

    public void menuStartDump(String i, Object[] args)
    {
        int returnVal = otherFileChooser.showDialog(window, "Dump to file");
        if(returnVal != 0)
            return;
        File choosen = otherFileChooser.getSelectedFile();
        try {
            dumper = new RAWDumper(bus, new String[]{"rawoutput=" + choosen.getAbsolutePath()});
            pc.refreshGameinfo(currentProject);
        } catch(Exception e) {
            errorDialog(e, "Failed to start dumping", null, "Dismiss");
            return;
        }
        profile &= ~PROFILE_NOT_DUMPING;
        profile |= PROFILE_DUMPING;
        menuManager.setProfile(profile);
    }

    public void menuStopDump(String i, Object[] args)
    {
        try {
            bus.unloadComponent(dumper);
        } catch(Exception f) {
        }
        profile &= ~PROFILE_DUMPING;
        profile |= PROFILE_NOT_DUMPING;
        menuManager.setProfile(profile);
    }

    public void menuStop(String i, Object[] args)
    {
        stop();
    }

    public void menuReset(String i, Object[] args)
    {
        reset();
    }

    public void menuImport(String i, Object[] args)
    {
        try {
            new ImportDiskImage();
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    public void menuNOFPU(String i, Object[] args)
    {
        pc.setFPUHack();
    }

    public void menuVGADRAW(String i, Object[] args)
    {
        pc.setVGADrawHack();
    }

    public void menuVGASCROLL2(String i, Object[] args)
    {
        pc.setVGAScroll2Hack();
    }

    public void menuFramerate(String i, Object[] args)
    {
        VGACard card = (VGACard)pc.getComponent(VGACard.class);
        if(card == null) {
            callShowOptionDialog(window, "Can't get current framerate!", "Error", JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE, null, new String[]{"Dismiss"}, "Dismiss");
            return;
        }
        callShowOptionDialog(window, "Current framerate is " + card.getFramerate() + " fps.", "Information",
            JOptionPane.YES_NO_OPTION,JOptionPane.INFORMATION_MESSAGE, null, new String[]{"Dismiss"}, "Dismiss");
    }

    public void menuShowCRTC(String i, Object[] args)
    {
        VGACard card = (VGACard)pc.getComponent(VGACard.class);
        if(card == null) {
            callShowOptionDialog(window, "Can't get current CTRC registers!", "Error", JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE, null, new String[]{"Dismiss"}, "Dismiss");
            return;
        }
        callShowOptionDialog(window, card.getCTRCDump(), "Information",
            JOptionPane.YES_NO_OPTION,JOptionPane.INFORMATION_MESSAGE, null, new String[]{"Dismiss"}, "Dismiss");
    }

    public void menuQuit(String i, Object[] args)
    {
        bus.shutdownEmulator();
    }

    public void menuSave(String i, Object[] args)
    {
        setTask(new SaveStateTask(((Boolean)args[0]).booleanValue()), SAVESTATE_LABEL);
    }

    public void menuStatusDump(String i, Object[] args)
    {
        setTask(new StatusDumpTask(), STATUSDUMP_LABEL);
    }

    public void menuLoad(String i, Object[] args)
    {
        setTask(new LoadStateTask(((Integer)args[0]).intValue()), LOADSTATE_LABEL);
    }

    public void menuRAMDump(String i, Object[] args)
    {
        setTask(new RAMDumpTask(((Boolean)args[0]).booleanValue()), RAMDUMP_LABEL);
    }

    public void menuDumpDisk(String i, Object[] args)
    {
        setTask(new ImageDumpTask(((Integer)args[0]).intValue()), IMAGEDUMP_LABEL);
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
        setTask(new AddDiskTask(), ADDDISK_LABEL);
    }

    public void menuChangeAuthors(String i, Object[] args)
    {
        setTask(new ChangeAuthorsTask(), CHANGEAUTHORS_LABEL);
    }

    public synchronized void start()
    {
        if(taskToDo != null)
            return;
        bus.invokeEvent("pc-start", null);
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
        bus.invokeEvent("pc-stop", null);
        Clock sysClock = (Clock)pc.getComponent(Clock.class);
        System.err.println("Notice: PC emulation stopped (at time sequence value " +
            prettyPrintTime(sysClock.getTime()) + ")");
    }

    public synchronized void stop()
    {
        pc.getTraceTrap().doPotentialTrap(TraceTrap.TRACE_STOP_IMMEDIATE);
        System.err.println("Informational: Waiting for PC to halt...");
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

    static String chooseMovie(Set<String> choices)
    {
        if(choices.isEmpty())
            return null;
        String[] x = new String[1];
        x = choices.toArray(x);
        int i = callShowOptionDialog(null, "Multiple initializations exist, pick one",
            "Multiple movies in one", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE , null,
            x, x[0]);
        return "initialization-" + x[i];
    }

    static void parseSubmovies(UnicodeInputStream lines, Set<String> choices, boolean force) throws IOException
    {
        String[] components = nextParseLine(lines);
        while(components != null) {
           if("SAVESTATEID".equals(components[0]) && !force) {
               choices.clear();
               return;
           }
           if("INITIALSTATE".equals(components[0])) {
               if(components.length != 2)
                   throw new IOException("Bad " + components[0] + " line in header segment: " +
                       "expected 2 components, got " + components.length);
               choices.add(components[1]);
           }
           components = nextParseLine(lines);
        }
    }

    private class LoadStateTask extends AsyncGUITask
    {
        File chosen;
        Exception caught;
        int _mode;
        long oTime;
        private static final int MODE_NORMAL = 1;
        private static final int MODE_PRESERVE = 2;
        private static final int MODE_MOVIEONLY = 3;
        BusRequest req;

        public LoadStateTask(int mode)
        {
            oTime = System.currentTimeMillis();
            chosen = null;
            _mode = mode;
        }

        public LoadStateTask(String name, int mode, BusRequest _req)
        {
            this(mode);
            chosen = new File(name);
            req = _req;
        }

        protected void runPrepare()
        {
            if(chosen == null) {
                int returnVal = 0;
                if(_mode == MODE_PRESERVE)
                    returnVal = snapshotFileChooser.showDialog(window, "LOAD JPC-RR Snapshot (PE)");
                else if(_mode == MODE_MOVIEONLY)
                    returnVal = snapshotFileChooser.showDialog(window, "LOAD JPC-RR Snapshot (MO)");
                else
                    returnVal = snapshotFileChooser.showDialog(window, "LOAD JPC-RR Snapshot");
                chosen = snapshotFileChooser.getSelectedFile();

                if (returnVal != 0)
                    chosen = null;
            }
        }

        protected void runFinish()
        {
            if(chosen == null)
                return;

            if(caught == null) {
                try {
                    connectPC(pc = currentProject.pc);
                    doCycle(pc);
                    System.err.println("Informational: Loadstate done on "+chosen.getAbsolutePath());
                } catch(Exception e) {
                    caught = e;
                }
            }
            if(caught != null) {
                errorDialog(caught, "Load savestate failed", window, "Dismiss");
                if(req != null)
                    req.doReturnL(false);
                return;
            }
            System.err.println("Total save time: " + (System.currentTimeMillis() - oTime) + "ms.");
            if(req != null)
                req.doReturnL(true);
        }

        protected void runTask()
        {
            if(chosen == null)
                return;

            try {
                System.err.println("Informational: Loading a snapshot of JPC-RR");
                long times1 = System.currentTimeMillis();
                JRSRArchiveReader reader = new JRSRArchiveReader(chosen.getAbsolutePath());

                PC.PCFullStatus fullStatus;
                String choosenSubmovie = null;
                Set<String> submovies = new HashSet<String>();
                UnicodeInputStream lines = reader.readMember("header");
                parseSubmovies(lines, submovies, _mode == MODE_MOVIEONLY);
                if(!submovies.isEmpty())
                    choosenSubmovie = chooseMovie(submovies);
                fullStatus = PC.loadSavestate(reader, _mode == MODE_PRESERVE, _mode == MODE_MOVIEONLY,
                    currentProject, choosenSubmovie);

                currentProject = fullStatus;

                reader.close();
                long times2 = System.currentTimeMillis();
                System.err.println("Informational: Loadstate complete (" + (times2 - times1) + "ms).");
            } catch(Exception e) {
                 caught = e;
            }
        }
    }

    private synchronized void doCycleDedicatedThread(PC _pc)
    {
        if(_pc == null) {
            cycleDone = true;
            return;
        }
        DisplayController dc = (DisplayController)_pc.getComponent(DisplayController.class);
        dc.getOutputDevice().holdOutput(_pc.getTime());
        cycleDone = true;
        notifyAll();
    }

    private void doCycle(PC _pc)
    {
        final PC _xpc = _pc;
        cycleDone = false;
        (new Thread(new Runnable() { public void run() { doCycleDedicatedThread(_xpc); }}, "VGA output cycle thread")).start();
        while(cycleDone)
            try {
                synchronized(this) {
                    if(cycleDone)
                        break;
                    wait();
                }
            } catch(Exception e) {
            }
    }

    private class SaveStateTask extends AsyncGUITask
    {
        File chosen;
        Exception caught;
        boolean movieOnly;
        long oTime;
        BusRequest req;

        public SaveStateTask(boolean movie)
        {
            oTime = System.currentTimeMillis();
            chosen = null;
            movieOnly = movie;
        }

        public SaveStateTask(String name, boolean movie, BusRequest _req)
        {
            this(movie);
            chosen = new File(name);
            req = _req;
        }

        protected void runPrepare()
        {
            if(chosen == null) {
                int returnVal = snapshotFileChooser.showDialog(window, movieOnly ? "Save JPC-RR Movie" :
                    "Save JPC-RR Snapshot");
                chosen = snapshotFileChooser.getSelectedFile();

                if (returnVal != 0)
                    chosen = null;
            }
        }

        protected void runFinish()
        {
            if(caught != null) {
                errorDialog(caught, "Saving savestate failed", window, "Dismiss");
                req.doReturnL(false);
                return;
            }
            System.err.println("Total save time: " + (System.currentTimeMillis() - oTime) + "ms.");
            if(req != null)
                req.doReturnL(true);
        }

        protected void runTask()
        {
            if(chosen == null)
                return;

            JRSRArchiveWriter writer = null;

            try {
                System.err.println("Informational: Savestating...");
                long times1 = System.currentTimeMillis();
                writer = new JRSRArchiveWriter(chosen.getAbsolutePath());
                PC.saveSavestate(writer, currentProject, movieOnly, uncompressedSave);
                renameFile(chosen, new File(chosen.getAbsolutePath() + ".backup"));
                writer.close();
                long times2 = System.currentTimeMillis();
                System.err.println("Informational: Savestate complete (" + (times2 - times1) + "ms). on"+chosen.getAbsolutePath());
            } catch(Exception e) {
                 if(writer != null)
                     try { writer.rollback(); } catch(Exception f) {}
                 caught = e;
            }
        }
    }

    private class StatusDumpTask extends AsyncGUITask
    {
        File chosen;
        Exception caught;
        BusRequest req;

        public StatusDumpTask()
        {
            chosen = null;
        }

        public StatusDumpTask(String name, BusRequest _req)
        {
            this();
            chosen = new File(name);
            req = _req;
        }

        protected void runPrepare()
        {
            if(chosen == null) {
                int returnVal = otherFileChooser.showDialog(window, "Save Status dump");
                chosen = otherFileChooser.getSelectedFile();

                if (returnVal != 0)
                    chosen = null;
            }
        }

        protected void runFinish()
        {
            if(caught != null) {
                errorDialog(caught, "Status dump failed", window, "Dismiss");
                req.doReturnL(false);
                return;
            }
            if(req != null)
                req.doReturnL(true);
        }

        protected void runTask()
        {
            if(chosen == null)
                return;

            try {
                if(pc == null)
                    throw new IllegalArgumentException("No PC");
                OutputStream outb = new BufferedOutputStream(new FileOutputStream(chosen));
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
        File chosen;
        Exception caught;
        boolean binary;
        BusRequest req;

        public RAMDumpTask(boolean binFlag)
        {
            chosen = null;
            binary = binFlag;
        }

        public RAMDumpTask(String name, boolean binFlag, BusRequest _req)
        {
            this(binFlag);
            chosen = new File(name);
            req = _req;
        }

        protected void runPrepare()
        {
            if(chosen == null) {
                int returnVal;
                if(binary)
                    returnVal = otherFileChooser.showDialog(window, "Save RAM dump");
                else
                    returnVal = otherFileChooser.showDialog(window, "Save RAM hexdump");
                chosen = otherFileChooser.getSelectedFile();

                if (returnVal != 0)
                    chosen = null;
            }
        }

        protected void runFinish()
        {
            if(caught != null) {
                errorDialog(caught, "RAM dump failed", window, "Dismiss");
                req.doReturnL(false);
                return;
            }
            if(req != null)
                req.doReturnL(true);
        }

        protected void runTask()
        {
            if(chosen == null)
                return;

            try {
                if(pc == null)
                    throw new IllegalArgumentException("No PC");
                OutputStream outb = new BufferedOutputStream(new FileOutputStream(chosen));
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

    private class ImageDumpTask extends AsyncGUITask
    {
        File chosen;
        Exception caught;
        int index;
        BusRequest req;

        public ImageDumpTask(int _index)
        {
            chosen = null;
            index = _index;
        }

        public ImageDumpTask(String name, int index, BusRequest _req)
        {
            this(index);
            chosen = new File(name);
            req = _req;
        }

        protected void runPrepare()
        {
            if(chosen == null) {
                int returnVal;
                returnVal = otherFileChooser.showDialog(window, "Save Image dump");
                chosen = otherFileChooser.getSelectedFile();

                if (returnVal != 0)
                    chosen = null;
            }
        }

        protected void runFinish()
        {
            if(caught != null) {
                errorDialog(caught, "Image dump failed", window, "Dismiss");
                req.doReturnL(false);
                return;
            }
            req.doReturnL(true);
        }

        protected void runTask()
        {
            if(chosen == null)
                return;

            try {
                COWImage dev;
                if(pc == null)
                    throw new IllegalArgumentException("No PC");
                if(index < 0)
                    try {
                        dev = pc.getDrives().getHardDrive(-1 - index);
                    } catch(Exception e) {
                        dev = null;
                    }
                else
                    dev = pc.getDisks().lookupDisk(index);
                if(dev == null)
                    throw new IOException("Trying to dump nonexistent disk");
                OutputStream outb = new BufferedOutputStream(new FileOutputStream(chosen));
                byte[] buf = new byte[512];
                long sectors = dev.getTotalSectors();
                for(long i = 0; i < sectors; i++) {
                    dev.read(i, buf, 1);
                    outb.write(buf);
                }
                outb.close();
                System.err.println("Informational: Dumped disk image (" + sectors + " sectors).");
            } catch(Exception e) {
                 caught = e;
            }
        }
    }

    private class AssembleTask extends AsyncGUITask
    {
        Exception caught;
        boolean canceled;

        public AssembleTask()
        {
            canceled = false;
        }

        protected void runPrepare()
        {
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
                    currentProject.events.setRerecordCount(0);
                    currentProject.events.setHeaders(currentProject.extraHeaders);
                    connectPC(pc);
                } catch(Exception e) {
                    caught = e;
                }
            }
            if(caught != null) {
                errorDialog(caught, "PC Assembly failed", window, "Dismiss");
            }
        }

        protected void runTask()
        {
            if(caught != null)
                return;
            PCHardwareInfo hw = configDialog.waitClose();
            if(hw == null) {
                canceled = true;
                return;
            }

            try {
                pc = new PC(hw);
            } catch(Exception e) {
                 caught = e;
            }
        }
    }

    private class AddDiskTask extends AsyncGUITask
    {
        Exception caught;
        NewDiskDialog dd;

        public AddDiskTask()
        {
            dd = new NewDiskDialog();
        }

        protected void runPrepare()
        {
        }

        protected void runFinish()
        {
            if(caught != null) {
                errorDialog(caught, "Adding disk failed", window, "Dismiss");
            }
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
                return;
            }
            try {
                if(pc == null)
                    throw new IllegalArgumentException("No PC");
                COWImage img;
                pc.getDisks().addDisk(img = new COWImage(res.diskID));
                img.setName(res.diskName);
            } catch(Exception e) {
                caught = e;
            }
        }
    }

    private class ChangeAuthorsTask extends AsyncGUITask
    {
        Exception caught;
        AuthorsDialog ad;

        public ChangeAuthorsTask()
        {
            int authors = 0;
            int headers = 0;
            AuthorsDialog.AuthorElement[] authorNames = null;
            String gameName = "";
            if(currentProject != null)
                authorNames = AuthorsDialog.readAuthorsFromHeaders(currentProject.extraHeaders);
            if(currentProject != null)
                gameName = AuthorsDialog.readGameNameFromHeaders(currentProject.extraHeaders);
            ad = new AuthorsDialog(authorNames, gameName);
        }

        protected void runPrepare()
        {
        }

        protected void runFinish()
        {
            if(caught != null) {
                errorDialog(caught, "Changing authors failed", window, "Dismiss");
            }
        }

        protected void runTask()
        {
            AuthorsDialog.Response res = ad.waitClose();
            if(res == null) {
                return;
            }
            try {
                 currentProject.extraHeaders = AuthorsDialog.rewriteHeaderAuthors(currentProject.extraHeaders,
                     res.authors, res.gameName);
                 currentProject.events.setHeaders(currentProject.extraHeaders);
            } catch(Exception e) {
                caught = e;
            }
        }
    }
}
