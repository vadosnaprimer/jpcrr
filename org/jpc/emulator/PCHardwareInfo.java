package org.jpc.emulator;

import org.jpc.images.ImageID;
import org.jpc.diskimages.DiskImageSet;
import org.jpc.images.COWImage;
import org.jpc.jrsr.UTFOutputLineStream;
import static org.jpc.emulator.peripheral.SoundCard.CONFIGWORD_PCM;
import static org.jpc.emulator.peripheral.SoundCard.CONFIGWORD_FM;
import static org.jpc.emulator.peripheral.SoundCard.CONFIGWORD_UART;
import static org.jpc.emulator.peripheral.SoundCard.CONFIGWORD_GAMEPORT;
import static org.jpc.emulator.peripheral.SoundCard.CONFIGWORD_GAMEPORT;
import static org.jpc.emulator.peripheral.SoundCard.DEFAULT_PCM_IO;
import static org.jpc.emulator.peripheral.SoundCard.DEFAULT_PCM_IRQ;
import static org.jpc.emulator.peripheral.SoundCard.DEFAULT_PCM_LDMA;
import static org.jpc.emulator.peripheral.SoundCard.DEFAULT_PCM_HDMA;
import static org.jpc.emulator.peripheral.SoundCard.DEFAULT_UART_IO;
import static org.jpc.emulator.peripheral.SoundCard.DEFAULT_UART_IRQ;
import org.jpc.jrsr.UTFInputLineStream;
import static org.jpc.Misc.nextParseLine;
import java.util.*;
import java.io.*;

public class PCHardwareInfo implements SRDumpable, Cloneable
{
    public ImageID biosID;
    public ImageID vgaBIOSID;
    public ImageID hdaID;
    public ImageID hdbID;
    public ImageID hdcID;
    public ImageID hddID;
    public DiskImageSet images;
    public int initFDAIndex;
    public int initFDBIndex;
    public int initCDROMIndex;
    public long initRTCTime;
    public int cpuDivider;
    public int memoryPages;
    public int scConfigWord;
    public int scPCMIO;
    public int scPCMIRQ;
    public int scPCMLDMA;
    public int scPCMHDMA;
    public int scUARTIO;
    public int scUARTIRQ;
    public Map<String, Set<String>> hwModules;
    public DriveSet.BootType bootType;
    public Map<String, Boolean> booleanOptions;
    public Map<String, Integer> intOptions;

    public Object clone()
    {
        PCHardwareInfo h = new PCHardwareInfo();
        h.biosID = biosID;
        h.vgaBIOSID = vgaBIOSID;
        h.hdaID = hdaID;
        h.hdbID = hdbID;
        h.hdcID = hdcID;
        h.hddID = hddID;
        h.images = images;
        h.initFDAIndex = initFDAIndex;
        h.initFDBIndex = initFDBIndex;
        h.initCDROMIndex = initCDROMIndex;
        h.initRTCTime = initRTCTime;
        h.cpuDivider = cpuDivider;
        h.memoryPages = memoryPages;
        h.scConfigWord = scConfigWord;
        h.scPCMIO = scPCMIO;
        h.scPCMIRQ = scPCMIRQ;
        h.scPCMLDMA = scPCMLDMA;
        h.scPCMHDMA = scPCMHDMA;
        h.scUARTIO = scUARTIO;
        h.scUARTIRQ = scUARTIRQ;
        h.hwModules = hwModules;
        h.bootType = bootType;
        h.booleanOptions = booleanOptions;
        h.intOptions = intOptions;
        return h;
    }

