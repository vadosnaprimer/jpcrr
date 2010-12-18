#include "outputs/public.hpp"
#include "outputs/internal.hpp"

void output_driver::set_audio_callback(bound_method<void, short, short> fn)
{
	audio_callback = fn;
}

void output_driver::set_gmidi_callback(bound_method<void, uint64_t, uint8_t> fn)
{
	gmidi_callback = fn;
}

void output_driver::set_audio_end_callback(bound_method<void> fn)
{
	audio_end_callback = fn;
}

void output_driver::set_video_callback(bound_method<void, uint64_t, const uint8_t*> fn)
{
	video_callback = fn;
}

void output_driver::set_subtitle_callback(bound_method<void, uint64_t, uint64_t, const uint8_t*> fn)
{
	subtitle_callback = fn;
}

void output_driver::do_audio_callback(short left, short right)
{
	do_callback(&output_driver::audio_callback, left, right);
}

void output_driver::do_gmidi_callback(uint64_t ts, uint8_t data)
{
	do_callback(&output_driver::gmidi_callback, ts, data);
}

void output_driver::do_audio_end_callback()
{
	do_callback(&output_driver::audio_end_callback);
}

void output_driver::do_video_callback(uint64_t ts, const uint8_t* framedata)
{
	do_callback(&output_driver::video_callback, ts, framedata);
}

void output_driver::do_subtitle_callback(uint64_t ts, uint64_t duration, const uint8_t* text)
{
	do_callback(&output_driver::subtitle_callback, ts, duration, text);
}

template<typename... args> void output_driver::do_callback(bound_method<void, args...> output_driver::*slot,
	args... arg)
{
	if(!(this->*slot))
		return;		//No handler.
	(this->*slot)(arg...);
}

void output_driver_group::do_audio_callback(short left, short right)
{
	do_callback(&output_driver::do_audio_callback, left, right);
}

void output_driver_group::do_gmidi_callback(uint64_t ts, uint8_t data)
{
	do_callback(&output_driver::do_gmidi_callback, ts, data);
}

void output_driver_group::do_audio_end_callback()
{
	do_callback(&output_driver::do_audio_end_callback);
}

void output_driver_group::do_video_callback(uint64_t ts, const uint8_t* framedata)
{
	do_callback(&output_driver::do_video_callback, ts, framedata);
}

void output_driver_group::do_subtitle_callback(uint64_t ts, uint64_t duration, const uint8_t* text)
{
	do_callback(&output_driver::do_subtitle_callback, ts, duration, text);
}

template<typename... args> void output_driver_group::do_callback(void (output_driver::*slot)(args... arg),
	args... arg)
{
	for(auto i = drivers.begin(); i != drivers.end(); i++)
		((*i)->*slot)(arg...);
}

output_driver_group::output_driver_group()
	: asettings(0), vsettings(0, 0, 0, 0)
{
}

output_driver_group::~output_driver_group()
{
	for(auto i = drivers.begin(); i != drivers.end(); i++)
		delete *i;
}

void output_driver_group::set_audio_settings(audio_settings a)
{
	asettings = a;
}

void output_driver_group::set_video_settings(video_settings v)
{
	vsettings = v;
}

void output_driver_group::set_subtitle_settings(subtitle_settings s)
{
	ssettings = s;
}

void output_driver_group::add_driver(const std::string& type, const std::string& name, const std::string& parameters)
{
	output_driver& drv = output_driver_factory::make_by_type(type, name, parameters);
	drivers.push_back(&drv);
	drv.set_audio_settings(asettings);
	drv.set_video_settings(vsettings);
	drv.set_subtitle_settings(ssettings);
	drv.ready();
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
}

output_driver::~output_driver()
{
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

audio_settings::audio_settings(uint32_t _rate)
{
	rate = _rate;
}

uint32_t audio_settings::get_rate() const
{
	return rate;
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

subtitle_settings::subtitle_settings()
{
}

std::string get_output_driver_string()
{
	bool first = true;
	if(!output_driver_factory::factories)
		return "";
	std::string c;
	std::map<std::string, output_driver_factory*>& f = *output_driver_factory::factories;
	for(auto i = f.begin(); i != f.end(); ++i) {
		if(first)
			c = i->first;
		else
			c = c + " " + i->first;
		first = false;
	}
	return c;
}

std::list<std::string> get_output_driver_list()
{
	std::list<std::string> l;
	std::map<std::string, output_driver_factory*>& f = *output_driver_factory::factories;
	for(auto i = f.begin(); i != f.end(); ++i)
		l.push_back(i->first);
	return l;
}
