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

import org.jpc.emulator.memory.PhysicalAddressSpace;
import org.jpc.emulator.processor.fpu64.FpuState64;
import org.jpc.plugins.LuaPlugin;
import java.io.*;
import java.util.*;


//Locking this class is used for preventing termination and when terminating.
public class MemorySearch extends LuaPlugin.LuaResource
{
    Map<Integer, byte[]> currentPages;
    Map<Integer, Integer> currentPageCandidates;
    long candidates;
    int firstBadPage;

    public MemorySearch(LuaPlugin plugin) throws IOException
    {
        super(plugin);
        currentPages = new HashMap<Integer, byte[]>();
        currentPageCandidates = new HashMap<Integer, Integer>();
    }

    public void destroy() throws IOException
    {
        currentPages.clear();
        currentPageCandidates.clear();
        candidates = 0;
        firstBadPage = 0;
    }

    public int luaCB_reset(Lua l, LuaPlugin plugin)
    {
        PhysicalAddressSpace mem = (PhysicalAddressSpace)plugin.getComponent(PhysicalAddressSpace.class);
        currentPages.clear();
        currentPageCandidates.clear();
        candidates = 0;
        firstBadPage = 0;
        if(mem != null) {
            int pageBase = 0;
            Integer n4096 = new Integer(4096);
            while(true) {
                pageBase = mem.findFirstRAMPage(pageBase);
                if(pageBase < 0)
                    break;
                byte[] buf = new byte[4096 + 4096 / 8];
                Arrays.fill(buf, 4096, 4096 + 4096 / 8, (byte)-1);
                Integer pBI = new Integer(pageBase);
                currentPages.put(pBI, buf);
                currentPageCandidates.put(pBI, n4096);
                mem.readRAMPage(pageBase, buf);
                candidates += 4096;
                pageBase++;
                firstBadPage = pageBase;
            }
        }
        l.pushNumber((double)candidates);
        return 1;
    }

    final private static int typeSize(int accessType) throws IOException
    {
        switch(accessType) {
        case 0:
            return 1;
        case 1:
            return 2;
        case 2:
            return 4;
        case 3:
            return 8;
        case 0x40:
            return 4;
        case 0x41:
            return 8;
        case 0x42:
            return 10;
        default:
            throw new IOException("Bad access type " + accessType);
        }
    }

    final private static boolean canFastpath(int compType)
    {
        switch(compType) {
        case 0:
        case 1:
        case 6:
        case 7:
        case 9:
            return true;
        default:
            return false;
        }
    }

    final public boolean compareCore(long addr, long b1, long b2, int compType) throws IOException
    {
        switch(compType) {
        case 0: //S<
            return (b1 < b2);
        case 3: //U<=
            if(b1 == b2)
                return true;
        case 1: //U<
            if(b1 >= 0 && b2 >= 0)
                return (b1 < b2);
            else if(b1 < 0 && b2 < 0)
                return (b1 > b2);
            else
                return (b1 >= 0 && b2 < 0);
        case 2: //S<=
            return (b1 <= b2);
        case 4: //S>=
            return (b1 >= b2);
        case 6: //S>
            return (b1 > b2);
        case 5: //U>=
            if(b1 == b2)
                return true;
        case 7: //U>
            if(b1 >= 0 && b2 >= 0)
                return (b1 > b2);
            else if(b1 < 0 && b2 < 0)
                return (b1 < b2);
            else
                return (b1 < 0 && b2 >= 0);
        case 8: //==
            return (b1 == b2);
        case 9: //!=
            return (b1 != b2);
        default:
            throw new IOException("Bad compare type " + compType);
        }
    }

    final public boolean floatCompareCore(long addr, double b1, double b2, int compType) throws IOException
    {
        switch(compType) {
        case 0: //<
            return (b1 < b2);
        case 2: //<=
            return (b1 <= b2);
        case 4: //>=
            return (b1 >= b2);
        case 6: //>
            return (b1 > b2);
        case 8: //==
            return (b1 == b2);
        case 9: //!=
            return (b1 != b2);
        default:
            throw new IOException("Bad compare type " + compType);
        }
    }

