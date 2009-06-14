/*
    JPC: A x86 PC Hardware Emulator for a pure Java Virtual Machine
    Release Version 2.0

    A project from the Physics Dept, The University of Oxford

    Copyright (C) 2007 Isis Innovation Limited

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

    www.physics.ox.ac.uk/jpc
*/

package org.jpc.emulator;

import org.jpc.emulator.motherboard.*;
import org.jpc.emulator.memory.*;
import org.jpc.emulator.memory.codeblock.*;
import org.jpc.emulator.pci.peripheral.*;
import org.jpc.emulator.pci.*;
import org.jpc.emulator.peripheral.*;
import org.jpc.emulator.processor.*;
import org.jpc.support.*;
import java.io.*;
import java.util.*;
import java.util.zip.*;

/**
 * The main parent class for JPC.
 */
public class PC implements org.jpc.SRDumpable
{
    public static final long PC_SAVESTATE_SR_MAGIC = 7576546867904543768L;
    public static final long PC_SAVESTATE_SR_VERSION = 1L;
    public int sysRamSize;
    private Processor processor;
    private IOPortHandler ioportHandler;
    private InterruptController irqController;
    private PhysicalAddressSpace physicalAddr;
    private LinearAddressSpace linearAddr;
    private IntervalTimer pit;
    private RTC rtc;
    private DMAController primaryDMA, secondaryDMA;
    private GateA20Handler gateA20;

    private PCIHostBridge pciHostBridge;
    private PCIISABridge pciISABridge;
    private PCIBus pciBus;
    private PIIX3IDEInterface ideInterface;

    private VGACard graphicsCard;
    private Keyboard kbdDevice;
    private PCSpeaker speaker;
    private FloppyController fdc;

    private Clock vmClock;
    private DriveSet drives;
    private DiskImageSet images;

    private VGABIOS vgaBIOS;
    private SystemBIOS sysBIOS;

    private TraceTrap traceTrap;
    private boolean hitTraceTrap;
    private boolean tripleFaulted;

    private HardwareComponent[] myParts;

    public void dumpStatusPartial(org.jpc.support.StatusDumper output)
    {
        output.println("\tsysRamSize " + sysRamSize + " tripleFaulted " + tripleFaulted);
        //hitTraceTrap not printed here.
        output.println("\tprocessor <object #" + output.objectNumber(processor) + ">"); if(processor != null) processor.dumpStatus(output);
        output.println("\tioportHandler <object #" + output.objectNumber(ioportHandler) + ">"); if(ioportHandler != null) ioportHandler.dumpStatus(output);
        output.println("\tirqController <object #" + output.objectNumber(irqController) + ">"); if(irqController != null) irqController.dumpStatus(output);
        output.println("\tphysicalAddr <object #" + output.objectNumber(physicalAddr) + ">"); if(physicalAddr != null) physicalAddr.dumpStatus(output);
        output.println("\tlinearAddr <object #" + output.objectNumber(linearAddr) + ">"); if(linearAddr != null) linearAddr.dumpStatus(output);
        output.println("\tpit <object #" + output.objectNumber(pit) + ">"); if(pit != null) pit.dumpStatus(output);
        output.println("\trtc <object #" + output.objectNumber(rtc) + ">"); if(rtc != null) rtc.dumpStatus(output);
        output.println("\tprimaryDMA <object #" + output.objectNumber(primaryDMA) + ">"); if(primaryDMA != null) primaryDMA.dumpStatus(output);
        output.println("\tsecondaryDMA <object #" + output.objectNumber(secondaryDMA) + ">"); if(secondaryDMA != null) secondaryDMA.dumpStatus(output);
        output.println("\tgateA20 <object #" + output.objectNumber(gateA20) + ">"); if(gateA20 != null) gateA20.dumpStatus(output);
        output.println("\tpciHostBridge <object #" + output.objectNumber(pciHostBridge) + ">"); if(pciHostBridge != null) pciHostBridge.dumpStatus(output);
        output.println("\tpciISABridge <object #" + output.objectNumber(pciISABridge) + ">"); if(pciISABridge != null) pciISABridge.dumpStatus(output);
        output.println("\tpciBus <object #" + output.objectNumber(pciBus) + ">"); if(pciBus != null) pciBus.dumpStatus(output);
        output.println("\tideInterface <object #" + output.objectNumber(ideInterface) + ">"); if(ideInterface != null) ideInterface.dumpStatus(output);
        output.println("\tgraphicsCard <object #" + output.objectNumber(graphicsCard) + ">"); if(graphicsCard != null) graphicsCard.dumpStatus(output);
        output.println("\tkbdDevice <object #" + output.objectNumber(kbdDevice) + ">"); if(kbdDevice != null) kbdDevice.dumpStatus(output);
        output.println("\tspeaker <object #" + output.objectNumber(speaker) + ">"); if(speaker != null) speaker.dumpStatus(output);
        output.println("\tfdc <object #" + output.objectNumber(fdc) + ">"); if(fdc != null) fdc.dumpStatus(output);
        output.println("\tvmClock <object #" + output.objectNumber(vmClock) + ">"); if(vmClock != null) vmClock.dumpStatus(output);
        output.println("\tdrives <object #" + output.objectNumber(drives) + ">"); if(drives != null) drives.dumpStatus(output);
        output.println("\timages <object #" + output.objectNumber(images) + ">"); if(images != null) images.dumpStatus(output);
        output.println("\tvgaBIOS <object #" + output.objectNumber(vgaBIOS) + ">"); if(vgaBIOS != null) vgaBIOS.dumpStatus(output);
        output.println("\tsysBIOS <object #" + output.objectNumber(sysBIOS) + ">"); if(sysBIOS != null) sysBIOS.dumpStatus(output);
        output.println("\ttraceTrap <object #" + output.objectNumber(traceTrap) + ">"); if(traceTrap != null) traceTrap.dumpStatus(output);
        for (int i=0; i < myParts.length; i++) {
            output.println("\tmyParts[" + i + "] <object #" + output.objectNumber(myParts[i]) + ">"); if(myParts[i] != null) myParts[i].dumpStatus(output);
        }
    }

