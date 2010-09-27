#include "newpacket.hpp"
#include <iostream>
#include <iomanip>

bool brief_mode = false;

void handle_packet(packet* p)
{
	std::cout << "Packet: channel " << p->rp_channel << "<perm#" << p->rp_channel_perm << ">[";
	for(size_t i = 0; i < p->rp_channel_name.length(); i++) {
		unsigned char ch = p->rp_channel_name[i];
		if(ch < 32 || ch > 126)
			std::cout << "\\x" << std::setfill('0') << std::setw(2) << std::hex << (uint16_t)ch;
		else if(ch == '[' || ch == ']' || ch == '\\')
			std::cout << "\\" << ch;
		else
			std::cout << ch;
	}
	std::cout << "] at " << p->rp_timestamp << ", type " << p->rp_major << "(" << (uint16_t)p->rp_minor << ") "
		<< "payload " << p->rp_payload.size() << ":" << std::endl;
	for(size_t i = 0; !brief_mode && i < p->rp_payload.size(); i += 16) {
		size_t j = p->rp_payload.size() - i;
		if(j > 16)
			j = 16;
		std::cout << "\t";
		for(size_t k = 0; k < j; k++)
			std::cout << std::setfill('0') << std::setw(2) << std::hex
				<< (uint16_t)p->rp_payload[i + k] << " ";
		std::cout << std::endl;
	}
	delete p;
}

int main(int argc, char** argv)
{
	struct packet* p;
	if(argc != 2) {
		std::cerr << "syntax: " << argv[0] << " <file>" << std::endl;
		exit(1);
	}
	if(getenv("BRIEF_PACKETDUMP"))
		brief_mode = true;
	read_channel rc(argv[1]);
	while((p = rc.read()))
		handle_packet(p);
	return 0;
}
