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

package org.jpc.luaextensions;

import mnj.lua.*;

import java.io.*;
import java.util.*;

import org.jpc.plugins.LuaPlugin;
import java.awt.GridBagConstraints;
import java.awt.Component;
import java.awt.Color;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.Insets;
import javax.swing.*;

//Locking this class is used for preventing termination and when terminating.
public class Window extends LuaPlugin.LuaResource implements ActionListener
{
    private JFrame window;
    private Map<String, JComponent> components;
    private LuaPlugin plug;

    Window(LuaPlugin plugin, String title) throws IOException
    {
        super(plugin);
        window = new JFrame(title);
        window.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        window.setLayout(new GridBagLayout());
        window.setVisible(false);
        components = new HashMap<String, JComponent>();
    }

    public void destroy() throws IOException
    {
        SwingUtilities.invokeLater(new Runnable() { public void run() {
            window.dispose();
            window = null;
            components = null;
        }});
    }

    private int doPush(Lua l, int x)
    {
        l.push(new Double(x));
        return 1;
    }

    public int luaCB_ABOVE_BASELINE(Lua l, LuaPlugin plugin) { return doPush(l, GridBagConstraints.ABOVE_BASELINE); }
    public int luaCB_ABOVE_BASELINE_LEADING(Lua l, LuaPlugin plugin) { return doPush(l, GridBagConstraints.ABOVE_BASELINE_LEADING); }
    public int luaCB_ABOVE_BASELINE_TRAILING(Lua l, LuaPlugin plugin) { return doPush(l, GridBagConstraints.ABOVE_BASELINE_TRAILING); }
    public int luaCB_BASELINE(Lua l, LuaPlugin plugin) { return doPush(l, GridBagConstraints.BASELINE); }
    public int luaCB_BASELINE_LEADING(Lua l, LuaPlugin plugin) { return doPush(l, GridBagConstraints.BASELINE_LEADING); }
    public int luaCB_BASELINE_TRAILING(Lua l, LuaPlugin plugin) { return doPush(l, GridBagConstraints.BASELINE_TRAILING); }
    public int luaCB_BELOW_BASELINE(Lua l, LuaPlugin plugin) { return doPush(l, GridBagConstraints.BELOW_BASELINE); }
    public int luaCB_BELOW_BASELINE_LEADING(Lua l, LuaPlugin plugin) { return doPush(l, GridBagConstraints.BELOW_BASELINE_LEADING); }
    public int luaCB_BELOW_BASELINE_TRAILING(Lua l, LuaPlugin plugin) { return doPush(l, GridBagConstraints.BELOW_BASELINE_TRAILING); }
    public int luaCB_BOTH(Lua l, LuaPlugin plugin) { return doPush(l, GridBagConstraints.BOTH); }
    public int luaCB_CENTER(Lua l, LuaPlugin plugin) { return doPush(l, GridBagConstraints.CENTER); }
    public int luaCB_EAST(Lua l, LuaPlugin plugin) { return doPush(l, GridBagConstraints.EAST); }
    public int luaCB_FIRST_LINE_END(Lua l, LuaPlugin plugin) { return doPush(l, GridBagConstraints.FIRST_LINE_END); }
    public int luaCB_FIRST_LINE_START(Lua l, LuaPlugin plugin) { return doPush(l, GridBagConstraints.FIRST_LINE_START); }
    public int luaCB_HORIZONTAL(Lua l, LuaPlugin plugin) { return doPush(l, GridBagConstraints.HORIZONTAL); }
    public int luaCB_LAST_LINE_END(Lua l, LuaPlugin plugin) { return doPush(l, GridBagConstraints.LAST_LINE_END); }
    public int luaCB_LAST_LINE_START(Lua l, LuaPlugin plugin) { return doPush(l, GridBagConstraints.LAST_LINE_START); }
    public int luaCB_LINE_END(Lua l, LuaPlugin plugin) { return doPush(l, GridBagConstraints.LINE_END); }
    public int luaCB_LINE_START(Lua l, LuaPlugin plugin) { return doPush(l, GridBagConstraints.LINE_START); }
    public int luaCB_NONE(Lua l, LuaPlugin plugin) { return doPush(l, GridBagConstraints.NONE); }
    public int luaCB_NORTH(Lua l, LuaPlugin plugin) { return doPush(l, GridBagConstraints.NORTH); }
    public int luaCB_NORTHEAST(Lua l, LuaPlugin plugin) { return doPush(l, GridBagConstraints.NORTHEAST); }
    public int luaCB_NORTHWEST(Lua l, LuaPlugin plugin) { return doPush(l, GridBagConstraints.NORTHWEST); }
    public int luaCB_PAGE_END(Lua l, LuaPlugin plugin) { return doPush(l, GridBagConstraints.PAGE_END); }
    public int luaCB_PAGE_START(Lua l, LuaPlugin plugin) { return doPush(l, GridBagConstraints.PAGE_START); }
    public int luaCB_RELATIVE(Lua l, LuaPlugin plugin) { return doPush(l, GridBagConstraints.RELATIVE); }
    public int luaCB_REMAINDER(Lua l, LuaPlugin plugin) { return doPush(l, GridBagConstraints.REMAINDER); }
    public int luaCB_SOUTH(Lua l, LuaPlugin plugin) { return doPush(l, GridBagConstraints.SOUTH); }
    public int luaCB_SOUTHEAST(Lua l, LuaPlugin plugin) { return doPush(l, GridBagConstraints.SOUTHEAST); }
    public int luaCB_SOUTHWEST(Lua l, LuaPlugin plugin) { return doPush(l, GridBagConstraints.SOUTHWEST); }
    public int luaCB_VERTICAL(Lua l, LuaPlugin plugin) { return doPush(l, GridBagConstraints.VERTICAL); }
    public int luaCB_WEST(Lua l, LuaPlugin plugin) { return doPush(l, GridBagConstraints.WEST); }

