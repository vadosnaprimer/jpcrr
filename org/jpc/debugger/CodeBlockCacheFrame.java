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
import java.lang.reflect.*;
import java.awt.*;
import java.awt.event.*;

import javax.swing.*;
import javax.swing.table.*;
import javax.swing.event.*;
import javax.swing.text.*;
import javax.swing.undo.*;

import org.jpc.debugger.util.*;
import org.jpc.emulator.*;
import org.jpc.emulator.processor.*;
import org.jpc.emulator.motherboard.*;
import org.jpc.emulator.memory.*;
import org.jpc.emulator.memory.codeblock.*;
import org.jpc.emulator.memory.codeblock.cache.*;
import org.jpc.emulator.memory.codeblock.optimised.*;

public class CodeBlockCacheFrame extends UtilityFrame implements PCListener, ActionListener
{
    private long unknownBlockCount, unknownX86Count;

    private CodeBlockModel model;
    private JTextField foundTextField;
    private JTextField addedTextField;
    private JTextField totalDecodedTextField;
    private JTextField tableRowsTextField;
    private JTextField tableInstanceTotalTextField;

    public CodeBlockCacheFrame() 
    {
        super("Code Block Cache Details");
        setPreferredSize(new Dimension(900, 400));
        JPC.getInstance().objects().addObject(this);
        
        model = new CodeBlockModel();
        JTable table = new MicrocodeOverlayTable(model, 3, false);
        table.setAutoCreateRowSorter(true);
        model.setupColumnWidths(table);

        add("Center", new JScrollPane(table));
        
        foundTextField = new JTextField(10);
        foundTextField.setEditable(false);
        addedTextField = new JTextField(10);
        addedTextField.setEditable(false);
        totalDecodedTextField = new JTextField(10);
        totalDecodedTextField.setEditable(false);
        tableRowsTextField = new JTextField(10);
        tableRowsTextField.setEditable(false);
        tableInstanceTotalTextField = new JTextField(10);
        tableInstanceTotalTextField.setEditable(false);

        JLabel foundLabel = new JLabel("Found CBs:");
        foundLabel.setLabelFor(foundTextField);
        JLabel addedLabel = new JLabel("Added CBs:");
        addedLabel.setLabelFor(addedTextField);
        JLabel totalDecodedLabel = new JLabel("Total Decoded CBs:");
        totalDecodedLabel.setLabelFor(totalDecodedTextField);
        JLabel tableRowsLabel = new JLabel("Rows in table:");
        tableRowsLabel.setLabelFor(tableRowsTextField);
        JLabel tableInstanceTotalLabel = new JLabel("Sum of instances:");
        tableInstanceTotalLabel.setLabelFor(tableInstanceTotalTextField);


        JPanel totalsPane = new JPanel();
        totalsPane.setLayout(new FlowLayout());
        totalsPane.add(foundLabel);
        totalsPane.add(foundTextField);
        totalsPane.add(addedLabel);
        totalsPane.add(addedTextField);
        totalsPane.add(totalDecodedLabel);
        totalsPane.add(totalDecodedTextField);        
        totalsPane.add(tableRowsLabel);
        totalsPane.add(tableRowsTextField);
        totalsPane.add(tableInstanceTotalLabel);
        totalsPane.add(tableInstanceTotalTextField);
        
        add("South", totalsPane);
        installReportPanel();
    }

    public void actionPerformed(ActionEvent evt) 
    {
    }

    public synchronized void frameClosed()
    {
        JPC.getInstance().objects().removeObject(this);
    }

    public void PCCreated()
    {
        refreshDetails();
    }

    public void PCDisposed() {}
    
    public void executionStarted() {}

    public void executionStopped() 
    {
        refreshDetails();
    }

    public void refreshDetails()
    {
        PC pc = (PC) JPC.getObject(PC.class);
        if (pc == null)
            return;
        ObjectTreeCache treeCache = null/*pc.getObjectTreeCache()*/;
        if (treeCache == null)
            return;

        ObjectTreeStateMachine tree = treeCache.getObjectTree();
        tree.visitNodes(model);
        model.visitComplete();

        refreshTotalsPane(treeCache);
    }

    private void refreshTotalsPane(ObjectTreeCache treeCache)
    {
        long added = treeCache.getAddedCount();
        long found = treeCache.getFoundCount();
        addedTextField.setText("" + added);
        foundTextField.setText("" + found);
        totalDecodedTextField.setText("" + (added + found));

        int rowCount = model.getRowCount();
        tableRowsTextField.setText("" + rowCount);
        int total = 0;
        for(int i = 0; i < rowCount; i++)
            total += (Integer) model.getValueAt(i, 1);
        tableInstanceTotalTextField.setText("" + total);
    }


