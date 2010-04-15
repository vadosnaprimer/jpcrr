/*
    JPC-RR: A x86 PC Hardware Emulator
    Release 1

    Copyright (C) 2007-2009 Isis Innovation Limited
    Copyright (C) 2009-2010 H. Ilari Liusvaara

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

package org.jpc.plugins;

import org.jpc.emulator.peripheral.Keyboard;
import org.jpc.emulator.KeyboardStatusListener;
import org.jpc.pluginsbase.Plugins;
import org.jpc.pluginsbase.Plugin;
import org.jpc.pluginsaux.ConstantTableLayout;
import static org.jpc.Misc.errorDialog;
import static org.jpc.Misc.moveWindow;

import javax.swing.*;
import java.util.*;
import java.awt.event.*;
import java.awt.*;

public class VirtualKeyboard implements ActionListener, Plugin, KeyboardStatusListener
{
    private JFrame window;
    private JPanel panel;
    private HashMap<String, Integer> commandToKey;
    private HashMap<String, JToggleButton> commandToButton;
    private JToggleButton capsLock;
    private JToggleButton numLock;
    private JToggleButton scrollLock;
    private org.jpc.emulator.peripheral.Keyboard keyboard;
    private int keyNo;
    private boolean[] cachedState;
    private Plugins pluginManager;
    private int nativeWidth, nativeHeight;

    public void addKey(String name, int scanCode, int x, int y, int w, int h)
    {
        String cmdName = name + "-" + (keyNo++);
        JToggleButton button = new JToggleButton(name, false);
        commandToKey.put(cmdName, new Integer(scanCode));
        commandToButton.put(cmdName, button);
        ConstantTableLayout.Placement c = new ConstantTableLayout.Placement(x, y, w, h);
        panel.add(button, c);
        button.setActionCommand(cmdName);
        button.addActionListener(this);
    }

    public JToggleButton addSpecial(String name, String text, int x, int y, int w, int h)
    {
        ConstantTableLayout.Placement c = new ConstantTableLayout.Placement(x, y, w, h);
        JToggleButton button = new JToggleButton(text, false);
        panel.add(button, c);
        button.setEnabled(false);
        button.setVisible(false);
        return button;
    }

    public void eci_virtualkeyboard_setwinpos(Integer x, Integer y)
    {
        moveWindow(window, x.intValue(), y.intValue(), nativeWidth, nativeHeight);
    }


    public VirtualKeyboard(Plugins _pluginManager)
    {
            pluginManager = _pluginManager;
            keyNo = 0;
            keyboard = null;
            commandToKey = new HashMap<String, Integer>();
            commandToButton = new HashMap<String, JToggleButton>();
            window = new JFrame("Virtual Keyboard");
            ConstantTableLayout layout = new ConstantTableLayout();
            cachedState = new boolean[256];
            panel = new JPanel(layout);
            window.add(panel);
            addKey("Esc", 1, 0, 0, 3, 2);   //Hack: W should be 2, but we use 3 to make keyboard narrower.
            addKey("F1", 59, 4, 0, 2, 2);
            addKey("F2", 60, 6, 0, 2, 2);
            addKey("F3", 61, 8, 0, 2, 2);
            addKey("F4", 62, 10, 0, 2, 2);
            addKey("F5", 63, 13, 0, 2, 2);
            addKey("F6", 64, 15, 0, 2, 2);
            addKey("F7", 65, 17, 0, 2, 2);
            addKey("F8", 66, 19, 0, 2, 2);
            addKey("F9", 67, 22, 0, 2, 2);
            addKey("FA", 68, 24, 0, 2, 2);
            addKey("FB", 87, 26, 0, 2, 2);
            addKey("FC", 88, 28, 0, 2, 2);
            addKey("PS", 128 + 55, 31, 0, 2, 2);
            addKey("SL", 70, 33, 0, 2, 2);
            addKey("PA", 255, 35, 0, 2, 2);

            numLock = addSpecial("NumLock", "N", 38, 0, 2, 2);
            capsLock = addSpecial("CapsLock", "C", 40, 0, 2, 2);
            scrollLock = addSpecial("ScrollLock", "S", 42, 0, 2, 2);

            addKey("1", 2, 2, 4, 2, 2);
            addKey("2", 3, 4, 4, 2, 2);
            addKey("3", 4, 6, 4, 2, 2);
            addKey("4", 5, 8, 4, 2, 2);
            addKey("5", 6, 10, 4, 2, 2);
            addKey("6", 7, 12, 4, 2, 2);
            addKey("7", 8, 14, 4, 2, 2);
            addKey("8", 9, 16, 4, 2, 2);
            addKey("9", 10, 18, 4, 2, 2);
            addKey("0", 11, 20, 4, 2, 2);
            addKey("-", 12, 22, 4, 2, 2);
            addKey("=", 13, 24, 4, 2, 2);
            addKey("BS", 14, 26, 4, 4, 2);
            addKey("I", 128 + 82, 31, 4, 2, 2);
            addKey("H", 128 + 71, 33, 4, 2, 2);
            addKey("PU", 128 + 73, 35, 4, 2, 2);
            addKey("NL", 69, 38, 4, 2, 2);
            addKey("/", 128 + 53, 40, 4, 2, 2);
            addKey("*", 55, 42, 4, 2, 2);
            addKey("-", 74, 44, 4, 2, 2);

            addKey("Tab", 15, 0, 6, 3, 2);
            addKey("Q", 16, 3, 6, 2, 2);
            addKey("W", 17, 5, 6, 2, 2);
            addKey("E", 18, 7, 6, 2, 2);
            addKey("R", 19, 9, 6, 2, 2);
            addKey("T", 20, 11, 6, 2, 2);
            addKey("Y", 21, 13, 6, 2, 2);
            addKey("U", 22, 15, 6, 2, 2);
            addKey("I", 23, 17, 6, 2, 2);
            addKey("O", 24, 19, 6, 2, 2);
            addKey("P", 25, 21, 6, 2, 2);
            addKey("[", 26, 23, 6, 2, 2);
            addKey("]", 27, 25, 6, 2, 2);
            addKey("EN", 28, 28, 6, 2, 4);
            addKey("D", 128 + 83, 31, 6, 2, 2);
            addKey("E", 128 + 79, 33, 6, 2, 2);
            addKey("PD", 128 + 81, 35, 6, 2, 2);
            addKey("7", 71, 38, 6, 2, 2);
            addKey("8", 72, 40, 6, 2, 2);
            addKey("9", 73, 42, 6, 2, 2);
            addKey("+", 78, 44, 6, 2, 4);

            addKey("CL", 58, 0, 8, 4, 2);
            addKey("A", 30, 4, 8, 2, 2);
            addKey("S", 31, 6, 8, 2, 2);
            addKey("D", 32, 8, 8, 2, 2);
            addKey("F", 33, 10, 8, 2, 2);
            addKey("G", 34, 12, 8, 2, 2);
            addKey("H", 35, 14, 8, 2, 2);
            addKey("J", 36, 16, 8, 2, 2);
            addKey("K", 37, 18, 8, 2, 2);
            addKey("L", 38, 20, 8, 2, 2);
            addKey(";", 39, 22, 8, 2, 2);
            addKey("'", 40, 24, 8, 2, 2);
            addKey("`", 41, 26, 8, 2, 2);
            addKey("4", 75, 38, 8, 2, 2);
            addKey("5", 76, 40, 8, 2, 2);
            addKey("6", 77, 42, 8, 2, 2);

            addKey("SH", 42, 0, 10, 3, 2);
            addKey("\\", 43, 3, 10, 2, 2);
            addKey("Z", 44, 5, 10, 2, 2);
            addKey("X", 45, 7, 10, 2, 2);
            addKey("C", 46, 9, 10, 2, 2);
            addKey("V", 47, 11, 10, 2, 2);
            addKey("B", 48, 13, 10, 2, 2);
            addKey("N", 49, 15, 10, 2, 2);
            addKey("M", 50, 17, 10, 2, 2);
            addKey(",", 51, 19, 10, 2, 2);
            addKey(".", 52, 21, 10, 2, 2);
            addKey("/", 53, 23, 10, 2, 2);
            addKey("SH", 54, 25, 10, 5, 2);
            addKey("^", 128 + 72, 33, 10, 2, 2);
            addKey("1", 79, 38, 10, 2, 2);
            addKey("2", 80, 40, 10, 2, 2);
            addKey("3", 81, 42, 10, 2, 2);
            addKey("EN", 128 + 28, 44, 10, 2, 4);

            addKey("CT", 29, 0, 12, 3, 2);
            addKey("AL", 56, 5, 12, 3, 2);
            addKey("SP", 57, 8, 12, 14, 2);
            addKey("AL", 128 + 56, 22, 12, 3, 2);
            addKey("CT", 128 + 29, 27, 12, 3, 2);
            addKey("<", 128 + 75, 31, 12, 2, 2);
            addKey("v", 128 + 80, 33, 12, 2, 2 );
            addKey(">", 128 + 77, 35, 12, 2, 2 );
            addKey("0", 82, 38, 12, 4, 2);
            addKey(".", 83, 42, 12, 2, 2);

            window.pack();
            window.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
            Dimension d = window.getSize();
            nativeWidth = d.width;
            nativeHeight = d.height;
            window.setVisible(true);
    }

    //-1 if unknonw, bit 2 is capslock, bit 1 is numlock, bit 0 is scrollock.
    private void updateLEDs(int status)
    {
        if(status < 0) {
            numLock.setVisible(false);
            numLock.setSelected(false);
            capsLock.setVisible(false);
            capsLock.setSelected(false);
            scrollLock.setVisible(false);
            scrollLock.setSelected(false);
        } else {
            numLock.setVisible(true);
            capsLock.setVisible(true);
            scrollLock.setVisible(true);
            numLock.setSelected((status & 2) != 0);
            capsLock.setSelected((status & 4) != 0);
            scrollLock.setSelected((status & 1) != 0);
        }
    }

    public void resetButtons()
    {
        for(Map.Entry<String, Integer> entry : commandToKey.entrySet()) {
            int scan = entry.getValue().intValue();
            JToggleButton button = commandToButton.get(entry.getKey());
            if(keyboard.getKeyStatus((byte)scan) != cachedState[scan]) {
                cachedState[scan] = keyboard.getKeyStatus((byte)scan);
                button.setSelected(cachedState[scan]);
            }
        }
        updateLEDs(keyboard.getLEDStatus());
    }

    private void keyStatusChangeEventThread(int scancode, boolean pressed)
    {
        for(Map.Entry<String, Integer> entry : commandToKey.entrySet()) {
            int scan = entry.getValue().intValue();
            if(scan != scancode)
                continue;
            JToggleButton button = commandToButton.get(entry.getKey());
            if(pressed != cachedState[scan]) {
                cachedState[scan] = pressed;
                button.setSelected(pressed);
            }
        }
    }

    public void keyExecStatusChange(int scancode, boolean pressed)
    {
        //These aren't currently shown.
    }

    public void keyStatusChange(int scancode, boolean pressed)
    {
        if(!SwingUtilities.isEventDispatchThread())
            try {
                final int _scancode = scancode;
                final boolean _pressed = pressed;
                SwingUtilities.invokeLater(new Thread() { public void run() { VirtualKeyboard.this.keyStatusChangeEventThread(_scancode, _pressed); }});
            } catch(Exception e) {
            }
        else
            keyStatusChangeEventThread(scancode, pressed);
    }

    public void keyStatusReload()
    {
        if(!SwingUtilities.isEventDispatchThread())
            try {
                SwingUtilities.invokeLater(new Thread() { public void run() { VirtualKeyboard.this.resetButtons(); }});
            } catch(Exception e) {
            }
        else
            resetButtons();
    }

    public void ledStatusChange(int newstatus)
    {
        if(!SwingUtilities.isEventDispatchThread())
            try {
                final int _newstatus = newstatus;
                SwingUtilities.invokeLater(new Thread() { public void run() { VirtualKeyboard.this.updateLEDs(_newstatus); }});
            } catch(Exception e) {
            }
        else
            updateLEDs(newstatus);
    }

    public void main()
    {
        //This runs entierely in UI thread.
    }

    public boolean systemShutdown()
    {
        //OK to proceed with JVM shutdown.
        return true;
    }

    public void pcStarting()
    {
        //Not interested.
    }

    public void pcStopping()
    {
        if(pluginManager.isShuttingDown())
            return;  //Too much of deadlock risk.

        if(!SwingUtilities.isEventDispatchThread())
            try {
                SwingUtilities.invokeAndWait(new Thread() { public void run() { VirtualKeyboard.this.resetButtons(); }});
            } catch(Exception e) {
            }
        else
            resetButtons();
    }

    public void reconnect(org.jpc.emulator.PC pc)
    {
        if(keyboard != null)
            keyboard.removeStatusListener(this);
        if(pc != null) {
            Keyboard keys = (Keyboard)pc.getComponent(Keyboard.class);
            keyboard = keys;
            keyboard.addStatusListener(this);
            keyStatusReload();
        } else {
            keyboard = null;
            Iterator<Map.Entry<String, Integer> > itt = commandToKey.entrySet().iterator();
            while (itt.hasNext())
            {
                Map.Entry<String, Integer> entry = itt.next();
                String n = entry.getKey();
                Integer s = entry.getValue();
                cachedState[s.intValue()] = false;
                commandToButton.get(n).setSelected(false);
                ledStatusChange(-1);
            }
        }
    }

    public void actionPerformed(ActionEvent evt)
    {
        if(keyboard == null)
            return;

        String command = evt.getActionCommand();
        JToggleButton button = commandToButton.get(command);
        int scan = commandToKey.get(command).intValue();
        if(button.isSelected())
            System.err.println("Informational: Keydown on key " + scan + ".");
        else
            System.err.println("Informational: Keyup on key " + scan + ".");
        try {
            keyboard.sendEdge(scan);
            if(scan != 255 && (evt.getModifiers() & ActionEvent.SHIFT_MASK) != 0)
                keyboard.sendEdge(scan);
        } catch(Exception e) {
            System.err.println("Error: Sending command failed: " + e);
            errorDialog(e, "Failed to send keyboard key edge", null, "Dismiss");
        }
        if(scan != 255 && (evt.getModifiers() & ActionEvent.SHIFT_MASK) == 0)
            cachedState[scan] = !cachedState[scan];
        button.setSelected(cachedState[scan]);
    }
}
