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

package org.jpc.emulator;

import org.jpc.emulator.motherboard.*;
import org.jpc.emulator.memory.*;
import org.jpc.emulator.pci.peripheral.*;
import org.jpc.emulator.pci.*;
import org.jpc.emulator.peripheral.*;
import org.jpc.emulator.processor.*;
import org.jpc.support.*;
import org.jpc.diskimages.BlockDevice;
import org.jpc.diskimages.DiskImage;
import org.jpc.diskimages.DiskImageSet;
import org.jpc.diskimages.GenericBlockDevice;
import org.jpc.diskimages.ImageLibrary;
import org.jpc.jrsr.JRSRArchiveReader;
import org.jpc.jrsr.JRSRArchiveWriter;
import org.jpc.jrsr.FourToFiveDecoder;
import org.jpc.jrsr.FourToFiveEncoder;
import java.io.*;
import java.util.*;
import java.util.zip.*;
import java.lang.reflect.*;
import org.jpc.emulator.memory.codeblock.CodeBlockManager;

import static org.jpc.Misc.arrayToString;
import static org.jpc.Misc.stringToArray;
import static org.jpc.Misc.nextParseLine;
import static org.jpc.Misc.componentEscape;
import static org.jpc.Misc.randomHexes;

/**
 * This class represents the emulated PC as a whole, and holds references
 * to its main hardware components.
 * @author Chris Dennis
 * @author Ian Preston
 */
public class PC implements SRDumpable
{
    public static class PCHardwareInfo implements SRDumpable
    {
        byte[] biosID;
        byte[] vgaBIOSID;
        byte[] hdaID;
        byte[] hdbID;
        byte[] hdcID;
        byte[] hddID;
        DiskImageSet images;
        int initFDAIndex;
        int initFDBIndex;
        int initCDROMIndex;
        long initRTCTime;
        int cpuDivider;
        int memoryPages;
        Map<String, String> hwModules;
        DriveSet.BootType bootType;

        public void dumpStatusPartial(StatusDumper output2) throws IOException
        {
            if(output2 != null)
                return;

            PrintStream output = System.err;

            output.println("BIOS " + arrayToString(biosID));
            output.println("VGABIOS " + arrayToString(vgaBIOSID));
            if(hdaID != null)
                output.println("HDA " + arrayToString(hdaID));
            if(hdbID != null)
                output.println("HDB " + arrayToString(hdbID));
            if(hdcID != null)
                output.println("HDC " + arrayToString(hdcID));
            if(hddID != null)
                output.println("HDD " + arrayToString(hddID));
            int disks = 1 + images.highestDiskIndex();
            for(int i = 0; i < disks; i++) {
                DiskImage disk = images.lookupDisk(i);
                if(disk != null)
                    output.println("DISK " + i + " " + arrayToString(disk.getImageID()));
            }
            if(initFDAIndex >= 0)
                output.println("FDA " + initFDAIndex);
            if(initFDBIndex >= 0)
                output.println("FDB " + initFDBIndex);
            if(initCDROMIndex >= 0)
                output.println("CDROM " + initCDROMIndex);
            output.println("INITIALTIME " + initRTCTime);
            output.println("CPUDIVIDER " + (cpuDivider - 1));
            output.println("MEMORYSIZE " + memoryPages);
            if(bootType == DriveSet.BootType.FLOPPY)
                output.println("BOOT FLOPPY");
            else if(bootType == DriveSet.BootType.HARD_DRIVE)
                output.println("BOOT HDD");
            else if(bootType == DriveSet.BootType.CDROM)
                output.println("BOOT CDROM");
            else if(bootType == null)
                ;
            else
                throw new IOException("Unknown boot type");
            if(hwModules != null && !hwModules.isEmpty()) {
                for(Map.Entry<String,String> e : hwModules.entrySet()) {
                    if(e.getValue() != null) 
                        output.println("LOADMODULEA " + e.getKey() + "(" + e.getValue() + ")");
                    else
                        output.println("LOADMODULE " + e.getKey());
                }
            }
        }

        public void dumpStatus(StatusDumper output)
        {
            if(output.dumped(this))
                return;

            output.println("#" + output.objectNumber(this) + ": PCHardwareInfo:");
            try { dumpStatusPartial(output); } catch(Exception e) {}
            output.endObject();
        }

        public void dumpSRPartial(SRDumper output) throws IOException
        {
            output.dumpArray(biosID);
            output.dumpArray(vgaBIOSID);
            output.dumpArray(hdaID);
            output.dumpArray(hdbID);
            output.dumpArray(hdcID);
            output.dumpArray(hddID);
            output.dumpObject(images);
            output.dumpInt(initFDAIndex);
            output.dumpInt(initFDBIndex);
            output.dumpInt(initCDROMIndex);
            output.dumpLong(initRTCTime);
            output.dumpInt(cpuDivider);
            output.dumpInt(memoryPages);
            if(hwModules != null) {
                output.dumpBoolean(true);
                for(Map.Entry<String,String> e : hwModules.entrySet()) {
                    output.dumpBoolean(true);
                    output.dumpString(e.getKey());
                    output.dumpString(e.getValue());
                }
                output.dumpBoolean(false);
            } else
                output.dumpBoolean(false);
            output.dumpByte(DriveSet.BootType.toNumeric(bootType));
        }