    final public boolean byteCompare(PhysicalAddressSpace mem, int pageNo, byte[] page, byte[] nextpage, int offset,
        int compType) throws IOException
    {
        int b1;
        int b2;
        byte[] newPage = new byte[4096];
        mem.readRAMPage(pageNo, newPage);
        b1 = (int)page[offset] & 0xFF;
        b2 = (int)newPage[offset] & 0xFF;
        if(b1 > 127)
            b1 -= 256;
        if(b2 > 127)
            b2 -= 256;
        return compareCore(pageNo * 4096 + offset, b2, b1, compType);
    }

    final public boolean leWordCompare(PhysicalAddressSpace mem, int pageNo, byte[] page, byte[] nextPage, int offset,
        int compType) throws IOException
    {
        int b1;
        int b2;
        int b11;
        int b12;
        int b21;
        int b22;
        byte[] newPage = new byte[4096];
        byte[] newNextPage = new byte[4096];
        mem.readRAMPage(pageNo, newPage);
        mem.readRAMPage(pageNo + 1, newNextPage);

        int offset0 = offset % 4096;
        int offset1 = (offset + 1) % 4096;
        if(offset1 < 1 && nextPage == null)
            return false;

        b11 = (int)page[offset0] & 0xFF;
        b21 = (int)newPage[offset0] & 0xFF;
        b12 = (int)((offset1 < 1) ? nextPage[offset1] : page[offset1]) & 0xFF;
        b22 = (int)((offset1 < 1) ? newNextPage[offset1] : newPage[offset1]) & 0xFF;
        b1 = (b12 << 8) | b11;
        b2 = (b22 << 8) | b21;
        //Wraparound negatives.
        if(b1 > 32767)
            b1 -= 65536;
        if(b2 > 32767)
            b2 -= 65536;
        return compareCore(pageNo * 4096 + offset, b2, b1, compType);
    }

    final public boolean leDwordCompare(PhysicalAddressSpace mem, int pageNo, byte[] page, byte[] nextPage, int offset,
        int compType) throws IOException
    {
        int b1;
        int b2;
        int b11;
        int b12;
        int b13;
        int b14;
        int b21;
        int b22;
        int b23;
        int b24;
        byte[] newPage = new byte[4096];
        byte[] newNextPage = new byte[4096];
        mem.readRAMPage(pageNo, newPage);
        mem.readRAMPage(pageNo + 1, newNextPage);

        int offset0 = offset % 4096;
        int offset1 = (offset + 1) % 4096;
        int offset2 = (offset + 2) % 4096;
        int offset3 = (offset + 3) % 4096;
        if(offset3 < 3 && nextPage == null)
            return false;

        b11 = (int)page[offset0] & 0xFF;
        b21 = (int)newPage[offset0] & 0xFF;
        b12 = (int)((offset1 < 1) ? nextPage[offset1] : page[offset1]) & 0xFF;
        b22 = (int)((offset1 < 1) ? newNextPage[offset1] : newPage[offset1]) & 0xFF;
        b13 = (int)((offset2 < 2) ? nextPage[offset2] : page[offset2]) & 0xFF;
        b23 = (int)((offset2 < 2) ? newNextPage[offset2] : newPage[offset2]) & 0xFF;
        b14 = (int)((offset3 < 3) ? nextPage[offset3] : page[offset3]) & 0xFF;
        b24 = (int)((offset3 < 3) ? newNextPage[offset3] : newPage[offset3]) & 0xFF;
        b1 = (b14 << 24) | (b13 << 16) | (b12 << 8) | b11;
        b2 = (b24 << 24) | (b23 << 16) | (b22 << 8) | b21;
        //Wraparound negatives.
        if(b1 > 2147483647)
            b1 -= 4294967296L;
        if(b2 > 2147483647)
            b2 -= 4294967296L;
        return compareCore(pageNo * 4096 + offset, b2, b1, compType);
    }

