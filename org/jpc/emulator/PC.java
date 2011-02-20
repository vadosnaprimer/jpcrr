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

package org.jpc.emulator;

import org.jpc.emulator.motherboard.*;
import org.jpc.emulator.memory.*;
import org.jpc.emulator.pci.peripheral.*;
import org.jpc.emulator.pci.*;
import org.jpc.emulator.peripheral.*;
import org.jpc.emulator.processor.*;
import org.jpc.diskimages.DiskImageSet;
import org.jpc.jrsr.JRSRArchiveReader;
import org.jpc.jrsr.JRSRArchiveWriter;
import org.jpc.jrsr.UnicodeInputStream;
import org.jpc.jrsr.UnicodeOutputStream;
import org.jpc.jrsr.FourToFiveDecoder;
import org.jpc.jrsr.FourToFiveEncoder;
import org.jpc.output.Output;
import org.jpc.output.OutputChannelDummy;
import org.jpc.output.OutputChannelGameinfo;
import org.jpc.images.BaseImage;
import org.jpc.images.BaseImageFactory;
import org.jpc.images.COWImage;
import org.jpc.images.ImageID;
import java.io.*;
import java.util.*;
import java.util.zip.*;
import java.lang.reflect.*;
import java.security.MessageDigest;
import org.jpc.emulator.memory.codeblock.CodeBlockManager;

import static org.jpc.Misc.arrayToString;
import static org.jpc.Misc.stringToArray;
import static org.jpc.Misc.nextParseLine;
import static org.jpc.Misc.randomHexes;

/**
 * This class represents the emulated PC as a whole, and holds references
 * to its main hardware components.
 * @author Chris Dennis
 * @author Ian Preston
 */
public class PC implements SRDumpable
{

    public int sysRAMSize;
    public int cpuClockDivider;
    private PCHardwareInfo hwInfo;

    public static volatile boolean compile = true;

    private final Processor processor;
    private final PhysicalAddressSpace physicalAddr;
    private final LinearAddressSpace linearAddr;
    private final Clock vmClock;
    private final Set<HardwareComponent> parts;
    private final CodeBlockManager manager;
    private DiskImageSet images;
    private final ResetButton brb;
    private final DiskChanger diskChanger;
    private final EventPoller poller;

    private Output outputs;
    private OutputChannelDummy dummyChannel;
    private OutputChannelGameinfo gameChannel;

    private TraceTrap traceTrap;
    private boolean hitTraceTrap;
    private boolean tripleFaulted;
    private boolean rebootRequest;

    private boolean hasCDROM;

    public Output getOutputs()
    {
        return outputs;
    }

    public HardwareComponent loadHardwareModule(String name, String params) throws IOException
    {
        Class<?> module;
        if("".equals(params))
            params = null;

        try {
            module = Class.forName(name);
        } catch(Exception e) {
            throw new IOException("Unable to find extension module \"" + name + "\".");
        }
        if(!HardwareComponent.class.isAssignableFrom(module)) {
            throw new IOException("Extension module \"" + name + "\" is not valid hardware module.");
        }
        HardwareComponent c;
        try {
            boolean x = params.equals("");  //Intentionally cause NPE if params is null.
            x = x & x;    //Silence warning.
            Constructor<?> cc = module.getConstructor(String.class);
            c = (HardwareComponent)cc.newInstance(params);
        } catch(NullPointerException e) {
            try {
                Constructor<?> cc = module.getConstructor();
                c = (HardwareComponent)cc.newInstance();
            } catch(InvocationTargetException f) {
                Throwable e2 = f.getCause();
                //If the exception is something unchecked, just pass it through.
                if(e2 instanceof RuntimeException)
                    throw (RuntimeException)e2;
                if(e2 instanceof Error) {
                    IOException ne =  new IOException("Error while invoking constructor: " + e2);
                    ne.setStackTrace(e2.getStackTrace());  //Copy stack trace.
                    throw ne;
                }
                //Also pass IOException through.
                if(e2 instanceof IOException)
                    throw (IOException)e2;
                //What the heck is that?
                IOException ne = new IOException("Unknown exception while invoking module constructor: " + e2);
                ne.setStackTrace(e2.getStackTrace());  //Copy stack trace.
                throw ne;
            } catch(Exception f) {
                throw new IOException("Unable to instantiate extension module \"" + name + "\".");
            }
        } catch(InvocationTargetException e) {
            Throwable e2 = e.getCause();
            //If the exception is something unchecked, just pass it through.
            if(e2 instanceof RuntimeException)
                throw (RuntimeException)e2;
            if(e2 instanceof Error) {
                IOException ne =  new IOException("Error while invoking constructor: " + e2);
                ne.setStackTrace(e2.getStackTrace());  //Copy stack trace.
                throw ne;
            }
            //Also pass IOException through.
            if(e2 instanceof IOException)
                throw (IOException)e2;
            //What the heck is that?
            IOException ne = new IOException("Unknown exception while invoking module constructor: " + e2);
            ne.setStackTrace(e2.getStackTrace());  //Copy stack trace.
            throw ne;
        } catch(Exception f) {
            throw new IOException("Unable to instantiate extension module \"" + name + "\": " + f.getMessage());
        }

        return c;
    }

