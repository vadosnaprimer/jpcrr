/*
    JPC-RR: A x86 PC Hardware Emulator
    Release 1

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


#include "frame.h"
#include "stdio.h"

int main(int argc, char** argv)
{
	if(argc < 3) {
		fprintf(stderr, "usage: %s <in> <out>\n", argv[0]);
		fprintf(stderr, "Read stream from <in> and dump the frames unmodified to <out>.\n");
		return 1;
	}
	struct frame_input_stream* in = fis_open(argv[1]);
	struct frame_output_stream* out = fos_open(argv[2]);
	struct frame* frame;
	int num = 1;

	while(1) {
		frame = fis_next_frame(in);
		if(!frame)
			break;
		fos_save_frame(out, frame);
		frame_release(frame);
	}

	fis_close(in);
	fos_close(out);
}
