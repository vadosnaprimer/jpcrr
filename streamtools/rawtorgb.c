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
#include <stdio.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>

struct framelist_entry
{
	unsigned long fe_first;
	unsigned long fe_last;		//0 is unbounded.
	struct framelist_entry* fe_next;
};

unsigned long read_number(const char* value, const char* name, int zero_ok, char sep)
{
	unsigned long x;
	char* end;

	x = strtoul(value, &end, 10);
	if((*end && *end != sep) || (x == 0 && !zero_ok)) {
		fprintf(stderr, "Invalid %s: '%s'\n", name, value);
		exit(1);
	}
	return x;
}

struct framelist_entry* parse_framelist(const char* list)
{
	const char* next;
	const char* split;
	struct framelist_entry* entry;
	unsigned long first, last;
	if(!list || !*list)
		return NULL;
	next = strchr(list, ',');
	if(!next)
		next = list + strlen(list);
	else
		next++;

	entry = malloc(sizeof(struct framelist_entry));
	if(!entry) {
		fprintf(stderr, "Out of memory!\n");
		exit(1);
	}

	split = strchr(list, '-');
	if(split)
		split++;

	if(split && !*split) {
		first = read_number(list, "framelist start", 0, '-');
		last = 0;
	} else if(!split || split > next)
		first = last = read_number(list, "framelist entry", 0, ',');
	else {
		first = read_number(list, "framelist start", 0, '-');
		last = read_number(split, "framelist end", 0, ',');
	}

	if(first > last && last != 0) {
		fprintf(stderr, "Bad framelist range %lu-%lu!\n", first, last);
		exit(1);
	}

	entry->fe_first = first;
	entry->fe_last = last;
	entry->fe_next = parse_framelist(next);
	return entry;
}

void dump_frame(FILE* out, unsigned width, unsigned height, struct frame* frame)
{
	static int dnum = 1;
	unsigned char* buffer = calloc(4 * width, height);
	if(!buffer) {
		fprintf(stderr, "Out of memory!\n");
		exit(1);
	}

	if(frame) {
		//FIXME: This isn't even nearest-point!
		for(unsigned y = 0; y < height; y++) {
			unsigned ysrc = y * frame->f_height / height;
			for(unsigned x = 0; x < width; x++) {
				unsigned xsrc = x * frame->f_width / width;
				uint32_t sample = frame->f_framedata[ysrc * frame->f_width + xsrc];
				buffer[y * 4 * width + 4 * x] = (unsigned char)(sample >> 16);
				buffer[y * 4 * width + 4 * x + 1] = (unsigned char)(sample >> 8);
				buffer[y * 4 * width + 4 * x + 2] = (unsigned char)(sample);
				buffer[y * 4 * width + 4 * x + 3] = 0;
			}
		}
		printf("Destination frame %i: Timeseq=%llu.\n", dnum, frame->f_timeseq);
	} else
		printf("Destination frame %i: Blank.\n", dnum);


	if(fwrite(buffer, 4 * width, height, out) < height) {
		fprintf(stderr, "Can't write frame to output file!\n");
		exit(1);
	}
	free(buffer);
	dnum++;
}

int main(int argc, char** argv)
{
	struct framelist_entry frame_default_list = {1, 0, NULL};
	struct framelist_entry* current_block = &frame_default_list;

	if(argc < 6 || argc > 7) {
		fprintf(stderr, "usage: %s <in> <out> <width> <height> <framegap> [<frames>]\n", argv[0]);
		fprintf(stderr, "Read stream from <in> and dump the raw RGBx output to <out>. The\n");
		fprintf(stderr, "dumped frames are scaled to be <width>x<height> and frame is read\n");
		fprintf(stderr, "every <framegap> ns. If <frames> is given, it lists frames to\n");
		fprintf(stderr, "include, in form '1,6,62-122,244-'. If not specified, default is\n");
		fprintf(stderr, "'1-' (dump every frame). Frame numbers are 1-based.\n");
		return 1;
	}
	struct frame_input_stream* in = fis_open(argv[1]);
	FILE* out = fopen(argv[2], "wb");
	struct frame* frame;
	struct frame* aframe = NULL;
	int num = 1;

	if(!out) {
		fprintf(stderr, "Can't open %s\n", argv[2]);
		exit(1);
	}

	unsigned long width = read_number(argv[3], "width", 0, 0);
	unsigned long height = read_number(argv[4], "height", 0, 0);
	unsigned long framegap = read_number(argv[5], "framegap", 0, 0);
	uint64_t lastdumped = 0;

	if(argc == 7)
		current_block = parse_framelist(argv[6]);

	while(1) {
		struct frame* old_aframe = aframe;
		frame = fis_next_frame(in);
		if(!frame) {
			if(aframe)
in_next_block2:
				if(!current_block || current_block->fe_first > num)
					;
				else if(current_block->fe_last + 1 == num && num > 1) {
					current_block = current_block->fe_next;
					goto in_next_block2;
				} else
					dump_frame(out, width, height, aframe);
			break;
		}

		old_aframe = aframe;
		aframe = frame;
		while(frame->f_timeseq > lastdumped + framegap) {
			//Check that frame is in list of frames to include.
in_next_block:
			if(!current_block || current_block->fe_first > num)
				;
			else if(current_block->fe_last + 1 == num && num > 1) {
				current_block = current_block->fe_next;
				goto in_next_block;
			} else {
				dump_frame(out, width, height, old_aframe);
			}
			lastdumped += framegap;
			num++;
		}
		if(old_aframe)
			frame_release(old_aframe);

	}

	if(aframe)
		frame_release(aframe);
	fis_close(in);
	if(fclose(out)) {
		fprintf(stderr, "Can't close output file!\n");
		exit(1);
	}
}