    private Integer extractIntParameter(Lua l, LuaTable table, String name)
    {
        Object o = l.rawGet(table, name);
        if(o == null || !(o instanceof Double))
            return null;
        Double n = (Double)o;
        return new Integer((int)n.doubleValue());
    }

    private Double extractDoubleParameter(Lua l, LuaTable table, String name)
    {
        Object o = l.rawGet(table, name);
        if(o == null || !(o instanceof Double))
            return null;
        return (Double)o;
    }

    private String extractStringParameter(Lua l, LuaTable table, String name)
    {
        Object o = l.rawGet(table, name);
        if(o == null || !(o instanceof String))
            return null;
        return (String)o;
    }

    private GridBagConstraints parseConstraints(Lua l, LuaTable table)
    {
        GridBagConstraints c = new GridBagConstraints();
        Integer i, il, ir, iu, id;
        Double d;

        i = extractIntParameter(l, table, "anchor"); if(i != null) c.anchor = i;
        i = extractIntParameter(l, table, "fill"); if(i != null) c.fill = i;
        i = extractIntParameter(l, table, "gridheight"); if(i != null) c.gridheight = i;
        i = extractIntParameter(l, table, "gridwidth"); if(i != null) c.gridwidth = i;
        i = extractIntParameter(l, table, "gridx"); if(i != null) c.gridx = i;
        i = extractIntParameter(l, table, "gridy"); if(i != null) c.gridy = i;
        i = extractIntParameter(l, table, "ipadx"); if(i != null) c.ipadx = i;
        i = extractIntParameter(l, table, "ipady"); if(i != null) c.ipady = i;
        d = extractDoubleParameter(l, table, "weightx"); if(d != null) c.weightx = d;
        d = extractDoubleParameter(l, table, "weighty"); if(d != null) c.weighty = d;
        il = extractIntParameter(l, table, "insets_left"); if(il == null) il = new Integer(0);
        ir = extractIntParameter(l, table, "insets_right"); if(ir == null) ir = new Integer(0);
        iu = extractIntParameter(l, table, "insets_up"); if(iu == null) iu = new Integer(0);
        id = extractIntParameter(l, table, "insets_down"); if(id == null) id = new Integer(0);
        c.insets = new Insets(iu, il, id, ir);
        return c;
    }

