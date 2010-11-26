#ifndef _output_drv__hpp__included__
#define _output_drv__hpp__included__

#include <stdint.h>
#include <string>
#include <map>
#include "resize.hpp"
#include "digital-filter.hpp"

class audio_settings
{
public:
	audio_settings(uint32_t _rate);
	uint32_t get_rate() const;
private:
	uint32_t rate;
};

class audio_dyncall_base
{
public:
	virtual ~audio_dyncall_base();
	virtual void operator()(short left, short right) = 0;
};

class audio_end_dyncall_base
{
public:
	virtual ~audio_end_dyncall_base();
	virtual void operator()() = 0;
};

template<class T>
class audio_dyncall : public audio_dyncall_base
{
public:
	audio_dyncall(T& _object, void (T::*_ptr)(short left, short right))
		: object(_object)
	{
		ptr = _ptr;
	}

	~audio_dyncall()
	{
	}

	void operator()(short left, short right)
	{
		(object.*ptr)(left, right);
	}
private:
	T& object;
	void (T::*ptr)(short left, short right);
};

template<class T>
class audio_end_dyncall : public audio_end_dyncall_base
{
public:
	audio_end_dyncall(T& _object, void (T::*_ptr)())
		: object(_object)
	{
		ptr = _ptr;
	}

	~audio_end_dyncall()
	{
	}

	void operator()()
	{
		(object.*ptr)();
	}
private:
	T& object;
	void (T::*ptr)();
};

class video_settings
{
public:
	video_settings(uint32_t _width, uint32_t _height, uint32_t _rate_num, uint32_t _rate_denum);
	uint32_t get_width() const;
	uint32_t get_height() const;
	uint32_t get_rate_num() const;
	uint32_t get_rate_denum() const;	//WARNING: Zero if framerate is variable!
private:
	uint32_t width;
	uint32_t height;
	uint32_t rate_num;
	uint32_t rate_denum;
};

class video_dyncall_base
{
public:
	virtual ~video_dyncall_base();
	virtual void operator()(uint64_t timestamp, const uint8_t* raw_rgbx_data) = 0;
};

template<class T>
class video_dyncall : public video_dyncall_base
{
public:
	video_dyncall(T& _object, void (T::*_ptr)(uint64_t timestamp, const uint8_t* raw_rgbx_data))
		: object(_object)
	{
		ptr = _ptr;
	}

	~video_dyncall()
	{
	}

	void operator()(uint64_t timestamp, const uint8_t* raw_rgbx_data)
	{
		(object.*ptr)(timestamp, raw_rgbx_data);
	}
private:
	T& object;
	void (T::*ptr)(uint64_t timestamp, const uint8_t* raw_rgbx_data);
};

class subtitle_settings
{
public:
	subtitle_settings();
private:
};

class subtitle_dyncall_base
{
public:
	virtual ~subtitle_dyncall_base();
	virtual void operator()(uint64_t basetime, uint64_t duration, const uint8_t* text) = 0;
};

template<class T>
class subtitle_dyncall : public subtitle_dyncall_base
{
public:
	subtitle_dyncall(T& _object, void (T::*_ptr)(uint64_t basetime, uint64_t duration, const uint8_t* text))
		: object(_object)
	{
		ptr = _ptr;
	}

	~subtitle_dyncall()
	{
	}

	void operator()(uint64_t basetime, uint64_t duration, const uint8_t* text)
	{
		(object.*ptr)(basetime, duration, text);
	}
private:
	T& object;
	void (T::*ptr)(uint64_t basetime, uint64_t duration, const uint8_t* text);
};


class output_driver
{
public:
	output_driver();
	virtual ~output_driver();
	virtual void ready() = 0;
	void set_audio_settings(audio_settings a);
	void set_video_settings(video_settings v);
	void set_subtitle_settings(subtitle_settings s);
	const audio_settings& get_audio_settings();
	const video_settings& get_video_settings();
	const subtitle_settings& get_subtitle_settings();
	template<class T> void set_audio_callback(T& object, void (T::*ptr)(short left, short right))
	{
		audio_dyncall_base* old = audio_handler;
		audio_handler = new audio_dyncall<T>(object, ptr);
		delete old;
	}

	template<class T> void set_audio_end_callback(T& object, void (T::*ptr)())
	{
		audio_end_dyncall_base* old = audio_end_handler;
		audio_end_handler = new audio_end_dyncall<T>(object, ptr);
		delete old;
	}

	template<class T> void set_video_callback(T& object, void (T::*ptr)(uint64_t timestamp,
		const uint8_t* raw_rgbx_data))
	{
		video_dyncall_base* old = video_handler;
		video_handler = new video_dyncall<T>(object, ptr);
		not_default_video_handler = true;
		delete old;
	}

	template<class T> void set_subtitle_callback(T& object, void (T::*ptr)(uint64_t basetime, uint64_t duration,
		const uint8_t* text))
	{
		subtitle_dyncall_base* old = subtitle_handler;
		subtitle_handler = new subtitle_dyncall<T>(object, ptr);
		delete old;
	}

	void do_audio_callback(short left, short right);
	void do_audio_end_callback();
	void do_video_callback(uint64_t timestamp, const uint8_t* raw_rgbx_data);
	void do_subtitle_callback(uint64_t basetime, uint64_t duration, const uint8_t* text);
	bool not_default_video_handler;
private:
	output_driver(const output_driver&);
	output_driver& operator=(const output_driver&);
	audio_dyncall_base* audio_handler;
	audio_end_dyncall_base* audio_end_handler;
	video_dyncall_base* video_handler;
	subtitle_dyncall_base* subtitle_handler;
	audio_settings asettings;
	video_settings vsettings;
	subtitle_settings ssettings;
};

void distribute_audio_callback(short left, short right);
void distribute_audio_callback(npair<short> sample);
void distribute_video_callback(uint64_t timestamp, const uint8_t* raw_rgbx_data);
void distribute_subtitle_callback(uint64_t basetime, uint64_t duration, const uint8_t* text);
void distribute_subtitle_callback(struct packet& p);
void set_audio_parameters(audio_settings a);
void set_video_parameters(video_settings v);
void set_subtitle_parameters(subtitle_settings s);
void add_output_driver(const std::string& type, const std::string& name, const std::string& parameters);
void close_output_drivers();

std::string get_output_driver_list();

class output_driver_factory
{
public:
	output_driver_factory(const std::string& type);
	virtual ~output_driver_factory();
	static output_driver& make_by_type(const std::string& type, const std::string& name,
		const std::string& parameters);
	virtual output_driver& make(const std::string& type, const std::string& name,
		const std::string& parameters) = 0;
private:
	static std::map<std::string, output_driver_factory*>* factories;
	friend std::string get_output_driver_list();
};

std::string expand_arguments_common(std::string spec, std::string commaexpand, std::string equalsexpand);
void I420_convert_common(const uint8_t* raw_rgbx_data, uint32_t width, uint32_t height, FILE* out, bool uvswap);
void I420_convert_common(const uint8_t* raw_rgbx_data, uint32_t width, uint32_t height, std::ostream& out,
	bool uvswap);

#endif