        public PCHardwareInfo()
        {
            images = new DiskImageSet();
        }

        public PCHardwareInfo(SRLoader input) throws IOException
        {
            input.objectCreated(this);
            biosID = input.loadArrayByte();
            vgaBIOSID = input.loadArrayByte();
            hdaID = input.loadArrayByte();
            hdbID = input.loadArrayByte();
            hdcID = input.loadArrayByte();
            hddID = input.loadArrayByte();
            images = (DiskImageSet)input.loadObject();
            initFDAIndex = input.loadInt();
            initFDBIndex = input.loadInt();
            initCDROMIndex = input.loadInt();
            initRTCTime = input.loadLong();
            cpuDivider = input.loadInt();
            memoryPages = input.loadInt();
            boolean present = input.loadBoolean();
            if(present) {
                hwModules = new LinkedHashMap<String, String>();
                present = input.loadBoolean();
                while(present) {
                    String name = input.loadString();
                    String params = input.loadString();
                    hwModules.put(name, params);
                    present = input.loadBoolean();
                }
            }
            bootType = DriveSet.BootType.fromNumeric(input.loadByte());
        }

        public void makeHWInfoSegment(UTFOutputLineStream output, DiskChanger changer) throws IOException
        {
            output.writeLine("BIOS " + arrayToString(biosID));
            output.writeLine("VGABIOS " + arrayToString(vgaBIOSID));
            if(hdaID != null)
                output.writeLine("HDA " + arrayToString(hdaID));
            if(hdbID != null)
                output.writeLine("HDB " + arrayToString(hdbID));
            if(hdcID != null)
                output.writeLine("HDC " + arrayToString(hdcID));
            if(hddID != null)
                output.writeLine("HDD " + arrayToString(hddID));
            //TODO: When event recording becomes available, only save the disk images needed.
            Set<Integer> usedDisks = changer.usedDiskSet();
            int disks = 1 + images.highestDiskIndex();
            for(int i = 0; i < disks; i++) {
                DiskImage disk = images.lookupDisk(i);
                if(disk != null && usedDisks.contains(i))
                    output.writeLine("DISK " + i + " " + arrayToString(disk.getImageID()));
            }
            if(initFDAIndex >= 0)
                output.writeLine("FDA " + initFDAIndex);
            if(initFDBIndex >= 0)
                output.writeLine("FDB " + initFDBIndex);
            if(initCDROMIndex >= 0)
                output.writeLine("CDROM " + initCDROMIndex);
            output.writeLine("INITIALTIME " + initRTCTime);
            output.writeLine("CPUDIVIDER " + cpuDivider);
            output.writeLine("MEMORYSIZE " + memoryPages);
            if(bootType == DriveSet.BootType.FLOPPY)
                output.writeLine("BOOT FLOPPY");
            else if(bootType == DriveSet.BootType.HARD_DRIVE)
                output.writeLine("BOOT HDD");
            else if(bootType == DriveSet.BootType.CDROM)
                output.writeLine("BOOT CDROM");
            else if(bootType == null)
                ;
            else
                throw new IOException("Unknown boot type");
            if(hwModules != null && !hwModules.isEmpty()) {
                for(Map.Entry<String,String> e : hwModules.entrySet()) {
                    if(e.getValue() != null) 
                        output.writeLine("LOADMODULEA " + e.getKey() + "(" + e.getValue() + ")");
                    else
                        output.writeLine("LOADMODULE " + e.getKey());
                }
            }
        }

        public static int componentsForLine(String op)
        {
            if("BIOS".equals(op))
                return 2;
            if("VGABIOS".equals(op))
                return 2;
            if("HDA".equals(op))
                return 2;
            if("HDB".equals(op))
                return 2;
            if("HDC".equals(op))
                return 2;
            if("HDD".equals(op))
                return 2;
            if("FDA".equals(op))
                return 2;
            if("FDB".equals(op))
                return 2;
            if("CDROM".equals(op))
                return 2;
            if("INITIALTIME".equals(op))
                return 2;
            if("CPUDIVIDER".equals(op))
                return 2;
            if("MEMORYSIZE".equals(op))
                return 2;
            if("BOOT".equals(op))
                return 2;
            if("LOADMODULE".equals(op))
                return 2;
            if("LOADMODULEA".equals(op))
                return 3;
            if("DISK".equals(op))
                return 3;
            return 0;
        }


