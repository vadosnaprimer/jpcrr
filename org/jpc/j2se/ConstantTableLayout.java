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

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.awt.event.*;
import java.awt.*;

//Yes, this really is custom layout manager. Fscking Java doesn't offer better flexible layout manager than GridBagLayout,
//and that can't do constant table layouts.
public class ConstantTableLayout implements LayoutManager2
{
    public static class Placement
    {
        public int posX;
        public int posY;
        public int sizeW;
        public int sizeH;
        public Placement(int x, int y, int w, int h)
        {
            posX = x;
            posY = y;
            sizeW = w;
            sizeH = h;
        } 
    }

    private HashMap<Component, Placement> components;
    private boolean cacheValid;
    private int rowHeight;
    private int columnWidth;
    private int rows;
    private int columns;

    public ConstantTableLayout()
    {
        components = new HashMap<Component, Placement>();
        cacheValid = false;
        rowHeight = 0;
        columnWidth = 0;
        rows = 0;
        columns = 0;
    }

    public void addLayoutComponent(String n, Component c)
    {
        System.err.println("addLayoutComponent(String, Component): Not supported.");
    }

    private void recomputeCache()
    {
        if(cacheValid)
            return;

        rowHeight = 0;
        columnWidth = 0;
        rows = 0;
        columns = 0;
        Iterator<Map.Entry<Component, Placement> > itt = components.entrySet().iterator();
        while (itt.hasNext())
        {
            Map.Entry<Component, Placement> entry = itt.next();
            Component c = entry.getKey();
            Placement p = entry.getValue();
            if(columns < p.posX + p.sizeW)
                columns = p.posX + p.sizeW;
            if(rows < p.posY + p.sizeH)
                rows = p.posY + p.sizeH;
            Dimension minSize = c.getMinimumSize();
            if(columnWidth < (minSize.width + p.sizeW - 1) / p.sizeW)
                columnWidth = (minSize.width + p.sizeW - 1) / p.sizeW;
            if(rowHeight < (minSize.height + p.sizeH - 1) / p.sizeH)
                rowHeight = (minSize.height + p.sizeH - 1) / p.sizeH;
        }
        cacheValid = true;
    }

    public void layoutContainer(Container co)
    {
        recomputeCache();
        Iterator<Map.Entry<Component, Placement> > itt = components.entrySet().iterator();
        while (itt.hasNext())
        {
            Map.Entry<Component, Placement> entry = itt.next();
            Component c = entry.getKey();
            Placement p = entry.getValue();
            c.setBounds(p.posX * columnWidth, p.posY * rowHeight, p.sizeW * columnWidth, p.sizeH * rowHeight);
        }
        co.setSize(minimumLayoutSize(co));
    }

    public Dimension minimumLayoutSize(Container c)
    {
        recomputeCache();
        return new Dimension(columns * columnWidth, rows * rowHeight);
    }

    public Dimension preferredLayoutSize(Container c)
    {
        return minimumLayoutSize(c);
    }

    public Dimension maximumLayoutSize(Container c)
    {
        return minimumLayoutSize(c);
    }

    public void removeLayoutComponent(Component c)
    {
        cacheValid = false;
        components.remove(c);
    }

    public void addLayoutComponent(Component c, Object s)
    {
        cacheValid = false;
        components.put(c, (Placement)s);
    }

    public float getLayoutAlignmentX(Container c)
    {
        return 0.0f;
    }

    public float getLayoutAlignmentY(Container c)
    {
        return 0.0f;
    }

    public void invalidateLayout(Container c)
    {
        cacheValid = false;
    }
}
