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
public class PC
{
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

    private VGABIOS vgaBIOS;
    private SystemBIOS sysBIOS;

    private TraceTrap traceTrap;
    private boolean hitTraceTrap;

    private HardwareComponent[] myParts;
    private Magic magic;

    public PC(Clock clock, DriveSet drives, int pagesMemory, int cpuClockDivider) throws IOException
    {
        magic = new Magic(Magic.PC_MAGIC_V1);
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
        rtc = new RTC(0x70, 8, sysRamSize);
        pit = new IntervalTimer(0x40, 0);
        gateA20 = new GateA20Handler();

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
        sysBIOS = new SystemBIOS("bios.bin");
        vgaBIOS = new VGABIOS("vgabios.bin");

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

    public BlockDevice getBootDevice()
    {
        return drives.getBootDevice();
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

    public boolean saveState(ZipOutputStream zip2) throws IOException
    {
        //save state of of Hardware Components
        //processor     DONE
        //rtc           DONE
        //pit           DONE (-TImerChannel.timer/irq)

        try
        {
            ZipEntry entry = new ZipEntry("HardwareSavestate");
            zip2.putNextEntry(entry);
            DataOutput zip = new DataOutputStream(zip2);

            magic.dumpState(zip);
            saveComponent(zip, drives, "Drives");
            saveComponent(zip, vmClock, "System Clock");
            saveComponent(zip, physicalAddr, "Physical Address Space");
            saveComponent(zip, linearAddr, "Linear Address Space");
            saveComponent(zip, processor, "Processor");
            saveComponent(zip, ioportHandler, "IO Port Handler");
            saveComponent(zip, irqController, "IRQ Controller");
            saveComponent(zip, primaryDMA, "DMA Controller #1");
            saveComponent(zip, secondaryDMA, "DMA Controller #2");
            saveComponent(zip, rtc, "Real Time Clock");
            saveComponent(zip, pit, "Interval Timer");
            saveComponent(zip, gateA20, "A20 Gate");
            saveComponent(zip, pciHostBridge, "PCI Host Bridge");
            saveComponent(zip, pciISABridge, "PCI ISA Bridge");
            saveComponent(zip, pciBus, "PCI Bus");
            saveComponent(zip, ideInterface, "IDE Interface");
            saveComponent(zip, sysBIOS, "System BIOS");
            saveComponent(zip, vgaBIOS, "Video BIOS");
            saveComponent(zip, kbdDevice, "Keyboard");
            saveComponent(zip, fdc, "Floppy Disk Controller");
            saveComponent(zip, graphicsCard, "VGA Controller");
            saveComponent(zip, speaker, "PC Speaker");
            //TraceTrap intentionally omitted.
            zip2.closeEntry();
            System.out.println("Final Save size: " + entry.getSize());
        } catch (IOException e) {
            e.printStackTrace();
            System.out.println("IO Error during state save.");
            return false;
        } catch(Exception e) {
            e.printStackTrace();
            System.out.println("Exception during state save.");
            return false;
        }

        return true;
    }

    private void saveComponent(DataOutput zip, HardwareComponent component, String name) throws IOException
    {
        System.out.print("Saving state of " + name + "...");
        System.out.flush();
        component.dumpState(zip);
        System.out.println("OK.");
    }

    private void loadComponent(DataInput zip, HardwareComponent component, String name) throws IOException
    {
        System.out.print("Loading state of " + name + "...");
        System.out.flush();
        if (component instanceof PIIX3IDEInterface)
            ((PIIX3IDEInterface) component).loadIOPorts(ioportHandler, zip);
        else
            component.loadState(zip);

        if (component instanceof IOPortCapable)
        {
            ioportHandler.registerIOPortCapable((IOPortCapable) component);
        }
        System.out.println("OK.");
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

    public void loadState(File f) throws IOException
    {
        try
        {
            ZipFile zip2 = new ZipFile(f);
            ZipEntry entry = zip2.getEntry("HardwareSavestate");
            DataInput zip = new DataInputStream(zip2.getInputStream(entry));

            magic.loadState(zip);
            loadComponent(zip, drives, "Drives");
            loadComponent(zip, vmClock, "System Clock");
            loadComponent(zip, physicalAddr, "Physical Address Space");
            loadComponent(zip, linearAddr, "Linear Address Space");
            loadComponent(zip, processor, "Processor");
            loadComponent(zip, ioportHandler, "IO Port Handler");
            loadComponent(zip, irqController, "IRQ Controller");
            loadComponent(zip, primaryDMA, "DMA Controller #1");
            loadComponent(zip, secondaryDMA, "DMA Controller #2");
            loadComponent(zip, rtc, "Real Time Clock");
            loadComponent(zip, pit, "Interval Timer");
            loadComponent(zip, gateA20, "A20 Gate");
            loadComponent(zip, pciHostBridge, "PCI Host Bridge");
            loadComponent(zip, pciISABridge, "PCI ISA Bridge");
            loadComponent(zip, pciBus, "PCI Bus");
            loadComponent(zip, ideInterface, "IDE Interface");
            loadComponent(zip, sysBIOS, "System BIOS");
            loadComponent(zip, vgaBIOS, "Video BIOS");
            loadComponent(zip, kbdDevice, "Keyboard");
            loadComponent(zip, fdc, "Floppy Disk Controller");
            loadComponent(zip, graphicsCard, "VGA Controller");
            loadComponent(zip, speaker, "PC Speaker");
            //TraceTrap intentionally omitted.

            linkComponents();
            //pciBus.biosInit();

            zip2.close();
        }
        catch (IOException e)
        {
            e.printStackTrace();
            System.out.println("IO Error during loading of Snapshot.");
            return;
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
        DriveSet disks = DriveSet.buildFromArgs(args);
        int cpuClockDivider = ArgProcessor.extractIntArg(args, "cpudivider", 25);
        int memorySize = ArgProcessor.extractIntArg(args, "memsize", 16384);
        return new PC(clock, disks, memorySize, cpuClockDivider);
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
                delta = addressSpace.execute(processor, processor.getInstructionPointer());
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
