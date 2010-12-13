/*
    JPC: An x86 PC Hardware Emulator for a pure Java Virtual Machine
    Release Version 2.4

    A project from the Physics Dept, The University of Oxford

    Copyright (C) 2007-2010 The University of Oxford

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

    jpc.sourceforge.net
    or the developer website
    sourceforge.net/projects/jpc/

    Conceived and Developed by:
    Rhys Newman, Ian Preston, Chris Dennis

    End of licence header
*/

package org.jpc.emulator.memory.codeblock;

/**
 * Provides a resetable stream of bytes.
 * <p>
 * <code>ByteSources</code> are used by <code>Decoder</code> instances to read
 * in the x86 sequences.
 * @author Chris Dennis
 */
public interface ByteSource
{
    /**
     * Get the next byte from this source.
     * @return next byte
     */
    public byte getByte();

    /**
     * Skip forward <code>count</code> bytes in this source.
     * @param count
     */
    public void skip(int count);

    /**
     * Reset this source to point to its first byte.
     */
    public void reset();
}
