#include "resize.hpp"
#include <stdexcept>
#include <iostream>
#include <cassert>
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

	void copy_surface_row(unsigned char* dest, unsigned char* src, SDL_PixelFormat *format, uint32_t width)
	{
		unsigned char tmpbuf[4 * width];
		memset(tmpbuf, 0, 4 * width);
		memcpy(tmpbuf, src, format->BytesPerPixel * width);
		for(uint32_t j = 0; j < width; j++) {
			uint32_t val = *(uint32_t*)(tmpbuf + format->BytesPerPixel * j);
			SDL_GetRGBA(val, format, dest + 4 * j + 0, dest + 4 * j + 1, dest + 4 * j + 2,
				dest + 4 * j + 3);
		}
	}
}

void image_frame_rgbx::get_ref()
{
	refs++;
}

void image_frame_rgbx::put_ref()
{
	if(!--refs)
		delete this;
}

void* image_frame_rgbx::operator new(size_t size)
{
	void* mem = malloc(size);
	if(!mem)
		throw std::bad_alloc();
	return mem;
}

void image_frame_rgbx::operator delete(void* mem)
{
	assert(((image_frame_rgbx*)mem)->refs == 0);
	free(mem);
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
	refs = 1;
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
	refs = 1;
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
	//Don't alter reference count.
	return *this;
}

image_frame_rgbx::image_frame_rgbx(struct packet& p)
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
	imagedata = new unsigned char[4 * width * height];

	if(p.rp_minor == 0)
		memcpy(imagedata, &p.rp_payload[4], 4 * width * height);
	else if(p.rp_minor == 1)
		try {
			decode_zlib(imagedata, &p.rp_payload[4], p.rp_payload.size() - 4, width * height);
		} catch(...) {
			delete[] imagedata;
			imagedata = NULL;
			throw;
		}
	refs = 1;
}

size_t image_frame_rgbx::get_data_size() const
{
	return 4 * width * height;
}

image_frame_rgbx& image_frame_rgbx::resize(uint32_t nwidth, uint32_t nheight, resizer& using_resizer)
{
	if(width == nwidth && height == nheight) {
		refs++;		//New ref.
		return *this;
	}
	image_frame_rgbx* newf = new image_frame_rgbx(nwidth, nheight);
	if(width == 0 && height == 0) {
		//Fill with black.
		memset(newf->get_pixels(), 0, newf->get_data_size());
		return *newf;
	}
	using_resizer(newf->get_pixels(), nwidth, nheight, get_pixels(), width, height);
	return *newf;
}

image_frame_rgbx::image_frame_rgbx(SDL_Surface* surf)
{
	width = surf->w;
	height = surf->h;
	refs = 1;
	if(width && height) {
		this->imagedata = new unsigned char[4 * width * height];
	} else {
		this->imagedata = NULL;
		return;
	}
	//Ok, copy the image data into pixel buffer.
	SDL_LockSurface(surf);
	for(uint32_t i = 0; i < height; i++)
		copy_surface_row(this->imagedata + 4 * width * i, (unsigned char*)surf->pixels + surf->pitch * i,
			surf->format, width);
	SDL_UnlockSurface(surf);
}

SDL_Surface* image_frame_rgbx::get_surface() const
{
#if SDL_BYTEORDER == SDL_BIG_ENDIAN
	uint32_t rmask = 0xFF000000;
	uint32_t gmask = 0xFF0000;
	uint32_t bmask = 0xFF00;
	uint32_t amask = 0xFF;
#else
	uint32_t amask = 0xFF000000;
	uint32_t bmask = 0xFF0000;
	uint32_t gmask = 0xFF00;
	uint32_t rmask = 0xFF;
#endif
	SDL_Surface* surf = SDL_CreateRGBSurface(SDL_SWSURFACE, width, height, 32, rmask, gmask, bmask, amask);
	if(!surf)
		throw std::bad_alloc();
	SDL_LockSurface(surf);
	memcpy(surf->pixels, imagedata, 4 * width * height);
	SDL_UnlockSurface(surf);
	return surf;
}

