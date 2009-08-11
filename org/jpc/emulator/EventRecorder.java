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

package org.jpc.emulator;

import org.jpc.support.UTFInputLineStream;
import org.jpc.support.UTFOutputLineStream;
import static org.jpc.Misc.nextParseLine;
import static org.jpc.Misc.componentEscape;
import java.io.*;

public class EventRecorder
{
     private static final int EVENT_MAGIC_CLASS = 0;
     private static final int EVENT_MAGIC_SAVESTATE = 1;

     public static final int EVENT_CHECK_ONLY = 0;
     public static final int EVENT_STATE_EFFECT = 1;
     public static final int EVENT_EXECUTE = 2;

     public class Event
     {
         public long timestamp;  //Event timestamp.
         public int magic;       //Magic type.o
         public Class<? extends HardwareComponent> clazz;     //Dispatch to where.
         public String[] args;   //Arguments to dispatch.
         public Event prev;      //Previous event.
         public Event next;      //Next event.

         public void dispatch(PC target, int level) throws IOException
         {
             if(magic != EVENT_MAGIC_CLASS)
                 return;   //We really don't want to dispatch these.
             HardwareComponent hwc = target.getComponent(clazz);
             if(hwc == null)
                 throw new IOException("Invalid event target \"" + clazz.getName() + "\": no component of such type");
             EventDispatchTarget component = (EventDispatchTarget)hwc;
         }
     }

     private Event first;
     private Event current;
     private Event last;
     private PC pc;

     private void dispatchStart(PC target)
     {
     }

     private void dispatchEnd(PC target)
     {
     }

     public EventRecorder()
     {
         first = null;
         current = null;
         last = null;
     }

     private void linkEvent(Event ev)
     {
          
     }

     public EventRecorder(UTFInputLineStream lines) throws IOException
     {
         String[] components = nextParseLine(lines);
         while(components != null) {
             Event ev = new Event();
             if(components.length < 2)
                 throw new IOException("Malformed event line");
             long timeStamp;
             try {
                 timeStamp = Long.parseLong(components[0]);
                 if(timeStamp < 0)
                     throw new IOException("Negative timestamp value " + timeStamp + " not allowed");
             } catch(NumberFormatException e) {
                 throw new IOException("Invalid timestamp value \"" + components[0] + "\"");
             }
             if(last != null && timeStamp < last.timestamp)
                 throw new IOException("Timestamp order violation: " + timeStamp + "<" + last.timestamp);
             String clazzName = components[1];
             if(clazzName.equals("SAVESTATE")) {
                 if(components.length != 3)
                     throw new IOException("Malformed SAVESTATE line");
                 ev.magic = EVENT_MAGIC_SAVESTATE;
                 ev.clazz = null;
                 ev.args = new String[]{components[2]};
             } else {
                 //Something dispatchable.
                 ev.magic = EVENT_MAGIC_CLASS;
                 Class<?> clazz;
                 try {
                     clazz = Class.forName(clazzName);
                     if(EventDispatchTarget.class.isAssignableFrom(clazz))
                         throw new Exception("bad class");
                     if(HardwareComponent.class.isAssignableFrom(clazz))
                         throw new Exception("bad class");
                 } catch(Exception e) {
                     throw new IOException("\"" + clazzName + "\" is not valid event target");
                 }
                 ev.clazz = clazz.asSubclass(HardwareComponent.class);
                 if(components.length == 2)
                     ev.args = null;
                 else {
                     ev.args = new String[components.length - 2];
                     System.arraycopy(components, 2, ev.args, 0, ev.args.length);
                 }
             }

             ev.prev = last;
             if(last == null)
                 first = ev;
             else
                 last.next = ev;
             last = ev;

             components = nextParseLine(lines);
         }
     }

     public void markSave(String id) throws IOException
     {
         /* Current is next event to dispatch. So add it before it. Null means add to
            end. */
         Event ev = new Event();
         ev.timestamp = ((Clock)pc.getComponent(Clock.class)).getTime();
         ev.magic = EVENT_MAGIC_SAVESTATE;
         ev.clazz = null;
         ev.args = new String[]{id};
         ev.next = current;
         if(current != null) {
            ev.prev = current.prev;
            current.prev = ev;
         } else {
            ev.prev = last;
            last = ev;
         }
         if(first == current)
            first = ev;
     }

     public void attach(PC aPC, String id) throws IOException
     {
         Event oldCurrent = current;

         long expectedTime = ((Clock)aPC.getComponent(Clock.class)).getTime();
         if(id == null) {
             current = first;
         } else {
             Event scan = first;
             while(scan != null) {
                 if(scan.magic == EVENT_MAGIC_SAVESTATE && scan.args[0].equals(id))
                     break;
                 scan = scan.next;
             }
             if(scan == null)
                 throw new IOException("Savestate not compatible with event stream");

             if(scan.timestamp != expectedTime)
                 throw new IOException("Incorrect savestate event timestamp");

             current = scan;
         }

         try {
             Event scan = first;
             boolean atPoint = false;
             dispatchStart(aPC);
             while(scan != null) {
                 if(scan == current)
                     atPoint = true;
                 if(atPoint)
                     scan.dispatch(aPC, EVENT_CHECK_ONLY);
                 else
                     scan.dispatch(aPC, EVENT_STATE_EFFECT);
                 scan = scan.next;
             }
             dispatchEnd(aPC);
         } catch(IOException e) {
             //Back off the changes.
             current = oldCurrent;
             throw e;
         }

         pc = aPC;
     }

     public void saveEvents(UTFOutputLineStream lines) throws IOException
     {
         Event scan = first;
         while(scan != null) {
             if(scan.magic == EVENT_MAGIC_SAVESTATE) {
                lines.writeLine(scan.timestamp + " SAVESTATE " + componentEscape(scan.args[0]));
             } else {
                 StringBuilder sb = new StringBuilder();
                 sb.append(scan.timestamp);
                 sb.append(" ");
                 sb.append(componentEscape(scan.clazz.getName()));
                 if(scan.args != null)
                     for(int i = 0; i < scan.args.length; i++) {
                         sb.append(" ");
                         sb.append(componentEscape(scan.args[i]));
                     }
                 lines.writeLine(sb.toString());
             }
             scan = scan.next;
         }
     }
}
