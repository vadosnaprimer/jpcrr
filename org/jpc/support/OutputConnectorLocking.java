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

import java.util.*;

public class OutputConnectorLocking
{
    private static final int WAITING_START = 0;
    private static final int WAITING_WAIT = 1;
    private static final int WAITING_END = 2;
    private int inWaitingStart;
    private int inWaitingWait;
    private int inWaitingEnd;
    private boolean holdingStable;
    private Map<Integer, ObjectNode> nodeLists;

    public OutputConnectorLocking()
    {
        inWaitingStart = 0;
        inWaitingWait = 0;
        inWaitingEnd = 0;
        holdingStable = false;
        nodeLists = new HashMap<Integer, ObjectNode>();
    }

    public class ObjectNode
    {
        Object key;
        int waitState;
        boolean aquiring;
        ObjectNode prev;
        ObjectNode next;
    }

    private ObjectNode lookupNodeForKey(Object key)
    {
        int hash = System.identityHashCode(key);
        ObjectNode node = nodeLists.get(hash);
        while(node != null) {
            if(node.key == key)
                return node;
            node = node.next;
        }
        return null;
    }

    public synchronized void subscribeOutput(Object handle)
    {
        if(lookupNodeForKey(handle) != null)
            throw new IllegalStateException("Trying to subcribe same object twice");
        int hash = System.identityHashCode(handle);
        ObjectNode node = nodeLists.get(hash);
        ObjectNode newNode = new ObjectNode();
        newNode.key = handle;
        newNode.waitState = WAITING_START;
        newNode.prev = null;
        if(node != null)
            node.prev = newNode;
        newNode.next = node;
        inWaitingStart++;
        nodeLists.put(hash, newNode);
        notifyAll();   //Conditions have changed.
    }

    public synchronized void unsubscribeOutput(Object handle)
    {
        ObjectNode node = lookupNodeForKey(handle);
        if(node == null)
            throw new IllegalStateException("Trying to unsubcribe nonexistent subscription");
        if(node.aquiring)
            throw new IllegalStateException("Trying to unsubcribe subscription that's waiting");
        if(node.prev != null)
            node.prev.next = node.next;
        if(node.next != null)
            node.next.prev = node.prev;
        if(node.waitState == WAITING_START)
            inWaitingStart--;
        if(node.waitState == WAITING_WAIT)
            inWaitingWait--;
        if(node.waitState == WAITING_END)
            inWaitingEnd--;
        notifyAll();   //Conditions have changed.
    }

    public synchronized boolean waitOutput(Object handle)
    {
        ObjectNode node = lookupNodeForKey(handle);
        if(node == null)
            throw new IllegalStateException("Trying to wait with nonexistent subscription");
        if(node.waitState == WAITING_WAIT)
            throw new IllegalStateException("Trying to wait twice with no release in between");
        node.aquiring = true;

        //Wait for object to become "START".
        while(node.waitState != WAITING_START || !holdingStable)
            try {
                wait();
            } catch(InterruptedException e) {
                //Check for one final time.
                if(node.waitState != WAITING_START || !holdingStable)
                    return false;
                break;
            }
        
        //Send it to "WAIT" state.
        node.waitState = WAITING_WAIT;
        inWaitingStart--;
        inWaitingWait++;

        //No need to inform others here. Only transitions to WAITING_START and WAITING_END are significant.

        node.aquiring = false;
        return true;
    }

    public synchronized void releaseOutput(Object handle)
    {
        ObjectNode node = lookupNodeForKey(handle);
        if(node == null)
            throw new IllegalStateException("Trying to release with nonexistent subscription");
        if(node.waitState != WAITING_WAIT)
            throw new IllegalStateException("Trying to release without wait");
        
        //Send it to "END" state.
        node.waitState = WAITING_END;
        inWaitingWait--;
        inWaitingEnd++;

        notifyAll(); //Conditions change.
    }

    public synchronized void holdOutput()
    {
        holdingStable = true;

        //Move everything from "END" to "START".
        for(Map.Entry<Integer,ObjectNode> lists : nodeLists.entrySet()) {
            ObjectNode listNode = lists.getValue();
            while(listNode != null) {
                if(listNode.waitState == WAITING_END) {
                    listNode.waitState = WAITING_START;
                    inWaitingEnd--;
                    inWaitingStart++;
                }
                listNode = listNode.next;
            }
        }

        notifyAll(); //Conditions change.

        //Now wait for all objects to go to "END" state.
        while(inWaitingStart > 0 || inWaitingWait > 0)
            try {
                wait();
            } catch(InterruptedException e) {
            }

        holdingStable = false;
    }
}