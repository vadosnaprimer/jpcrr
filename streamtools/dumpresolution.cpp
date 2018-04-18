#include <map>
#include <iostream>
#include <stdint.h>
#include "newpacket.hpp"

int real_main(int argc, char** argv)
{
	uint32_t current_width = 0, current_height = 0;
	uint32_t current_numerator = 0, current_denominator = 0;
	uint64_t last_timestamp = 0;
	uint64_t time_correction = 0;
	uint64_t delta = 0;

	if(argc == 1) {
		std::cerr << "Syntax: guessresolution.exe <files>..." << std::endl;
		return 1;
	}
	for(int i = 1; i < argc; i++) {
		read_channel in(argv[i]);
		time_correction = last_timestamp;
		packet* p;
		while((p = in.read())) {
			p->rp_timestamp += time_correction;
			last_timestamp = p->rp_timestamp;
			if(p->rp_major == 0 && p->rp_payload.size() >= 4) {
				current_width =
					((uint32_t)p->rp_payload[0] << 8) |
					p->rp_payload[1];
				current_height =
					((uint32_t)p->rp_payload[2] << 8) |
					p->rp_payload[3];
				current_numerator = 
					((uint32_t)p->rp_payload[4] << 24) |
					((uint32_t)p->rp_payload[5] << 16) |
					((uint32_t)p->rp_payload[6] << 8) |
					p->rp_payload[7];
				current_denominator = 
					((uint32_t)p->rp_payload[8] << 24) |
					((uint32_t)p->rp_payload[9] << 16) |
					((uint32_t)p->rp_payload[10] << 8) |
					p->rp_payload[11];
				std::cout
					<< last_timestamp << " "
					<< current_width << "x" << current_height << " "
					<< current_numerator << "/" << current_denominator << " "
					<< last_timestamp - delta
					<< std::endl;
				delta = last_timestamp;
			}
		}
	}
	return 0;
}
