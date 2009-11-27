#include "frame.h"
#include "stdio.h"
#include <string.h>
#include <stdlib.h>

void letterbox_frame(struct frame* n, struct frame* o)
{
	unsigned scalefactor;
	unsigned src_left;
	unsigned dst_left;
	unsigned src_copyw;
	unsigned src_top;
	unsigned dst_top;
	unsigned src_copyh;

	scalefactor = n->f_width / o->f_width;
	if(scalefactor > n->f_height / o->f_height)
		scalefactor = n->f_height / o->f_height;

	if(scalefactor == 0) {
		src_left = o->f_width / 2 - n->f_width / 2;
		src_top = o->f_height / 2 - n->f_height / 2;
		dst_left = dst_top = 0;
		src_copyw = n->f_width;
		src_copyh = n->f_height;
		scalefactor = 1;
	} else {
		src_left = src_top = 0;
		dst_left = n->f_width / 2 - scalefactor * o->f_width / 2;
		dst_top = n->f_height / 2 - scalefactor * o->f_height / 2;
		src_copyw = o->f_width;
		src_copyh = o->f_height;
	}

/*
	fprintf(stderr, "Resize %u*%u -> %u*%u: src offset = %u*%u, dst offset = %u*%u "
		"scale=%u.\n", o->f_width, o->f_height, n->f_width, n->f_height, src_left,
		src_top, dst_left, dst_top, scalefactor);
*/
	for(unsigned y = 0; y < src_copyh; y++) {
		for(unsigned i = 0; i < scalefactor; i++) {
			unsigned dsty = y * scalefactor + i + dst_top;
			for(unsigned x = 0; x < src_copyw; x++) {
				int px = o->f_framedata[(y + src_top) * o->f_width + (x + src_left)];
				for(unsigned j = 0; j < scalefactor; j++) {
					unsigned dstx = x * scalefactor + j + dst_left;
					n->f_framedata[dsty * n->f_width + dstx] = px;
				}
			}
		}
	}
}

int main(int argc, char** argv)
{
	unsigned long width, height;
	char* tmp;

	if(argc < 5) {
		fprintf(stderr, "usage: %s <in> <out> <width> <height>\n", argv[0]);
		return 1;
	}
	struct frame_input_stream* in = fis_open(argv[1]);
	struct frame_output_stream* out = fos_open(argv[2]);
	struct frame* frame;
	int num = 1;

	width = strtoul(argv[3], &tmp, 10);
	if(*tmp) {
		fprintf(stderr, "Bad width %s.\n", argv[3]);
		return 1;
	}
	height = strtoul(argv[4], &tmp, 10);
	if(*tmp) {
		fprintf(stderr, "Bad height %s.\n", argv[4]);
		return 1;
	}

	while(1) {
		struct frame* newframe;
		frame = fis_next_frame(in);
		if(!frame)
			break;
		newframe = frame_create(width, height);
		newframe->f_timeseq = frame->f_timeseq;

		letterbox_frame(newframe, frame);

		fos_save_frame(out, newframe);
		frame_release(frame);
		frame_release(newframe);
	}

	fis_close(in);
	fos_close(out);
	return 0;
}