        public static PCHardwareInfo parseHWInfoSegment(UTFInputLineStream input) throws IOException
        {

            PCHardwareInfo hw = new PCHardwareInfo();
            hw.initFDAIndex = -1;
            hw.initFDBIndex = -1;
            hw.initCDROMIndex = -1;
            hw.images = new DiskImageSet();
            hw.hwModules = new LinkedHashMap<String, String>();
            String[] components = nextParseLine(input);
            while(components != null) {
                if(components.length != componentsForLine(components[0]))
                    throw new IOException("Bad " + components[0] + " line in ininitialization segment: " + 
                        "expected " + componentsForLine(components[0]) + " components, got " + components.length);
                if("BIOS".equals(components[0]))
                    hw.biosID = stringToArray(components[1]);
                else if("VGABIOS".equals(components[0]))
                    hw.vgaBIOSID = stringToArray(components[1]);
                else if("HDA".equals(components[0]))
                    hw.hdaID = stringToArray(components[1]);
                else if("HDB".equals(components[0]))
                    hw.hdbID = stringToArray(components[1]);
                else if("HDC".equals(components[0]))
                    hw.hdcID = stringToArray(components[1]);
                else if("HDD".equals(components[0]))
                    hw.hddID = stringToArray(components[1]);
                else if("DISK".equals(components[0])) {
                    int id;
                    try {
                        id = Integer.parseInt(components[1]);
                        if(id < 0)
                            throw new NumberFormatException("Bad id");
                    } catch(NumberFormatException e) {
                        throw new IOException("Bad DISK line in initialization segment");
                    }
                    hw.images.addDisk(id, new DiskImage(components[2], false));
                } else if("FDA".equals(components[0])) {
                    int id;
                    try {
                        id = Integer.parseInt(components[1]);
                        if(id < 0)
                            throw new NumberFormatException("Bad id");
                    } catch(NumberFormatException e) {
                        throw new IOException("Bad FDA line in initialization segment");
                    }
                    hw.initFDAIndex = id;
                } else if("FDB".equals(components[0])) {
                    int id;
                    try {
                        id = Integer.parseInt(components[1]);
                        if(id < 0)
                            throw new NumberFormatException("Bad id");
                    } catch(NumberFormatException e) {
                        throw new IOException("Bad FDB line in initialization segment");
                    }
                    hw.initFDBIndex = id;
                } else if("CDROM".equals(components[0])) {
                    int id;
                    try {
                        id = Integer.parseInt(components[1]);
                        if(id < 0)
                            throw new NumberFormatException("Bad id");
                    } catch(NumberFormatException e) {
                        throw new IOException("Bad CDROM line in initialization segment");
                    }
                    hw.initCDROMIndex = id;
                } else if("INITIALTIME".equals(components[0])) {
                    long id;
                    try {
                        id = Long.parseLong(components[1]);
                        if(id < 0 || id > 4102444799999L)
                            throw new NumberFormatException("Bad id");
                    } catch(NumberFormatException e) {
                        throw new IOException("Bad INITIALTIME line in initialization segment");
                    }
                    hw.initRTCTime = id;
                } else if("CPUDIVIDER".equals(components[0])) {
                    int id;
                    try {
                        id = Integer.parseInt(components[1]);
                        if(id < 1 || id > 256)
                            throw new NumberFormatException("Bad id");
                    } catch(NumberFormatException e) {
                        throw new IOException("Bad CPUDIVIDER line in initialization segment");
                    }
                    hw.cpuDivider = id;
                } else if("MEMORYSIZE".equals(components[0])) {
                    int id;
                    try {
                        id = Integer.parseInt(components[1]);
                        if(id < 256 || id > 262144)
                            throw new NumberFormatException("Bad id");
                    } catch(NumberFormatException e) {
                        throw new IOException("Bad MEMORYSIZE line in initialization segment");
                    }
                    hw.memoryPages = id;
                } else if("BOOT".equals(components[0])) {
                    if("FLOPPY".equals(components[1]))
                        hw.bootType = DriveSet.BootType.FLOPPY;
                    else if("HDD".equals(components[1]))
                        hw.bootType = DriveSet.BootType.HARD_DRIVE;
                    else if("CDROM".equals(components[1]))
                        hw.bootType = DriveSet.BootType.CDROM;
                    else
                        throw new IOException("Bad BOOT line in initialization segment");
                } else if("LOADMODULE".equals(components[0])) {
                    hw.hwModules.put(components[1], null);
                } else if("LOADMODULEA".equals(components[0])) {
                    hw.hwModules.put(components[1], components[2]);
                }
                components = nextParseLine(input);
            }
            return hw;
        }
    }


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

    private VGADigitalOut videoOut;

    private TraceTrap traceTrap;
    private boolean hitTraceTrap;
    private boolean tripleFaulted;
    private boolean rebootRequest;

    private int cdromIndex;

    public VGADigitalOut getVideoOutput()
    {
        return videoOut;
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
        } catch(Exception e) {
            try {
                Constructor<?> cc = module.getConstructor();
                c = (HardwareComponent)cc.newInstance();
            } catch(Exception f) {
                throw new IOException("Unable to instantiate extension module \"" + name + "\".");
            }
        }

