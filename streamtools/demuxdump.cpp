#include "newpacket.hpp"
#include <iostream>
#include <sstream>
#include <cstdio>
#include <cstring>
#include <stdexcept>
#include <algorithm>

int real_main(int argc, char** argv)
{
	if(argc != 4) {
		std::cerr << "syntax: demuxdump.exe <input> <channel> <output>" << std::endl;
		exit(1);
	}

	read_channel rchan(argv[1]);
	uint32_t pchan = rchan.number_for_channel(argv[2]);
	write_channel wchan(argv[3]);

	packet* p;
	bool segtable_present = false;
	uint16_t lasttype = 65535;
	uint64_t lasttime = 0;
	while((p = rchan.read())) {
		lasttime = p->rp_timestamp;
		if(p->rp_channel_perm == pchan) {
			if(!segtable_present || lasttype != p->rp_major) {
				std::vector<struct channel> channels;
				channels.resize(2);
				channels[0].c_channel = 0;
				channels[0].c_type = lasttype = p->rp_major;
				channels[0].c_channel_name = p->rp_channel_name;
				channels[1].c_channel = 1;
				channels[1].c_type = 3;
				channels[1].c_channel_name = "<DUMMY>";
				wchan.start_segment(channels);
				segtable_present = true;
			}
			p->rp_channel = 0;
			wchan.write(*p);
		}
		delete p;
	}
	struct packet p2;
	p2.rp_channel = 1;
	p2.rp_timestamp = lasttime;
	p2.rp_minor = 0;
	wchan.write(p2);
	return 0;
}
