#include "newpacket.hpp"
#include <cstdio>
#include <iostream>
#include <climits>
#include <cstdlib>
#include <cstring>
#include <stdexcept>
#include <sstream>

namespace
{
	void handle_input_error(FILE* rcs)
	{
		if(ferror(rcs))
			throw std::runtime_error("Error reading input stream");
		else if(feof(rcs))
			throw std::runtime_error("Unexpected end of file reading input stream");
		else
			throw std::runtime_error("Unexpected short read reading input stream");
	}

	bool read_input(FILE* rcs, unsigned char* buffer, size_t amount, int blank_ok)
	{
		int r = fread(buffer, 1, amount, rcs);
		if(r == (int)amount)
			return true;
		if(r == 0 && blank_ok)
			return false;
		handle_input_error(rcs);
		return false;	//Notreached.
	}

	channel& lookup_channel(std::vector<channel>& chantab, uint16_t chan)
	{
		if(chan == 0xFFFF)
			std::runtime_error("lookup_channel: Illegal channel 0xFFFF");
		for(std::vector<channel>::iterator i = chantab.begin(); i != chantab.end(); ++i)
			if(i->c_channel == chan)
				return *i;
		std::stringstream str;
		str << "lookup_channel: Channel " << chan << " not found";
		throw std::runtime_error(str.str());
	}

#define CHANNEL_BITMASK_SIZE ((65536 + CHAR_BIT - 1) / CHAR_BIT)

	void check_segment_table(const std::vector<channel>& table)
	{
		unsigned char channelbits[CHANNEL_BITMASK_SIZE];
		if(table.size() == 0)
			throw std::runtime_error("check_segment_table: Zero channels in segment not allowed");
		if(table.size() > 0xFFFF)
			throw std::runtime_error("check_segment_table: Too many channels in segment (max 65535)");
		for(size_t i = 0; i < CHANNEL_BITMASK_SIZE; i++)
			channelbits[i] = 0;
		for(std::vector<channel>::const_iterator i = table.begin(); i != table.end(); ++i) {
			uint16_t num = i->c_channel;
			if(num == 0xFFFF)
				throw std::runtime_error("Fatal: check_segment_table: Illegal channel 0xFFFF");
			if(channelbits[num / CHAR_BIT] & (1 << (num % CHAR_BIT))) {
				std::stringstream str;
				str << "check_segment_table: Duplicate channel " << num;
				throw std::runtime_error(str.str());
			}
			channelbits[num / CHAR_BIT] |= (1 << (num % CHAR_BIT));
			if(i->c_channel_name.length() > 0xFFFF) {
				std::stringstream str;
				str << "check_segment_Table: Channel name too long (" << i->c_channel_name.length()
					<< " bytes, max 65535)";
				throw std::runtime_error(str.str());
			}
		}
	}

#define CHAN_MAXNAME 65535

	void read_segment_table_entry(read_channel& rc, FILE* rc_stream, channel& chan)
	{
		unsigned char hdr[6 + CHAN_MAXNAME];
		read_input(rc_stream, hdr, 6, 0);
		size_t namelen = ((uint16_t)hdr[4] << 8) | (uint16_t)hdr[5];
		read_input(rc_stream, hdr + 6, namelen, 0);
		chan.c_channel = ((uint16_t)hdr[0] << 8) | (uint16_t)hdr[1];
		chan.c_type = ((uint16_t)hdr[2] << 8) | (uint16_t)hdr[3];
		chan.c_channel_name.resize(namelen);
		for(size_t i = 0; i < namelen; i++)
			chan.c_channel_name[i] = hdr[6 + i];
		chan.c_channel_perm = rc.number_for_channel(chan.c_channel_name);
	}

	void read_segment_table(std::vector<channel>& chantab, FILE* rc_stream, read_channel& rc)
	{
		unsigned char x[2];
		uint16_t i;

		read_input(rc_stream, x, 2, 0);
		uint16_t chans = ((uint16_t)x[0] << 8) | (uint16_t)x[1];
		if(chans == 0)
			throw std::runtime_error("read_segment_table: 0 channel segments not allowed");

		chantab.resize(chans);
		for(i = 0; i < chans; i++)
			read_segment_table_entry(rc, rc_stream, chantab[i]);
		check_segment_table(chantab);
	}


#define SPECIAL_TIMESKIP 0
#define SPECIAL_TIMESKIP_STR "\xFF\xFF\xFF\xFF"
#define SPECIAL_NEWSEGMENT 1
#define SPECIAL_NEWSEGMENT_STR "JPCRRMULTIDUMP"

