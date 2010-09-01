#ifndef _png_out__hpp__included__
#define _png_out__hpp__included__

#include <stdint.h>
#include <string>
#include "newpacket.hpp"

struct image_frame
{
public:
	image_frame(uint32_t width, uint32_t height);
	image_frame(struct packet& p);
	~image_frame();
	image_frame(const image_frame& x);
	image_frame& operator=(const image_frame& x);
	uint32_t get_height() const;
	uint32_t get_width() const;
	unsigned char* get_pixels();				//RGB data.
	const unsigned char* get_pixels() const;		//RGB data.
	bool save_png(const std::string& name);
private:
	uint32_t width;
	uint32_t height;
	unsigned char* imagedata;		//RGB.
};

#endif
