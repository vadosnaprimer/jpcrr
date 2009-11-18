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

package org.jpc.pluginsaux;

import org.jpc.pluginsaux.ConstantTableLayout;
import org.jpc.emulator.PC;
import org.jpc.emulator.processor.fpu64.FpuState;
import org.jpc.diskimages.DiskImage;
import org.jpc.emulator.DriveSet;
import static org.jpc.Misc.errorDialog;

import javax.swing.*;
import java.util.*;
import java.io.*;
import java.awt.event.*;
import java.awt.*;

public class PCConfigDialog implements ActionListener, WindowListener
{
    private JFrame window;
    private JPanel panel;
    private PC.PCHardwareInfo hw;
    private PC.PCHardwareInfo hwr;
    private boolean answerReady;
    private Map<String, JTextField> settings;
    private JComboBox bootDevice;

    public void addOption(String name, String id, String deflt)
    {
        JLabel label = new JLabel(name);
        JTextField text = new JTextField(deflt, 40);
        settings.put(id, text);
        panel.add(label);
        panel.add(text);
    }

    public PCConfigDialog()
    {
            hw = new PC.PCHardwareInfo();
            hwr = null;
            answerReady = false;
            window = new JFrame("PC Settings");
            settings = new HashMap<String, JTextField>();
            GridLayout layout = new GridLayout(0, 2);
            panel = new JPanel(layout);
            window.add(panel);
            window.addWindowListener(this);

            addOption("BIOS image", "BIOS", "BIOS");
            addOption("VGA BIOS image", "VGABIOS", "VGABIOS");
            addOption("Fda image", "FDA", "");
            addOption("Fdb image", "FDB", "");
            addOption("Hda image", "HDA", "");
            addOption("Hdb image", "HDB", "");
            addOption("Hdc image", "HDC", "");
            addOption("Hdd image", "HDD", "");
            addOption("CD-ROM image", "CDROM", "");
            addOption("Initial RTC time", "INITTIME", "1000000000000");
            addOption("CPU freq. divider", "CPUDIVIDER", "50");
            addOption("FPU emulator", "FPU", "");
            addOption("Memory size (4KiB pages)", "MEMSIZE", "4096");
            addOption("Modules", "MODULES", "");

            JLabel label1 = new JLabel("Boot device");
            bootDevice = new JComboBox(new String[]{"fda", "hda", "cdrom"});
            bootDevice.setEditable(false);
            panel.add(label1);
            panel.add(bootDevice);

            JButton ass = new JButton("Assemble");
            ass.setActionCommand("ASSEMBLE");
            ass.addActionListener(this);
            JButton cancl = new JButton("Cancel");
            cancl.setActionCommand("CANCEL");
            cancl.addActionListener(this);
            panel.add(ass);
            panel.add(cancl);

            window.pack();
            window.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
    }

    public void popUp()
    {
        window.setVisible(true);
    }

    public synchronized PC.PCHardwareInfo waitClose()
    {
        if(answerReady) {
            answerReady = false;
            return hwr;
        }
        while(!answerReady) {
            try {
                wait();
            } catch(InterruptedException e) {
            }
        }
        answerReady = false;
        return hwr;
    }

    private String textFor(String field)
    {
        String x = settings.get(field).getText();
        if(!("".equals(x))) {
            return x;
        } else
            return null;
    }

