/*
    JPC-RR: A x86 PC Hardware Emulator
    Release 1

    Copyright (C) 2007-2009 Isis Innovation Limited
    Copyright (C) 2009-2010 H. Ilari Liusvaara
    Copyright (C) 2010 Henrik Andersson

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

import javax.swing.*;
import javax.swing.table.*;
import javax.swing.event.*;
import java.awt.event.*;
import java.awt.*;
import java.util.*;

public class AuthorsDialog implements ActionListener, WindowListener
{
    private JFrame window;
    private JTable table;
    private Response response;
    private boolean answerReady;
    private AuthorModel model;
    private JButton removeButton;

    public class Response
    {
        public String[] authors;
    }

    public AuthorsDialog(String[] existing)
    {
        response = null;
        answerReady = false;
        window = new JFrame("Change run authors");
        JPanel panel = new JPanel();
        BoxLayout layout = new BoxLayout(panel, BoxLayout.Y_AXIS);
        panel.setLayout(layout);

        model = new AuthorModel(existing);

        table = new JTable(model);
        table.getSelectionModel().addListSelectionListener(new SelectionListener());

        JScrollPane scroll = new JScrollPane(table);
        panel.add(scroll);

        window.add(panel);
        window.addWindowListener(this);

        JPanel buttonPanel = new JPanel();
        buttonPanel.setLayout(new BoxLayout(buttonPanel, BoxLayout.X_AXIS));
        panel.add(buttonPanel);

        JButton addButton = new JButton("Add");
        addButton.setActionCommand("ADD");
        addButton.addActionListener(this);
        removeButton = new JButton("Remove");
        removeButton.setActionCommand("REMOVE");
        removeButton.addActionListener(this);
        removeButton.setEnabled(false);
        JButton okButton = new JButton("Ok");
        okButton.setActionCommand("CLOSE");
        okButton.addActionListener(this);
        JButton cancelButton = new JButton("Cancel");
        cancelButton.setActionCommand("CANCEL");
        cancelButton.addActionListener(this);

        buttonPanel.add(addButton);
        buttonPanel.add(removeButton);
        buttonPanel.add(okButton);
        buttonPanel.add(cancelButton);

        Dimension size = buttonPanel.getPreferredSize();
        size.height = (int)(size.width*0.5);
        scroll.setPreferredSize(size);

        window.pack();
        window.setVisible(true);
        window.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
    }

    public synchronized Response waitClose()
    {
        if(answerReady) {
            answerReady = false;
            return response;
        }
        while(!answerReady) {
            try {
                wait();
            } catch(InterruptedException e) {
            }
        }
        answerReady = false;
        return response;
    }

    private class SelectionListener implements ListSelectionListener
    {
        public void valueChanged(ListSelectionEvent e)
        {
            boolean rowSelected = table.getSelectedRow() != -1;
            removeButton.setEnabled(rowSelected);
        }
    }

    public void actionPerformed(ActionEvent evt)
    {
        String command = evt.getActionCommand();
        if(command == "ADD") {
            model.addAuthor("");
            int toselect = model.getRowCount() - 1;
            table.editCellAt(toselect, 0);
            table.setRowSelectionInterval(toselect, toselect);
            table.requestFocus();
        } else if(command == "REMOVE") {
            int index = table.getSelectedRow();
            index = table.convertRowIndexToModel(index);
            model.removeAuthor(index);
        } else if(command == "CLOSE") {
            //end editing, if any
            CellEditor editor = table.getCellEditor();
            if(editor != null)
                editor.stopCellEditing();

            response = new Response();
            response.authors = model.toArray();
            for(int i = 0; i < response.authors.length; i++)
                if(response.authors[i].equals(""))
                    response.authors[i] = null;

            window.setVisible(false);
            window.dispose();
            synchronized(this) {
                answerReady = true;
                notifyAll();
            }
        } else if(command == "CANCEL") {
            window.setVisible(false);
            window.dispose();
            synchronized(this) {
                response = null;
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
            response = null;
            answerReady = true;
            notifyAll();
        }
    }

    private class AuthorModel extends AbstractTableModel
    {
        private static final long serialVersionUID = 1;
        private Vector<String> authors;

        private AuthorModel(String[] existing)
        {
            authors = new Vector<String>();
            if(existing != null)
                for(String name : existing)
                    authors.add(name);
        }

        public void addAuthor(String n)
        {
            authors.add(n);
            int index = authors.size();
            this.fireTableRowsInserted(index, index);
        }

        public void removeAuthor(int index)
        {
            //end editing, if any
            CellEditor editor = table.getCellEditor();
            if(editor != null)
                editor.stopCellEditing();

            int toselect = 0;
            authors.remove(index);
            this.fireTableRowsDeleted(index, index);
            if(index == authors.size())
                toselect = authors.size() - 1;
            else
                toselect = index;
            if(toselect >= 0)
                table.setRowSelectionInterval(toselect, toselect);
        }

        public int getColumnCount()
        {
            return 1;
        }

        public int getRowCount()
        {
            return authors.size();
        }

        public String getColumnName(int column)
        {
            return "Name";
        }

        public boolean isCellEditable(int rowIndex, int columnIndex)
        {
            return true;
        }

        public Object getValueAt(int row, int col)
        {
            return authors.get(row);
        }

        public void setValueAt(Object o, int row, int col)
        {
            authors.set(row, (String)o);
        }

        public String[] toArray()
        {
            String[] out = new String[authors.size()];
            authors.copyInto(out);
            return out;
        }
    }
}
