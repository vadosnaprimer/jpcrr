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


package org.jpc.debugger;

import java.util.*;
import java.util.zip.*;
import java.io.*;
import java.text.*;
import java.awt.*;
import java.awt.event.*;

import javax.swing.*;
import javax.swing.event.*;
import javax.swing.text.*;
import javax.swing.undo.*;

import org.jpc.debugger.util.*;
import org.jpc.emulator.*;
import org.jpc.support.*;
import org.jpc.emulator.motherboard.*;
import org.jpc.emulator.memory.*;
import org.jpc.emulator.processor.*;
import org.jpc.emulator.peripheral.*;
import org.jpc.emulator.pci.peripheral.*;
import org.jpc.j2se.*;

public class JPC extends ApplicationFrame implements ActionListener
{
    private static JPC instance = null;

    private ObjectDatabase objects;
    private RunMenu runMenu;
    private CodeBlockRecord codeBlocks;

    private JDesktopPane desktop;
    private DiskSelector floppyDisk, hardDisk;
    private JMenuItem createPC, scanForImages, loadSnapshot, saveSnapshot, quit, createBlankDisk;
    private JMenuItem processorFrame, physicalMemoryViewer, linearMemoryViewer, breakpoints, opcodeFrame, traceFrame, monitor, frequencies, codeBlockTreeFrame;
    private MemoryViewer physicalViewer, linearViewer;

    private JPC(boolean fullScreen)
    {
        super("JPC Debugger");
        
        if (fullScreen)
            setBoundsToMaximum();
        else
            setBounds(0, 0, 1024, 900);

        objects = new ObjectDatabase();
        desktop = new JDesktopPane();
        add("Center", desktop);
        
        physicalViewer = null;
        linearViewer = null;

        JFileChooser chooser = new JFileChooser(System.getProperty("user.dir"));
        chooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
        objects.addObject(chooser);

        JMenuBar bar = new JMenuBar();
        JMenu actions = new JMenu("Actions");
        createPC = actions.add("Create New PC");
        createPC.setEnabled(false);
        createPC.addActionListener(this);
        scanForImages = actions.add("Scan Directory for Images");
        scanForImages.addActionListener(this);
        actions.addSeparator();
        loadSnapshot = actions.add("Load Snapshot");
        loadSnapshot.addActionListener(this);
        saveSnapshot = actions.add("Save Snapshot");
        saveSnapshot.addActionListener(this);
        actions.addSeparator();
        quit = actions.add("Quit");
        quit.addActionListener(this);



        JMenu windows = new JMenu("Windows");
        monitor = windows.add("PC Monitor");
        monitor.addActionListener(this);
        processorFrame = windows.add("Processor Frame");
        processorFrame.addActionListener(this);
        physicalMemoryViewer = windows.add("Physical Memory Viewer");
        physicalMemoryViewer.addActionListener(this);
        linearMemoryViewer = windows.add("Linear Memory Viewer");
        linearMemoryViewer.addActionListener(this);
        breakpoints = windows.add("Breakpoints");
        breakpoints.addActionListener(this);
        opcodeFrame = windows.add("Opcode Frame");
        opcodeFrame.addActionListener(this);
        traceFrame = windows.add("Execution Trace Frame");
        traceFrame.addActionListener(this);
//         frequencies = windows.add("Opcode Frequency Frame");
//         frequencies.addActionListener(this);
        codeBlockTreeFrame = windows.add("Code Block Tree Frame");
        codeBlockTreeFrame.addActionListener(this);

        JMenu tools = new JMenu("Tools");
        createBlankDisk = tools.add("Create Blank Disk (file)");
        createBlankDisk.addActionListener(this);

        runMenu = new RunMenu();

        floppyDisk = new DiskSelector("FDD", Color.red);
        hardDisk = new DiskSelector("HDD", Color.blue);

        bar.add(actions);
        bar.add(windows);
        bar.add(runMenu);
        bar.add(tools);
        bar.add(floppyDisk);
        bar.add(hardDisk);
        
        bar.add(Box.createHorizontalGlue());
        bar.add(new Hz());

        codeBlocks = null;
        setJMenuBar(bar);

        resyncImageSelection(new File(System.getProperty("user.dir")));
    }

