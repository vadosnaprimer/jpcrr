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

import org.jpc.hud.HUDRenderer;
import org.jpc.emulator.HardwareComponent;
import org.jpc.plugins.RAWDumper;
import org.jpc.pluginsaux.BreakpointsMenu;
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
    //This includes have flags and dumping flags.
    private static long PROFILE_INVARIANT = 4083;
    //This includes dumping flags.
    private static long PROFILE_INVARIANT_UI = 192;

    private static final long serialVersionUID = 8;
    private Bus bus;

    private JFrame window;
    private JFileChooser snapshotFileChooser;
    private JFileChooser otherFileChooser;
    private DropTarget dropTarget;
    private RAWDumper dumper;
    private BreakpointsMenu breakpointsMenu;
    private JLabel statusBar;
    private JLabel movieStatusBar;
    private JLabel signalStatusBar;
    private int nativeWidth;
    private int nativeHeight;
    private MenuManager menuManager;
    private volatile int currentResolutionWidth;
    private volatile int currentResolutionHeight;
    private volatile String taskLabel;
    private volatile long profile;


    private Set<String> disks;

    private volatile boolean running;
    private boolean shuttingDown;
    private Map<String, List<String[]> > extraActions;
    private PCMonitorPanel panel;
    private boolean cycleDone;
    private Map<String, Class<?>> debugInClass;
    private Map<String, Boolean> debugState;


    public synchronized void reconnect(String cmd, Object[] args)
    {
        if(args == null || args.length != 1)
            throw new IllegalArgumentException("Event needs an argument");
        profile &= PROFILE_INVARIANT_UI;
        //The rest of profile flags have to be reloaded.
        if((Boolean)(bus.executeCommandNoFault("has-pc", null)[0]))
            profile |= PROFILE_HAVE_PC;
        else
            profile |= PROFILE_NO_PC;
        if((Boolean)(bus.executeCommandNoFault("is-running", null)[0]))
            profile |= PROFILE_RUNNING;
        else
            profile |= PROFILE_STOPPED;
        if((Boolean)(bus.executeCommandNoFault("has-events", null)[0]))
            profile |= PROFILE_EVENTS;
        if((Boolean)(bus.executeCommandNoFault("has-cd-drive", null)[0]))
            profile |= PROFILE_CDROM;
        if((Boolean)(bus.executeCommandNoFault("has-drive", new Object[]{0})[0]))
            profile |= PROFILE_HAVE_HDA;
        if((Boolean)(bus.executeCommandNoFault("has-drive", new Object[]{1})[0]))
            profile |= PROFILE_HAVE_HDA;
        if((Boolean)(bus.executeCommandNoFault("has-drive", new Object[]{2})[0]))
            profile |= PROFILE_HAVE_HDB;
        if((Boolean)(bus.executeCommandNoFault("has-drive", new Object[]{3})[0]))
            profile |= PROFILE_HAVE_HDC;

        try {
            updateDisks();
        } catch(Exception e) {
            errorDialog(e, "Failed to update disk menus", null, "Dismiss");
        }
        updateDebug();
        updateHacks();
    }

    private void updateHacks()
    {
        menuManager.removeAllMenuItems("Debug→Hacks→");
        Object[] hacks = bus.executeCommandNoFault("list-hacks", null);
        for(Object o : hacks)
            menuManager.addMenuItem("Debug→Hacks→" + o.toString(), this, "menuHack", new Object[]{o.toString()},
                PROFILE_HAVE_PC);
    }

    public synchronized void pcStarting(String cmd, Object[] args)
    {
        profile &= PROFILE_INVARIANT;
        profile |= PROFILE_RUNNING;
        menuManager.setProfile(profile);
        updateStatusBar();
    }

    public synchronized void pcStopping(String cmd, Object[] args)
    {
        if(shuttingDown)
            return;   //Don't mess with UI when shutting down.

        profile &= PROFILE_INVARIANT;
        profile |= PROFILE_STOPPED;
        menuManager.setProfile(profile);
        updateStatusBar();
    }

    public PCControl(Bus _bus, String[] args) throws Exception
    {
        this(_bus);

        UnicodeInputStream file = null;
        Map<String, String> params = parseStringsToComponents(args);
        Set<String> used = new HashSet<String>();
        String extramenu = params.get("extramenu");
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
                    menuManager.addMenuItem("Extra→" + line[0], this, "menuExtra", new String[]{line[0]},
                        PROFILE_ALWAYS, stroke);
            }
            file.close();
        } catch(IOException e) {
            errorDialog(e, "Failed to load extra menu defintions", null, "dismiss");
            if(file != null)
                file.close();
        }
        window.setJMenuBar(menuManager.getMainBar());
    }


    public PCControl(Bus _bus) throws Exception
    {
        bus = _bus;
        bus.setShutdownHandler(this, "systemShutdown");
        bus.setEventHandler(this, "reconnect", "pc-change");
        bus.setEventHandler(this, "addedDisk", "disk-added");
        bus.setEventHandler(this, "pcStarting", "pc-start");
        bus.setEventHandler(this, "pcStopping", "pc-stop");
        bus.setEventHandler(this, "emunameChanged", "emuname-changed");
        bus.setEventHandler(this, "emuStatusChanged", "emustatus-changed");
        bus.setCommandHandler(this, "setWinPos", "set-pccontrol-window-position");

        window = new JFrame("JPC-RR" + Misc.getEmuname());

        running = false;
        shuttingDown = false;

        debugInClass = new HashMap<String, Class<?>>();
        debugState = new HashMap<String, Boolean>();

        extraActions = new HashMap<String, List<String[]> >();
        menuManager = new MenuManager();

        menuManager.setProfile(profile = (PROFILE_NO_PC | PROFILE_STOPPED | PROFILE_NOT_DUMPING));

        menuManager.addMenuItem("System→Assemble", this, "menuAssemble", null, PROFILE_STOPPED);
        menuManager.addMenuItem("System→Start", this, "menuStart", null, PROFILE_STOPPED | PROFILE_HAVE_PC);
        menuManager.addMenuItem("System→Stop", this, "menuStop", null, PROFILE_RUNNING);
        menuManager.addMenuItem("System→Reset", this, "menuReset", null, PROFILE_HAVE_PC);
        menuManager.addMenuItem("System→Start dumping", this, "menuStartDump", null,
            PROFILE_STOPPED | PROFILE_NOT_DUMPING);
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
            new Object[]{new Integer(MODE_NORMAL)}, PROFILE_STOPPED);
        menuManager.addMenuItem("Snapshot→Load Snapshot (preserve events)", this, "menuLoad",
            new Object[]{new Integer(MODE_PRESERVE)}, PROFILE_STOPPED | PROFILE_EVENTS);
        menuManager.addMenuItem("Snapshot→Load Movie", this, "menuLoad",
            new Object[]{new Integer(MODE_MOVIEONLY)}, PROFILE_STOPPED);
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

        disks = new HashSet<String>();

        panel = new PCMonitorPanel(this, bus);
        dropTarget = new DropTarget(panel.getMonitorPanel(), new LoadstateDropTarget());

        statusBar = new JLabel("");
        movieStatusBar = new JLabel("");
        signalStatusBar = new JLabel("");
        statusBar.setBorder(new EtchedBorder(EtchedBorder.LOWERED));
        movieStatusBar.setBorder(new EtchedBorder(EtchedBorder.LOWERED));
        signalStatusBar.setBorder(new EtchedBorder(EtchedBorder.LOWERED));
        panel.startThread();

        window.getContentPane().add("Center", panel.getMonitorPanel());
        window.getContentPane().add("South", statusBar);
        window.getContentPane().add("South", movieStatusBar);
        window.getContentPane().add("South", signalStatusBar);
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
        updateStatusBar();

        window.setVisible(true);
    }

    private String diskNameByIdx(int idx)
    {
        return (String)(bus.executeCommandNoFault("get-diskname", new Object[]{idx})[0]);
    }

    private void updateDisks() throws Exception
    {
        for(String x : disks)
            menuManager.removeMenuItem(x);

        disks.clear();

        Object[] floppies = bus.executeCommandNoFault("get-disks", new Object[]{BaseImage.Type.FLOPPY});
        Object[] cdroms = bus.executeCommandNoFault("get-disks", new Object[]{BaseImage.Type.CDROM});

        for(int i = 0; i < floppies.length; i++) {
            String name = diskNameByIdx((Integer)floppies[i]);
            menuManager.addMenuItem("Drives→fda→" + name, this, "menuChangeDisk", new Object[]{new Integer(0),
                floppies[i]}, PROFILE_HAVE_PC);
            menuManager.addMenuItem("Drives→fdb→" + name, this, "menuChangeDisk", new Object[]{new Integer(1),
                 floppies[i]}, PROFILE_HAVE_PC);
            menuManager.addMenuItem("Drives→dump→" + name, this, "menuDumpDisk", new Object[]{ floppies[i]},
                 PROFILE_HAVE_PC);
            menuManager.addMenuItem("Drives→Write Protect→" + name, this, "menuWriteProtect",
                 new Object[]{floppies[i]}, PROFILE_HAVE_PC);
            menuManager.addMenuItem("Drives→Write Unprotect→" + name, this, "menuWriteUnprotect",
                 new Object[]{floppies[i]}, PROFILE_HAVE_PC);
            disks.add("Drives→fda→" + name);
            disks.add("Drives→fdb→" + name);
            disks.add("Drives→Write Protect→" + name);
            disks.add("Drives→Write Unprotect→" + name);
            disks.add("Drives→dump→" + name);
        }

        for(int i = 0; i < cdroms.length; i++) {
            String name = diskNameByIdx((Integer)cdroms[i]);
            menuManager.addMenuItem("Drives→CD-ROM→" + name, this, "menuChangeDisk", new Object[]{new Integer(2),
                 cdroms[i]}, PROFILE_HAVE_PC | PROFILE_CDROM);
            disks.add("Drives→CD-ROM→" + name);
        }
    }

    public synchronized void addedDisk(String cmd, Object[] args)
    {
        //TODO.
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






    public void menuWriteProtect(String i, Object[] args)
    {
        int disk = ((Integer)args[0]).intValue();
        writeProtect(disk, true);
    }

    public void menuWriteUnprotect(String i, Object[] args)
    {
        int disk = ((Integer)args[0]).intValue();
        writeProtect(disk, false);
    }

    public void notifyRenderer(HUDRenderer r)
    {
        bus.executeCommandNoFault("add-renderer", new Object[]{r});
    }

    public void menuHack(String i, Object[] args)
    {
        bus.executeCommandNoFault("set-hack", args);
    }

    private static final int MODE_NONE = 0;
    private static final int MODE_NORMAL = 1;
    private static final int MODE_PRESERVE = 2;
    private static final int MODE_MOVIEONLY = 3;
    private String loadFilename;
    private int loadMode;

    private boolean handleURLDropped(String url)
    {
        if(!url.startsWith("file:///")) {
            callShowOptionDialog(window, "Can't load remote resource.",
                "DnD error", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE, null,
                new String[]{"Dismiss"}, "Dismiss");
            return false;
        }
        url = url.substring(7);
        if(loadMode != MODE_NONE)
            return true; //One load at a time please.
        loadMode = MODE_NORMAL;
        loadFilename = url;
        callLoadState(null);
        return true;
    }

    public void loadCompletedhandler(BusFuture x)
    {
        final BusFuture _x = x;
        //Execute in AWT event thread.
        if(!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(new Runnable() { public void run() { loadCompletedhandler(_x); }});
            return;
        }
        Object ret[] = x.getData();
        if(ret[0] instanceof Boolean) {
            //Success or failure. We will receive events to change our state, so the operation ends.
            loadMode = MODE_NONE;
            return;
        }
        if(ret[0] == null) {
            //No submovie and we specified one (race condition). Try again without submovie.
            try {
                callLoadState(null);
            } catch(Exception e) {
            }
            return;
        }
        //Submovie found and we didn't specify one (or wrong one). Pick one.
        try {
            callLoadState(chooseMovie(ret));
        } catch(Exception e) {
        }
        return;
    }

    public void callLoadState(String submovie)
    {
        //Invoke the load and set callback.
        BusFuture f;
        if(loadMode == MODE_NORMAL) {
            if(submovie != null)
                f = bus.executeCommandAsynchronous("load-state", new Object[]{loadFilename, submovie});
            else
                f = bus.executeCommandAsynchronous("load-state", new Object[]{loadFilename});
        } else if(loadMode == MODE_MOVIEONLY) {
            if(submovie != null)
                f = bus.executeCommandAsynchronous("load-movie", new Object[]{loadFilename, submovie});
            else
                f = bus.executeCommandAsynchronous("load-movie", new Object[]{loadFilename});
        } else if(loadMode == MODE_PRESERVE) {
            f = bus.executeCommandAsynchronous("load-rewind", new Object[]{loadFilename});
        }
        f.addCallback(this, "loadCompletedhandler");
    }

    public void menuLoad(String i, Object[] args)
    {
        //Well holy god damn, it is operation so chock full of asynchrony.
        loadMode = (Integer)args[0];
        String okButtonName = null;

        if(loadMode == MODE_NORMAL)
            okButtonName = "Load snapshot";
        if(loadMode == MODE_PRESERVE)
            okButtonName = "Rewind to snapshot";
        if(loadMode == MODE_MOVIEONLY)
            okButtonName = "Load movie";

        //Pick the file.
        int returnVal = snapshotFileChooser.showDialog(window, okButtonName);
        File chosen = snapshotFileChooser.getSelectedFile();
        if(returnVal != 0)
            return;  //User canceled.
        loadFilename = chosen.getAbsolutePath();
        try {
            callLoadState(null);
        } catch(Exception e) {
        }
        return;
    }

    static String chooseMovie(Object[] choices)
    {
        if(choices == null || choices.length == 0 || choices[0] == null)
            return null;
        String[] x = new String[choices.length];
        int i = 0;
        for(Object o : choices)
            x[i++] = (String)o;

        i = callShowOptionDialog(null, "Multiple initializations exist, pick one",
            "Multiple movies in one", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE , null,
            x, x[0]);
        return "initialization-" + x[i];
    }

    public void sendMessage(String msg)
    {
        try {
            bus.executeCommandSynchronous("send-lua-message", new Object[]{msg});
        } catch(Exception e) {
            //Okay, just ignore this error. Probably no lua plugin present.
        }
    }

    public void emunameChanged(String cmd, Object[] args)
    {
        Misc.emunameHelper(window, "JPC-RR");
    }

    public void emuStatusChanged(String cmd, Object[] args)
    {
        if(args == null || args.length == 0)
            return;
        String status = castToString(args[0]);
        taskLabel = status;
        updateStatusBar();
    }

    private void updateStatusBar()
    {
        if(bus.isShuttingDown())
            return;  //Too much of deadlock risk.
        if(!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(new Runnable() { public void run() { updateStatusBar(); }});
            return;
        }

        statusBar.setText("Status: " + taskLabel);
        Object[] movieStatus = bus.executeCommandNoFault("get-movie-status", null);
        boolean movieValid = (Boolean)movieStatus[0];
        long timeNow = (Long)movieStatus[1];
        long timeEnd = (Long)movieStatus[2];
        long rerecords = (Long)movieStatus[3];
        long frameCount = (Long)movieStatus[4];
        String text1;
        if(movieValid) {
            text1 = "Movie: " + (timeNow / 1000000) + "/" + (timeEnd / 1000000);
            if(timeNow >= timeEnd)
                text1 = text1 + "(end)";
            text1 = text1 + " Frame: " + frameCount + " Rerecords: " + rerecords;
        } else
            text1 = "Movie: No movie";
        movieStatusBar.setText(text1);

        Object[] signalStatus = bus.executeCommandNoFault("get-signal-status", null);
        int width = (Integer)signalStatus[0];
        int height = (Integer)signalStatus[1];
        long fpsn = (Long)signalStatus[2];
        long fpsd = (Long)signalStatus[3];
        if(width == 0 || height == 0 || fpsn == 0 || fpsd == 0)
            signalStatusBar.setText("VGA signal: No signal");
        else if(fpsd == 1)
            signalStatusBar.setText("VGA signal: " + width + "*" + height + "@" + fpsn + "fps");
        else
            signalStatusBar.setText("VGA signal: " + width + "*" + height + "@" + fpsn + "/" + fpsd + "fps");
    }

    public void menuExtra(String i, Object[] args)
    {
        final List<String[]> commandList = extraActions.get(args[0]);
        if(commandList == null) {
            System.err.println("Warning: Called extra menu with unknown entry '" + args[0] + "'.");
            return;
        }

        //Run the functions on seperate thread to avoid deadlocking.
        (new Thread(new Runnable() { public void run() { menuExtraThreadFunc(commandList); }}, "Extra action " +
            "thread")).start();
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
        new PCConfigDialog(bus);
    }

    public void menuStart(String i, Object[] args)
    {
        bus.executeCommandNoFault("unpause-pc", null);
    }

    public void menuStartDump(String i, Object[] args)
    {
        int returnVal = otherFileChooser.showDialog(window, "Dump to file");
        if(returnVal != 0)
            return;
        File choosen = otherFileChooser.getSelectedFile();
        try {
            dumper = new RAWDumper(bus, new String[]{"rawoutput=" + choosen.getAbsolutePath()});
            bus.executeCommandNoFault("refresh-game-info", null);
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

    public String setWinPos_help(String cmd, boolean brief)
    {
        if(brief)
            return "Set the PC Control window position";
        System.err.println("Synopsis: " + cmd + " <x> <y>");
        System.err.println("Moves the PC Control window to <x> <y>");
        return null;
    }

    public void setWinPos(BusRequest req, String cmd, Object[] args) throws IllegalArgumentException
    {
        if(args == null || args.length != 2)
            throw new IllegalArgumentException("Command takes two arguments");
        moveWindow(window, castToInt(args[0]), castToInt(args[1]), nativeWidth, nativeHeight);
        req.doReturn();
    }

    public String projectIDMangleFileName(String name)
    {
        String ID = (String)(bus.executeCommandNoFault("get-project-id", null)[0]);
        return name.replaceAll("\\|", ID);
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
            updateStatusBar();
        }});
    }

    public void notifyFrameReceived(int w, int h)
    {
        currentResolutionWidth = w;
        currentResolutionHeight = h;
        updateStatusBar();
    }

    public boolean systemShutdown()
    {
        if(panel != null) {
            panel.exitMontorPanelThread();
            bus.executeCommandNoFault("remove-renderer", new Object[]{panel.getRenderer()});
        }
        if(!bus.isShuttingDown())
            window.dispose();
        return true;
    }

    public void menuStop(String i, Object[] args)
    {
        bus.executeCommandNoFault("pause-pc", null);
    }

    public void menuReset(String i, Object[] args)
    {
        reset();
    }

    public void menuImport(String i, Object[] args)
    {
        try {
            new ImportDiskImage(bus);
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    public void menuQuit(String i, Object[] args)
    {
        bus.shutdownEmulator();
    }

    private void doOpWithFile(JFileChooser chooser, String okButton, String command)
    {
        int returnVal = chooser.showDialog(window, okButton);
        File chosen = chooser.getSelectedFile();
        if(returnVal != 0)
            return;  //User canceled.
        try {
            bus.executeCommandSynchronous(command, new Object[]{chosen.getAbsolutePath()});
        } catch(Exception e) {
            //This shouldn't fail. Or if it does, it should print a message.
            e.printStackTrace();
        }
    }

    public void menuSave(String i, Object[] args)
    {
        boolean movie = ((Boolean)args[0]).booleanValue();
        if(movie)
            doOpWithFile(snapshotFileChooser, "Save movie", "save-movie");
        else
            doOpWithFile(snapshotFileChooser, "Save state", "save-state");
    }

    public void menuStatusDump(String i, Object[] args)
    {
        doOpWithFile(otherFileChooser, "Save status dump", "save-dump");
    }

    public void menuRAMDump(String i, Object[] args)
    {
        boolean binary = ((Boolean)args[0]).booleanValue();
        if(binary)
            doOpWithFile(otherFileChooser, "Save RAM dump", "save-ram-binary");
        else
            doOpWithFile(otherFileChooser, "Save RAM hexdump", "save-ram-text");
    }

    public void menuDumpDisk(String i, Object[] args)
    {
        int returnVal = otherFileChooser.showDialog(window, "Save image");
        File chosen = otherFileChooser.getSelectedFile();
        if(returnVal != 0)
            return;  //User canceled.
        try {
            bus.executeCommandSynchronous("save-image", new Object[]{chosen.getAbsolutePath(), (Integer)args[0]});
        } catch(Exception e) {
            //This shouldn't fail. Or if it does, it should print a message.
            e.printStackTrace();
        }
    }

    public void menuTruncate(String i, Object[] args)
    {
        bus.executeCommandNoFault("truncate-event-stream", null);
    }

    public void menuChangeDisk(String i, Object[] args)
    {
        changeFloppy(((Integer)args[0]).intValue(), ((Integer)args[1]).intValue());
    }

    public void menuAddDisk(String i, Object[] args)
    {
        new NewDiskDialog(bus);
    }

    public void menuChangeAuthors(String i, Object[] args)
    {
        new AuthorsDialog(bus);
    }

    protected void reset()
    {
        try {
            bus.executeCommandSynchronous("send-event", new Object[]{"org.jpc.emultor.PC$ResetButton"});
        } catch(Exception e) {
            System.err.println("Error: Failed to reboot");
            errorDialog(e, "Failed to reboot", null, "Dismiss");
        }
    }

    private void changeFloppy(int drive, int image)
    {
        try {
            String driveID = null;
            if(drive == 0)
                driveID = "FDA";
            else if(drive == 1)
                driveID = "FDB";
            else if(drive == 2)
                driveID = "CDROM";
            else
                throw new Exception("Bad drive identifier " + drive + ".");
            bus.executeCommandSynchronous("send-event", new Object[]{"org.jpc.emultor.PC$DiskChanger",
                driveID, "" + image});
        } catch (Exception e) {
            System.err.println("Error: Failed to change disk");
            errorDialog(e, "Failed to change disk", null, "Dismiss");
        }
    }

    private void writeProtect(int image, boolean state)
    {
        try {
            bus.executeCommandSynchronous("send-event", new Object[]{"org.jpc.emultor.PC$DiskChanger",
                state ? "WRITEPROTECT" : "WRITEUNPROTECT", "" + image});
        } catch (Exception e) {
            System.err.println("Error: Failed to change floppy write protect");
            errorDialog(e, "Failed to write (un)protect floppy", null, "Dismiss");
        }
    }

    class LoadstateDropTarget implements DropTargetListener
    {
        public void dragEnter(DropTargetDragEvent e)         {}
        public void dragOver(DropTargetDragEvent e)          {}
        public void dragExit(DropTargetEvent e)              {}
        public void dropActionChanged(DropTargetDragEvent e) {}

        public void drop(DropTargetDropEvent e)
        {
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
            callShowOptionDialog(window, "Can't recognize file to load from drop (debugging information dumped " +
                "to console).", "DnD error", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE, null,
                new String[]{"Dismiss"}, "Dismiss");
            e.dropComplete(false);
        }
    }
}
