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

package org.jpc.pluginsaux;

import org.jpc.emulator.PC;
import org.jpc.images.ImageID;
import org.jpc.images.COWImage;
import org.jpc.images.BaseImageFactory;
import org.jpc.emulator.DriveSet;
import org.jpc.emulator.PCHardwareInfo;
import static org.jpc.Misc.errorDialog;
import static org.jpc.emulator.peripheral.SoundCard.CONFIGWORD_PCM;
import static org.jpc.emulator.peripheral.SoundCard.CONFIGWORD_FM;
import static org.jpc.emulator.peripheral.SoundCard.CONFIGWORD_UART;
import static org.jpc.emulator.peripheral.SoundCard.CONFIGWORD_GAMEPORT;

import javax.swing.*;
import java.util.*;
import java.io.*;
import java.awt.event.*;
import java.awt.*;

public class PCConfigDialog implements ActionListener, WindowListener
{
    private JFrame window;
    private JPanel panel;
    private PCHardwareInfo hw;
    private PCHardwareInfo hwr;
    private boolean answerReady;
    private Map<String, JTextField> settings;
    private Map<String, JComboBox> settings2;
    private Map<String, JCheckBox> settings3;
    private Map<String, JCheckBox> settings4;
    private Map<String, Long> settings2Types;
    private Map<String, String[]> settings2Values;
    private JComboBox bootDevice;

    public JTextField getOption(String id, String deflt, int width)
    {
        JTextField text = new JTextField(deflt, width);
        settings.put(id, text);
        return text;
    }

    public JCheckBox getBoolean(String name, String id, boolean deflt)
    {
        JCheckBox box = new JCheckBox(name);
        settings3.put(id, box);
        box.setSelected(deflt);
        return box;
    }

    public JCheckBox getBoolean2(String name, String id, boolean deflt)
    {
        JCheckBox box = new JCheckBox(name);
        settings4.put(id, box);
        box.setSelected(deflt);
        return box;
    }

    public JComboBox getDiskCombo(String id, long type) throws Exception
    {
        String[] choices = BaseImageFactory.getNamesByType(type);

        if(choices != null) {
            Arrays.sort(choices);
            settings2.put(id, new JComboBox(choices));
        } else
            settings2.put(id, new JComboBox());

        settings2Types.put(id, new Long(type));
        settings2Values.put(id, choices);

        if(choices == null)
            return settings2.get(id);

        //Hack to default the BIOS images.
        if((type & 16) != 0 && Arrays.binarySearch(choices, id, null) >= 0)
            settings2.get(id).setSelectedItem(id);

        return settings2.get(id);
    }

    public JComboBox getCombo(String id, String[] choices, String deflt) throws Exception
    {
        settings2.put(id, new JComboBox(choices));
        settings2.get(id).setSelectedItem(deflt);
        return settings2.get(id);
    }

    public void updateDiskCombo(String id) throws Exception
    {
        String[] choices = BaseImageFactory.getNamesByType(settings2Types.get(id).longValue());
        if(choices == null)
            throw new Exception("No valid " + id + " image");
        Arrays.sort(choices);
        String[] oldChoices = settings2Values.get(id);
        int oldChoicesLen = 0;
        if(oldChoices != null)
            oldChoicesLen = oldChoices.length;
        JComboBox combo = settings2.get(id);
        int i = 0, j = 0;

        while(i < oldChoicesLen || j < choices.length) {
            int x = 0;
            if(i == oldChoicesLen)
                x = 1;
            else if(j == choices.length)
                x = -1;
            else
                x = oldChoices[i].compareTo(choices[j]);

            if(x < 0) {
                combo.removeItem(oldChoices[i]);
                i++;
            } else if(x > 0) {
                combo.addItem(choices[j]);
                j++;
            } else {
                i++;
                j++;
            }
        }
        settings2Values.put(id, choices);
    }

    private GridBagConstraints getLocation(int x, int y, boolean exp)
    {
        return new GridBagConstraints(x, y, 1, 1, exp ? 1 : 0, 0, GridBagConstraints.CENTER,
            exp ? GridBagConstraints.HORIZONTAL : GridBagConstraints.NONE, new Insets(4, 6, 4, 6), 0, 0);
    }