        return c;
    }

    /**
     * Constructs a new <code>PC</code> instance with the specified external time-source and
     * drive set.
     * @param drives drive set for this instance.
     * @throws java.io.IOException propogated from bios resource loading
     */
    public PC(DriveSet drives, int ramPages, int clockDivide, String sysBIOSImg, String vgaBIOSImg,
        long initTime, DiskImageSet images, Map<String, String> hwModules) throws IOException
    {
        parts = new LinkedHashSet<HardwareComponent>();

        cdromIndex = -1;
        for(int i = 0; i < 4; i++) {
            BlockDevice dev = drives.getHardDrive(i);
            if(dev != null && dev.getType() == BlockDevice.Type.CDROM)
                cdromIndex = i;
        }

        cpuClockDivider = clockDivide;
        sysRAMSize = ramPages * 4096;

        if(hwModules != null)
            for(Map.Entry<String,String> e : hwModules.entrySet()) {
                String name = e.getKey();
                String params = e.getValue();
                System.err.println("Informational: Loading module \"" + name + "\".");
                parts.add(loadHardwareModule(name, params));
            }

        vmClock = new VirtualClock();
        parts.add(vmClock);
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
        parts.add(new RTC(0x70, 8, sysRAMSize, initTime));
        System.err.println("Informational: Creating interval timer...");
        parts.add(new IntervalTimer(0x40, 0));
        System.err.println("Informational: Creating A20 Handler...");
        parts.add(new GateA20Handler());
        this.images = images;

        //Peripherals
        System.err.println("Informational: Creating IDE interface...");
        parts.add(new PIIX3IDEInterface());

        System.err.println("Informational: Creating Keyboard...");
        parts.add(new Keyboard());
        System.err.println("Informational: Creating floppy disk controller...");
        parts.add(new FloppyController());
        System.err.println("Informational: Creating PC speaker...");
        parts.add(new PCSpeaker());

        //PCI Stuff
        System.err.println("Informational: Creating PCI Host Bridge...");
        parts.add(new PCIHostBridge());
        System.err.println("Informational: Creating PCI-to-ISA Bridge...");
        parts.add(new PCIISABridge());
        System.err.println("Informational: Creating PCI Bus...");
        parts.add(new PCIBus());

        //BIOSes
        System.err.println("Informational: Creating system BIOS...");
        parts.add(new SystemBIOS(sysBIOSImg));
        System.err.println("Informational: Creating VGA BIOS...");
        parts.add(new VGABIOS(vgaBIOSImg));
        System.err.println("Informational: Creating trace trap...");
        parts.add(traceTrap = new TraceTrap());

        System.err.println("Informational: Creating hardware info...");
        hwInfo = new PCHardwareInfo();

        DisplayController displayController = null;
        for(HardwareComponent c : parts)
            if(c instanceof DisplayController)
                if(displayController == null)
                    displayController = (DisplayController)c;
                else
                    throw new IOException("Can not have multiple display controllers: \"" +
                        c.getClass().getName() + "\" and \"" + displayController.getClass().getName() +
                        "\" are both display controllers.");
        if(displayController == null)
        {
            System.err.println("Informational: Creating VGA card...");
            VGACard card = new VGACard();
            parts.add(card);
            displayController = card;
        }
        videoOut = displayController.getOutputDevice();


        System.err.println("Informational: Configuring components...");
        if (!configure()) {
            throw new IllegalStateException("PC Configuration failed");
        }
        System.err.println("Informational: PC initialization done.");
    }

    public int getCDROMIndex()
    {
        return cdromIndex;
    }

    public void dumpStatusPartial(StatusDumper output)
    {
        output.println("\tsysRAMSize " + sysRAMSize + " cpuClockDivider " + cpuClockDivider);
        output.println("\ttripleFaulted " + tripleFaulted + " cdromIndex " + cdromIndex);
        //hitTraceTrap not printed here.
        output.println("\tprocessor <object #" + output.objectNumber(processor) + ">"); if(processor != null) processor.dumpStatus(output);
        output.println("\tphysicalAddr <object #" + output.objectNumber(physicalAddr) + ">"); if(physicalAddr != null) physicalAddr.dumpStatus(output);
        output.println("\tlinearAddr <object #" + output.objectNumber(linearAddr) + ">"); if(linearAddr != null) linearAddr.dumpStatus(output);
        output.println("\tvmClock <object #" + output.objectNumber(vmClock) + ">"); if(vmClock != null) vmClock.dumpStatus(output);
        output.println("\timages <object #" + output.objectNumber(images) + ">"); if(images != null) images.dumpStatus(output);
        output.println("\ttraceTrap <object #" + output.objectNumber(traceTrap) + ">"); if(traceTrap != null) traceTrap.dumpStatus(output);
        output.println("\thwInfo <object #" + output.objectNumber(hwInfo) + ">"); if(hwInfo != null) hwInfo.dumpStatus(output);
        output.println("\thvideoOut <object #" + output.objectNumber(videoOut) + ">"); if(videoOut != null) videoOut.dumpStatus(output);
        output.println("\tbrb <object #" + output.objectNumber(brb) + ">"); if(brb != null) brb.dumpStatus(output);

        int i = 0;
        for (HardwareComponent part : parts) {
            output.println("\tparts[" + i + "] <object #" + output.objectNumber(part) + ">"); if(part != null) part.dumpStatus(output);
            i++;
        }
    }

    public PC(SRLoader input) throws IOException
    {
        input.objectCreated(this);
        cdromIndex = input.loadInt();
        sysRAMSize = input.loadInt();
        cpuClockDivider = input.loadInt();
        processor = (Processor)input.loadObject();
        physicalAddr = (PhysicalAddressSpace)input.loadObject();
        linearAddr = (LinearAddressSpace)input.loadObject();
        vmClock = (Clock)input.loadObject();
        images = (DiskImageSet)(input.loadObject());
        traceTrap = (TraceTrap)input.loadObject();
        manager = (CodeBlockManager)input.loadObject();
        hwInfo = (PCHardwareInfo)(input.loadObject());
        videoOut = (VGADigitalOut)(input.loadObject());
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
        output.dumpInt(cdromIndex);
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
        output.dumpObject(videoOut);
        output.dumpBoolean(hitTraceTrap);
        output.dumpBoolean(tripleFaulted);
        for (HardwareComponent part : parts) {
            output.dumpBoolean(true);
            output.dumpObject(part);
        }
        output.dumpBoolean(false);
        output.dumpBoolean(rebootRequest);
        output.dumpObject(brb);
        output.dumpObject(diskChanger);
    }

    public static Map<String, String> parseHWModules(String moduleString) throws IOException
    {
        Map<String, String> ret = new LinkedHashMap<String, String>();

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

            ret.put(name, params);
        }

        return ret;
    }

    public static PCHardwareInfo parseArgs(String[] args) throws IOException
    {
        PCHardwareInfo hw = new PCHardwareInfo();

        String sysBIOSImg = ArgProcessor.findVariable(args, "sysbios", "BIOS");
        hw.biosID = DiskImage.getLibrary().canonicalNameFor(sysBIOSImg);
        if(hw.biosID == null)
            throw new IOException("Can't find image \"" + sysBIOSImg + "\".");

        String vgaBIOSImg = ArgProcessor.findVariable(args, "vgabios", "VGABIOS");
        hw.vgaBIOSID = DiskImage.getLibrary().canonicalNameFor(vgaBIOSImg);
        if(hw.vgaBIOSID == null)
            throw new IOException("Can't find image \"" + vgaBIOSImg + "\".");

        String hdaImg = ArgProcessor.findVariable(args, "hda", null);
        hw.hdaID = DiskImage.getLibrary().canonicalNameFor(hdaImg);
        if(hw.hdaID == null && hdaImg != null)
            throw new IOException("Can't find image \"" + hdaImg + "\".");

        String hdbImg = ArgProcessor.findVariable(args, "hdb", null);
        hw.hdbID = DiskImage.getLibrary().canonicalNameFor(hdbImg);
        if(hw.hdbID == null && hdbImg != null)
            throw new IOException("Can't find image \"" + hdbImg + "\".");

        String hdcImg = ArgProcessor.findVariable(args, "hdc", null);
        hw.hdcID = DiskImage.getLibrary().canonicalNameFor(hdcImg);
        if(hw.hdcID == null && hdcImg != null)
            throw new IOException("Can't find image \"" + hdcImg + "\".");

        String hddImg = ArgProcessor.findVariable(args, "hdd", null);
        hw.hddID = DiskImage.getLibrary().canonicalNameFor(hddImg);
        if(hw.hddID == null && hddImg != null)
            throw new IOException("Can't find image \"" + hddImg + "\".");

        String cdRomFileName = ArgProcessor.findVariable(args, "-cdrom", null);
        if (cdRomFileName != null) {
             if(hdcImg != null)
                 throw new IOException("-hdc and -cdrom are mutually exclusive.");
            hw.initCDROMIndex = hw.images.addDisk(new DiskImage(cdRomFileName, false));
        } else
            hw.initCDROMIndex = -1;

        String fdaFileName = ArgProcessor.findVariable(args, "-fda", null);
        if(fdaFileName != null) {
            hw.initFDAIndex = hw.images.addDisk(new DiskImage(fdaFileName, false));
        } else
            hw.initFDAIndex = -1;

        String fdbFileName = ArgProcessor.findVariable(args, "-fdb", null);
        if(fdbFileName != null) {
            hw.initFDBIndex = hw.images.addDisk(new DiskImage(fdbFileName, false));
        } else
            hw.initFDBIndex = -1;

        String initTimeS = ArgProcessor.findVariable(args, "inittime", null);
        try {
            hw.initRTCTime = Long.parseLong(initTimeS, 10);
            if(hw.initRTCTime < 0 || hw.initRTCTime > 4102444799999L)
               throw new Exception("Invalid time value.");
        } catch(Exception e) {
            if(initTimeS != null)
                System.err.println("Warning: Invalid -inittime. Using default value of 1 000 000 000 000.");
            hw.initRTCTime = 1000000000000L;
        }

        String cpuDividerS = ArgProcessor.findVariable(args, "cpudivider", "50");
        try {
            hw.cpuDivider = Integer.parseInt(cpuDividerS, 10);
            if(hw.cpuDivider < 1 || hw.cpuDivider > 256)
               throw new Exception("Invalid CPU divider value.");
        } catch(Exception e) {
            if(cpuDividerS != null)
                System.err.println("Warning: Invalid -cpudivider. Using default value of 50.");
            hw.cpuDivider = 50;
        }

        String memoryPagesS = ArgProcessor.findVariable(args, "memsize", "4096");
        try {
            hw.memoryPages = Integer.parseInt(memoryPagesS, 10);
            if(hw.memoryPages < 256 || hw.memoryPages > 262144)
               throw new Exception("Invalid memory size value.");
        } catch(Exception e) {
            if(memoryPagesS != null)
                System.err.println("Warning: Invalid -memsize. Using default value of 4096.");
            hw.memoryPages = 4096;
        }

        String hwModulesS = ArgProcessor.findVariable(args, "-hwmodules", null);
        if(hwModulesS != null) {
            hw.hwModules = parseHWModules(hwModulesS);
        }

        String bootArg = ArgProcessor.findVariable(args, "-boot", "fda");
        bootArg = bootArg.toLowerCase();
        if (bootArg.equals("fda"))
            hw.bootType = DriveSet.BootType.FLOPPY;
        else if (bootArg.equals("hda"))
            hw.bootType = DriveSet.BootType.HARD_DRIVE;
        else if (bootArg.equals("cdrom"))
            hw.bootType = DriveSet.BootType.CDROM;

        return hw;
    }

    private static GenericBlockDevice blockdeviceFor(String name) throws IOException
    {
        if(name == null)
            return null;
        return new GenericBlockDevice(new DiskImage(name, false));
    }

    public static PC createPC(PCHardwareInfo hw) throws IOException
    {
        PC pc;
        String biosID = arrayToString(hw.biosID);
        String vgaBIOSID = arrayToString(hw.vgaBIOSID);
        BlockDevice hda = blockdeviceFor(arrayToString(hw.hdaID));
        BlockDevice hdb = blockdeviceFor(arrayToString(hw.hdbID));
        BlockDevice hdc = blockdeviceFor(arrayToString(hw.hdcID));
        BlockDevice hdd = blockdeviceFor(arrayToString(hw.hddID));
        if(hdc == null) {
            hdc = new GenericBlockDevice(BlockDevice.Type.CDROM);
        }

        DriveSet drives = new DriveSet(hw.bootType, hda, hdb, hdc, hdd);
        pc = new PC(drives, hw.memoryPages, hw.cpuDivider, biosID, vgaBIOSID, hw.initRTCTime, hw.images,
            hw.hwModules);
        FloppyController fdc = (FloppyController)pc.getComponent(FloppyController.class);

        DiskImage img1 = pc.getDisks().lookupDisk(hw.initFDAIndex);
        BlockDevice device1 = new GenericBlockDevice(img1, BlockDevice.Type.FLOPPY);
        fdc.changeDisk(device1, 0);

        DiskImage img2 = pc.getDisks().lookupDisk(hw.initFDBIndex);
        BlockDevice device2 = new GenericBlockDevice(img2, BlockDevice.Type.FLOPPY);
        fdc.changeDisk(device2, 1);

        if(hdc.getType() == BlockDevice.Type.CDROM) {
            DiskImage img3 = pc.getDisks().lookupDisk(hw.initCDROMIndex);
            ((GenericBlockDevice)hdc).configure(img3);
        }

        PCHardwareInfo hw2 = pc.getHardwareInfo();
        hw2.biosID = hw.biosID;
        hw2.vgaBIOSID = hw.vgaBIOSID;
        hw2.hdaID = hw.hdaID;
        hw2.hdbID = hw.hdbID;
        hw2.hdcID = hw.hdcID;
        hw2.hddID = hw.hddID;
        hw2.images = hw.images;
        hw2.initFDAIndex = hw.initFDAIndex;
        hw2.initFDBIndex = hw.initFDBIndex;
        hw2.initCDROMIndex = hw.initCDROMIndex;
        hw2.initRTCTime = hw.initRTCTime;
        hw2.cpuDivider = hw.cpuDivider;
        hw2.memoryPages = hw.memoryPages;
        hw2.bootType = hw.bootType;
        hw2.hwModules = hw.hwModules;
        return pc;
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
        vmClock.pause();
    }

    /**
     * Inserts the specified floppy disk into the drive identified.
     * @param disk new floppy disk to be inserted.
     * @param index drive which the disk is inserted into.
     */
    private void changeFloppyDisk(BlockDevice disk, int index) 
    {
        ((FloppyController) getComponent(FloppyController.class)).changeDisk(disk, index);
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
        private EventRecorder eRecorder;
        private PC upperBackref;
        private int currentDriveA;
        private int currentDriveB;
        private int currentCDROM;
        private Set<Integer> usedDisks;

        private void checkFloppyChange(int driveIndex, int diskIndex) throws IOException
        {
            if(driveIndex == 2 && upperBackref.cdromIndex < 0)
                throw new IOException("No CD-ROM drive available");
            if(diskIndex < -1)
                throw new IOException("Illegal disk number");
            DiskImage disk = upperBackref.images.lookupDisk(diskIndex);
            if(driveIndex < 0 || driveIndex > 2)
                throw new IOException("Illegal drive number");
            if(diskIndex >= 0 && (diskIndex == currentDriveA || diskIndex == currentDriveB || 
                    diskIndex == currentCDROM))
                throw new IOException("Specified disk is already in some drive");
            if(diskIndex < 0 && driveIndex == 0 && currentDriveA < 0)
                throw new IOException("No disk present in drive A");
            if(diskIndex < 0 && driveIndex == 1 && currentDriveB < 0)
                throw new IOException("No disk present in drive B");
            if(diskIndex < 0 && driveIndex == 2 && currentCDROM < 0)
                throw new IOException("No disk present in CD-ROM Drive");
            if(diskIndex > 0 && driveIndex < 2 && (disk == null || disk.getType() != BlockDevice.Type.FLOPPY))
                throw new IOException("Attempt to put non-floppy into drive A or B");
            if(diskIndex > 0 && driveIndex == 2 && (disk == null || disk.getType() != BlockDevice.Type.CDROM))
                throw new IOException("Attempt to put non-CDROM into CDROM drive");
        }

        private void checkFloppyWP(int diskIndex, boolean turnOn) throws IOException
        {
            if(diskIndex < 0)
                throw new IOException("Illegal floppy disk number");
            if(diskIndex == currentDriveA || diskIndex == currentDriveB)
                throw new IOException("Can not manipulate WP of disk in drive");
            DiskImage disk = upperBackref.images.lookupDisk(diskIndex);
            if(disk == null || disk.getType() != BlockDevice.Type.FLOPPY)
                throw new IOException("Can not manipulate WP of non-floppy disk");
        }

        public synchronized void changeFloppyDisk(int driveIndex, int diskIndex) throws IOException
        {
            checkFloppyChange(driveIndex, diskIndex);
            DiskImage disk = upperBackref.images.lookupDisk(diskIndex);
            try {
                if(driveIndex == 0)
                    eRecorder.addEvent(-1, 0, getClass(), new String[]{"FDA", "" + diskIndex});
                else if(driveIndex == 1)
                    eRecorder.addEvent(-1, 0, getClass(), new String[]{"FDB", "" + diskIndex});
                else if(driveIndex == 2)
                    eRecorder.addEvent(-1, 0, getClass(), new String[]{"CDROM", "" + diskIndex});
            } catch(Exception e) {}
        }

        public synchronized void wpFloppyDisk(int diskIndex, boolean turnOn) throws IOException
        {
            checkFloppyWP(diskIndex, turnOn);
            DiskImage disk = upperBackref.images.lookupDisk(diskIndex);
            try {
                if(turnOn && !disk.isReadOnly())
                    eRecorder.addEvent(-1, 0, getClass(), new String[]{"WRITEPROTECT", "" + diskIndex});
                else if(!turnOn && disk.isReadOnly())
                    eRecorder.addEvent(-1, 0, getClass(), new String[]{"WRITEUNPROTECT", "" + diskIndex});
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
            DiskImage diskImg = upperBackref.images.lookupDisk(disk);

            if("FDA".equals(args[0])) {
                if(level <= EventRecorder.EVENT_STATE_EFFECT) {
                    checkFloppyChange(0, disk);
                    currentDriveA = disk;
                }
                if(level == EventRecorder.EVENT_EXECUTE) 
                    upperBackref.changeFloppyDisk(new GenericBlockDevice(diskImg), 0);
            } else if("FDB".equals(args[0])) {
                if(level <= EventRecorder.EVENT_STATE_EFFECT) {
                    checkFloppyChange(1, disk);
                    currentDriveB = disk;
                }
                if(level == EventRecorder.EVENT_EXECUTE) 
                    upperBackref.changeFloppyDisk(new GenericBlockDevice(diskImg), 1);
            } else if("CDROM".equals(args[0])) {
                if(level <= EventRecorder.EVENT_STATE_EFFECT) {
                    checkFloppyChange(2, disk);
                    currentCDROM = disk;
                }
                DriveSet drives = (DriveSet)upperBackref.getComponent(DriveSet.class);
                if(level == EventRecorder.EVENT_EXECUTE)
                    try {
                        ((GenericBlockDevice)drives.getHardDrive(upperBackref.cdromIndex)).configure(diskImg);
                    } catch(Exception e) {
                        System.err.println("Warning: Unable to change disk in CD-ROM drive");
                    }
            } else if("WRITEPROTECT".equals(args[0])) {
                if(level <= EventRecorder.EVENT_STATE_EFFECT)
                    checkFloppyWP(disk, true);
                if(level == EventRecorder.EVENT_EXECUTE) 
                    diskImg.setWP(true);
            } else if("WRITEUNPROTECT".equals(args[0])) {
                if(level <= EventRecorder.EVENT_STATE_EFFECT)
                    checkFloppyWP(disk, false);
                if(level == EventRecorder.EVENT_EXECUTE) 
                    diskImg.setWP(false);
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

        private Set<Integer> usedDiskSet()
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

        public long getEventTimeLowBound(String[] args) throws IOException
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
            for (HardwareComponent outer : parts) {
                if (outer.initialised()) {
                    continue;
                }

                for (HardwareComponent inner : parts) {
                    outer.acceptComponent(inner);
                }

                fullyInitialised &= outer.initialised();
            }
            count++;
        } while ((fullyInitialised == false) && (count < 100));

        if (!fullyInitialised) {
            for(HardwareComponent hwc : parts) {
                if(!hwc.initialised()) {
                    System.err.println("Error: Component of type " + hwc.getClass() + " failed to initialize.");
                }
            }
            System.err.println("Critical error: PC component initialization failed.");
            return false;
        }

        for (HardwareComponent hwc : parts) {
            if (hwc instanceof PCIBus) {
                ((PCIBus) hwc).biosInit();
            }
        }

        return true;
    }

    /**
     * Reset this PC back to its initial state.
     * <p>
     * This is roughly equivalent to a hard-reset (power down-up cycle).
     */
    protected void reset() {
        for (HardwareComponent hwc : parts) {
            hwc.reset();
        }
        configure();
    }

    public void reboot()
    {
        brb.reboot();
    }

    public static class ResetButton extends AbstractHardwareComponent implements SRDumpable, EventDispatchTarget
    {
        private EventRecorder eRecorder;
        private PC upperBackref;

        public void reboot()
        {
            try {
                eRecorder.addEvent(-1, 0, getClass(), null);
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

        public long getEventTimeLowBound(String[] args) throws IOException
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
    public HardwareComponent getComponent(Class<? extends HardwareComponent> cls) {
        if (!HardwareComponent.class.isAssignableFrom(cls)) {
            return null;
        }

        for (HardwareComponent hwc : parts) {
            if (cls.isInstance(hwc)) {
                return hwc;
            }
        }
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
    public Processor getProcessor() {
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
    public final int execute() {

        if (processor.isProtectedMode()) {
            if (processor.isVirtual8086Mode()) {
                return executeVirtual8086();
            } else {
                return executeProtected();
            }
        } else {
            return executeReal();
        }
    }

    public final int executeReal()
    {
        int x86Count = 0;

        if(rebootRequest) {
            reset();
            rebootRequest = false;
        }

        try
        {
            for (int i = 0; i < 100; i++)
            {
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
        }
        catch (ModeSwitchException e)
        {
            System.err.println("Informational: CPU switching modes: " + e.toString());
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

    public final int executeProtected() {
        int x86Count = 0;

        if(rebootRequest) {
            reset();
            rebootRequest = false;
        }

        try
        {
            for (int i = 0; i < 100; i++)
            {
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
        }
        catch (ModeSwitchException e)
        {
            System.err.println("Informational: CPU switching modes: " + e.toString());
        }
        return x86Count;
    }

    public final int executeVirtual8086() {
        int x86Count = 0;

        if(rebootRequest) {
            reset();
            rebootRequest = false;
        }

        try
        {
            for (int i = 0; i < 100; i++)
            {
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
        }
        catch (ProcessorException p)
        {
            processor.handleVirtual8086ModeException(p);
        }
        catch (ModeSwitchException e)
        {
            System.err.println("Informational: CPU switching modes: " + e.toString());
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

    public static void saveSavestate(JRSRArchiveWriter writer, PCFullStatus fullStatus, boolean movie) 
        throws IOException
    {
        fullStatus.savestateID = randomHexes(24);
        fullStatus.events.markSave(fullStatus.savestateID);

        UTFOutputLineStream lines = new UTFOutputLineStream(writer.addMember("header"));
        lines.writeLine("PROJECTID " + fullStatus.projectID);
        if(!movie)
            lines.writeLine("SAVESTATEID " + fullStatus.savestateID);
        lines.writeLine("RERECORDS " + fullStatus.rerecords);
        if(fullStatus.extraHeaders != null)
            for(int i = 0; i < fullStatus.extraHeaders.length; i++) {
                StringBuilder line = new StringBuilder();
                for(int j = 0; j < fullStatus.extraHeaders[i].length; j++) {
                    line.append(componentEscape(fullStatus.extraHeaders[i][j]));
                    line.append((char)32);
                }
                lines.writeLine(line.toString());
            }
        lines.close();

        lines = new UTFOutputLineStream(writer.addMember("initialization"));
        fullStatus.pc.getHardwareInfo().makeHWInfoSegment(lines, fullStatus.pc.diskChanger);
        lines.close();

        if(!movie) {
            FourToFiveEncoder entry = new FourToFiveEncoder(writer.addMember("savestate"));
            DeflaterOutputStream dos;
            DataOutput zip = new DataOutputStream(dos = new DeflaterOutputStream(entry));
            SRDumper dumper = new SRDumper(zip);
            dumper.dumpObject(fullStatus.pc);
            dos.close();

            OutputStream entry2 = writer.addMember("manifest");
            dumper.writeConstructorManifest(entry2);
            entry2.close();
        }
        lines = new UTFOutputLineStream(writer.addMember("events"));
        fullStatus.events.saveEvents(lines);
        lines.close();

    }

    public static PCFullStatus loadSavestate(JRSRArchiveReader reader, EventRecorder reuse) throws IOException
    {
        PCFullStatus fullStatus = new PCFullStatus();
        boolean ssPresent = false;
        UTFInputLineStream lines = new UTFInputLineStream(reader.readMember("header"));

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

        if(ssPresent) {
            InputStream entry = reader.readMember("manifest");
            if(!SRLoader.checkConstructorManifest(entry))
                throw new IOException("Wrong savestate version");
            entry.close();

            entry = new FourToFiveDecoder(reader.readMember("savestate"));
            DataInput save = new DataInputStream(new InflaterInputStream(entry));
            SRLoader loader = new SRLoader(save);
            fullStatus.pc = (PC)(loader.loadObject());
            entry.close();
        } else {
            lines = new UTFInputLineStream(reader.readMember("initialization"));
            PC.PCHardwareInfo hwInfo = PC.PCHardwareInfo.parseHWInfoSegment(lines);
            fullStatus.pc = createPC(hwInfo);
        }

        if(reuse != null) {
            fullStatus.events = reuse;
        } else {
            lines = new UTFInputLineStream(reader.readMember("events"));
            fullStatus.events = new EventRecorder(lines);
        }
        fullStatus.events.attach(fullStatus.pc, fullStatus.savestateID);

        return fullStatus;
    }
}
