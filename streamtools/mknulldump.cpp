#include "newpacket.hpp"
#include <iostream>
#include <sstream>
#include <cstdio>
#include <cstring>
#include <stdexcept>
#include <algorithm>
#include "timeparse.hpp"

int real_main(int argc, char** argv)
{
	if(argc != 3) {
		std::cerr << "syntax: mknulldmp.exe <length> <output>" << std::endl;
		exit(1);
	}

	uint64_t length = parse_timespec(argv[1]);
	write_channel wchan(argv[2]);

	std::vector<struct channel> channels;
	channels.resize(1);
	channels[0].c_channel = 0;
	channels[0].c_type = 3;
	channels[0].c_channel_name = "<DUMMY>";
	wchan.start_segment(channels);

	struct packet p2;
	p2.rp_channel = 0;
	p2.rp_timestamp = length;
	p2.rp_minor = 0;
	wchan.write(p2);
	return 0;
}
