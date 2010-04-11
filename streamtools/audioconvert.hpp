#ifndef _audioconvert__hpp__included__
#define _audioconvert__hpp__included__

#include <stdint.h>
#include <cstdio>

struct converter_parameters
{
	void* opaque;
	FILE* in;
	FILE* (*next_out)(void* opaque);
	int input_type;
	int output_type;
	uint32_t output_rate;
	uint64_t output_max;
};

#define INPUT_TYPE_PCM 0
#define INPUT_TYPE_FM 1
#define OUTPUT_TYPE_RAW 0
#define OUTPUT_TYPE_WAV 1
#define OUTPUT_MAX_UNLIMITED 0xFFFFFFFFFFFFFFFFULL

void audioconvert(struct converter_parameters* params);

#endif