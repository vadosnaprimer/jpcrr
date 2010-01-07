#include <stdio.h>
#include <stdlib.h>
#include <stdint.h>
#include <string.h>

#define BLOCKSIZE 2048
#define SAMPLESIZE 2
#define CHANNELS 2

typedef float* float_ptr;

void* xmalloc(size_t size)
{
	void* x = malloc(size);
	if(!x) {
		fprintf(stderr, "Out of memory!\n");
		exit(1);
	}
	return x;
}

double tonumber(const char* value)
{
	char* end;
	double x = strtod(value, &end);
	if(*end) {
		fprintf(stderr, "Invalid number %s!\n", value);
		exit(1);
	}
	return x;
}

int main(int argc, char** argv)
{
	unsigned char iobuffer[BLOCKSIZE * SAMPLESIZE];
	float filterbuffer[BLOCKSIZE];
	unsigned bufferfill = 0;
	uint64_t cut = 0;
	uint64_t total = 0;
	uint64_t written = 0;
	int eof = 0;

	float_ptr past_input[CHANNELS];
	float_ptr past_output[CHANNELS];
	float* input_coeffs;
	float* output_coeffs;
	unsigned input_size;
	unsigned output_size;
	unsigned input_current = 0;
	unsigned input_lag = 0;

	if(argc < 4) {
		fprintf(stderr, "Syntax: %s <in> <out> <coefficients>\n", argv[0]);
		fprintf(stderr, "<in> and <out> are raw audio data (not audio dumps).\n");
		fprintf(stderr, "Coefficient prefixed with '#' is b0 (if none is marked so,\n");
		fprintf(stderr, "Then the first coefficient for numerator is b0 (the coefficients\n");
		fprintf(stderr, "are in order of increasing index). Coefficients prefixed with '/'\n");
		fprintf(stderr, "are sent to denominator (first as a0).\n");
		return 1;
	}

	input_coeffs = xmalloc(argc * sizeof(float));
	output_coeffs = xmalloc((argc + 1) * sizeof(float));
	for(unsigned j = 0; j < CHANNELS; j++)
		past_input[j] = xmalloc(argc * sizeof(float));;
	for(unsigned j = 0; j < CHANNELS; j++)
		past_output[j] = xmalloc(argc * sizeof(float));;

	input_size = 0;
	output_size = 0;

	for(int i = 3; i < argc; i++) {
		if(argv[i][0] == '#') {
			input_lag = input_size;
			input_coeffs[input_size++] = tonumber(argv[i] + 1);
		} else if(argv[i][0] == '/') {
			output_coeffs[output_size++] = tonumber(argv[i] + 1);
		} else
			input_coeffs[input_size++] = tonumber(argv[i]);
	}

	if(output_size < 1)
		output_coeffs[output_size++] = 1;


	FILE* in = fopen(argv[1], "rb");
	if(!in) {
		fprintf(stderr, "Can't open '%s' (for input)\n", argv[1]);
		exit(1);
	}
	FILE* out = fopen(argv[2], "wb");
	if(!out) {
		fprintf(stderr, "Can't open '%s' (for output)\n", argv[2]);
		exit(1);
	}

	//Prefill the previous input and output buffers.
	for(unsigned i = 0; i < input_size; i++)
		for(unsigned j = 0; j < CHANNELS; j++)
			past_input[j][i] = 0;
	for(unsigned i = 0; i < output_size; i++)
		for(unsigned j = 0; j < CHANNELS; j++)
			past_output[j][i] = 0;

	while(1) {
		//Read the sample data.
		int r;
		bufferfill = 0;
		for(unsigned i = 0; i < BLOCKSIZE; i++)
			filterbuffer[i] = 0;

		for(unsigned i = 0; i < BLOCKSIZE * SAMPLESIZE; i++)
			iobuffer[i] = 0;

		if(eof)
			r = 0;
		else
			r = fread(iobuffer, SAMPLESIZE, BLOCKSIZE, in);
		bufferfill = r;
		total += bufferfill;
		if(r < BLOCKSIZE) {
			eof = 1;
		}

		for(unsigned i = 0; i < BLOCKSIZE; i++) {
			short sample = ((unsigned short)iobuffer[i * SAMPLESIZE + 0]) |
				((unsigned short)iobuffer[i * SAMPLESIZE + 1] << 8);
			filterbuffer[i] += (float)sample;
		}

		//Filter the data.
		bufferfill = 0;
		for(unsigned i = 0; i < BLOCKSIZE; i++) {
			float sample = 0;
			unsigned old_lag = input_lag;
			int chan = i % CHANNELS;
			if(input_size > 1)
				memmove(past_input[chan] + 1, past_input[chan], (input_size - 1) *
					sizeof(float));
			past_input[chan][0] = filterbuffer[i];
			if(chan == CHANNELS - 1 && old_lag < input_current)
				input_lag++;
			if(old_lag < input_current)
				continue;

			for(unsigned j = 0; j < input_size; j++)
				sample += input_coeffs[j] * past_input[chan][j];
			for(unsigned j = 1; j < output_size; j++)
				sample -= output_coeffs[j] * past_output[chan][j - 1];
			sample /= output_coeffs[0];

			filterbuffer[bufferfill++] = sample;
			if(output_size > 1)
				memmove(past_output[chan] + 1, past_output[chan], (output_size - 1) *
					sizeof(float));
			past_output[chan][0] = sample;
		}

		if(written + bufferfill > total)
			bufferfill = total - written;

		//Output the data.
		for(unsigned i = 0; i < BLOCKSIZE; i++) {
			int sample = (int)filterbuffer[i];
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
		written += bufferfill;

		if(eof && total == written)
			break;
	}

	if(fclose(out)) {
		fprintf(stderr, "Can't close output file.\n");
		return 1;
	}

	if(cut)
		fprintf(stderr, "Warning: %llu of %llu sample(s) truncated in value.\n", cut, total);
}
