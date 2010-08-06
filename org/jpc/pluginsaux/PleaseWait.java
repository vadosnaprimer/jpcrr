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
import java.awt.*;

public class PleaseWait extends JFrame
{
    private static final long serialVersionUID = 2;
    private boolean poppedUp;
    private javax.swing.JLabel label;

    public PleaseWait(String message)
    {
        super("Please Wait...");
        poppedUp = false;
        label = new JLabel(message);
        label.setFont(label.getFont().deriveFont(18.0f));
        JPanel panel = new JPanel(new GridLayout(1,1));
        panel.add(label);
        this.add(panel);
        this.pack();
        label.setVisible(true);
        panel.setVisible(true);
    }

    public void popUp()
    {
        int screenW = 1280;
        int screenH = 1024;
        Dimension wanted = label.getPreferredSize();
        DisplayMode mode = null;
        GraphicsDevice device = null;
        GraphicsEnvironment environment = GraphicsEnvironment.getLocalGraphicsEnvironment();
        if(environment != null)
            device = environment.getDefaultScreenDevice();
        if(device != null)
            mode = device.getDisplayMode();
        if(mode != null) {
            screenW = mode.getWidth();
            screenH = mode.getHeight();
        }
        if(poppedUp)
            return;
        this.setLocation((screenW - wanted.width) / 2, (screenH - wanted.height) / 2);
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
        this.dispose();
        poppedUp = false;
    }
}
