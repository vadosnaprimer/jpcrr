#include "newpacket.hpp"
#include "timecounter.hpp"
#include <iostream>
#include <sstream>
#include <cstdio>
#include <cstring>
#include <stdexcept>
#include <algorithm>

#define BMODE_DEFAULT -1
#define BMODE_8BIT 0
#define BMODE_16BIT_LE 1
#define BMODE_16BIT_BE 2

#define CMODE_DEFAULT -1
#define CMODE_MONO 0
#define CMODE_STEREO 1
#define CMODE_STEREO_SWAPPED 2

#define SMODE_DEFAULT -1
#define SMODE_SIGNED 0
#define SMODE_UNSIGNED 1

int bit_mode = BMODE_DEFAULT;
int channel_mode = CMODE_DEFAULT;
int signed_mode = SMODE_DEFAULT;
bool rate_given = false;
timecounter curtime(44100);
uint32_t lvoln = 0;
uint32_t lvold = 0;
uint32_t rvoln = 0;
uint32_t rvold = 0;

int bmode(int x)
{
	if(x == BMODE_DEFAULT)
		return BMODE_16BIT_LE;
	return x;
}

int cmode(int x)
{
	if(x == CMODE_DEFAULT)
		return CMODE_STEREO;
	return x;
}

int smode(int x)
{
	if(x == SMODE_DEFAULT)
		return SMODE_SIGNED;
	return x;
}

void set_bmode(int x)
{
	if(x == BMODE_DEFAULT)
		bit_mode = x;
	else
		throw std::runtime_error("Multiple bit width specifications present");
}

void set_cmode(int x)
{
	if(x == CMODE_DEFAULT)
		channel_mode = x;
	else
		throw std::runtime_error("Multiple channel specifications present");
}

void set_smode(int x)
{
	if(x == SMODE_DEFAULT)
		signed_mode = x;
	else
		throw std::runtime_error("Multiple signedness specifications present");
}

void set_rate(const std::string& s)
{
	if(!rate_given) {
		curtime = timecounter(s);
		rate_given = true;
	} else
		throw std::runtime_error("Multiple rate specifications present");
}

void set_chan_volume(const std::string& spec, uint32_t& n, uint32_t& d)
{
	uint64_t base = 1;
	bool decimal = false;
	uint64_t readfpu = 0;

	if(!spec.length())
		throw std::runtime_error("Empty volume spec is not legal");

	for(size_t i = 0; i < spec.length(); i++) {
		if(readfpu > 1844674407370955160ULL)
			throw std::runtime_error("Overflow reading number");
		if(!decimal)
			if(spec[i] >= '0' && spec[i] <= '9')
				readfpu = 10 * readfpu + (spec[i] - '0');
			else if(spec[i] == '.')
				decimal = true;
			else {
				std::stringstream str;
				str << "Expected number or '.', got '" << spec[i] << "'";
				throw std::runtime_error(str.str());
			}
		else
			if(spec[i] >= '0' && spec[i] <= '9') {
				if(base == 10000000000000000000ULL) {
					std::stringstream str;
					str << "volume number has more than 19 decimal digits";
					throw std::runtime_error(str.str());
				}
				base *= 10;
				readfpu = 10 * readfpu + (spec[i] - '0');
			} else {
				std::stringstream str;
				str << "Expected number, got '" << spec[i] << "'";
				throw std::runtime_error(str.str());
			}
	}

	while(base > 0xFFFFFFFFULL || readfpu > 0xFFFFFFFFULL) {
		base /= 2;
		readfpu /= 2;
	}

	n = (uint32_t)readfpu;
	d = (uint32_t)base;
}

void set_volume(const std::string& s)
{
	uint32_t ln, ld, rn, rd;
	std::string s2, sl, sr;

	if(lvold)
		throw std::runtime_error("Initial volume already given");

	s2 = s;
	size_t split = s2.find_first_of(",");
	if(split > s2.length()) {
		sl = s2;
		sr = s2;
	} else {
		sl = s2.substr(0, split);
		sr = s2.substr(split + 1);
	}

	set_chan_volume(sl, ln, ld);
	set_chan_volume(sr, rn, rd);
	lvoln = ln;
	lvold = ld;
	rvoln = rn;
	rvold = rd;
}