    public PC(PCHardwareInfo hw) throws IOException
    {
        FloppyController fdc;
        COWImage hda = imageFor(hw.hdaID);
        COWImage hdb = imageFor(hw.hdbID);
        COWImage hdc = imageFor(hw.hdcID);
        COWImage hdd = imageFor(hw.hddID);

        DriveSet drives = new DriveSet(hw.bootType, hda, hdb, hdc, hdd);
        parts = new LinkedHashSet<HardwareComponent>();

        cpuClockDivider = hw.cpuDivider;
        sysRAMSize = hw.memoryPages * 4096;
        vmClock = new Clock();

        if(hw.hwModules != null)
            for(Map.Entry<String,Set<String>> e : hw.hwModules.entrySet()) {
                String name = e.getKey();
                for(String params : e.getValue()) {
                    System.err.println("Informational: Loading module \"" + name + "\".");
                    parts.add(loadHardwareModule(name, params));
                }
            }

        parts.add(vmClock);
        System.err.println("Informational: Creating Outputs...");
        outputs = new Output();
        dummyChannel = new OutputChannelDummy(outputs, "<DUMMY>");
        gameChannel = new OutputChannelGameinfo(outputs, "<GAMEINFO>");
        System.err.println("Informational: Creating CPU...");
        processor = new Processor(vmClock, cpuClockDivider);
        parts.add(processor);
        manager = new CodeBlockManager();

        System.err.println("Informational: Creating Reset Button...");
        brb = new ResetButton(this);
        parts.add(brb);

        System.err.println("Informational: Creating Disk Changer..");
        diskChanger = new DiskChanger(this);
        parts.add(diskChanger);

        System.err.println("Informational: Creating physical address space...");
        physicalAddr = new PhysicalAddressSpace(manager, sysRAMSize);
        parts.add(physicalAddr);

        System.err.println("Informational: Creating linear address space...");
        linearAddr = new LinearAddressSpace();
        parts.add(linearAddr);

        parts.add(drives);

        //Motherboard
        System.err.println("Informational: Creating I/O port handler...");
        parts.add(new IOPortHandler());
        System.err.println("Informational: Creating IRQ controller...");
        parts.add(new InterruptController());

        System.err.println("Informational: Creating primary DMA controller...");
        parts.add(new DMAController(false, true));
        System.err.println("Informational: Creating secondary DMA controller...");
        parts.add(new DMAController(false, false));

        System.err.println("Informational: Creating real time clock...");
        parts.add(new RTC(0x70, 8, sysRAMSize, hw.initRTCTime));
        System.err.println("Informational: Creating interval timer...");
        parts.add(new IntervalTimer(0x40, 0));
        System.err.println("Informational: Creating A20 Handler...");
        parts.add(new GateA20Handler());
        this.images = hw.images;

        //Peripherals
        System.err.println("Informational: Creating IDE interface...");
        PIIX3IDEInterface ide;
        parts.add(ide = new PIIX3IDEInterface());
        System.err.println("Informational: Creating Sound Card...");
        parts.add(new SoundCard(hw.scConfigWord, hw.scPCMIO, hw.scPCMIRQ, hw.scPCMLDMA, hw.scPCMHDMA,
            hw.scUARTIO, hw.scUARTIRQ));
        System.err.println("Informational: Creating Keyboard...");
        parts.add(new Keyboard());
        System.err.println("Informational: Creating floppy disk controller...");
        parts.add(fdc = new FloppyController());
        System.err.println("Informational: Creating PC speaker...");
        parts.add(new PCSpeaker(outputs, "org.jpc.emulator.peripheral.PCSpeaker-0"));

        //PCI Stuff
        System.err.println("Informational: Creating PCI Host Bridge...");
        parts.add(new PCIHostBridge());
        System.err.println("Informational: Creating PCI-to-ISA Bridge...");
        parts.add(new PCIISABridge());
        System.err.println("Informational: Creating PCI Bus...");
        parts.add(new PCIBus());

        //BIOSes
        System.err.println("Informational: Creating system BIOS...");
        parts.add(new SystemBIOS(hw.biosID));
        System.err.println("Informational: Creating VGA BIOS...");
        parts.add(new VGABIOS(hw.vgaBIOSID));
        System.err.println("Informational: Creating trace trap...");
        parts.add(traceTrap = new TraceTrap());
        physicalAddr.setPage0Hack(traceTrap);   //Mark page 0.

        System.err.println("Informational: Creating event poller...");
        poller = new EventPoller(vmClock);

        System.err.println("Informational: Creating hardware info...");
        hwInfo = (PCHardwareInfo)hw.clone();

        DisplayController displayController = null;
        for(HardwareComponent c : parts)
            if(c instanceof DisplayController)
                if(displayController == null)
                    displayController = (DisplayController)c;
                else
                    throw new IOException("Can not have multiple display controllers: \"" +
                        c.getClass().getName() + "\" and \"" + displayController.getClass().getName() +
                        "\" are both display controllers.");
        if(displayController == null) {
            System.err.println("Informational: Creating VGA card...");
            VGACard card = new VGACard();
            parts.add(card);
            displayController = card;
        }
        displayController.getOutputDevice().setSink(outputs, "<VIDEO>");

        System.err.println("Informational: Creating sound outputs...");
        Map<String, Integer> numBase = new HashMap<String, Integer>();
        for(HardwareComponent c : parts) {
            if(!(c instanceof SoundOutputDevice))
                continue;
            SoundOutputDevice c2 = (SoundOutputDevice)c;
            int channels = c2.requestedSoundChannels();
            int base = 0;
            if(numBase.containsKey(c.getClass().getName()))
                base = numBase.get(c.getClass().getName()).intValue();
            for(int i = 0; i < channels; i++) {
                String outname = c.getClass().getName() + "-" + (base + i);
                c2.soundChannelCallback(outputs, outname);
            }
            numBase.put(c.getClass().getName(), base + channels);
        }

        System.err.println("Informational: Setting system flags...");
        Set<String> found = new HashSet<String>();
        for(HardwareComponent part : parts) {
            Class<?> clazz = part.getClass();
            Field[] fields = clazz.getDeclaredFields();
            for(Field field : fields)
                if(field.getName().startsWith("SYSFLAG_")) {
                    String name = field.getName().substring(8);
                    found.add(name);
                    Boolean b = null;
                    Integer i = null;
                    if(hw.booleanOptions != null)
                        b = hw.booleanOptions.get(name);
                    if(hw.intOptions != null)
                        i = hw.intOptions.get(name);
                    if(b != null && b.booleanValue()) {
                        try {
                            field.setBoolean(part, true);
                        } catch(IllegalAccessException e) {
                            throw new IOException("System flag field for " + name + " is of wrong type");
                        }
                    }
                    if(i != null && i.intValue() != 0) {
                        try {
                            field.setInt(part, i.intValue());
                        } catch(IllegalAccessException e) {
                            throw new IOException("System flag field for " + name + " is of wrong type");
                        }
                    }
                }
        }
        if(hw.booleanOptions != null)
            for(Map.Entry<String, Boolean> b : hw.booleanOptions.entrySet())
                if(!found.contains(b.getKey()))
                    throw new IOException("Unrecognized system flag " + b.getKey() + " encountered");
        if(hw.intOptions != null)
            for(Map.Entry<String, Integer> i : hw.intOptions.entrySet())
                if(!found.contains(i.getKey()))
                    throw new IOException("Unrecognized system flag " + i.getKey() + " encountered");

        System.err.println("Informational: Configuring components...");
        if(!configure())
            throw new IllegalStateException("Can't initialize components (cyclic dependency?)");
        hasCDROM = ide.hasCD();
        System.err.println("Informational: PC initialization done.");

        fdc.changeDisk(getDisks().lookupDisk(hw.initFDAIndex), 0);
        fdc.changeDisk(getDisks().lookupDisk(hw.initFDBIndex), 1);

        if(hasCDROM && hw.initCDROMIndex >= 0)
            try {
                ide.swapCD(getDisks().lookupDisk(hw.initCDROMIndex));
            } catch(Exception e) {
                System.err.println("Warning: Unable to change disk in CD-ROM drive");
            }

    }