    public PCConfigDialog() throws Exception
    {
            hw = new PCHardwareInfo();
            hwr = null;
            answerReady = false;

            settings = new HashMap<String, JTextField>();
            settings2 = new HashMap<String, JComboBox>();
            settings2Types = new HashMap<String, Long>();
            settings2Values = new HashMap<String, String[]>();
            settings3 = new HashMap<String, JCheckBox>();
            settings4 = new HashMap<String, JCheckBox>();

            window = new JFrame("PC Settings");
            JPanel mainPanel = new JPanel();
            window.add(mainPanel);
            mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
            window.addWindowListener(this);

            //BIOS Panel.
            JPanel biosPanel = new JPanel(new GridBagLayout());
            biosPanel.setBorder(BorderFactory.createTitledBorder("BIOS"));
            mainPanel.add(biosPanel);
            biosPanel.add(new JLabel("BIOS"), getLocation(0, 0, false));
            biosPanel.add(getDiskCombo("BIOS", 16), getLocation(1, 0, true));
            biosPanel.add(new JLabel("VGABIOS"), getLocation(2, 0, false));
            biosPanel.add(getDiskCombo("VGABIOS", 16), getLocation(3, 0, true));

            //Mass storage panel.
            JPanel msPanel = new JPanel(new GridBagLayout());
            msPanel.setBorder(BorderFactory.createTitledBorder("Mass Storage"));
            mainPanel.add(msPanel);
            msPanel.add(new JLabel("fda"), getLocation(0, 0, false));
            msPanel.add(getDiskCombo("FDA", 3), getLocation(1, 0, true));
            msPanel.add(new JLabel("fdb"), getLocation(2, 0, false));
            msPanel.add(getDiskCombo("FDB", 3), getLocation(3, 0, true));
            msPanel.add(new JLabel("hda"), getLocation(0, 1, false));
            msPanel.add(getDiskCombo("HDA", 5), getLocation(1, 1, true));
            msPanel.add(new JLabel("hdb"), getLocation(2, 1, false));
            msPanel.add(getDiskCombo("HDB", 5), getLocation(3, 1, true));
            msPanel.add(new JLabel("hdc"), getLocation(0, 2, false));
            msPanel.add(getDiskCombo("HDC", 5), getLocation(1, 2, true));
            msPanel.add(new JLabel("hdd"), getLocation(2, 2, false));
            msPanel.add(getDiskCombo("HDD", 5), getLocation(3, 2, true));
            msPanel.add(new JLabel("CD-ROM"), getLocation(0, 3, false));
            msPanel.add(getDiskCombo("CDROM", 9), getLocation(1, 3, true));
            bootDevice = new JComboBox(new String[]{"fda", "hda", "cdrom"});
            bootDevice.setEditable(false);
            msPanel.add(new JLabel("Boot"), getLocation(2, 3, false));
            msPanel.add(bootDevice, getLocation(3, 3, true));

            //Main System parameters.
            JPanel mspPanel = new JPanel();
            mspPanel.setLayout(new BoxLayout(mspPanel, BoxLayout.Y_AXIS));
            mspPanel.setBorder(BorderFactory.createTitledBorder("Main system parameters"));
            JPanel mspPanel1 = new JPanel(new FlowLayout(FlowLayout.LEADING, 12, 4));
            JPanel mspPanel2 = new JPanel(new FlowLayout(FlowLayout.LEADING, 12, 4));
            mainPanel.add(mspPanel);
            mspPanel.add(mspPanel1);
            mspPanel.add(mspPanel2);
            mspPanel1.add(new JLabel("Initial Time"));
            mspPanel1.add(getOption("INITTIME", "1000000000000", 12));
            mspPanel1.add(new JLabel("CPU divider"));
            mspPanel1.add(getOption("CPUDIVIDER", "100", 3));
            mspPanel1.add(new JLabel("Memory size"));
            mspPanel1.add(getOption("MEMSIZE", "1536", 6));
            mspPanel2.add(getBoolean("Emulate I/O delay", "IOPORTDELAY", false));
            mspPanel2.add(getBoolean("Emulate VGA Hretrace", "VGAHRETRACE", true));
            mspPanel2.add(getBoolean("Flush pipeline on self-modify", "FLUSHONMODIFY", true));

            //Sound settings.
            JPanel ssPanel = new JPanel();
            ssPanel.setLayout(new BoxLayout(ssPanel, BoxLayout.Y_AXIS));
            ssPanel.setBorder(BorderFactory.createTitledBorder("Sound card parameters"));
            JPanel ssPanel1 = new JPanel(new FlowLayout(FlowLayout.LEADING, 12, 4));
            JPanel ssPanel2 = new JPanel(new FlowLayout(FlowLayout.LEADING, 12, 4));
            mainPanel.add(ssPanel);
            ssPanel.add(ssPanel1);
            ssPanel.add(ssPanel2);
            ssPanel1.add(getBoolean2("PCM emulation", "PCMEMU", true));
            ssPanel1.add(new JLabel("I/O base"));
            ssPanel1.add(getCombo("SCIO", new String[]{"220", "240", "260", "280"}, "220"));
            ssPanel1.add(new JLabel("IRQ"));
            ssPanel1.add(getCombo("SCIRQ", new String[]{"2", "5", "7", "10"}, "5"));
            ssPanel1.add(new JLabel("8-bit DMA"));
            ssPanel1.add(getCombo("SCLDMA", new String[]{"0", "1", "2", "3"}, "1"));
            ssPanel1.add(new JLabel("16-bit DMA"));
            ssPanel1.add(getCombo("SCHDMA", new String[]{"4", "5", "6", "7"}, "5"));
            ssPanel2.add(getBoolean2("FM emulation", "FMEMU", true));
            ssPanel2.add(getBoolean2("Game port emulation", "GAMEPORTEMU", true));
            ssPanel2.add(getBoolean2("UART emulation", "UARTEMU", true));
            ssPanel2.add(new JLabel("I/O base"));
            ssPanel2.add(getOption("UARTIO", "330", 4));
            ssPanel2.add(new JLabel("IRQ"));
            ssPanel2.add(getCombo("UARTIRQ", new String[]{"0", "1", "2", "3", "4", "5", "6", "7", "8", "9",
                "10", "11", "12", "13", "14", "15"}, "9"));

            //Modules
            JPanel mPanel = new JPanel(new GridBagLayout());
            mPanel.setBorder(BorderFactory.createTitledBorder("Modules"));
            mainPanel.add(mPanel);
            mPanel.add(getOption("MODULES", "org.jpc.modules.BasicFPU", 40), getLocation(0, 0, true));
            //Buttons
            JButton ass = new JButton("Assemble");
            ass.setActionCommand("ASSEMBLE");
            ass.addActionListener(this);
            JButton cancl = new JButton("Cancel");
            cancl.setActionCommand("CANCEL");
            cancl.addActionListener(this);
            JPanel bPanel = new JPanel(new GridBagLayout());
            mainPanel.add(bPanel);
            bPanel.add(ass, getLocation(0, 0, true));
            bPanel.add(cancl, getLocation(1, 0, true));

            window.pack();
            window.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
    }