	struct special_entry
	{
		int se_num;
		const char* se_spec;
	} specials[] = {
		{SPECIAL_TIMESKIP, SPECIAL_TIMESKIP_STR},
		{SPECIAL_NEWSEGMENT, SPECIAL_NEWSEGMENT_STR},
		{-1, NULL}
	};

#define MAXSPECIALLEN 4096

	static int read_special(FILE* rcs)
	{
		char buf[MAXSPECIALLEN];
		size_t readcount = 0;
		while(1) {
			unsigned char ch;
			read_input(rcs, &ch, 1, 0);
			buf[readcount++] = (char)ch;
			struct special_entry* e = specials;
			int matched = 0;
			while(e->se_spec) {
				if(readcount < strlen(e->se_spec) && !strncmp(buf, e->se_spec, readcount))
					matched = 1;
				if(readcount == strlen(e->se_spec) && !strncmp(buf, e->se_spec, readcount))
					return e->se_num;
				e++;
			}
			if(!matched)
				break;
		}
		throw std::runtime_error("read_special: Bad special");
	}
}

read_channel::~read_channel()
{
	fclose(rc_stream);
}

read_channel::read_channel(const std::string& filename)
{
	rc_stream = fopen(filename.c_str(), "rb");
	if(!rc_stream) {
		std::stringstream str;
		str << "read_channel: Can't open '" << filename << "' for reading";
		throw std::runtime_error(str.str());
	}
	rc_last_timestamp = 0;
	rc_eof_flag = false;
	rc_segmenttable_coming = false;
	rc_next_permchan = 0;
}

uint32_t read_channel::number_for_channel(const std::string& name)
{
	if(rc_permchans.count(name))
		return rc_permchans[name];
	return rc_permchans[name] = rc_next_permchan++;
}

struct packet* read_channel::read()
{
	struct packet* ret = NULL;
	unsigned char packetheader[7];

	if(rc_segmenttable_coming) {
		//This is the segment channel table.
		read_segment_table(rc_channels, rc_stream, *this);
		rc_segmenttable_coming = 0;
		goto restart;
	}

	if(rc_eof_flag)
		return NULL;

	//Read the channel number.
	if(!read_input(rc_stream, packetheader, 2, 1)) {
		rc_eof_flag = 1;
		return NULL;	//Stream ends.
	}
	if(packetheader[0] == 0xFF && packetheader[1] == 0xFF) {
		//Special.
		int specialtype = read_special(rc_stream);
		if(specialtype == SPECIAL_TIMESKIP)
			rc_last_timestamp += 0xFFFFFFFFU;
		else if(specialtype == SPECIAL_NEWSEGMENT)
			rc_segmenttable_coming = 1;

		//If we don't have channel table nor channel table won't be next, then the magic is bad (not
		//a valid dump).
		if(!rc_channels.size() && !rc_segmenttable_coming)
			throw std::runtime_error("read_channel: Bad magic");
	} else {
		//The first element must be of special type (segment start).
		if(!rc_channels.size())
			throw std::runtime_error("read_channel: Bad magic");
		uint16_t chan = ((uint16_t)packetheader[0] << 8) | (uint16_t)packetheader[1];
		struct channel& c = lookup_channel(rc_channels, chan);
		//Read next 5 bytes of header (that far is safe).
		read_input(rc_stream, packetheader + 2, 5, 0);
		size_t payload = 0;
		ret = new packet();
		unsigned char tmp;

		try {
			do {
				read_input(rc_stream, &tmp, 1, 0);
				if(((size_t)0 - 1) / 128 <= payload)
					throw std::runtime_error("read_channel: Packet payload too large");
				payload = 128 * payload + (tmp & 0x7F);
			} while(tmp & 0x80);

			ret->rp_payload.resize(payload);
			read_input(rc_stream, &ret->rp_payload[0], payload, 0);
			ret->rp_channel = chan;
			ret->rp_channel_perm = c.c_channel_perm;
			ret->rp_major = c.c_type;
			ret->rp_minor = packetheader[6];
			uint32_t timedelta = ((uint32_t)packetheader[2] << 24) | ((uint32_t)packetheader[3] << 16) |
				((uint32_t)packetheader[4] << 8) | ((uint32_t)packetheader[5]);
			ret->rp_timestamp = (rc_last_timestamp += timedelta);
			ret->rp_channel_name = c.c_channel_name;
		} catch(...) {
			delete ret;
			throw;
		}
	}

	//Try again if return value would be NULL.
restart:
	if(!ret)
		return this->read();
	else
		return ret;
}

write_channel::write_channel(const std::string& filename)
{
	wc_stream = fopen(filename.c_str(), "wb");
	if(!wc_stream) {
		std::stringstream str;
		str << "write_channel: Can't open '" << filename << "' for writing";
		throw std::runtime_error(str.str());
	}
	wc_last_timestamp = 0;
}

