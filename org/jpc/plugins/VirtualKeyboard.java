/*
    JPC-RR: A x86 PC Hardware Emulator
    Release 1

    Copyright (C) 2007-2009 Isis Innovation Limited
    Copyright (C) 2009-2010 H. Ilari Liusvaara
    Copyright (C) 2010 Foone Debonte

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

import org.jpc.Misc;
import org.jpc.emulator.peripheral.Keyboard;
import org.jpc.emulator.KeyboardStatusListener;
import org.jpc.jrsr.UTFInputLineStream;
import org.jpc.pluginsbase.Plugins;
import org.jpc.pluginsbase.Plugin;
import org.jpc.pluginsaux.ConstantTableLayout;
import static org.jpc.Misc.errorDialog;
import static org.jpc.Misc.moveWindow;
import static org.jpc.Misc.parseStringToComponents;

import java.io.*;
import javax.swing.*;
import javax.swing.border.*;
import java.util.*;
import java.awt.event.*;
import java.awt.*;
import javax.swing.plaf.basic.*;
import javax.swing.plaf.*;

public class VirtualKeyboard implements ActionListener, Plugin, KeyboardStatusListener
{
    private JFrame window;
    private JPanel panel;
    private HashMap<String, Integer> commandToKey;
    private HashMap<String, JToggleButton> commandToButton;
    private JToggleButton capsLock;
    private JToggleButton numLock;
    private JToggleButton scrollLock;
    private Font keyFont;
    private Font smallKeyFont;
    private Border keyBorder, smallKeyBorder, classicBorder;
    private boolean nativeButtons;

    private org.jpc.emulator.peripheral.Keyboard keyboard;
    private int keyNo;
    private boolean[] cachedState;
    private Plugins pluginManager;
    private int nativeWidth, nativeHeight;

    private static String[][] DEFAULT_KEYBOARD_DATA = new String[][] {
        new String[]{"001", "0", "0", "3", "2", "C", "Esc"},
        new String[]{"059", "4", "-", "2", "2", "C", "F1"},
        new String[]{"060", "-", "-", "2", "2", "C", "F2"},
        new String[]{"061", "-", "-", "2", "2", "C", "F3"},
        new String[]{"062", "-", "-", "2", "2", "C", "F4"},
        new String[]{"063", "13", "-", "2", "2", "C", "F5"},
        new String[]{"064", "-", "-", "2", "2", "C", "F6"},
        new String[]{"065", "-", "-", "2", "2", "C", "F7"},
        new String[]{"066", "-", "-", "2", "2", "C", "F8"},
        new String[]{"067", "22", "-", "2", "2", "C", "F9"},
        new String[]{"068", "-", "-", "2", "2", "C", "FA"},
        new String[]{"087", "-", "-", "2", "2", "C", "FB"},
        new String[]{"088", "-", "-", "2", "2", "C", "FC"},
        new String[]{"183", "31", "-", "2", "2", "C", "PS"},
        new String[]{"070", "-", "-", "2", "2", "C", "SL"},
        new String[]{"255", "-", "-", "2", "2", "C", "PA"},
        new String[]{"002", "2", "4", "2", "2", "C", "1"},
        new String[]{"003", "-", "-", "2", "2", "C", "2"},
        new String[]{"004", "-", "-", "2", "2", "C", "3"},
        new String[]{"005", "-", "-", "2", "2", "C", "4"},
        new String[]{"006", "-", "-", "2", "2", "C", "5"},
        new String[]{"007", "-", "-", "2", "2", "C", "6"},
        new String[]{"008", "-", "-", "2", "2", "C", "7"},
        new String[]{"009", "-", "-", "2", "2", "C", "8"},
        new String[]{"010", "-", "-", "2", "2", "C", "9"},
        new String[]{"011", "-", "-", "2", "2", "C", "0"},
        new String[]{"012", "-", "-", "2", "2", "C", "-"},
        new String[]{"013", "-", "-", "2", "2", "C", "="},
        new String[]{"014", "-", "-", "4", "2", "C", "BS"},
        new String[]{"210", "31", "-", "2", "2", "C", "I"},
        new String[]{"199", "-", "-", "2", "2", "C", "H"},
        new String[]{"201", "-", "-", "2", "2", "C", "PU"},
        new String[]{"069", "38", "-", "2", "2", "C", "NL"},
        new String[]{"181", "-", "-", "2", "2", "C", "/"},
        new String[]{"055", "-", "-", "2", "2", "C", "*"},
        new String[]{"074", "-", "-", "2", "2", "C", "-"},
        new String[]{"NumLock", "38", "0", "2", "2", "C", "N"},
        new String[]{"CapsLock", "-", "-", "2", "2", "C", "C"},
        new String[]{"ScrollLock", "-", "-", "2", "2", "C", "S"},
        new String[]{"015", "0", "6", "3", "2", "C", "Tab"},
        new String[]{"016", "-", "-", "2", "2", "C", "Q"},
        new String[]{"017", "-", "-", "2", "2", "C", "W"},
        new String[]{"018", "-", "-", "2", "2", "C", "E"},
        new String[]{"019", "-", "-", "2", "2", "C", "R"},
        new String[]{"020", "-", "-", "2", "2", "C", "T"},
        new String[]{"021", "-", "-", "2", "2", "C", "Y"},
        new String[]{"022", "-", "-", "2", "2", "C", "U"},
        new String[]{"023", "-", "-", "2", "2", "C", "I"},
        new String[]{"024", "-", "-", "2", "2", "C", "O"},
        new String[]{"025", "-", "-", "2", "2", "C", "P"},
        new String[]{"026", "-", "-", "2", "2", "C", "["},
        new String[]{"027", "-", "-", "2", "2", "C", "]"},
        new String[]{"028", "28", "-", "2", "4", "C", "EN"},
        new String[]{"211", "31", "-", "2", "2", "C", "D"},
        new String[]{"207", "-", "-", "2", "2", "C", "E"},
        new String[]{"209", "-", "-", "2", "2", "C", "PD"},
        new String[]{"071", "38", "-", "2", "2", "C", "7"},
        new String[]{"072", "-", "-", "2", "2", "C", "8"},
        new String[]{"073", "-", "-", "2", "2", "C", "9"},
        new String[]{"078", "-", "-", "2", "4", "C", "+"},
        new String[]{"058", "0", "8", "4", "2", "C", "CL"},
        new String[]{"030", "-", "-", "2", "2", "C", "A"},
        new String[]{"031", "-", "-", "2", "2", "C", "S"},
        new String[]{"032", "-", "-", "2", "2", "C", "D"},
        new String[]{"033", "-", "-", "2", "2", "C", "F"},
        new String[]{"034", "-", "-", "2", "2", "C", "G"},
        new String[]{"035", "-", "-", "2", "2", "C", "H"},
        new String[]{"036", "-", "-", "2", "2", "C", "J"},
        new String[]{"037", "-", "-", "2", "2", "C", "K"},
        new String[]{"038", "-", "-", "2", "2", "C", "L"},
        new String[]{"039", "-", "-", "2", "2", "C", ";"},
        new String[]{"040", "-", "-", "2", "2", "C", "'"},
        new String[]{"041", "-", "-", "2", "2", "C", "`"},
        new String[]{"075", "38", "-", "2", "2", "C", "4"},
        new String[]{"076", "-", "-", "2", "2", "C", "5"},
        new String[]{"077", "-", "-", "2", "2", "C", "6"},
        new String[]{"042", "0", "10", "3", "2", "C", "SH"},
        new String[]{"043", "-", "-", "2", "2", "C", "\\"},
        new String[]{"044", "-", "-", "2", "2", "C", "Z"},
        new String[]{"045", "-", "-", "2", "2", "C", "X"},
        new String[]{"046", "-", "-", "2", "2", "C", "C"},
        new String[]{"047", "-", "-", "2", "2", "C", "V"},
        new String[]{"048", "-", "-", "2", "2", "C", "B"},
        new String[]{"049", "-", "-", "2", "2", "C", "N"},
        new String[]{"050", "-", "-", "2", "2", "C", "M"},
        new String[]{"051", "-", "-", "2", "2", "C", ","},
        new String[]{"052", "-", "-", "2", "2", "C", "."},
        new String[]{"053", "-", "-", "2", "2", "C", "/"},
        new String[]{"054", "-", "-", "5", "2", "C", "SH"},
        new String[]{"200", "33", "-", "2", "2", "C", "^"},
        new String[]{"079", "38", "-", "2", "2", "C", "1"},
        new String[]{"080", "-", "-", "2", "2", "C", "2"},
        new String[]{"081", "-", "-", "2", "2", "C", "3"},
        new String[]{"156", "-", "-", "2", "4", "C", "EN"},
        new String[]{"029", "0", "12", "3", "2", "C", "CT"},
        new String[]{"056", "5", "-", "3", "2", "C", "AL"},
        new String[]{"057", "-", "-", "14", "2", "C", "SP"},
        new String[]{"184", "-", "-", "3", "2", "C", "AL"},
        new String[]{"157", "27", "-", "3", "2", "C", "CT"},
        new String[]{"203", "31", "-", "2", "2", "C", "<"},
        new String[]{"208", "-", "-", "2", "2", "C", "v"},
        new String[]{"205", "-", "-", "2", "2", "C", ">"},
        new String[]{"082", "38", "-", "4", "2", "C", "0"},
        new String[]{"083", "-", "-", "2", "2", "C", "."},
    };

    public JToggleButton addKey(String name, String topKey, int scanCode, int x, int y, int w, int h, char sizeCode,
        boolean special)
    {
        String cmdName = name + "-" + (keyNo++);
        String label = name;
        if(topKey != null) {
            label = "<html>" + topKey + "<br>" + name + "</html>";
        } else if(label.indexOf('&') >= 0) {
            label = "<html>" + name + "</html>";
        }
        JToggleButton button = new JToggleButton(label, false);
        if(sizeCode == 'N') {
            button.setFont(keyFont);
            button.setBorder(keyBorder);
        } else if(sizeCode == 'S') {
            button.setFont(smallKeyFont);
            button.setBorder(smallKeyBorder);
        } else if(sizeCode == 'C' && !nativeButtons) {
            button.setBorder(classicBorder);
        }

        button.setRolloverEnabled(false);
        if(special) {
            button.setEnabled(false);
            button.setVisible(false);
        } else {
            commandToKey.put(cmdName, new Integer(scanCode));
            commandToButton.put(cmdName, button);
            button.setActionCommand(cmdName);
            button.addActionListener(this);
        }

        if(!nativeButtons) button.setUI(new KeyboardButtonUI());

        panel.add(button, new ConstantTableLayout.Placement(x, y, w, h));
        return button;
    }

    public void eci_virtualkeyboard_setwinpos(Integer x, Integer y)
    {
        moveWindow(window, x.intValue(), y.intValue(), nativeWidth, nativeHeight);
    }

    public VirtualKeyboard(Plugins _pluginManager) throws IOException
    {
        this(_pluginManager, "");
    }

    public VirtualKeyboard(Plugins _pluginManager, String args) throws IOException
    {
        pluginManager = _pluginManager;
        Map<String, String> params = parseStringToComponents(args);
        String keyboardPath = params.get("keyboard");

        nativeButtons = "native".equalsIgnoreCase(params.get("style")) || keyboardPath == null;

        keyNo = 0;
        keyboard = null;
        commandToKey = new HashMap<String, Integer>();
        commandToButton = new HashMap<String, JToggleButton>();
        window = new JFrame("Virtual Keyboard");
        ConstantTableLayout layout = new ConstantTableLayout();
        cachedState = new boolean[256];
        panel = new JPanel(layout);
        keyFont = new Font("SanSerif", Font.PLAIN, 11);
        smallKeyFont = keyFont.deriveFont(9.0f);

        if(nativeButtons) {
            keyBorder = new EmptyBorder(0, 5, 0, 5);
            smallKeyBorder = new EmptyBorder(0, 1, 0, 1);
            // classicBorder isn't used with native buttons
        } else {
            Border outerBorder = new CompoundBorder(new EmptyBorder(1, 1, 0, 0), new SimpleButtonBorder(false));
            keyBorder = new CompoundBorder(outerBorder, new EmptyBorder(0, 3, 0, 3));
            smallKeyBorder = new CompoundBorder(outerBorder, new EmptyBorder(0, 1, 0, 1));
            classicBorder = new SimpleButtonBorder(true);
        }

        window.add(panel);

        parseKeyboardFile(keyboardPath);

        window.pack();
        window.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        Dimension d = window.getSize();
        nativeWidth = d.width;
        nativeHeight = d.height;
        window.setVisible(true);
    }

    private static int parseCoord(String value, int next)
    {
        if("-".equals(value))
            return next;
        else
            return Integer.valueOf(value);
    }

    private void handleKeyboardData(java.util.List<String[]> array)  throws IOException
    {
        int nextX = 0, nextY = 0;
        for(String[] line : array) {
            if(line.length == 7 || line.length == 8) {
                int x = parseCoord(line[1], nextX), y = parseCoord(line[2], nextY);
                int w = Integer.parseInt(line[3]), h = Integer.parseInt(line[4]);
                char sizeCode = line[5].charAt(0);
                String name = line[6], shifted = null;
                if(line.length == 8)
                    shifted = line[7];

                try {
                    int scanCode = Integer.parseInt(line[0]);
                    addKey(name, shifted, scanCode, x, y, w, h, sizeCode, false);
                } catch(NumberFormatException nfe) { // The scanCode wasn't a number, so this must be a special key
                    String scanName = line[0];
                    JToggleButton specialButton = addKey(name, null, 0, x, y, w, h, sizeCode, true);
                    if(scanName.equalsIgnoreCase("numlock"))
                        numLock=specialButton;
                    else if(scanName.equalsIgnoreCase("capslock"))
                        capsLock=specialButton;
                    else if(scanName.equalsIgnoreCase("scrolllock"))
                        scrollLock=specialButton;

                }
                nextX = x + w;
                nextY = y;
            } else
                throw new IOException("Invalid line in keyboard layout: " + line.toString());
        }
    }

    private void parseKeyboardFile(String filename) throws IOException
    {
        if(filename == null) {
            handleKeyboardData(Arrays.asList(DEFAULT_KEYBOARD_DATA));
            return;
        }

        UTFInputLineStream keyboardFile = new UTFInputLineStream(new FileInputStream(filename));
        String[] line;
        java.util.List<String[]> data = new ArrayList<String[]>();

        while((line = Misc.nextParseLine(keyboardFile)) != null)
            if(line.length <= 1)
                continue;
            else
                data.add(line);

        handleKeyboardData(data);
    }

    //-1 if unknown, bit 2 is capslock, bit 1 is numlock, bit 0 is scrollock.
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
            numLock.setVisible((status & 2) != 0);
            capsLock.setVisible((status & 4) != 0);
            scrollLock.setVisible((status & 1) != 0);
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
/*      THIS IS JUST PLAIN BROKEN.
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
*/
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
                SwingUtilities.invokeLater(new Thread() { public void run() {
                    VirtualKeyboard.this.keyStatusChangeEventThread(_scancode, _pressed); }});
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
                SwingUtilities.invokeLater(new Thread() { public void run() {
                    VirtualKeyboard.this.updateLEDs(_newstatus); }});
            } catch(Exception e) {
            }
        else
            updateLEDs(newstatus);
    }

    public void mouseButtonsChange(int newstatus)
    {
        //Not interesting.
    }

    public void mouseExecButtonsChange(int newstatus)
    {
        //Not interesting.
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
        boolean doubleEdge = (scan != 255) && ((evt.getModifiers() & ActionEvent.SHIFT_MASK) != 0);
        if(button.isSelected())
            if(doubleEdge)
                System.err.println("Informational: Keyhit on key " + scan + ".");
            else
                System.err.println("Informational: Keydown on key " + scan + ".");
        else
            if(doubleEdge)
                System.err.println("Informational: Keyupdown on key " + scan + ".");
            else
                System.err.println("Informational: Keyup on key " + scan + ".");
        try {
            keyboard.sendEdge(scan);
            if(doubleEdge)
                keyboard.sendEdge(scan);
        } catch(Exception e) {
            System.err.println("Error: Sending command failed: " + e);
            errorDialog(e, "Failed to send keyboard key edge", null, "Dismiss");
        }
        if(!doubleEdge)
            cachedState[scan] = !cachedState[scan];
        button.setSelected(cachedState[scan]);
    }

    protected static class KeyboardButtonUI extends BasicToggleButtonUI
    {
        protected static Color highlightColor = new Color(200, 200, 200);
        protected static Color backgroundColor = new Color(220, 220, 220);

        protected void simplePaint(Graphics g, JComponent c, Color color)
        {
            Rectangle viewRect = new Rectangle(c.getSize());
            Insets margin;
            try {
                /* We want to ignore the inner margins while calculating the background size, so we have to
                 * pull the outside border out of the compound border */
                CompoundBorder border = (CompoundBorder)c.getBorder();
                margin = border.getOutsideBorder().getBorderInsets(c);
            } catch(ClassCastException cce) {
                // We were called on a button without our elaborate triple-border, so default to the whole inset
                margin = c.getBorder().getBorderInsets(c);
            }

            g.setColor(color);

            g.fillRect(viewRect.x + margin.left, viewRect.y + margin.top,
                    viewRect.width - (margin.left + margin.right),
                    viewRect.height - (margin.top + margin.bottom));
        }

        protected void paintButtonPressed(Graphics g, AbstractButton b)
        {
            simplePaint(g, b, highlightColor);
        }

        public void paint(Graphics g, JComponent c)
        {
            simplePaint(g, c, backgroundColor);
            super.paint(g, c);
        }
    }

    protected static class SimpleButtonBorder extends LineBorder
    {
        private static final long serialVersionUID = 1L;

        protected static Color nwColor=new Color(240, 240, 240);
        protected static Color seColor=new Color(130, 130, 130);
        protected static Color pressedColor=new Color(160, 160, 160);
        protected boolean thin;
        public SimpleButtonBorder(boolean thin)
        {
            super(Color.BLACK, 1, true);
            this.thin = thin;
        }

        public void paintBorder(final Component c, final Graphics g, final int x, final int y, final int width,
            final int height)
        {
            Color oldColor = g.getColor();
            JToggleButton button = (JToggleButton)c;
            ButtonModel model = button.getModel();
            int adjust = thin ? 0 : 1;

            // Draw inner highlights
            if(model.isSelected() || model.isPressed()) {
                // Draw the north-west highlight, but in the south-east color
                g.setColor(seColor);
                g.drawRect(x + 1, y + 1, width - 2, 0);
                g.drawRect(x + 1, y + 1, 0, height - 2);
            } else {
                // Draw the north-west highlight
                g.setColor(nwColor);
                g.drawRect(x + 1, y + 1, width - 2, adjust);
                g.drawRect(x + 1, y + 1, adjust, height - 2);
                // Draw the south-east highlight
                g.setColor(seColor);
                g.drawRect(x + 1, y + height - 2, width - 2, 0);
                g.drawRect(x + width - 2, y + 1, 0, height - 2);
                if(!thin) { // Draw inner line of shadow
                    g.drawRect(x + 2, y + height - 3, width - 3, 0);
                    g.drawRect(x + width - 3, y + 2, 0, height - 3);
                }
            }

            // Draw actual border
            g.setColor(model.isPressed() ? pressedColor : lineColor);
            g.drawRoundRect(x, y, width - 1, height - 1, 2, 2);

            // Restore color state
            g.setColor(oldColor);
        }
    }
}