    public int luaCB_create_component(Lua l, LuaPlugin plugin)
    {
        l.pushNil();
        Object o = l.value(2);
        if(!(o instanceof LuaTable)) {
            l.pushBoolean(false);
            return 1;
        }
        LuaTable tab = (LuaTable)o;
        GridBagConstraints placement = parseConstraints(l, tab);
        String componentType = extractStringParameter(l, tab, "type");
        String componentText = extractStringParameter(l, tab, "text");
        String componentName = extractStringParameter(l, tab, "name");
        if(components.get(componentName) != null)
            components.remove(componentName);
        JComponent newComponent = null;
        if(componentType == "button") {
            JButton _newComponent = new JButton(componentText);
            _newComponent.setActionCommand(componentName);
            _newComponent.addActionListener(Window.this);
            newComponent = _newComponent;
        } else if(componentType == "label") {
            JLabel _newComponent = new JLabel(componentText);
            newComponent = _newComponent;
        } else if(componentType == "textfield") {
            Integer i = extractIntParameter(l, tab, "columns");
            JTextField _newComponent;
            if(i == null)
                _newComponent = new JTextField(componentText);
            else
                _newComponent = new JTextField(componentText, i);
            _newComponent.setActionCommand(componentName);
            _newComponent.addActionListener(Window.this);
            newComponent = _newComponent;
        } else if(componentType == "checkbox") {
            JCheckBox _newComponent = new JCheckBox(componentText);
            _newComponent.setActionCommand(componentName);
            _newComponent.addActionListener(Window.this);
            newComponent = _newComponent;
        }
        if(newComponent != null) {
            components.put(componentName, newComponent);
            l.pushBoolean(true);
        } else
            l.pushBoolean(false);
        final JComponent newComponent2 = newComponent;
        final GridBagConstraints placement2 = placement;
        SwingUtilities.invokeLater(new Runnable() { public void run() {
            window.add(newComponent2, placement2);
            window.pack();
        }});
        return 1;
    }

    public int luaCB_set_text(Lua l, LuaPlugin plugin)
    {
        l.pushNil();
        l.pushNil();
        String name = l.checkString(2);
        final String newText = l.checkString(3);
        final JComponent c = components.get(name);
        SwingUtilities.invokeLater(new Runnable() { public void run() {
            if(c != null) {
                if(c instanceof JButton)
                    ((JButton)c).setText(newText);
                else if(c instanceof JCheckBox)
                    ((JCheckBox)c).setText(newText);
                else if(c instanceof JLabel)
                    ((JLabel)c).setText(newText);
                else if(c instanceof JTextField)
                    ((JTextField)c).setText(newText);
            }
        }});
        return 0;
    }

    public int luaCB_set_color(Lua l, LuaPlugin plugin)
    {
        l.pushNil();
        l.pushNil();
        l.pushNil();
        l.pushNil();
        String name = l.checkString(2);
        final int r = (int)l.checkNumber(3);
        final int g = (int)l.checkNumber(4);
        final int b = (int)l.checkNumber(5);
        final JComponent c = components.get(name);
        SwingUtilities.invokeLater(new Runnable() { public void run() {
            if(c != null) {
                c.setForeground(new Color(r, g, b));
            }
        }});
        return 0;
    }

