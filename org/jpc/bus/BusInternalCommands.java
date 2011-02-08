package org.jpc.bus;

import org.jpc.emulator.PC;
import org.jpc.emulator.memory.PhysicalAddressSpace;
import static org.jpc.Misc.castToByte;
import static org.jpc.Misc.castToShort;
import static org.jpc.Misc.castToInt;
import static org.jpc.Misc.castToLong;

class BusInternalCommands
{
//    private PC.PCFullStatus status;
//    private volatile boolean running;

    public BusInternalCommands(Bus _bus)
    {
        _bus.setShutdownHandler(this, "shutdown");
    }

    public boolean shutdown()
    {
        return true;
    }
/*
    public void getRerecordCount(BusRequest req, String cmd, Object[] args)
    {
        if(status != null && status.pc != null)
            req.doReturnL(status.rerecords);
        else
            req.doReturn();
    }

    public void writeMemoryByte(BusRequest req, String cmd, Object[] args)
    {
        int addr = castToInt(args[0]);
        byte value = castToByte(args[1]);
        if(status != null && status.pc != null) {
            PhysicalAddressSpace addrSpace;
            addrSpace = (PhysicalAddressSpace)status.pc.getComponent(PhysicalAddressSpace.class);
            addrSpace.setByte(addr, value);
            req.doReturnL(true);
        } else
            req.doReturnL(false);
    }

    public void writeMemoryWord(BusRequest req, String cmd, Object[] args)
    {
        int addr = castToInt(args[0]);
        short value = castToShort(args[1]);
        if(status != null && status.pc != null) {
            PhysicalAddressSpace addrSpace;
            addrSpace = (PhysicalAddressSpace)status.pc.getComponent(PhysicalAddressSpace.class);
            addrSpace.setWord(addr, value);
            req.doReturnL(true);
        } else
            req.doReturnL(false);
    }

    public void writeMemoryDoubleWord(BusRequest req, String cmd, Object[] args)
    {
        int addr = castToInt(args[0]);
        int value = castToInt(args[1]);
        if(status != null && status.pc != null) {
            PhysicalAddressSpace addrSpace;
            addrSpace = (PhysicalAddressSpace)status.pc.getComponent(PhysicalAddressSpace.class);
            addrSpace.setDoubleWord(addr, value);
            req.doReturnL(true);
        } else
            req.doReturnL(false);
    }

    public void writeMemoryQuadWord(BusRequest req, String cmd, Object[] args)
    {
        int addr = castToInt(args[0]);
        long value = castToLong(args[1]);
        if(status != null && status.pc != null) {
            PhysicalAddressSpace addrSpace;
            addrSpace = (PhysicalAddressSpace)status.pc.getComponent(PhysicalAddressSpace.class);
            addrSpace.setQuadWord(addr, value);
            req.doReturnL(true);
        } else
            req.doReturnL(false);
    }

    public void readMemoryByte(BusRequest req, String cmd, Object[] args)
    {
        int addr = castToInt(args[0]);
        if(status != null && status.pc != null) {
            PhysicalAddressSpace addrSpace;
            addrSpace = (PhysicalAddressSpace)status.pc.getComponent(PhysicalAddressSpace.class);
            req.doReturnL(addrSpace.getByte(addr));
        } else
            req.doReturn();
    }

    public void readMemoryWord(BusRequest req, String cmd, Object[] args)
    {
        int addr = castToInt(args[0]);
        if(status != null && status.pc != null) {
            PhysicalAddressSpace addrSpace;
            addrSpace = (PhysicalAddressSpace)status.pc.getComponent(PhysicalAddressSpace.class);
            req.doReturnL(addrSpace.getWord(addr));
        } else
            req.doReturn();
    }

    public void readMemoryDoubleWord(BusRequest req, String cmd, Object[] args)
    {
        int addr = castToInt(args[0]);
        if(status != null && status.pc != null) {
            PhysicalAddressSpace addrSpace;
            addrSpace = (PhysicalAddressSpace)status.pc.getComponent(PhysicalAddressSpace.class);
            req.doReturnL(addrSpace.getDoubleWord(addr));
        } else
            req.doReturn();
    }

    public void readMemoryQuadWord(BusRequest req, String cmd, Object[] args)
    {
        int addr = castToInt(args[0]);
        if(status != null && status.pc != null) {
            PhysicalAddressSpace addrSpace;
            addrSpace = (PhysicalAddressSpace)status.pc.getComponent(PhysicalAddressSpace.class);
            req.doReturnL(addrSpace.getQuadWord(addr));
        } else
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