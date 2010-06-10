#include "frame.h"
#include <stdio.h>
#include <stdlib.h>
#include <stdint.h>
#include <string.h>
#include <ctype.h>

#define MAXNAME 8192
#define MAXLINE 16384

const char* current_name = NULL;

FILE* nextfile(const char* pattern, int frame)
{
	static char prevname[MAXNAME] = {0};
	static char curname[MAXNAME] = {0};

	/* Quick and very dirty hack. */
	sprintf(curname, pattern, frame);
	current_name = curname;
	if(!strcmp(prevname, curname))
		return NULL;		/* No more. */
	strcpy(prevname, curname);
	return fopen(curname, "r");
}

uint64_t tonumeric(const char* number)
{
	const char* orig_number = number;
	uint64_t ret = 0;
	while(*number) {
		if(!isdigit((unsigned char)*number)) {
			fprintf(stderr, "Error: Bad number '%s'.\n", orig_number);
			exit(1);
		}
		if(ret * 10 / 10 != ret) {
			fprintf(stderr, "Error: Bad number '%s'.\n", orig_number);
			exit(1);
		}
		ret *= 10;
		if(ret + (*number - '0') < ret) {
			fprintf(stderr, "Error: Bad number '%s'.\n", orig_number);
			exit(1);
		}
		ret += (*number - '0');
		number++;
	}
	return ret;
}

struct frame* read_frame(FILE* in)
{
	struct frame* frame = NULL;
	int linetype = 0;
	uint64_t pixels_remaining = 1;
	uint64_t pixelindex = 0;
	unsigned subpixel = 0;
	uint32_t scale = 1;
	uint32_t subpixeldata[3];
	uint64_t last_remaining = 1;
	char linebuffer[MAXLINE];
	if(!fgets(linebuffer, MAXLINE - 1, in)) {
		fprintf(stderr, "Error: Bad input file '%s': No magic.\n", current_name);
		exit(1);
	}
	if(strcmp(linebuffer, "P3\n")) {
		fprintf(stderr, "Error: Bad input file '%s': Wrong magic.\n", current_name);
		exit(1);
	}
	while(pixels_remaining > 0) {
		if(!fgets(linebuffer, MAXLINE - 1, in)) {
			fprintf(stderr, "Error: Bad input file '%s': Can't read line.\n", current_name);
			exit(1);
		}
		if(linebuffer[0] == '#') {
			/* Comment. */
			continue;
		}
		if(linetype == 0) {
			/* Dimensions. */
			unsigned int w = 0, h = 0;
			sscanf(linebuffer, "%u %u", &w, &h);
			if(w == 0 || h == 0) {
				fprintf(stderr, "Error: Bad input file '%s': Bad dimensions line '%s'.\n", current_name, linebuffer);
				exit(1);
			}
			last_remaining = pixels_remaining = (uint64_t)w * h;
			frame = frame_create(w, h);
			if(!frame) {
				fprintf(stderr, "Error: Bad input file '%s': Can't create %zu*%zu frame.\n", current_name, (size_t)w,
					(size_t)h);
				exit(1);
			}
			printf("Processing %s (%zu*%zu).\n", current_name, (size_t)w, (size_t)h);
			linetype = 1;
		} else if(linetype == 1) {
			/* Scale. */
			unsigned scaletmp = 0;
			sscanf(linebuffer, "%u", &scaletmp);
			if(scaletmp == 0) {
				fprintf(stderr, "Error: Bad input file '%s': Bad scale line '%s'.\n", current_name, linebuffer);
				exit(1);
			}
			scale = scaletmp;
			linetype = 2;
		} else if(linetype == 2) {
			/* Data. */
			char* linebuffer2 = linebuffer;
			while(*linebuffer2) {
				unsigned parsepixel;
				if(isspace((unsigned char)*linebuffer2)) {
					linebuffer2++;
					continue;
				}
				unsigned x = strtoul(linebuffer2, &linebuffer2, 10);
				parsepixel = 255 * x / scale;
				subpixeldata[subpixel++] = parsepixel;
				if(subpixel == 3 && pixels_remaining > 0) {
					frame->f_framedata[pixelindex++] = (subpixeldata[0] << 16) | (subpixeldata[1] << 8) | subpixeldata[2];
					pixels_remaining--;
					subpixel = 0;
				}
			}
		}
		if(last_remaining % 10000 < 5000 && pixels_remaining % 10000 > 5000) {
			printf("\e[1G%llu pixels left.", (unsigned long long)pixels_remaining);
			fflush(stdout);
		}
	}
	printf("\e[1G");
	fflush(stdout);

	return frame;
}

int main(int argc, char** argv)
{
	const char* input_pattern;
	uint64_t framegap;
	uint64_t length;
	const char* output_pattern;
	uint32_t maxframes;
	uint32_t i;
	struct frame_output_stream* out;
	char tmp[MAXNAME];
	struct frame* frame = NULL;
	FILE* handle;
	unsigned char bufl[8] = {0};

	if(argc != 5) {
		fprintf(stderr, "Syntax: %s <input-pattern> <framegap> <length> <output-prefix>\n", argv[0]);
		exit(1);
	}
	input_pattern = argv[1];
	framegap = tonumeric(argv[2]);
	length = tonumeric(argv[3]);
	output_pattern = argv[4];
	maxframes = (length + framegap - 1) / framegap;

	sprintf(tmp, "%s.video.dump", output_pattern);
	out = fos_open(tmp);
	if(!out) {
		fprintf(stderr, "Error: Can't open output stream.\n");
		exit(1);
	}


	for(i = 0; i < maxframes; i++) {
		handle = nextfile(input_pattern, i + 1);
		if(!handle)
			continue;

		frame = read_frame(handle);
		fclose(handle);
		frame->f_timeseq = i * framegap;
		fos_save_frame(out, frame);
		frame_release(frame);
	}

	frame = frame_create(1, 1);
	frame->f_timeseq = length;
	frame->f_framedata[0] = 0;
	fos_save_frame(out, frame);
	fos_close(out);

	sprintf(tmp, "%s.audio.dump", output_pattern);
	handle = fopen(tmp, "wb");
	if(!handle) {
		fprintf(stderr, "Error: Can't open audio output stream.\n");
		exit(1);
	}
	while(length >= 0xFFFFFFFFULL) {
		unsigned char buf[4] = {0xFF, 0xFF, 0xFF, 0xFF};
		if(fwrite(buf, 4, 1, handle) < 1) {
			fprintf(stderr, "Error: Can't write audio output stream.\n");
			exit(1);
		}
		length -= 0xFFFFFFFFULL;
	}

	bufl[0] = (unsigned char)((length >> 24) & 0xFF);
	bufl[1] = (unsigned char)((length >> 16) & 0xFF);
	bufl[2] = (unsigned char)((length >> 8) & 0xFF);
	bufl[3] = (unsigned char)(length & 0xFF);

	if(fwrite(bufl, 8, 1, handle) < 1) {
		fprintf(stderr, "Error: Can't write audio output stream.\n");
		exit(1);
	}

	return 0;
}