    public boolean getHasCDROM()
    {
        return hasCDROM;
    }

    public DriveSet getDrives()
    {
        for(HardwareComponent c2 : parts)
            if(c2 instanceof DriveSet)
                return (DriveSet)c2;
        return null;
    }

    public void dumpStatusPartial(StatusDumper output)
    {
        output.println("\tsysRAMSize " + sysRAMSize + " cpuClockDivider " + cpuClockDivider);
        output.println("\ttripleFaulted " + tripleFaulted + " hasCDROM " + hasCDROM);
        //hitTraceTrap not printed here.
        output.println("\tprocessor <object #" + output.objectNumber(processor) + ">"); if(processor != null) processor.dumpStatus(output);
        output.println("\tphysicalAddr <object #" + output.objectNumber(physicalAddr) + ">"); if(physicalAddr != null) physicalAddr.dumpStatus(output);
        output.println("\tlinearAddr <object #" + output.objectNumber(linearAddr) + ">"); if(linearAddr != null) linearAddr.dumpStatus(output);
        output.println("\tvmClock <object #" + output.objectNumber(vmClock) + ">"); if(vmClock != null) vmClock.dumpStatus(output);
        output.println("\timages <object #" + output.objectNumber(images) + ">"); if(images != null) images.dumpStatus(output);
        output.println("\ttraceTrap <object #" + output.objectNumber(traceTrap) + ">"); if(traceTrap != null) traceTrap.dumpStatus(output);
        output.println("\thwInfo <object #" + output.objectNumber(hwInfo) + ">"); if(hwInfo != null) hwInfo.dumpStatus(output);
        output.println("\toutputs <object #" + output.objectNumber(outputs) + ">"); if(outputs != null) outputs.dumpStatus(output);
        output.println("\tbrb <object #" + output.objectNumber(brb) + ">"); if(brb != null) brb.dumpStatus(output);
        output.println("\tpoller <object #" + output.objectNumber(poller) + ">"); if(poller != null) poller.dumpStatus(output);
        output.println("\tdummyChannel <object #" + output.objectNumber(dummyChannel) + ">"); if(dummyChannel != null) dummyChannel.dumpStatus(output);
        output.println("\tgameChannel <object #" + output.objectNumber(gameChannel) + ">"); if(gameChannel != null) gameChannel.dumpStatus(output);

        int i = 0;
        for(HardwareComponent part : parts) {
            output.println("\tparts[" + i++ + "] <object #" + output.objectNumber(part) + ">");
            if(part != null) part.dumpStatus(output);
        }

    }

    public long getTime()
    {
        return vmClock.getTime();
    }

    public PC(SRLoader input) throws IOException
    {
        input.objectCreated(this);
        hasCDROM = input.loadBoolean();
        sysRAMSize = input.loadInt();
        cpuClockDivider = input.loadInt();
        processor = (Processor)input.loadObject();
        physicalAddr = (PhysicalAddressSpace)input.loadObject();
        linearAddr = (LinearAddressSpace)input.loadObject();
        vmClock = (Clock)input.loadObject();
        images = (DiskImageSet)(input.loadObject());
        traceTrap = (TraceTrap)input.loadObject();
        physicalAddr.setPage0Hack(traceTrap);   //Mark page 0.
        manager = (CodeBlockManager)input.loadObject();
        hwInfo = (PCHardwareInfo)(input.loadObject());
        outputs = (Output)(input.loadObject());
        hitTraceTrap = input.loadBoolean();
        tripleFaulted = input.loadBoolean();

        boolean present = input.loadBoolean();
        parts = new LinkedHashSet<HardwareComponent>();
        while(present) {
            parts.add((HardwareComponent)input.loadObject());
            present = input.loadBoolean();
        }
        rebootRequest = input.loadBoolean();
        brb = (ResetButton)input.loadObject();
        diskChanger = (DiskChanger)input.loadObject();

        poller = (EventPoller)(input.loadObject());
        dummyChannel = (OutputChannelDummy)input.loadObject();
        if(!input.objectEndsHere())
            gameChannel = (OutputChannelGameinfo)input.loadObject();
        else
            gameChannel = new OutputChannelGameinfo(outputs, "<GAMEINFO>");
    }

    public void dumpStatus(StatusDumper output)
    {
        if(output.dumped(this))
            return;

        output.println("#" + output.objectNumber(this) + ": PC:");
        dumpStatusPartial(output);
        output.endObject();
    }

    public PCHardwareInfo getHardwareInfo()
    {
        return hwInfo;
    }

    public boolean getAndClearTripleFaulted()
    {
        boolean flag = tripleFaulted;
        tripleFaulted = false;
        return flag;
    }


    public void dumpSRPartial(SRDumper output) throws IOException
    {
        output.dumpBoolean(hasCDROM);
        output.dumpInt(sysRAMSize);
        output.dumpInt(cpuClockDivider);
        output.dumpObject(processor);
        output.dumpObject(physicalAddr);
        output.dumpObject(linearAddr);
        output.dumpObject(vmClock);
        output.dumpObject(images);
        output.dumpObject(traceTrap);
        output.dumpObject(manager);
        output.dumpObject(hwInfo);
        output.dumpObject(outputs);
        output.dumpBoolean(hitTraceTrap);
        output.dumpBoolean(tripleFaulted);
        for(HardwareComponent part : parts) {
            output.dumpBoolean(true);
            output.dumpObject(part);
        }
        output.dumpBoolean(false);
        output.dumpBoolean(rebootRequest);
        output.dumpObject(brb);
        output.dumpObject(diskChanger);

        output.dumpObject(poller);
        output.dumpObject(dummyChannel);
        output.dumpObject(gameChannel);
    }

