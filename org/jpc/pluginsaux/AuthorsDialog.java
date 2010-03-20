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

import javax.swing.*;
import java.awt.event.*;
import java.awt.*;

public class AuthorsDialog implements ActionListener, WindowListener
{
    private JFrame window;
    private JPanel panel;
    private JPanel panel2;
    private Response response;
    private boolean answerReady;
    private JTextField[] texts;

    public class Response
    {
        public String[] authors;
    }

    public AuthorsDialog(String[] existing)
    {
        response = null;
        answerReady = false;
        window = new JFrame("Change run authors");
        panel = new JPanel(null);
        BoxLayout layout2 = new BoxLayout(panel, BoxLayout.Y_AXIS);
        panel.setLayout(layout2);
        GridLayout layout = new GridLayout(0, 1);
        panel2 = new JPanel(layout);
        window.add(panel);
        window.addWindowListener(this);

        panel.add(panel2);

        if(existing == null) {
            texts = new JTextField[1];
            texts[0] = new JTextField("", 40);
        } else {
            texts = new JTextField[1 + existing.length];
            for(int i = 0; i < existing.length; i++)
               texts[i] = new JTextField(existing[i], 40);
            texts[existing.length] = new JTextField("", 40);
        }

        for(int i = 0; i < texts.length; i++)
            panel2.add(texts[i]);

        JButton ass = new JButton("Add");
        ass.setActionCommand("ADD");
        ass.addActionListener(this);
        JButton close = new JButton("Close");
        close.setActionCommand("CLOSE");
        close.addActionListener(this);
        JButton cancl = new JButton("Cancel");
        cancl.setActionCommand("CANCEL");
        cancl.addActionListener(this);
        panel.add(ass);
        panel.add(close);
        panel.add(cancl);

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

    public void actionPerformed(ActionEvent evt)
    {
        String command = evt.getActionCommand();
        if(command == "ADD") {
            JTextField[] ntexts = new JTextField[texts.length + 1];
            System.arraycopy(texts, 0, ntexts, 0, texts.length);
            ntexts[texts.length] = new JTextField("", 40);
            panel2.add(ntexts[texts.length]);
            texts = ntexts;
            window.pack();
        } else if(command == "CLOSE") {
            response = new Response();
            response.authors = new String[texts.length];
            for(int i = 0; i < texts.length; i++) {
                String str = texts[i].getText();
                if("".equals(str))
                    str = null;
                response.authors[i] = str;
            }
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
}
