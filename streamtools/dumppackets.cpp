#include "newpacket.hpp"
#include <stdio.h>

bool brief_mode = false;

void handle_packet(packet* p)
{
	printf("Packet: channel %u<perm#%u>[", p->rp_channel, p->rp_channel_perm);
	for(size_t i = 0; i < p->rp_channel_name.length(); i++) {
		unsigned char ch = p->rp_channel_name[i];
		if(ch < 32 || ch > 126)
			printf("\\x%02X", ch);
		else if(ch == '[' || ch == ']' || ch == '\\')
			printf("\\%c", ch);
		else
			printf("%c", ch);
	}
	printf("] at %llu, type %u(%u), payload %zu:\n", (unsigned long long)p->rp_timestamp, p->rp_major,
		p->rp_minor, p->rp_payload.size());
	for(size_t i = 0; !brief_mode && i < p->rp_payload.size(); i += 16) {
		size_t j = p->rp_payload.size() - i;
		if(j > 16)
			j = 16;
		printf("\t");
		for(size_t k = 0; k < j; k++)
			printf("%02X ", p->rp_payload[i + k]);
		printf("\n");
	}
	delete p;
}

int main(int argc, char** argv)
{
	struct packet* p;
	if(argc != 2) {
		fprintf(stderr, "syntax: %s <file>\n", argv[0]);
		exit(1);
	}
	if(getenv("BRIEF_PACKETDUMP"))
		brief_mode = true;
	read_channel rc(argv[1]);
	while((p = rc.read()))
		handle_packet(p);
	return 0;
}