    public int luaCB_get_text(Lua l, LuaPlugin plugin)
    {
        l.pushNil();
        String name = l.checkString(2);
        JComponent c = components.get(name);
        if(c != null) {
            if(c instanceof JButton)
                l.push(((JButton)c).getText());
            else if(c instanceof JCheckBox)
                l.push(((JCheckBox)c).getText());
            else if(c instanceof JLabel)
                l.push(((JLabel)c).getText());
            else if(c instanceof JTextField)
                l.push(((JTextField)c).getText());
        }
        return 1;
    }

    public int luaCB_destroy_component(Lua l, LuaPlugin plugin)
    {
        l.pushNil();
        String name = l.checkString(2);
        final JComponent c = components.get(name);
        if(c != null) {
            window.remove(c);
            SwingUtilities.invokeLater(new Runnable() { public void run() {
                components.remove(c);
            }});
            l.pushBoolean(true);
        } else
            l.pushBoolean(false);
        return 1;
    }

    public int luaCB_disable(Lua l, LuaPlugin plugin)
    {
        l.pushNil();
        String name = l.checkString(2);
        final JComponent c = components.get(name);
        SwingUtilities.invokeLater(new Runnable() { public void run() {
            c.setEnabled(false);
        }});
        return 0;
    }

    public int luaCB_enable(Lua l, LuaPlugin plugin)
    {
        l.pushNil();
        String name = l.checkString(2);
        final JComponent c = components.get(name);
        SwingUtilities.invokeLater(new Runnable() { public void run() {
            c.setEnabled(true);
        }});
        return 0;
    }

    public int luaCB_selected(Lua l, LuaPlugin plugin)
    {
        l.pushNil();
        String name = l.checkString(2);
        final JComponent c = components.get(name);
        if(c instanceof JCheckBox)
            l.pushBoolean(((JCheckBox)c).isSelected());
        else
            l.pushBoolean(false);
        return 1;
    }

    public int luaCB_unselect(Lua l, LuaPlugin plugin)
    {
        l.pushNil();
        String name = l.checkString(2);
        final JComponent c = components.get(name);
        SwingUtilities.invokeLater(new Runnable() { public void run() {
            if(c instanceof JCheckBox)
                ((JCheckBox)c).setSelected(false);
        }});
        return 0;
    }

    public int luaCB_select(Lua l, LuaPlugin plugin)
    {
        l.pushNil();
        String name = l.checkString(2);
        final JComponent c = components.get(name);
        SwingUtilities.invokeLater(new Runnable() { public void run() {
            if(c instanceof JCheckBox)
                ((JCheckBox)c).setSelected(true);
        }});
        return 0;
    }

    public int luaCB_show(Lua l, LuaPlugin plugin)
    {
        SwingUtilities.invokeLater(new Runnable() { public void run() {
            window.setVisible(true);
        }});
        return 0;
    }

    public int luaCB_hide(Lua l, LuaPlugin plugin)
    {
        SwingUtilities.invokeLater(new Runnable() { public void run() {
            window.setVisible(false);
        }});
        return 0;
    }

    public int luaCB_close(Lua l, LuaPlugin plugin)
    {
        try {
            plugin.destroyLuaObject(l);
            l.pushBoolean(true);
        } catch(IOException e) {
            l.pushBoolean(false);
            l.pushString("IOException: " + e.getMessage());
            return 2;
        }
        return 1;
    }

    public static int luaCB_create(Lua l, LuaPlugin plugin)
    {
        l.pushNil();
        String title = l.checkString(1);
        try {
            Window x;
            plugin.generateLuaClass(l, x = new Window(plugin,title));
            x.plug = plugin;
        } catch(IOException e) {
            l.pushNil();
            l.pushString("IOException: " + e.getMessage());
            return 2;
        } catch(IllegalArgumentException e) {
            l.pushNil();
            l.pushString("Illegal argument: " + e.getMessage());
            return 2;
        }
        return 1;
    }

    public void actionPerformed(ActionEvent e)
    {
        String actionCommand = e.getActionCommand();
        plug.queueEvent("uiaction", actionCommand);
    }
}