resizer::~resizer()
{
}

std::map<std::string, resizer_factory*>* resizer_factory::factories;

resizer_factory::~resizer_factory()
{
}

resizer_factory::resizer_factory(const std::string& type)
{
	if(!factories)
		factories = new std::map<std::string, resizer_factory*>();
	(*factories)[type] = this;
}

resizer& resizer_factory::make_by_type(const std::string& type)
{
	if(!factories || !factories->count(type))
		throw std::runtime_error("Unknown output driver type");
	return (*factories)[type]->make(type);
}

std::string get_resizer_list()
{
	bool first = true;
	if(!resizer_factory::factories)
		return "";
	std::string c;
	std::map<std::string, resizer_factory*>& f = *resizer_factory::factories;
	for(std::map<std::string, resizer_factory*>::iterator i = f.begin(); i != f.end(); ++i) {
		if(first)
			c = i->first;
		else
			c = c + " " + i->first;
		first = false;
	}
	return c;
}

std::list<std::string> get_resizer_list2()
{
	std::list<std::string> ret;
	if(!resizer_factory::factories)
		return ret;
	std::map<std::string, resizer_factory*>& f = *resizer_factory::factories;
	for(std::map<std::string, resizer_factory*>::iterator i = f.begin(); i != f.end(); ++i)
		ret.push_back(i->first);
	return ret;
}

namespace
{
	class simple_resizer_c : public resizer
	{
	public:
		simple_resizer_c(void(*_resize_fn)(uint8_t* target, uint32_t twidth, uint32_t theight,
			const uint8_t* source, uint32_t swidth, uint32_t sheight))
		{
			resize_fn = _resize_fn;
		}

		void operator()(uint8_t* target, uint32_t twidth, uint32_t theight,
			const uint8_t* source, uint32_t swidth, uint32_t sheight)
		{
			resize_fn(target, twidth, theight, source, swidth, sheight);
		}

	private:
		void(*resize_fn)(uint8_t* target, uint32_t twidth, uint32_t theight,
			const uint8_t* source, uint32_t swidth, uint32_t sheight);
	};

	class simple_resizer_c2 : public resizer
	{
	public:
		simple_resizer_c2(void(*_resize_fn)(uint8_t* target, uint32_t twidth, uint32_t theight,
			const uint8_t* source, uint32_t swidth, uint32_t sheight, int algo), int _algo)
		{
			resize_fn = _resize_fn;
			algo = _algo;
		}

		void operator()(uint8_t* target, uint32_t twidth, uint32_t theight,
			const uint8_t* source, uint32_t swidth, uint32_t sheight)
		{
			resize_fn(target, twidth, theight, source, swidth, sheight, algo);
		}

	private:
		void(*resize_fn)(uint8_t* target, uint32_t twidth, uint32_t theight,
			const uint8_t* source, uint32_t swidth, uint32_t sheight, int algo);
		int algo;
	};
}

simple_resizer::simple_resizer(const std::string& type, void(*_resize_fn)(uint8_t* target, uint32_t twidth, uint32_t theight,
	const uint8_t* source, uint32_t swidth, uint32_t sheight))
	: resizer_factory(type)
{
	resize_fn = _resize_fn;
	resize_fn2 = NULL;
	algo = 0;
}

simple_resizer::simple_resizer(const std::string& type, void(*_resize_fn)(uint8_t* target, uint32_t twidth, uint32_t theight,
	const uint8_t* source, uint32_t swidth, uint32_t sheight, int algo), int _algo)
	: resizer_factory(type)
{
	resize_fn = NULL;
	resize_fn2 = _resize_fn;
	algo = _algo;
}

resizer& simple_resizer::make(const std::string& type)
{
	if(resize_fn)
		return *new simple_resizer_c(resize_fn);
	else
		return *new simple_resizer_c2(resize_fn2, algo);
}