    private void resyncImageSelection(File dir)
    {
        floppyDisk.rescan(dir);
        hardDisk.rescan(dir);
        
        checkBootEnabled();
    }

    private void checkBootEnabled()
    {
        createPC.setEnabled(floppyDisk.isBootDevice() || hardDisk.isBootDevice());
    }

    class DiskSelector extends JMenu implements ActionListener
    {
        String mainTitle;
        ButtonGroup group;
        Vector<File> diskImages;
        Hashtable<ButtonModel, File> lookup;
        JCheckBoxMenuItem bootFrom;
        JMenuItem openFile;

        public DiskSelector(String mainTitle, Color fg)
        {
            super(mainTitle);
            this.mainTitle = mainTitle;
            setForeground(fg);
            
            lookup = new Hashtable<ButtonModel, File>();
            diskImages = new Vector<File>();
            group = new ButtonGroup();
            bootFrom = new JCheckBoxMenuItem("Set as Boot Device");
            bootFrom.addActionListener(this);
            openFile = new JMenuItem("Select Image File");
            openFile.addActionListener(this);
        }

        public void actionPerformed(ActionEvent evt)
        {
            if (evt.getSource() == openFile)
            {
                JFileChooser chooser = (JFileChooser) objects.getObject(JFileChooser.class);
                if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION)
                    return;
                
                rescan(chooser.getSelectedFile());
            }

            resetTitle();
        }

        public void setSelectedFile(File f, boolean isBootDevice)
        {
            rescan(f);
            bootFrom.setState(isBootDevice);
            resetTitle();
        }

        private void resetTitle()
        {
            String fileName = "";
            File f = getSelectedFile();
            if (f != null)
                fileName = f.getAbsolutePath();

            if (isBootDevice())
                setText(mainTitle+" >"+fileName+"<");
            else
                setText(mainTitle+" "+fileName);

            checkBootEnabled();
            if (bootFrom.getState() && (getSelectedFile() == null))
                bootFrom.setState(false);
        }

        public File getSelectedFile()
        {
            ButtonModel selectedModel = group.getSelection();
            if (selectedModel == null)
                return null;

            return lookup.get(selectedModel);
        }

        public boolean isBootDevice()
        {
            return bootFrom.getState() && (getSelectedFile() != null);
        }

