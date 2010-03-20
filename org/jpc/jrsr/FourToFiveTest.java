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

package org.jpc.jrsr;

import java.io.*;

public class FourToFiveTest
{
    public static void main(String[] args) throws Exception
    {
        String src = args[1];
        String dest = args[2];
        InputStream in;
        OutputStream out;
        JRSRArchiveWriter arch = null;
        byte[] transferBuffer = new byte[4096];
        int transferSize = 0;
        if(args[0].equals("E")) {
            in = new FileInputStream(src);
            out = new FourToFiveEncoder(new FileOutputStream(dest));
        } else if(args[0].equals("D")) {
            out = new FileOutputStream(dest);
            in = new FourToFiveDecoder(new FileInputStream(src));
        } else if(args[0].equals("A")) {
            in = new FileInputStream(src);
            arch = new JRSRArchiveWriter(dest);
            out = new FourToFiveEncoder(arch.addMember("fooX"));
        } else if(args[0].equals("B")) {
            JRSRArchiveReader rArch = new JRSRArchiveReader(src);
            in = rArch.readMember(dest);
            out = new FileOutputStream(dest);
        } else if(args[0].equals("C")) {
            JRSRArchiveReader rArch = new JRSRArchiveReader(src);
            in = new java.util.zip.InflaterInputStream(new FourToFiveDecoder(rArch.readMember(dest)));
            out = new FileOutputStream(dest);
        } else if(args[0].equals("L")) {
            in = new FileInputStream(src);
            UTFInputLineStream lines = new UTFInputLineStream(in);
            String x = lines.readLine();
            while(x != null) {
                System.err.println("Line: \"" + x + "\".");
                x = lines.readLine();
            }
            return;
        } else {
            System.err.println("Bad mode: \"" + args[0] + "\".");
            return;
        }
        while(transferSize >= 0) {
            transferSize = in.read(transferBuffer);
            if(transferSize < 0)
                break;
            out.write(transferBuffer, 0, transferSize);
        }
        out.close();
        in.close();
        if(arch != null) {
            out = arch.addMember("bar");
            out.close();
            arch.close();
        }
    }
}
