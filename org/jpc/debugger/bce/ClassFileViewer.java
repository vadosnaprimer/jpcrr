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

package org.jpc.debugger.bce;

import java.io.*;
import java.awt.*;
import javax.swing.*;

import org.jpc.debugger.util.*;
import org.jpc.debugger.*;

public class ClassFileViewer extends ApplicationFrame 
{
    private ClassFile classFile;
    private JPanel props;
    
    private byte[] rawData;
    private BinaryDataTableModel rawDataModel;
    private JTable rawDataTable;

    public ClassFileViewer(File src) throws IOException
    {
        super("Class File Viewer");
        
        /*classFile = new ClassFile(new DataInputStream(new FileInputStream(src)));
        
        props = new JPanel(new GridLayout(0, 2, 10, 10));
        props.setBorder(BorderFactory.createTitledBorder("Basic Properties"));
        add("Center", props);

        addProperty("Magic Number", hex(ClassFile.MAGIC_NUMBER));
        addProperty("Major Version", ""+classFile.getMajorVersion());
        addProperty("Minor Version", ""+classFile.getMinorVersion());*/
        
        rawData = new byte[0];
        rawDataModel = new BinaryDataTableModel();
        Font f = new Font("Monospaced", Font.PLAIN, 12);
        rawDataTable = new JTable(rawDataModel);
        rawDataTable.setFont(f);
        rawDataModel.setupColumnWidths(rawDataTable);

        add("Center", new JScrollPane(rawDataTable));

        loadData(src);
    }

    public void loadData(File f) throws IOException
    {
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        FileInputStream fin = null;
        try
        {
            fin = new FileInputStream(f);
            while (true)
            {
                int b = fin.read();
                if (b < 0)
                    break;
                bout.write((byte) b);
            }

            rawData = bout.toByteArray();
            rawDataModel.fireTableDataChanged();
        }
        finally
        {
            try
            {
                fin.close();
            }
            catch (Exception e){}
        }
    }

    class BinaryDataTableModel extends BasicTableModel
    {
        BinaryDataTableModel()
        {
            super(new String[]{"Address", "0-3", "4-7", "8-B", "C-F", "ASCII"}, new int[]{90, 90, 90, 90, 90, 140});
        }

        public int getRowCount()
        {
            return rawData.length / 16 + 1;
        }

        private char getASCII(byte b)
        {
            if ((b >= 32) && (b < 127))
                return (char) b;
            return '.';
        }

        private String zeroPadHex(long value, int size)
        {
            StringBuffer result = new StringBuffer(Long.toHexString(value).toUpperCase());
            while (result.length() < size)
                result.insert(0, '0');
            
            return result.toString();
        }

        private String asciiText(int address)
        {
            StringBuffer buffer = new StringBuffer();
            for (int i=0; i<16; i++)
            {
                try
                {
                    byte b = (byte) rawData[address + i];
                    buffer.append(getASCII(b));
                }
                catch (ArrayIndexOutOfBoundsException e) {}
            }

            return buffer.toString();
        }

        public Object getValueAt(int row, int column)
        {
            int index = row * 16;

            switch (column)
            {
            case 0:
                return zeroPadHex(index, 8);
            case 5:
                return asciiText(index);
            default:
                int address = index + (column - 1)*4;
                StringBuffer buf = new StringBuffer();
                for (int i=0; i<4; i++)
                {
                    try
                    {
                        int val = rawData[address+i];
                        buf.append(zeroPadHex(0xFF & val, 2)+" ");
                    }
                    catch (ArrayIndexOutOfBoundsException e) {}
                }

                return buf;
            }            
        }
    }

    private String hex(int value)
    {
        return Integer.toHexString(value).toUpperCase();
    }

    private void addProperty(String name, String value)
    {
        props.add(new JLabel(name));
        JTextField tf = new JTextField(value);
        tf.setEditable(false);
        props.add(tf);
    }

    public static void main(String[] args) throws Exception
    {
        ApplicationFrame.initialise();
        String fileName = "Test.class";
        if (args.length > 0)
            fileName = args[0];

        ClassFileViewer v = new ClassFileViewer(new File(fileName));
        v.setBounds(50, 50, 600, 500);
        v.validate();
        v.setVisible(true);
    }
}
