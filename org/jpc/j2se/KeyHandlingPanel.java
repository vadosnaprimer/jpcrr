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


package org.jpc.j2se;

import java.util.*;
import java.awt.*;
import java.awt.event.*;

import javax.swing.*;
import javax.swing.event.*;

public class KeyHandlingPanel extends JPanel
{
    public static final String MOUSE_CAPTURE = "Mouse Capture";

    private int currentButtons;
    private double mouseSensitivity = 0.5;

    private boolean inputsLocked = false;
    private int lastMouseX, lastMouseY;

    private static Robot robot;
    private static Cursor emptyCursor;

    static
    {
        try
        {
            ImageIcon emptyIcon = new ImageIcon(new byte[0]);
            emptyCursor = Toolkit.getDefaultToolkit().createCustomCursor(emptyIcon.getImage(), new Point(0, 0), "emptyCursor");
        }
        catch (Throwable t) {}

        try
        {
            robot = new Robot();
            robot.setAutoDelay(5);
        }
        catch (Throwable t)
        {
            System.out.println("Warning: Mouse Capture will not function");
        }
    }

    public KeyHandlingPanel()
    {
        super();
        init();
    }

    public KeyHandlingPanel(LayoutManager mgr)
    {
        super(mgr);
        init();
    }

    public static boolean mouseCaptureEnabled()
    {
        return robot != null;
    }

    protected void init()
    {
    }

    public void ensureParentsFocussable()
    {
        for (Component comp = getParent(); comp != null; comp = comp.getParent())
            comp.setFocusable(true);
    }

    public boolean mouseCaptured()
    {
        return inputsLocked;
    }

    public void lockInputs()
    {
        if (emptyCursor != null)
            setCursor(emptyCursor);
        inputsLocked = true;
        firePropertyChange(MOUSE_CAPTURE, false, true);
    }

    public void unlockInputs()
    {
        if (emptyCursor != null)
            setCursor(Cursor.getDefaultCursor());
        inputsLocked = false;
        firePropertyChange(MOUSE_CAPTURE, true, false);
    }

    public void setMouseSensitivity(double factor)
    {
        mouseSensitivity = factor;
    }

}

