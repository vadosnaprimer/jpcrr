#include "audioconvert.hpp"
#include <cstdio>
#include <cstdlib>
#include <cstring>

static FILE* out_open(void* opaque)
{
	char* name = (char*)opaque;
	FILE* out = fopen(name, "wb");
	if(!out) {
		fprintf(stderr, "Error: Can't open output file '%s'.\n", name);
		exit(1);
	}
	return out;
}

int main(int argc, char** argv)
{
	struct converter_parameters params;

	params.next_out = out_open;
	params.output_type = OUTPUT_TYPE_RAW;
	params.output_max = OUTPUT_MAX_UNLIMITED;
	params.amplification = 1;


	if(argc != 5) {
		fprintf(stderr, "Syntax: %s <in> <out> <samplerate> <fm/pcm>\n", argv[0]);
		return 1;
	}

	if(!strcmp(argv[4], "pcm")) {
		params.input_type = INPUT_TYPE_PCM;
	} else if(!strcmp(argv[4], "fm")) {
		params.input_type = INPUT_TYPE_FM;
	} else {
		fprintf(stderr, "Invalid mode '%s'\n", argv[4]);
		return 1;
	}

	params.output_rate = atoi(argv[3]);
	if(params.output_rate <= 0 || params.output_rate > 1000000000) {
		fprintf(stderr, "Error: Bad rate %s\n", argv[3]);
		return 1;
	}

	params.in = fopen(argv[1], "rb");
	params.opaque = argv[2];
	if(!params.in) {
		fprintf(stderr, "Error: Can't open input %s.\n", argv[1]);
		return 1;
	}

	audioconvert(&params, NULL);
}
