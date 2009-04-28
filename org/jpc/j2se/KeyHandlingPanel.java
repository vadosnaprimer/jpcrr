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

public class KeyHandlingPanel extends JPanel implements KeyListener, FocusListener, MouseListener, MouseMotionListener, MouseWheelListener
{
    public static final String MOUSE_CAPTURE = "Mouse Capture";
    
    private int currentButtons;
    private double mouseSensitivity = 0.5;
    private HashSet keyPressedSet;

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
        keyPressedSet = new HashSet();

        addFocusListener(this);
        addKeyListener(this);
        addMouseListener(this);
        addMouseMotionListener(this);
        addMouseWheelListener(this);
        setFocusable(true);
        setRequestFocusEnabled(true);
        setFocusTraversalKeysEnabled(false);
        setFocusTraversalKeys(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS, Collections.EMPTY_SET);
        setFocusTraversalKeys(KeyboardFocusManager.BACKWARD_TRAVERSAL_KEYS, Collections.EMPTY_SET);
        setFocusTraversalKeys(KeyboardFocusManager.UP_CYCLE_TRAVERSAL_KEYS, Collections.EMPTY_SET);
    }

    protected void keyPressed(int keyCode)
    {
    }

    protected void keyReleased(int keyCode)
    {
    }

    protected void repeatedKeyPress(int keyCode)
    {
    }

    public void focusGained(FocusEvent e) {}

    public void focusLost(FocusEvent e) 
    {
        HashSet keysDown = null;

        synchronized (this)
        {
            keysDown = keyPressedSet;
            keyPressedSet = new HashSet();
        }

        Iterator itt = keysDown.iterator();
        while (itt.hasNext())
        {
            Integer code = (Integer) itt.next();
            keyReleased(code.intValue());
        }
    }

    public void keyPressed(KeyEvent e) 
    {
        boolean isRepeat = false;

        synchronized (this)
        {
            Integer code = new Integer(e.getKeyCode());
            isRepeat = keyPressedSet.contains(code);
            if (!isRepeat)
                keyPressedSet.add(code);
        }

        if (isRepeat)
            repeatedKeyPress(e.getKeyCode());
        else
            keyPressed(e.getKeyCode());

        e.consume();
    }

    public void keyReleased(KeyEvent e)
    {
        synchronized (this)
        {
            Integer code = new Integer(e.getKeyCode());
            keyPressedSet.remove(code);
        }

        keyReleased(e.getKeyCode());
        e.consume();
    }
    
    public void keyTyped(KeyEvent e)
    {
        e.consume();
    }

    public void ensureParentsFocussable()
    {
        for (Component comp = getParent(); comp != null; comp = comp.getParent())
            comp.setFocusable(true);
    }

    public void mouseClicked(MouseEvent e) 
    {
        if (e.getClickCount() == 2) 
        {
            if (e.getButton() == MouseEvent.BUTTON1)
                lockInputs();
            else if (e.getButton() == MouseEvent.BUTTON3)
                unlockInputs();
        }

        requestFocusInWindow();
    }

    public void mouseEntered(MouseEvent e) 
    {
        if (!inputsLocked || (robot != null))
            return;
        
        int tolerance = 20;
        int rate = 256;

        int x = e.getX();
        int y = e.getY();
        Dimension s = size();

        if (x < tolerance)
            mouseEventReceived(-rate, 0, 0, currentButtons);
        if (y < tolerance)
            mouseEventReceived(0, -rate, 0, currentButtons);
        if (x > s.width - tolerance)
            mouseEventReceived(rate, 0, 0, currentButtons);
        if (y > s.height - tolerance)
            mouseEventReceived(0, rate, 0, currentButtons);
    }

    public void mouseExited(MouseEvent e) 
    {
        if (!inputsLocked || (robot != null))
            return;
    }

    public void mousePressed(MouseEvent e)
    {
        if (!inputsLocked)
            return;

        switch(e.getButton()) 
        {
        case MouseEvent.BUTTON1: 
            currentButtons |= 1; 
            break;
        case MouseEvent.BUTTON3: 
            currentButtons |= 2; 
            break;
        case MouseEvent.BUTTON2: 
            currentButtons |= 4; 
            break;
        }

        mouseEventReceived(0, 0, 0, currentButtons);
    }

    public void mouseReleased(MouseEvent e)
    {
        if (!inputsLocked)
            return;

        switch(e.getButton()) 
        {
        case MouseEvent.BUTTON1: 
            currentButtons &= ~1; 
            break;
        case MouseEvent.BUTTON3: 
            currentButtons &= ~2; 
            break;
        case MouseEvent.BUTTON2: 
            currentButtons &= ~4; 
            break;
        }

        mouseEventReceived(0, 0, 0, currentButtons);
    }

    public void mouseDragged(MouseEvent e) 
    {
        movedMouse(e);
    }

    public void mouseMoved(MouseEvent e)
    {
        movedMouse(e);
    }

    public void mouseWheelMoved(MouseWheelEvent e)
    {
        if (!inputsLocked)
            return;

        mouseEventReceived(0, 0, e.getWheelRotation(), currentButtons);
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

    private void movedMouse(MouseEvent e)
    {
        if (!inputsLocked)
            return;
        
        int mx = 0, my = 0;

        if (robot == null)
        {
            mx = e.getX() - lastMouseX;
            my = e.getY() - lastMouseY;
            lastMouseX = e.getX();
            lastMouseY = e.getY();
        }
        else
        {
            Point origin = getLocationOnScreen();
            int win_x = origin.x;
            int win_y = origin.y;
            int win_w2 = getWidth() / 2;
            int win_h2 = getHeight() / 2;

            mx = e.getX() - win_w2;
            my = e.getY() - win_h2;
            
            if ((mx == 0) && (my == 0)) 
                return;
            robot.mouseMove(win_x + win_w2, win_y + win_h2);
        }

        if (mx > 0)
            mx = Math.max((int)(mx * mouseSensitivity), 1);
        else if (mx < 0)
            mx = Math.min((int)(mx * mouseSensitivity), -1);

        if (my > 0)
            my = Math.max((int)(my * mouseSensitivity), 1);
        else if (my < 0)
            my = Math.min((int)(my * mouseSensitivity), -1);
        
        mouseEventReceived(mx, my, 0, currentButtons);
    }

    protected void mouseEventReceived(int dx, int dy, int dz, int buttons) {}
}
 