    final public boolean leQwordCompare(PhysicalAddressSpace mem, int pageNo, byte[] page, byte[] nextPage, int offset,
        int compType) throws IOException
    {
        long b1;
        long b2;
        int b11;
        int b12;
        int b13;
        int b14;
        int b21;
        int b22;
        int b23;
        int b24;
        int b15;
        int b16;
        int b17;
        int b18;
        int b25;
        int b26;
        int b27;
        int b28;
        byte[] newPage = new byte[4096];
        byte[] newNextPage = new byte[4096];
        mem.readRAMPage(pageNo, newPage);
        mem.readRAMPage(pageNo + 1, newNextPage);

        int offset0 = offset % 4096;
        int offset1 = (offset + 1) % 4096;
        int offset2 = (offset + 2) % 4096;
        int offset3 = (offset + 3) % 4096;
        int offset4 = (offset + 4) % 4096;
        int offset5 = (offset + 5) % 4096;
        int offset6 = (offset + 6) % 4096;
        int offset7 = (offset + 7) % 4096;
        if(offset7 < 7 && nextPage == null)
            return false;

        b11 = (int)page[offset0] & 0xFF;
        b21 = (int)newPage[offset0] & 0xFF;
        b12 = (int)((offset1 < 1) ? nextPage[offset1] : page[offset1]) & 0xFF;
        b22 = (int)((offset1 < 1) ? newNextPage[offset1] : newPage[offset1]) & 0xFF;
        b13 = (int)((offset2 < 2) ? nextPage[offset2] : page[offset2]) & 0xFF;
        b23 = (int)((offset2 < 2) ? newNextPage[offset2] : newPage[offset2]) & 0xFF;
        b14 = (int)((offset3 < 3) ? nextPage[offset3] : page[offset3]) & 0xFF;
        b24 = (int)((offset3 < 3) ? newNextPage[offset3] : newPage[offset3]) & 0xFF;
        b15 = (int)((offset4 < 4) ? nextPage[offset4] : page[offset4]) & 0xFF;
        b25 = (int)((offset4 < 4) ? newNextPage[offset4] : newPage[offset4]) & 0xFF;
        b16 = (int)((offset5 < 5) ? nextPage[offset5] : page[offset5]) & 0xFF;
        b26 = (int)((offset5 < 5) ? newNextPage[offset5] : newPage[offset5]) & 0xFF;
        b17 = (int)((offset6 < 6) ? nextPage[offset6] : page[offset6]) & 0xFF;
        b27 = (int)((offset6 < 6) ? newNextPage[offset6] : newPage[offset6]) & 0xFF;
        b18 = (int)((offset7 < 7) ? nextPage[offset7] : page[offset7]) & 0xFF;
        b28 = (int)((offset7 < 7) ? newNextPage[offset7] : newPage[offset7]) & 0xFF;

        b1 = ((long)b18 << 56) | ((long)b17 << 48) | ((long)b16 << 40) | ((long)b15 << 32) |
            ((long)b14 << 24) | ((long)b13 << 16) | ((long)b12 << 8) | (long)b11;
        b2 = ((long)b28 << 56) | ((long)b27 << 48) | ((long)b26 << 40) | ((long)b25 << 32) |
            ((long)b24 << 24) | ((long)b23 << 16) | ((long)b22 << 8) | (long)b21;
        return compareCore(pageNo * 4096 + offset, b2, b1, compType);
    }