    public void popUp() throws Exception
    {
        updateDiskCombo("BIOS");
        updateDiskCombo("VGABIOS");
        updateDiskCombo("FDA");
        updateDiskCombo("FDB");
        updateDiskCombo("HDA");
        updateDiskCombo("HDB");
        updateDiskCombo("HDC");
        updateDiskCombo("HDD");
        updateDiskCombo("CDROM");
        window.setVisible(true);
    }

    public synchronized PCHardwareInfo waitClose()
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
        String x = null;
        if(settings.containsKey(field))
            x = settings.get(field).getText();
        if(x == null)
            x = (String)(settings2.get(field).getSelectedItem());

        if(!("".equals(x))) {
            return x;
        } else
            return null;
    }

    private boolean booleanValue(String field)
    {
        boolean x = false;
        if(settings3.containsKey(field))
            x = settings3.get(field).isSelected();
        if(settings4.containsKey(field))
            x = settings4.get(field).isSelected();
        return x;
    }

    private boolean checkOK()
    {
        try {
            String sysBIOSImg = textFor("BIOS");
            hw.biosID = BaseImageFactory.getIDByName(sysBIOSImg);
            if(hw.biosID == null)
                throw new IOException("Can't find image \"" + sysBIOSImg + "\".");

            String vgaBIOSImg = textFor("VGABIOS");
            hw.vgaBIOSID = BaseImageFactory.getIDByName(vgaBIOSImg);
            if(hw.vgaBIOSID == null)
                throw new IOException("Can't find image \"" + vgaBIOSImg + "\".");

            String hdaImg = textFor("HDA");
            hw.hdaID = BaseImageFactory.getIDByName(hdaImg);
            if(hw.hdaID == null && hdaImg != null)
                throw new IOException("Can't find image \"" + hdaImg + "\".");

            String hdbImg = textFor("HDB");
            hw.hdbID = BaseImageFactory.getIDByName(hdbImg);
            if(hw.hdbID == null && hdbImg != null)
                throw new IOException("Can't find image \"" + hdbImg + "\".");

            String hdcImg = textFor("HDC");
            hw.hdcID = BaseImageFactory.getIDByName(hdcImg);
            if(hw.hdcID == null && hdcImg != null)
                throw new IOException("Can't find image \"" + hdcImg + "\".");

            String hddImg = textFor("HDD");
            hw.hddID = BaseImageFactory.getIDByName(hddImg);
            if(hw.hddID == null && hddImg != null)
                throw new IOException("Can't find image \"" + hddImg + "\".");

            String cdRomFileName = textFor("CDROM");
            if (cdRomFileName != null) {
                if(hdcImg != null)
                    throw new IOException("-hdc and -cdrom are mutually exclusive.");
                ImageID cdromID = BaseImageFactory.getIDByName(cdRomFileName);
                if(cdromID == null)
                    throw new IOException("Can't find image \"" + cdRomFileName + "\".");
                hw.initCDROMIndex = hw.images.addDisk(new COWImage(cdromID));
                hw.images.lookupDisk(hw.initCDROMIndex).setName(cdRomFileName + " (initial cdrom disk)");
            } else
                hw.initCDROMIndex = -1;

            String fdaFileName = textFor("FDA");
            if(fdaFileName != null) {
                ImageID fdaID = BaseImageFactory.getIDByName(fdaFileName);
                if(fdaID == null)
                    throw new IOException("Can't find image \"" + fdaFileName + "\".");
                hw.initFDAIndex = hw.images.addDisk(new COWImage(fdaID));
                hw.images.lookupDisk(hw.initFDAIndex).setName(fdaFileName + " (initial fda disk)");
            } else
                hw.initFDAIndex = -1;

            String fdbFileName = textFor("FDB");
            if(fdbFileName != null) {
                ImageID fdbID = BaseImageFactory.getIDByName(fdbFileName);
                if(fdbID == null)
                    throw new IOException("Can't find image \"" + fdbFileName + "\".");
                hw.initFDBIndex = hw.images.addDisk(new COWImage(fdbID));
                hw.images.lookupDisk(hw.initFDBIndex).setName(fdbFileName + " (initial fdb disk)");
            } else
                hw.initFDBIndex = -1;

            String initTimeS = textFor("INITTIME");
            hw.initRTCTime = Long.parseLong(initTimeS, 10);
            if(hw.initRTCTime < 0 || hw.initRTCTime > 4102444799999L)
                throw new Exception("Invalid time value (bounds are 0 and 4102444799999).");

            String cpuDividerS = textFor("CPUDIVIDER");
            hw.cpuDivider = Integer.parseInt(cpuDividerS, 10);
            if(hw.cpuDivider < 1 || hw.cpuDivider > 256)
                throw new Exception("Invalid CPU divider value (bounds are 1 and 256).");

            String memoryPagesS = textFor("MEMSIZE");
            hw.memoryPages = Integer.parseInt(memoryPagesS, 10);
            if(hw.memoryPages < 256 || hw.memoryPages > 262144)
               throw new Exception("Invalid memory size value (bounds are 256 and 262144).");

            boolean pcmemu = booleanValue("PCMEMU");
            boolean fmemu = booleanValue("FMEMU");
            boolean gameportemu = booleanValue("GAMEPORTEMU");
            boolean uartemu = booleanValue("UARTEMU");
            hw.scConfigWord = (pcmemu ? CONFIGWORD_PCM : 0) | (fmemu ? CONFIGWORD_FM : 0) |
                (gameportemu ? CONFIGWORD_GAMEPORT : 0) | (uartemu ? CONFIGWORD_UART : 0);
            String pcmIO = textFor("SCIO");
            String pcmIRQ = textFor("SCIRQ");
            String pcmLDMA = textFor("SCLDMA");
            String pcmHDMA = textFor("SCHDMA");
            String uartIO = textFor("UARTIO");
            String uartIRQ = textFor("UARTIRQ");
            hw.scPCMIO = Integer.parseInt(pcmIO, 16);
            hw.scPCMIRQ = Integer.parseInt(pcmIRQ, 10);
            hw.scPCMLDMA = Integer.parseInt(pcmLDMA, 10);
            hw.scPCMHDMA = Integer.parseInt(pcmHDMA, 10);
            hw.scUARTIO = Integer.parseInt(uartIO, 16);
            hw.scUARTIRQ = Integer.parseInt(uartIRQ, 10);
            if(hw.scPCMIO < 0 || hw.scPCMIO > 65520)
               throw new Exception("Invalid sc I/O base (bounds are 0 and FFF0).");
            if(hw.scUARTIO < 0 || hw.scUARTIO > 65534)
               throw new Exception("Invalid UART I/O base (bounds are 0 and FFFE).");

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

            hw.booleanOptions = new TreeMap<String, Boolean>();
            hw.intOptions = new TreeMap<String, Integer>();
            for(Map.Entry<String, JCheckBox> box : settings3.entrySet())
                hw.booleanOptions.put(box.getKey(), booleanValue(box.getKey()));
        } catch(Exception e) {
            errorDialog(e, "Problem with settings.", window, "Dismiss");
            return false;
        }
        return true;
    }

    public void actionPerformed(ActionEvent evt)
    {
        String command = evt.getActionCommand();
        if(command == "ASSEMBLE") {
            if(!checkOK()) {
                hw = new PCHardwareInfo();
                return;
            }
            window.setVisible(false);
            synchronized(this) {
                hwr = hw;
                answerReady = true;
                notifyAll();
                hw = new PCHardwareInfo();
            }
            hw = new PCHardwareInfo();
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
