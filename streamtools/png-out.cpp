#include "png-out.hpp"
#include <zlib.h>
#include <cstdio>
#include <cstring>
#include <stdexcept>
#include <sstream>

namespace
{
	void encode32(unsigned char* buf, uint32_t val)
	{
		buf[0] = ((val >> 24) & 0xFF);
		buf[1] = ((val >> 16) & 0xFF);
		buf[2] = ((val >> 8) & 0xFF);
		buf[3] = (val & 0xFF);
	}

	uint32_t decode32(const unsigned char* buf)
	{
		uint32_t v = 0;
		v |= ((uint32_t)buf[0] << 24);
		v |= ((uint32_t)buf[1] << 16);
		v |= ((uint32_t)buf[2] << 8);
		v |= ((uint32_t)buf[3]);
		return v;
	}

	void write_hunk(FILE* filp, uint32_t typecode, const unsigned char* data, uint32_t datasize)
	{
		uLong crc = crc32(0, NULL, 0);
		unsigned char fixed[12];
		encode32(fixed, datasize);
		encode32(fixed + 4, typecode);
		crc = crc32(crc, fixed + 4, 4);
		if(datasize > 0)
			crc = crc32(crc, data, datasize);
		encode32(fixed + 8, crc);

		if(fwrite(fixed, 1, 8, filp) < 8)
			throw std::runtime_error("Can't write PNG file hunk header");
		if(datasize > 0 && fwrite(data, 1, datasize, filp) < datasize)
			throw std::runtime_error("Can't write PNG file hunk contents");
		if(fwrite(fixed + 8, 1, 4, filp) < 4)
			throw std::runtime_error("Can't write PNG file hunk checksum");
	}

	void write_magic(FILE* filp)
	{
		unsigned char magic[] = {137, 80, 78, 71, 13, 10, 26, 10};
		if(fwrite(magic, 1, sizeof(magic), filp) < sizeof(magic))
			throw std::runtime_error("Can't write PNG file magic");
	}

	void write_ihdr(FILE* filp, struct image_frame& image)
	{
		unsigned char ihdr[] = {25, 25, 25, 25, 25, 25, 25, 25, 8, 2, 0, 0, 0};
		encode32(ihdr, image.get_width());
		encode32(ihdr + 4, image.get_height());
		write_hunk(filp, 0x49484452, ihdr, sizeof(ihdr));
	}

	void write_iend(FILE* filp)
	{
		write_hunk(filp, 0x49454E44, NULL, 0);
	}

	void write_idat(FILE* filp, const unsigned char* data, uint32_t datasize)
	{
		write_hunk(filp, 0x49444154, data, datasize);
	}

	void write_idat_zlib_flush(FILE* filp, z_stream* s, uint32_t buffer)
	{
		if(s->avail_out >= buffer)
			return;
		write_idat(filp, s->next_out + s->avail_out - buffer, buffer - s->avail_out);
	}

	#define INBUF_SIZE 16384
	#define OUTBUF_SIZE 16384

	int output_png2(FILE* filp, struct image_frame& image)
	{
		write_magic(filp);
		write_ihdr(filp, image);

		z_stream s;
		memset(&s, 0, sizeof(s));
		int r = deflateInit(&s, 9);
		if(r < 0) {
			std::stringstream str;
			str << "deflateInit: zlib error: " << s.msg;
			throw std::runtime_error(str.str());
		}

		unsigned char in[INBUF_SIZE];
		unsigned char out[OUTBUF_SIZE];
		s.avail_out = OUTBUF_SIZE;
		s.next_out = out;
		int finish_flag = 0;
		uint32_t total_data = (3 * image.get_width() * image.get_height() + image.get_height());
		uint32_t data_emitted = 0;
		uint32_t filter_divisior = 3 * image.get_width() + 1;
		uint32_t dataptr = 0;
		while(1) {
			if(s.avail_in == 0) {
				s.next_in = in;
				//Input buffer empty. Fill it.
				while(data_emitted < total_data && s.avail_in < INBUF_SIZE) {
					if(data_emitted % filter_divisior == 0)
						s.next_in[s.avail_in++] = 0;	//Filter none.
					else
						s.next_in[s.avail_in++] = (image.get_pixels())[dataptr++];
					data_emitted++;
				}
				if(data_emitted == total_data)
					finish_flag = 1;
			}
			r = deflate(&s, finish_flag ? Z_FINISH : Z_NO_FLUSH);
			if(r < 0) {
				std::stringstream str;
				str << "deflate: zlib error: " << s.msg;
				deflateEnd(&s);
				throw std::runtime_error(str.str());
			}
			if(s.avail_out == 0 || finish_flag) {
				try {
					write_idat_zlib_flush(filp, &s, OUTBUF_SIZE);
				} catch(...) {
					deflateEnd(&s);
					throw;
				}
				s.avail_out = OUTBUF_SIZE;
				s.next_out = out;
			}
			if(r == Z_STREAM_END)
				break;
		}

		deflateEnd(&s);
		write_iend(filp);
		return 0;
	}


