/*
    JPC-RR: A x86 PC Hardware Emulator
    Release 1

    Copyright (C) 2007-2009 Isis Innovation Limited
    Copyright (C) 2009-2011 H. Ilari Liusvaara
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
    private JTextField gameName;

    public static class AuthorElement
    {
        String fullName;
        String nickName;
    }

    public class Response
    {
        public AuthorElement[] authors;
        public String gameName;
    }

    public AuthorsDialog(AuthorElement[] existing, String _gameName)
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

        JPanel gnPanel = new JPanel();
        gnPanel.setLayout(new BoxLayout(gnPanel, BoxLayout.X_AXIS));
        panel.add(gnPanel);

        gnPanel.add(new JLabel("Game name: "));
        gnPanel.add(gameName = new JTextField(_gameName, 40));

        JPanel buttonPanel = new JPanel();
        buttonPanel.setLayout(new BoxLayout(buttonPanel, BoxLayout.X_AXIS));
        panel.add(buttonPanel);

        JButton addButton = new JButton("Add");
        addButton.setActionCommand("ADD");
        addButton.addActionListener(this);
        addButton.setMnemonic(KeyEvent.VK_A);
        removeButton = new JButton("Remove");
        removeButton.setActionCommand("REMOVE");
        removeButton.addActionListener(this);
        removeButton.setMnemonic(KeyEvent.VK_R);
        removeButton.setEnabled(false);
        JButton okButton = new JButton("Ok");
        okButton.setActionCommand("CLOSE");
        okButton.addActionListener(this);
        okButton.setMnemonic(KeyEvent.VK_O);
        JButton cancelButton = new JButton("Cancel");
        cancelButton.setActionCommand("CANCEL");
        cancelButton.addActionListener(this);
        cancelButton.setMnemonic(KeyEvent.VK_C);

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
            AuthorElement e = new AuthorElement();
            e.fullName = e.nickName = "";
            model.addAuthor(e);
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
            response.gameName = gameName.getText();
            response.authors = model.toArray();

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
        private Vector<AuthorElement> authors;

        private AuthorModel(AuthorElement[] existing)
        {
            authors = new Vector<AuthorElement>();
            if(existing != null)
                for(AuthorElement name : existing) {
                    AuthorElement c = new AuthorElement();
                    c.fullName = name.fullName;
                    if(c.fullName == null)
                        c.fullName = "";
                    c.nickName = name.nickName;
                    if(c.nickName == null)
                        c.nickName = "";
                    authors.add(c);
                }
        }

        public void addAuthor(AuthorElement n)
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
            return 2;
        }

        public int getRowCount()
        {
            return authors.size();
        }

        public String getColumnName(int column)
        {
            if(column == 0)
                return "Full name";
            else
                return "Nickname";
        }

        public boolean isCellEditable(int rowIndex, int columnIndex)
        {
            return true;
        }

        public Object getValueAt(int row, int col)
        {
            AuthorElement e = authors.get(row);
            if(col == 0)
                return e.fullName;
            else
                return e.nickName;
        }

        public void setValueAt(Object o, int row, int col)
        {
            AuthorElement e = null;
            if(row < authors.size())
                e = authors.get(row);
            else {
               e = new AuthorElement();
               e.fullName = e.nickName = "";
            }
            if(col == 0)
                e.fullName = (String)o;
            else
                e.nickName = (String)o;

            authors.set(row, e);
        }

        public AuthorElement[] toArray()
        {
            AuthorElement[] out = new AuthorElement[authors.size()];
            int i = 0;
            for(AuthorElement e : authors) {
                out[i] = new AuthorElement();

                if(e.fullName.equals(""))
                    out[i].fullName = null;
                else
                    out[i].fullName = e.fullName;

                if(e.nickName.equals(""))
                    out[i].nickName = null;
                else
                    out[i].nickName = e.nickName;

                i++;
            }
            return out;
        }
    }

    public static String readGameNameFromHeaders(String[][] headers)
    {
        if(headers == null)
            return "";
        for(String[] header : headers) {
            if(header == null || header.length != 2)
                continue;
            if("GAMENAME".equals(header[0]))
                return header[1];
        }
        return "";
    }

    public static AuthorElement[] readAuthorsFromHeaders(String[][] headers)
    {
         //Put fake header if none.
         if(headers == null)
             headers = new String[1][];

        //First count how many authors are there.
        int authorCount = 0;
        for(String[] header : headers) {
            boolean interesting = false;
            boolean multi = true;
            if(header == null || header.length == 0)
                continue;
            if(header[0].equals("AUTHORS"))
                interesting = true;
            if(header[0].equals("AUTHORNICKS"))
                interesting = true;
            if(header[0].equals("AUTHORFULL")) {
                interesting = true;
                multi = false;
            }
            if(!interesting)
                continue;
            if(multi)
               authorCount += (header.length - 1); //-1 for header type.
            else
               authorCount++;
        }

        AuthorElement[] authors = new AuthorElement[authorCount];
        for(int k = 0; k < authors.length; k++)
            authors[k] = new AuthorElement();

        //Then fill the authors.
        int i = 0;
        for(String[] header : headers) {
            int type = 0;
            if(header == null || header.length == 0)
                continue;
            if(header[0].equals("AUTHORS"))
                for(int j = 1; j < header.length; j++) {
                    authors[i].fullName = header[j];
                    authors[i].nickName = null;
                    i++;
                }
            if(header[0].equals("AUTHORNICKS"))
                for(int j = 1; j < header.length; j++) {
                    authors[i].nickName = header[j];
                    authors[i].fullName = null;
                    i++;
                }
            if(header[0].equals("AUTHORFULL")) {
                if(header.length != 3) {
                    System.err.println("Warning: Skipping bad AUTHORFULL header");
                    continue;
                }
                authors[i].fullName = header[1];
                authors[i].nickName = header[2];
                i++;
            }
        }
        return authors;
    }

    public static String[][] rewriteHeaderAuthors(String[][] headers, AuthorElement[] authors, String gameName)
    {
         //Put fake header if none.
         if(headers == null)
             headers = new String[1][];

        //First count number of other headers.
        int headerCount = 0;
        for(String[] header : headers) {
            boolean interesting = true;
            if(header == null || header.length == 0)
                continue;
            if(header[0].equals("AUTHORS"))
                interesting = false;
            if(header[0].equals("AUTHORNICKS"))
                interesting = false;
            if(header[0].equals("AUTHORFULL"))
                interesting = false;
            if(header[0].equals("GAMENAME"))
                interesting = false;
            if(!interesting)
                continue;
            headerCount++;
        }

        //Then count number of headers required by authors.
        int cat1 = 0;
        int cat2 = 0;
        for(AuthorElement e : authors) {
            if(e.fullName != null && e.nickName == null)
               if(cat1++ == 0)
                   headerCount++;
            if(e.fullName == null && e.nickName != null)
               if(cat2++ == 0)
                   headerCount++;
            if(e.fullName != null && e.nickName != null)
               headerCount++;
        }
        if(!("".equals(gameName)))
            headerCount++;

        //All headers removed?
        if(headerCount == 0)
            return null;

        //Copy the other headers.
        String[][] newHeaders = new String[headerCount][];
        int i = 0;
        for(String[] header : headers) {
            boolean interesting = true;
            if(header == null || header.length == 0)
                continue;
            if(header[0].equals("AUTHORS"))
                interesting = false;
            if(header[0].equals("AUTHORNICKS"))
                interesting = false;
            if(header[0].equals("AUTHORFULL"))
                interesting = false;
            if(header[0].equals("GAMENAME"))
                interesting = false;
            if(!interesting)
                continue;
            newHeaders[i++] = header;
        }

        //Write AUTHORS header.
        if(cat1 > 0) {
            String[] table = new String[cat1 + 1];
            newHeaders[i++] = table;
            table[0] = "AUTHORS";
            int j = 1;
            for(AuthorElement e : authors)
                if(e.fullName != null && e.nickName == null)
                    table[j++] = e.fullName;
        }

        //Write AUTHORNICKS header.
        if(cat2 > 0) {
            String[] table = new String[cat2 + 1];
            newHeaders[i++] = table;
            table[0] = "AUTHORNICKS";
            int j = 1;
            for(AuthorElement e : authors)
                if(e.fullName == null && e.nickName != null)
                    table[j++] = e.nickName;
        }

        //Write AUTHORFULL headers.
        for(AuthorElement e : authors)
            if(e.fullName != null && e.nickName != null) {
                String[] table = new String[3];
                newHeaders[i++] = table;
                table[0] = "AUTHORFULL";
                table[1] = e.fullName;
                table[2] = e.nickName;
            }
        //Write GAMENAME header.
        if(!("".equals(gameName)))
            newHeaders[i++] = new String[]{"GAMENAME", gameName};

        return newHeaders;
    }
}