    public void dumpStatusPartial(StatusDumper output2) throws IOException
    {
        if(output2 != null)
            return;

        PrintStream output = System.err;

        output.println("BIOS " + biosID);
        output.println("VGABIOS " + vgaBIOSID);
        if(hdaID != null)
            output.println("HDA " + hdaID);
        if(hdbID != null)
            output.println("HDB " + hdbID);
        if(hdcID != null)
            output.println("HDC " + hdcID);
        if(hddID != null)
            output.println("HDD " + hddID);
        int disks = 1 + images.highestDiskIndex();
        for(int i = 0; i < disks; i++) {
            COWImage disk = images.lookupDisk(i);
            if(disk != null)
                output.println("DISK " + i + " " + disk.getID());
        }
        if(initFDAIndex >= 0)
            output.println("FDA " + initFDAIndex);
        if(initFDBIndex >= 0)
            output.println("FDB " + initFDBIndex);
        if(initCDROMIndex >= 0)
            output.println("CDROM " + initCDROMIndex);
        output.println("INITIALTIME " + initRTCTime);
        output.println("CPUDIVIDER " + (cpuDivider - 1));
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
        output.println("SCCONFIGWORD" + scConfigWord);
        output.println("SCPCMIO" + scPCMIO);
        output.println("SCPCMIRQ" + scPCMIRQ);
        output.println("SCPCMLDMA" + scPCMLDMA);
        output.println("SCPCMHDMA" + scPCMHDMA);
        output.println("SCUARTIO" + scUARTIO);
        output.println("SCUARTIRQ" + scUARTIRQ);
        if(hwModules != null && !hwModules.isEmpty()) {
            for(Map.Entry<String,Set<String>> e : hwModules.entrySet()) {
                for(String p : e.getValue())
                    if(p != null)
                        output.println("LOADMODULEA " + e.getKey() + "(" + p + ")");
                    else
                        output.println("LOADMODULE " + e.getKey());
            }
        }
        if(booleanOptions != null)
            for(Map.Entry<String, Boolean> setting : booleanOptions.entrySet())
                if(setting.getValue().booleanValue())
                    output.println(setting.getKey());
        if(intOptions != null)
            for(Map.Entry<String, Integer> setting : intOptions.entrySet())
                if(setting.getValue().intValue() != 0)
                    output.println(setting.getKey() + " " + setting.getValue());
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
        output.dumpObject(biosID);
        output.dumpObject(vgaBIOSID);
        output.dumpObject(hdaID);
        output.dumpObject(hdbID);
        output.dumpObject(hdcID);
        output.dumpObject(hddID);
        output.dumpObject(images);
        output.dumpInt(initFDAIndex);
        output.dumpInt(initFDBIndex);
        output.dumpInt(initCDROMIndex);
        output.dumpLong(initRTCTime);
        output.dumpInt(cpuDivider);
        output.dumpInt(memoryPages);
        output.dumpInt(scConfigWord);
        output.dumpInt(scPCMIO);
        output.dumpInt(scPCMIRQ);
        output.dumpInt(scPCMLDMA);
        output.dumpInt(scPCMHDMA);
        output.dumpInt(scUARTIO);
        output.dumpInt(scUARTIRQ);
        if(hwModules != null) {
            output.dumpBoolean(true);
            for(Map.Entry<String,Set<String>> e : hwModules.entrySet()) {
                output.dumpBoolean(true);
                output.dumpString(e.getKey());
                for(String s : e.getValue()) {
                    output.dumpBoolean(true);
                    output.dumpString(s);
                }
                output.dumpBoolean(false);
            }
            output.dumpBoolean(false);
        } else
            output.dumpBoolean(false);
        output.dumpByte(DriveSet.BootType.toNumeric(bootType));
        //The new system setting stuff.
        if(intOptions != null)
            for(Map.Entry<String, Boolean> setting : booleanOptions.entrySet())
                if(setting.getValue().booleanValue()) {
                    output.dumpBoolean(true);
                    output.dumpString(setting.getKey());
                }
        output.dumpBoolean(false);
        if(intOptions != null)
            for(Map.Entry<String, Integer> setting : intOptions.entrySet())
                if(setting.getValue().intValue() != 0) {
                    output.dumpBoolean(true);
                    output.dumpString(setting.getKey());
                    output.dumpInt(setting.getValue());
                }
        output.dumpBoolean(false);
    }

    public PCHardwareInfo()
    {
        images = new DiskImageSet();
        scConfigWord = CONFIGWORD_PCM | CONFIGWORD_FM | CONFIGWORD_UART | CONFIGWORD_GAMEPORT;
        scPCMIO = DEFAULT_PCM_IO;
        scPCMIRQ = DEFAULT_PCM_IRQ;
        scPCMLDMA = DEFAULT_PCM_LDMA;
        scPCMHDMA = DEFAULT_PCM_HDMA;
        scUARTIO = DEFAULT_UART_IO;
        scUARTIRQ = DEFAULT_UART_IRQ;
    }

