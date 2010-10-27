#include "packet-processor.hpp"
#include "output-drv.hpp"
#include <iostream>

packet_processor::packet_processor(int64_t _audio_delay, int64_t _subtitle_delay, uint32_t _audio_rate,
	packet_demux& _demux, uint32_t _width, uint32_t _height, uint32_t _rate_num, uint32_t _rate_denum,
	uint32_t _dedup_max, resizer& _using_resizer,
	std::map<std::pair<uint32_t, uint32_t>, resizer*> _special_resizers, std::list<subtitle*> _hardsubs,
	framerate_reducer* _frame_dropper, Lua* _lua)
	: using_resizer(_using_resizer), demux(_demux), dedupper(_dedup_max, _width, _height),
	audio_timer(_audio_rate), video_timer(_rate_num, _rate_denum)
{
	subtitle_delay = _subtitle_delay;
	special_resizers = _special_resizers;
	audio_rate = _audio_rate;
	width = _width;
	height = _height;
	rate_num = _rate_num;
	rate_denum = _rate_denum;
	frame_dropper = _frame_dropper;
	hardsubs = _hardsubs;
	sequence_length = 0;
	saved_video_frame = NULL;
	insert_audio_flag = (_audio_delay > 0);
	if(_audio_delay < 0)
		_audio_delay = -_audio_delay;
	audio_counter = _audio_delay * audio_rate / 1000000000;
	lua = _lua;
}

packet_processor::~packet_processor()
{
	for(std::list<subtitle*>::iterator i = hardsubs.begin(); i != hardsubs.end(); ++i)
		delete *i;
	delete &demux;
	delete &using_resizer;
}

int64_t packet_processor::get_real_time(struct packet& p)
{
	switch(p.rp_major) {
	case 4:
		return p.rp_timestamp + subtitle_delay;
	default:
		return p.rp_timestamp;
	}
}

void packet_processor::delete_audio(uint64_t samples)
{
	if(insert_audio_flag) {
		if(samples > audio_counter) {
			audio_counter = samples - audio_counter;
			insert_audio_flag = false;
		} else
			audio_counter -= samples;
	} else
		audio_counter += samples;
}
void packet_processor::insert_silence(uint64_t samples)
{
	for(uint64_t i = 0; i < samples; i++)
		samples_to_insert.push_back(sample_number_t());
}

void packet_processor::insert_audio(const std::vector<sample_number_t>& samples)
{
	for(std::vector<sample_number_t>::const_iterator i = samples.begin(); i != samples.end(); i++)
		samples_to_insert.push_back(*i);
}

void packet_processor::handle_packet(struct packet& q)
{
	int64_t packet_realtime = get_real_time(q);
	if((int64_t)q.rp_timestamp + subtitle_delay >= 0)
		distribute_no_subtitle_callback((uint64_t)((int64_t)q.rp_timestamp + subtitle_delay));

	//Insert silence in beginning.
	while(insert_audio_flag && audio_counter > 0) {
		distribute_audio_callback(0, 0);
		audio_counter--;
	}
	//Insert data if any.
	while(!samples_to_insert.empty()) {
		for(std::vector<sample_number_t>::iterator i = samples_to_insert.begin();
			i != samples_to_insert.end(); i++)
			distribute_audio_callback(*i);
		samples_to_insert.clear();
	}

	//Read the audio data until this packet.
	while(audio_timer <= q.rp_timestamp) {
		//Extract sample.
		sample_number_t v = demux.nextsample();
		//Send sample only if audio time is really positive.
		if(!audio_counter)
			distribute_audio_callback(v);
		else
			audio_counter--;
		audio_timer++;
	}
	//Dump the video data until this packet (fixed fps mode).
	while(rate_denum > 0 && packet_realtime >= 0 && (int64_t)(uint64_t)video_timer <= packet_realtime) {
		image_frame_rgbx* f = &frame_dropper->pull((uint64_t)video_timer);
		if(!f->get_width() || !f->get_height()) {
			//Replace with valid frame.
			f->put_ref();
			f = new image_frame_rgbx(width, height);
		}

		//Subtitles.
		for(std::list<subtitle*>::iterator i = hardsubs.begin(); i != hardsubs.end(); ++i)
			if((*i)->timecode <= video_timer && (*i)->timecode + (*i)->duration > video_timer)
				render_subtitle(*f, **i);

		//Write && Free the temporary frames.
		distribute_video_callback(video_timer, *f);
		f->put_ref();
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
		delete &q;
		break;
	case 0: {
		image_frame_rgbx* f = new image_frame_rgbx(q);
		uint64_t ts = q.rp_timestamp;
		delete &q;
		if(!lua->enabled())
			inject_frame(ts, f);
		else {
			lua->frame_callback(ts, f);
			f->put_ref();
		}
		break;
	} case 4:
		//Subttitle.
		if(packet_realtime >= 0) {
			q.rp_timestamp += subtitle_delay;
			distribute_subtitle_callback(q);
		}
		delete &q;
		break;
	default:
		delete &q;
		break;
	}
}

uint32_t packet_processor::get_width()
{
	return width;
}

uint32_t packet_processor::get_height()
{
	return height;
}

uint32_t packet_processor::get_rate()
{
	return audio_rate;
}

void packet_processor::inject_frame(uint64_t ts, image_frame_rgbx* f)
{
	//If special resizer has been defined, use that.
	resizer* rs = &using_resizer;
	std::pair<uint32_t, uint32_t> size = std::make_pair(f->get_width(), f->get_height());
	if(special_resizers.count(size))
		rs = special_resizers[size];
	image_frame_rgbx& r = f->resize(width, height, *rs);
	f->put_ref();
	if(rate_denum > 0) {
		frame_dropper->push(ts, r);
	} else {
		//Handle frame immediately.
		//Subtitles.
		for(std::list<subtitle*>::iterator i = hardsubs.begin(); i != hardsubs.end(); ++i)
			if((*i)->timecode <= ts && (*i)->timecode + (*i)->duration > ts)
		render_subtitle(r, **i);

		//Write && Free the temporary frames.
		if(!dedupper(r.get_pixels()))
			distribute_video_callback(ts, r);

	}
	r.put_ref();
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
	const std::string& resize_type, std::map<std::pair<uint32_t, uint32_t>, std::string> _special_resizers,
	int argc, char** argv, framerate_reducer* frame_dropper, Lua* _lua)
{
	hardsub_settings stsettings;
	std::map<std::pair<uint32_t, uint32_t>, resizer*> special_resizers;
	resizer& _using_resizer = resizer_factory::make_by_type(resize_type);
	mixer& mix = *new mixer();
	packet_demux& ademux = *new packet_demux(mix, _audio_rate);
	process_audio_resampler_options(ademux, "--audio-mixer-", argc, argv);
	std::list<subtitle*> subtitles = process_hardsubs_options(stsettings, "--video-hardsub-", argc, argv);

	for(std::map<std::pair<uint32_t, uint32_t>, std::string>::iterator i = _special_resizers.begin();
		i != _special_resizers.end(); ++i)
		special_resizers[i->first] = &resizer_factory::make_by_type(i->second);

	return *new packet_processor(_audio_delay, _subtitle_delay, _audio_rate, ademux, _width, _height, _rate_num,
		_rate_denum, _dedup_max, _using_resizer, special_resizers, subtitles, frame_dropper, _lua);
}