    public static Map<String, Set<String>> parseHWModules(String moduleString) throws IOException
    {
        Map<String, Set<String>> ret = new LinkedHashMap<String, Set<String>>();

        while(moduleString != null && !moduleString.equals("")) {
            String currentModule;
            int parenDepth = 0;
            int nameEnd = -1;
            int paramsStart = -1;
            int paramsEnd = -1;
            int stringLen = moduleString.length();
            boolean requireNextSep = false;

            for(int i = 0; true; i++) {
                int cp;
                if(i < stringLen)
                    cp = moduleString.codePointAt(i);
                else if(parenDepth == 0)
                    cp = ',';    //Hack, consider last character seperator.
                else
                    throw new IOException("Error in module string: unclosed '('.");
                if(cp >= 0x10000)
                     i++; //Skip the next surrogate.
                if((cp >= 0xD800 && cp < 0xE000) || ((cp & 0xFFFE) == 0xFFFE) || (cp >>> 16) > 16 || cp < 0)
                    throw new IOException("Error In module string: invalid Unicode character.");
                if(requireNextSep && cp != ',')
                        throw new IOException("Error in module string: Expected ',' after ')' closing parameter list.");
                else if(cp == ',' && i == 0)
                        throw new IOException("Error in module string: Blank module name not allowed.");
                else if(cp == '(') {
                    if(parenDepth == 0) {
                        paramsStart = i + 1;
                        nameEnd = i - 1;
                    }
                    parenDepth++;
                } else if(cp == ')') {
                    if(parenDepth == 0)
                        throw new IOException("Error in module string: Unpaired ')'.");
                    else if(parenDepth == 1) {
                        paramsEnd = i - 1;
                        requireNextSep = true;
                    }
                    parenDepth--;
                } else if(cp == ',' && parenDepth == 0) {
                    if(nameEnd < 0)
                        nameEnd = i - 1;
                    currentModule = moduleString.substring(0, i);
                    if(i < stringLen ) {
                        moduleString = moduleString.substring(i + 1);
                        if(moduleString.equals(""))
                            throw new IOException("Error in module string: Blank module name not allowed.");
                    } else
                        moduleString = "";
                    break;
                }
            }

            String name = currentModule.substring(0, nameEnd + 1);
            String params = null;
            if(paramsStart >= 0)
                params = currentModule.substring(paramsStart, paramsEnd + 1);

            if(ret.containsKey(name))
                ret.get(name).add(params);
            else {
                Set<String> foo = new LinkedHashSet<String>();
                foo.add(params);
                ret.put(name, foo);
            }
        }

        return ret;
    }


    private static COWImage imageFor(ImageID name) throws IOException
    {
        if(name == null)
            return null;
        return new COWImage(name);
    }

    /**
     * Starts this PC's attached clock instance.
     */
    public void start()
    {
        vmClock.resume();
    }

    /**
     * Stops this PC's attached clock instance
     */
    public void stop()
    {
        dummyChannel.addFrameDummy(vmClock.getTime());
        vmClock.pause();
    }

    /**
     * Inserts the specified floppy disk into the drive identified.
     * @param disk new floppy disk to be inserted.
     * @param index drive which the disk is inserted into.
     */
    private void changeFloppyDisk(COWImage disk, int index) throws IOException
    {
        ((FloppyController)getComponent(FloppyController.class)).changeDisk(disk, index);
    }

    public void changeFloppyDisk(int driveIndex, int diskIndex) throws IOException
    {
        diskChanger.changeFloppyDisk(driveIndex, diskIndex);
    }

    public void wpFloppyDisk(int diskIndex, boolean turnOn) throws IOException
    {
        diskChanger.wpFloppyDisk(diskIndex, turnOn);
    }

    public static class DiskChanger extends AbstractHardwareComponent implements SRDumpable, EventDispatchTarget
    {
        private EventRecorder eRecorder;     //Not saved.
        private PC upperBackref;
        private int currentDriveA;           //Not saved.
        private int currentDriveB;           //Not saved.
        private int currentCDROM;            //Not saved.
        private Set<Integer> usedDisks;      //Not saved.

        private void checkFloppyChange(int driveIndex, int diskIndex) throws IOException
        {
            if(driveIndex == 2 && !upperBackref.hasCDROM)
                throw new IOException("No CD-ROM drive available");
            if(diskIndex < -1)
                throw new IOException("Illegal disk number");
            COWImage disk = upperBackref.images.lookupDisk(diskIndex);
            if(driveIndex < 0 || driveIndex > 2)
                throw new IOException("Illegal drive number");
            if(diskIndex >= 0 && (diskIndex == currentDriveA || diskIndex == currentDriveB))
                throw new IOException("Specified disk is already in some drive");
            if(diskIndex < 0 && driveIndex == 0 && currentDriveA < 0)
                throw new IOException("No disk present in drive A");
            if(diskIndex < 0 && driveIndex == 1 && currentDriveB < 0)
                throw new IOException("No disk present in drive B");
            if(diskIndex > 0 && driveIndex < 2 && (disk == null || disk.getType() != BaseImage.Type.FLOPPY))
                throw new IOException("Attempt to put non-floppy into drive A or B");
            if(diskIndex > 0 && driveIndex == 2 && (disk == null || disk.getType() != BaseImage.Type.CDROM))
                throw new IOException("Attempt to put non-CDROM into CDROM drive");
            if(diskIndex > 0)
                usedDisks.add(new Integer(diskIndex));
        }

        private void checkFloppyWP(int diskIndex, boolean turnOn) throws IOException
        {
            if(diskIndex < 0)
                throw new IOException("Illegal floppy disk number");
            if(diskIndex == currentDriveA || diskIndex == currentDriveB)
                throw new IOException("Can not manipulate WP of disk in drive");
            COWImage disk = upperBackref.images.lookupDisk(diskIndex);
            if(disk == null || disk.getType() != BaseImage.Type.FLOPPY)
                throw new IOException("Can not manipulate WP of non-floppy disk");
        }

        public synchronized void changeFloppyDisk(int driveIndex, int diskIndex) throws IOException
        {
            checkFloppyChange(driveIndex, diskIndex);
            upperBackref.images.lookupDisk(diskIndex);
            try {
                if(driveIndex == 0)
                    eRecorder.addEvent(-1, getClass(), new String[]{"FDA", "" + diskIndex});
                else if(driveIndex == 1)
                    eRecorder.addEvent(-1, getClass(), new String[]{"FDB", "" + diskIndex});
                else if(driveIndex == 2)
                    eRecorder.addEvent(-1, getClass(), new String[]{"CDROM", "" + diskIndex});
            } catch(Exception e) {}
        }