    public PCHardwareInfo(SRLoader input) throws IOException
    {
        input.objectCreated(this);
        biosID = (ImageID)input.loadObject();
        vgaBIOSID = (ImageID)input.loadObject();
        hdaID = (ImageID)input.loadObject();
        hdbID = (ImageID)input.loadObject();
        hdcID = (ImageID)input.loadObject();
        hddID = (ImageID)input.loadObject();
        images = (DiskImageSet)input.loadObject();
        initFDAIndex = input.loadInt();
        initFDBIndex = input.loadInt();
        initCDROMIndex = input.loadInt();
        initRTCTime = input.loadLong();
        cpuDivider = input.loadInt();
        memoryPages = input.loadInt();
        scConfigWord = input.loadInt();
        scPCMIO = input.loadInt();
        scPCMIRQ = input.loadInt();
        scPCMLDMA = input.loadInt();
        scPCMHDMA = input.loadInt();
        scUARTIO = input.loadInt();
        scUARTIRQ = input.loadInt();
        boolean present = input.loadBoolean();
        if(present) {
            hwModules = new LinkedHashMap<String, Set<String>>();
            present = input.loadBoolean();
            while(present) {
                String name = input.loadString();
                hwModules.put(name, new LinkedHashSet<String>());
                boolean present2 = input.loadBoolean();
                while(present2) {
                    String params = input.loadString();
                    present2 = input.loadBoolean();
                    hwModules.get(name).add(params);
                }
                present = input.loadBoolean();
            }
        }
        bootType = DriveSet.BootType.fromNumeric(input.loadByte());
        booleanOptions = new TreeMap<String, Boolean>();
        intOptions = new TreeMap<String, Integer>();
        //Real settings stuff.
        while(input.loadBoolean())
            booleanOptions.put(input.loadString(), true);
        while(input.loadBoolean()) {
            String name = input.loadString();
            int value = input.loadInt();
            intOptions.put(name, value);
        }
    }

    private String didString(ImageID id)
    {
        if(id == null)
            return null;
        return id.getIDAsString();
    }

    public void makeHWInfoSegment(UTFOutputLineStream output, PC.DiskChanger changer) throws IOException
    {
        output.encodeLine("BIOS", didString(biosID));
        output.encodeLine("VGABIOS", didString(vgaBIOSID));
        output.encodeLine("HDA", didString(hdaID));
        output.encodeLine("HDB", didString(hdbID));
        output.encodeLine("HDC", didString(hdcID));
        output.encodeLine("HDD", didString(hddID));
        //TODO: When event recording becomes available, only save the disk images needed.
        Set<Integer> usedDisks = changer.usedDiskSet();
        int disks = 1 + images.highestDiskIndex();
        for(int i = 0; i < disks; i++) {
            COWImage disk = images.lookupDisk(i);
            if(disk != null && usedDisks.contains(i)) {
                output.encodeLine("DISK", i, disk.getID().getIDAsString());
                output.encodeLine("DISKNAME", i, disk.getName());
            }
        }
        if(initFDAIndex >= 0) output.encodeLine("FDA", initFDAIndex);
        if(initFDBIndex >= 0) output.encodeLine("FDB", initFDBIndex);
        if(initCDROMIndex >= 0) output.encodeLine("CDROM", initCDROMIndex);
        output.encodeLine("INITIALTIME", initRTCTime);
        output.encodeLine("CPUDIVIDER", cpuDivider);
        output.encodeLine("MEMORYSIZE", memoryPages);
        if(bootType == DriveSet.BootType.FLOPPY) output.encodeLine("BOOT", "FLOPPY");
        else if(bootType == DriveSet.BootType.HARD_DRIVE) output.encodeLine("BOOT", "HDD");
        else if(bootType == DriveSet.BootType.CDROM) output.encodeLine("BOOT", "CDROM");
        else if(bootType == null)
            ;
        else
            throw new IOException("Unknown boot type");
        output.encodeLine("SCCONFIGWORD", scConfigWord);
        output.encodeLine("SCPCMIO", scPCMIO);
        output.encodeLine("SCPCMIRQ", scPCMIRQ);
        output.encodeLine("SCPCMLDMA", scPCMLDMA);
        output.encodeLine("SCPCMHDMA", scPCMHDMA);
        output.encodeLine("SCUARTIO", scUARTIO);
        output.encodeLine("SCUARTIRQ", scUARTIRQ);
        if(hwModules != null && !hwModules.isEmpty()) {
            for(Map.Entry<String,Set<String>> e : hwModules.entrySet()) {
                for(String p : e.getValue())
                    if(p != null)
                        output.encodeLine("LOADMODULEA", e.getKey(), p);
                    else
                        output.encodeLine("LOADMODULE", e.getKey());
            }
        }
        if(booleanOptions != null)
            for(Map.Entry<String, Boolean> setting : booleanOptions.entrySet())
                if(setting.getValue().booleanValue())
                    output.encodeLine(setting.getKey());
        if(intOptions != null)
            for(Map.Entry<String, Integer> setting : intOptions.entrySet())
                if(setting.getValue().intValue() != 0)
                    output.encodeLine(setting.getKey(), setting.getValue().toString());
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
        if("DISKNAME".equals(op))
            return 3;
        if("SCCONFIGWORD".equals(op))
            return 2;
        if("SCPCMIO".equals(op))
            return 2;
        if("SCPCMIRQ".equals(op))
            return 2;
        if("SCPCMLDMA".equals(op))
            return 2;
        if("SCPCMHDMA".equals(op))
            return 2;
        if("SCUARTIO".equals(op))
            return 2;
        if("SCUARTIRQ".equals(op))
            return 2;
        return 0;
    }

