#include "newpacket.hpp"
#include <iostream>
#include <sstream>
#include <list>
#include <map>
#include <vector>
#include <cstdio>
#include <cstring>
#include <stdexcept>
#include <algorithm>

struct input
{
	input(const std::string& name)
		: rchan(name)
	{
		curpacket = NULL;
		eof = 0;
	}
	packet* peek_packet()
	{
		if(!curpacket && !eof) {
			curpacket = rchan.read();
			if(curpacket == NULL)
				eof = 1;
		}
		return curpacket;
	}

	void discard_packet()
	{
		curpacket = NULL;
	}
private:
	read_channel rchan;
	packet* curpacket;
	int eof;
};

struct packet* first_of_inputs(std::list<input*>& rchans)
{
	struct packet* f = NULL;
	std::list<input*>::iterator j = rchans.begin();
	for(std::list<input*>::iterator i = rchans.begin(); i != rchans.end(); ++i) {
		struct packet* g = (*i)->peek_packet();
		if(g && (!f || g->rp_timestamp < f->rp_timestamp)) {
			f = g;
			j = i;
		}
	}
	(*j)->discard_packet();
	return f;
}

int main(int argc, char** argv)
{
	std::list<input*> rchans;
	std::map<std::string, uint16_t> channel_assignments;
	std::vector<struct channel> chans;
	if(argc < 3) {
		std::cerr << "syntax: muxdump.exe <input>... <output>" << std::endl;
		exit(1);
	}

	write_channel wchan(argv[argc - 1]);
	for(int i = 1; i < argc - 1; i++)
		rchans.push_back(new input(argv[i]));

	packet* p;
	while((p = first_of_inputs(rchans))) {
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
		if(chans[chan].c_type != p->rp_major) {
			//Change type.
			chans[chan].c_type = p->rp_major;
			wchan.start_segment(chans);
		}
		p->rp_channel = chan;
		wchan.write(*p);
		delete p;
	}

	for(std::list<input*>::iterator i = rchans.begin(); i != rchans.end(); ++i)
		delete(*i);

	return 0;
}
