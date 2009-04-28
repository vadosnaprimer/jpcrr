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
    public static final int SYS_RAM_SIZE = 256 * 1024 * 1024;

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

    private HardwareComponent[] myParts;

    public PC(Clock clock, DriveSet drives) throws IOException
    {
        this.drives = drives;
        processor = new Processor();
        vmClock = clock;

        //Motherboard
        physicalAddr = new PhysicalAddressSpace();
        for (int i=0; i<SYS_RAM_SIZE; i+= AddressSpace.BLOCK_SIZE)
            //physicalAddr.allocateMemory(i, new ByteArrayMemory(blockSize));
            //physicalAddr.allocateMemory(i, new CompressedByteArrayMemory(blockSize));
            physicalAddr.allocateMemory(i, new LazyMemory(AddressSpace.BLOCK_SIZE));

        linearAddr = new LinearAddressSpace();
               ioportHandler = new IOPortHandler();
        irqController = new InterruptController();
        primaryDMA = new DMAController(false, true);
        secondaryDMA = new DMAController(false, false);
        rtc = new RTC(0x70, 8);
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
                                          sysBIOS, vgaBIOS};
        
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

    public boolean saveState(ZipOutputStream zip) throws IOException
    {
        //save state of of Hardware Components
        //processor     DONE (-fpu)
        //rtc           DONE (-calendar)
        //pit           DONE (-TImerChannel.timer/irq)

        try
        {
            saveComponent(zip, drives);
            saveComponent(zip, vmClock);
            saveComponent(zip, physicalAddr);
            saveComponent(zip, linearAddr);
            saveComponent(zip, processor);
            saveComponent(zip, ioportHandler);
            saveComponent(zip, irqController);
            saveComponent(zip, primaryDMA);
            saveComponent(zip, secondaryDMA);
            saveComponent(zip, rtc);
            saveComponent(zip, pit);
            saveComponent(zip, gateA20);
            saveComponent(zip, pciHostBridge);
            saveComponent(zip, pciISABridge);
            saveComponent(zip, pciBus);
            saveComponent(zip, ideInterface);
            saveComponent(zip, sysBIOS);
            saveComponent(zip, vgaBIOS);
            saveComponent(zip, kbdDevice);
            saveComponent(zip, fdc);
            saveComponent(zip, graphicsCard);
            saveComponent(zip, speaker);
        }
        catch (IOException e)
        {
            e.printStackTrace();
            System.out.println("IO Error during state save.");
            return false;
        }

        return true;
    }

    private void saveComponent(ZipOutputStream zip, HardwareComponent component) throws IOException
    {
        ZipEntry entry = new ZipEntry(component.getClass().getName());
        try
        {
            zip.putNextEntry(entry);
        }
        catch (ZipException e)
        {
            entry = new ZipEntry(component.getClass().getName() + "2");
            zip.putNextEntry(entry);
        }
        component.dumpState(new DataOutputStream(zip));
        zip.closeEntry();
        System.out.println("component size " + entry.getSize() + " for " + component.getClass().getName());
    }

    private void loadComponent(ZipFile zip, HardwareComponent component) throws IOException
    {
        ZipEntry entry = zip.getEntry(component.getClass().getName());
        if (component == secondaryDMA)
            entry = zip.getEntry(component.getClass().getName() + "2");

        if (entry != null)
        {
            System.out.println("component size " + entry.getSize() + " for " + component.getClass().getName());
            DataInputStream in = new DataInputStream(zip.getInputStream(entry));
            if (component instanceof PIIX3IDEInterface)
                ((PIIX3IDEInterface) component).loadIOPorts(ioportHandler, in);
            else
                component.loadState(in);

            if (component instanceof IOPortCapable)
            {
                ioportHandler.registerIOPortCapable((IOPortCapable) component);
            }
        }
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
            ZipFile zip = new ZipFile(f);

            loadComponent(zip, drives);
            loadComponent(zip, vmClock);     
            loadComponent(zip, physicalAddr);
            loadComponent(zip, linearAddr);
            loadComponent(zip, processor);
            loadComponent(zip, irqController);    
            loadComponent(zip, ioportHandler);
            loadComponent(zip, primaryDMA);            
            loadComponent(zip, secondaryDMA);          
            loadComponent(zip, rtc);            
            loadComponent(zip, pit);            
            loadComponent(zip, gateA20);            
            loadComponent(zip, pciHostBridge);            
            loadComponent(zip, pciISABridge);            
            loadComponent(zip, pciBus);            
            loadComponent(zip, ideInterface);            
            loadComponent(zip, sysBIOS);            
            loadComponent(zip, vgaBIOS);            
            loadComponent(zip, kbdDevice);            
            loadComponent(zip, fdc);            
            loadComponent(zip, graphicsCard);
            loadComponent(zip, speaker);

            linkComponents();
            //pciBus.biosInit();

            zip.close();
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

    public static PC createPC(String[] args, Clock clock) throws IOException
    {
        DriveSet disks = DriveSet.buildFromArgs(args);
        return new PC(clock, disks);
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
