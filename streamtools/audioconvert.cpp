#include <stdio.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include "opl.h"
#include "audioconvert.hpp"

#define BLOCKSAMPLES 1024
#define SAMPLESIZE 8
#define OUTSAMPLESIZE 4

#define INLINE inline
#define RIFF_MAGIC 0x46464952
#define WAVE_MAGIC 0x45564157
#define FMT_MAGIC 0x20746d66
#define DATA_MAGIC 0x61746164

static short do_filter(std::vector<double>* old_input, std::vector<double>* old_output,
	std::vector<double>& numerator, std::vector<double>& denumerator, size_t* lag, size_t delay,
	unsigned index, double amplification, double preamp, short sample, uint64_t& clipcount)
{
	double out;
	short out2;
	double in = (double)sample * preamp;
	size_t nsize = numerator.size();
	size_t dsize = denumerator.size();

	//Save input
	if(nsize > 1)
		memmove(&old_input[index][1], &old_input[index][0], (nsize - 1) * sizeof(double));
	old_input[index][0] = in;

	size_t old_lag = lag[index];
	if(old_lag < delay)
		lag[index]++;
	if(old_lag < delay)
		return 0;	//No sample yet.

	out = 0;
	for(size_t j = 0; j < nsize; j++)
		out += numerator[j] * old_input[index][j];
	for(size_t j = 1; j < dsize; j++)
		out -= denumerator[j] * old_output[index][j];

	//Save output. The last sample needs to wind up into index 1. Also scale by denum[0].
	out /= denumerator[0];
	old_output[index][0] = out;
	if(dsize > 1)
		memmove(&old_output[index][1], &old_output[index][0], (dsize - 1) * sizeof(double));

	//Finally amplify and cast back.
	out *= amplification;
	if(out < -32768) {
		out2 = -32768;
		clipcount++;
	} else if(out > 32767) {
		out2 = 32767;
		clipcount++;
	} else
		out2 = (short)out;
	return out2;
}

static void write_little32(unsigned char* to, uint32_t value)
{
	to[0] = (value) & 0xFF;
	to[1] = (value >> 8) & 0xFF;
	to[2] = (value >> 16) & 0xFF;
	to[3] = (value >> 24) & 0xFF;
}

static void write_little16(unsigned char* to, uint16_t value)
{
	to[0] = (value) & 0xFF;
	to[1] = (value >> 8) & 0xFF;
}

void write_wav_header(struct converter_parameters* params, FILE* out, uint64_t samples)
{
		unsigned char header[44];
		uint32_t size1;
		uint32_t size2;
		if(samples == OUTPUT_MAX_UNLIMITED) {
			size1 = 0xFFFFFFFFU;
			size2 = 0xFFFFFFFFU;
		} else {
			size2 = samples << 2;
			size1 = size2 + 36;
			if((size2 >> 2) != samples || size1 < 36 || size1 > 0x7FFFFFFF) {
				fprintf(stderr, "Error: Too many samples for wav file.\n");
				exit(1);
			}
		}

		write_little32(header + 0, RIFF_MAGIC);			//The main RIFF header.
		write_little32(header + 4, size1);			//File size.
		write_little32(header + 8, WAVE_MAGIC);			//This is wave data.
		write_little32(header + 12, FMT_MAGIC);			//Format data.
		write_little32(header + 16, 16);			//16 bytes of format for PCM.
		write_little16(header + 20, 1);				//PCM data.
		write_little16(header + 22, 2);				//Stereo
		write_little32(header + 24, params->output_rate);	//Sample rate.
		write_little32(header + 28, params->output_rate << 2);	//Data rate.
		write_little16(header + 32, 4);				//4 bytes per sample.
		write_little16(header + 34, 16);			//16 bits.
		write_little32(header + 36, DATA_MAGIC);		//Actual data.
		write_little32(header + 40, size2);			//Data size.
		if(fwrite(header, 1, 44, out) < 44) {
			fprintf(stderr, "Error: Can't write wave header.\n");
			exit(1);
		}
}

static void start_output_file(struct converter_parameters* params, FILE* out)
{
	if(params->output_type == OUTPUT_TYPE_WAV) {
		write_wav_header(params, out, OUTPUT_MAX_UNLIMITED);
	}
}

static void finish_output_file(struct converter_parameters* params, FILE* out, uint64_t samples)
{
	if(params->output_type == OUTPUT_TYPE_WAV) {
		if(fseek(out, 0, SEEK_SET)) {
			fprintf(stderr, "Warning: Can't seek output to fix wave header.");
		} else
			write_wav_header(params, out, samples);
	}
	if(fclose(out)) {
		fprintf(stderr, "Error: Can't close output file.");
		exit(1);
	}
}

static short average_s(int64_t accumulator, uint64_t base, uint64_t bound)
{
	if(bound <= base)
		return 0;
	return (short)(accumulator / (int64_t)(bound - base));
}

