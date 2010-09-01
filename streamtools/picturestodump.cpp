#include "SDL_image.h"
#include "SDL.h"
#include "png-out.hpp"
#include <zlib.h>
#include <cstdlib>
#include <stdint.h>
#include <string>
#include <iostream>
#include <vector>
#include "newpacket.hpp"
#include "timecounter.hpp"
#include <stdexcept>

timecounter curtime(60);
std::vector<std::string> inputfiles;
std::string outputfile;
bool always_file = false;

void add_file(const std::string& file)
{
	if(outputfile != "")
		inputfiles.push_back(outputfile);
	outputfile = file;
}

void usage()
{
	std::cerr << "Syntax: picturestodump.exe <options> <input>... <output>" << std::endl;
	std::cerr << "--fps=<fps>\tSet framerate to <fps>. Default 60." << std::endl;
	std::cerr << "<input>...\tInput files, in order." << std::endl;
	std::cerr << "<output>\t\tOutput file." << std::endl;
	exit(1);
}

void resize_block(unsigned char*& block, size_t& alloc, size_t min = 0)
{
	size_t new_size = 3 * alloc / 2 + 5;
	if(new_size < min)
		new_size = min;
	block = (unsigned char*)realloc(block, new_size);
	if(!block) {
		std::cerr << "Out of memory while trying to allocate " << new_size << " bytes." << std::endl;
		exit(2);
	}
	alloc = new_size;
}

void reset_zlib_output(z_stream* s, unsigned char* buffer, size_t buffersize)
{
	s->next_out = buffer;
	s->avail_out = buffersize;
}

void flush_zlib_output(z_stream* s, std::vector<unsigned char>& block, unsigned char* buffer, size_t buffersize)
{
	//Resize the block so the new data fits.
	size_t delta = buffersize - s->avail_out;
	if(delta) {
		size_t osize = block.size();
		block.resize(osize + delta);
		//Copy the data.
		memcpy(&block[osize], buffer, delta);
	}
	//Reset the buffer.
	s->next_out = buffer;
	s->avail_out = buffersize;
}

#define INBUFFER_SIZE 65536
#define OUTBUFFER_SIZE 16384

void write_image(const std::string& name, write_channel& wchan)
{
	struct packet p;
	uint32_t width = 0;
	uint32_t height = 0;
	uint8_t* pixels;
	p.rp_channel = 0;					//On video channel.
	p.rp_minor = 1;						//Compressed frame.
	p.rp_timestamp = curtime;				//time.
	//rp_payload empty.

	//Load the image.
	SDL_Surface* img = IMG_Load(name.c_str());
	if(!img) {
		std::cerr << "Can't load image '" << name << "':" << IMG_GetError() << std::endl;
		exit(2);
	}

	//Convert the SDL surface into raw image.
	width = img->w;
	height = img->h;
	pixels = new uint8_t[4 * width * height];
	SDL_LockSurface(img);
	for(uint32_t y = 0; y < height; y++)
		for(uint32_t x = 0; x < width; x++) {
			Uint8 r, g, b;
			size_t addr = y * img->pitch + x * img->format->BytesPerPixel;
			SDL_GetRGB(*(Uint32*)((unsigned char*)img->pixels + addr), img->format, &r, &g, &b);
			pixels[4 * width * y + 4 * x + 0] = r;
			pixels[4 * width * y + 4 * x + 1] = g;
			pixels[4 * width * y + 4 * x + 2] = b;
			pixels[4 * width * y + 4 * x + 3] = 0;
		}
	SDL_UnlockSurface(img);
	SDL_FreeSurface(img);

	//Reserve space for dimensions and write them.
	p.rp_payload.push_back((width >> 8) & 0xFF);
	p.rp_payload.push_back(width & 0xFF);
	p.rp_payload.push_back((height >> 8) & 0xFF);
	p.rp_payload.push_back(height & 0xFF);

	//Compress the image data.
	size_t bytes_processed = 0;
	size_t bytes_total = 4 * width * height;
	unsigned char in[INBUFFER_SIZE];
	unsigned char out[OUTBUFFER_SIZE];
	bool finished = false;
	z_stream s;

	memset(&s, 0, sizeof(z_stream));
	int r = deflateInit(&s, 9);
	if(r) {
		std::cerr << "Zlib error: " << s.msg << " while initializing deflate." << std::endl;
		exit(3);
	}

	reset_zlib_output(&s, out, OUTBUFFER_SIZE);
	while(!finished) {
		int flushflag = Z_NO_FLUSH;

		if(s.avail_in == 0) {
			//Fill the input buffer.
			s.next_in = in;
			if(INBUFFER_SIZE > bytes_total - bytes_processed) {
				memcpy(in, pixels + bytes_processed, bytes_total - bytes_processed);
				s.avail_in = bytes_total - bytes_processed;
				bytes_processed = bytes_total;
			} else {
				memcpy(in, pixels + bytes_processed, INBUFFER_SIZE);
				s.avail_in = INBUFFER_SIZE;
				bytes_processed += INBUFFER_SIZE;
			}
		}

		//Set the finish flag if needed.
		if(bytes_processed == bytes_total)
			flushflag = Z_FINISH;
		//Compress the data.
		r = deflate(&s, flushflag);
		if(r < 0) {
			if(s.msg)
				std::cerr << "Zlib error: " << s.msg << " while deflating data." << std::endl;
			exit(3);
		}
		if(r == Z_STREAM_END)
			finished = true;
		flush_zlib_output(&s, p.rp_payload, out, OUTBUFFER_SIZE);
	}
	deflateEnd(&s);

	//Give the payload, write and free the payload.
	wchan.write(p);
	delete[] pixels;
}

int main(int argc, char** argv)
{
	if(SDL_Init(0) < 0) {
		std::cerr << "Can't initialize SDL." << std::endl;
		exit(2);
	}
	IMG_Init(IMG_INIT_JPG | IMG_INIT_PNG | IMG_INIT_TIF);

	for(int i = 1; i < argc; i++) {
		if(!always_file && !strcmp(argv[i], "--"))
			always_file = true;
		else if(!always_file && !strncmp(argv[i], "--fps=", 6))
			try {
				curtime = timecounter(argv[i] + 6);
			} catch(std::exception& e) {
				std::cerr << "Invalid --fps value: " << e.what() << std::endl;
				exit(1);
			}
		else if(!always_file && !strncmp(argv[i], "--", 2))
			usage();
		else
			add_file(std::string(argv[i]));
	}
	if(inputfiles.size() == 0)
		usage();

	std::vector<channel> channels;
	channels.resize(2);
	channels[0].c_channel = 0;				//Channel #0.
	channels[0].c_type = 0;					//Video channel.
	channels[0].c_channel_name = "<VIDEO>";			//Channel name.
	channels[1].c_channel = 1;				//Channel #1.
	channels[1].c_type = 3;					//Comment channel.
	channels[1].c_channel_name = "<DUMMY>";			//Channel name.

	//Open the dump and start new segment.
	write_channel wchan(outputfile.c_str());
	wchan.start_segment(channels);

	//For each image...
	for(std::vector<std::string>::iterator i = inputfiles.begin(); i != inputfiles.end(); ++i) {
		write_image(*i, wchan);
		curtime++;
	}

	//Closing comment packet and end stream.
	struct packet p;
	p.rp_channel = 1;					//On comment channel.
	p.rp_minor = 0;						//Ignored.
	p.rp_timestamp = curtime;				//Ending time.
	//Payload defaults to empty.
	wchan.write(p);
}
