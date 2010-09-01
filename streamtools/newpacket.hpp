#ifndef _newpacket__hpp__included__
#define _newpacket__hpp__included__

#include <vector>
#include <stdint.h>
#include <cstdlib>
#include <string>
#include <map>

struct packet
{
	uint16_t rp_channel;			//Channel number.
	uint32_t rp_channel_perm;		//Pemanent channel number. Not used when writing.
	std::string rp_channel_name;		//Channel name. Not used when writing.
	uint16_t rp_major;			//Major type. Not used when writing.
	uint8_t rp_minor;			//Minor type.
	uint64_t rp_timestamp;			//Timestamp in nanoseconds.
	std::vector<unsigned char> rp_payload;	//Payload.
};

struct channel
{
	uint16_t c_channel;			//Channel number.
	uint32_t c_channel_perm;		//Channel permanent number. Not used when writing.
	uint16_t c_type;			//Channel type.
	std::string c_channel_name;		//Channel name.
};

class read_channel
{
public:
	read_channel(const std::string& filename);
	~read_channel();
	uint32_t number_for_channel(const std::string& name);
	struct packet* read();
private:
	read_channel(const read_channel& x);
	read_channel& operator=(const read_channel& x);

	FILE* rc_stream;
	std::vector<channel> rc_channels;
	uint64_t rc_last_timestamp;
	bool rc_eof_flag;
	bool rc_segmenttable_coming;
	uint32_t rc_next_permchan;
	std::map<std::string, uint32_t> rc_permchans;
};

class write_channel
{
public:
	write_channel(const std::string& filename);
	~write_channel();
	void start_segment(const std::vector<channel>& channels);
	void write(struct packet& p);
private:
	write_channel(const write_channel& x);
	write_channel& operator=(const write_channel& x);

	FILE* wc_stream;
	std::vector<channel> wc_channels;
	uint64_t wc_last_timestamp;
};

#endif