    public void dumpStatus(org.jpc.support.StatusDumper output)
    {
        if(output.dumped(this))
            return;

        output.println("#" + output.objectNumber(this) + ": PC:");
        dumpStatusPartial(output);
        output.endObject();
    }

    public void dumpSR(org.jpc.support.SRDumper output) throws IOException
    {
        if(output.dumped(this))
            return;
        dumpSRPartial(output);
        output.endObject();
    }

    public void dumpSRPartial(org.jpc.support.SRDumper output) throws IOException
    {
        output.dumpLong(PC_SAVESTATE_SR_MAGIC);
        output.dumpLong(PC_SAVESTATE_SR_VERSION);
        output.dumpInt(sysRamSize);
        output.dumpObject(processor);
        output.dumpObject(ioportHandler);
        output.dumpObject(irqController);
        output.dumpObject(physicalAddr);
        output.dumpObject(linearAddr);
        output.dumpObject(pit);
        output.dumpObject(rtc);
        output.dumpObject(primaryDMA);
        output.dumpObject(secondaryDMA);
        output.dumpObject(gateA20);
        output.dumpObject(pciHostBridge);
        output.dumpObject(pciISABridge);
        output.dumpObject(pciBus);
        output.dumpObject(ideInterface);
        output.dumpObject(graphicsCard);
        output.dumpObject(kbdDevice);
        output.dumpObject(speaker);
        output.dumpObject(fdc);
        output.dumpObject(vmClock);
        output.dumpObject(drives);
        output.dumpObject(images);
        output.dumpObject(vgaBIOS);
        output.dumpObject(sysBIOS);
        output.dumpObject(traceTrap);
        output.dumpBoolean(hitTraceTrap);
        output.dumpBoolean(tripleFaulted);
        output.dumpInt(myParts.length);
        for(int i = 0; i < myParts.length; i++)
            output.dumpObject(myParts[i]);
    }

    public PC(org.jpc.support.SRLoader input) throws IOException
    {
        input.objectCreated(this);
        if(input.loadLong() != PC_SAVESTATE_SR_MAGIC)
            throw new IOException("Not a JPC SR savestate file.");
        if(input.loadLong() != PC_SAVESTATE_SR_VERSION)
            throw new IOException("Unsupported version of JPC SR savestate file.");
        sysRamSize = input.loadInt();
        processor = (Processor)(input.loadObject());
        ioportHandler = (IOPortHandler)(input.loadObject());
        irqController = (InterruptController)(input.loadObject());
        physicalAddr = (PhysicalAddressSpace)(input.loadObject());
        linearAddr = (LinearAddressSpace)(input.loadObject());
        pit = (IntervalTimer)(input.loadObject());
        rtc = (RTC)(input.loadObject());
        primaryDMA = (DMAController)(input.loadObject());
        secondaryDMA = (DMAController)(input.loadObject());
        gateA20 = (GateA20Handler)(input.loadObject());
        pciHostBridge = (PCIHostBridge)(input.loadObject());
        pciISABridge = (PCIISABridge)(input.loadObject());
        pciBus = (PCIBus)(input.loadObject());
        ideInterface = (PIIX3IDEInterface)(input.loadObject());
        graphicsCard = (VGACard)(input.loadObject());
        kbdDevice = (Keyboard)(input.loadObject());
        speaker = (PCSpeaker)(input.loadObject());
        fdc = (FloppyController)(input.loadObject());
        vmClock = (Clock)(input.loadObject());
        drives = (DriveSet)(input.loadObject());
        images = (DiskImageSet)(input.loadObject());
        vgaBIOS = (VGABIOS)(input.loadObject());
        sysBIOS = (SystemBIOS)(input.loadObject());
        traceTrap = (TraceTrap)(input.loadObject());
        hitTraceTrap = input.loadBoolean();
        tripleFaulted = input.loadBoolean();
        myParts = new HardwareComponent[input.loadInt()];
        for(int i = 0; i < myParts.length; i++)
            myParts[i] = (HardwareComponent)(input.loadObject());
    }

