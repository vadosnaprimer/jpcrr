#include "output-drv.hpp"
#include <stdexcept>
#include <list>
#include "dedup.hpp"

class audio_dyncall_null : public audio_dyncall_base
{
	void operator()(short left, short right)
	{
	}
};

class audio_end_dyncall_null : public audio_end_dyncall_base
{
	void operator()()
	{
	}
};

class video_dyncall_null : public video_dyncall_base
{
	void operator()(uint64_t timestamp, const uint8_t* raw_rgbx_data)
	{
	}
};

class subtitle_dyncall_null : public subtitle_dyncall_base
{
	void operator()(uint64_t basetime, uint64_t duration, const uint8_t* text)
	{
	}
};

audio_settings::audio_settings(uint32_t _rate)
{
	rate = _rate;
}

uint32_t audio_settings::get_rate() const
{
	return rate;
}

audio_dyncall_base::~audio_dyncall_base()
{
}

audio_end_dyncall_base::~audio_end_dyncall_base()
{
}

video_settings::video_settings(uint32_t _width, uint32_t _height, uint32_t _rate_num, uint32_t _rate_denum)
{
	width = _width;
	height = _height;
	rate_num = _rate_num;
	rate_denum = _rate_denum;
}

uint32_t video_settings::get_width() const
{
	return width;
}

uint32_t video_settings::get_height() const
{
	return height;
}

uint32_t video_settings::get_rate_num() const
{
	return rate_num;
}

uint32_t video_settings::get_rate_denum() const
{
	return rate_denum;
}

video_dyncall_base::~video_dyncall_base()
{
}

subtitle_settings::subtitle_settings()
{
}

subtitle_dyncall_base::~subtitle_dyncall_base()
{
}

const audio_settings& output_driver::get_audio_settings()
{
	return asettings;
}

const video_settings& output_driver::get_video_settings()
{
	return vsettings;
}

const subtitle_settings& output_driver::get_subtitle_settings()
{
	return ssettings;
}

output_driver::output_driver()
	: asettings(0), vsettings(0, 0, 0, 0), ssettings()
{
	audio_handler = new audio_dyncall_null();
	audio_end_handler = new audio_end_dyncall_null();
	video_handler = new video_dyncall_null();
	subtitle_handler = new subtitle_dyncall_null();
}

output_driver::~output_driver()
{
	delete audio_handler;
	delete audio_end_handler;
	delete video_handler;
	delete subtitle_handler;
}

void output_driver::do_audio_callback(short left, short right)
{
	(*audio_handler)(left, right);
}

void output_driver::do_audio_end_callback()
{
	(*audio_end_handler)();
}

void output_driver::do_video_callback(uint64_t timestamp, const uint8_t* raw_rgbx_data)
{
	(*video_handler)(timestamp, raw_rgbx_data);
}

void output_driver::do_subtitle_callback(uint64_t basetime, uint64_t duration, const uint8_t* text)
{
	(*subtitle_handler)(basetime, duration, text);
}

void output_driver::set_audio_settings(audio_settings a)
{
	asettings = a;
}

void output_driver::set_video_settings(video_settings v)
{
	vsettings = v;
}

void output_driver::set_subtitle_settings(subtitle_settings s)
{
	ssettings = s;
}

std::map<std::string, output_driver_factory*>* output_driver_factory::factories;

output_driver_factory::~output_driver_factory()
{
}

output_driver_factory::output_driver_factory(const std::string& type)
{
	if(!factories)
		factories = new std::map<std::string, output_driver_factory*>();
	(*factories)[type] = this;
}

output_driver& output_driver_factory::make_by_type(const std::string& type, const std::string& name,
	const std::string& parameters)
{
	if(!factories || !factories->count(type))
		throw std::runtime_error("Unknown output driver type");
	return (*factories)[type]->make(type, name, parameters);
}

namespace
{
	std::list<output_driver*> drivers;
	audio_settings asettings(0);
	video_settings vsettings(0, 0, 0, 0);
	subtitle_settings ssettings;
	dedup dedupper(0, 0, 0);
}

void distribute_audio_callback(short left, short right)
{
	for(std::list<output_driver*>::iterator i = drivers.begin(); i != drivers.end(); ++i)
		(*i)->do_audio_callback(left, right);
}
void distribute_video_callback(uint64_t timestamp, const uint8_t* raw_rgbx_data)
{
	for(std::list<output_driver*>::iterator i = drivers.begin(); i != drivers.end(); ++i)
		(*i)->do_video_callback(timestamp, raw_rgbx_data);
}

void distribute_subtitle_callback(uint64_t basetime, uint64_t duration, const uint8_t* text)
{
	for(std::list<output_driver*>::iterator i = drivers.begin(); i != drivers.end(); ++i)
		(*i)->do_subtitle_callback(basetime, duration, text);
}

void set_audio_parameters(audio_settings a)
{
	asettings = a;
}

void set_video_parameters(video_settings v)
{
	vsettings = v;
}

void set_subtitle_parameters(subtitle_settings s)
{
	ssettings = s;
}

void add_output_driver(const std::string& type, const std::string& name, const std::string& parameters)
{
	output_driver& driver = output_driver_factory::make_by_type(type, name, parameters);
	drivers.push_back(&driver);
	driver.set_audio_settings(asettings);
	driver.set_video_settings(vsettings);
	driver.set_subtitle_settings(ssettings);
	driver.ready();
}

void close_output_drivers()
{
	for(std::list<output_driver*>::iterator i = drivers.begin(); i != drivers.end(); ++i)
		(*i)->do_audio_end_callback();
	for(std::list<output_driver*>::iterator i = drivers.begin(); i != drivers.end(); ++i)
		delete *i;
	drivers.clear();
}

void distribute_audio_callback(npair<short> sample)
{
	distribute_audio_callback(sample.get_x(), sample.get_y());
}

void distribute_subtitle_callback(struct packet& p)
{
	uint64_t basetime = p.rp_timestamp;
	uint64_t duration = 0;
	uint8_t* text = NULL;
	if(p.rp_major != 4 || p.rp_minor != 0 || p.rp_payload.size() < 8)
		return;		//Bad subtitle packet.
	for(size_t i = 0; i < 8; i++)
		duration |= ((uint64_t)p.rp_payload[i] << (56 - 8 * i));
	text = &p.rp_payload[8];
	distribute_subtitle_callback(basetime, duration, text);
}

std::string get_output_driver_list()
{
	bool first = true;
	if(!output_driver_factory::factories)
		return "";
	std::string c;
	std::map<std::string, output_driver_factory*>& f = *output_driver_factory::factories;
	for(std::map<std::string, output_driver_factory*>::iterator i = f.begin(); i != f.end(); ++i) {
		if(first)
			c = i->first;
		else
			c = c + " " + i->first;
		first = false;
	}
	return c;
}