    final public boolean floatCompare(PhysicalAddressSpace mem, int pageNo, byte[] page, byte[] nextPage, int offset,
        int compType) throws IOException
    {
        int b1;
        int b2;
        int b11;
        int b12;
        int b13;
        int b14;
        int b21;
        int b22;
        int b23;
        int b24;
        byte[] newPage = new byte[4096];
        byte[] newNextPage = new byte[4096];
        mem.readRAMPage(pageNo, newPage);
        mem.readRAMPage(pageNo + 1, newNextPage);

        int offset0 = offset % 4096;
        int offset1 = (offset + 1) % 4096;
        int offset2 = (offset + 2) % 4096;
        int offset3 = (offset + 3) % 4096;
        if(offset3 < 3 && nextPage == null)
            return false;

        b11 = (int)page[offset0] & 0xFF;
        b21 = (int)newPage[offset0] & 0xFF;
        b12 = (int)((offset1 < 1) ? nextPage[offset1] : page[offset1]) & 0xFF;
        b22 = (int)((offset1 < 1) ? newNextPage[offset1] : newPage[offset1]) & 0xFF;
        b13 = (int)((offset2 < 2) ? nextPage[offset2] : page[offset2]) & 0xFF;
        b23 = (int)((offset2 < 2) ? newNextPage[offset2] : newPage[offset2]) & 0xFF;
        b14 = (int)((offset3 < 3) ? nextPage[offset3] : page[offset3]) & 0xFF;
        b24 = (int)((offset3 < 3) ? newNextPage[offset3] : newPage[offset3]) & 0xFF;
        b1 = (b14 << 24) | (b13 << 16) | (b12 << 8) | b11;
        b2 = (b24 << 24) | (b23 << 16) | (b22 << 8) | b21;
        return floatCompareCore(pageNo * 4096 + offset, Float.intBitsToFloat(b2), Float.intBitsToFloat(b1), compType);
    }

    final public boolean doubleCompare(PhysicalAddressSpace mem, int pageNo, byte[] page, byte[] nextPage, int offset,
        int compType) throws IOException
    {
        long b1;
        long b2;
        int b11;
        int b12;
        int b13;
        int b14;
        int b21;
        int b22;
        int b23;
        int b24;
        int b15;
        int b16;
        int b17;
        int b18;
        int b25;
        int b26;
        int b27;
        int b28;
        byte[] newPage = new byte[4096];
        byte[] newNextPage = new byte[4096];
        mem.readRAMPage(pageNo, newPage);
        mem.readRAMPage(pageNo + 1, newNextPage);

        int offset0 = offset % 4096;
        int offset1 = (offset + 1) % 4096;
        int offset2 = (offset + 2) % 4096;
        int offset3 = (offset + 3) % 4096;
        int offset4 = (offset + 4) % 4096;
        int offset5 = (offset + 5) % 4096;
        int offset6 = (offset + 6) % 4096;
        int offset7 = (offset + 7) % 4096;
        if(offset7 < 7 && nextPage == null)
            return false;

        b11 = (int)page[offset0] & 0xFF;
        b21 = (int)newPage[offset0] & 0xFF;
        b12 = (int)((offset1 < 1) ? nextPage[offset1] : page[offset1]) & 0xFF;
        b22 = (int)((offset1 < 1) ? newNextPage[offset1] : newPage[offset1]) & 0xFF;
        b13 = (int)((offset2 < 2) ? nextPage[offset2] : page[offset2]) & 0xFF;
        b23 = (int)((offset2 < 2) ? newNextPage[offset2] : newPage[offset2]) & 0xFF;
        b14 = (int)((offset3 < 3) ? nextPage[offset3] : page[offset3]) & 0xFF;
        b24 = (int)((offset3 < 3) ? newNextPage[offset3] : newPage[offset3]) & 0xFF;
        b15 = (int)((offset4 < 4) ? nextPage[offset4] : page[offset4]) & 0xFF;
        b25 = (int)((offset4 < 4) ? newNextPage[offset4] : newPage[offset4]) & 0xFF;
        b16 = (int)((offset5 < 5) ? nextPage[offset5] : page[offset5]) & 0xFF;
        b26 = (int)((offset5 < 5) ? newNextPage[offset5] : newPage[offset5]) & 0xFF;
        b17 = (int)((offset6 < 6) ? nextPage[offset6] : page[offset6]) & 0xFF;
        b27 = (int)((offset6 < 6) ? newNextPage[offset6] : newPage[offset6]) & 0xFF;
        b18 = (int)((offset7 < 7) ? nextPage[offset7] : page[offset7]) & 0xFF;
        b28 = (int)((offset7 < 7) ? newNextPage[offset7] : newPage[offset7]) & 0xFF;

        b1 = ((long)b18 << 56) | ((long)b17 << 48) | ((long)b16 << 40) | ((long)b15 << 32) |
            ((long)b14 << 24) | ((long)b13 << 16) | ((long)b12 << 8) | (long)b11;
        b2 = ((long)b28 << 56) | ((long)b27 << 48) | ((long)b26 << 40) | ((long)b25 << 32) |
            ((long)b24 << 24) | ((long)b23 << 16) | ((long)b22 << 8) | (long)b21;
        return floatCompareCore(pageNo * 4096 + offset, Double.longBitsToDouble(b2), Double.longBitsToDouble(b1),
            compType);
    }

