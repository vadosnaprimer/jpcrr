#ifndef _resize__hpp__included__
#define _resize__hpp__included__

#include <stdint.h>
#include <string>
#include "newpacket.hpp"
#include "rescalers/public.hpp"

struct image_frame_rgbx
{
public:
	image_frame_rgbx(uint32_t width, uint32_t height);
	image_frame_rgbx(struct packet& p);
	~image_frame_rgbx();
	image_frame_rgbx(const image_frame_rgbx& x);
	image_frame_rgbx& operator=(const image_frame_rgbx& x);
	uint32_t get_height() const;
	uint32_t get_width() const;
	unsigned char* get_pixels();				//RGBx data.
	const unsigned char* get_pixels() const;		//RGBx data.
	size_t get_data_size() const;				//Bytes.
	//This returns the frame itself if it is already of suitable size.
	image_frame_rgbx& resize(uint32_t nwidth, uint32_t nheight, rescaler_group& rescalers);
private:
	uint32_t width;
	uint32_t height;
	unsigned char* imagedata;		//RGBx.
};

std::string get_resizer_list();

#endif
