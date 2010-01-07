#include <stdio.h>
#include <stdlib.h>
#include <stdint.h>

struct file
{
	FILE* f_filp;
	int f_eof;
	struct file* f_next;
};

struct file* open_files(char** names)
{
	struct file* f;

	if(!names[0])
		return NULL;

	f = malloc(sizeof(struct file));
	if(!f) {
		fprintf(stderr, "Out of memory\n");
		exit(1);
	}

	f->f_filp = fopen(names[0], "rb");
	if(!f->f_filp) {
		fprintf(stderr, "Can't open '%s'\n", names[0]);
		exit(1);
	}
	f->f_eof = 0;
	f->f_next = open_files(++names);
}

#define BLOCKSIZE 2048
#define SAMPLESIZE 2

int main(int argc, char** argv)
{
	unsigned char iobuffer[BLOCKSIZE * SAMPLESIZE];
	float mixbuffer[BLOCKSIZE];
	unsigned bufferfill = 0;
	uint64_t cut = 0;
	uint64_t total = 0;

	if(argc < 3) {
		fprintf(stderr, "Syntax: %s <outfile> <infiles>...\n", argv[0]);
		return 1;
	}

	struct file* files = open_files(argv + 2);
	FILE* out = fopen(argv[1], "wb");
	if(!out) {
		fprintf(stderr, "Can't open '%s' (for output)\n", argv[1]);
		exit(1);
	}

	while(1) {
		bufferfill = 0;
		for(unsigned i = 0; i < BLOCKSIZE; i++)
			mixbuffer[i] = 0;

		for(struct file* scan = files; scan; scan = scan->f_next) {
			for(unsigned i = 0; i < BLOCKSIZE * SAMPLESIZE; i++)
				iobuffer[i] = 0;
			if(scan->f_eof)
				break;
			int r = fread(iobuffer, SAMPLESIZE, BLOCKSIZE, scan->f_filp);
			if(r < BLOCKSIZE)
				scan->f_eof = 1;
			if(r > bufferfill)
				bufferfill = r;

			for(unsigned i = 0; i < BLOCKSIZE; i++) {
				short sample = ((unsigned short)iobuffer[i * SAMPLESIZE + 0]) |
					((unsigned short)iobuffer[i * SAMPLESIZE + 1] << 8);
				mixbuffer[i] += (float)sample;
			}
		}

		for(unsigned i = 0; i < BLOCKSIZE; i++) {
			int sample = (int)mixbuffer[i];
			if(sample < -32768) {
				sample = -32768;
				cut++;
			}
			if(sample > 32767) {
				sample = 32767;
				cut++;
			}
			unsigned short value = (unsigned short)sample;
			iobuffer[i * SAMPLESIZE + 0] = (unsigned char)sample;
			iobuffer[i * SAMPLESIZE + 1] = (unsigned char)(sample >> 8);
		}
		if(fwrite(iobuffer, SAMPLESIZE, bufferfill, out) < bufferfill) {
			fprintf(stderr, "Can't write output file.\n");
			return 1;
		}

		total += bufferfill;
		if(bufferfill < BLOCKSIZE)
			break;
	}

	if(fclose(out)) {
		fprintf(stderr, "Can't close output file.\n");
		return 1;
	}

	if(cut)
		fprintf(stderr, "Warning: %llu of %llu sample(s) truncated in value.\n", cut, total);
}
