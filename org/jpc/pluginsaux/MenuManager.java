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

import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.*;
import java.util.*;
import java.util.zip.*;
import java.security.AccessControlException;
import javax.swing.*;
import java.lang.reflect.*;

import static org.jpc.Misc.randomHexes;
import static org.jpc.Misc.errorDialog;
import static org.jpc.Misc.callShowOptionDialog;

public class MenuManager implements ActionListener
{
    private static final long serialVersionUID = 9;

    private JMenuBar menuBar;
    private Map<String, JMenu> menus;
    private Map<String, Integer> menuSubItems;
    private Map<String, JMenuItem> menuItems;
    private Map<String, JCheckBoxMenuItem> selectableMenuItems;
    private Map<String, Object> cbObjects;
    private Map<String, Method> cbMethods;
    private Map<String, Object[]> cbArgs;

    public MenuManager()
    {
        menuBar = new JMenuBar();
        menus = new HashMap<String, JMenu>();
        menuSubItems = new HashMap<String, Integer>();
        menuItems = new HashMap<String, JMenuItem>();
        selectableMenuItems = new HashMap<String, JCheckBoxMenuItem>();
        cbObjects = new HashMap<String, Object>();
        cbMethods = new HashMap<String, Method>();
        cbArgs = new HashMap<String, Object[]>();
    }

    public void enable(String item)
    {
        setEnabled(item, true);
    }

    public void disable(String item)
    {
        setEnabled(item, false);
    }

    public void setEnabled(String item, boolean enabled)
    {
        if(menuItems.containsKey(item))
            menuItems.get(item).setEnabled(enabled);
        else if(selectableMenuItems.containsKey(item))
            selectableMenuItems.get(item).setEnabled(enabled);
        else
            System.err.println("Error: No such menu item " + item + ".");
    }

    public boolean isEnabled(String item)
    {
        if(menuItems.containsKey(item))
            return menuItems.get(item).isEnabled();
        else if(selectableMenuItems.containsKey(item))
            return selectableMenuItems.get(item).isEnabled();
        else
            System.err.println("Error: No such menu item " + item + ".");
        return false;
    }

    public void select(String item)
    {
        setSelected(item, true);
    }

    public void unselect(String item)
    {
        setSelected(item, false);
    }

    public void setSelected(String item, boolean enabled)
    {
        if(selectableMenuItems.containsKey(item))
            selectableMenuItems.get(item).setSelected(enabled);
        else
            System.err.println("Error: No such selectable menu item " + item + ".");
    }

    public boolean isSelected(String item)
    {
        if(selectableMenuItems.containsKey(item))
            return selectableMenuItems.get(item).isSelected();
        else
            System.err.println("Error: No such selectable menu item " + item + ".");
        return false;
    }

    public void removeMenuItem(String item)
    {
        String upperItem = upperForItem(item);
        if(menuItems.containsKey(item)) {
             menus.get(upperItem).remove(menuItems.get(item));
             decrementCounter(upperItem);
             menuItems.remove(item);
        } else if(selectableMenuItems.containsKey(item)) {
             menus.get(upperItem).remove(selectableMenuItems.get(item));
             decrementCounter(upperItem);
             selectableMenuItems.remove(item);
        } else
            System.err.println("Error: No such removable menu item " + item + ".");
    }

    public void addMenuItem(String item, Object cbObject, String cbMethod, Object[] args, boolean enabled)
        throws Exception
    {
        createItem(item);
        addCallback(item, cbObject, cbMethod, args);
        if(!enabled)
            disable(item);
    }

    public void addSelectableMenuItem(String item, Object cbObject, String cbMethod, Object[] args, boolean enabled,
        boolean selected) throws Exception
    {
        createSelectableItem(item);
        addCallback(item, cbObject, cbMethod, args);
        if(!enabled)
            disable(item);
        if(selected)
            select(item);
    }

