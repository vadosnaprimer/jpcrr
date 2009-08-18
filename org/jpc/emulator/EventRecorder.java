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
import java.util.*;

public class EventRecorder implements TimerResponsive
{
     private static final int EVENT_MAGIC_CLASS = 0;
     private static final int EVENT_MAGIC_SAVESTATE = 1;

     public static final int EVENT_TIMED = 0;
     public static final int EVENT_STATE_EFFECT_FUTURE = 1;
     public static final int EVENT_STATE_EFFECT = 2;
     public static final int EVENT_EXECUTE = 3;

     public class Event
     {
         public long timestamp;                               //Event timestamp (low bound)
         public long timestampModulo;                         //Timestamp modulo.
         public int magic;                                    //Magic type.
         public Class<? extends HardwareComponent> clazz;     //Dispatch to where.
         public String[] args;                                //Arguments to dispatch.
         public Event prev;                                   //Previous event.
         public Event next;                                   //Next event.

         public void dispatch(PC target, int level) throws IOException
         {
             if(magic != EVENT_MAGIC_CLASS)
                 return;   //We really don't want to dispatch these.

             HardwareComponent hwc = target.getComponent(clazz);
             if(hwc == null)
                 throw new IOException("Invalid event target \"" + clazz.getName() + "\": no component of such type");
             EventDispatchTarget component = (EventDispatchTarget)hwc;
             component.doEvent(timestamp, args, level);
         }
     }

     private Event first;
     private Event current;
     private Event last;
     private Event firstUndispatched;
     private Event lastUndispatched;
     private PC pc;
     private boolean directMode;
     private Clock sysClock;
     private Timer sysTimer;
     private long timerInvokeTime;

     public void setTimer(long time)
     {
         if(directMode) {
             sysTimer.disable();     //No need for timers in direct mode.
             return;
         }
         if((sysTimer.enabled() && timerInvokeTime <= time))
             return;         //No need for timer.
         sysTimer.setExpiry(timerInvokeTime = time);
     }

     public synchronized void addEvent(long timeLowBound, long timeModulo, Class<? extends HardwareComponent> clazz,
         String[] args) throws IOException
     {
         /* Compute the final time for event. */
         long timeNow = sysClock.getTime();
         long time = timeLowBound;
         if(last != null && time < last.timestamp)
             time = last.timestamp;
         if(time < timeNow)
             time = timeNow;

         HardwareComponent hwc = pc.getComponent(clazz);
         EventDispatchTarget component = (EventDispatchTarget)hwc;
         long freeLowBound = -1;
         try {
             freeLowBound = component.getEventTimeLowBound(args);
         } catch(Exception e) {};  //Shouldn't throw.
         if(time < freeLowBound)
              time = freeLowBound;

         Event ev = new Event();
         ev.timestamp = time;
         ev.timestampModulo = timeModulo;
         ev.magic = EVENT_MAGIC_CLASS;
         ev.clazz = clazz;
         ev.args = args;

         if(firstUndispatched == null)
             firstUndispatched = ev;
         if(lastUndispatched != null)
             lastUndispatched.next = ev;
         ev.prev = lastUndispatched;
         lastUndispatched = ev;

         if(directMode) {
             handleUndispatchedEvents();
         } else {
             setTimer(timeNow);   //Fire it as soon as possible.
         }
     }


     private void handleUndispatchedEvents()
     {
         long timeNow = sysClock.getTime();

         //First move undispatched events to main queue.
         Event scan = firstUndispatched;
         while(scan != null) {
             //Compute time for event.
             Event scanNext = scan.next;
             if(scan.timestamp < timeNow)
                 scan.timestamp = timeNow;
             if(last != null && scan.timestamp < last.timestamp)
                 scan.timestamp = last.timestamp;

             HardwareComponent hwc = pc.getComponent(scan.clazz);
             EventDispatchTarget component = (EventDispatchTarget)hwc;
             long freeLowBound = -1;
             try {
                 freeLowBound = component.getEventTimeLowBound(scan.args);
             } catch(Exception e) {};  //Shouldn't throw.
             if(scan.timestamp < freeLowBound)
                 scan.timestamp = freeLowBound;

             if(scan.timestampModulo > 0 && scan.timestamp % scan.timestampModulo != 0)
                 scan.timestamp += (scan.timestampModulo - scan.timestamp % scan.timestampModulo);

             //Because of constraints to time, the event must go last.
             scan.next = null;
             scan.prev = last;
             if(last != null)
                 last.next = scan;
             last = scan;
             if(current == null)
                 current = scan;
             if(first == null)
                 first = scan;

             try {
                 scan.dispatch(pc, EVENT_TIMED);
             } catch(Exception e) {
                 System.err.println("Error: Event dispatch failed.");
                 e.printStackTrace();
             }

             scan = scanNext;
         }
         firstUndispatched = null;
         lastUndispatched = null;

         //Then fire apporiate events from main queue.
         while(current != null && current.timestamp <= timeNow) {
             try {
                 current.dispatch(pc, EVENT_EXECUTE);
             } catch(Exception e) {
                 System.err.println("Error: Event dispatch failed.");
                 e.printStackTrace();
             }
             current = current.next;
         }
         if(current != null)
             setTimer(current.timestamp); 
     }

