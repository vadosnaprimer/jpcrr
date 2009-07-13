/*
    JPC: A x86 PC Hardware Emulator for a pure Java Virtual Machine
    Release Version 2.0

    A project from the Physics Dept, The University of Oxford

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

    Details (including contact information) can be found at:

    www-jpc.physics.ox.ac.uk
*/

package org.jpc.j2se;

import org.jpc.emulator.Timer;
import java.io.*;

//The reason this exists is that standard Java PriorityQueue breaks ties in arbitiary way. This
//application requires that ties are broken deterministically. In this case, the policy is first-
//in-first-out.


/**
 *
 * @author Ilari Liusvaara
 */
public class TimerPriorityQueue implements org.jpc.SRDumpable
{
    private Node first, last;

    public static class Node
    {
        public Timer timer;
        public Node next;
    }

    public void dumpSRPartial(org.jpc.support.SRDumper output) throws IOException
    {
        Node current = first;
        while(current != null) {
            output.dumpBoolean(true);
            output.dumpObject(current.timer);
            current = current.next;
        }
        output.dumpBoolean(false);
    }

    public TimerPriorityQueue(org.jpc.support.SRLoader input) throws IOException
    {
        input.objectCreated(this);
        boolean present = input.loadBoolean();
        first = null;
        while(present) {
            if(last != null)
                last = last.next = new Node();
            else
                last = first = new Node();
            last.timer = (Timer)input.loadObject();
            present = input.loadBoolean();
        }
    }

    public TimerPriorityQueue()
    {
    }

    public void dumpStatusPartial(org.jpc.support.StatusDumper output)
    {
        //super.dumpStatusPartial(output); <no superclass 20090704>
        Node current = first;
        while(current != null) {
            output.println("\ttimernode <object #" + output.objectNumber(current.timer) + ">"); if(current.timer != null) current.timer.dumpStatus(output);
            current = current.next;
        }
    }

    public void dumpStatus(org.jpc.support.StatusDumper output)
    {
        if(output.dumped(this))
            return;

        output.println("#" + output.objectNumber(this) + ": TimerPriorityQueue:");
        dumpStatusPartial(output);
        output.endObject();
    }

    public Timer peek()
    {
        if(first != null)
            return first.timer;
        else
            return null;
    }

    public void remove(Timer t)
    {
        Node previousNode = null;
        Node currentNode = first;

        //System.err.println("<<<<<<<<<<<<< REMOVING TIMER <<<<<<<<<<<<<<<<<<<");
        //dumpTimers();
        //System.err.println("Removing timer #" + System.identityHashCode(t) + ": expiry at: " + t.getExpiry());


        while(currentNode != null) {
            if(currentNode.timer == t) {
                if(previousNode == null)
                    first = currentNode.next;
                else
                    previousNode.next = currentNode.next;
                if(currentNode == last)
                    last = previousNode;
                //dumpTimers();
                //System.err.println(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>");
                return;
            }
            previousNode = currentNode;
            currentNode = currentNode.next;
        }
        //LOGGING.log(Level.WARNING, "Trying to delete non-existent timer from queue."); <this is annoying>
        //dumpTimers();
        //System.err.println(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>");
    }

    public void offer(Timer t)
    {
        Node newNode = new Node();
        newNode.timer = t;
        Node previousNode = null;
        Node currentNode = first;

        //System.err.println("<<<<<<<<<<<<<< ADDING TIMER <<<<<<<<<<<<<<<<<<<<");
        //dumpTimers();
        //System.err.println("Adding timer #" + System.identityHashCode(t) + ": expiry at: " + t.getExpiry());
        //try {
        //    throw new RuntimeException("Test Stack");
        //} catch(Exception e) {
        //    e.printStackTrace();
        //}

        while(currentNode != null) {
            if(t.compareTo(currentNode.timer) < 0) {
                //This is the first node that's later than node to be added. Insert
                //between previousNode and currentNode.
                newNode.next = currentNode;
                if(previousNode == null)
                    first = newNode;
                else
                    previousNode.next = newNode;
                //dumpTimers();
                //System.err.println(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>");
                return;
            }
            previousNode = currentNode;
            currentNode = currentNode.next;
         }
        //All existing timers should go first.
        if(previousNode != null)
            previousNode.next = newNode;
        else
            first = newNode;
        last = newNode;
        //dumpTimers();
        //System.err.println(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>");
    }

    public String toString()
    {
        return "Timer Event Queue";
    }
}
