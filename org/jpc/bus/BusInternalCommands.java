package org.jpc.bus;

import org.jpc.emulator.PC;
import org.jpc.emulator.memory.PhysicalAddressSpace;
import static org.jpc.Misc.castToByte;
import static org.jpc.Misc.castToShort;
import static org.jpc.Misc.castToInt;
import static org.jpc.Misc.castToLong;

class BusInternalCommands
{
    private PC pc;
//    private PC.PCFullStatus status;
//    private volatile boolean running;

    public BusInternalCommands(Bus _bus)
    {
        _bus.setShutdownHandler(this, "shutdown");
        _bus.setEventHandler(this, "reconnect", "pc-change");
        _bus.setCommandHandler(this, "writeMemoryByte", "write-memory-byte");
        _bus.setCommandHandler(this, "writeMemoryWord", "write-memory-word");
        _bus.setCommandHandler(this, "writeMemoryDoubleWord", "write-memory-dword");
        _bus.setCommandHandler(this, "writeMemoryQuadWord", "write-memory-qword");
        _bus.setCommandHandler(this, "readMemoryByte", "read-memory-byte");
        _bus.setCommandHandler(this, "readMemoryWord", "read-memory-word");
        _bus.setCommandHandler(this, "readMemoryDoubleWord", "read-memory-dword");
        _bus.setCommandHandler(this, "readMemoryQuadWord", "read-memory-qword");
    }

    public void reconnect(String command, Object[] args)
    {
        if(args == null || args.length != 1)
            throw new IllegalArgumentException("pc-change: Event needs an argument");
        pc = (PC)args[0];
    }

    public boolean shutdown()
    {
        return true;
    }

    public String writeMemoryByte_help(String cmd, boolean brief)
    {
        if(brief)
            return "Write byte into memory";
        System.err.println("Synopsis: " + cmd + " <address> <value>");
        System.err.println("Writes byte-sized value <value> to physical address <address>.");
        return null;
    }

    public void writeMemoryByte(BusRequest req, String cmd, Object[] args) throws IllegalArgumentException
    {
        int addr = castToInt(args[0]);
        byte value = castToByte(args[1]);
        if(pc != null) {
            PhysicalAddressSpace addrSpace;
            addrSpace = (PhysicalAddressSpace)pc.getComponent(PhysicalAddressSpace.class);
            addrSpace.setByte(addr, value);
            req.doReturn();
        } else
            throw new IllegalArgumentException("No pc present");
    }

    public String writeMemoryWord_help(String cmd, boolean brief)
    {
        if(brief)
            return "Write word into memory";
        System.err.println("Synopsis: " + cmd + " <address> <value>");
        System.err.println("Writes word-sized value <value> to physical address <address>.");
        return null;
    }

    public void writeMemoryWord(BusRequest req, String cmd, Object[] args)
    {
        int addr = castToInt(args[0]);
        short value = castToShort(args[1]);
        if(pc != null) {
            PhysicalAddressSpace addrSpace;
            addrSpace = (PhysicalAddressSpace)pc.getComponent(PhysicalAddressSpace.class);
            addrSpace.setWord(addr, value);
            req.doReturn();
        } else
            throw new IllegalArgumentException("No pc present");
    }

    public String writeMemoryDoubleWord_help(String cmd, boolean brief)
    {
        if(brief)
            return "Write dword into memory";
        System.err.println("Synopsis: " + cmd + " <address> <value>");
        System.err.println("Writes dword-sized value <value> to physical address <address>.");
        return null;
    }

    public void writeMemoryDoubleWord(BusRequest req, String cmd, Object[] args)
    {
        int addr = castToInt(args[0]);
        int value = castToInt(args[1]);
        if(pc != null) {
            PhysicalAddressSpace addrSpace;
            addrSpace = (PhysicalAddressSpace)pc.getComponent(PhysicalAddressSpace.class);
            addrSpace.setDoubleWord(addr, value);
            req.doReturn();
        } else
            throw new IllegalArgumentException("No pc present");
    }

    public String  writeMemoryQuadWord_help(String cmd, boolean brief)
    {
        if(brief)
            return "Write qword into memory";
        System.err.println("Synopsis: " + cmd + " <address> <value>");
        System.err.println("Writes qword-sized value <value> to physical address <address>.");
        return null;
    }

    public void writeMemoryQuadWord(BusRequest req, String cmd, Object[] args)
    {
        int addr = castToInt(args[0]);
        long value = castToLong(args[1]);
        if(pc != null) {
            PhysicalAddressSpace addrSpace;
            addrSpace = (PhysicalAddressSpace)pc.getComponent(PhysicalAddressSpace.class);
            addrSpace.setQuadWord(addr, value);
            req.doReturn();
        } else
            throw new IllegalArgumentException("No pc present");
    }

