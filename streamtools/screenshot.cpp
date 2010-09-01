#include "newpacket.hpp"
#include "png-out.hpp"
#include <cstdio>
#include <cctype>
#include <iostream>
#include <iomanip>
#include <cstdlib>
#include <cstring>
#include <sstream>
#include "timeparse.hpp"

unsigned image_seq = 0;
char namebuffer[4096];
char prefix[4000];

#define MAXTIME 0xFFFFFFFFFFFFFFFFULL

struct packet* old_packet;

uint64_t* target_stamps;
unsigned target_count;

int in_range(uint64_t low, uint64_t high)
{
	for(unsigned i = 0; i < target_count; i++)
		if(target_stamps[i] >= low && target_stamps[i] < high)
			return 1;
	return 0;
}

void handle_packet(struct packet* p)
{
	int do_it = 0;

	if(p == NULL) {
		if(old_packet && in_range(old_packet->rp_timestamp, MAXTIME))
			do_it = 1;
	} else {
		if(p->rp_major != 0)
			return;
		if(old_packet && in_range(old_packet->rp_timestamp, p->rp_timestamp))
			do_it = 1;
	}

	if(do_it && old_packet) {
		try {
			image_frame f(*old_packet);
			std::stringstream name;
			name << prefix << std::setfill('0') << std::setw(5) << image_seq++ << ".png";
			std::cerr << "Saving screenshot '" << name.str() << "'." << std::endl;
			f.save_png(name.str());
		} catch(std::exception& e) {
			std::cerr << "Can't save screenshot: " << e.what() << std::endl;
		}
	}
	if(old_packet)
		delete old_packet;
	old_packet = p;
}

void packet_loop(const char* name)
{
	struct packet* p;
	read_channel rc(name);
	while((p = rc.read()))
		handle_packet(p);
	handle_packet(NULL);
}

int main(int argc, char** argv)
{
	const char* _prefix = "screenshot-";
	char* _input = NULL;

	for(int i = 1; i < argc; i++) {
		if(!strncmp(argv[i], "--input=", 8))
			_input = argv[i] + 8;
		else if(!strncmp(argv[i], "--prefix=", 9))
			_prefix= argv[i] + 9;
		else if(!strncmp(argv[i], "-", 1)) {
			fprintf(stderr, "Unknown option %s\n", argv[i]);
			exit(1);
		} else {
			uint64_t stamp = parse_timespec(argv[i]);
			target_stamps = (uint64_t*)realloc(target_stamps, sizeof(uint64_t) * (target_count + 1));
			target_stamps[target_count++] = stamp;
		}
	}

	if(!_input || !target_count) {
		fprintf(stderr, "syntax: %s --input=<file> [--prefix=<prefix>] <timespec>...\n", argv[0]);
		exit(1);
	}
	strcpy(prefix, _prefix);
	packet_loop(_input);
	return 0;
}
