#ifndef _outputs__internal__hpp__included__
#define _outputs__internal__hpp__included__

#include "outputs/public.hpp"
#include "bound-method.hpp"
#include <map>

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
	void set_audio_callback(bound_method<void, short, short> fn);
	void set_audio_end_callback(bound_method<void> fn);
	void set_video_callback(bound_method<void, uint64_t, const uint8_t*> fn);
	void set_subtitle_callback(bound_method<void, uint64_t, uint64_t, const uint8_t*> fn);
	void set_gmidi_callback(bound_method<void, uint64_t, uint8_t> fn);
	void do_audio_callback(short left, short right);
	void do_audio_end_callback();
	void do_video_callback(uint64_t ts, const uint8_t* framedata);
	void do_subtitle_callback(uint64_t ts, uint64_t duration, const uint8_t* text);
	void do_gmidi_callback(uint64_t ts, uint8_t data);
private:
	template<typename... args> void do_callback(bound_method<void, args...> output_driver::*slot,
		args... arg);
	bound_method<void, short, short> audio_callback;
	bound_method<void> audio_end_callback;
	bound_method<void, uint64_t, const uint8_t*> video_callback;
	bound_method<void, uint64_t, uint64_t, const uint8_t*> subtitle_callback;
	bound_method<void, uint64_t, uint8_t> gmidi_callback;
	audio_settings asettings;
	video_settings vsettings;
	subtitle_settings ssettings;
};

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
	friend std::string get_output_driver_string();
	friend std::list<std::string> get_output_driver_list();
};

#endif
