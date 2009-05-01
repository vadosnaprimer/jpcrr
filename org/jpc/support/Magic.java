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

package org.jpc.support;
import java.io.*;

public class Magic
{
    private long magic;
    public static final long ABSTRACT_HARDWARE_COMPONENT_MAGIC_V1 =  4644606269319879489L;
    public static final long ABSTRACT_PCI_DEVICE_MAGIC_V1 =          7699257267231847134L;
    public static final long BM_DMA_IO_REGION_MAGIC_V1 =             5896661359304212501L;
    public static final long DELAYED_SECOND_CALLBACK_MAGIC_V1 =      6300125428517400221L;
    public static final long DMA_CONTROLLER_MAGIC_V1 =                475890549860334175L;
    public static final long DMA_REGISTER_MAGIC_V1 =                  132264834477003832L;
    public static final long DRIVE_SET_MAGIC_V1 =                    3367813771034235226L;
    public static final long FLOPPY_BLOCK_DEVICE_MAGIC_V1 =          7364900043666633980L;
    public static final long FLOPPY_CONTROLLER_MAGIC_V1 =            5753837871962494678L;
    public static final long FLOPPY_DRIVE_MAGIC_V1 =                 2540845665010227857L;
    public static final long FPU_STATE_64_MAGIC_V1 =                  339627003138517302L;
    public static final long GATE_A20_HANDLER_MAGIC_V1 =              497916493256992171L;
    public static final long GRAPHICS_UPDATER_MAGIC_V1 =             2569409506855397161L;
    public static final long IDE_CHANNEL_MAGIC_V1 =                  2311669263038275010L;
    public static final long IDE_STATE_MAGIC_V1 =                    8354838684805699379L;
    public static final long INTERRUPT_CONTROLLER_ELEMENT_MAGIC_V1 = 9033787586491562739L;
    public static final long INTERRUPT_CONTROLLER_MAGIC_V1 =         4507219452130721082L;
    public static final long INTERVAL_TIMER_MAGIC_V1 =               5128847404360152276L;
    public static final long IO_PORT_HANDLER_MAGIC_V1 =              7468525780308679430L;
    public static final long KEYBOARD_MAGIC_V1 =                     3049935325425873427L;
    public static final long KEYBOARD_QUEUE_MAGIC_V1 =                837011981899437637L;
    public static final long LINEAR_ADDRESS_SPACE_MAGIC_V1 =         3716031609648790850L;
    public static final long PCI_BUS_MAGIC_V1 =                      1550314299443854446L;
    public static final long PCI_HOST_BRIDGE_MAGIC_V1 =               297664763652786927L;
    public static final long PCI_ISA_BRIDGE_MAGIC_V1 =               4632198759524815864L;
    public static final long PC_MAGIC_V1 =                           1701298434976308288L;
    public static final long PC_SPEAKER_MAGIC_V1 =                   6266243820231454978L;
    public static final long PERIODIC_CALLBACK_MAGIC_V1 =            7450756709038269506L;
    public static final long PHYSICAL_ADDRESS_SPACE_MAGIC_V1 =       5497205389656213169L;
    public static final long PIIX3_IDE_INTERFACE_MAGIC_V1 =          4529278218404998927L;
    public static final long PROCESSOR_MAGIC_V1 =                    1760102576727073157L;
    public static final long RAW_BLOCK_DEVICE_MAGIC_V1 =             8573557541321310955L;
    public static final long RTC_MAGIC_V1 =                          6166764488511328118L;
    public static final long SECOND_CALLBACK_MAGIC_V1 =              5974524727286888918L;
    public static final long SYSTEM_BIOS_MAGIC_V1 =                  2441043442269591584L;
    public static final long TIMER_CHANNEL_MAGIC_V1 =                8064725222447562876L;
    public static final long TIMER_MAGIC_V1 =                        8055729959377651095L;
    public static final long TREE_BLOCK_DEVICE_MAGIC_V1 =            4684893311561399908L;
    public static final long UNCONNECTED_IO_PORT_MAGIC_V1 =          8151034261477691774L;
    public static final long VGA_BIOS_MAGIC_V1 =                     6826993180585731773L;
    public static final long VGA_CARD_MAGIC_V2 =                     4462673531800987738L;
    public static final long VGA_RAM_IO_REGION_MAGIC_V1 =            1755290051316910236L;
    public static final long VIRTUAL_CLOCK_MAGIC_V1 =                7337468882294820848L;

    public Magic(long _magic)
    {
        magic = _magic;
    }
    public void loadState(DataInput input) throws IOException
    {
        long rmagic;

        rmagic = input.readLong();
        if(magic != rmagic)
            throw new IOException("Wrong savestate version. Expected magic " + magic + ", got " + rmagic + ".");
    }
    public void dumpState(DataOutput output) throws IOException
    {
        output.writeLong(magic);
    }
}