    private boolean checkOK()
    {
        try {
            String sysBIOSImg = textFor("BIOS");
            hw.biosID = DiskImage.getLibrary().canonicalNameFor(sysBIOSImg);
            if(hw.biosID == null)
                throw new IOException("Can't find image \"" + sysBIOSImg + "\".");

            String vgaBIOSImg = textFor("VGABIOS");
            hw.vgaBIOSID = DiskImage.getLibrary().canonicalNameFor(vgaBIOSImg);
            if(hw.vgaBIOSID == null)
                throw new IOException("Can't find image \"" + vgaBIOSImg + "\".");

            String hdaImg = textFor("HDA");
            hw.hdaID = DiskImage.getLibrary().canonicalNameFor(hdaImg);
            if(hw.hdaID == null && hdaImg != null)
                throw new IOException("Can't find image \"" + hdaImg + "\".");

            String hdbImg = textFor("HDB");
            hw.hdbID = DiskImage.getLibrary().canonicalNameFor(hdbImg);
            if(hw.hdbID == null && hdbImg != null)
                throw new IOException("Can't find image \"" + hdbImg + "\".");

            String hdcImg = textFor("HDC");
            hw.hdcID = DiskImage.getLibrary().canonicalNameFor(hdcImg);
            if(hw.hdcID == null && hdcImg != null)
                throw new IOException("Can't find image \"" + hdcImg + "\".");

            String hddImg = textFor("HDD");
            hw.hddID = DiskImage.getLibrary().canonicalNameFor(hddImg);
            if(hw.hddID == null && hddImg != null)
                throw new IOException("Can't find image \"" + hddImg + "\".");

            String cdRomFileName = textFor("CDROM");
            if (cdRomFileName != null) {
                 if(hdcImg != null)
                     throw new IOException("-hdc and -cdrom are mutually exclusive.");
                hw.initCDROMIndex = hw.images.addDisk(new DiskImage(cdRomFileName, false));
                hw.images.lookupDisk(hw.initCDROMIndex).setName(cdRomFileName + " (initial cdrom disk)");
            } else
                hw.initCDROMIndex = -1;

            String fdaFileName = textFor("FDA");
            if(fdaFileName != null) {
                byte[] fdaID = DiskImage.getLibrary().canonicalNameFor(fdaFileName);
                if(fdaID == null && fdaFileName != null)
                    throw new IOException("Can't find image \"" + fdaFileName + "\".");
                hw.initFDAIndex = hw.images.addDisk(new DiskImage(fdaFileName, false));
                hw.images.lookupDisk(hw.initFDAIndex).setName(fdaFileName + " (initial fda disk)");
            } else
                hw.initFDAIndex = -1;

            String fdbFileName = textFor("FDB");
            if(fdbFileName != null) {
                byte[] fdbID = DiskImage.getLibrary().canonicalNameFor(fdbFileName);
                if(fdbID == null && fdbFileName != null)
                    throw new IOException("Can't find image \"" + fdbFileName + "\".");
                hw.initFDBIndex = hw.images.addDisk(new DiskImage(fdbFileName, false));
                hw.images.lookupDisk(hw.initFDBIndex).setName(fdbFileName + " (initial fdb disk)");
            } else
                hw.initFDBIndex = -1;

            String initTimeS = textFor("INITTIME");
            try {
                hw.initRTCTime = Long.parseLong(initTimeS, 10);
                if(hw.initRTCTime < 0 || hw.initRTCTime > 4102444799999L)
                   throw new Exception("Invalid time value (bounds are 0 and 4102444799999).");
            } catch(Exception e) {
                if(initTimeS != null)
                    throw e;
                hw.initRTCTime = 1000000000000L;
            }

            String cpuDividerS = textFor("CPUDIVIDER");
            try {
                hw.cpuDivider = Integer.parseInt(cpuDividerS, 10);
                if(hw.cpuDivider < 1 || hw.cpuDivider > 256)
                    throw new Exception("Invalid CPU divider value (bounds are 1 and 256).");
            } catch(Exception e) {
                if(cpuDividerS != null)
                    throw e;
                hw.cpuDivider = 50;
            }

            hw.fpuEmulator = textFor("FPU");
            try {
                if(hw.fpuEmulator != null) {
                    Class<?> fpuClass;
                    try {
                        fpuClass = Class.forName(hw.fpuEmulator);
                    } catch(Exception e) {
                        throw new Exception("Class does not exist");
                    }
                    if(!FpuState.class.isAssignableFrom(fpuClass))
                        throw new Exception("FPU emulators must inherit from FpuState");
                }
            } catch(Exception e) {
                throw new Exception("Bad FPU emulator: " + hw.fpuEmulator + ": " + e.getMessage());
            }

            String memoryPagesS = textFor("MEMSIZE");
            try {
                hw.memoryPages = Integer.parseInt(memoryPagesS, 10);
                if(hw.memoryPages < 256 || hw.memoryPages > 262144)
                   throw new Exception("Invalid memory size value (bounds are 256 and 262144).");
            } catch(Exception e) {
                if(memoryPagesS != null)
                    throw e;
                hw.memoryPages = 4096;
            }

            String bootArg = (String)bootDevice.getSelectedItem();
            bootArg = bootArg.toLowerCase();
            if (bootArg.equals("fda"))
                hw.bootType = DriveSet.BootType.FLOPPY;
            else if (bootArg.equals("hda"))
                hw.bootType = DriveSet.BootType.HARD_DRIVE;
            else if (bootArg.equals("cdrom"))
                hw.bootType = DriveSet.BootType.CDROM;

            String hwModulesS = textFor("MODULES");
            if(hwModulesS != null) {
                hw.hwModules = PC.parseHWModules(hwModulesS);
            }


        } catch(Exception e) {
            errorDialog(e, "Problem with settings.", window, "Dismiss");
            return false;
        }
        return true;
    }

    public void actionPerformed(ActionEvent evt)
    {
        String command = evt.getActionCommand();
        System.err.println("Command: " + command);
        if(command == "ASSEMBLE") {
            if(!checkOK()) {
                hw = new PC.PCHardwareInfo();
                return;
            }
            window.setVisible(false);
            synchronized(this) {
                hwr = hw;
                answerReady = true;
                notifyAll();
                hw = new PC.PCHardwareInfo();
            }
            hw = new PC.PCHardwareInfo();
        } else if(command == "CANCEL") {
            window.setVisible(false);
            synchronized(this) {
                hwr = null;
                answerReady = true;
                notifyAll();
            }
        }
    }

    public void windowActivated(WindowEvent e) { /* Not interested. */ }
    public void windowClosed(WindowEvent e) { /* Not interested. */ }
    public void windowDeactivated(WindowEvent e) { /* Not interested. */ }
    public void windowDeiconified(WindowEvent e) { /* Not interested. */ }
    public void windowIconified(WindowEvent e) { /* Not interested. */ }
    public void windowOpened(WindowEvent e) { /* Not interested. */ }

    public void windowClosing(WindowEvent e)
    {
        window.setVisible(false);
        synchronized(this) {
            hwr = null;
            answerReady = true;
            notifyAll();
        }
    }

}