void process_argument(const char* s, std::string& input, std::string& channel, std::string& output)
{
	try {
		if(!strcmp(s, "--8bit"))
			set_bmode(BMODE_8BIT);
		else if(!strcmp(s, "--16bit"))
			set_bmode(BMODE_16BIT_LE);
		else if(!strcmp(s, "--16bit-little-endian"))
			set_bmode(BMODE_16BIT_LE);
		else if(!strcmp(s, "--16bit-big-endian"))
			set_bmode(BMODE_16BIT_BE);
		else if(!strcmp(s, "--mono"))
			set_bmode(CMODE_MONO);
		else if(!strcmp(s, "--stereo"))
			set_bmode(CMODE_STEREO);
		else if(!strcmp(s, "--stereo-swapped"))
			set_bmode(CMODE_STEREO_SWAPPED);
		else if(!strcmp(s, "--signed"))
			set_bmode(SMODE_SIGNED);
		else if(!strcmp(s, "--unsigned"))
			set_bmode(SMODE_UNSIGNED);
		else if(!strncmp(s, "--rate=", 7))
			set_rate(s + 7);
		else if(!strncmp(s, "--volume=", 9))
			set_volume(s + 9);
		else if(!strncmp(s, "--", 2))
			throw std::runtime_error("Unknown option");
		else if(*s) {
			if(input == "")
				input = s;
			else if(channel == "")
				channel = s;
			else if(output == "")
				output = s;
			else
				throw std::runtime_error("Only three non-options may be present");
		}
	} catch(std::exception& e) {
		std::cerr << "Error processing argument '" << s << "': " << e.what() << std::endl;
		exit(1);
	}
}

#define MAXSAMPLE 4

bool readsample(FILE* filp, short& left, short& right)
{
	int _bmode = bmode(bit_mode);
	int _cmode = cmode(channel_mode);
	int _smode = smode(signed_mode);
	unsigned char sample[MAXSAMPLE];

	int bytes = 1;
	switch(_bmode) {
	case BMODE_8BIT:
		bytes *= 1;
	case BMODE_16BIT_LE:
	case BMODE_16BIT_BE:
		bytes *= 2;
	default:
		throw std::runtime_error("Internal error: Unknown bit mode!");
	}
	switch(_cmode) {
	case CMODE_MONO:
		bytes *= 1;
	case CMODE_STEREO:
	case CMODE_STEREO_SWAPPED:
		bytes *= 2;
	default:
		throw std::runtime_error("Internal error: Unknown channel mode!");
	}

	int r = fread(sample, 1, bytes, filp);
	if(r > 0 && r < bytes)
		throw std::runtime_error("Error reading input file");
	if(r == 0)
		return false;

	//Now, we have sample to decode. First get it to 16-bit little-endian layout.
	//First, swap channels in swapped stereo.
	if(_cmode == CMODE_STEREO_SWAPPED)
		for(int i = 0; i < bytes / 2; i++)
			std::swap(sample[i], sample[bytes / 2 + i]);
	//If mono, copy the samples for stereo.
	if(_cmode == CMODE_MONO)
		for(int i = 0; i < bytes; i++)
			sample[bytes + i] = sample[i];
	//Expand 8-bit samples.
	if(_bmode == BMODE_8BIT) {
		sample[3] = sample[1];
		sample[2] = 0;
		sample[1] = sample[0];
		sample[0] = 0;
	}
	//Byteswap 16-bit BE samples.
	if(_bmode == BMODE_16BIT_BE) {
		std::swap(sample[0], sample[1]);
		std::swap(sample[2], sample[3]);
	}

	if(_smode == SMODE_UNSIGNED) {
		left = (short)(((int)sample[1] << 8) + (int)sample[0] - 32768);
		right = (short)(((int)sample[1] << 8) + (int)sample[0] - 32768);
	} else {
		left = (short)(((int)sample[1] << 8) + (int)sample[0]);
		right = (short)(((int)sample[1] << 8) + (int)sample[0]);
	}
	return true;
}

