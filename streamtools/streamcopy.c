#include "frame.h"
#include "stdio.h"

int main(int argc, char** argv)
{
	if(argc < 3) {
		fprintf(stderr, "usage: %s <in> <out>\n", argv[0]);
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