        public synchronized void wpFloppyDisk(int diskIndex, boolean turnOn) throws IOException
        {
            checkFloppyWP(diskIndex, turnOn);
            COWImage disk = upperBackref.images.lookupDisk(diskIndex);
            try {
                if(turnOn && !disk.isReadOnly())
                    eRecorder.addEvent(-1, getClass(), new String[]{"WRITEPROTECT", "" + diskIndex});
                else if(!turnOn && disk.isReadOnly())
                    eRecorder.addEvent(-1, getClass(), new String[]{"WRITEUNPROTECT", "" + diskIndex});
            } catch(Exception e) {}
        }

        public void doEvent(long timeStamp, String[] args, int level) throws IOException
        {
            if(args == null || args.length != 2)
                throw new IOException("Invalid disk event parameters");
            int disk;
            try {
                disk = Integer.parseInt(args[1]);
            } catch(Exception e) {
                throw new IOException("Invalid disk number");
            }
            usedDisks.add(disk);
            COWImage diskImg = upperBackref.images.lookupDisk(disk);

            if("FDA".equals(args[0])) {
                if(level <= EventRecorder.EVENT_STATE_EFFECT) {
                    checkFloppyChange(0, disk);
                    currentDriveA = disk;
                }
                if(level == EventRecorder.EVENT_EXECUTE)
                    upperBackref.changeFloppyDisk(diskImg, 0);
            } else if("FDB".equals(args[0])) {
                if(level <= EventRecorder.EVENT_STATE_EFFECT) {
                    checkFloppyChange(1, disk);
                    currentDriveB = disk;
                }
                if(level == EventRecorder.EVENT_EXECUTE)
                    upperBackref.changeFloppyDisk(diskImg, 1);
            } else if("CDROM".equals(args[0])) {
                if(level <= EventRecorder.EVENT_STATE_EFFECT) {
                    checkFloppyChange(2, disk);
                    currentCDROM = disk;
                }
                if(level == EventRecorder.EVENT_EXECUTE)
                    try {
                        PIIX3IDEInterface ide = (PIIX3IDEInterface)upperBackref.getComponent(PIIX3IDEInterface.class);
                        ide.swapCD(diskImg);
                    } catch(Exception e) {
                        System.err.println("Warning: Unable to change disk in CD-ROM drive");
                    }
            } else if("WRITEPROTECT".equals(args[0])) {
                if(level <= EventRecorder.EVENT_STATE_EFFECT)
                    checkFloppyWP(disk, true);
                if(level == EventRecorder.EVENT_EXECUTE)
                    diskImg.setReadOnly(true);
            } else if("WRITEUNPROTECT".equals(args[0])) {
                if(level <= EventRecorder.EVENT_STATE_EFFECT)
                    checkFloppyWP(disk, false);
                if(level == EventRecorder.EVENT_EXECUTE)
                    diskImg.setReadOnly(false);
            } else
                throw new IOException("Invalid disk event type");
        }

        public void startEventCheck()
        {
            currentDriveA = upperBackref.hwInfo.initFDAIndex;
            currentDriveB = upperBackref.hwInfo.initFDBIndex;
            currentCDROM = upperBackref.hwInfo.initCDROMIndex;
            usedDisks = new HashSet<Integer>();
            if(currentDriveA >= 0)
                usedDisks.add(currentDriveA);
            if(currentDriveB >= 0)
                usedDisks.add(currentDriveB);
            if(currentCDROM >= 0)
                usedDisks.add(currentCDROM);
        }

        Set<Integer> usedDiskSet()
        {
            return usedDisks;
        }

        public void endEventCheck() throws IOException
        {
            //Nothing to do.
        }

        public DiskChanger(PC pc)
        {
            upperBackref = pc;
        }

        public DiskChanger(SRLoader input) throws IOException
        {
            super(input);
            upperBackref = (PC)input.loadObject();
        }

        public void dumpSRPartial(SRDumper output) throws IOException
        {
            super.dumpSRPartial(output);
            output.dumpObject(upperBackref);
        }

        public long getEventTimeLowBound(long stamp, String[] args) throws IOException
        {
            return -1;  //No timing constraints.
        }

        public void setEventRecorder(EventRecorder recorder)
        {
            eRecorder = recorder;
        }

        public void dumpStatus(StatusDumper output)
        {
            if(output.dumped(this))
                return;

            output.println("#" + output.objectNumber(this) + ": DiskChanger:");
            output.endObject();
        }
    }


    public DiskImageSet getDisks()
    {
        return images;
    }

    private boolean configure() {
        boolean fullyInitialised;
        int count = 0;
        do {
            fullyInitialised = true;
            for(HardwareComponent outer : parts) {
                if(outer.initialised())
                    continue;

                for(HardwareComponent inner : parts)
                    outer.acceptComponent(inner);

                fullyInitialised &= outer.initialised();
            }
            count++;
        } while((fullyInitialised == false) && (count < 100));

        if(!fullyInitialised) {
            for(HardwareComponent hwc : parts)
                if(!hwc.initialised())
                    System.err.println("Error: Component of type " + hwc.getClass() + " failed to initialize.");
            System.err.println("Critical error: PC component initialization failed.");
            return false;
        }

        for(HardwareComponent hwc : parts)
            if(hwc instanceof PCIBus)
                ((PCIBus)hwc).biosInit();

        return true;
    }

    public void setFPUHack()
    {
        physicalAddr.setFPUHack();
    }

    public void setVGADrawHack()
    {
        HardwareComponent displayController = getComponent(VGACard.class);
        if(displayController != null)
            ((VGACard)displayController).setVGADrawHack();
    }

    public void setVGAScroll2Hack()
    {
        HardwareComponent displayController = getComponent(VGACard.class);
        if(displayController != null)
            ((VGACard)displayController).setVGAScroll2Hack();
    }

    /**
     * Reset this PC back to its initial state.
     * <p>
     * This is roughly equivalent to a hard-reset (power down-up cycle).
     */
    protected void reset()
    {
        for(HardwareComponent hwc : parts)
            hwc.reset();
        configure();
    }