        void rescan(File f)
        {
            File selected = getSelectedFile();
            boolean isBoot = isBootDevice();
            
            for (int i=diskImages.size()-1; i>=0; i--)
                if (!diskImages.elementAt(i).exists())
                    diskImages.removeElementAt(i);

            if (f.isDirectory())
            {
                File[] files = f.listFiles();
                for (int i=0; i<files.length; i++)
                    if (files[i].getName().toLowerCase().endsWith(".img"))
                    {
                        if (!diskImages.contains(files[i]))
                            diskImages.add(files[i]);
                    }
            }
            else if (f.exists())
            {
                boolean found = false;
                for (int i=0; i<diskImages.size(); i++)
                    if (diskImages.elementAt(i).getAbsolutePath().equals(f.getAbsolutePath()))
                    {
                        selected = diskImages.elementAt(i);
                        found = true;
                    }

                if (!found)
                {
                    diskImages.add(f);
                    selected = f;
                }
            }

            removeAll();
            lookup.clear();

            group = new ButtonGroup();
            bootFrom.setState(isBoot);
            add(bootFrom);
            addSeparator();
            
            for (int i=0; i<diskImages.size(); i++)
            {
                File ff = diskImages.elementAt(i);
                JRadioButtonMenuItem item = new JRadioButtonMenuItem(ff.getAbsolutePath());
                item.addActionListener(this);
                lookup.put(item.getModel(), ff);

                group.add(item);
                add(item);

                if (ff.equals(selected))
                    group.setSelected(item.getModel(), true);
            }

            addSeparator();
            add(openFile);
        }
    }

    // Hook for F2 - print status report
    public void statusReport()
    {
        System.out.println("No status to report");
    }

    public Object get(Class cls)
    {
        return objects.getObject(cls);
    }

    public ObjectDatabase objects()
    {
        return objects;
    }

    public JDesktopPane getDesktop()
    {
        return desktop;
    }

    protected void frameCloseRequested()
    {
        BreakpointsFrame bp = (BreakpointsFrame) objects.getObject(BreakpointsFrame.class);
        if ((bp != null) && bp.isEdited())
            bp.dispose();
        System.exit(0);
    }

    public void bringToFront(JInternalFrame f)
    {
        desktop.moveToFront(f);
        desktop.setSelectedFrame(f);
    }

    public void actionPerformed(ActionEvent evt)
    {
        Object src = evt.getSource();

        if (src == quit)
            frameCloseRequested();
        else if (src == scanForImages)
        {
            JFileChooser chooser = (JFileChooser) objects.getObject(JFileChooser.class);
            if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION)
                return;

            File dir = chooser.getSelectedFile();
            if (!dir.isDirectory())
                dir = dir.getParentFile();
            resyncImageSelection(dir);
        }
        else if (src == createPC)
        {
            try
            {
                File floppyImage = floppyDisk.getSelectedFile();
                File hardImage = hardDisk.getSelectedFile();
                
                int bootType = DriveSet.HARD_DRIVE_BOOT;
                if (floppyDisk.isBootDevice())
                {
                    if (!floppyImage.exists())
                    {
                        alert("Floppy Image: "+floppyImage+" does not exist", "Boot", JOptionPane.ERROR_MESSAGE);
                        return;
                    }
                    bootType = DriveSet.FLOPPY_BOOT;
                }
                else
                {
                    if (!hardImage.exists())
                    {
                        alert("Hard disk Image: "+hardImage+" does not exist", "Boot", JOptionPane.ERROR_MESSAGE);
                        return;
                    }
                }

                String[] args;
                int argc = 0;
                if (floppyImage != null)
                    argc += 2;
                if (hardImage != null)
                    argc += 2;
                if (argc > 2)
                    argc += 2;
                args = new String[argc];

                int pos = 0;
                if (floppyImage != null)
                {
                    args[pos++] = "-fda";
                    args[pos++] = floppyImage.getAbsolutePath();
                }
                if (hardImage != null)
                {
                    args[pos++] = "-hda";
                    args[pos++] = hardImage.getAbsolutePath();
                }
                if (pos <= (argc - 2))
                {
                    args[pos++] = "-boot";
                    if (bootType == DriveSet.HARD_DRIVE_BOOT)
                        args[pos++] = "hda";
                    else
                        args[pos++] = "fda";
                }

                instance.createPC(args);                    
                resyncImageSelection(new File(System.getProperty("user.dir")));
            }
            catch (Exception e)
            {
                alert("Failed to create PC: "+e, "Boot", JOptionPane.ERROR_MESSAGE);
            }
        }
        else if (src == loadSnapshot)
        {
            runMenu.stop();
            JFileChooser fc = new JFileChooser();
            try
            {
                BufferedReader in = new BufferedReader(new FileReader("prefs.txt"));
                String path = in.readLine();
                in.close();
                if (path != null)
                {
                    File f = new File(path);
                    if (f.isDirectory())
                        fc.setCurrentDirectory(f);
                }
            }
            catch (Exception e) {}
            
            int returnVal = fc.showDialog(this, "Load JPC Snapshot");
            File file = fc.getSelectedFile();
            try
            {
                if (file != null)
                {
                    BufferedWriter out = new BufferedWriter(new FileWriter("prefs.txt"));
                    out.write(file.getPath());
                    out.close();
                }
            }
            catch (Exception e) {e.printStackTrace();}
            
            if (returnVal == 0)
                try
                {
                    System.out.println("Loading a snapshot of JPC");
                    ((PC) objects.getObject(PC.class)).loadState(file);
                    
                    System.out.println("Loading data");
                    ((PCMonitorFrame) objects.getObject(PCMonitorFrame.class)).resizeDisplay();
                    ((PCMonitorFrame) objects.getObject(PCMonitorFrame.class)).loadMonitorState(file);
                    
                    System.out.println("done");
                }
                catch (IOException e)
                {
                    e.printStackTrace();
                }
        }
        else if (src == saveSnapshot)
        {
            runMenu.stop();
            JFileChooser fc = new JFileChooser();
            try
            {
                BufferedReader in = new BufferedReader(new FileReader("prefs.txt"));
                String path = in.readLine();
                in.close();
                if (path != null)
                {
                    File f = new File(path);
                    if (f.isDirectory())
                        fc.setCurrentDirectory(f);
                }
            }
            catch (Exception e) {}
            
            int returnVal = fc.showDialog(this, "Save JPC Snapshot");
            File file = fc.getSelectedFile();
            try
            {
                if (file != null)
                {
                    BufferedWriter out = new BufferedWriter(new FileWriter("prefs.txt"));
                    out.write(file.getPath());
                    out.close();
                }
            }
            catch (Exception e) {e.printStackTrace();}
            
            if (returnVal == 0)
                try
                {
                    DataOutputStream out = new DataOutputStream(new FileOutputStream(file));
                    ZipOutputStream zip = new ZipOutputStream(out);
                    
                    ((PC) objects.getObject(PC.class)).saveState(zip);
                    ((PCMonitorFrame) objects.getObject(PCMonitorFrame.class)).saveState(zip);
                    zip.close();
                }
                catch (IOException e)
                {
                    e.printStackTrace();
                }
        }
        else if (src == createBlankDisk)
            createBlankHardDisk();
        else if (src == processorFrame)
        {
            ProcessorFrame pf = (ProcessorFrame) objects.getObject(ProcessorFrame.class);
            if (pf != null)
                bringToFront(pf);
            else
            {
                pf = new ProcessorFrame();
                addInternalFrame(desktop, 10, 10, pf);
            }
        }
        else if (src == physicalMemoryViewer)
        {
            MemoryViewer mv = (MemoryViewer) objects.getObject(MemoryViewer.class);
            
            if (mv != null)
                bringToFront(mv);
            else
            {
                mv = new MemoryViewer("Physical Memory");
                addInternalFrame(desktop, 360, 50, mv);
            }
        }
        else if (src == linearMemoryViewer)
        {
            LinearMemoryViewer lmv = (LinearMemoryViewer) objects.getObject(LinearMemoryViewer.class);
            
            if (lmv != null)
                bringToFront(lmv);
            else
            {
                lmv = new LinearMemoryViewer("Linear Memory");
                addInternalFrame(desktop, 360, 50, lmv);
            }
        }
        else if (src == breakpoints)
        {
            BreakpointsFrame bp = (BreakpointsFrame) objects.getObject(BreakpointsFrame.class);
            if (bp != null)
                bringToFront(bp);
            else
            {
                bp = new BreakpointsFrame();
                addInternalFrame(desktop, 550, 360, bp);
            }
        }
        else if (src == opcodeFrame)
        {
            OpcodeFrame op = (OpcodeFrame) objects.getObject(OpcodeFrame.class);
            if (op != null)
                bringToFront(op);
            else
            {
                op = new OpcodeFrame();
                addInternalFrame(desktop, 100, 200, op);
            }
        }
        else if (src == traceFrame)
        {
            ExecutionTraceFrame tr = (ExecutionTraceFrame) objects.getObject(ExecutionTraceFrame.class);
            if (tr != null)
                bringToFront(tr);
            else
            {
                tr = new ExecutionTraceFrame();
                addInternalFrame(desktop, 30, 100, tr);
            }
        }
        else if (src == monitor)
        {
            PCMonitorFrame m = (PCMonitorFrame) objects.getObject(PCMonitorFrame.class);
            if (m != null)
                bringToFront(m);
            else
            {
                m = new PCMonitorFrame();
                addInternalFrame(desktop, 30, 30, m);
            }
        }  
