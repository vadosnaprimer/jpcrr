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
#include "outputs/public.hpp"

struct packet_processor_parameters
{
	int64_t audio_delay;
	int64_t subtitle_delay;
	uint32_t audio_rate;
	packet_demux* demux;
	uint32_t width;
	uint32_t height;
	uint32_t rate_num;
	uint32_t rate_denum;
	uint32_t dedup_max;
	framerate_reducer* frame_dropper;
	output_driver_group* outgroup;
	//These are filled by create_packet_processor().
	rescaler_group* rescalers;
	std::list<subtitle*> hardsubs;
};

class packet_processor
{
public:
	packet_processor(struct packet_processor_parameters* params);
	~packet_processor();
	void send_packet(struct packet& p, uint64_t timebase);
	uint64_t get_last_timestamp();
	void send_end_of_stream();
private:
	int64_t get_real_time(struct packet& p);
	void handle_packet(struct packet& q);
	int64_t audio_delay;
	int64_t subtitle_delay;
	uint32_t audio_rate;
	rescaler_group& rescalers;
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
	output_driver_group& group;
};

//Returns new timebase.
uint64_t send_stream(packet_processor& p, read_channel& rc, uint64_t timebase);

packet_processor& create_packet_processor(struct packet_processor_parameters* params,
	int argc, char** argv);


#endif
