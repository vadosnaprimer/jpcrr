package org.jpc.bus;

import org.jpc.emulator.PC;
import org.jpc.emulator.TraceTrap;
import org.jpc.emulator.PCHardwareInfo;
import org.jpc.emulator.StatusDumper;
import org.jpc.emulator.EventRecorder;
import org.jpc.emulator.DiskImageSet;
import org.jpc.emulator.Clock;
import org.jpc.jrsr.*;
import org.jpc.images.BaseImage;
import org.jpc.images.COWImage;
import org.jpc.images.ImageID;
import org.jpc.images.ImageID;
import org.jpc.emulator.DisplayController;
import org.jpc.emulator.HardwareComponent;
import org.jpc.emulator.memory.PhysicalAddressSpace;
import java.io.*;
import java.util.*;
import javax.swing.JOptionPane;
import static org.jpc.Misc.callShowOptionDialog;
import static org.jpc.Misc.nextParseLine;
import static org.jpc.Misc.renameFile;
import static org.jpc.Misc.castToByte;
import static org.jpc.Misc.castToShort;
import static org.jpc.Misc.castToInt;
import static org.jpc.Misc.castToLong;
import static org.jpc.Misc.castToString;
import static org.jpc.Misc.errorDialog;
import static org.jpc.Misc.castToBoolean;
import static org.jpc.Misc.randomHexes;
import static org.jpc.emulator.TraceTrap.TRACE_STOP_VRETRACE_START;
import static org.jpc.emulator.TraceTrap.TRACE_STOP_VRETRACE_END;
import static org.jpc.emulator.TraceTrap.TRACE_STOP_BIOS_KBD;

class BusInternalCommands
{
    //All the information about PC and associated state.
    private PC.PCFullStatus status;
    //Bus this is connected to.
    private Bus bus;
    //If True, savestates are uncompressed. If false, savestates are compressed.
    private boolean uncompressedSave;
    //Current status string.
    private String statusName;
    //If false, emulation is paused (or no PC instance), if true, emulation is active.
    volatile private boolean running;
    //Last state of running flag signaled to PC.
    volatile private boolean wasRunning;
    //True if pause in emulation is temporary. False if running or in permanent pause.
    volatile private boolean temporaryPause;
    //True if tasks are being run right now.
    volatile private boolean runningTasks;
    //If true, emulation thread is currently idle.
    volatile private boolean waiting;
    //Tasks to perform when emulation is paused.
    private List<Runnable> taskToDo;
    //Labels for the tasks.
    private List<String> taskNameToDo;
    //Trap flags to set on resume.
    private long trapFlags;
    //Timed trap duration.
    private long timedTrapDuration;

    public BusInternalCommands(Bus _bus)
    {
        _bus.setShutdownHandler(this, "shutdown");
        _bus.setCommandHandler(this, "setStatusName", "set-status-name");
        _bus.setCommandHandler(this, "writeMemoryByte", "write-memory-byte");
        _bus.setCommandHandler(this, "writeMemoryWord", "write-memory-word");
        _bus.setCommandHandler(this, "writeMemoryDoubleWord", "write-memory-dword");
        _bus.setCommandHandler(this, "writeMemoryQuadWord", "write-memory-qword");
        _bus.setCommandHandler(this, "readMemoryByte", "read-memory-byte");
        _bus.setCommandHandler(this, "readMemoryWord", "read-memory-word");
        _bus.setCommandHandler(this, "readMemoryDoubleWord", "read-memory-dword");
        _bus.setCommandHandler(this, "readMemoryQuadWord", "read-memory-qword");
        _bus.setCommandHandler(this, "getProjectID", "get-project-id");
        _bus.setCommandHandler(this, "getMovieStatus", "get-movie-status");
        _bus.setCommandHandler(this, "getSignalStatus", "get-signal-status");
        _bus.setCommandHandler(this, "refreshGameInfo", "refresh-game-info");
        _bus.setCommandHandler(this, "hasEvents", "has-events");
        _bus.setCommandHandler(this, "sendevent", "send-event");
        _bus.setCommandHandler(this, "truncateEventStream", "truncate-event-stream");
        _bus.setCommandHandler(this, "sendeventLB", "send-event-lowbound");
        _bus.setCommandHandler(this, "assemblePC", "assemble-pc");
        _bus.setCommandHandler(this, "getGameinfo", "get-gameinfo");
        _bus.setCommandHandler(this, "setGameinfo", "set-gameinfo");
        _bus.setCommandHandler(this, "setUncompressedFlag", "set-uncompressed-flag");
        _bus.setCommandHandler(this, "getUncompressedFlag", "get-uncompressed-flag");
        _bus.setCommandHandler(this, "saveState", "save-state");
        _bus.setCommandHandler(this, "saveMovie", "save-movie");
        _bus.setCommandHandler(this, "saveStatus", "save-dump");
        _bus.setCommandHandler(this, "saveRAMBinary", "save-ram-binary");
        _bus.setCommandHandler(this, "saveRAMHex", "save-ram-text");
        _bus.setCommandHandler(this, "addDisk", "add-disk");
        _bus.setCommandHandler(this, "hasCDDrive", "has-cd-drive");
        _bus.setCommandHandler(this, "resumeExecution", "unpause-pc");
        _bus.setCommandHandler(this, "pauseExecution", "pause-pc");
        _bus.setCommandHandler(this, "trapVRS", "trap-vertical-retrace-start");
        _bus.setCommandHandler(this, "trapVRE", "trap-vertical-retrace-end");
        _bus.setCommandHandler(this, "trapBKI", "trap-bios-keyboard-input");
        _bus.setCommandHandler(this, "trapTimed", "trap-timed");
        _bus.setCommandHandler(this, "triggerCallbacks", "do-trap-callbacks");
        _bus.setCommandHandler(this, "saveImage", "save-image");
        _bus.setCommandHandler(this, "loadState", "load-state");
        _bus.setCommandHandler(this, "loadStateSeekOnly", "load-rewind");
        _bus.setCommandHandler(this, "loadMovie", "load-movie");
        _bus.setCommandHandler(this, "isRunning", "is-running");
        _bus.setCommandHandler(this, "hasPC", "has-pc");
        _bus.setCommandHandler(this, "hasDrive", "has-drive");
        _bus.setCommandHandler(this, "setHack", "set-hack");
        _bus.setCommandHandler(this, "listHacks", "list-hacks");
        _bus.setCommandHandler(this, "getDiskName", "get-diskname");
        _bus.setCommandHandler(this, "getDisks", "get-disks");


        status = new PC.PCFullStatus();
        bus = _bus;
        taskToDo = new LinkedList<Runnable>();
        taskNameToDo = new LinkedList<String>();
    }