    public void reboot()
    {
        brb.reboot();
    }

    public static class ResetButton extends AbstractHardwareComponent implements SRDumpable, EventDispatchTarget
    {
        private EventRecorder eRecorder;    //Not saved.
        private PC upperBackref;

        public EventRecorder getRecorder()
        {
            return eRecorder;
        }

        public void reboot()
        {
            try {
                eRecorder.addEvent(-1, getClass(), null);
            } catch(Exception e) {}
        }

        public void startEventCheck()
        {
            //No state.
        }

        public void doEvent(long timeStamp, String[] args, int level) throws IOException
        {
            if(args != null)
                throw new IOException("Invalid reboot event");
            if(level == EventRecorder.EVENT_EXECUTE) {
                upperBackref.processor.eflagsMachineHalt = true;
                upperBackref.rebootRequest = true;
            }
        }

        public void endEventCheck() throws IOException
        {
        }

        public ResetButton(PC pc)
        {
            upperBackref = pc;
        }

        public ResetButton(SRLoader input) throws IOException
        {
            super(input);
            upperBackref = (PC)input.loadObject();
        }

        public void dumpSRPartial(SRDumper output) throws IOException
        {
            super.dumpSRPartial(output);
            output.dumpObject(upperBackref);
        }

        public long getEventTimeLowBound(long stamp, String[] args) throws IOException
        {
            return -1;  //No timing constraints.
        }

        public void setEventRecorder(EventRecorder recorder)
        {
            eRecorder = recorder;
        }

        public void dumpStatus(StatusDumper output)
        {
            if(output.dumped(this))
                return;

            output.println("#" + output.objectNumber(this) + ": ResetButton:");
            output.endObject();
        }
    }

    /**
     * Get an subclass of <code>cls</code> from this instance's parts list.
     * <p>
     * If <code>cls</code> is not assignment compatible with <code>HardwareComponent</code>
     * then this method will return null immediately.
     * @param cls component type required.
     * @return an instance of class <code>cls</code>, or <code>null</code> on failure
     */
    public HardwareComponent getComponent(Class<?> cls)
    {
        for(HardwareComponent hwc : parts)
            if(cls.isInstance(hwc))
                return hwc;

        return null;
    }

    public Set<HardwareComponent> allComponents()
    {
        return parts;
    }

    /**
     * Gets the processor instance associated with this PC.
     * @return associated processor instance.
     */
    public Processor getProcessor()
    {
        return processor;
    }

    /**
     * Execute an arbitrarily large amount of code on this instance.
     * <p>
     * This method will execute continuously until there is either a mode switch,
     * or a unspecified large number of instructions have completed.  It should
     * never run indefinitely.
     * @return total number of x86 instructions executed.
     */
    public final int execute()
    {
        if(processor.isProtectedMode())
            if(processor.isVirtual8086Mode())
                return executeVirtual8086();
            else
                return executeProtected();
        else
            return executeReal();
    }

    public final int executeReal()
    {
        int x86Count = 0;

        if(rebootRequest) {
            reset();
            rebootRequest = false;
        }

        try {
            for(int i = 0; i < 100; i++) {
                int block;
                try {
                    block = physicalAddr.executeReal(processor, processor.getInstructionPointer());
                } catch(org.jpc.emulator.processor.Processor.TripleFault e) {
                    reset();      //Reboot the system to get the CPU back online.
                    hitTraceTrap = true;
                    tripleFaulted = true;
                    break;
                }
                x86Count += block;
                processor.instructionsExecuted += block;
                //Don't call this on aborted blocks. Doing so is probably good source of desyncs.
                if(!processor.eflagsLastAborted)
                    processor.processRealModeInterrupts(1);
                if(traceTrap.getAndClearTrapActive()) {
                    hitTraceTrap = true;
                    break;
                }
                if(rebootRequest) {
                    reset();
                    rebootRequest = false;
                    break;
                }
            }
        } catch (ProcessorException p) {
             processor.handleRealModeException(p);
        } catch (ModeSwitchException e) {
            //System.err.println("Informational: CPU switching modes: " + e.toString());
        }
        return x86Count;
    }

    public TraceTrap getTraceTrap()
    {
        return traceTrap;
    }

    public boolean getHitTraceTrap()
    {
        boolean tmp = hitTraceTrap;
        hitTraceTrap = false;
        return tmp;
    }

    public final int executeProtected()
    {
        int x86Count = 0;

        if(rebootRequest) {
            reset();
            rebootRequest = false;
        }

        try {
            for(int i = 0; i < 100; i++) {
                int block;
                try {
                    block= linearAddr.executeProtected(processor, processor.getInstructionPointer());
                } catch(org.jpc.emulator.processor.Processor.TripleFault e) {
                    reset();      //Reboot the system to get the CPU back online.
                    hitTraceTrap = true;
                    tripleFaulted = true;
                    break;
                }
                x86Count += block;
                processor.instructionsExecuted += block;
                //Don't call this on aborted blocks. Doing so is probably good source of desyncs.
                if(!processor.eflagsLastAborted)
                    processor.processProtectedModeInterrupts(1);
                if(traceTrap.getAndClearTrapActive()) {
                    hitTraceTrap = true;
                    break;
                }
                if(rebootRequest) {
                    reset();
                    rebootRequest = false;
                    break;
                }
            }
        } catch (ProcessorException p) {
                processor.handleProtectedModeException(p);
        } catch (ModeSwitchException e) {
            //System.err.println("Informational: CPU switching modes: " + e.toString());
        }
        return x86Count;
    }

    public final int executeVirtual8086()
    {
        int x86Count = 0;

        if(rebootRequest) {
            reset();
            rebootRequest = false;
        }

        try {
            for(int i = 0; i < 100; i++) {
                int block;
                try {
                    block = linearAddr.executeVirtual8086(processor, processor.getInstructionPointer());
                } catch(org.jpc.emulator.processor.Processor.TripleFault e) {
                    reset();      //Reboot the system to get the CPU back online.
                    hitTraceTrap = true;
                    tripleFaulted = true;
                    break;
                }
                x86Count += block;
                processor.instructionsExecuted += block;
                //Don't call this on aborted blocks. Doing so is probably good source of desyncs.
                if(!processor.eflagsLastAborted)
                    processor.processVirtual8086ModeInterrupts(1);
                if(traceTrap.getAndClearTrapActive()) {
                    hitTraceTrap = true;
                    break;
                }
                if(rebootRequest) {
                    reset();
                    rebootRequest = false;
                    break;
                }
            }
        } catch (ProcessorException p) {
            processor.handleVirtual8086ModeException(p);
        } catch (ModeSwitchException e) {
            //System.err.println("Informational: CPU switching modes: " + e.toString());
        }
        return x86Count;
    }

