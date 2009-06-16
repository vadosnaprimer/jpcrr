/*
    JPC: A x86 PC Hardware Emulator for a pure Java Virtual Machine
    Release Version 2.0

    A project from the Physics Dept, The University of Oxford

    Copyright (C) 2007 Isis Innovation Limited
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
 
    Details (including contact information) can be found at: 

    www.physics.ox.ac.uk/jpc
*/

package org.jpc.support;

import java.io.*;
import javax.swing.*;
import java.awt.GridLayout;

public class PleaseWait extends JFrame
{
    private boolean poppedUp;
    private javax.swing.JLabel label;

    public PleaseWait(String message)
    {
        super("Please Wait...");
        poppedUp = false;
        label = new JLabel(message);
        JPanel panel = new JPanel(new GridLayout(1,1));
        panel.add(label);
        this.add(panel);
        this.pack();
        label.setVisible(true);
        panel.setVisible(true);
    }

    public void popUp()
    {
        if(poppedUp)
            return;
        this.setVisible(true);
        this.repaint();
        label.repaint();
        poppedUp = true;
    }

    public void popDown()
    {
        if(!poppedUp)
            return;
        this.setVisible(false);
        poppedUp = false;
    }
}
