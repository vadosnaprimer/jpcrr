/*
    JPC-RR: A x86 PC Hardware Emulator
    Release 1

    Copyright (C) 2009-2010 H. Ilari Liusvaara

    This program is free software; you can redistribute it and/or modify
    it under the terms of the GNU General Public License version 2 as published by
    the Free Software Foundation.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License along
    with this program; if not, write to the Free Software Foundation, Inc.,
    51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.

    Based on JPC x86 PC Hardware emulator,
    A project from the Physics Dept, The University of Oxford

    Details about original JPC can be found at:

    www-jpc.physics.ox.ac.uk

*/

#include "newpacket.hpp"
#include "resize.hpp"
#include "resampler.hpp"
#include "hardsubs.hpp"
#include <sstream>
#include "digital-filter.hpp"
#include "timecounter.hpp"
#include <iostream>
#include <cmath>
#include "misc.hpp"
#include "SDL.h"
#define MAXADVANCE 50
#define MINSAMPLES 512
#define BUFSAMPLES 2048
#define MAXSAMPLES 8192

std::vector<sample_number_t> audiobuffer;
uint32_t timebase;
uint32_t audio_clear = 0;
uint32_t audio_stamp = 0;
uint64_t audio_samples;
uint32_t audiorate = 44100;
uint32_t audio_lag = 0;

void audio_callback(void* x, Uint8* stream, int bytes)
{
	unsigned samples = bytes / 4;
	audio_samples += samples;
	audio_stamp = SDL_GetTicks();
	audio_clear = 1000 * audio_samples / audiorate;

	//Remove the samples that have been missed.
	if(audio_lag > 0) {
		if(audio_lag > audiobuffer.size()) {
			std::cerr << "Throwing away " << audiobuffer.size() << " samples as missed" << std::endl;
			audio_lag -= audiobuffer.size();
			audiobuffer.resize(0);
		} else {
			std::cerr << "Throwing away " << audio_lag << " samples as missed" << std::endl;
			memmove((Uint8*)&audiobuffer[0], (Uint8*)&audiobuffer[audio_lag], 4 * (audiobuffer.size() - audio_lag));
			audiobuffer.resize(audiobuffer.size() - audio_lag);
			audio_lag = 0;
		}
	}

	if(samples > audiobuffer.size()) {
		std::cerr << "Audio underflow!" << std::endl;
		memcpy(stream, (Uint8*)&audiobuffer[0], 4 * audiobuffer.size());
		audio_lag += (bytes - 4 * audiobuffer.size()) / 4;
		memset(stream + 4 * audiobuffer.size(), 0, bytes - 4 * audiobuffer.size());
		samples = audiobuffer.size();
	} else
		memcpy(stream, (Uint8*)&audiobuffer[0], 4 * samples);
	memmove((Uint8*)&audiobuffer[0], (Uint8*)&audiobuffer[samples], 4 * (audiobuffer.size() - samples));
	audiobuffer.resize(audiobuffer.size() - samples);
}

int next_filename_index(int argc, char** argv, int currentindex)
{
	bool split = false;
	for(int i = 1; i < argc; i++) {
		std::string arg = argv[i];
		if(!split && arg == "--") {
			split = true;
		}
		if(i <= currentindex)
			continue;
		if(split || !isstringprefix(argv[i], "--")) {
			return i;
		}
	}
	return -1;
}