    final public boolean longDoubleCompare(PhysicalAddressSpace mem, int pageNo, byte[] page, byte[] nextPage, int offset,
        int compType) throws IOException
    {
        byte[] newPage = new byte[4096];
        byte[] newNextPage = new byte[4096];
        byte[] b1 = new byte[10];
        byte[] b2 = new byte[10];
        mem.readRAMPage(pageNo, newPage);
        mem.readRAMPage(pageNo + 1, newNextPage);

        if(offset > 4096 - 10 && nextPage == null)
            return false;

        for(int i = 0; i < 10; i++) {
            b1[i] = (offset + i > 4095) ? nextPage[(offset + i) % 4096] : page[offset + i];
            b2[i] = (offset + i > 4095) ? newNextPage[(offset + i) % 4096] : newPage[offset + i];
        }
        return floatCompareCore(pageNo * 4096 + offset, FpuState64.extendedToDouble(b2),
            FpuState64.extendedToDouble(b1), compType);
    }

    private int fastpathProcessPage(PhysicalAddressSpace mem, int pageNum, byte[] page,
        int comparisionType, int accessType) throws IOException
    {
        if(!canFastpath(comparisionType))
            return 0;   //No fastpath available.
        byte[] newPage = new byte[4096];
        mem.readRAMPage(pageNum, newPage);
        int maxEliminate = 4096;
        int tSize = typeSize(accessType);
        for(int i = 0; i < maxEliminate; i++)
            if(page[i] != newPage[i]) {
                if(i >= tSize)
                    return i - tSize + 1;
                else
                    return 0;
            }
        return maxEliminate - tSize;
    }


