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

package org.jpc;

import java.io.*;
import org.jpc.emulator.*;

public interface EventDispatchTarget
{
    //When event stream gets loaded, event dispatcher does so called event check. All events in stream are
    //dispatched in test mode, from start to end. This is done by first calling startEventCheck(), then for
    //each event in timer order checkEvent() and finally after last event, call endEventCheck(). If no 
    //exceptions are thrown, the event stream is assumed OK. This check is called after reporting the new
    //event controller using setEventController().
    //
    // If event has no data, eventData parameter is null.
    public void setEventController(EventController controller);
    public void startEventCheck();
    public void checkEvent(long timeStamp, short subChannel, byte[] eventData) throws IOException;
    public void endEventCheck() throws IOException;
    //
    //These two are used to set the state feedback. stateReset() resets the feedback state. stateEvent()
    //updates state feedback like that event really occured (but that event is not really carried out). This
    //process can occur at any time (in response to reloads, truncations, etc)...
    public void stateReset();
    public void stateEvent(long timeStamp, short subChannel, byte[] eventData) throws IOException;

    //This is real event occuring.
    public void eventDispatched(long timeStamp, short subChannel, byte[] eventData) throws IOException;
}
