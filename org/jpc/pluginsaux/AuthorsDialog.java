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

import org.jpc.bus.Bus;
import org.jpc.bus.GameInfo;
import javax.swing.*;
import javax.swing.table.*;
import javax.swing.event.*;
import java.awt.event.*;
import java.awt.*;
import java.util.*;
import static org.jpc.Misc.callShowOptionDialog;
import static org.jpc.Misc.errorDialog;

public class AuthorsDialog implements ActionListener, WindowListener
{
    private JFrame window;
    private JTable table;
    private GameInfo response;
    private boolean answerReady;
    private AuthorModel model;
    private JButton removeButton;
    private JTextField gameName;
    private Bus bus;

    public static class AuthorElement
    {
        String fullName;
        String nickName;
    }

    public AuthorsDialog(Bus _bus)
    {
        bus = _bus;
        GameInfo g = (GameInfo)(_bus.executeCommandNoFault("get-gameinfo", null)[0]);
        response = null;
        answerReady = false;
        window = new JFrame("Change run authors");
        JPanel panel = new JPanel();
        BoxLayout layout = new BoxLayout(panel, BoxLayout.Y_AXIS);
        panel.setLayout(layout);

        AuthorElement[] existing = new AuthorElement[g.getNameCount()];
        for(int i = 0; i < existing.length; i++) {
            existing[i].fullName = g.getName(i);
            existing[i].nickName = g.getNick(i);
        }
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
        gnPanel.add(gameName = new JTextField(g.getGameName(), 40));

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

            response = new GameInfo();
            response.setGameName(gameName.getText());
            AuthorElement[] newE = model.toArray();
            for(int i = 0; i < newE.length; i++)
               response.insertName(i, newE[i].fullName, newE[i].nickName);

            window.setVisible(false);
            window.dispose();
            try {
                bus.executeCommandNoFault("set-gameinfo", new Object[]{response});
            } catch(Exception e) {
                errorDialog(e, "Can't set gameinfo", null, "Dismiss");
                return;
            }
            window.dispose();
        } else if(command == "CANCEL") {
            window.dispose();
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
}