    public int doUpdate(PhysicalAddressSpace mem, int ctype) throws IOException
    {
        int accessType = ctype & 0x43;
        int comparisionType = (ctype & ~3) >>> 2;
        for(Map.Entry<Integer, byte[]> page : currentPages.entrySet()) {
            byte[] nextPage = currentPages.get(page.getKey().intValue() + 1);
            int candidateReduction = 0;
            Integer pageNum = page.getKey();
            if(currentPageCandidates.get(pageNum) == 0)
                continue;
            byte[] pageContent = page.getValue();
            //Fastpath-eliminate entries.
            int offsetBase = fastpathProcessPage(mem, pageNum, pageContent, comparisionType, accessType);
            for(int i = 0; i < offsetBase; i++)
                if((pageContent[4096 + i / 8] & (1 << (i % 8))) != 0) {
                    candidateReduction++;
                    pageContent[4096 + i / 8] &= ~(1 << (i % 8));
                }
            for(int i = offsetBase; i < 4096; i++) {
                if(((pageContent[4096 + i / 8] >> (i % 8)) & 1) == 0)
                    continue;
                boolean res = true;
                switch(accessType) {
                case 0:
                    res = byteCompare(mem, pageNum, pageContent, nextPage, i, comparisionType);
                    break;
                case 1:
                    res = leWordCompare(mem, pageNum, pageContent, nextPage, i, comparisionType);
                    break;
                case 2:
                    res = leDwordCompare(mem, pageNum, pageContent, nextPage, i, comparisionType);
                    break;
                case 3:
                    res = leQwordCompare(mem, pageNum, pageContent, nextPage, i, comparisionType);
                    break;
                case 0x40:
                    res = floatCompare(mem, pageNum, pageContent, nextPage, i, comparisionType);
                    break;
                case 0x41:
                    res = doubleCompare(mem, pageNum, pageContent, nextPage, i, comparisionType);
                    break;
                case 0x42:
                    res = longDoubleCompare(mem, pageNum, pageContent, nextPage, i, comparisionType);
                    break;
                default:
                    throw new IOException("Bad access type " + accessType);
                }
                if(!res) {
                    candidateReduction++;
                    pageContent[4096 + i / 8] &= ~(1 << (i % 8));
                }
            }
            currentPageCandidates.put(pageNum, currentPageCandidates.get(pageNum) - candidateReduction);
            candidates -= candidateReduction;
            mem.readRAMPage(page.getKey(), page.getValue());
        }
        return (int)candidates;
    }

    private static String doFormat(long number, int mode, boolean hex)
    {
        if(mode == 8) {
            int b = (int)number;
            return String.format("%g", Float.intBitsToFloat(b));
        }
        if(mode == 9)
            return String.format("%g", Double.longBitsToDouble(number));

        if(hex && (mode == 0 || mode == 4))
            return String.format("%02x", number & 0xFF);
        if(hex && (mode == 1 || mode == 5))
            return String.format("%04x", number & 0xFFFF);
        if(hex && (mode == 2 || mode == 6))
            return String.format("%08x", number & 0xFFFFFFFFL);
        if(hex && (mode == 3 || mode == 7)) {
            String s1 = String.format("%08x", number >>> 32);
            String s2 = String.format("%08x", number & 0xFFFFFFFFL);
            return s1 + s2;
        }

        if(mode == 0)
            return String.format("%d", number & 0xFF);
        if(mode == 1)
            return String.format("%d", number & 0xFFFF);
        if(mode == 2)
            return String.format("%d", number & 0xFFFFFFFFL);
        if(mode == 3)
            //FIXME!
            return String.format("%d", number);
        if(mode == 4 || mode == 5 || mode == 6 || mode == 7)
            return String.format("%d", number);
        return "N/A";
    }

    private static String doFormatLongDouble(PhysicalAddressSpace mem, long address)
    {
        byte[] newPage = new byte[4096];
        byte[] newNextPage = new byte[4096];
        byte[] b = new byte[10];
        int pageNo = (int)(address >> 12);
        int offset = (int)(address & 0xFFF);
        mem.readRAMPage(pageNo, newPage);
        mem.readRAMPage(pageNo + 1, newNextPage);

        for(int i = 0; i < 10; i++)
            b[i] = (offset + i > 4095) ? newNextPage[(offset + i) % 4096] : newPage[offset + i];

        return String.format("%g", FpuState64.extendedToDouble(b));
    }