     public synchronized void setPCRunStatus(boolean running)
     {
         directMode = !running;
         if(directMode)
             handleUndispatchedEvents();
         if(current != null)
             setTimer(current.timestamp);
     }

     public void truncateEventStream()
     {
         if(current != null) {
             last = current;
             last.next = null;
             Event scan = first;
             dispatchStart(pc);
             while(scan != null) {
                 try {
                     scan.dispatch(pc, EVENT_STATE_EFFECT);
                 } catch(Exception e) {}
                 scan = scan.next;
             }
             try {
                 dispatchEnd(pc);
             } catch(Exception e) {}
         }
         firstUndispatched = null;
         lastUndispatched = null;
     }

     private void dispatchStart(PC target)
     {
         Set<HardwareComponent> pcParts = target.allComponents();
         for(HardwareComponent hwc : pcParts) {
             if(!EventDispatchTarget.class.isAssignableFrom(hwc.getClass()))
                 continue;
             EventDispatchTarget t = (EventDispatchTarget)hwc;
             t.setEventRecorder(this);
             t.startEventCheck();
         }
     }

     private void dispatchEnd(PC target) throws IOException
     {
         Set<HardwareComponent> pcParts = target.allComponents();
         for(HardwareComponent hwc : pcParts) {
             if(!EventDispatchTarget.class.isAssignableFrom(hwc.getClass()))
                 continue;
             EventDispatchTarget t = (EventDispatchTarget)hwc;
             t.endEventCheck();
         }
     }

     public EventRecorder()
     {
         first = null;
         current = null;
         last = null;
         firstUndispatched = null;
         lastUndispatched = null;
         directMode = true;
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
                 ev.timestamp = timeStamp = Long.parseLong(components[0]);
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
                     if(!EventDispatchTarget.class.isAssignableFrom(clazz))
                         throw new Exception("bad class");
                     if(!HardwareComponent.class.isAssignableFrom(clazz))
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

         firstUndispatched = null;
         lastUndispatched = null;
         directMode = true;
     }

     public void markSave(String id) throws IOException
     {
         /* Current is next event to dispatch. So add it before it. Null means add to
            end. */
         Event ev = new Event();
         ev.timestamp = sysClock.getTime();
         ev.magic = EVENT_MAGIC_SAVESTATE;
         ev.clazz = null;
         ev.args = new String[]{id};
         ev.next = current;
         if(current != null) {
            ev.prev = current.prev;
            if(ev.prev != null)
                ev.prev.next = ev;
            current.prev = ev;
            ev.next = current;
         } else {
            ev.prev = last;
            if(last != null)
                last.next = ev;
            last = ev;
         }
         if(ev.prev == null) {
            first = ev;
         }
     }

     public void attach(PC aPC, String id) throws IOException
     {
         Event oldCurrent = current;

         Clock newSysClock = (Clock)aPC.getComponent(Clock.class);
         long expectedTime = newSysClock.getTime();
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
             dispatchStart(aPC);
             boolean future = false;
             while(scan != null) {
                 if(scan == current)
                     future = true;
                 if(future)
                     scan.dispatch(aPC, EVENT_STATE_EFFECT_FUTURE);
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
         sysClock = newSysClock;
         sysTimer = sysClock.newTimer(this);
         directMode = true;  //Assume direct mode on attach.
         timerInvokeTime = -1;  //No wait in progress.

         handleUndispatchedEvents();    //Do the events that occur after and simultaneously with savestate.
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

     public void callback()
     {
         handleUndispatchedEvents();
     }

     public int getTimerType()
     {
         return 17;
     }

     public void dumpStatus(StatusDumper output)
     {
     }
}
