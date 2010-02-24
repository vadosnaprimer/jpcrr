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
#include <math.h>
#include <assert.h>

#define MAXCOEFFICIENTS 256

typedef signed long long position_t;

enum algorithm
{
	ALGO_AVERAGE,
	ALGO_LANCZOS1,
	ALGO_LANCZOS2,
	ALGO_LANCZOS3,
	ALGO_LANCZOS4,
	ALGO_LANCZOS5
};

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

#define LANZCOS_A 2

void compute_coefficients_lanczos(float* coeffs, position_t num, position_t denum, position_t width,
	unsigned* count, unsigned* base, unsigned a)
{
	signed lowbound, highbound, scan;

	if(num % denum == 0) {
		coeffs[0] = 1;
		*count = 1;
		*base = num / denum;
		return;
	}

	if(a == 0) {
		fprintf(stderr, "Error: Parameter alpha must be positive in lanczos resizer.",
			2 * a + 1, MAXCOEFFICIENTS);
		exit(1);
	}

	if(2 * a + 1 <= a) {
		fprintf(stderr, "Error: Parameter alpha way too large in lanczos resizer.",
			2 * a + 1, MAXCOEFFICIENTS);
		exit(1);
	}

	if(2 * a + 1 > MAXCOEFFICIENTS) {
		fprintf(stderr, "Error: Conversion would require %u coefficients, but only up to %u coefficients are supported.",
			2 * a + 1, MAXCOEFFICIENTS);
		exit(1);
	}

	lowbound = num - a * denum;
	highbound = num + a * denum;
	if(lowbound < 0)
		lowbound = 0;
	if(highbound > width * denum)
		highbound = width * denum - denum;

	scan = lowbound + (denum - lowbound % denum) % denum;
	*base = scan / denum;
	*count = 0;
	while(scan <= highbound) {
		float difference = (float)(num - scan) / denum;
		if(num == scan)
			coeffs[(*count)++] = 1;
		else
			coeffs[(*count)++] = a * sin(M_PI*difference) * sin(M_PI*difference/2) /
				(M_PI * M_PI * difference * difference);

		scan = scan + denum;
	}
}

void compute_coefficients_average(float* coeffs, position_t num, position_t denum, position_t width,
	unsigned* count, unsigned* base)
{
	signed lowbound, highbound, scan;

	lowbound = num;
	highbound = num + width;
	scan = lowbound - lowbound % denum;

	if((width + denum - 1) / denum > MAXCOEFFICIENTS) {
		fprintf(stderr, "Error: Conversion would require %lli coefficients, but only up to %u coefficients are supported.",
			(width + denum - 1) / denum, MAXCOEFFICIENTS);
		exit(1);
	}

	*base = scan / denum;
	*coeffs = (scan + denum) - lowbound;
	*count = 1;
	scan = scan + denum;
	while(scan < highbound) {
		if(scan + denum > highbound)
			coeffs[(*count)++] = highbound - scan;
		else
			coeffs[(*count)++] = denum;

		scan = scan + denum;
	}
}

// The coodinate space is such that range is [0, srclength] and is given as fraction
// num / denum, where denumerator is destination length. Thus source pixel spacing
// is unity.
void compute_coefficients(float* coeffs, position_t num, position_t denum, position_t width,
	unsigned* count, unsigned* base, enum algorithm algo)
{
	float sum = 0;
	switch(algo) {
	case ALGO_AVERAGE:
		return compute_coefficients_average(coeffs, num, denum, width, count, base);
	case ALGO_LANCZOS1:
		return compute_coefficients_lanczos(coeffs, num, denum, width, count, base, 1);
	case ALGO_LANCZOS2:
		return compute_coefficients_lanczos(coeffs, num, denum, width, count, base, 2);
	case ALGO_LANCZOS3:
		return compute_coefficients_lanczos(coeffs, num, denum, width, count, base, 3);
	case ALGO_LANCZOS4:
		return compute_coefficients_lanczos(coeffs, num, denum, width, count, base, 4);
	case ALGO_LANCZOS5:
		return compute_coefficients_lanczos(coeffs, num, denum, width, count, base, 5);
	default:
		fprintf(stderr, "Error: Unknown algorithm #%i.", algo);
		exit(1);
	}

	/* Normalize the coefficients. */
	for(int i = 0; i < *count; i++)
		sum += coeffs[i];
	for(int i = 0; i < *count; i++)
		coeffs[i] /= sum;
}


