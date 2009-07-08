/*
    JPC: A x86 PC Hardware Emulator for a pure Java Virtual Machine
    Release Version 2.0

    A project from the Physics Dept, The University of Oxford

    Copyright (C) 2007-2009 Isis Innovation Limited

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

    www-jpc.physics.ox.ac.uk
*/


package org.jpc.j2se;

import java.util.*;
import java.awt.*;
import java.awt.event.*;
import java.util.logging.*;

import javax.swing.*;

/**
 *
 * @author Rhys Newman
 * @author Chris Dennis
 */
public class KeyHandlingPanel extends JPanel
{
    private static final Logger LOGGING = Logger.getLogger(KeyHandlingPanel.class.getName());

    public static final String MOUSE_CAPTURE = "Mouse Capture";

    private static Robot robot;
    private static Cursor emptyCursor;

    private int currentButtons;
    private double mouseSensitivity = 0.5;

    private boolean inputsLocked = false, mouseCaptureEnabled = true;
    private int lastMouseX, lastMouseY;

    static
    {
        try {
            ImageIcon emptyIcon = new ImageIcon(new byte[0]);
            emptyCursor = Toolkit.getDefaultToolkit().createCustomCursor(emptyIcon.getImage(), new Point(0, 0), "emptyCursor");
        } catch (AWTError e) {
            LOGGING.log(Level.WARNING, "Could not get AWT Toolkit, not even headless", e);
            emptyCursor = Cursor.getDefaultCursor();
        } catch (HeadlessException e) {
            LOGGING.log(Level.WARNING, "Headless environment could not create invisible cursor, using default.", e);
            emptyCursor = Cursor.getDefaultCursor();
        }
    }

    public KeyHandlingPanel()
    {
        super();
    }

    public KeyHandlingPanel(LayoutManager mgr)
    {
        super(mgr);
    }

    public void ensureParentsFocussable()
    {
    }

    public boolean mouseCaptured()
    {
        return false;
    }

    public void lockInputs()
    {
    }

    public void unlockInputs()
    {
    }

    public void setMouseSensitivity(double factor)
    {
    }
}
