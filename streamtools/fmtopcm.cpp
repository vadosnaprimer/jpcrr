#include "newpacket.hpp"
#include "timeparse.hpp"
#include "timecounter.hpp"
#include "resampler.hpp"
#include <iostream>
#include <sstream>
#include <list>
#include <map>
#include <vector>
#include <cstdio>
#include <cstring>
#include <stdexcept>
#include <algorithm>

int main(int argc, char** argv)
{
	std::map<std::string, uint16_t> channel_assignments;
	std::vector<struct channel> chans;
	uint32_t converted_channel = 0;
	uint16_t converted_channel_out;
	if(argc != 5) {
		std::cerr << "syntax: fmtopcm.exe <input> <channel> <rate> <output>" << std::endl;
		exit(1);
	}

	read_channel rchan(argv[1]);
	write_channel wchan(argv[4]);
	converted_channel = rchan.number_for_channel(argv[2]);
	timecounter counter(argv[3]);
	resampler_fm resampler(atoi(argv[3]));

	//Create output channel.
	channel_assignments[argv[2]] = converted_channel_out = (uint16_t)chans.size();
	struct channel c;
	c.c_channel = (uint16_t)chans.size();
	c.c_type = 1;
	c.c_channel_name = argv[2];
	chans.push_back(c);
	wchan.start_segment(chans);

	packet* p;
	packet p2;
	while((p = rchan.read())) {
		//Extract output...
		while((uint64_t)counter <= p->rp_timestamp) {
			sample_number_t out = resampler.nextsample();
			p2.rp_timestamp = counter;
			p2.rp_channel = converted_channel_out;
			p2.rp_minor = 1;
			p2.rp_payload.resize(4);
			unsigned short x = (unsigned short)out.get_x();
			unsigned short y = (unsigned short)out.get_y();
			p2.rp_payload[0] = (x >> 8) & 0xFF;
			p2.rp_payload[1] = x & 0xFF;
			p2.rp_payload[2] = (y >> 8) & 0xFF;
			p2.rp_payload[3] = y & 0xFF;
			wchan.write(p2);
			counter++;
		}
		//Send input...
		if(p->rp_channel_perm == converted_channel && p->rp_minor > 0) {
			resampler.sendpacket(*p);
			delete p;
			continue;
		}

		if(!channel_assignments.count(p->rp_channel_name)) {
			//No channel yet, create.
			channel_assignments[p->rp_channel_name] = (uint16_t)chans.size();
			struct channel c;
			c.c_channel = (uint16_t)chans.size();
			c.c_type = p->rp_major;
			c.c_channel_name = p->rp_channel_name;
			chans.push_back(c);
			wchan.start_segment(chans);
		}
		uint16_t chan = channel_assignments[p->rp_channel_name];
		if(chans[chan].c_type != p->rp_major && p->rp_channel_perm != converted_channel) {
			//Change type.
			chans[chan].c_type = p->rp_major;
			wchan.start_segment(chans);
		}
		p->rp_channel = chan;
		wchan.write(*p);
		delete p;
	}

	return 0;
}