    public String readMemoryByte_help(String cmd, boolean brief)
    {
        if(brief)
            return "Read byte from memory";
        System.err.println("Synopsis: " + cmd + " <address>");
        System.err.println("Read byte-sized value from physical address <address>.");
        return null;
    }

    public void readMemoryByte(BusRequest req, String cmd, Object[] args)
    {
        int addr = castToInt(args[0]);
        if(pc != null) {
            PhysicalAddressSpace addrSpace;
            addrSpace = (PhysicalAddressSpace)pc.getComponent(PhysicalAddressSpace.class);
            req.doReturnL((int)addrSpace.getByte(addr) & 0xFF);
        } else
            throw new IllegalArgumentException("No pc present");
    }

    public String readMemoryWord_help(String cmd, boolean brief)
    {
        if(brief)
            return "Read word from memory";
        System.err.println("Synopsis: " + cmd + " <address>");
        System.err.println("Read word-sized value from physical address <address>.");
        return null;
    }

    public void readMemoryWord(BusRequest req, String cmd, Object[] args)
    {
        int addr = castToInt(args[0]);
        if(pc != null) {
            PhysicalAddressSpace addrSpace;
            addrSpace = (PhysicalAddressSpace)pc.getComponent(PhysicalAddressSpace.class);
            req.doReturnL((int)addrSpace.getWord(addr) & 0xFFFF);
        } else
            throw new IllegalArgumentException("No pc present");
    }

    public String readMemoryDoubleWord_help(String cmd, boolean brief)
    {
        if(brief)
            return "Read dword from memory";
        System.err.println("Synopsis: " + cmd + " <address>");
        System.err.println("Read dword-sized value from physical address <address>.");
        return null;
    }

    public void readMemoryDoubleWord(BusRequest req, String cmd, Object[] args)
    {
        int addr = castToInt(args[0]);
        if(pc != null) {
            PhysicalAddressSpace addrSpace;
            addrSpace = (PhysicalAddressSpace)pc.getComponent(PhysicalAddressSpace.class);
            req.doReturnL((long)addrSpace.getDoubleWord(addr) & 0xFFFFFFFF);
        } else
            throw new IllegalArgumentException("No pc present");
    }

    public String readMemoryQuadWord_help(String cmd, boolean brief)
    {
        if(brief)
            return "Read qword from memory";
        System.err.println("Synopsis: " + cmd + " <address>");
        System.err.println("Read qword-sized value from physical address <address>.");
        return null;
    }

    public void readMemoryQuadWord(BusRequest req, String cmd, Object[] args)
    {
        int addr = castToInt(args[0]);
        if(pc != null) {
            PhysicalAddressSpace addrSpace;
            addrSpace = (PhysicalAddressSpace)pc.getComponent(PhysicalAddressSpace.class);
            req.doReturnL(addrSpace.getQuadWord(addr));
        } else
            throw new IllegalArgumentException("No pc present");
    }

/*
    public void getRerecordCount(BusRequest req, String cmd, Object[] args)
    {
        if(status != null && status.pc != null)
            req.doReturnL(status.rerecords);
        else
            req.doReturn();
    }


    public void loadState(BusRequest req, String cmd, Object[] args) {}
    public void loadStateSeekOnly(BusRequest req, String cmd, Object[] args) {}
    public void loadMovie(BusRequest req, String cmd, Object[] args) {}
    public void saveState(BusRequest req, String cmd, Object[] args) {}
    public void saveMovie(BusRequest req, String cmd, Object[] args) {}
    public void saveStatus(BusRequest req, String cmd, Object[] args) {}
    public void saveRAMBinary(BusRequest req, String cmd, Object[] args) {}
    public void saveRAMHex(BusRequest req, String cmd, Object[] args) {}
    public void saveImage(BusRequest req, String cmd, Object[] args) {}
    public void sendEvent(BusRequest req, String cmd, Object[] args) {}
    public void sendEventLowbound(BusRequest req, String cmd, Object[] args) {}
    public void assemblePC(BusRequest req, String cmd, Object[] args) {}
    public void trapVRS(BusRequest req, String cmd, Object[] args) {}
    public void trapVRE(BusRequest req, String cmd, Object[] args) {}
    public void trapBKBD(BusRequest req, String cmd, Object[] args) {}
    public void trapTimed(BusRequest req, String cmd, Object[] args) {}
    public void setRunning(BusRequest req, String cmd, Object[] args) {}
    public void isRunning(BusRequest req, String cmd, Object[] args) {}
    public void getLoadedRerecordCount(BusRequest req, String cmd, Object[] args) {}
*/
}