//         else if (src == frequencies)
//         {
//             OpcodeFrequencyFrame f = (OpcodeFrequencyFrame) objects.getObject(OpcodeFrequencyFrame.class);
//             if (f != null)
//                 bringToFront(f);
//             else
//             {
//                 f = new OpcodeFrequencyFrame();
//                 addInternalFrame(desktop, 550, 30, f);
//             }
//         }
        else if (src == codeBlockTreeFrame)
        {
            CodeBlockCacheFrame f = (CodeBlockCacheFrame) objects.getObject(CodeBlockCacheFrame.class);
            if (f != null)
                bringToFront(f);
            else
            {
                f = new CodeBlockCacheFrame();
                addInternalFrame(desktop, 60, 60, f);
            }
        }

        refresh();
    }

    public void notifyExecutionStarted()
    {
        for (int i=0; i<objects.getSize(); i++)
        {
            Object obj = objects.getObjectAt(i);
            if (!(obj instanceof PCListener))
                continue;

            try
            {
                PCListener l = (PCListener) obj;
                l.executionStarted();
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
        }
    }

    public void notifyExecutionStopped()
    {
        for (int i=0; i<objects.getSize(); i++)
        {
            Object obj = objects.getObjectAt(i);
            if (!(obj instanceof PCListener))
                continue;

            try
            {
                PCListener l = (PCListener) obj;
                l.executionStopped();
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
        }
    }

    public void notifyPCDisposed()
    {
        for (int i=0; i<objects.getSize(); i++)
        {
            Object obj = objects.getObjectAt(i);
            if (!(obj instanceof PCListener))
                continue;

            try
            {
                PCListener l = (PCListener) obj;
                l.PCDisposed();
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
        }
    }

    public void notifyPCCreated()
    {
        for (int i=0; i<objects.getSize(); i++)
        {
            Object obj = objects.getObjectAt(i);
            if (!(obj instanceof PCListener))
                continue;

            try
            {
                PCListener l = (PCListener) obj;
                l.PCCreated();
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
        }
    }

    public void refresh()
    {
        for (int i=0; i<objects.getSize(); i++)
        {
            Object obj = objects.getObjectAt(i);
            if (!(obj instanceof PCListener))
                continue;
            
            try
            {
                PCListener l = (PCListener) obj;
                l.refreshDetails();
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
        }
    }

    public PC loadNewPC(PC pc)
    {
        PC oldPC = (PC) objects.removeObject(PC.class);
        if (oldPC != null)
            notifyPCDisposed();
        
        JInternalFrame[] frames = desktop.getAllFrames();
        for (int i=0; i<frames.length; i++)
            frames[i].dispose();
        runMenu.refresh();

        objects.removeObject(Processor.class);
        objects.removeObject(PhysicalAddressSpace.class);
        objects.removeObject(LinearAddressSpace.class);
        objects.removeObject(VGACard.class);
        objects.removeObject(Keyboard.class);
        objects.removeObject(ProcessorAccess.class);
        objects.removeObject(CodeBlockRecord.class);
        
        for (int i=0; i<10; i++)
        {
            System.gc();
            try
            {
                Thread.sleep(100);
            }
            catch (Exception e) {}
        }

        setTitle("JPC Debugger            Boot Device: "+pc.getBootDevice());
        objects.addObject(pc);
        objects.addObject(pc.getProcessor());
        objects.addObject(pc.getLinearMemory());
        objects.addObject(pc.getPhysicalMemory());
        objects.addObject(pc.getGraphicsCard());
        objects.addObject(pc.getKeyboard());

        ProcessorAccess pca = new ProcessorAccess(pc.getProcessor());
        codeBlocks = new CodeBlockRecord(pc);

        objects.addObject(pca);
        objects.addObject(codeBlocks);

        runMenu.refresh();
        notifyPCCreated();

         //processorFrame.doClick();
         //breakpoints.doClick();
        monitor.doClick();
         //codeBlockTreeFrame.doClick();
         //opcodeFrame.doClick();
        
        return pc;
    }

    public PC createPC(DriveSet drives) throws IOException
    {
        PC newPC = new PC(new VirtualClock(), drives);
        
        BlockDevice fdd = drives.getFloppyDrive(0);
        if (fdd != null)
            floppyDisk.setSelectedFile(new File(fdd.getImageFileName()), fdd == drives.getBootDevice());

        BlockDevice hdd = drives.getHardDrive(0);
        if (hdd != null)
            hardDisk.setSelectedFile(new File(hdd.getImageFileName()), hdd == drives.getBootDevice());

        return loadNewPC(newPC);
    }

    public PC createPC(String[] args) throws IOException
    {
        DriveSet drives = DriveSet.buildFromArgs(args);
        PC pc = createPC(drives);
        String snapShot = ArgProcessor.findArg(args, "ss" , null);
        if (snapShot != null)
        {
            //load PC snapshot
            File f = new File(snapShot);
            pc.loadState(f);
            ((PCMonitorFrame) objects.getObject(PCMonitorFrame.class)).resizeDisplay();
            ((PCMonitorFrame) objects.getObject(PCMonitorFrame.class)).loadMonitorState(f);
        }
        return pc;
    }
    
    class Hz extends JLabel implements ActionListener
    {
        DecimalFormat fmt;
        long lastCount, lastTime;

        Hz()
        {
            super("MHz = 0");
            fmt = new DecimalFormat("#.##");
            lastTime = System.currentTimeMillis();
            javax.swing.Timer timer = new javax.swing.Timer(1000, this);
            timer.setRepeats(true);
            timer.start();
        }

        public void actionPerformed(ActionEvent evt)
        {
            if (codeBlocks == null)
                return;

            long count = codeBlocks.getInstructionCount();
            long decoded = codeBlocks.getDecodedCount();
            long optimised = codeBlocks.getOptimisedBlockCount();
            long executed = codeBlocks.getExecutedBlockCount();
            long now = System.currentTimeMillis();

            double mhz = 1000.0*(count - lastCount)/(now - lastTime)/1000000;
            setText("Decoded: ("+decoded+" x86 Instr) ("+optimised+" UBlocks) | Executed: ("+count+" x86 Instr) ("+executed+" UBlocks) | "+fmt.format(mhz)+" MHz");
            lastCount = count;
            lastTime = now;
        }
    }

    public void createBlankHardDisk()
    {
        try
        {
            JFileChooser chooser = (JFileChooser) objects.getObject(JFileChooser.class);
            if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION)
                return;

            String sizeString = JOptionPane.showInputDialog(this, "Enter the size in MB for the disk", "Disk Image Creation", JOptionPane.QUESTION_MESSAGE);
            if (sizeString == null)
                return;
            long size = Long.parseLong(sizeString)*1024l*1024l;
            if (size < 0)
                throw new Exception("Negative file size");
                    
            RandomAccessFile f = new RandomAccessFile(chooser.getSelectedFile(), "rw");
            f.setLength(size);
            f.close();
        }
        catch (Exception e) 
        {
            alert("Failed to create blank disk "+e, "Create Disk", JOptionPane.ERROR_MESSAGE);
        }
    }

    public static Object getObject(Class cls)
    {
        return instance.get(cls);
    }

    public static JPC getInstance()
    {
        return instance;
    }

    public static void main(String[] args) throws IOException
    {
	initialise();

	boolean fullScreen = true;
	for (int i=0; i<args.length; i++)
	    if (args[i].startsWith("full"))
	    {
		fullScreen = true;
		break;
	    }

	instance = new JPC(fullScreen);
	instance.validate();
	instance.setVisible(true);

	if (args.length > 0)
	    instance.createPC(args);
    }
}
