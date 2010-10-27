#ifndef _resize__hpp__included__
#define _resize__hpp__included__

#include <stdint.h>
#include <string>
#include <list>
#include <SDL.h>
#include "newpacket.hpp"

class resizer
{
public:
	virtual ~resizer();
	virtual void operator()(uint8_t* target, uint32_t twidth, uint32_t theight,
		const uint8_t* source, uint32_t swidth, uint32_t sheight) = 0;
};

class resizer_factory
{
public:
	resizer_factory(const std::string& type);
	virtual ~resizer_factory();
	static resizer& make_by_type(const std::string& type);
	virtual resizer& make(const std::string& type) = 0;
private:
	static std::map<std::string, resizer_factory*>* factories;
	friend std::string get_resizer_list();
	friend std::list<std::string> get_resizer_list2();
};

class simple_resizer : public resizer_factory
{
public:
	simple_resizer(const std::string& type, void(*resize_fn)(uint8_t* target, uint32_t twidth, uint32_t theight,
		const uint8_t* source, uint32_t swidth, uint32_t sheight));
	simple_resizer(const std::string& type, void(*resize_fn)(uint8_t* target, uint32_t twidth, uint32_t theight,
		const uint8_t* source, uint32_t swidth, uint32_t sheight, int algo), int algo);
	resizer& make(const std::string& type);
private:
	void(*resize_fn)(uint8_t* target, uint32_t twidth, uint32_t theight, const uint8_t* source, uint32_t swidth,
		uint32_t sheight);
	void(*resize_fn2)(uint8_t* target, uint32_t twidth, uint32_t theight, const uint8_t* source, uint32_t swidth,
		uint32_t sheight, int algo);
	int algo;
};


struct image_frame_rgbx
{
public:
	image_frame_rgbx(uint32_t width, uint32_t height);
	image_frame_rgbx(struct packet& p);
	image_frame_rgbx(SDL_Surface* surf);
	~image_frame_rgbx();
	image_frame_rgbx(const image_frame_rgbx& x);
	image_frame_rgbx& operator=(const image_frame_rgbx& x);
	void get_ref();
	void put_ref();
	uint32_t get_height() const;
	uint32_t get_width() const;
	unsigned char* get_pixels();				//RGBx data.
	const unsigned char* get_pixels() const;		//RGBx data.
	size_t get_data_size() const;				//Bytes.
	SDL_Surface* get_surface() const;
	//This returns the frame itself if it is already of suitable size.
	image_frame_rgbx& resize(uint32_t nwidth, uint32_t nheight, resizer& using_resizer);
	void* operator new(size_t size);
	void operator delete(void* memory);
private:
	uint32_t width;
	uint32_t height;
	unsigned char* imagedata;		//RGBx.
	uint32_t refs;
};


std::string get_resizer_list();
std::list<std::string> get_resizer_list2();

#endif
