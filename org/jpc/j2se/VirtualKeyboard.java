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

package org.jpc.j2se;

import java.io.*;
import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.awt.event.*;
import java.awt.GridLayout;
import java.awt.GridBagLayout;
import java.awt.FlowLayout;

public class VirtualKeyboard implements ActionListener
{
    private JFrame window;
    private JPanel panel;
    private HashMap commandToKey;
    private HashMap commandToButton;
    private org.jpc.emulator.peripheral.Keyboard keyboard;

    public void addKey(String name, int scanCode)
    {
        JToggleButton button = new JToggleButton(name, false);
        commandToKey.put(name, new Integer(scanCode));
        commandToButton.put(name, button);
        panel.add(button);
        button.setActionCommand(name);
        button.addActionListener(this);
    }

    public VirtualKeyboard(org.jpc.emulator.peripheral.Keyboard keys)
    {
            keyboard = keys;
            commandToKey = new HashMap();
            commandToButton = new HashMap();
            window = new JFrame("Virtual Keyboard");
            panel = new JPanel(new GridLayout(15,10));
            window.add(panel);
            addKey("Esc", 1);
            addKey("1", 2);
            addKey("2", 3);
            addKey("3", 4);
            addKey("4", 5);
            addKey("5", 6);
            addKey("6", 7);
            addKey("7", 8);
            addKey("8", 9);
            addKey("9", 10);
            addKey("0", 11);
            addKey("-", 12);
            addKey("=", 13);
            addKey("Backspace", 14);
            addKey("Tab", 15);
            addKey("Q", 16);
            addKey("W", 17);
            addKey("E", 18);
            addKey("R", 19);
            addKey("T", 20);
            addKey("Y", 21);
            addKey("U", 22);
            addKey("I", 23);
            addKey("O", 24);
            addKey("P", 25);
            addKey("[", 26);
            addKey("]", 27);
            addKey("Enter", 28);
            addKey("Left Ctrl", 29);
            addKey("A", 30);
            addKey("S", 31);
            addKey("D", 32);
            addKey("F", 33);
            addKey("G", 34);
            addKey("H", 35);
            addKey("J", 36);
            addKey("K", 37);
            addKey("L", 38);
            addKey(";", 39);
            addKey("'", 40);
            addKey("`", 41);
            addKey("Left Shift", 42);
            addKey("\\", 43);
            addKey("Z", 44);
            addKey("X", 45);
            addKey("C", 46);
            addKey("V", 47);
            addKey("B", 48);
            addKey("N", 49);
            addKey("M", 50);
            addKey(",", 51);
            addKey(".", 52);
            addKey("/", 53);
            addKey("Right Shift", 54);
            addKey("Keypad *", 55);
            addKey("Left Alt", 56);
            addKey("Space", 57);
            addKey("Caps Lock", 58);
            addKey("F1", 59);
            addKey("F2", 60);
            addKey("F3", 61);
            addKey("F4", 62);
            addKey("F5", 63);
            addKey("F6", 64);
            addKey("F7", 65);
            addKey("F8", 66);
            addKey("F9", 67);
            addKey("F10", 68);
            addKey("Num Lock", 69);
            addKey("Scroll Lock", 70);
            addKey("Keypad 7", 71);
            addKey("Keypad 8", 72);
            addKey("Keypad 9", 73);
            addKey("Keypad -", 74);
            addKey("Keypad 4", 75);
            addKey("Keypad 5", 76);
            addKey("Keypad 6", 77);
            addKey("Keypad +", 78);
            addKey("Keypad 1", 79);
            addKey("Keypad 2", 80);
            addKey("Keypad 3", 81);
            addKey("Keypad 0", 82);
            addKey("Keypad ,", 83);
            addKey("SysRq", 84);
            addKey("F11", 87);
            addKey("F12", 88);
            addKey("Keypad Enter", 128 + 28);
            addKey("Right Ctrl", 128 + 29);
            addKey("Keypad /", 128 + 53);
            addKey("PrintScreen", 128 + 55);
            addKey("Right Alt", 128 + 56);
            addKey("Break", 128 + 70);
            addKey("Home", 128 + 71);
            addKey("Up", 128 + 72);
            addKey("PgUp", 128 + 73);
            addKey("Left", 128 + 75);
            addKey("Right", 128 + 77);
            addKey("End", 128 + 79);
            addKey("Down", 128 + 80);
            addKey("PgDown", 128 + 81);
            addKey("Inser", 128 + 82);
            addKey("Delete", 128 + 83);
            addKey("Pause", 255);
            window.pack();
            window.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
            window.setVisible(true);
    }

    public void actionPerformed(ActionEvent evt)
    {
        String command = evt.getActionCommand();
        JToggleButton button = (JToggleButton)commandToButton.get(command);
        int scan = ((Integer)commandToKey.get(command)).intValue();
        if(button.isSelected()) {
            System.out.println("Keydown on key " + scan + ".");
            keyboard.keyPressed((byte)scan);
            if("Pause".equals(command))
                button.setSelected(false);
        } else {
            System.out.println("Keyup on key " + scan + ".");
            keyboard.keyReleased((byte)scan);
        }
    }
}