#include "packet-processor.hpp"
#include "outputs/public.hpp"
#include <iostream>
#include <sstream>
#include <set>
#include <map>
#include <stdexcept>
#include "misc.hpp"

packet_processor::packet_processor(struct packet_processor_parameters* params)
	: rescalers(*params->rescalers), demux(*params->demux),
	dedupper(params->dedup_max, params->width, params->height),
	audio_timer(params->audio_rate), video_timer(params->rate_num, params->rate_denum),
	group(*params->outgroup)
{
	audio_delay = params->audio_delay;
	subtitle_delay = params->subtitle_delay;
	audio_rate = params->audio_rate;
	width = params->width;
	height = params->height;
	rate_num = params->rate_num;
	rate_denum = params->rate_denum;
	frame_dropper = params->frame_dropper;
	hardsubs = params->hardsubs;
	sequence_length = 0;
	min_shift = 0;
	saved_video_frame = NULL;
	if(min_shift > params->audio_delay)
		min_shift = params->audio_delay;
	if(min_shift > params->subtitle_delay)
		min_shift = params->subtitle_delay;
}

packet_processor::~packet_processor()
{
	//for(std::list<subtitle*>::iterator i = hardsubs.begin(); i != hardsubs.end(); ++i)
	//	delete *i;
	delete &demux;
	delete &rescalers;
}

int64_t packet_processor::get_real_time(struct packet& p)
{
	switch(p.rp_major) {
	case 1:
	case 2:
	case 6:
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
			group.do_audio_callback(v.get_x(), v.get_y());
		audio_timer++;
	}
	//Dump the video data until this packet (fixed fps mode).
	while(rate_denum > 0 && packet_realtime >= 0 && (int64_t)(uint64_t)video_timer <= packet_realtime) {
		image_frame_rgbx* f = &frame_dropper->pull((uint64_t)video_timer);
		if(!f->get_width() || !f->get_height()) {
			//Replace with valid frame.
			delete f;
			f = new image_frame_rgbx(width, height);
		}

		//Subtitles.
		//for(std::list<subtitle*>::iterator i = hardsubs.begin(); i != hardsubs.end(); ++i)
		//	if((*i)->timecode <= video_timer && (*i)->timecode + (*i)->duration > video_timer)
		//		render_subtitle(*f, **i);

		//Write && Free the temporary frames.
		group.do_video_callback(video_timer, f->get_pixels());
		delete f;
		video_timer++;
	}
	switch(q.rp_major) {
	case 6:
		//General MIDI.
		if(q.rp_payload.size() > 0)
			group.do_gmidi_callback(q.rp_timestamp, q.rp_payload[0]);
		break;
	case 1:
	case 2:
		demux.sendpacket(q);
		delete &q;
		break;
	case 5:
		//subtitle_process_gameinfo(hardsubs, q);
		//delete &q;
		break;
	case 0:
		if(!width) {
			//Video output is disabled.
			delete &q;
			break;
		}
		if(rate_denum > 0) {
			image_frame_rgbx* f = new image_frame_rgbx(q);
			uint64_t ts = q.rp_timestamp;
			delete &q;
			image_frame_rgbx& r = f->resize(width, height, rescalers);
			if(&r != f)
				delete f;
			frame_dropper->push(ts, r);
		} else {
			//Handle frame immediately.
			image_frame_rgbx f(q);
			image_frame_rgbx& r = f.resize(width, height, rescalers);

			//Subtitles.
			//for(std::list<subtitle*>::iterator i = hardsubs.begin(); i != hardsubs.end(); ++i)
			//	if((*i)->timecode <= q.rp_timestamp &&
			//		(*i)->timecode + (*i)->duration > q.rp_timestamp)
			//		render_subtitle(r, **i);

			//Write && Free the temporary frames.
			if(!dedupper(r.get_pixels()))
				group.do_video_callback(q.rp_timestamp, r.get_pixels());
			if(&r != &f)
				delete &r;
			delete &q;
		}
		break;
	case 4:
		//Subttitle.
		if(packet_realtime >= 0) {
			q.rp_timestamp += subtitle_delay;
			uint64_t basetime = q.rp_timestamp;
			uint64_t duration = 0;
			uint8_t* text = NULL;
			if(q.rp_major != 4 || q.rp_minor != 0 || q.rp_payload.size() < 8)
				goto bad;		//Bad subtitle packet.
			for(size_t i = 0; i < 8; i++)
				duration |= ((uint64_t)q.rp_payload[i] << (56 - 8 * i));
			text = &q.rp_payload[8];
			group.do_subtitle_callback(basetime, duration, text);
		}
bad:
		delete &q;
		break;
	case 3:
		//Dummy. Ignore.
		delete &q;
		break;
	default:
		std::cerr << "Warning: Unknown packet major " << q.rp_major << "." << std::endl;
		delete &q;
		break;
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

packet_processor& create_packet_processor(struct packet_processor_parameters* params, int argc, char** argv)
{
	bool default_reset = false;
	std::set<std::pair<uint32_t, uint32_t> > resets;
	//hardsub_settings stsettings;
	mixer& mix = *new mixer();
	params->demux = new packet_demux(mix, params->audio_rate);
	process_audio_resampler_options(*params->demux, "--audio-mixer-", argc, argv);
	//params->hardsubs = process_hardsubs_options(stsettings, "--video-hardsub-", argc, argv);

	//Deal with the rescalers.
	params->rescalers = new rescaler_group(get_default_rescaler());
	for(int i = 1; i < argc; i++) {
		std::string arg = argv[i];
		try {
			if(isstringprefix(arg, "--video-scale-algo=")) {
				std::string value = settingvalue(arg);
				struct parsed_scaler ps = parse_rescaler_expression(value);
				std::pair<uint32_t, uint32_t> x = std::make_pair(ps.swidth, ps.sheight);
				if(ps.is_special) {
					if(resets.count(x)) {
						std::ostringstream str;
						str << "Special rescaler for " << ps.swidth << "*" << ps.sheight
							<< "already specified." << std::endl;
						throw std::runtime_error(str.str());
					}
					params->rescalers->set_special_rescaler(ps.swidth, ps.sheight, *ps.use_rescaler);
					resets.insert(x);
				} else {
					if(default_reset)
						throw std::runtime_error("Default rescaler already specified");
					params->rescalers->set_default_rescaler(*ps.use_rescaler);
					default_reset = true;
				}
			}
		} catch(std::exception& e) {
			std::ostringstream str;
			str << "Error processing option: " << arg << ":" << e.what() << std::endl;
			throw std::runtime_error(str.str());
		}
	}

	return *new packet_processor(params);
}