//Read the frame data in src (swidth x sheight) and resize it to dest (dwidth x dheight).
void resize_frame(unsigned char* dest, unsigned dwidth, unsigned dheight, uint32_t* src, unsigned swidth,
	unsigned sheight, enum algorithm algo)
{
	float coeffs[MAXCOEFFICIENTS];
	unsigned trap = 0xCAFEFACE;
	unsigned count;
	unsigned base;
	float* interm;

	interm = calloc(3 * sizeof(float), dwidth * sheight);
	if(!interm) {
		fprintf(stderr, "Out of memory!\n");
		exit(1);
	}

	for(unsigned x = 0; x < dwidth; x++) {
		count = 0xDEADBEEF;
		base = 0xDEADBEEF;
		compute_coefficients(coeffs, (position_t)x * swidth, dwidth, swidth, &count, &base, algo);
		assert(trap == 0xCAFEFACE);
		for(unsigned y = 0; y < sheight; y++) {
			float vr = 0, vg = 0, vb = 0;
			for(unsigned k = 0; k < count; k++) {
				uint32_t sample = src[y * swidth + k + base];
				vr += coeffs[k] * ((sample >> 16) & 0xFF);
				vg += coeffs[k] * ((sample >> 8) & 0xFF);
				vb += coeffs[k] * (sample & 0xFF);
			}
			interm[y * 3 * dwidth + 3 * x + 0] = vr;
			interm[y * 3 * dwidth + 3 * x + 1] = vg;
			interm[y * 3 * dwidth + 3 * x + 2] = vb;
		}
	}

	for(unsigned y = 0; y < dheight; y++) {
		count = 0;
		base = 0;
		compute_coefficients(coeffs, (position_t)y * sheight, dheight, sheight, &count, &base, algo);
		assert(trap == 0xCAFEFACE);
		for(unsigned x = 0; x < dwidth; x++) {
			float vr = 0, vg = 0, vb = 0;
			for(unsigned k = 0; k < count; k++) {
				vr += coeffs[k] * interm[(base + k) * 3 * dwidth + x * 3 + 0];
				vg += coeffs[k] * interm[(base + k) * 3 * dwidth + x * 3 + 1];
				vb += coeffs[k] * interm[(base + k) * 3 * dwidth + x * 3 + 2];
			}
			int wr = (int)vr;
			int wg = (int)vg;
			int wb = (int)vb;
			wr = (wr < 0) ? 0 : ((wr > 255) ? 255 : wr);
			wg = (wg < 0) ? 0 : ((wg > 255) ? 255 : wg);
			wb = (wb < 0) ? 0 : ((wb > 255) ? 255 : wb);

			dest[y * 4 * dwidth + 4 * x] = (unsigned char)wr;
			dest[y * 4 * dwidth + 4 * x + 1] = (unsigned char)wg;
			dest[y * 4 * dwidth + 4 * x + 2] = (unsigned char)wb;
			dest[y * 4 * dwidth + 4 * x + 3] = 0;
		}
	}

	free(interm);
}

void dump_frame(FILE* out, unsigned width, unsigned height, struct frame* frame, enum algorithm algo)
{
	static int dnum = 1;
	unsigned char* buffer = calloc(4 * width, height);
	if(!buffer) {
		fprintf(stderr, "Out of memory!\n");
		exit(1);
	}

	if(frame) {
		if(width != frame->f_width || height != frame->f_height)
			resize_frame(buffer, width, height, frame->f_framedata, frame->f_width, frame->f_height,
				algo);
		else {
			int k;
			for(k = 0; k < width * height; k++) {
				buffer[4 * k + 0] = (unsigned char)(frame->f_framedata[k] >> 16);
				buffer[4 * k + 1] = (unsigned char)(frame->f_framedata[k] >> 8);
				buffer[4 * k + 2] = (unsigned char)(frame->f_framedata[k]);
				buffer[4 * k + 3] = (unsigned char)0;
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
	enum algorithm algo;

	if(argc < 7 || argc > 8) {
		fprintf(stderr, "usage: %s <in> <out> <algo> <width> <height> <framegap> [<frames>]\n", argv[0]);
		fprintf(stderr, "Read stream from <in> and dump the raw RGBx output to <out>. The\n");
		fprintf(stderr, "dumped frames are scaled to be <width>x<height> and frame is read\n");
		fprintf(stderr, "every <framegap> ns. If <frames> is given, it lists frames to\n");
		fprintf(stderr, "include, in form '1,6,62-122,244-'. If not specified, default is\n");
		fprintf(stderr, "'1-' (dump every frame). Frame numbers are 1-based.\n");
		fprintf(stderr, "<algo> gives the algorithm used. Following are supported:\n");
		fprintf(stderr, "average: Weighted average of covering pixels\n");
		fprintf(stderr, "lanczos1: Lanczos with alpha = 1\n");
		fprintf(stderr, "lanczos2: Lanczos with alpha = 2\n");
		fprintf(stderr, "lanczos3: Lanczos with alpha = 3\n");
		fprintf(stderr, "lanczos4: Lanczos with alpha = 4\n");
		fprintf(stderr, "lanczos5: Lanczos with alpha = 5\n");
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

	unsigned long width = read_number(argv[4], "width", 0, 0);
	unsigned long height = read_number(argv[5], "height", 0, 0);
	unsigned long framegap = read_number(argv[6], "framegap", 0, 0);
	uint64_t lastdumped = 0;

	if(!strcasecmp(argv[3], "average"))
		algo = ALGO_AVERAGE;
	else if(!strcasecmp(argv[3], "lanczos1"))
		algo = ALGO_LANCZOS1;
	else if(!strcasecmp(argv[3], "lanczos2"))
		algo = ALGO_LANCZOS2;
	else if(!strcasecmp(argv[3], "lanczos3"))
		algo = ALGO_LANCZOS3;
	else if(!strcasecmp(argv[3], "lanczos4"))
		algo = ALGO_LANCZOS4;
	else if(!strcasecmp(argv[3], "lanczos5"))
		algo = ALGO_LANCZOS5;
	else {
		fprintf(stderr, "Error: Unknown resize algorithm '%s'\n", argv[3]);
		exit(1);
	}

	if(argc == 8)
		current_block = parse_framelist(argv[7]);

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
					dump_frame(out, width, height, aframe, algo);
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
				dump_frame(out, width, height, old_aframe, algo);
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