	bool output_png(const char* name, struct image_frame& image)
	{
		if(!image.get_width() || !image.get_height())
			return false;

		FILE* filp = fopen(name, "wb");
		if(!filp) {
			std::stringstream str;
			str << "Can't open PNG output file '" << name << "'";
			throw std::runtime_error(str.str());
		}

		try {
			output_png2(filp, image);
		} catch(...) {
			fclose(filp);
			throw;
		}

		if(fclose(filp) < 0) {
			std::stringstream str;
			str << "Can't close PNG output file '" << name << "'";
			throw std::runtime_error(str.str());
		}
		return true;
	}

	void copy4to3(unsigned char* target, const unsigned char* src, uint32_t pixels)
	{
		for(uint32_t i = 0; i < pixels; i++) {
			target[3 * i + 0] = src[4 * i + 0];
			target[3 * i + 1] = src[4 * i + 1];
			target[3 * i + 2] = src[4 * i + 2];
		}
	}

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
				copy4to3(target, out, INBUF_SIZE / 4);
				target += 3 * (INBUF_SIZE / 4);
				s.avail_out = INBUF_SIZE;
				s.next_out = out;
			}
			if(dptr == 4 * pixels) {
				copy4to3(target, out, (INBUF_SIZE - s.avail_out) / 4);
				target += 3 * ((INBUF_SIZE - s.avail_out) / 4);
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

const unsigned char* image_frame::get_pixels() const
{
	return imagedata;
}

unsigned char* image_frame::get_pixels()
{
	return imagedata;
}

uint32_t image_frame::get_height() const
{
	return height;
}

uint32_t image_frame::get_width() const
{
	return width;
}

image_frame::~image_frame()
{
	delete[] imagedata;
}

image_frame::image_frame(uint32_t width, uint32_t height)
{
	this->width = width;
	this->height = height;
	this->imagedata = new unsigned char[3 * width * height];
}

image_frame::image_frame(const image_frame& x)
{
	this->width = x.width;
	this->height = x.height;
	this->imagedata = new unsigned char[3 * width * height];
	memcpy(imagedata, x.imagedata, 3 * width * height);
}

image_frame& image_frame::operator=(const image_frame& x)
{
	if(this == &x)
		return *this;
	unsigned char* old_imagedata = imagedata;
	this->imagedata = new unsigned char[3 * width * height];
	this->width = x.width;
	this->height = x.height;
	memcpy(imagedata, x.imagedata, 3 * width * height);
	delete[] old_imagedata;
	return *this;
}

image_frame::image_frame(struct packet& p)
{
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
	if(p.rp_payload.size() < 4)
		throw std::runtime_error("frame_from_packet: Malformed payload (image parameters missing)");

	uint32_t ihdr = decode32(&p.rp_payload[0]);
	width = ihdr / 65536;
	height = ihdr % 65536;
	imagedata = new unsigned char[3 * width * height];

	if(p.rp_minor == 0)
		copy4to3(imagedata, &p.rp_payload[4], width * height);
	else if(p.rp_minor == 1)
		try {
			decode_zlib(imagedata, &p.rp_payload[4], p.rp_payload.size() - 4, width * height);
		} catch(...) {
			delete[] imagedata;
			imagedata = NULL;
		}
}

bool image_frame::save_png(const std::string& name)
{
	return output_png(name.c_str(), *this);
}