    private void addCallback(String item, Object cbObject, String cbMethod, Object[] args) throws Exception
    {
        Object[] x = new Object[1];
        Method _cbMethod = cbObject.getClass().getMethod(cbMethod, String.class, x.getClass());
        cbObjects.put(item, cbObject);
        cbMethods.put(item, _cbMethod);
        cbArgs.put(item, args);

        if(menuItems.containsKey(item))
            menuItems.get(item).addActionListener(this);
        else if(selectableMenuItems.containsKey(item))
            selectableMenuItems.get(item).addActionListener(this);
    }

    private void decrementCounter(String item)
    {
        int newCount;
        String upperItem = upperForItem(item);
        menuSubItems.put(item, new Integer(newCount = (menuSubItems.get(item).intValue() - 1)));
        if(newCount == 0 && upperItem != null) {
            //Nifty, top-level menus can't be removed!
             menus.get(upperItem).remove(menus.get(item));
             menus.remove(item);
             decrementCounter(upperItem);
        }
    }

    private void createItem(String item) throws Exception
    {
        JMenuItem newMenuItem = null;
        String upperItem = upperForItem(item);

        if(menuItems.containsKey(item) || selectableMenuItems.containsKey(item) || menus.containsKey(item))
            throw new Exception("Error: createItem: Conflicting item " + item  +"!");

        _createNeededMenus(upperItem);
        menus.get(upperItem).add(newMenuItem = new JMenuItem(lastComponent(item)));
        incrementCounter(upperItem);
        menuItems.put(item, newMenuItem);
    }

    private void createSelectableItem(String item) throws Exception
    {
        JCheckBoxMenuItem newMenuItem = null;
        String upperItem = upperForItem(item);

        if(menuItems.containsKey(item) || selectableMenuItems.containsKey(item) || menus.containsKey(item))
            throw new Exception("Error: createItem: Conflicting item " + item  +"!");

        _createNeededMenus(upperItem);
        menus.get(upperItem).add(newMenuItem = new JCheckBoxMenuItem(lastComponent(item)));
        incrementCounter(upperItem);
        selectableMenuItems.put(item, newMenuItem);
    }

    private void _createNeededMenus(String item) throws Exception
    {
        JMenu newMenu = null;

        if(item == null)
            throw new Exception("_createNeededMenus: item is NULL!");
        if(menuItems.containsKey(item) || selectableMenuItems.containsKey(item))
            throw new Exception("Error: _createNeededMenus: Conflicting item " + item  +"!");

        if(menus.containsKey(item))
            return; //Already exists.

        String upperItem = upperForItem(item);
        if(upperItem == null) {
            menuBar.add(newMenu = new JMenu(lastComponent(item)));
        } else {
            _createNeededMenus(upperItem);
            menus.get(upperItem).add(newMenu = new JMenu(lastComponent(item)));
            incrementCounter(upperItem);
        }
        menuSubItems.put(item, new Integer(0));
        menus.put(item, newMenu);
    }

    private void incrementCounter(String item)
    {
        menuSubItems.put(item, new Integer(menuSubItems.get(item).intValue() + 1));
    }

    private String upperForItem(String item)
    {
        String ans;
        int split = item.lastIndexOf(0x2192);
        if(split < 0)
            return null;
        ans = item.substring(0, split);
        return ans;
    }

    private String lastComponent(String item)
    {
        String ans;
        int split = item.lastIndexOf(0x2192);
        if(split < 0)
            return item;
        ans = item.substring(split + 1);
        return ans;
    }

    public JMenuBar getMainBar()
    {
        return menuBar;
    }

    public void actionPerformed(ActionEvent evt)
    {
        String match = null;
        for(Map.Entry<String, JMenuItem> x : menuItems.entrySet())
            if(x.getValue() == evt.getSource())
                match = x.getKey();
        for(Map.Entry<String, JCheckBoxMenuItem> x : selectableMenuItems.entrySet())
            if(x.getValue() == evt.getSource())
                match = x.getKey();

        if(cbMethods.containsKey(match))
            try {
                cbMethods.get(match).invoke(cbObjects.get(match), match, cbArgs.get(match));
            } catch(Exception e) {
                errorDialog(e, "Can't dispatch menu event", null, "Dismiss");
            }
        else
            System.err.println("actionPerformed on unknown object " + match + ".");
    }
}