    public boolean shutdown()
    {
        return true;
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

    public String getDisks_help(String cmd, boolean brief)
    {
        if(brief)
            return "Get indices of disks of given type";
        System.err.println("Synopsis: " + cmd + " <type>");
        System.err.println("Returns array containing all disks of given type.");
        return null;
    }

    public synchronized void getDisks(BusRequest req, String cmd, Object[] args)
    {
        if(status == null || status.pc == null) {
            req.doReturn();
            return;
        }
        DiskImageSet imageSet = status.pc.getDisks();
        int[] x = imageSet.diskIndicesByType((BaseImage.Type)args[0]);
        Object[] y = new Object[x.length];
        for(int i = 0; i < x.length; i++)
            y[i] = new Integer(x[i]);
        req.doReturnA(y);
    }

    public String getDiskName_help(String cmd, boolean brief)
    {
        if(brief)
            return "Get name of disk for given disk index";
        System.err.println("Synopsis: " + cmd + " <index>");
        System.err.println("Returns string containing name of disk in given index, blank if none exists.");
        return null;
    }

    public synchronized void getDiskName(BusRequest req, String cmd, Object[] args)
    {
        int idx = castToInt(args[0]);
        if(status == null || status.pc == null) {
            req.doReturn();
            return;
        }
        req.doReturnL(status.pc.getDisks().lookupDisk(idx).getName());
    }

    public String listHacks_help(String cmd, boolean brief)
    {
        if(brief)
            return "List available hacks";
        System.err.println("Synopsis: " + cmd);
        System.err.println("Returns list of valid hack names for set-hack.");
        return null;
    }

    public synchronized void listHacks(BusRequest req, String cmd, Object[] args)
    {
        String hname = castToString(args[0]);
        if(status != null && status.pc != null)
            req.doReturnL("VGA_Draw_Hack", "VGA_Scroll2_Hack");
        else
            req.doReturnL();
    }

    public String setHack_help(String cmd, boolean brief)
    {
        if(brief)
            return "Enable a hack";
        System.err.println("Synopsis: " + cmd + " <hackname>");
        System.err.println("Enables hack <hackname>.");
        return null;
    }

    public synchronized void setHack(BusRequest req, String cmd, Object[] args)
    {
        String hname = castToString(args[0]);
        if(status != null && status.pc != null) {
            if(hname.equals("VGA_Draw_Hack"))
                status.pc.setVGADrawHack();
            if(hname.equals("VGA_Scroll2_Hack"))
                status.pc.setVGAScroll2Hack();
        }
        req.doReturnL();
    }

    public String hasDrive_help(String cmd, boolean brief)
    {
        if(brief)
            return "Is there valid drive in given slot?";
        System.err.println("Synopsis: " + cmd + " <drivenum>");
        System.err.println("Returns true if there is drive there, false otherwise.");
        return null;
    }

    public synchronized void hasDrive(BusRequest req, String cmd, Object[] args)
    {
        int drive = castToInt(args[0]);
        if(drive < 0 || drive > 3) {
            //There's never a drive there.
            req.doReturnL(false);
            return;
        }
        if(status.pc == null) {
            //No PC -> No drive.
            req.doReturnL(false);
            return;
        }
        COWImage img = null;
        try { img = status.pc.getDrives().getHardDrive(drive); } catch(Exception e) {}
        req.doReturnL(img != null);
    }

    public String hasPC_help(String cmd, boolean brief)
    {
        if(brief)
            return "Is there valid PC instance?";
        System.err.println("Synopsis: " + cmd);
        System.err.println("Returns true if there is valid PC instance, false otherwise.");
        return null;
    }

    public synchronized void hasPC(BusRequest req, String cmd, Object[] args)
    {
        req.doReturnL(status != null && status.pc != null);
    }

    public String isRunning_help(String cmd, boolean brief)
    {
        if(brief)
            return "Is the PC running?";
        System.err.println("Synopsis: " + cmd);
        System.err.println("Returns true if the system is running, false otherwise.");
        return null;
    }

    public synchronized void isRunning(BusRequest req, String cmd, Object[] args)
    {
        req.doReturnL(running);
    }

    public String writeMemoryByte_help(String cmd, boolean brief)
    {
        if(brief)
            return "Write byte into memory";
        System.err.println("Synopsis: " + cmd + " <address> <value>");
        System.err.println("Writes byte-sized value <value> to physical address <address>.");
        return null;
    }

    public synchronized void writeMemoryByte(BusRequest req, String cmd, Object[] args)
    {
        int addr = castToInt(args[0]);
        byte value = castToByte(args[1]);
        if(status.pc != null) {
            PhysicalAddressSpace addrSpace;
            addrSpace = (PhysicalAddressSpace)status.pc.getComponent(PhysicalAddressSpace.class);
            addrSpace.setByte(addr, value);
            req.doReturn();
        } else
            throw new IllegalArgumentException("No pc present");
    }

    public String writeMemoryWord_help(String cmd, boolean brief)
    {
        if(brief)
            return "Write word into memory";
        System.err.println("Synopsis: " + cmd + " <address> <value>");
        System.err.println("Writes word-sized value <value> to physical address <address>.");
        return null;
    }

    public synchronized void writeMemoryWord(BusRequest req, String cmd, Object[] args)
    {
        int addr = castToInt(args[0]);
        short value = castToShort(args[1]);
        if(status.pc != null) {
            PhysicalAddressSpace addrSpace;
            addrSpace = (PhysicalAddressSpace)status.pc.getComponent(PhysicalAddressSpace.class);
            addrSpace.setWord(addr, value);
            req.doReturn();
        } else
            throw new IllegalArgumentException("No pc present");
    }

    public String writeMemoryDoubleWord_help(String cmd, boolean brief)
    {
        if(brief)
            return "Write dword into memory";
        System.err.println("Synopsis: " + cmd + " <address> <value>");
        System.err.println("Writes dword-sized value <value> to physical address <address>.");
        return null;
    }

    public synchronized void writeMemoryDoubleWord(BusRequest req, String cmd, Object[] args)
    {
        int addr = castToInt(args[0]);
        int value = castToInt(args[1]);
        if(status.pc != null) {
            PhysicalAddressSpace addrSpace;
            addrSpace = (PhysicalAddressSpace)status.pc.getComponent(PhysicalAddressSpace.class);
            addrSpace.setDoubleWord(addr, value);
            req.doReturn();
        } else
            throw new IllegalArgumentException("No pc present");
    }

    public String  writeMemoryQuadWord_help(String cmd, boolean brief)
    {
        if(brief)
            return "Write qword into memory";
        System.err.println("Synopsis: " + cmd + " <address> <value>");
        System.err.println("Writes qword-sized value <value> to physical address <address>.");
        return null;
    }

    public synchronized void writeMemoryQuadWord(BusRequest req, String cmd, Object[] args)
    {
        int addr = castToInt(args[0]);
        long value = castToLong(args[1]);
        if(status.pc != null) {
            PhysicalAddressSpace addrSpace;
            addrSpace = (PhysicalAddressSpace)status.pc.getComponent(PhysicalAddressSpace.class);
            addrSpace.setQuadWord(addr, value);
            req.doReturn();
        } else
            throw new IllegalArgumentException("No pc present");
    }

    public String readMemoryByte_help(String cmd, boolean brief)
    {
        if(brief)
            return "Read byte from memory";
        System.err.println("Synopsis: " + cmd + " <address>");
        System.err.println("Read byte-sized value from physical address <address>.");
        return null;
    }

    public synchronized void readMemoryByte(BusRequest req, String cmd, Object[] args)
    {
        int addr = castToInt(args[0]);
        if(status.pc != null) {
            PhysicalAddressSpace addrSpace;
            addrSpace = (PhysicalAddressSpace)status.pc.getComponent(PhysicalAddressSpace.class);
            req.doReturnL((int)addrSpace.getByte(addr) & 0xFF);
        } else
            throw new IllegalArgumentException("No pc present");
    }

    public String readMemoryWord_help(String cmd, boolean brief)
    {
        if(brief)
            return "Read word from memory";
        System.err.println("Synopsis: " + cmd + " <address>");
        System.err.println("Read word-sized value from physical address <address>.");
        return null;
    }

    public synchronized void readMemoryWord(BusRequest req, String cmd, Object[] args)
    {
        int addr = castToInt(args[0]);
        if(status.pc != null) {
            PhysicalAddressSpace addrSpace;
            addrSpace = (PhysicalAddressSpace)status.pc.getComponent(PhysicalAddressSpace.class);
            req.doReturnL((int)addrSpace.getWord(addr) & 0xFFFF);
        } else
            throw new IllegalArgumentException("No pc present");
    }

    public String readMemoryDoubleWord_help(String cmd, boolean brief)
    {
        if(brief)
            return "Read dword from memory";
        System.err.println("Synopsis: " + cmd + " <address>");
        System.err.println("Read dword-sized value from physical address <address>.");
        return null;
    }

    public synchronized void readMemoryDoubleWord(BusRequest req, String cmd, Object[] args)
    {
        int addr = castToInt(args[0]);
        if(status.pc != null) {
            PhysicalAddressSpace addrSpace;
            addrSpace = (PhysicalAddressSpace)status.pc.getComponent(PhysicalAddressSpace.class);
            req.doReturnL((long)addrSpace.getDoubleWord(addr) & 0xFFFFFFFF);
        } else
            throw new IllegalArgumentException("No pc present");
    }

    public String readMemoryQuadWord_help(String cmd, boolean brief)
    {
        if(brief)
            return "Read qword from memory";
        System.err.println("Synopsis: " + cmd + " <address>");
        System.err.println("Read qword-sized value from physical address <address>.");
        return null;
    }

    public synchronized void readMemoryQuadWord(BusRequest req, String cmd, Object[] args)
    {
        int addr = castToInt(args[0]);
        if(status.pc != null) {
            PhysicalAddressSpace addrSpace;
            addrSpace = (PhysicalAddressSpace)status.pc.getComponent(PhysicalAddressSpace.class);
            req.doReturnL(addrSpace.getQuadWord(addr));
        } else
            throw new IllegalArgumentException("No pc present");
    }

    public String hasCDDrive_help(String cmd, boolean brief)
    {
        if(brief)
            return "Determine if system has a CD-ROM drive";
        System.err.println("Synopsis: " + cmd);
        System.err.println("Returns true if system has a CD-ROM drive, false otherwise.");
        return null;
    }

    public synchronized void hasCDDrive(BusRequest req, String cmd, Object[] args)
    {
        req.doReturnL(status.pc != null && status.pc.getHasCDROM());
    }

    public String hasEvents_help(String cmd, boolean brief)
    {
        if(brief)
            return "Determine if system has an events stream";
        System.err.println("Synopsis: " + cmd);
        System.err.println("Returns true if system has events stream, false otherwise.");
        return null;
    }

    public synchronized void hasEvents(BusRequest req, String cmd, Object[] args)
    {
        req.doReturnL(status != null && status.events != null);
    }

    public String getProjectID_help(String cmd, boolean brief)
    {
        if(brief)
            return "Get the current project ID";
        System.err.println("Synopsis: " + cmd);
        System.err.println("Returns the project ID, blank string if none.");
        return null;
    }

    public synchronized void getProjectID(BusRequest req, String cmd, Object[] args)
    {
        if(status.projectID != null)
            req.doReturnL(status.projectID);
        else
            req.doReturnL("");
    }

    private synchronized boolean sendeventCommon(Long timeMin, String clazz, String[] rargs)
    {
        if(timeMin > 0)
            System.err.println("Event to: '" + clazz + "' (with low bound of " + timeMin + "):");
        else
            System.err.println("Event to: '" + clazz + "':");
        for(int i = 0; i < rargs.length; i++) {
            System.err.println("rargs[" + i + "]: '"  + rargs[i] + "'.");
        }
        if(status.events != null) {
            try {
                Class <? extends HardwareComponent> x = Class.forName(clazz).asSubclass(HardwareComponent.class);
                status.events.addEvent(timeMin, x, rargs);
                return true;
            } catch(Exception e) {
                errorDialog(e, "Failed to send event!", null, "dismiss");
                System.err.println("Error adding event: " + e.getMessage());
                return false;
            }
        }
        return false;
    }

    public String sendevent_help(String cmd, boolean brief)
    {
        if(brief)
            return "Send event to PC";
        System.err.println("Synopsis: " + cmd + " <class> [<arguments>...]");
        System.err.println("Send event to class <class>, with arguments <arguments>...");
        return null;
    }

    //No need to grab the lock in order to serialize here since sendeventCommon will do it and this
    //method by itself doesn't do anything that might race with other requests.
    public void sendevent(BusRequest req, String cmd, Object[] args)
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

    public String sendeventLB_help(String cmd, boolean brief)
    {
        if(brief)
            return "Send event to PC with specified low time bound";
        System.err.println("Synopsis: " + cmd + " <lowbound> <class> [<arguments>...]");
        System.err.println("Send event, later than <lowbound> to class <class>,");
        System.err.println("with arguments <arguments>...");
        return null;
    }

    //No need to grab the lock in order to serialize here since sendeventCommon will do it and this
    //method by itself doesn't do anything that might race with other requests.
    public void sendeventLB(BusRequest req, String cmd, Object[] args)
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

    public String getMovieStatus_help(String cmd, boolean brief)
    {
        if(brief)
            return "Get the current movie status";
        System.err.println("Synopsis: " + cmd);
        System.err.println("Returns at least five elements:");
        System.err.println("First is a Boolean, true if movie is valid");
        System.err.println("First is a Long, giving current PC timecode");
        System.err.println("Second is a Long, giving timecode at end of the movie");
        System.err.println("Third is a Long, giving current rerecord count");
        System.err.println("Fourth and subsequent are Longs, giving frames PCs are at.");
        return null;
    }

    public synchronized void getMovieStatus(BusRequest req, String cmd, Object[] args)
    {
        long timeNow;
        long timeEnd;
        long rerecords;
        long frameCount;
        if(status.pc == null) {
            timeNow = 0;
            frameCount = 0;
        } else {
            timeNow = ((Clock)status.pc.getComponent(Clock.class)).getTime();
            frameCount = ((DisplayController)status.pc.getComponent(DisplayController.class)).getFrameNumber();
        }
        if(status.events != null)
            timeEnd = status.events.getLastEventTime();
        else
            timeEnd = 0;
        rerecords = status.rerecords;
        req.doReturnL(status.events != null, timeNow, timeEnd, rerecords, frameCount);
    }

    public String getSignalStatus_help(String cmd, boolean brief)
    {
        if(brief)
            return "Get the current signal status";
        System.err.println("Synopsis: " + cmd);
        System.err.println("Returns four elements:");
        System.err.println("First is a Integer, giving x resolution of the signal");
        System.err.println("Second is a Integer, giving y resolution of the signal");
        System.err.println("Third is a Long, giving numerator of fps");
        System.err.println("Fourth is a Long, giving denumerator of fps.");
        return null;
    }

    public synchronized void getSignalStatus(BusRequest req, String cmd, Object[] args)
    {
        if(status.pc == null) {
            req.doReturnL(new Integer(0), new Integer(0), new Long(0), new Long(0));
        } else {
            DisplayController dc = ((DisplayController)status.pc.getComponent(DisplayController.class));
            req.doReturnL(dc.getWidth(), dc.getHeight(), dc.getFPSN(), dc.getFPSD());
        }
    }

    public String refreshGameInfo_help(String cmd, boolean brief)
    {
        if(brief)
            return "[Internal] Refresh the gameinfo";
        System.err.println("Synopsis: " + cmd);
        System.err.println("Causes current gameinfo data to be output to the dumping system");
        return null;
    }

    public synchronized void refreshGameInfo(BusRequest req, String cmd, Object[] args)
    {
        status.pc.refreshGameinfo(status);
        req.doReturn();
    }

    public String truncateEventStream_help(String cmd, boolean brief)
    {
        if(brief)
            return "Truncate the event stream at the point where it is now";
        System.err.println("Synopsis: " + cmd);
        System.err.println("Causes event stream to be truncated, discarding future events.");
        return null;
    }

    public synchronized void truncateEventStream(BusRequest req, String cmd, Object[] args)
    {
        status.events.truncateEventStream();
        req.doReturn();
    }

    private synchronized void addTask(Runnable task, String name)
    {
        //If emulation is running, pause it temporarily.
        if(running) {
            temporaryPause = true;
            running = false;
            status.pc.getTraceTrap().doPotentialTrap(TraceTrap.TRACE_STOP_IMMEDIATE);
            System.err.println("Informational: Waiting for PC to halt...");
            status.events.setPCRunStatus(false);
        }
        taskToDo.add(task);
        taskNameToDo.add(name);
        notifyAll();
    }

    //Returns NULL if no task is in the run queue.
    private synchronized Runnable getTask()
    {
        if(!taskToDo.isEmpty())
            return taskToDo.remove(0);
        else
            return null;
    }

    //Return the name of next task.
    private synchronized String getTaskName()
    {
        return taskNameToDo.remove(0);
    }

    //Is there a runnable task?
    private synchronized boolean isTaskRunnable()
    {
        return !taskToDo.isEmpty();
    }

    public String assemblePC_help(String cmd, boolean brief)
    {
        if(brief)
            return "[internal] Given PC hardware specs, make the PC";
        System.err.println("Synopsis: " + cmd + " <specs>");
        System.err.println("Take in PC hardware specs, make the PC and start new project.");
        return null;
    }

    //We don't grab the lock here as all action happens inside a task.
    public void assemblePC(BusRequest req, String cmd, Object[] args) throws Exception
    {
        if(args == null || args.length != 1)
            throw new IllegalArgumentException("Command needs an argument");
        final BusRequest _req = req;
        final PCHardwareInfo info = (PCHardwareInfo)args[0];
        addTask(new Runnable() { public void run() {
            try {
                PC newPC = new PC(info);
                synchronized(BusInternalCommands.this) {
                    status.pc = newPC;
                    status.projectID = randomHexes(24);
                    status.rerecords = 0;
                    status.events = new EventRecorder();
                    status.events.attach(status.pc, null);
                    status.savestateID = null;
                    status.extraHeaders = null;
                    status.events.setRerecordCount(0);
                    status.events.setHeaders(status.extraHeaders);
                    bus.executeCommandNoFault("set-pc", new Object[]{status.pc});
                }
                _req.doReturnL(true);
            } catch(Exception e) {
                errorDialog(e, "PC Assembly failed", null, "Dismiss");
                _req.doReturnL(false);
            }
        }}, "Assembling PC");
    }

    public String getGameInfo_help(String cmd, boolean brief)
    {
        if(brief)
            return "[internal] Read the game info structure";
        System.err.println("Synopsis: " + cmd);
        System.err.println("Return gameinfo structure based on current headers.");
        return null;
    }

    public synchronized void getGameinfo(BusRequest req, String cmd, Object[] args) throws Exception
    {
        req.doReturnL(new GameInfo(status.extraHeaders));
    }

    public String setGameInfo_help(String cmd, boolean brief)
    {
        if(brief)
            return "[internal] Write the game info structure";
        System.err.println("Synopsis: " + cmd + " <structure>");
        System.err.println("Write the contents of gameinfo structure to headers.");
        return null;
    }

    public synchronized void setGameinfo(BusRequest req, String cmd, Object[] args) throws Exception
    {
        if(args == null || args.length != 1)
            throw new IllegalArgumentException("Command needs an argument");
        GameInfo res = (GameInfo)args[0];
        status.extraHeaders = res.rewriteExtraHeaders(status.extraHeaders);
        req.doReturn();
    }

    public String setStatusName_help(String cmd, boolean brief)
    {
        if(brief)
            return "[internal] Set description what the emulator is currently doing";
        System.err.println("Synopsis: " + cmd + " <description>");
        System.err.println("Set the action descriptor and broadcast the change.");
        return null;
    }

    public synchronized void setStatusName(BusRequest req, String cmd, Object[] args) throws Exception
    {
        if(args == null || args.length != 1)
            throw new IllegalArgumentException("Command needs an argument");
        statusName = castToString(args[0]);
        bus.invokeEvent("emustatus-changed", new Object[]{statusName});
        req.doReturn();
    }

    public synchronized void pauseExecution(BusRequest req, String cmd, Object[] args)
    {
        //Make pause permanent.
        temporaryPause = false;
        if(!running) {
            req.doReturn();
            return;
        }
        status.pc.getTraceTrap().doPotentialTrap(TraceTrap.TRACE_STOP_IMMEDIATE);
        System.err.println("Informational: Waiting for PC to halt...");
        status.events.setPCRunStatus(false);
        req.doReturn();
    }

    public synchronized void resumeExecution(BusRequest req, String cmd, Object[] args)
    {
        if(runningTasks) {
            //Make pause temporary.
            temporaryPause = true;
            req.doReturn();
            return;
        }
        if(running) {
            req.doReturn();
            return;
        }
        status.pc.getTraceTrap().setTrapFlags(trapFlags);
        Clock sysClock = (Clock)status.pc.getComponent(Clock.class);
        long current = sysClock.getTime();
        long imminentTrapTime = timedTrapDuration;
        if(timedTrapDuration >= 0)
            status.pc.getTraceTrap().setTrapTime(current + imminentTrapTime);
        else
            status.pc.getTraceTrap().clearTrapTime();
        status.events.setPCRunStatus(true);
        running = true;
        notifyAll();
        req.doReturn();
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

    public String setUncompressedFlag_help(String cmd, boolean brief)
    {
        if(brief)
            return "Set the uncompressed flag";
        System.err.println("Synopsis: " + cmd + " <flag>");
        System.err.println("Sets the state of uncompressed flag to boolean <flag>");
        return null;
    }

    public synchronized void setUncompressedFlag(BusRequest req, String cmd, Object[] args)
    {
        if(args == null || args.length != 1)
            throw new IllegalArgumentException("Command needs an argument");
        uncompressedSave = castToBoolean(args[0]);
        req.doReturn();
    }

    public String getUncompressedFlag_help(String cmd, boolean brief)
    {
        if(brief)
            return "Get the uncompressed flag";
        System.err.println("Synopsis: " + cmd);
        System.err.println("Gets the state of uncompressed flag.");
        return null;
    }

    public synchronized void getUncompressedFlag(BusRequest req, String cmd, Object[] args)
    {
        req.doReturnL(uncompressedSave);
    }

    public String triggerCallbacks_help(String cmd, boolean brief)
    {
        if(brief)
            return "[Internal] Trigeer trap related callbacks";
        System.err.println("Synopsis: " + cmd);
        System.err.println("Triggers trap-flags-changed and trap-duration-changed");
        return null;
    }

    //This doesn't use any state so no need to lock.
    public void triggerCallbacks(BusRequest req, String cmd, Object[] args) throws IllegalArgumentException
    {
        bus.invokeEvent("trap-flags-changed", new Object[]{trapFlags});
        bus.invokeEvent("trap-duration-changed", new Object[]{timedTrapDuration});
        req.doReturn();
    }

    private void trapGeneric(BusRequest req, Object[] args, long flag)
        throws IllegalArgumentException
    {
        if(args == null || args.length != 1)
            throw new IllegalArgumentException("Command takes an argument");
        boolean enable = castToBoolean(args[0]);
        synchronized(this) {
            if(enable)
                trapFlags |= flag;
            else
                trapFlags &= ~flag;
        }
        bus.invokeEvent("trap-flags-changed", new Object[]{trapFlags});
        req.doReturn();
    }


    public String trapVRS_help(String cmd, boolean brief)
    {
        if(brief)
            return "Set Vertical Retrace Start trap on/off";
        System.err.println("Synopsis: " + cmd + " <state>");
        System.err.println("Sets Vertical Retrace Start trap state to <state>.");
        return null;
    }

    public void trapVRS(BusRequest req, String cmd, Object[] args) throws IllegalArgumentException
    {
        trapGeneric(req, args, TRACE_STOP_VRETRACE_START);
    }

    public String trapVRE_help(String cmd, boolean brief)
    {
        if(brief)
            return "Set Vertical Retrace End trap on/off";
        System.err.println("Synopsis: " + cmd + " <state>");
        System.err.println("Sets Vertical Rretrace End trap state to <state>.");
        return null;
    }

    public void trapVRE(BusRequest req, String cmd, Object[] args) throws IllegalArgumentException
    {
        trapGeneric(req, args, TRACE_STOP_VRETRACE_END);
    }

    public String trapBKI_help(String cmd, boolean brief)
    {
        if(brief)
            return "Set BIOS Keyboard Input trap on/off";
        System.err.println("Synopsis: " + cmd + " <state>");
        System.err.println("Sets BIOS Keyboard Input trap state to <state>.");
        return null;
    }

    public void trapBKI(BusRequest req, String cmd, Object[] args) throws IllegalArgumentException
    {
        trapGeneric(req, args, TRACE_STOP_BIOS_KBD);
    }

    public String trapTimed_help(String cmd, boolean brief)
    {
        if(brief)
            return "Set timed trap duration";
        System.err.println("Synopsis: " + cmd + " <duration>");
        System.err.println("Synopsis: " + cmd + " -1");
        System.err.println("Sets Timed trap duration to <duration>.");
        System.err.println("If <duration> is -1, disables the timed trap.");
        return null;
    }

    public void trapTimed(BusRequest req, String cmd, Object[] args) throws IllegalArgumentException
    {
        if(args == null || args.length != 1)
            throw new IllegalArgumentException("Command takes an argument");
        synchronized(this) {
            timedTrapDuration = castToLong(args[0]);
        }
        bus.invokeEvent("trap-duration-changed", new Object[]{timedTrapDuration});
        req.doReturn();
    }

    public String saveState_help(String cmd, boolean brief)
    {
        if(brief)
            return "Save the current PC state";
        System.err.println("Synopsis: " + cmd + " <filename>");
        System.err.println("Save the current PC state to <filename>.");
        return null;
    }

    public void saveState(BusRequest req, String cmd, Object[] args) throws Exception
    {
        saveStateCommon(req, cmd, args, false);
    }

    public String saveMovie_help(String cmd, boolean brief)
    {
        if(brief)
            return "Save the current timeline";
        System.err.println("Synopsis: " + cmd + " <filename>");
        System.err.println("Save the current timeline to <filename>.");
        return null;
    }

    public void saveMovie(BusRequest req, String cmd, Object[] args) throws Exception
    {
        saveStateCommon(req, cmd, args, true);
    }

    public String saveRAMBinary_help(String cmd, boolean brief)
    {
        if(brief)
            return "Dump the PC RAM in binary format";
        System.err.println("Synopsis: " + cmd + " <filename>");
        System.err.println("Dumps the PC main RAM to <filename> (binary format).");
        return null;
    }

    public void saveRAMBinary(BusRequest req, String cmd, Object[] args) throws Exception
    {
        saveRAMCommon(req, cmd, args, false);
    }

    public String saveRAMHex_help(String cmd, boolean brief)
    {
        if(brief)
            return "Dump the PC RAM in hexadecimal format";
        System.err.println("Synopsis: " + cmd + " <filename>");
        System.err.println("Dumps the PC main RAM to <filename> (hexadecimal format).");
        return null;
    }

    public void saveRAMHex(BusRequest req, String cmd, Object[] args) throws Exception
    {
        saveRAMCommon(req, cmd, args, true);
    }

    //No need to lock since everything interesting happens inside a task.
    public void saveRAMCommon(BusRequest req, String cmd, Object[] args, boolean hex) throws Exception
    {
        if(args == null || args.length != 1)
            throw new IllegalArgumentException("Command needs an argument");
        final String filename = castToString(args[0]);
        final BusRequest _req = req;
        final boolean _hex = hex;

        addTask(new Runnable() { public void run() {
            try {
                if(status.pc == null) {
                    callShowOptionDialog(null, "Can't dump PC RAM: No PC present", "Error",
                        JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE, null, new String[]{"Dismiss"},
                            "Dismiss");
                    _req.doReturnL(false);
                    return;
                }
                OutputStream outb = new BufferedOutputStream(new FileOutputStream(filename));
                byte[] pagebuf = new byte[4096];
                PhysicalAddressSpace addr = (PhysicalAddressSpace)status.pc.getComponent(PhysicalAddressSpace.class);
                int lowBound = addr.findFirstRAMPage(0);
                int firstUndumped = 0;
                int highBound = 0;
                int present = 0;
                while(lowBound >= 0) {
                    for(; firstUndumped < lowBound; firstUndumped++)
                        dumpPage(outb, firstUndumped, null, !_hex);
                    addr.readRAMPage(firstUndumped++, pagebuf);
                    dumpPage(outb, lowBound, pagebuf, !_hex);
                    present++;
                    highBound = lowBound + 1;
                    lowBound = addr.findFirstRAMPage(++lowBound);
                }
                outb.flush();
                outb.close();
                System.err.println("Informational: Dumped machine RAM (" + highBound + " pages examined, " +
                    present + " pages present).");
                _req.doReturnL(true);
            } catch(Exception e) {
                errorDialog(e, "Dumping RAM failed", null, "Dismiss");
                _req.doReturnL(false);
            }
        }}, "Saving RAM contents...");
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

    private void dumpPage(OutputStream stream, int pageNo, byte[] buffer, boolean binary) throws IOException
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

    public String saveStatus_help(String cmd, boolean brief)
    {
        if(brief)
            return "Dump the PC state";
        System.err.println("Synopsis: " + cmd + " <filename>");
        System.err.println("Dumps the current state of PC to <filename>.");
        return null;
    }

    public String addDisk_help(String cmd, boolean brief)
    {
        if(brief)
            return "Add disk to the system";
        System.err.println("Synopsis: " + cmd + " <imagename> <imageid>");
        System.err.println("Adds new disk starting from <imageid> as disk <imagename>");
        return null;
    }

    public void saveStatus(BusRequest req, String cmd, Object[] args) throws Exception
    {
        if(args == null || args.length != 1)
            throw new IllegalArgumentException("Command needs an argument");
        final String filename = castToString(args[0]);
        final BusRequest _req = req;

        addTask(new Runnable() { public void run() {
            try {
                if(status.pc == null) {
                    callShowOptionDialog(null, "Can't dump PC status: No PC present", "Error",
                        JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE, null, new String[]{"Dismiss"},
                            "Dismiss");
                    _req.doReturnL(false);
                    return;
                }
                OutputStream outb = new BufferedOutputStream(new FileOutputStream(filename));
                PrintStream out = new PrintStream(outb, false, "UTF-8");
                StatusDumper sd = new StatusDumper(out);
                status.pc.dumpStatus(sd);
                out.flush();
                outb.flush();
                out.close();
                System.err.println("Informational: Dumped " + sd.dumpedObjects() + " objects");
                _req.doReturnL(true);
            } catch(Exception e) {
                errorDialog(e, "Dumping status failed", null, "Dismiss");
                _req.doReturnL(false);
            }
        }}, "Dumping PC status...");
    }

    public void addDisk(BusRequest req, String cmd, Object[] args) throws Exception
    {
        if(args == null || args.length != 2)
            throw new IllegalArgumentException("Command needs two arguments");
        String name = castToString(args[0]);
        COWImage img;
        if(args[1] instanceof String)
            img = new COWImage(new ImageID(castToString(args[1])));
        else if(args[1] instanceof ImageID)
            img = new COWImage((ImageID)args[1]);
        else
            throw new IllegalArgumentException("Can't recognize type of image id parameter");
        img.setName(name);
        final COWImage _img = img;
        final BusRequest _req = req;
        addTask(new Runnable() { public void run() {
            try {
                if(status.pc == null) {
                    callShowOptionDialog(null, "Can't add new disk: No PC present", "Error",
                        JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE, null, new String[]{"Dismiss"},
                            "Dismiss");
                    _req.doReturnL(false);
                    return;
                }
                int idx = status.pc.getDisks().addDisk(_img);
                bus.invokeEvent("disk-added", new Object[]{idx});
                _req.doReturnL(true);
            } catch(Exception e) {
                errorDialog(e, "Adding disk failed", null, "Dismiss");
                _req.doReturnL(false);
            }
        }}, "Adding disk...");
    }

    private void saveStateCommon(BusRequest req, String cmd, Object[] args, boolean movieOnly) throws Exception
    {
        if(args == null || args.length != 1)
            throw new IllegalArgumentException("Command needs an argument");
        final String filename = castToString(args[0]);
        final BusRequest _req = req;
        final boolean _movieOnly = movieOnly;

        addTask(new Runnable() { public void run() {
            JRSRArchiveWriter writer = null;
            try {
                if(status.pc == null) {
                    callShowOptionDialog(null, "Can't save: No PC present", "Error",
                        JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE, null, new String[]{"Dismiss"},
                            "Dismiss");
                    _req.doReturnL(false);
                    return;
                }
                System.err.println("Informational: Savestating...");
                long times1 = System.currentTimeMillis();
                writer = new JRSRArchiveWriter(filename);
                PC.saveSavestate(writer, status, _movieOnly, uncompressedSave);
                renameFile(new File(filename), new File(filename + ".backup"));
                writer.close();
                long times2 = System.currentTimeMillis();
                System.err.println("Informational: Savestate complete (" + (times2 - times1) + "ms) on " + filename);
                _req.doReturnL(true);
            } catch(Exception e) {
                try { writer.rollback(); } catch(Exception f) {}
                errorDialog(e, "Saving failed", null, "Dismiss");
                _req.doReturnL(false);
            }
        }}, "Saving...");
    }

    private synchronized void waitRunnableOrTask()
    {
        while((!running || status.pc == null) && !isTaskRunnable()) {
            //If we were just running, signal the pause to PC.
            if(!running && wasRunning) {
                wasRunning = false;
            }
            //Try to wait for we to get a task or the emulation to resume.
            try {
                synchronized(this) {
                    //Check again to guard for race conditions.
                    if((running && status.pc != null) || isTaskRunnable())
                        continue;
                    if(temporaryPause && status.pc != null) {
                        running = true; //End the temporary pause.
                        continue;
                    }
                    String taskName = (status.pc != null) ? "Paused" : "No PC";
                    bus.invokeEvent("emustatus-changed", new Object[]{taskName});
                    //Okay, wait.
                    waiting = true;
                    notifyAll();
                    wait();
                    waiting = false;
                }
            } catch(Exception e) {
            }
        }
    }

    private void executionThreadRoutine()
    {
        boolean wasRunning = false;
        while(true) {   //We will be killed by JVM.
            waitRunnableOrTask();
            //If we have tasks to do, do them.
            while(true) {
                Runnable task = getTask();
                //Did we get a task?
                if(task == null)
                    break;
                //Yes, run the task.
                String taskName = getTaskName();
                bus.invokeEvent("emustatus-changed", new Object[]{taskName});
                task.run();
            }

            synchronized(this) {
                //If we were not running but now are, signal the resume to PC.
                if(running && !wasRunning) {
                    bus.invokeEvent("pc-start", null);
                    status.pc.start();
                    wasRunning = true;
                    bus.invokeEvent("emustatus-changed", new Object[]{"Running"});
                }
            }

            //Try to execute some instructions.
            try {
                status.pc.execute();
                //Did we hit trace trap and should stop?
                if(status.pc.getHitTraceTrap()) {
                    //Was the cause of stop a triple fault? If yes, show message.
                    if(status.pc.getAndClearTripleFaulted())
                        callShowOptionDialog(null, "CPU shut itself down due to triple fault. Rebooting the system.",
                            "Triple fault!", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE, null,
                            new String[]{"Dismiss"}, "Dismiss");
                    //Stop the emulation.
                    running = false;
                    bus.invokeEvent("pc-stop", null);
                    status.pc.stop();
                    wasRunning = false;
                    Clock sysClock = (Clock)status.pc.getComponent(Clock.class);
                    System.err.println("Notice: PC emulation stopped (at time sequence value " +
                        prettyPrintTime(sysClock.getTime()) + ")");
                    DisplayController dc = (DisplayController)status.pc.getComponent(DisplayController.class);
                    dc.getOutputDevice().holdOutput(sysClock.getTime());
                    if(status.pc != null) {
                        status.pc.getTraceTrap().clearTrapTime();
                        status.pc.getTraceTrap().getAndClearTrapActive();
                    }
                }
            } catch (Exception e) {
                running = false;
                Clock sysClock = (Clock)status.pc.getComponent(Clock.class);
                DisplayController dc = (DisplayController)status.pc.getComponent(DisplayController.class);
                dc.getOutputDevice().holdOutput(sysClock.getTime());
                errorDialog(e, "Hardware emulator internal error", null, "Dismiss");
                if(status.pc != null) {
                    status.pc.getTraceTrap().clearTrapTime();
                    status.pc.getTraceTrap().getAndClearTrapActive();
                }
                try {
                    running = false;
                    bus.invokeEvent("pc-stop", null);
                    status.pc.stop();
                    wasRunning = false;
                    System.err.println("Notice: PC emulation ABORTED (at time sequence value " +
                        prettyPrintTime(sysClock.getTime()) + ")");
                } catch (Exception f) {
                }
            }
        }
    }

    public String saveImage_help(String cmd, boolean brief)
    {
        if(brief)
            return "Dump disk image from system";
        System.err.println("Synopsis: " + cmd + " <filename> <index>");
        System.err.println("Dumps disk in index <index> to file <filename> in raw format.");
        System.err.println("<index> may be -1 (hda), -2 (hdb), -3 (hdc), -4 (hdd) if those");
        System.err.println("hard disk devices are present.");
        return null;
    }

    //No need to lock since everything interesting happens inside a task.
    public void saveImage(BusRequest req, String cmd, Object[] args) throws Exception
    {
        if(args == null || args.length != 2)
            throw new IllegalArgumentException("Command needs two arguments");
        final String chosen = castToString(args[0]);
        final int index = castToInt(args[1]);
        final BusRequest _req = req;

        addTask(new Runnable() { public void run() {
            try {
                if(status.pc == null) {
                    callShowOptionDialog(null, "Can't dump disk image: No PC present", "Error",
                        JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE, null, new String[]{"Dismiss"},
                            "Dismiss");
                    _req.doReturnL(false);
                    return;
                }
                COWImage img = null;
                if(index >= -4 && index <= -1)
                    try { img = status.pc.getDrives().getHardDrive(-1 - index); } catch(Exception e) {}
                else if(index >= 0)
                    img = status.pc.getDisks().lookupDisk(index);
                if(img == null)
                    throw new IOException("Trying to dump nonexistent disk");
                OutputStream outb = new BufferedOutputStream(new FileOutputStream(chosen));
                byte[] buf = new byte[512];
                int sectors = img.getTotalSectors();
                for(int i = 0; i < sectors; i++) {
                    img.read(i, buf, 1);
                    outb.write(buf);
                }
                outb.close();
                System.err.println("Informational: Dumped disk image (" + sectors + " sectors).");
                _req.doReturnL(true);
            } catch(Exception e) {
                errorDialog(e, "Dumping Image failed", null, "Dismiss");
                _req.doReturnL(false);
            }
        }}, "Dumping disk image...");
    }

    private static final int LOAD_STATE = 0;
    private static final int LOAD_REWIND = 1;
    private static final int LOAD_MOVIE = 2;

    public String loadState_help(String cmd, boolean brief)
    {
        if(brief)
            return "Load PC state or movie";
        System.err.println("Synopsis: " + cmd + " <filename> [<movieName>]");
        System.err.println("Loads PC state or movie from <filename>.");
        System.err.println("If <moviename> is specified, it is used as name of submovie");
        System.err.println("to load. If loading state or movie with single submovie, it");
        System.err.println("must be absent. If moviename is required, this returns the");
        System.err.println("list of candidates if it is absent.");
        return null;
    }

    public String loadStateSeekOnly_help(String cmd, boolean brief)
    {
        if(brief)
            return "Load PC state, preserving events";
        System.err.println("Synopsis: " + cmd + " <filename>");
        System.err.println("Loads PC state from <filename> preserving current movie.");
        return null;
    }

    public String loadMovie_help(String cmd, boolean brief)
    {
        if(brief)
            return "Load movie";
        System.err.println("Synopsis: " + cmd + " <filename> [<movieName>]");
        System.err.println("Loads movie from <filename>.");
        System.err.println("If <moviename> is specified, it is used as name of submovie");
        System.err.println("to load. If loading movie with single submovie, it");
        System.err.println("must be absent. If moviename is required, this returns the");
        System.err.println("list of candidates if it is absent.");
        return null;
    }

    public void loadState(BusRequest req, String cmd, Object[] args)
    {
        if(args == null || args.length < 1 || args.length > 2)
            throw new IllegalArgumentException("Command needs 1-2 arguments");
        String fileName = castToString(args[0]);
        String movieName = null;
        if(args.length == 2)
            movieName = castToString(args[1]);
        loadCore(req, fileName, movieName, LOAD_STATE);
    }

    public void loadStateSeekOnly(BusRequest req, String cmd, Object[] args)
    {
        if(args == null || args.length != 1)
            throw new IllegalArgumentException("Command needs an argument");
        String fileName = castToString(args[0]);
        loadCore(req, fileName, null, LOAD_REWIND);
    }

    public void loadMovie(BusRequest req, String cmd, Object[] args)
    {
        if(args == null || args.length < 1 || args.length > 2)
            throw new IllegalArgumentException("Command needs 1-2 arguments");
        String fileName = castToString(args[0]);
        String movieName = null;
        if(args.length == 2)
            movieName = castToString(args[1]);
        loadCore(req, fileName, movieName, LOAD_MOVIE);
    }

    //Returns TRUE if this is movie, FALSE if this is savestate.
    static boolean parseSubmovies(UnicodeInputStream lines, Set<String> choices, boolean force) throws IOException
    {
        String[] components = nextParseLine(lines);
        while(components != null) {
           //Savestateid is overridden in force (i.e. movie only) mode.
           if("SAVESTATEID".equals(components[0]) && !force) {
               choices.clear();
               return false;
           }
           if("INITIALSTATE".equals(components[0])) {
               if(components.length != 2)
                   throw new IOException("Bad " + components[0] + " line in header segment: " +
                       "expected 2 components, got " + components.length);
               choices.add(components[1]);
           }
           components = nextParseLine(lines);
        }
        return true;
    }

    public void loadCore(BusRequest req, String filename, String submovie, int mode)
    {
        final String _filename = filename;
        final String _submovie = submovie;
        final int _mode = mode;
        final BusRequest _req = req;
        addTask(new Runnable() { public void run() {
            JRSRArchiveReader reader = null;
            try {
                PC.PCFullStatus fullStatus = null;
                System.err.println("Informational: Loading a snapshot of JPC-RR");
                long times1 = System.currentTimeMillis();
                reader = new JRSRArchiveReader(_filename);

                //See if there are submovies.
                Set<String> submovies = new HashSet<String>();
                UnicodeInputStream lines = reader.readMember("header");
                boolean isMovie = parseSubmovies(lines, submovies, _mode == LOAD_MOVIE);
                lines.close();

                //No, you can't rewind to a movie.
                if(_mode == LOAD_REWIND && isMovie)
                    throw new IOException("No savestate to rewind to");

                //Is submovie spec ok?
                if(_submovie != null && submovies.isEmpty()) {
                    //No, submovie was specified but none can be selected.
                    _req.doReturnA(new Object[]{null});
                    return;
                }
                if(_submovie == null && !submovies.isEmpty()) {
                    //No, submovie wasn't specified but multiple can be selected.
                    _req.doReturnA(submovies.toArray());
                    return;
                }
                if(_submovie != null && !submovies.contains(_submovie)) {
                    //No, Bad submovie.
                    _req.doReturnA(submovies.toArray());
                    return;
                }
                //Submovie spec ok.

                //Do the actual load.
                fullStatus = PC.loadSavestate(reader, _mode == LOAD_REWIND, _mode == LOAD_MOVIE,
                    status, _submovie);

                //Cycle the output.
                DisplayController dc = (DisplayController)fullStatus.pc.getComponent(DisplayController.class);
                dc.getOutputDevice().holdOutput(fullStatus.pc.getTime());

                //Make the loadstate effective.
                synchronized(this) {
                    status = fullStatus;
                    bus.invokeEvent("pc-reconfigure", null);
                }
                try { reader.close(); } catch(Exception f) {}
                reader = null;
                long times2 = System.currentTimeMillis();
                System.err.println("Informational: Loadstate complete (" + (times2 - times1) + "ms).");
                _req.doReturnL(true);
            } catch(Exception e) {
                if(reader != null)
                    try { reader.close(); } catch(Exception f) {}
                errorDialog(e, "Loading state failed", null, "Dismiss");
                _req.doReturnL(false);
            }
        }}, "Loading state...");
    }



/*

    public void isRunning(BusRequest req, String cmd, Object[] args) {}
    public void getLoadedRerecordCount(BusRequest req, String cmd, Object[] args) {}
*/
}