    public static class PCFullStatus
    {
        public PC pc;                      //Loaded SAVED.
        public EventRecorder events;       //Loaded SAVED.
        public String projectID;           //Loaded SAVED.
        public String savestateID;         //Loaded SAVED.
        public long rerecords;             //Loaded SAVED.
        public String[][] extraHeaders;    //Loaded SAVED.
    }

    public void refreshGameinfo(PCFullStatus newstatus)
    {
        EventRecorder rec = newstatus.events;
        long lastTime = rec.getLastEventTime();
        long attachTime = rec.getAttachTime();
        long time = 0;
        if(attachTime < lastTime)
            time = lastTime - attachTime;
        gameChannel.addFrameLength(newstatus.pc.getTime(), time);
        System.err.println("Updated game info: Length: " + time + ".");
        gameChannel.addFrameRerecords(newstatus.pc.getTime(), rec.getRerecordCount());
        System.err.println("Updated game info: Rerecords: " + rec.getRerecordCount() + ".");
        String authors = null;
        if(newstatus.extraHeaders != null)
            for(String[] hdr : newstatus.extraHeaders) {
                if(hdr.length == 2 && "GAMENAME".equals(hdr[0])) {
                    gameChannel.addFrameGamename(newstatus.pc.getTime(), hdr[1]);
                    System.err.println("Updated game info: Name: " + hdr[1] + ".");
                }
                if(hdr.length == 3 && "AUTHORFULL".equals(hdr[0]))
                    authors = addName(authors, hdr[2]);
                if(hdr.length > 1 && ("AUTHORS".equals(hdr[0]) || "AUTHORNICKS".equals(hdr[0])))
                    for(int j = 1; j < hdr.length; j++)
                        authors = addName(authors, hdr[j]);
            }
        if(authors != null) {
            gameChannel.addFrameAuthors(newstatus.pc.getTime(), authors);
            System.err.println("Updated game info: Authors: " + authors + ".");
        } else {
            gameChannel.addFrameAuthors(newstatus.pc.getTime(), "(unknown)");
            System.err.println("Updated game info: Authors: (unknown).");
        }
    }

    private static String addName(String existing, String newname)
    {
        if(existing == null)
            return newname;
        else
            return existing + ", " + newname;
    }

    private static void saveDiskInfo(UnicodeOutputStream lines, ImageID diskID)
    {
        try {
            BaseImage pimg = BaseImageFactory.getImageByID(diskID);
            switch(pimg.getType()) {
            case FLOPPY:
                lines.encodeLine("TYPE", "FLOPPY");
                break;
            case HARDDRIVE:
                lines.encodeLine("TYPE", "HDD");
                break;
            case CDROM:
                lines.encodeLine("TYPE", "CDROM");
                break;
            case BIOS:
                lines.encodeLine("TYPE", "BIOS");
                break;
            default:
                lines.encodeLine("TYPE", "UNKNOWN");
                break;
            }
            lines.encodeLine("ID", diskID.getIDAsString());
            switch(pimg.getType()) {
            case FLOPPY:
            case HARDDRIVE:   //Floppies/HDD have the same fields.
                lines.encodeLine("TRACKS", pimg.getTracks());
                lines.encodeLine("SIDES", pimg.getSides());
                lines.encodeLine("SECTORS", pimg.getSectors());
            case CDROM:   //Floppies/HDD have these fields as well.
                lines.encodeLine("TOTALSECTORS", pimg.getTotalSectors());
                byte[] sector = new byte[512];
                MessageDigest md = MessageDigest.getInstance("MD5");
                for(int i = 0; i < pimg.getTotalSectors(); i++) {
                    pimg.read(i, sector, 1);
                    md.update(sector);
                }
                lines.encodeLine("IMAGEMD5", arrayToString(md.digest()));
                break;
            case BIOS:     //BIOS
                lines.encodeLine("IMAGELENGTH", pimg.getTotalSectors());
                md = MessageDigest.getInstance("MD5");
                byte[] content = new byte[(int)pimg.getTotalSectors()];
                pimg.read(0, content, content.length);
                md.update(content);
                lines.encodeLine("IMAGEMD5", arrayToString(md.digest()));
            }

            List<String> comments = pimg.getComments();
            if(comments != null) {
                for(String x : comments)
                    lines.encodeLine("COMMENT", x);
            }
        } catch(Exception e) {
            System.err.println("Warning: Can't lookup disk information: " + e.getMessage() +
                "[" + e.getClass().getName() + "].");
        }
    }

    private static void saveDiskInfo(JRSRArchiveWriter writer, COWImage image, Set<ImageID> saved) throws IOException
    {
        if(image == null)
            return;
        saveDiskInfo(writer, image.getID(), saved);
    }

    private static void saveDiskInfo(JRSRArchiveWriter writer, ImageID diskID, Set<ImageID> saved) throws IOException
    {
        if(diskID == null)
            return;
        if(saved.contains(diskID))
            return;
        saved.add(diskID);
        UnicodeOutputStream lines = writer.addMember("diskinfo-" + diskID);
        saveDiskInfo(lines, diskID);
        lines.close();
    }

