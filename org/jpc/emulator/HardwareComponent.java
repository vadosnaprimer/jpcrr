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

package org.jpc.emulator;

/**
 * An object which can form part of a PC.
 * <p>
 * Usually but not always, objects that implement <code>HardwareComponent</code> have
 * a physical counterpart in a real computer.
 * @author Chris Dennis
 */
public interface HardwareComponent extends SRDumpable
{
    /**
     * Returns true when a object need be offered no more
     * <code>HardwareComponent</code> instances through the
     * <code>acceptComponent</code> method.
     * @return true when this component is fully initialised.
     */
    public boolean initialised();

    /**
     * Offers a <code>Hardware Component</code> as possible configuration
     * information for this object.
     * <p>
     * Implementations of this method may or may not maintain a reference to
     * <code>component</code> depending on its type and value.
     * @param component <code>HardwareComponent</code> being offered.
     */
    public void acceptComponent(HardwareComponent component);

    /**
     * Resets this component to its default initial state
     * <p>
     * Implementations of this method should not erase any configuration
     * information.
     */
    public void reset();

    public void dumpStatus(StatusDumper output);
}
