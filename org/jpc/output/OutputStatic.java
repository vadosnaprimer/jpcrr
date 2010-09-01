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

package org.jpc.output;

import java.io.*;
import java.util.*;

public class OutputStatic
{
    long timeBase;
    long lastTime;

    public class OutputPair
    {
        public short channel;
        public OutputFrame frame;
        public OutputPair(short ch, OutputFrame f)
        {
            channel = ch;
            frame = f;
        }
    };

    LinkedList<OutputPair> frames;

    public OutputStatic()
    {
        timeBase = 0;
        lastTime = 0;
        frames = new LinkedList<OutputPair>();
        clients = new HashSet<OutputClient>();
    }

    public long getLastTime()
    {
        return lastTime;
    }

    Map<Short, OutputChannel> activeChannelTable;

    public byte[] makeChannelTable()
    {
        return makeChannelTable(activeChannelTable);
    }

    public byte[] makeChannelTable(Map<Short, OutputChannel> tab)
    {
        List<byte[]> hunks = new LinkedList<byte[]>();
        byte[] head = new byte[18];
        head[0] = (byte)0xFF;
        head[1] = (byte)0xFF;
        head[2] = (byte)'J';
        head[3] = (byte)'P';
        head[4] = (byte)'C';
        head[5] = (byte)'R';
        head[6] = (byte)'R';
        head[7] = (byte)'M';
        head[8] = (byte)'U';
        head[9] = (byte)'L';
        head[10] = (byte)'T';
        head[11] = (byte)'I';
        head[12] = (byte)'D';
        head[13] = (byte)'U';
        head[14] = (byte)'M';
        head[15] = (byte)'P';
        head[16] = (byte)((tab.size() >>> 8) & 0xFF);
        head[17] = (byte)(tab.size() & 0xFF);
        hunks.add(head);

        for(Map.Entry<Short, OutputChannel> e : tab.entrySet())
            hunks.add(e.getValue().channelHeader());

        int size = 0;
        for(byte[] hunk : hunks)
            size += hunk.length;
        byte[] raw = new byte[size];
        size = 0;
        for(byte[] hunk : hunks) {
            System.arraycopy(hunk, 0, raw, size, hunk.length);
            size += hunk.length;
        }
        return raw;
    }

    public void updateChannelTable(Map<Short, OutputChannel> tab)
    {
        byte[] raw = makeChannelTable(tab);
        addFrame((short)-1, new OutputFrameRaw(raw), false);
        activeChannelTable = tab;
    }

    public void addFrame(short chan, OutputFrame frame, boolean sync)
    {
        synchronized(this) {
            lastTime = frame.getTime();
            frames.add(new OutputPair(chan, frame));
        }
        if(sync) {
            //Signal subscribers.
            waitReaders();
            if(frames.size() > 0)
                timeBase = frames.getLast().frame.getTime();
            frames.clear();
        }
    }

    public synchronized OutputFrame lastFrame(Class<? extends OutputFrame> clazz)
    {
        OutputFrame f = null;
        for(OutputPair p : frames)
            if(clazz == null || clazz.isAssignableFrom(p.frame.getClass()))
                f = p.frame;
        return f;
    }

    static public interface FrameFilter
    {
        public OutputFrame doFilter(OutputFrame f, short channel);
    }

    public synchronized long writeFrames(OutputStream out, FrameFilter filter) throws IOException
    {
        long localTimeBase = timeBase;
        for(OutputPair frame : frames) {
            long newTime = frame.frame.getTime();
            OutputFrame f = null;
            if(filter != null)
                f = filter.doFilter(frame.frame, frame.channel);
            else
                f = frame.frame;
            if(f != null) {
                out.write(f.dump(frame.channel, localTimeBase));
                if(newTime >= localTimeBase)
                    localTimeBase = newTime;
            }
        }
        return localTimeBase;
    }

    volatile int clientsNew;
    volatile int clientsAquiring;
    volatile int clientsAquired;
    volatile int clientsReleasing;
    volatile int clientsReleased;
    volatile boolean waiting;
    Set<OutputClient> clients;

    private void setClientState(OutputClient c, int newState)
    {
        if(c.getState() == -1)
            ;
        else if(c.getState() == 0)
            clientsNew--;
        else if(c.getState() == 1)
            clientsAquiring--;
        else if(c.getState() == 2)
            clientsAquired--;
        else if(c.getState() == 3)
            clientsReleasing--;
        else if(c.getState() == 4)
            clientsReleased--;
        else
            throw new IllegalStateException("Client in illegal state #" + c.getState());
        if(newState == -1)
            ;
        else if(newState == 0)
            clientsNew++;
        else if(newState == 1)
            clientsAquiring++;
        else if(newState == 2)
            clientsAquired++;
        else if(newState == 3)
            clientsReleasing++;
        else if(newState == 4)
            clientsReleased++;
        else
            throw new IllegalStateException("Trying to set illegal state #" + newState);
        c.setState(newState);
        notifyAll();
    }

    private synchronized void waitReaders()
    {
        //Set all relesed back to new.
        for(OutputClient c : clients)
            if(c.getState() == 4)
                setClientState(c, 0);

        waiting = true;
        notifyAll();
        //Wait until everyone is released.
        while(clientsNew > 0 || clientsAquiring > 0 || clientsAquired > 0 || clientsReleasing > 0)
            try {
                wait();
            } catch(InterruptedException e) {
            }
        waiting = false;
    }

    protected synchronized void clientNew(OutputClient c)
    {
        if(clients.add(c)) {
           c.setState(-1);
           setClientState(c, 0);
        }
    }

    protected synchronized void clientDestroy(OutputClient c)
    {
        if(clients.remove(c)) {
            setClientState(c, -1);
        }
    }

    protected synchronized boolean clientAquire(OutputClient c)
    {
        int state = c.getState();
        if(state != 0 && state != 4)
            throw new IllegalStateException("Trying to lock already locked lock (state=" + state + ")");

        //Wait for released state to get cleared.
        while(c.getState() == 4)
            try {
                wait();
            } catch(InterruptedException e) {
                if(c.getState() == 0 && waiting) {
                    setClientState(c, 2);
                    return true;
                } else
                    return false;
            }

        if(waiting) {
            setClientState(c, 2);
            return true;
        } else
            setClientState(c, 1);

        //Wait for us to get the lock.
        while(!waiting)
            try {
                wait();
            } catch(InterruptedException e) {
                break;
            }

        if(c.getState() == 1)
            setClientState(c, waiting ? 2 : 0);

        return waiting;
    }

    protected synchronized void clientRelease(OutputClient c, boolean waitAll)
    {
        int state = c.getState();
        if(state != 2)
            throw new IllegalStateException("Trying to unlock already unlocked lock");
        if(waitAll) {
            setClientState(c, 3);
            //Wait for everybody to release the lock.
            while(clientsNew > 0 || clientsAquiring > 0 || clientsAquired > 0)
                try {
                    wait();
                } catch(InterruptedException e) {
                }
        }
        setClientState(c, 4);
    }
};