    public static org.jpc.SRDumpable loadSR(org.jpc.support.SRLoader input, Integer id) throws IOException
    {
        org.jpc.SRDumpable x = new PC(input);
        input.endObject();
        return x;
    }

    public boolean getAndClearTripleFaulted()
    {
        boolean flag = tripleFaulted;
        tripleFaulted = false;
        return flag;
    }

    public PC(Clock clock, DriveSet drives, int pagesMemory, int cpuClockDivider, String sysBIOSImg, String vgaBIOSImg,
        long initTime) throws IOException
    {
        sysRamSize = 4096 * pagesMemory;
        this.drives = drives;
        processor = new Processor(cpuClockDivider);
        vmClock = clock;
        //Motherboard
        physicalAddr = new PhysicalAddressSpace(sysRamSize);
        for (int i=0; i < sysRamSize; i+= AddressSpace.BLOCK_SIZE)
            //physicalAddr.allocateMemory(i, new ByteArrayMemory(blockSize));
            //physicalAddr.allocateMemory(i, new CompressedByteArrayMemory(blockSize));
            physicalAddr.allocateMemory(i, new LazyMemory(AddressSpace.BLOCK_SIZE));

        traceTrap = new TraceTrap();

        linearAddr = new LinearAddressSpace();
               ioportHandler = new IOPortHandler();
        irqController = new InterruptController();
        primaryDMA = new DMAController(false, true);
        secondaryDMA = new DMAController(false, false);
        rtc = new RTC(0x70, 8, sysRamSize, initTime);
        pit = new IntervalTimer(0x40, 0);
        gateA20 = new GateA20Handler();

        images = new DiskImageSet();

        //Peripherals
        ideInterface = new PIIX3IDEInterface();
        graphicsCard = new VGACard();

        kbdDevice = new Keyboard();
        fdc = new FloppyController();
        speaker = new PCSpeaker();

        //PCI Stuff
        pciHostBridge = new PCIHostBridge();
        pciISABridge = new PCIISABridge();
        pciBus = new PCIBus();

        //BIOSes
        sysBIOS = new SystemBIOS(sysBIOSImg);
        vgaBIOS = new VGABIOS(vgaBIOSImg);

        myParts = new HardwareComponent[]{processor, vmClock, physicalAddr, linearAddr,
                                          ioportHandler, irqController,
                                          primaryDMA, secondaryDMA, rtc, pit, gateA20,
                                          pciHostBridge, pciISABridge, pciBus,
                                          ideInterface, drives,
                                          graphicsCard,
                                          kbdDevice, fdc, speaker,
                                          sysBIOS, vgaBIOS, traceTrap};

        if (!configure())
            throw new IllegalStateException("PC Configuration failed");
    }

    public void start()
    {
        vmClock.resume();
    }

    public void stop()
    {
        vmClock.pause();
    }

    public void dispose()
    {
        stop();
        LazyCodeBlockMemory.dispose();
    }

    public void setFloppy(org.jpc.support.BlockDevice drive, int i)
    {
        if ((i < 0) || (i > 1))
            return;
        fdc.setDrive(drive, i);
    }

    public synchronized void runBackgroundTasks()
    {
        notify();
    }

    public DriveSet getDrives()
    {
        return drives;
    }

    public DiskImageSet getDisks()
    {
        return images;
    }

    public int getBootType()
    {
        return drives.getBootType();
    }

    private boolean configure()
    {
        boolean fullyInitialised;
        int count = 0;
        do
        {
            fullyInitialised = true;
            for (int j = 0; j < myParts.length; j++)
            {
                if (myParts[j].initialised() == false)
                {
                    for (int i = 0; i < myParts.length; i++)
                        myParts[j].acceptComponent(myParts[i]);

                    fullyInitialised &= myParts[j].initialised();
                }
            }
            count++;
        }
        while ((fullyInitialised == false) && (count < 100));

        if (count == 100)
        {
            for (int i=0; i<myParts.length; i++)
                System.out.println("Part "+i+" ("+myParts[i].getClass()+") "+myParts[i].initialised());
            return false;
        }

        for (int i = 0; i < myParts.length; i++)
        {
            if (myParts[i] instanceof PCIBus)
                ((PCIBus)myParts[i]).biosInit();
        }

        return true;
    }