    public static PCHardwareInfo parseHWInfoSegment(UTFInputLineStream input) throws IOException
    {

        PCHardwareInfo hw = new PCHardwareInfo();
        hw.booleanOptions = new TreeMap<String, Boolean>();
        hw.intOptions = new TreeMap<String, Integer>();
        hw.initFDAIndex = -1;
        hw.initFDBIndex = -1;
        hw.initCDROMIndex = -1;
        hw.scConfigWord = CONFIGWORD_PCM | CONFIGWORD_FM | CONFIGWORD_UART | CONFIGWORD_GAMEPORT;
        hw.scPCMIO = DEFAULT_PCM_IO;
        hw.scPCMIRQ = DEFAULT_PCM_IRQ;
        hw.scPCMLDMA = DEFAULT_PCM_LDMA;
        hw.scPCMHDMA = DEFAULT_PCM_HDMA;
        hw.scUARTIO = DEFAULT_UART_IO;
        hw.scUARTIRQ = DEFAULT_UART_IRQ;
        hw.images = new DiskImageSet();
        hw.hwModules = new LinkedHashMap<String, Set<String>>();
        String[] components = nextParseLine(input);
        while(components != null) {
            if(componentsForLine(components[0]) == 0 && components.length == 1) {
                hw.booleanOptions.put(components[0], true);
                components = nextParseLine(input);
                continue;
            }
            if(componentsForLine(components[0]) == 0 && components.length == 2) {
                try {
                    hw.intOptions.put(components[0], Integer.parseInt(components[1]));
                } catch(Exception e) {
                    throw new IOException("Bad " + components[0] + " line in initialization segment");
                }
                components = nextParseLine(input);
                continue;
            }
            if(components.length != componentsForLine(components[0]))
                throw new IOException("Bad " + components[0] + " line in ininitialization segment: " +
                    "expected " + componentsForLine(components[0]) + " components, got " + components.length);
            if("BIOS".equals(components[0]))
                hw.biosID = new ImageID(components[1]);
            else if("VGABIOS".equals(components[0]))
                hw.vgaBIOSID = new ImageID(components[1]);
            else if("HDA".equals(components[0]))
                hw.hdaID = new ImageID(components[1]);
            else if("HDB".equals(components[0]))
                hw.hdbID = new ImageID(components[1]);
            else if("HDC".equals(components[0]))
                hw.hdcID = new ImageID(components[1]);
            else if("HDD".equals(components[0]))
                hw.hddID = new ImageID(components[1]);
            else if("DISK".equals(components[0])) {
                int id;
                try {
                    id = Integer.parseInt(components[1]);
                    if(id < 0)
                        throw new NumberFormatException("Bad id");
                } catch(NumberFormatException e) {
                    throw new IOException("Bad DISK line in initialization segment");
                }
                hw.images.addDisk(id, new COWImage(new ImageID(components[2])));
            } else if("DISKNAME".equals(components[0])) {
                int id;
                try {
                    id = Integer.parseInt(components[1]);
                    if(id < 0)
                        throw new NumberFormatException("Bad id");
                    hw.images.lookupDisk(id).setName(components[2]);
                } catch(Exception e) {
                    throw new IOException("Bad DISKNAME line in initialization segment");
                }
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
            } else if("SCCONFIGWORD".equals(components[0])) {
                int id;
                try {
                    id = Integer.parseInt(components[1]);
                    if((id & ~(CONFIGWORD_PCM | CONFIGWORD_FM | CONFIGWORD_UART | CONFIGWORD_GAMEPORT)) != 0)
                        throw new NumberFormatException("Bad Config word");
                } catch(NumberFormatException e) {
                    throw new IOException("Bad SCCONFIGWORD line in initialization segment");
                }
                hw.scConfigWord = id;
            } else if("SCPCMIO".equals(components[0])) {
                int id;
                try {
                    id = Integer.parseInt(components[1]);
                    if(id < 0 || id > 65520)
                        throw new NumberFormatException("Bad PCM base I/O address");
                } catch(NumberFormatException e) {
                    throw new IOException("Bad SCPCMIO line in initialization segment");
                }
                hw.scPCMIO = id;
            } else if("SCPCMIRQ".equals(components[0])) {
                int id;
                try {
                    id = Integer.parseInt(components[1]);
                    if(id != 2 && id != 5 && id != 7 && id != 10)
                        throw new NumberFormatException("Bad PCM IRQ");
                } catch(NumberFormatException e) {
                    throw new IOException("Bad SCPCMIRQ line in initialization segment");
                }
                hw.scPCMIRQ = id;
            } else if("SCPCMLDMA".equals(components[0])) {
                int id;
                try {
                    id = Integer.parseInt(components[1]);
                    if(id < 0 || id > 3)
                        throw new NumberFormatException("Bad PCM Low DMA");
                } catch(NumberFormatException e) {
                    throw new IOException("Bad SCPCMLDMA line in initialization segment");
                }
                hw.scPCMLDMA = id;
            } else if("SCPCMHDMA".equals(components[0])) {
                int id;
                try {
                    id = Integer.parseInt(components[1]);
                    if(id < 4 || id > 7)
                        throw new NumberFormatException("Bad PCM High DMA");
                } catch(NumberFormatException e) {
                    throw new IOException("Bad SCPCMHDMA line in initialization segment");
                }
                hw.scPCMHDMA = id;
            } else if("SCUARTIO".equals(components[0])) {
                int id;
                try {
                    id = Integer.parseInt(components[1]);
                    if(id < 0 || id > 65534)
                        throw new NumberFormatException("Bad UART base I/O address");
                } catch(NumberFormatException e) {
                    throw new IOException("Bad SCUARTIO line in initialization segment");
                }
                hw.scUARTIO = id;
            } else if("SCUARTIRQ".equals(components[0])) {
                int id;
                try {
                    id = Integer.parseInt(components[1]);
                    if(id < 0 || id > 15)
                        throw new NumberFormatException("Bad UART IRQ");
                } catch(NumberFormatException e) {
                    throw new IOException("Bad SCUARTIRQ line in initialization segment");
                }
                hw.scUARTIRQ = id;
            } else if("LOADMODULE".equals(components[0])) {
                if(!hw.hwModules.containsKey(components[1]))
                    hw.hwModules.put(components[1],new LinkedHashSet<String>());
                hw.hwModules.get(components[1]).add(null);
            } else if("LOADMODULEA".equals(components[0])) {
                if(!hw.hwModules.containsKey(components[1]))
                    hw.hwModules.put(components[1],new LinkedHashSet<String>());
                hw.hwModules.get(components[1]).add(components[2]);
            }
            components = nextParseLine(input);
        }
        return hw;
    }
}