    class CodeBlockInfo
    {
        String id;
        int instanceCount;
        CodeBlock codeBlock;

        CodeBlockInfo(String id, int count, CodeBlock block)
        {
            this.id = id;
            this.codeBlock = block;
            instanceCount = count;
        }
    }

    class CodeBlockModel extends BasicTableModel implements ObjectTreeStateMachineVisitor
    {
        Vector buffer;
        CodeBlockInfo[] blockInfo;

        CodeBlockModel()
        {
            super(new String[]{"Block ID", "Instance Count", "X86 Count", "CodeBlock"}, new int[]{150, 150, 150, 400});
            blockInfo = new CodeBlockInfo[0];
            buffer = new Vector();
        }

        public Class getColumnClass(int col)
        {
            switch (col)
            {
            case 0:
            default:
                return String.class;
            case 1:
            case 2:
                return Integer.class;
            case 3:
                return Object.class;
            }
        }

        public boolean visitNode(ObjectTreeStateMachine.Node node)
        {
            Object o = node.peekObject();
            if (o == null)
                return true;

            CodeBlock block;
            if (o instanceof CodeBlock)
                block = (CodeBlock) o;
            else
                block = null;
            String firstLetter = "" + node.getClass().getSimpleName().charAt(0);
            CodeBlockInfo info = new CodeBlockInfo(firstLetter + node.hashCode(), node.getUsageCount(), block);
            buffer.add(info);
            return true;
        }

        public void visitComplete()
        {
            synchronized (this)
            {
                blockInfo = new CodeBlockInfo[buffer.size()];
                buffer.toArray(blockInfo);
                buffer.removeAllElements();
            }

            fireTableDataChanged();
        }

        public synchronized int getRowCount()
        {
            return blockInfo.length;
        }

        public synchronized Object getValueAt(int row, int column)
        {
            CodeBlockInfo b = blockInfo[row];

            switch (column)
            {
            case 0:
                return b.id;
            case 1:
                return new Integer(b.instanceCount);
            case 2:
                return new Integer(b.codeBlock.getX86Count());
            case 3:
                return b.codeBlock;
            }

            return null;
        }
    }
    
    /**
     * Count the instances of nodes in the tree
     *
     * @return number of nodes in the tree
     */
    /*    public int countAllNodes()
    {
        class CountAllVisitor implements ObjectTreeStateMachineVisitor
        {
            int count = 0;
            
            public boolean visitNode(ObjectTreeStateMachine.Node node)
            {
                count++;
                return true;
            }
        }
        
        CountAllVisitor visitor = new CountAllVisitor();
        visitNodes(visitor);
        return visitor.count;
    }

    public int countLeafNodes()
    {
        class CountLeafsVisitor implements ObjectTreeStateMachineVisitor
        {
            int count = 0;
            
            public boolean visitNode(ObjectTreeStateMachine.Node node)
            {
                if (node instanceof LeafNode)
                    count++;
                return true;
            }
        }

        CountLeafsVisitor visitor = new CountLeafsVisitor();
        visitNodes(visitor);
        return visitor.count;
        }*/


    /**
     * Structure class to contain node type counts
     */
    /*    public class NodeTypeCount
    {
        public int singular, binary, narrow, wide, leaf;
    }

    /**
     * Count the instances of nodes in the tree
     *
     * @return number of nodes in the tree
     */
    /*public NodeTypeCount countNodeTypes()
    {
        

        /**
         * Visitor class that will count all the leaf nodes in the tree
         */
    /*class CountTypesVisitor implements ObjectTreeStateMachineVisitor
        {
            NodeTypeCount count;

            CountTypesVisitor()
            {
                count = new NodeTypeCount();
            }

            public boolean visitNode(ObjectTreeStateMachine.Node node)
            {
                if (node instanceof SingularNode)
                {
                    count.singular++;
                    System.out.print("s");
                }
                else if (node instanceof BinaryNode)
                {
                    count.binary++;
                    System.out.print("b");
                }
                else if (node instanceof NarrowNode)
                {
                    count.narrow++;
                    System.out.print("n");
                }
                else if (node instanceof WideNode)
                {
                    count.wide++;
                    System.out.print("w");
                }
                else if (node instanceof LeafNode)
                {
                    count.leaf++;
                    System.out.print("l");
                }
                return true;
            }
        }

        CountTypesVisitor visitor = new CountTypesVisitor();
        visitNodes(visitor);
        return visitor.count;
    }*/
}









