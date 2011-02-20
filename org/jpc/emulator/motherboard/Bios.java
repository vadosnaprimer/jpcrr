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

package org.jpc.emulator.motherboard;

import java.io.*;

import org.jpc.emulator.*;
import org.jpc.emulator.memory.*;
import org.jpc.images.BaseImage;
import org.jpc.images.ImageID;
import org.jpc.images.BaseImageFactory;


/**
 * Abstract class for loading bios images into a <code>PhysicalAddressSpace</code>.
 * Sub-classes must implement the <code>loadAddress</code> method indicating the
 * physical address at which the image they were constructed with should be
 * loaded.
 * @author Chris Dennis
 */
public abstract class Bios extends AbstractHardwareComponent {

    private byte[] imageData;
    private boolean loaded;
    private final StringBuilder biosOutputBuffer = new StringBuilder();

    public void dumpStatusPartial(StatusDumper output)
    {
        super.dumpStatusPartial(output);
        output.println("\tloaded " + loaded);
        output.println("\timageData:");
        output.printArray(imageData, "imageData");
    }

    public void dumpStatus(StatusDumper output)
    {
        if(output.dumped(this))
            return;

        output.println("#" + output.objectNumber(this) + ": Bios:");
        dumpStatusPartial(output);
        output.endObject();
    }

    public void dumpSRPartial(SRDumper output) throws IOException
    {
        super.dumpSRPartial(output);
        output.dumpBoolean(loaded);
        output.dumpArray(imageData);
    }

    public Bios(SRLoader input) throws IOException
    {
        super(input);
        loaded = input.loadBoolean();
        imageData = input.loadArrayByte();
    }

    /**
     * Constructs a new bios which will load its image using the given resource
     * name.
     * @param image name of the bios image resource.
     * @throws java.io.IOException potentially caused by reading the resource.
     * @throws java.util.MissingResourceException if the named resource cannot be found
     */
    public Bios(ImageID image) throws IOException {
        this(getBiosData(image));
    }

    private Bios(byte[] image) {
        imageData = new byte[image.length];
        System.arraycopy(image, 0, imageData, 0, image.length);
        loaded = false;
    }

    private void load(PhysicalAddressSpace addressSpace) {
        int loadAddress = loadAddress();
        int nextBlockStart = (loadAddress & AddressSpace.INDEX_MASK) + AddressSpace.BLOCK_SIZE;

        //repeat and load the system bios a second time at the end of the memory
        int endLoadAddress = (int) (0x100000000l - imageData.length);
        EPROMMemory ep = new EPROMMemory(AddressSpace.BLOCK_SIZE, loadAddress & AddressSpace.BLOCK_MASK, imageData, 0, nextBlockStart - loadAddress, addressSpace.getCodeBlockManager());
        addressSpace.mapMemory(loadAddress & AddressSpace.INDEX_MASK, ep);
        if (this instanceof SystemBIOS) {
            //only copy the bios in the end of memory, don't make it an eeprom there
            addressSpace.copyArrayIntoContents(endLoadAddress, imageData, 0, imageData.length);
        }

        int imageOffset = nextBlockStart - loadAddress;
        int epromOffset = nextBlockStart;
        while ((imageOffset + AddressSpace.BLOCK_SIZE) <= imageData.length) {
            ep = new EPROMMemory(imageData, imageOffset, AddressSpace.BLOCK_SIZE, addressSpace.getCodeBlockManager());
            addressSpace.mapMemory(epromOffset, ep);
            epromOffset += AddressSpace.BLOCK_SIZE;
            imageOffset += AddressSpace.BLOCK_SIZE;
        }

        if (imageOffset < imageData.length) {
            ep = new EPROMMemory(AddressSpace.BLOCK_SIZE, 0, imageData, imageOffset, imageData.length - imageOffset, addressSpace.getCodeBlockManager());
            addressSpace.mapMemory(epromOffset, ep);
        }
    }

    /**
     * Address where the sub-class of <code>Bios</code> wants to load its image.
     * @return physical address where the image is loaded.
     */
    protected abstract int loadAddress();

    public boolean initialised() {
        return loaded;
    }

    public void acceptComponent(HardwareComponent component) {
        if ((component instanceof PhysicalAddressSpace) && component.initialised()) {
            this.load((PhysicalAddressSpace) component);
            loaded = true;
        }
    }

    public void reset() {
        loaded = false;
    }

    public int length() {
        return imageData.length;
    }

    protected void print(int data) {
        synchronized (biosOutputBuffer) {
            if(data == 10) {
                System.err.println("Emulated: BIOS output: " + biosOutputBuffer.toString());
                biosOutputBuffer.delete(0, biosOutputBuffer.length());
            }
            else
                biosOutputBuffer.append((char)data);
        }
    }

    protected void print(String data) {
        synchronized (biosOutputBuffer) {
            int newline;
            while ((newline = data.indexOf('\n')) >= 0) {
                biosOutputBuffer.append(data.substring(0, newline));
                System.err.println("Emulated: BIOS output: " + biosOutputBuffer.toString());
                biosOutputBuffer.delete(0, biosOutputBuffer.length());
                data = data.substring(newline + 1);
            }
            biosOutputBuffer.append(data);
        }
    }

    private static final byte[] getBiosData(ImageID image) throws IOException {
        BaseImage pimg = BaseImageFactory.getImageByID(image);
        if(pimg.getType() != BaseImage.Type.BIOS)
            throw new IOException(image + ": is not a BIOS image.");
        byte[] data = new byte[(int)pimg.getTotalSectors()];
        pimg.read(0, data, data.length);
        return data;
    }
}