void audioconvert(struct converter_parameters* params, struct filter* filter)
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
	float left_volume = 1.0;
	float right_volume = 1.0;
	unsigned subsample = 0;
	uint64_t seconds = 0;
	uint64_t samples_in_file = 0;
	uint64_t clipped = 0;
	int eofd = 0;
	int r;

	std::vector<double>* filter_numerator_ptr = filter ? &(filter->numerator) : NULL;
	std::vector<double>* filter_denumerator_ptr = filter ? &(filter->denumerator) : NULL;
	std::vector<double> dummy_vector;
	dummy_vector.push_back(1.0);
	if(!filter_numerator_ptr || filter_numerator_ptr->size() == 0)
		filter_numerator_ptr = &dummy_vector;
	if(!filter_denumerator_ptr || filter_denumerator_ptr->size() == 0)
		filter_denumerator_ptr = &dummy_vector;

	//Define handy variables for filter stuff.
	size_t filter_input_delay = filter ? filter->input_delay : 0;
	std::vector<double>& filter_numerator = *filter_numerator_ptr;
	std::vector<double>& filter_denumerator = *filter_denumerator_ptr;
	if(filter_input_delay >= filter_numerator.size()) {
		fprintf(stderr, "Error: Filter delay too large (not enough coefficients).\n");
		exit(1);
	}

	//Old input/output buffers (also clear them).
	size_t input_lag[2] = {0, 0};
	std::vector<double> old_input[2];
	std::vector<double> old_output[2];
	old_input[0].resize(filter_numerator.size());
	old_input[1].resize(filter_numerator.size());
	for(size_t i = 0; i < filter_numerator.size(); i++) {
		old_input[0][i] = 0;
		old_input[1][i] = 0;
	}
	old_output[0].resize(filter_denumerator.size());
	old_output[1].resize(filter_denumerator.size());
	for(size_t i = 0; i < filter_denumerator.size(); i++) {
		old_output[0][i] = 0;
		old_output[1][i] = 0;
	}

	rate = params->output_rate;
	FILE* in = params->in;
	FILE* out = params->next_out(params->opaque);
	if(!out) {
		fprintf(stderr, "Error: Can't open output file.\n");
		exit(1);
	}
	start_output_file(params, out);

	if(params->input_type == INPUT_TYPE_FM) {
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
//			fprintf(stderr, "Dumping block. Input time %llu, Output time %llu.\n", input_time,
//				output_time);
			r = fwrite(outbuf, OUTSAMPLESIZE, BLOCKSAMPLES, out);
			if(r < BLOCKSAMPLES) {
				fprintf(stderr, "Error: Can't write to output file.\n");
				exit(1);
			}
			samples_in_file += BLOCKSAMPLES;
			if(samples_in_file > params->output_max) {
				finish_output_file(params, out, samples_in_file);
				out = params->next_out(params->opaque);
				if(!out) {
					fprintf(stderr, "Error: Can't open output file.\n");
					exit(1);
				}
				start_output_file(params, out);
				samples_in_file = 0;
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
			if(params->input_type == INPUT_TYPE_FM) {
				short samples[2];
				adlib_getsample(samples, 1);
				active_xleft = samples[0];
				active_xright = samples[1];
			} else {
				left_accumulator += (int64_t)(output_time - last_accumulator_update) *
					(short)active_left;
				right_accumulator += (int64_t)(output_time - last_accumulator_update) *
					(short)active_right;
				last_accumulator_update = output_time;
				active_xleft = average_s(left_accumulator, accumulator_base, output_time);
				active_xright = average_s(right_accumulator, accumulator_base, output_time);
			}


			active_xleft = do_filter(old_input, old_output, filter_numerator, filter_denumerator,
				input_lag, filter_input_delay, 0, params->amplification, left_volume, active_xleft,
				clipped);
			active_xright = do_filter(old_input, old_output, filter_numerator, filter_denumerator,
				input_lag, filter_input_delay, 1, params->amplification, right_volume, active_xright,
				clipped);

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
			if(params->input_type != INPUT_TYPE_FM) {
				left_accumulator += (int64_t)(input_time  - last_accumulator_update) *
					(short)active_left;
				right_accumulator += (int64_t)(input_time - last_accumulator_update) *
					(short)active_right;
				last_accumulator_update = input_time;
			}
			active_left = ((unsigned short)inbuf[4] << 8) | (unsigned short)inbuf[5];
			active_right = ((unsigned short)inbuf[6] << 8) | (unsigned short)inbuf[7];
			inbuf_usage -= SAMPLESIZE;
			memmove(inbuf, inbuf + SAMPLESIZE, inbuf_usage);
			if(params->input_type == INPUT_TYPE_FM) {
				if(active_left & 0x800) {
					left_volume = (float)(active_left & 0x7FF) / 255;
					right_volume = (float)(active_right & 0x7FF) / 255;
					fprintf(stderr, "Note: Volume now %f:%f.\n", (double)left_volume,
						(double)right_volume);
				} else if(active_left == 512) {
					//RESET.
					int i;
					for(i = 0; i < 512; i++) {
						adlib_write(i, 0);
					}
				} else if(active_left < 512) {
					adlib_write(active_left, active_right);
				} else {
					fprintf(stderr, "Warning: Ignored unknown FM command: %04X/%04X\n",
						active_left, active_right);
				}
			}
		}
	}

	r = fwrite(outbuf, OUTSAMPLESIZE, outbuf_usage, out);
	if(r < outbuf_usage) {
		fprintf(stderr, "Error: Can't write to output file.\n");
		exit(1);
	}
	samples_in_file += outbuf_usage;
	outbuf_usage = 0;
	finish_output_file(params, out, samples_in_file);

	fclose(in);
	fprintf(stderr, "Note: %llu samples clipped by filtering / amplification.\n",
		(unsigned long long)clipped);
}
