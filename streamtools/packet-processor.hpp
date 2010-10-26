#ifndef _packet_processor__hpp__included__
#define _packet_processor__hpp__included__

#include <stdint.h>
#include <list>
#include "hardsubs.hpp"
#include "newpacket.hpp"
#include "resampler.hpp"
#include "dedup.hpp"
#include "timecounter.hpp"
#include "framerate-reducer.hpp"

class packet_processor
{
public:
	packet_processor(int64_t _audio_delay, int64_t _subtitle_delay, uint32_t _audio_rate, packet_demux& _demux,
		uint32_t _width, uint32_t _height, uint32_t _rate_num, uint32_t _rate_denum, uint32_t _dedup_max,
		resizer& _using_resizer, std::map<std::pair<uint32_t, uint32_t>, resizer*> _special_resizers,
		std::list<subtitle*> _hardsubs, framerate_reducer* frame_dropper);
	~packet_processor();
	void send_packet(struct packet& p, uint64_t timebase);
	uint64_t get_last_timestamp();
	void send_end_of_stream();
private:
	int64_t get_real_time(struct packet& p);
	void handle_packet(struct packet& q);
	uint64_t audio_counter;
	bool insert_audio_flag;
	int64_t subtitle_delay;
	uint32_t audio_rate;
	resizer& using_resizer;
	std::map<std::pair<uint32_t, uint32_t>, resizer*> special_resizers;
	packet_demux& demux;
	uint32_t width;
	uint32_t height;
	uint32_t rate_num;
	uint32_t rate_denum;
	std::list<subtitle*> hardsubs;
	dedup dedupper;
	timecounter audio_timer;
	timecounter video_timer;
	std::list<packet*> unprocessed;
	uint64_t sequence_length;
	int64_t min_shift;
	packet* saved_video_frame;
	framerate_reducer* frame_dropper;
};

//Returns new timebase.
uint64_t send_stream(packet_processor& p, read_channel& rc, uint64_t timebase);

packet_processor& create_packet_processor(int64_t _audio_delay, int64_t _subtitle_delay, uint32_t _audio_rate,
	uint32_t _width, uint32_t _height, uint32_t _rate_num, uint32_t _rate_denum, uint32_t _dedup_max,
	const std::string& resize_type, std::map<std::pair<uint32_t, uint32_t>, std::string> _special_resizers,
	int argc, char** argv, framerate_reducer* frame_dropper);


#endif