namespace
{
	void encode32(unsigned char* buf, uint32_t value)
	{
		buf[0] = (value >> 24) & 0xFF;
		buf[1] = (value >> 16) & 0xFF;
		buf[2] = (value >> 8) & 0xFF;
		buf[3] = (value) & 0xFF;
	}
}

void write_volume_change(write_channel& wchan)
{
	if(!lvold || !rvold)
		return;
	struct packet p;
	p.rp_channel = 0;
	p.rp_minor = 0;			//Set volume.
	p.rp_payload.resize(16);
	encode32(&p.rp_payload[0], lvoln);
	encode32(&p.rp_payload[4], lvold);
	encode32(&p.rp_payload[8], rvoln);
	encode32(&p.rp_payload[12], rvold);
	p.rp_timestamp = curtime;
	wchan.write(p);
}

void copy_loop(FILE* filp, write_channel& wchan)
{
	short left, right;
	struct packet p;
	p.rp_channel = 0;
	p.rp_minor = 1;			//PCM sample.
	p.rp_payload.resize(4);
	while(readsample(filp, left, right)) {
		p.rp_timestamp = curtime;
		p.rp_payload[0] = (unsigned char)((unsigned char)left >> 8);
		p.rp_payload[1] = (unsigned char)((unsigned char)left & 0xFF);
		p.rp_payload[2] = (unsigned char)((unsigned char)right >> 8);
		p.rp_payload[3] = (unsigned char)((unsigned char)right & 0xFF);
		wchan.write(p);
		curtime++;
	}
	//Write the end-of-clip sample.
	p.rp_channel = 1;
	p.rp_timestamp = curtime;
	wchan.write(p);
}

int main(int argc, char** argv)
{
	std::string input, channel, output;

	for(int i = 1; i < argc; i++)
		process_argument(argv[i], input, channel, output);

	if(output == "") {
		std::cerr << "Syntax: audiotodump.exe <options> <input> <channel> <output>" << std::endl;
		std::cerr << "--8bit: 8-bit samples." << std::endl;
		std::cerr << "--16bit: 16-bit little-endian samples." << std::endl;
		std::cerr << "--16bit-little-endian: 16-bit little-endian samples (default)." << std::endl;
		std::cerr << "--16bit-big-endian: 16-bit big-endian samples." << std::endl;
		std::cerr << "--mono: Mono sound." << std::endl;
		std::cerr << "--stereo: Stereo sound (default)." << std::endl;
		std::cerr << "--stereo-swapped: Stereo sound with swapped channels." << std::endl;
		std::cerr << "--signed: Signed samples (default)." << std::endl;
		std::cerr << "--unsigned: Unsigned samples." << std::endl;
		std::cerr << "--rate=<rate>: Use specified sampling rate (default 44100)." << std::endl;
		std::cerr << "--volume=<vol>: Write initial volume." << std::endl;
		std::cerr << "--volume=<lvol>,<rvol>: Write initial volume (unbalanced)." << std::endl;
		return 1;
	}

	FILE* in = NULL;
	in = fopen(input.c_str(), "rb");
	if(!in) {
		std::cerr << "Error opening input file '" << input << "'." << std::endl;
		exit(1);
	}

	std::vector<struct channel> channels;
	channels.resize(2);
	channels[0].c_channel = 0;				//Channel #0.
	channels[0].c_type = 1;					//audio channel.
	channels[0].c_channel_name = channel;			//Channel name.
	channels[1].c_channel = 1;				//Channel #1.
	channels[1].c_type = 3;					//dummy channel.
	channels[1].c_channel_name = "<DUMMY>";			//Channel name.
	write_channel wchan(output);
	wchan.start_segment(channels);

	write_volume_change(wchan);
	copy_loop(in, wchan);
	fclose(in);
	return 0;
}