    public static void saveSavestate(JRSRArchiveWriter writer, PCFullStatus fullStatus, boolean movie,
        boolean noCompress) throws IOException
    {
        fullStatus.savestateID = randomHexes(24);
        fullStatus.events.markSave(fullStatus.savestateID, fullStatus.rerecords);

        //Save the header.
        UnicodeOutputStream lines = writer.addMember("header");
        lines.writeLine("PROJECTID " + fullStatus.projectID);
        if(!movie)
            lines.writeLine("SAVESTATEID " + fullStatus.savestateID);
        lines.writeLine("RERECORDS " + fullStatus.rerecords);
        lines.writeLine("SYSTEM PC-JPC-RR-r10");
        if(fullStatus.extraHeaders != null)
            for(int i = 0; i < fullStatus.extraHeaders.length; i++) {
                Object[] arr = new Object[fullStatus.extraHeaders[i].length];
                System.arraycopy(fullStatus.extraHeaders[i], 0, arr, 0, arr.length);
                lines.encodeLine(arr);
            }
        lines.close();

        //Save intialization segment.
        lines = writer.addMember("initialization");
        fullStatus.pc.getHardwareInfo().makeHWInfoSegment(lines, fullStatus.pc.diskChanger);
        lines.close();

        //Save savestate itsefl (if any).
        if(!movie) {
            FourToFiveEncoder entry = new FourToFiveEncoder(writer.addMember("savestate"));
            DeflaterOutputStream dos;
            Deflater deflater = new Deflater(noCompress ? Deflater.NO_COMPRESSION : Deflater.DEFAULT_COMPRESSION);
            OutputStream zip = dos = new DeflaterOutputStream(entry, deflater);
            SRDumper dumper = new SRDumper(zip);
            dumper.dumpObject(fullStatus.pc);
            dumper.flush();
            dos.close();

            UnicodeOutputStream entry2 = writer.addMember("manifest");
            dumper.writeConstructorManifest(entry2);
            entry2.close();
        }

        //Save the movie events.
        lines = writer.addMember("events");
        fullStatus.events.saveEvents(lines);
        lines.close();

        //Save the disk info.
        PCHardwareInfo hw = fullStatus.pc.getHardwareInfo();
        DiskImageSet images = hw.images;
        int disks = 1 + images.highestDiskIndex();
        Set<ImageID> imageSet = new HashSet<ImageID>();
        for(int i = 0; i < disks; i++)
            saveDiskInfo(writer, images.lookupDisk(i), imageSet);
        saveDiskInfo(writer, hw.biosID, imageSet);
        saveDiskInfo(writer, hw.vgaBIOSID, imageSet);
        saveDiskInfo(writer, hw.hdaID, imageSet);
        saveDiskInfo(writer, hw.hdbID, imageSet);
        saveDiskInfo(writer, hw.hdcID, imageSet);
        saveDiskInfo(writer, hw.hddID, imageSet);
    }

    public static PCFullStatus loadSavestate(JRSRArchiveReader reader, boolean reuse, boolean forceMovie,
        PCFullStatus existing, String initName) throws IOException
    {
        if(initName == null)
            initName = "initialization";
        PCFullStatus fullStatus = new PCFullStatus();
        boolean ssPresent = false;
        UnicodeInputStream lines = reader.readMember("header");

        fullStatus.rerecords = -1;

        String[] components = nextParseLine(lines);
        while(components != null) {
           if("SAVESTATEID".equals(components[0])) {
               if(components.length != 2)
                   throw new IOException("Bad " + components[0] + " line in header segment: " +
                       "expected 2 components, got " + components.length);
               ssPresent = true;
               fullStatus.savestateID = components[1];
           } else if("PROJECTID".equals(components[0])) {
               if(components.length != 2)
                   throw new IOException("Bad " + components[0] + " line in header segment: " +
                       "expected 2 components, got " + components.length);
               fullStatus.projectID = components[1];
           } else if("RERECORDS".equals(components[0])) {
               if(components.length != 2)
                   throw new IOException("Bad " + components[0] + " line in header segment: " +
                       "expected 2 components, got " + components.length);
               try {
                   fullStatus.rerecords = Long.parseLong(components[1]);
                   if(fullStatus.rerecords < 0) {
                       throw new IOException("Invalid rerecord count");
                   }
               } catch(NumberFormatException e) {
                   throw new IOException("Invalid rerecord count");
               }
           } else if("SYSTEM".equals(components[0])) {
               if(components.length != 2)
                   throw new IOException("Bad " + components[0] + " line in header segment: " +
                       "expected 2 components, got " + components.length);
               if(!"PC-JPC-RR-r10".equals(components[1]) && !"PC-JPC-RR-r11.3".equals(components[1]))
                   throw new IOException("Invalid system type '" + components[1] + "'");
           } else {
               if(fullStatus.extraHeaders == null) {
                   fullStatus.extraHeaders = new String[1][];
                   fullStatus.extraHeaders[0] = components;
               } else {
                   String[][] extraHeaders = new String[fullStatus.extraHeaders.length + 1][];
                   System.arraycopy(fullStatus.extraHeaders, 0, extraHeaders, 0, fullStatus.extraHeaders.length);
                   extraHeaders[fullStatus.extraHeaders.length] = components;
                   fullStatus.extraHeaders = extraHeaders;
               }
           }
           components = nextParseLine(lines);
        }

        if(fullStatus.projectID == null)
            throw new IOException("PROJECTID header missing");
        if(fullStatus.rerecords < 0)
            throw new IOException("RERECORDS header missing");

        if(ssPresent && !forceMovie) {
            UnicodeInputStream entry;
            entry = reader.readMember("manifest");
            if(!SRLoader.checkConstructorManifest(entry))
                throw new IOException("Wrong savestate version");
            entry.close();

            InputStream entry2 = new FourToFiveDecoder(reader.readMember("savestate"));
            SRLoader loader = new SRLoader(new InflaterInputStream(entry2));
            fullStatus.pc = (PC)(loader.loadObject());
            entry.close();
        } else {
            lines = reader.readMember(initName);
            PCHardwareInfo hwInfo = PCHardwareInfo.parseHWInfoSegment(lines);
            fullStatus.pc = new PC(hwInfo);
        }

        if(reuse)
            fullStatus.events = existing.events;
        else {
            lines = reader.readMember("events");
            fullStatus.events = new EventRecorder(lines);
        }

        if(reuse && (existing == null || !fullStatus.projectID.equals(existing.projectID)))
            throw new IOException("Savestate is not from current movie");

        fullStatus.events.attach(fullStatus.pc, forceMovie ? null : fullStatus.savestateID);

        if(!reuse) {
            fullStatus.events.setHeaders(fullStatus.extraHeaders);
            fullStatus.events.setRerecordCount(fullStatus.rerecords);
        }

        if(existing == null || !fullStatus.projectID.equals(existing.projectID))
            fullStatus.rerecords++;
        else
            if(existing != null && existing.rerecords > fullStatus.rerecords)
                 fullStatus.rerecords = existing.rerecords + 1;
            else
                fullStatus.rerecords++;

        fullStatus.pc.refreshGameinfo(fullStatus);
        return fullStatus;
    }
}