write_channel::~write_channel()
{
	fclose(wc_stream);
}

void write_channel::start_segment(const std::vector<channel>& channels)
{
	unsigned char x[] = {0xFF, 0xFF, 'J', 'P', 'C', 'R', 'R', 'M', 'U', 'L', 'T', 'I', 'D', 'U', 'M', 'P'};
	unsigned char chanbuf[6 + 65535];	//Any channel entry fits in this.

	check_segment_table(channels);

	//Write new segment start.
	if(fwrite(x, 16, 1, wc_stream) < 1)
		throw std::runtime_error("write_channel: Error writing output stream");

	//Write new segment channel table.
	chanbuf[0] = (unsigned char)(channels.size() >> 8);
	chanbuf[1] = (unsigned char)(channels.size());
	if(fwrite(chanbuf, 2, 1, wc_stream) < 1)
		throw std::runtime_error("write_channel: Error writing output stream (channel count)");

	for(std::vector<channel>::const_iterator i = channels.begin(); i != channels.end(); ++i) {
		size_t len;

		chanbuf[0] = (unsigned char)(i->c_channel >> 8);
		chanbuf[1] = (unsigned char)(i->c_channel);
		chanbuf[2] = (unsigned char)(i->c_type >> 8);
		chanbuf[3] = (unsigned char)(i->c_type);
		chanbuf[4] = (unsigned char)(i->c_channel_name.length() >> 8);
		chanbuf[5] = (unsigned char)(i->c_channel_name.length());
		for(size_t j = 0; j < i->c_channel_name.length(); j++)
			chanbuf[6 + j] = i->c_channel_name[j];
		len = 6 + i->c_channel_name.length();
		if(fwrite(chanbuf, len, 1, wc_stream) < 1)
			throw std::runtime_error("write_channel: Error writing output stream (channel entry)");
	}
	wc_channels = channels;
}

void write_channel::write(struct packet& p)
{
	unsigned char packetheaders[17];
	if(!wc_channels.size())
		throw std::runtime_error("write_channel: Attempt to write outside segment");
	if(p.rp_timestamp < wc_last_timestamp) {
		std::stringstream str;
		str << "write_channel: Non-monotonic timestream (" << p.rp_timestamp << "<" << wc_last_timestamp
			<< ")";
		throw std::runtime_error(str.str());
	}
	uint64_t deltatime = p.rp_timestamp - wc_last_timestamp;
	while(deltatime > 0xFFFFFFFFULL) {
		//Timeskip.
		packetheaders[0] = 0xFF;
		packetheaders[1] = 0xFF;
		packetheaders[2] = 0xFF;
		packetheaders[3] = 0xFF;
		packetheaders[4] = 0xFF;
		packetheaders[5] = 0xFF;
		if(fwrite(packetheaders, 6, 1, wc_stream) < 1)
			throw std::runtime_error("write_channel: Error writing output stream (delay)");
		deltatime -= 0xFFFFFFFFU;
	}
	lookup_channel(wc_channels, p.rp_channel);
	size_t hdrlen = 7, counter;
	uint64_t tmplen = p.rp_payload.size();

	//Compose the actual headers.
	packetheaders[0] = (unsigned char)(p.rp_channel >> 8);
	packetheaders[1] = (unsigned char)(p.rp_channel);
	packetheaders[2] = (unsigned char)(deltatime >> 24);
	packetheaders[3] = (unsigned char)(deltatime >> 16);
	packetheaders[4] = (unsigned char)(deltatime >> 8);
	packetheaders[5] = (unsigned char)(deltatime);
	packetheaders[6] = (unsigned char)(p.rp_minor);
	bool wflag = false;
	for(counter = 9; counter <= 9; counter--) {
		unsigned shift = 7 * counter;
		unsigned char bias = shift ? 0x80 : 0x00;
		if(tmplen >= (1ULL << shift) || !shift || wflag) {
			packetheaders[hdrlen++] = bias + ((tmplen >> shift) & 0x7F);
			tmplen &= ((1ULL << shift) - 1);
			wflag = true;
		}
	}

	if(hdrlen && fwrite(packetheaders, hdrlen, 1, wc_stream) < 1)
		throw std::runtime_error("write_channel: Error writing output stream (packet headers)");
	//Write the actual payload.
	if(p.rp_payload.size() && fwrite(&p.rp_payload[0], p.rp_payload.size(), 1, wc_stream) < 1)
		throw std::runtime_error("write_channel: Error writing output stream (payload)");
	wc_last_timestamp = p.rp_timestamp;
}
