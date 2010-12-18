#ifndef _outputs__public__hpp__included__
#define _outputs__public__hpp__included__

#include <cstdint>
#include <string>
#include <list>

class output_driver;

class audio_settings
{
public:
	audio_settings(uint32_t _rate);
	uint32_t get_rate() const;
private:
	uint32_t rate;
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

class subtitle_settings
{
public:
	subtitle_settings();
private:
};

class output_driver_group
{
public:
	output_driver_group();
	~output_driver_group();
	//Set the settings first before adding drivers!
	void set_audio_settings(audio_settings a);
	void set_video_settings(video_settings v);
	void set_subtitle_settings(subtitle_settings s);
	void add_driver(const std::string& type, const std::string& name, const std::string& parameters);
	void do_audio_callback(short left, short right);
	void do_audio_end_callback();
	void do_video_callback(uint64_t ts, const uint8_t* framedata);
	void do_gmidi_callback(uint64_t ts, uint8_t data);
	void do_subtitle_callback(uint64_t ts, uint64_t duration, const uint8_t* text);
	template<typename... args> void do_callback(void (output_driver::*slot)(args... arg), args... arg);
private:
	audio_settings asettings;
	video_settings vsettings;
	subtitle_settings ssettings;
	std::list<output_driver*> drivers;
};

std::string get_output_driver_string();
std::list<std::string> get_output_driver_list();

#endif
