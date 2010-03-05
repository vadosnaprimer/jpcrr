#include <stdio.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include "opl.h"

#define BLOCKSAMPLES 1024
#define SAMPLESIZE 8
#define OUTSAMPLESIZE 4

#define INLINE inline

short average_s(int64_t accumulator, uint64_t base, uint64_t bound)
{
	if(bound <= base)
		return 0;
	return (short)(accumulator / (int64_t)(bound - base));
}

int main(int argc, char** argv)
{
	unsigned char inbuf[SAMPLESIZE * BLOCKSAMPLES];
	unsigned char outbuf[OUTSAMPLESIZE * BLOCKSAMPLES];
	unsigned inbuf_usage = 0;
	unsigned outbuf_usage = 0;
	uint64_t output_time = 0;
	uint64_t input_time = 0;
	unsigned short active_left = 32768;
	unsigned short active_right = 32768;
	int64_t left_accumulator = 0;
	int64_t right_accumulator = 0;
	uint64_t last_accumulator_update = 0;
	uint64_t accumulator_base = 0;
	int rate;
	int load_delta;
	int fm_mode;
	float left_volume = 1.0;
	float right_volume = 1.0;
	unsigned subsample = 0;
	uint64_t seconds = 0;
	int eofd = 0;
	int r;

	if(argc != 5) {
		fprintf(stderr, "Syntax: %s <in> <out> <samplerate> <fm/pcm>\n", argv[0]);
		return 1;
	}

	if(!strcmp(argv[4], "pcm")) {
		fm_mode = 0;
	} else if(!strcmp(argv[4], "fm")) {
		fm_mode = 1;
	} else {
		fprintf(stderr, "Invalid mode '%s'\n", argv[4]);
		return 1;
	}

	rate = atoi(argv[3]);
	if(rate <= 0 || rate > 1000000000) {
		fprintf(stderr, "Bad rate %s\n", argv[2]);
		return 1;
	}

	FILE* in = fopen(argv[1], "rb");
	FILE* out = fopen(argv[2], "wb");
	if(!in) {
		fprintf(stderr, "Can't open %s.\n", argv[1]);
		return 1;
	}
	if(!out) {
		fprintf(stderr, "Can't open %s.\n", argv[2]);
		return 1;
	}

	if(fm_mode) {
		adlib_init(rate);
	}

	while(1) {
		if(!eofd && inbuf_usage < BLOCKSAMPLES * SAMPLESIZE / 2) {
			r = fread(inbuf + inbuf_usage, 1, BLOCKSAMPLES * SAMPLESIZE - inbuf_usage, in);
			if(r < BLOCKSAMPLES * SAMPLESIZE - inbuf_usage)
				eofd = 1;
			inbuf_usage += (unsigned)r;
		}
		//Invariant: eofd || inbuf_usage == BLOCKSAMPLES.
		if(outbuf_usage == BLOCKSAMPLES) {
//			fprintf(stderr, "Dumping block. Input time %llu, Output time %llu.\n", input_time, output_time);
			r = fwrite(outbuf, OUTSAMPLESIZE, BLOCKSAMPLES, out);
			if(r < BLOCKSAMPLES) {
				fprintf(stderr, "Can't write to %s.\n", argv[2]);
				return 1;
			}
			outbuf_usage = 0;
		}
		//Invariant: outbuf_usage <  BLOCKSAMPLES.
		unsigned long delta = 0;
		load_delta = 1;
		if(eofd && inbuf_usage < SAMPLESIZE)
			load_delta = 0;

		if(load_delta)
			delta = ((unsigned long)inbuf[0] << 24) | ((unsigned long)inbuf[1] << 16) |
				((unsigned long)inbuf[2] << 8) | (unsigned long)inbuf[3];
		if(input_time + delta > output_time || !load_delta) {
			short active_xleft, active_xright;
			if(fm_mode) {
				short samples[2];
				adlib_getsample(samples, 1);
				active_xleft = samples[0];
				active_xright = samples[1];
			} else {
				left_accumulator += (int64_t)(output_time - last_accumulator_update) * (short)active_left;
				right_accumulator += (int64_t)(output_time - last_accumulator_update) * (short)active_right;
				last_accumulator_update = output_time;
				active_xleft = average_s(left_accumulator, accumulator_base, output_time);
				active_xright = average_s(right_accumulator, accumulator_base, output_time);
			}

			if(fm_mode) {
				float xl = active_xleft * left_volume;
				float xr = active_xright * right_volume;
				if(xl < -32768)
					active_xleft = -32768;
				else if(xl > 32767)
					active_xleft = 32767;
				else
					active_xleft = (short)xl;
				if(xr < -32768)
					active_xright = -32768;
				else if(xr > 32767)
					active_xright = 32767;
				else
					active_xright = (short)xl;
			}

			outbuf[outbuf_usage * OUTSAMPLESIZE + 0] = (unsigned char)active_xleft;
			outbuf[outbuf_usage * OUTSAMPLESIZE + 1] = (unsigned char)(active_xleft >> 8);
			outbuf[outbuf_usage * OUTSAMPLESIZE + 2] = (unsigned char)active_xright;
			outbuf[outbuf_usage * OUTSAMPLESIZE + 3] = (unsigned char)(active_xright >> 8);
			left_accumulator = 0;
			right_accumulator = 0;
			accumulator_base = output_time;
			outbuf_usage++;
			subsample++;
			if(subsample == (unsigned)rate) {
				subsample = 0;
				seconds++;
			}
			output_time = seconds * 1000000000 + (1000000000ULL * subsample) / rate;
			if(!load_delta)
				break;
		} else if(delta == 0xFFFFFFFFUL) {
			input_time = input_time + delta;
			inbuf_usage -= 4;
			memmove(inbuf, inbuf + 4, inbuf_usage);
		} else {
			input_time = input_time + delta;
			if(!fm_mode) {
				left_accumulator += (int64_t)(input_time  - last_accumulator_update) * (short)active_left;
				right_accumulator += (int64_t)(input_time - last_accumulator_update) * (short)active_right;
				last_accumulator_update = input_time;
			}
			active_left = ((unsigned short)inbuf[4] << 8) | (unsigned short)inbuf[5];
			active_right = ((unsigned short)inbuf[6] << 8) | (unsigned short)inbuf[7];
			inbuf_usage -= SAMPLESIZE;
			memmove(inbuf, inbuf + SAMPLESIZE, inbuf_usage);
			if(fm_mode) {
				if(active_left & 0x800) {
					left_volume = (float)(active_left & 0x7FF) / 255;
					right_volume = (float)(active_right & 0x7FF) / 255;
					fprintf(stderr, "Volume now %f:%f.\n", (double)left_volume, (double)right_volume);
				} else if(active_left == 512) {
					//RESET.
					int i;
					for(i = 0; i < 512; i++) {
						adlib_write(i, 0);
					}
				} else if(active_left < 512) {
					adlib_write(active_left, active_right);
				} else {
					fprintf(stderr, "Ignored unknown FM command: %04X/%04X\n",
						active_left, active_right);
				}
			}
		}
	}

	r = fwrite(outbuf, OUTSAMPLESIZE, outbuf_usage, out);
	if(r < outbuf_usage) {
		fprintf(stderr, "Can't write to %s.\n", argv[2]);
		return 1;
	}
	outbuf_usage = 0;

	fclose(in);
	if(fclose(out)) {
		fprintf(stderr, "Can't close %s.\n", argv[2]);
		return 1;
	}

	return 0;
}