    private void linkComponents()
    {
        boolean fullyInitialised;
        int count = 0;
        do
        {
            fullyInitialised = true;
            for (int j = 0; j < myParts.length; j++)
            {
                if (myParts[j].updated() == false)
                {
                    for (int i = 0; i < myParts.length; i++)
                        myParts[j].updateComponent(myParts[i]);

                    fullyInitialised &= myParts[j].updated();
                }
            }
            count++;
        }
        while ((fullyInitialised == false) && (count < 100));

        if (count == 100)
        {
            for (int i=0; i<myParts.length; i++)
                System.out.println("Part "+i+" ("+myParts[i].getClass()+") "+myParts[i].updated());
        }
    }

    public void reset()
    {
        for (int i = 0; i < myParts.length; i++)
        {
            if (myParts[i] == this)
                continue;
            myParts[i].reset();
        }
        configure();
    }

    public Keyboard getKeyboard()
    {
        return kbdDevice;
    }

    public Processor getProcessor()
    {
        return processor;
    }

    public VGACard getGraphicsCard()
    {
        return graphicsCard;
    }

    public PhysicalAddressSpace getPhysicalMemory()
    {
        return physicalAddr;
    }

    public LinearAddressSpace getLinearMemory()
    {
        return linearAddr;
    }

    public Clock getSystemClock()
    {
        return vmClock;
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

    public static PC createPC(String[] args, Clock clock) throws IOException
    {
        PC pc;
        DriveSet disks = DriveSet.buildFromArgs(args);
        int cpuClockDivider = ArgProcessor.extractIntArg(args, "cpudivider", 25);
        int memorySize = ArgProcessor.extractIntArg(args, "memsize", 16384);
        String sysBIOSImg = ArgProcessor.scanArgs(args, "sysbios", "BIOS");
        String vgaBIOSImg = ArgProcessor.scanArgs(args, "vgabios", "VGABIOS");
        String initTimeS = ArgProcessor.scanArgs(args, "inittime", null);
        long initTime;
        try {
            initTime = Long.parseLong(initTimeS, 10);
            if(initTime < 0 || initTime > 4102444799999L)
               throw new Exception("Invalid time value.");
        } catch(Exception e) { 
            if(initTimeS != null)
                System.err.println("Invalid -inittime. Using default value of 1 000 000 000 000.");
            initTime = 1000000000000L;
        }
        if(cpuClockDivider < 1 || cpuClockDivider > 256) {
            System.err.println("CPU Clock divider out of range, using default value of 25.");
            cpuClockDivider = 25;
        }

        pc = new PC(clock, disks, memorySize, cpuClockDivider, sysBIOSImg, vgaBIOSImg, initTime);

        String fdaFileName = ArgProcessor.findArg(args, "-fda", null);
        if(fdaFileName != null) {
            int image = pc.getDisks().addDisk(new DiskImage(fdaFileName, false));
            DiskImage img = pc.getDisks().lookupDisk(image);
            BlockDevice device = new GenericBlockDevice(img, BlockDevice.TYPE_FLOPPY);
            pc.setFloppy(device, 0);
        }
        String fdbFileName = ArgProcessor.findArg(args, "-fdb", null);
        if(fdbFileName != null) {
            int image = pc.getDisks().addDisk(new DiskImage(fdaFileName, false));
            DiskImage img = pc.getDisks().lookupDisk(image);
            BlockDevice device = new GenericBlockDevice(img, BlockDevice.TYPE_FLOPPY);
            pc.setFloppy(device, 1);
        }

        return pc;
    }

    public final int execute()
    {
        int x86Count = 0;
        AddressSpace addressSpace = null;
        if (processor.isProtectedMode())
            addressSpace = linearAddr;
        else
            addressSpace = physicalAddr;

        // do it multiple times
        try
        {
            for (int i=0; i<100; i++) {
                int delta;
                try {
                    delta = addressSpace.execute(processor, processor.getInstructionPointer());
                } catch(org.jpc.emulator.processor.Processor.TripleFault e) {
                    reset();      //Reboot the system to get the CPU back online.
                    hitTraceTrap = true;
                    tripleFaulted = true;
                    break;
                }
                x86Count += delta;
                processor.instructionsExecuted += delta;
                if(traceTrap.getAndClearTrapActive()) {
                    hitTraceTrap = true;
                    break;
                }
            }
        }
        catch (ModeSwitchException e) {}

        return x86Count;
    }


    public final CodeBlock decodeCodeBlockAt(int address)
    {
        AddressSpace addressSpace = null;
        if (processor.isProtectedMode())
            addressSpace = linearAddr;
        else
            addressSpace = physicalAddr;
        CodeBlock block= addressSpace.decodeCodeBlockAt(processor, address);
        return block;
    }
}
