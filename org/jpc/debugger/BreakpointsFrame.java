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
import java.io.*;
import java.awt.*;
import java.awt.event.*;

import javax.swing.*;
import javax.swing.table.*;
import javax.swing.event.*;
import javax.swing.text.*;
import javax.swing.undo.*;

import org.jpc.emulator.*;
import org.jpc.debugger.util.*;
import org.jpc.emulator.processor.*;

public class BreakpointsFrame extends UtilityFrame implements PCListener, ActionListener
{
    public static final String BREAKPOINT_FILE = "breakpoints.jpc";
    public static final long BREAKPOINT_MAGIC = 0x81057FAB7272F10l;

    private boolean edited;
    private Vector<Breakpoint> breakpoints;
    private BPModel model;
    private JTable bpTable;
    private String breakpointFileName;

    private JCheckBoxMenuItem ignoreBP, breakAtPrimary;
    private JMenuItem setBP, removeAll;

    public BreakpointsFrame()
    {
        super("Breakpoints");

        breakpointFileName = BREAKPOINT_FILE;
        breakpoints = new Vector<Breakpoint>();
        model = new BPModel();
        edited = false;

        bpTable = new JTable(model);
        model.setupColumnWidths(bpTable);

        String delBP = "Del BP";
        InputMap in = new InputMap();
        in.put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), delBP);
        ActionMap ac = new ActionMap();
        ac.setParent(bpTable.getActionMap());
        ac.put(delBP, new Deleter());

        bpTable.setInputMap(JComponent.WHEN_FOCUSED, in);
        bpTable.setActionMap(ac);

        add("Center", new JScrollPane(bpTable));

        JMenu options = new JMenu("Options");
        setBP = options.add("Set Breakpoint");
        setBP.addActionListener(this);
        options.addSeparator();
        removeAll = options.add("Remove All Breakpoints");
        removeAll.addActionListener(this);
        options.addSeparator();
        ignoreBP = new JCheckBoxMenuItem("Ignore Breakpoints");
        options.add(ignoreBP);
        breakAtPrimary = new JCheckBoxMenuItem("Break at 'Primary' breakpoints only");
        options.add(breakAtPrimary);

        JMenuBar bar = new JMenuBar();
        bar.add(new BPFileMenu());
        bar.add(options);
        setJMenuBar(bar);

        setPreferredSize(new Dimension(450, 300));
        JPC.getInstance().objects().addObject(this);
        loadBreakpoints();
    }

    public boolean breakAtPrimaryOnly()
    {
        return breakAtPrimary.getState();
    }

    public boolean ignoreBreakpoints()
    {
        return ignoreBP.getState();
    }

    public boolean isEdited()
    {
        return edited;
    }

    public void frameClosed()
    {
        if (edited)
        {
            if (JOptionPane.showConfirmDialog(this, "Do you want to save the changes to the breakpoints?", "Save Breakpoints", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE) == JOptionPane.YES_OPTION)
                saveBreakpoints();
            edited = false;
        }
        
        JPC.getInstance().objects().removeObject(this);
    }

    public void actionPerformed(ActionEvent evt)
    {
        if (evt.getSource() == setBP)
        {
            try
            {
                String input = JOptionPane.showInputDialog(this, "Enter the address (in Hex) for the breakpoint: ", "Breakpoint", JOptionPane.QUESTION_MESSAGE);
                int address = (int) Long.parseLong(input.toLowerCase(), 16);
                setBreakpoint(address);
            }
            catch (Exception e){}
        }
        else if (evt.getSource() == removeAll)
            removeAllBreakpoints();
    }

    class BPFileMenu extends JMenu implements ActionListener
    {
        private JMenuItem load, save, saveAs, importBP;

        BPFileMenu()
        {
            super("File");
            
            load = add("Load Breakpoints");
            load.addActionListener(this);
            save = add("Save Breakpoints");
            save.addActionListener(this);
            saveAs = add("Save Breakpoints As");
            saveAs.addActionListener(this);
            addSeparator();
            importBP = add("Import Breakpoints");
            importBP.addActionListener(this);
        }
        
        private String deriveBPFileName(String name)
        {
            String nm = name.toLowerCase();
            if (nm.endsWith(".jpc"))
                return name;

            int dot = nm.indexOf(".");
            if (dot < 0)
                dot = nm.length();

            return name.substring(0, dot)+".jpc";
        }
        
        public void actionPerformed(ActionEvent evt)
        {
            JFileChooser chooser = (JFileChooser) JPC.getObject(JFileChooser.class);
            if (evt.getSource() == load)
            {
                if (chooser.showOpenDialog(JPC.getInstance()) != chooser.APPROVE_OPTION)
                    return;
                
                breakpointFileName = chooser.getSelectedFile().getAbsolutePath();
                removeAllBreakpoints();
                loadBreakpoints();
            }
            else if (evt.getSource() == save)
            {
                saveBreakpoints();
            }
            else if (evt.getSource() == importBP)
            {
                if (chooser.showOpenDialog(JPC.getInstance()) != chooser.APPROVE_OPTION)
                    return;
                
                removeAllBreakpoints();
                String fileName = chooser.getSelectedFile().getAbsolutePath();
                importBreakpoints(fileName, false);
            }
            else if (evt.getSource() == saveAs)
            {
                if (chooser.showSaveDialog(JPC.getInstance()) != chooser.APPROVE_OPTION)
                    return;
                
                breakpointFileName = chooser.getSelectedFile().getAbsolutePath();
                saveBreakpoints();
            }
        }
    }
    
    class Deleter extends AbstractAction
    {
        public void actionPerformed(ActionEvent evt)
        {
            deleteBreakpoint(bpTable.getSelectedRow());
        }
    }
    
    public boolean isBreakpoint(int address)
    {
        Breakpoint bp = new Breakpoint(address);
        return breakpoints.contains(bp);
    }

    public void setBreakpoint(int address)
    {
        setBreakpoint(address, false);
    }

    public void setBreakpoint(int address, boolean isPrimary)
    {
        Breakpoint bp = new Breakpoint(address);
        int idx = breakpoints.indexOf(bp);
        if (idx < 0)
            breakpoints.add(bp);
        else
            bp = breakpoints.elementAt(idx);

        if (isPrimary)
            bp.isPrimary = isPrimary;

        edited = true; 
        JPC.getInstance().refresh();
    }

    public void removeAllBreakpoints()
    {
        breakpoints.removeAllElements();
        edited = true;
        JPC.getInstance().refresh();
    }

    public void removeBreakpoint(int address)
    {
        Breakpoint bp = new Breakpoint(address);
        int idx = breakpoints.indexOf(bp);
        if (idx < 0)
            return;

        deleteBreakpoint(idx);
    }

    public Breakpoint checkForBreak(int start, int end)
    {
        return checkForBreak(start, end, breakAtPrimary.getState());
    }

    public Breakpoint checkForBreak(int start, int end, boolean isPrimary)
    {
        if (ignoreBP.getState())
            return null;


        for (int i=0; i<breakpoints.size(); i++)
        {
            Breakpoint bp = breakpoints.elementAt(i);
            if ((bp.address >= start) && (bp.address < end)) 
            {
                if (isPrimary && !bp.isPrimary)
                    continue;

                return bp;
            }
        }

        return null;
    }

    public void deleteBreakpoint(int index)
    {
        try
        {
            breakpoints.removeElementAt(index);
        }
        catch (Exception e) {}
        edited = true;

        JPC.getInstance().refresh();
    }

    public class Breakpoint implements Comparable<Breakpoint>
    {
        private int address;
        private boolean isPrimary;
        private String name;

        Breakpoint(int addr)
        {
            this(addr, false);
        }

        Breakpoint(int addr, boolean prim)
        {
            address = addr;
            isPrimary = prim;
            name = "";
        }
        
        public boolean equals(Object another)
        {
            if (!(another instanceof Breakpoint))
                return false;

            return address == ((Breakpoint) another).address;
        }

        public int compareTo(Breakpoint bp)
        {
            return address - bp.address;
        }

        public String getName()
        {
            return name;
        }
        
        public int getAddress()
        {
            return address;
        }

        public boolean isPrimary()
        {
            return isPrimary;
        }
    }

    class BPModel extends BasicTableModel
    {
        BPModel()
        {
            super(new String[]{"Address", "Name", "Primary"}, new int[]{100, 250, 100});
        }
        
        public int getRowCount()
        {
            return breakpoints.size();
        }

        public boolean isCellEditable(int row, int column)
        {
            return true;
        }

        public Class getColumnClass(int col)
        {
            if (col == 2)
                return Boolean.class;
            return String.class;
        }

        public void setValueAt(Object obj, int row, int column)
        {
            Breakpoint bp = breakpoints.elementAt(row);

            if (column == 0)
            {
                try
                {
                    int addr = (int) Long.parseLong(obj.toString().toLowerCase(), 16);
                    bp.address = addr;
                }
                catch (Exception e) {}
            }
            else if (column == 2)
                bp.isPrimary = ((Boolean) obj).booleanValue();
            else if (column == 1)
                bp.name = obj.toString();

            int selected = sortBreakpoints(row);
            JPC.getInstance().refresh();

            if (selected >= 0)
            {
                bpTable.setRowSelectionInterval(selected, selected);
                Rectangle rect = bpTable.getCellRect(selected, 0, true);
                bpTable.scrollRectToVisible(rect);
            }
            edited = true;
        }

        public Object getValueAt(int row, int column)
        {
            Breakpoint bp = breakpoints.elementAt(row);

            switch (column)
            {
            case 0:
                return MemoryViewPanel.zeroPadHex(bp.address, 8);
            case 1:
                return bp.name;
            case 2:
                return new Boolean(bp.isPrimary);
            default:
                return "";
            }
        }
    }

    private int sortBreakpoints(int selectedRow)
    {
        int addr = -1;
        if (selectedRow >= 0)
            addr = breakpoints.elementAt(selectedRow).address;

        Breakpoint[] buffer = new Breakpoint[breakpoints.size()];
        breakpoints.toArray(buffer);
        Arrays.sort(buffer);

        int result = -1;
        breakpoints.removeAllElements();
        for (int i=0; i<buffer.length; i++)
        {
            if (buffer[i].address == addr)
                result = i;
            breakpoints.add(buffer[i]);
        }

        return result;
    }

    public boolean importBreakpoints(String fileName, boolean ignoreDots)
    {
        FileInputStream fin = null;
        breakpoints.removeAllElements();

        try
        {
            File f = new File(fileName);
            if (!f.exists())
                return false;

            fin = new FileInputStream(f);
            DataInputStream din = new DataInputStream(fin);

            while (true)
            {
                String line = din.readLine();
                if (line == null)
                    break;

                try
                {
                    int space = line.indexOf(" ");
                    String hexAddress = line.substring(0, space).trim();
                    String name = line.substring(space+1).trim();
                    
                    if (name.startsWith(".") && ignoreDots)
                        continue;

                    int addr = Integer.parseInt(hexAddress, 16);
                    Breakpoint bp = new Breakpoint(addr, false);
                    bp.name = name;
                    breakpoints.add(bp);
                }
                catch (Exception e) {}
            }
        }
        catch (EOFException e) {}
        catch (Exception e)
        {
            System.out.println("Warning: failed to import breakpoints");
            e.printStackTrace();
            alert("Error importing breakpoints from "+fileName+": "+e, JOptionPane.ERROR_MESSAGE);
            return false;
        }
        finally
        {
            try
            {
                fin.close();
            }
            catch (Exception e) {}
            sortBreakpoints(-1);
            edited = true;
        }     
        return true;
    }

    public void loadBreakpoints()
    {
        FileInputStream fin = null;
        breakpoints.removeAllElements();

        try
        {
            File f = new File(breakpointFileName);
            if (!f.exists())
                return;

            fin = new FileInputStream(f);
            DataInputStream din = new DataInputStream(fin);
            
            if (din.readLong() != BREAKPOINT_MAGIC)
                throw new IOException("Magic number mismatch");

            while (true)
            {
                int addr = din.readInt();
                boolean primary = din.readBoolean();
                String name = din.readUTF();

                Breakpoint bp = new Breakpoint(addr, primary);
                bp.name = name;
                breakpoints.add(bp);
            }
        }
        catch (EOFException e) 
        {
            setTitle("Breakpoints: "+breakpointFileName);
        }
        catch (Exception e)
        {
            System.out.println("Warning: failed to load breakpoints");
            e.printStackTrace();
            setTitle("Breakpoints: "+breakpointFileName+" ERROR");
            alert("Error loading breakpoints: "+e, JOptionPane.ERROR_MESSAGE);
        }
        finally
        {
            try
            {
                fin.close();
            }
            catch (Exception e) {}
            sortBreakpoints(-1);
            edited = false;
        }       
    }

    public void saveBreakpoints()
    {
        FileOutputStream out = null;
        try
        {
            out = new FileOutputStream(breakpointFileName);
            DataOutputStream dout = new DataOutputStream(out);
            dout.writeLong(BREAKPOINT_MAGIC);

            for (int i=0; i<breakpoints.size(); i++)
            {
                Breakpoint bp = breakpoints.elementAt(i);
                dout.writeInt(bp.address);
                dout.writeBoolean(bp.isPrimary);
                dout.writeUTF(bp.name);
            }
            
            setTitle("Breakpoints: "+breakpointFileName);
        }
        catch (Exception e)
        {
            System.out.println("Warning: failed to save breakpoints");
            e.printStackTrace();
            setTitle("Breakpoints: "+breakpointFileName+" ERROR");
            alert("Error saving breakpoints: "+e, JOptionPane.ERROR_MESSAGE);
        }
        finally
        {
            try
            {
                out.close();
            }
            catch (Exception e) {}
            edited = false;
        } 
    }

    public void PCCreated() {}

    public void PCDisposed() {}
    
    public void executionStarted() {}

    public void executionStopped() {}

    public void refreshDetails()
    {
        model.fireTableDataChanged();
    }
}
