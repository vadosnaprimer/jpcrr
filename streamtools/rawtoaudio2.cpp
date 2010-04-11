#include "audioconvert.hpp"
#include <cstdio>
#include <cstdlib>
#include <cstring>

static FILE* open_next(void* opaque)
{
	static uint64_t counter = 0;
	char namebuffer[8192];

	if(!counter)
		sprintf(namebuffer, "%s", (const char*)opaque);
	else
		sprintf(namebuffer, "%s.%llu", (const char*)opaque, (unsigned long long)counter);
	counter++;
	FILE* out = fopen(namebuffer, "wb");
	if(!out) {
		fprintf(stderr, "Error: Can't open output '%s'.\n", namebuffer);
		exit(1);
	}
	return out;
}

void usage(char* argv0)
{
	fprintf(stderr, "Usage: %s <parameters>...\n", argv0);
	fprintf(stderr, "\t--input-file=<name>: Read input from <name> (mandatory)\n");
	fprintf(stderr, "\t--input-format=<format>: set input format\n");
	fprintf(stderr, "\t\tpcm\tInput is digital sound data (default).\n");
	fprintf(stderr, "\t\tfm\tInput is FM commands.\n");
	fprintf(stderr, "\t--output-file=<name>: Write output to <name> (mandatory)\n");
	fprintf(stderr, "\t--output-format=<format>: set output format\n");
	fprintf(stderr, "\t\traw\tWrite raw PCM data (default).\n");
	fprintf(stderr, "\t\twav\tWrite WAV output.\n");
	fprintf(stderr, "\t--output-rate=<rate>: set output sample rate (default is 44100)\n");
	fprintf(stderr, "\t--output-split=<samples>: Split output file after approximately this \n"
		"\t\tnumber of samples is reached (default is 500M for WAV, unlimited for RAW).\n"
		"\t\tNote that there is some slop (not larger than few tens of thoursands of\n"
		"\t\tsamples). Prefixes 'k', 'M', 'G', etc may be used. Maximum allowed value is\n"
		"\t\t500M for WAV, 4E for RAW. Special value 'unlimited' stands for unlimited.\n");
	exit(1);
}

uint64_t parse_limit(const char* expr)
{
	uint64_t value = 0;
	bool should_end = false;
	size_t len = strlen(expr);
	if(!strcmp(expr, "unlimited"))
		return OUTPUT_MAX_UNLIMITED;
	for(size_t i = 0; i < len; i++) {
		uint64_t ovalue = value;
		uint64_t multiplier = 0;
		int numericvalue = -1;
		if(should_end)
			goto bad;
		switch(expr[i]) {
		case '0':
			if(!value)
				goto bad;
			numericvalue = 0;
			break;
		case '1':
			numericvalue = 1;
			break;
		case '2':
			numericvalue = 2;
			break;
		case '3':
			numericvalue = 3;
			break;
		case '4':
			numericvalue = 4;
			break;
		case '5':
			numericvalue = 5;
			break;
		case '6':
			numericvalue = 6;
			break;
		case '7':
			numericvalue = 7;
			break;
		case '8':
			numericvalue = 8;
			break;
		case '9':
			numericvalue = 9;
			break;
		case 'k':
			multiplier = 1000ULL;
			break;
		case 'M':
			multiplier = 1000000ULL;
			break;
		case 'G':
			multiplier = 1000000000ULL;
			break;
		case 'T':
			multiplier = 1000000000000ULL;
			break;
		case 'P':
			multiplier = 1000000000000000ULL;
			break;
		case 'E':
			multiplier = 1000000000000000000ULL;
			break;
		default:
			goto bad;
		}
		if(numericvalue >= 0) {
			value = 10 * value + numericvalue;
			if(value / 10 != ovalue)
				goto bad;
			ovalue = value;
		}
		if(multiplier > 0) {
			value *= multiplier;
			if(value / multiplier != ovalue)
				goto bad;
			should_end = true;
		}
	}
	return value;
bad:
	fprintf(stderr, "Error: Invalid limit '%s'\n", expr);
	exit(1);
}

int main(int argc, char** argv)
{
	struct converter_parameters params;
	bool limit_set;
	params.opaque = NULL;
	params.in = NULL;
	params.next_out = open_next;
	params.input_type = INPUT_TYPE_PCM;
	params.output_type = OUTPUT_TYPE_RAW;
	params.output_rate = 44100;
	params.output_max = OUTPUT_MAX_UNLIMITED;

	for(int i = 1; i < argc; i++) {
		if(!strncmp(argv[i], "--input-file=", 13)) {
			params.in = fopen(argv[i] + 13, "rb");
			if(!params.in) {
				fprintf(stderr, "Error: Can't open input '%s'.\n", argv[i] + 13);
				return 1;
			}
		} else if(!strncmp(argv[i], "--output-file=", 14)) {
			if(strlen(argv[i]) == 14) {
				fprintf(stderr, "Error: Blank --output-file not allowed.\n");
				return 1;
			}
			params.opaque = argv[i] + 14;
		} else if(!strncmp(argv[i], "--input-format=", 15)) {
			if(!strcmp(argv[i] + 15, "pcm")) {
				params.input_type = INPUT_TYPE_PCM;
			} else if(!strcmp(argv[i] + 15, "fm")) {
				params.input_type = INPUT_TYPE_FM;
			} else {
				fprintf(stderr, "Error: Bad input format '%s'\n", argv[i] + 15);
				return 1;
			}
		} else if(!strncmp(argv[i], "--output-format=", 16)) {
			if(!strcmp(argv[i] + 16, "raw")) {
				params.output_type = OUTPUT_TYPE_RAW;
				if(!limit_set)
					params.output_max = OUTPUT_MAX_UNLIMITED;
			} else if(!strcmp(argv[i] + 16, "wav")) {
				params.output_type = OUTPUT_TYPE_WAV;
				if(!limit_set)
					params.output_max = 500000000;
			} else {
				fprintf(stderr, "Error: Bad output format '%s'\n", argv[i] + 16);
				return 1;
			}
		} else if(!strncmp(argv[i], "--output-rate=", 14)) {
			char* endat;
			unsigned long rate = strtoul(argv[i] + 14, &endat, 10);
			if(*endat || rate <= 0 || rate > 1000000000) {
				fprintf(stderr, "Error: Bad rate '%s'\n", argv[i] + 14);
				return 1;
			}
			params.output_rate = rate;
		} else if(!strncmp(argv[i], "--output-split=", 15)) {
			params.output_max = parse_limit(argv[i] + 15);
			limit_set = true;
		} else
			usage(argv[0]);
	}

	if(!params.in) {
		fprintf(stderr, "Error: --input-file is mandatory.\n");
		exit(1);
	}
	if(!params.opaque) {
		fprintf(stderr, "Error: --output-file is mandatory.\n");
		exit(1);
	}
	if(params.output_type == OUTPUT_TYPE_WAV && params.output_max > 500000000) {
		fprintf(stderr, "Error: Maximum allowed --output-split for WAV output is 500M.\n");
		exit(1);
	}

	audioconvert(&params);
}

