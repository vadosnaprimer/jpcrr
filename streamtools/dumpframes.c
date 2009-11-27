#include "frame.h"
#include "stdio.h"

int main(int argc, char** argv)
{
	if(argc < 2) {
		fprintf(stderr, "usage: %s <filename>\n", argv[0]);
		fprintf(stderr, "Dump header information about each frame in stream read from <filename>.\n");
		return 1;
	}
	struct frame_input_stream* in = fis_open(argv[1]);
	struct frame* frame;
	int num = 1;

	while((frame = fis_next_frame(in))) {
		printf("Frame #%i: %u*%u, timeseq = %llu.\n",
			num, (unsigned)frame->f_width, (unsigned)frame->f_height,
			frame->f_timeseq);
		num++;
		frame_release(frame);
	}

	fis_close(in);
}
