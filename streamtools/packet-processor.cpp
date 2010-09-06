#include "packet-processor.hpp"
#include "output-drv.hpp"
#include <iostream>

packet_processor::packet_processor(int64_t _audio_delay, int64_t _subtitle_delay, uint32_t _audio_rate,
	packet_demux& _demux, uint32_t _width, uint32_t _height, uint32_t _rate_num, uint32_t _rate_denum,
	uint32_t _dedup_max, resizer& _using_resizer, std::list<subtitle*> _hardsubs)
	: using_resizer(_using_resizer), demux(_demux), dedupper(_dedup_max, _width, _height),
	audio_timer(_audio_rate), video_timer(_rate_num, _rate_denum)
{
	audio_delay = _audio_delay;
	subtitle_delay = _subtitle_delay;
	audio_rate = _audio_rate;
	width = _width;
	height = _height;
	rate_num = _rate_num;
	rate_denum = _rate_denum;
	hardsubs = _hardsubs;
	sequence_length = 0;
	min_shift = 0;
	saved_video_frame = NULL;
	if(min_shift > _audio_delay)
		min_shift = _audio_delay;
	if(min_shift > _subtitle_delay)
		min_shift = _subtitle_delay;
}

packet_processor::~packet_processor()
{
	for(std::list<subtitle*>::iterator i = hardsubs.begin(); i != hardsubs.end(); ++i)
		delete *i;
}

int64_t packet_processor::get_real_time(struct packet& p)
{
	switch(p.rp_major) {
	case 1:
	case 2:
		return p.rp_timestamp + audio_delay;
	case 4:
		return p.rp_timestamp + subtitle_delay;
	default:
		return p.rp_timestamp;
	}
}

void packet_processor::handle_packet(struct packet& q)
{
	int64_t packet_realtime = get_real_time(q);
	int64_t audio_linear_time = packet_realtime - min_shift;
	//Read the audio data until this packet. Audio_linear_time is always positive.
	while(audio_timer <= (uint64_t)audio_linear_time) {
		//Extract sample.
		sample_number_t v = demux.nextsample();
		//Send sample only if audio time is really positive.
		if(audio_linear_time > (int64_t)-min_shift)
			distribute_audio_callback(v);
		audio_timer++;
	}
	//Dump the video data until this packet (fixed fps mode).
	while(rate_denum > 0 && packet_realtime >= 0 && (int64_t)(uint64_t)video_timer <= packet_realtime) {
		image_frame_rgbx* f;
		//Parse the frame from saved packet.
		if(saved_video_frame)
			f = new image_frame_rgbx(*saved_video_frame);
		else
			f = new image_frame_rgbx(0, 0);
		image_frame_rgbx& r = f->resize(width, height, using_resizer);

		//Subtitles.
		for(std::list<subtitle*>::iterator i = hardsubs.begin(); i != hardsubs.end(); ++i)
			if((*i)->timecode <= video_timer && (*i)->timecode + (*i)->duration > video_timer)
				render_subtitle(r, **i);

		//Write && Free the temporary frames.
		distribute_video_callback(video_timer, r.get_pixels());
		if(&r != f)
			delete &r;
		delete f;
		video_timer++;
	}
	switch(q.rp_major) {
	case 1:
	case 2:
		demux.sendpacket(q);
		delete &q;
		break;
	case 5:
		subtitle_process_gameinfo(hardsubs, q);
		break;
	case 0:
		if(rate_denum > 0) {
			if(saved_video_frame)
				delete saved_video_frame;
			saved_video_frame = &q;
		} else {
			//Handle frame immediately.
			image_frame_rgbx f(q);
			image_frame_rgbx& r = f.resize(width, height, using_resizer);

			//Subtitles.
			for(std::list<subtitle*>::iterator i = hardsubs.begin(); i != hardsubs.end(); ++i)
				if((*i)->timecode <= q.rp_timestamp &&
					(*i)->timecode + (*i)->duration > q.rp_timestamp)
					render_subtitle(r, **i);

			//Write && Free the temporary frames.
			if(!dedupper(r.get_pixels()))
				distribute_video_callback(q.rp_timestamp, r.get_pixels());
			if(&r != &f)
				delete &r;
			delete &q;
		}
		break;
	case 4:
		//Subttitle.
		if(packet_realtime >= 0) {
			q.rp_timestamp += subtitle_delay;
			distribute_subtitle_callback(q);
		}
	}
}

uint64_t packet_processor::get_last_timestamp()
{
	return sequence_length;
}

void packet_processor::send_packet(struct packet& p, uint64_t timebase)
{
	p.rp_timestamp += timebase;
	sequence_length = p.rp_timestamp;
	//Keep list of unprocessed packets sorted in real time order.
	int64_t packet_realtime = get_real_time(p);
	//Find the point to insert before and do the insertion.
	std::list<packet*>::iterator i = unprocessed.begin();
	while(i != unprocessed.end() && packet_realtime >= get_real_time(**i))
		++i;
	unprocessed.insert(i, &p);

	//Read the first packet from the queue.
	handle_packet(*unprocessed.front());
	unprocessed.pop_front();
}

void packet_processor::send_end_of_stream()
{
	while(!unprocessed.empty()) {
		handle_packet(*unprocessed.front());
		unprocessed.pop_front();
	}
}

uint64_t send_stream(packet_processor& p, read_channel& rc, uint64_t timebase)
{
	struct packet* q;
	static uint64_t last_second = 0;
	while((q = rc.read())) {
		if((q->rp_timestamp + timebase) / 1000000000 > last_second) {
			last_second = (q->rp_timestamp + timebase) / 1000000000;
			std::cerr << "Processed " << last_second << "s." << std::endl;
		}
		p.send_packet(*q, timebase);
	}
	return p.get_last_timestamp();
}

packet_processor& create_packet_processor(int64_t _audio_delay, int64_t _subtitle_delay, uint32_t _audio_rate,
	uint32_t _width, uint32_t _height, uint32_t _rate_num, uint32_t _rate_denum, uint32_t _dedup_max,
	const std::string& resize_type, int argc, char** argv)
{
	hardsub_settings stsettings;
	resizer& _using_resizer = resizer_factory::make_by_type(resize_type);
	mixer& mix = *new mixer();
	packet_demux& ademux = *new packet_demux(mix, _audio_rate);
	process_audio_resampler_options(ademux, "--audio-mixer-", argc, argv);
	std::list<subtitle*> subtitles = process_hardsubs_options(stsettings, "--video-hardsub-", argc, argv);
	return *new packet_processor(_audio_delay, _subtitle_delay, _audio_rate, ademux, _width, _height, _rate_num,
		_rate_denum, _dedup_max, _using_resizer, subtitles);
}
