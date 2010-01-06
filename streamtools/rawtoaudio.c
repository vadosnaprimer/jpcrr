#include <stdio.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>

#define BLOCKSAMPLES 1024
#define SAMPLESIZE 8
#define OUTSAMPLESIZE 4

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
	int rate;
	unsigned subsample = 0;
	uint64_t seconds = 0;
	int eofd = 0;
	int r;

	if(argc != 4) {
		fprintf(stderr, "Syntax: %s <in> <out> <samplerate>\n", argv[0]);
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

	while(1) {
		if(!eofd && inbuf_usage < BLOCKSAMPLES) {
			r = fread(inbuf + inbuf_usage * SAMPLESIZE, SAMPLESIZE, BLOCKSAMPLES - inbuf_usage, in);
			if(r < BLOCKSAMPLES - inbuf_usage)
				eofd = 1;
			inbuf_usage += (unsigned)r;
		}
		//Invariant: eofd || inbuf_usage == BLOCKSAMPLES.
		if(outbuf_usage == BLOCKSAMPLES) {
			r = fwrite(outbuf, OUTSAMPLESIZE, BLOCKSAMPLES, out);
			if(r < BLOCKSAMPLES) {
				fprintf(stderr, "Can't write to %s.\n", argv[2]);
				return 1;
			}
			outbuf_usage = 0;
		}
		//Invariant: outbuf_usage <  BLOCKSAMPLES.
		if(eofd && inbuf_usage == 0) {
			short active_xleft = active_left - 32768;
			short active_xright = active_right - 32768;
			outbuf[outbuf_usage * OUTSAMPLESIZE + 0] = (unsigned char)(active_xleft >> 8);
			outbuf[outbuf_usage * OUTSAMPLESIZE + 1] = (unsigned char)active_xleft;
			outbuf[outbuf_usage * OUTSAMPLESIZE + 2] = (unsigned char)(active_xright >> 8);
			outbuf[outbuf_usage * OUTSAMPLESIZE + 3] = (unsigned char)active_xright;
			outbuf_usage++;
			break;
		}

		unsigned long delta = ((unsigned long)inbuf[0] << 24) | ((unsigned long)inbuf[1] << 16) |
			((unsigned long)inbuf[2] << 8) | (unsigned long)inbuf[3];
		if(input_time + delta > output_time) {
			short active_xleft = active_left - 32768;
			short active_xright = active_right - 32768;
			outbuf[outbuf_usage * OUTSAMPLESIZE + 0] = (unsigned char)active_xleft;
			outbuf[outbuf_usage * OUTSAMPLESIZE + 1] = (unsigned char)(active_xleft >> 8);
			outbuf[outbuf_usage * OUTSAMPLESIZE + 2] = (unsigned char)active_xright;
			outbuf[outbuf_usage * OUTSAMPLESIZE + 3] = (unsigned char)(active_xright >> 8);
			outbuf_usage++;
			subsample++;
			if(subsample == (unsigned)rate) {
				subsample = 0;
				seconds++;
			}
			output_time = seconds * 1000000000 + (1000000000ULL * subsample) / rate;
		} else {
			active_left = ((unsigned short)inbuf[4] << 8) | (unsigned short)inbuf[5];
			active_right = ((unsigned short)inbuf[6] << 8) | (unsigned short)inbuf[7];
			input_time = input_time + delta;
			inbuf_usage--;
			memmove(inbuf, inbuf + SAMPLESIZE, inbuf_usage * SAMPLESIZE);
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
