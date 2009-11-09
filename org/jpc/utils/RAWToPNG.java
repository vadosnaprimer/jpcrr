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

package org.jpc.utils;

import java.io.*;
import java.nio.*;
import java.util.*;
import java.util.zip.*;
import javax.swing.*;
import org.jpc.emulator.*;
import org.jpc.pluginsaux.PNGSaver;
import static org.jpc.Misc.errorDialog;
import static org.jpc.Misc.callShowOptionDialog;
import static org.jpc.Misc.parseStringToComponents;

public class RAWToPNG
{
    public static void main(String[] args)
    {
        PNGSaver saver;
        PrintStream timingFile;
        InputStream rawInputStreamFile;
        if(args.length != 2) {
            callShowOptionDialog(null, "Syntax: RAWToPNG <input> <prefix>", "Usage error",
                JOptionPane.OK_OPTION, JOptionPane.WARNING_MESSAGE, null, new String[]{"Quit"}, "Quit");
            return;
        }

        saver = new PNGSaver(args[1]);
        try {
            rawInputStreamFile = new FileInputStream(args[0]);
            timingFile = new PrintStream(args[1] + ".timing", "UTF-8");
        } catch(IOException e) {
            errorDialog(e, "Failed to open file", null, "Quit");
            return;
        }

        long readCount = 0;
        int resets = 0;
        byte[] iBuffer = new byte[1024];
        int iBufferFill = 0;
        Inflater inflater = new Inflater();
        boolean justReset = true;
        long time = 0;
        boolean haveTime = false;
        boolean haveDimensions = false;
        long frameNum = 0;
        int w = 0;
        int h = 0;
        byte[] headerWord = new byte[4];
        int headerFill = 0;
        byte[] frameBuffer = null;

        while(true) {
            try {
                if(inflater.needsInput()) {
                    int r;
                    r = rawInputStreamFile.read(iBuffer);
                    if(r >= 0) {
                        iBufferFill += r;
                        inflater.setInput(iBuffer, 0, r);
                        justReset = false;
                    } else if(!justReset) {
                        callShowOptionDialog(null, "Unexpected end of stream", "Stream error",
                           JOptionPane.OK_OPTION, JOptionPane.WARNING_MESSAGE, null, new String[]{"Quit"}, "Quit");
                        return;
                    } else {
                        return;
                    }
                }
            } catch(IOException e) {
                errorDialog(e, "Failed to read from stream", null, "Quit");
                return;
            }

            try {
                if(!haveDimensions) {
                    int r = inflater.inflate(headerWord, headerFill, headerWord.length - headerFill);
                    if(r > 0)
                        headerFill += r;
                }

                if(!haveTime && headerFill == 4) {
                    long timeOffset = 0;
                    timeOffset += (((long)headerWord[0] & 0xFF) << 24);
                    timeOffset += (((long)headerWord[1] & 0xFF) << 16);
                    timeOffset += (((long)headerWord[2] & 0xFF) << 8);
                    timeOffset += (((long)headerWord[3] & 0xFF));
                    time += timeOffset;
                    if(timeOffset < 0xFFFFFFFFL)
                        haveTime = true;
                    headerFill = 0;
                } else if(haveTime && !haveDimensions && headerFill == 4) {
                    w = 0;
                    h = 0;
                    w += (((long)headerWord[0] & 0xFF) << 8);
                    w += (((long)headerWord[1] & 0xFF));
                    h += (((long)headerWord[2] & 0xFF) << 8);
                    h += (((long)headerWord[3] & 0xFF));
                    frameBuffer = new byte[4 * w * h];
                    headerFill = 0;
                    haveDimensions = true;
                    continue;
                } else if(haveDimensions) {
                    int r = inflater.inflate(frameBuffer, headerFill, frameBuffer.length - headerFill);
                    if(r > 0)
                        headerFill += r;
                    if(headerFill == 4 * w * h) {
                        //Complete frame.
                        frameNum++;

                        haveTime = false;
                        haveDimensions = false;
                        headerFill = 0;
                        ByteBuffer frameB = ByteBuffer.wrap(frameBuffer);
                        frameB.order(ByteOrder.BIG_ENDIAN);
                        IntBuffer frameI = frameB.asIntBuffer();
                        int[] frameData = new int[w * h];
                        frameI.get(frameData);

                        if(saver != null)
                            saver.savePNG(frameData, w, h);
                        if(timingFile != null)
                            timingFile.println(time + " " + saver.lastPNGName());
                        System.out.println("Read frame #" + frameNum + ": time=" + time + " w=" + w + " h=" + h + ".");
                    }
                }
            } catch(Exception e) {
                errorDialog(e, "Stream uncompression / frame save error", null, "Quit");
                return;
            }

            if(inflater.finished()) {
                int overflow = inflater.getRemaining();
                inflater.reset();
                inflater.setInput(iBuffer, iBuffer.length - overflow, overflow);
                justReset = true;
                resets++;
            }
        }
    }
}