    private static long readNumber(PhysicalAddressSpace mem, long address, int mode)
    {
        byte[] buffer = new byte[8192];
        mem.readRAMPage((int)(address >> 12), buffer, 0);
        mem.readRAMPage((int)(address >> 12) + 1, buffer, 4096);
        int offset = (int)(address & 0xFFF);

        if(mode == 0 || mode == 4) {
            return (long)buffer[offset] & 0xFF;
        } else if(mode == 1 || mode == 5) {
            return (((long)buffer[offset + 1] & 0xFF) << 8) |
                ((long)buffer[offset + 0] & 0xFF);
        } else if(mode == 2 || mode == 6 || mode == 8) {
            return (((long)buffer[offset + 3] & 0xFF) << 24) |
                (((long)buffer[offset + 2] & 0xFF) << 16) |
                (((long)buffer[offset + 1] & 0xFF) << 8) |
                ((long)buffer[offset + 0] & 0xFF);
        } else if(mode == 3 || mode == 7 || mode == 9) {
            return (((long)buffer[offset + 7] & 0xFF) << 56) |
                (((long)buffer[offset + 6] & 0xFF) << 48) |
                (((long)buffer[offset + 5] & 0xFF) << 40) |
                (((long)buffer[offset + 4] & 0xFF) << 32) |
                (((long)buffer[offset + 3] & 0xFF) << 24) |
                (((long)buffer[offset + 2] & 0xFF) << 16) |
                (((long)buffer[offset + 1] & 0xFF) << 8) |
                ((long)buffer[offset + 0] & 0xFF);
        }
        return 0;
    }

    public static int luaCB_format(Lua l, LuaPlugin plugin)
    {
        l.pushNil();
        l.pushNil();
        l.pushNil();
        long addr = (long)(int)l.checkNumber(1) & 0xFFFFFFFF;
        int mode  = (int)l.checkNumber(2);
        Object _hex = l.value(3);
        boolean hex = l.toBoolean(_hex);
        if(!l.isBoolean(_hex))
            l.error("Expected boolean as 3rd parameter of MemorySearch.Format");

        PhysicalAddressSpace mem = (PhysicalAddressSpace)plugin.getComponent(PhysicalAddressSpace.class);
        if(mem == null) {
            l.pushString("N/A");
            return 1;
        }

        if(mode != 10) {
            long num = readNumber(mem, addr, mode);
            l.pushString(doFormat(num, mode, hex));
        } else
            l.pushString(doFormatLongDouble(mem, addr));

        return 1;
    }

    public int luaCB_update(Lua l, LuaPlugin plugin)
    {
        l.pushNil();
        int type = (int)l.checkNumber(2);
        try {
            PhysicalAddressSpace mem = (PhysicalAddressSpace)plugin.getComponent(PhysicalAddressSpace.class);
            if(mem == null) {
                currentPages.clear();
                candidates = 0;
                l.pushNumber((double)0);
            } else
                l.pushNumber((double)doUpdate(mem, type));
        } catch(IOException e) {
            l.pushBoolean(false);
            l.pushString("IOException: " + e.getMessage());
            return 2;
        }
        return 1;
    }

    public int luaCB_candidate_count(Lua l, LuaPlugin plugin)
    {
        l.pushNumber((double)candidates);
        return 1;
    }

    public int luaCB_next_candidate(Lua l, LuaPlugin plugin)
    {
        l.pushNil();
        int base = (int)l.checkNumber(2);
        while(true) {
            int page = base >> 12;
            if(page >= firstBadPage) {
                base = -1;
                break;
            }
            if(!currentPageCandidates.containsKey(page) || currentPageCandidates.get(page) == 0) {
                //Skip the whole page.
                base = ((base >> 12) + 1) << 12;
                continue;
            }
            int block = (base % 4096) / 8;
            byte[] pageData = currentPages.get(page);
            if(pageData[4096 + block] == 0) {
                //Skip block.
                base = ((base >> 3) + 1) << 3;
                continue;
            }
            if(((pageData[4096 + block] >> (base % 8)) & 1) == 0)
                base++;
            else
                break;
        }

        l.pushNumber((double)base);
        return 1;
    }

    public int luaCB_destroy(Lua l, LuaPlugin plugin)
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
        try {
            plugin.generateLuaClass(l, new MemorySearch(plugin));
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
}
