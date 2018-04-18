#include "resize.hpp"
#include <stdexcept>
#include <iostream>
#include <zlib.h>
#include <cstring>
#include <map>
#include <string>
#include <sstream>
#include <cmath>

namespace
{
	uint32_t decode32(const unsigned char* buf)
	{
		uint32_t v = 0;
		v |= ((uint32_t)buf[0] << 24);
		v |= ((uint32_t)buf[1] << 16);
		v |= ((uint32_t)buf[2] << 8);
		v |= ((uint32_t)buf[3]);
		return v;
	}

	#define INBUF_SIZE 16384
	#define OUTBUF_SIZE 16384

	void decode_zlib(unsigned char* target, const unsigned char* src, uint32_t insize, uint32_t pixels)
	{
		unsigned char out[INBUF_SIZE];
		unsigned char in[OUTBUF_SIZE];
		uint32_t dptr = 0;

		z_stream s;
		memset(&s, 0, sizeof(s));
		int r = inflateInit(&s);
		if(r < 0) {
			std::stringstream str;
			str << "inflateInit: zlib error: " << s.msg;
			throw std::runtime_error(str.str());
		}

		s.next_in = in;
		s.avail_in = 0;
		s.next_out = out;
		s.avail_out = INBUF_SIZE;

		while(1) {
			uint32_t old_avail_out = s.avail_out;

			if(s.avail_in == 0) {
				if(insize > OUTBUF_SIZE) {
					s.next_in = in;
					s.avail_in = OUTBUF_SIZE;
					memcpy(s.next_in, src, OUTBUF_SIZE);
					src += OUTBUF_SIZE;
					insize -= OUTBUF_SIZE;
				} else {
					s.next_in = in;
					s.avail_in = insize;
					memcpy(s.next_in, src, insize);
					src += insize;
					insize = 0;
				}
			}

			r = inflate(&s, Z_NO_FLUSH);
			if(r < 0) {
				inflateEnd(&s);
				std::stringstream str;
				str << "inflate: zlib error: " << s.msg;
				throw std::runtime_error(str.str());
			}
			dptr += (old_avail_out - s.avail_out);
			if(s.avail_out == 0) {
				memcpy(target, out, INBUF_SIZE);
				target += INBUF_SIZE;
				s.avail_out = INBUF_SIZE;
				s.next_out = out;
			}
			if(dptr == 4 * pixels) {
				memcpy(target, out, INBUF_SIZE - s.avail_out);
				target += (INBUF_SIZE - s.avail_out);
				s.avail_out = INBUF_SIZE;
				s.next_out = out;
				break;
			}
			if(r == Z_STREAM_END) {
				inflateEnd(&s);
				std::stringstream str;
				str << "Uncompressed stream truncated";
				throw std::runtime_error(str.str());
			}
		}
		inflateEnd(&s);
	}
}


const unsigned char* image_frame_rgbx::get_pixels() const
{
	return imagedata;
}

unsigned char* image_frame_rgbx::get_pixels()
{
	return imagedata;
}

uint32_t image_frame_rgbx::get_height() const
{
	return height;
}

uint32_t image_frame_rgbx::get_width() const
{
	return width;
}

uint32_t image_frame_rgbx::get_numerator() const
{
	return numerator;
}

uint32_t image_frame_rgbx::get_denominator() const
{
	return denominator;
}

image_frame_rgbx::~image_frame_rgbx()
{
	delete[] imagedata;
}

image_frame_rgbx::image_frame_rgbx(uint32_t width, uint32_t height)
{
	this->width = width;
	this->height = height;
	if(width && height) {
		this->imagedata = new unsigned char[4 * width * height];
		memset(this->imagedata, 0, 4 * width * height);
	} else
		this->imagedata = NULL;
}

image_frame_rgbx::image_frame_rgbx(const image_frame_rgbx& x)
{
	this->width = x.width;
	this->height = x.height;
	if(width && height) {
		this->imagedata = new unsigned char[4 * width * height];
		memcpy(imagedata, x.imagedata, 4 * width * height);
	} else
		this->imagedata = NULL;
}

image_frame_rgbx& image_frame_rgbx::operator=(const image_frame_rgbx& x)
{
	if(this == &x)
		return *this;
	unsigned char* old_imagedata = imagedata;
	if(x.width && x.height)
		this->imagedata = new unsigned char[4 * x.width * x.height];
	else
		this->imagedata = NULL;
	this->width = x.width;
	this->height = x.height;
	if(x.width && x.height)
		memcpy(imagedata, x.imagedata, 4 * width * height);
	delete[] old_imagedata;
	return *this;
}

image_frame_rgbx::image_frame_rgbx(struct packet& p)
{
	int headersize = 4;
	if(p.rp_major != 0) {
		std::stringstream str;
		str << "frame_from_packet: Incorrect major type (" << p.rp_major << ", should be 0)";
		throw std::runtime_error(str.str());
	}
	if(p.rp_minor != 0 && p.rp_minor != 1) {
		std::stringstream str;
		str << "frame_from_packet: Unknown minor type (" << p.rp_minor << ", should be 0 or 1)";
		throw std::runtime_error(str.str());
	}
	if(p.rp_payload.size() < headersize)
		throw std::runtime_error("frame_from_packet: Malformed payload (image parameters missing)");

	uint32_t ihdr = decode32(&p.rp_payload[0]);
	width = ihdr / 65536;
	height = ihdr % 65536;
	numerator = decode32(&p.rp_payload[4]);
	denominator = decode32(&p.rp_payload[8]);
	imagedata = new unsigned char[4 * width * height];

	if(p.rp_minor == 0)
		memcpy(imagedata, &p.rp_payload[headersize], 4 * width * height);
	else if(p.rp_minor == 1)
		try {
			decode_zlib(imagedata, &p.rp_payload[headersize], p.rp_payload.size() - headersize, width * height);
		} catch(...) {
			delete[] imagedata;
			imagedata = NULL;
			throw;
		}
}

size_t image_frame_rgbx::get_data_size() const
{
	return 4 * width * height;
}

image_frame_rgbx& image_frame_rgbx::resize(uint32_t nwidth, uint32_t nheight, rescaler_group& rescalers)
{
	if(width == nwidth && height == nheight)
		return *this;
	image_frame_rgbx* newf = new image_frame_rgbx(nwidth, nheight);
	if(width == 0 && height == 0) {
		//Fill with black.
		memset(newf->get_pixels(), 0, newf->get_data_size());
		return *newf;
	}
	rescalers(newf->get_pixels(), nwidth, nheight, get_pixels(), width, height);
	return *newf;
}