int main(int argc, char** argv)
{
	int filenameindex = -1;
	uint64_t timecorrection = 0;
	uint64_t last_timestamp = 0;
	bool split = false;
	unsigned long percentspeed = 100;

	for(int i = 1; i < argc; i++)
		try {
			if(split || !isstringprefix(argv[i], "--")) {
				if(filenameindex == -1)
					filenameindex = i;
			} else if(isstringprefix(argv[i], "--audio-rate=")) {
				std::string val = settingvalue(argv[i]);
				char* x;
				unsigned long rate = strtoul(val.c_str(), &x, 10);
				if(*x || !rate || rate > 1000000) {
					std::cerr << "--audio-rate: Bad audio rate" << std::endl;
					return 1;
				}
				audiorate = rate;
			} else if(isstringprefix(argv[i], "--speed=")) {
				std::string val = settingvalue(argv[i]);
				char* x;
				unsigned long rate = strtoul(val.c_str(), &x, 10);
				if(*x || !rate || rate > 1000) {
					std::cerr << "--speed: Bad speed" << std::endl;
					return 1;
				}
				percentspeed = rate;
			} else if(isstringprefix(argv[i], "--audio-mixer-")) {
				//We process these later.
			} else if(isstringprefix(argv[i], "--video-hardsub-")) {
				//We process these later.
			} else if(!strcmp(argv[i], "--"))
				split = true;
			else {
				std::cerr << "Bad option: " << argv[i] << "." << std::endl;
				return 1;
			}
		} catch(std::exception& e) {
			std::cerr << "Error processing option: " << argv[i] << ":" << e.what() << std::endl;
			return 1;
		}

	std::list<subtitle*> subtitles;
	hardsub_settings stsettings;
	//Initialize audio processing.
	mixer mix;
	packet_demux ademux(mix, audiorate);
	timecounter acounter(audiorate);

	process_audio_resampler_options(ademux, "--audio-mixer-", argc, argv);
	subtitles = process_hardsubs_options(stsettings, "--video-hardsub-", argc, argv);

	if(filenameindex < 0) {
		std::cout << "usage: " << argv[0] << " [<options>] [--] <filename>..." << std::endl;
		std::cout << "Show video contained in stream <filename> in window." << std::endl;
		std::cout << "--speed=<speed>" << std::endl;
		std::cout << "\tSet speed to <speed>%." << std::endl;
		print_hardsubs_help("--video-hardsub-");
		print_audio_resampler_help("--audio-mixer-");
		return 1;
	}
	read_channel* in = new read_channel(argv[filenameindex]);

	//Video stuff.
	SDL_Surface* swsurf = NULL;
	SDL_Surface* hwsurf = NULL;
	struct packet* p = NULL;
	unsigned prev_width = -1;
	unsigned prev_height = -1;

	if(SDL_Init(SDL_INIT_VIDEO) < 0) {
		fprintf(stderr, "Can't initialize SDL.\n");
		return 1;
	}

#if SDL_BYTEORDER == SDL_BIG_ENDIAN
	uint32_t rmask = 0xFF000000;
	uint32_t gmask = 0x00FF0000;
	uint32_t bmask = 0x0000FF00;
#else
	uint32_t rmask = 0x000000FF;
	uint32_t gmask = 0x0000FF00;
	uint32_t bmask = 0x00FF0000;
#endif
	int enable_debug = 0;
	if(getenv("PLAYDUMP_STATS"))
		enable_debug = 1;

	uint64_t lagged_frames = 0, total_frames = 0;
	uint32_t last_realtime_second = 0;
	timebase = SDL_GetTicks();
	uint64_t audiosamples = 0;
	std::list<image_frame_rgbx*> picture_buffer;
	std::list<uint64_t> picture_stamp;
	uint64_t first_stamp = 0;

	SDL_AudioSpec aspec;
	aspec.freq = audiorate * percentspeed / 100;
	aspec.format = AUDIO_S16LSB;
	aspec.channels = 2;
	aspec.samples = BUFSAMPLES / 2;
	aspec.callback = audio_callback;
	if(SDL_OpenAudio(&aspec, NULL) < 0) {
		std::cerr << "Can't initialize audio." << std::endl;
		return 1;
	} else
		SDL_PauseAudio(0);

	while(true) {
		p = in->read();
		//Correct the timestamp and update last seen time;
		if(!p) {
			//Exhausted current file, switch to next.
			delete in;
			timecorrection = last_timestamp;
			std::cerr << "Time correction set to " << timecorrection << "." << std::endl;
			filenameindex = next_filename_index(argc, argv, filenameindex);
			if(filenameindex < 0)
				break;		//No more files.
			in = new read_channel(argv[filenameindex]);
			continue;
		} else {
			p->rp_timestamp += timecorrection;
			last_timestamp = p->rp_timestamp;
		}
		//If we are too far ahead, slow down a bit.
		if(audiobuffer.size() > MAXSAMPLES)
			SDL_Delay(10);
		while(p->rp_timestamp > acounter) {
			acounter++;
			//Extract audio sample.
			sample_number_t s = ademux.nextsample();
			SDL_LockAudio();
			audiobuffer.push_back(s);
			SDL_UnlockAudio();
			audiosamples++;
		}
		if(p->rp_major == 5) {
			//This is gameinfo packet.
			subtitle_process_gameinfo(subtitles, *p);
			continue;
		} else if(p->rp_major != 0) {
			//Process the audio packet.
			ademux.sendpacket(*p);
			delete p;
			continue;		//Not image.
		}
		uint32_t timenow = SDL_GetTicks();
		uint32_t realtime = timenow - timebase;
		total_frames++;
		if(audiobuffer.size() < MINSAMPLES && p->rp_timestamp > 0) {
			lagged_frames++;
			delete p;
			continue;		//Behind deadline, try to catch up.
		}
		if(enable_debug && realtime / 1000 > last_realtime_second) {
			last_realtime_second = realtime / 1000;
			printf("\e[1GTime %lus: Frames: %llu(lagged:%llu), Audio: %llu(%llu)",
				(unsigned long)last_realtime_second, (unsigned long long)total_frames,
				(unsigned long long)lagged_frames, (unsigned long long)audiosamples,
				(unsigned long long)audiobuffer.size());
			fflush(stdout);
		}
		//Decode the frame.
		picture_buffer.push_back(new image_frame_rgbx(*p));
		if(picture_stamp.empty())
			first_stamp = p->rp_timestamp / 1000000;
		picture_stamp.push_back(p->rp_timestamp);
		delete p;
		while(true) {
			if(picture_stamp.empty())
				break;
			uint32_t audiocorr = (SDL_GetTicks() - audio_stamp) * 100 / percentspeed;
			if(first_stamp >= audio_clear)
				if(first_stamp - audio_clear > audiocorr)
					break;
			uint64_t stamp = *picture_stamp.begin();
			picture_stamp.pop_front();
			if(!picture_stamp.empty())
				first_stamp = *picture_stamp.begin() / 1000000;

			image_frame_rgbx& frame = **picture_buffer.begin();
			picture_buffer.pop_front();

			if(prev_width != frame.get_width() || prev_height != frame.get_height()) {
				hwsurf = SDL_SetVideoMode(frame.get_width(), frame.get_height(), 0, SDL_SWSURFACE |
					SDL_DOUBLEBUF | SDL_ANYFORMAT);
				swsurf = SDL_CreateRGBSurface(SDL_SWSURFACE, frame.get_width(), frame.get_height(), 32,
					rmask, gmask, bmask, 0);
			}
			prev_width = frame.get_width();
			prev_height = frame.get_height();
			for(std::list<subtitle*>::iterator j = subtitles.begin(); j != subtitles.end(); ++j)
				if((*j)->timecode <= stamp && (*j)->timecode + (*j)->duration > stamp)
					render_subtitle(frame, **j);

			SDL_LockSurface(swsurf);
			memcpy((unsigned char*)swsurf->pixels, frame.get_pixels(), 4 * prev_width * prev_height);
			SDL_UnlockSurface(swsurf);
			SDL_BlitSurface(swsurf, NULL, hwsurf, NULL);
			SDL_Flip(hwsurf);
			SDL_Event e;
			if(SDL_PollEvent(&e) == 1 && e.type == SDL_QUIT)
				goto quit;		//Quit.
			delete &frame;
		}
	}
quit:
	SDL_Quit();
	return 0;
}
