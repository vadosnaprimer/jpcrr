#ifndef _hardsubs__hpp__included__
#define _hardsubs__hpp__included__

#include "png-out.hpp"
#include "newpacket.hpp"
#include "resize.hpp"
#include <list>
#include <string>

#define ALIGN_LEFT 0
#define ALIGN_TOP 0
#define ALIGN_CENTER 1
#define ALIGN_RIGHT 2
#define ALIGN_BOTTOM 2
#define ALIGN_CUSTOM 3

struct subtitle;

#define DEFAULT_FONT_SIZE 16
#define DEFAULT_HALO_THICKNESS 0
#define DEFAULT_FOREGROUND_R 255
#define DEFAULT_FOREGROUND_G 255
#define DEFAULT_FOREGROUND_B 255
#define DEFAULT_FOREGROUND_A 255
#define DEFAULT_HALO_R 0
#define DEFAULT_HALO_G 0
#define DEFAULT_HALO_B 0
#define DEFAULT_HALO_A 255
#define DEFAULT_BACKGROUND_R 0
#define DEFAULT_BACKGROUND_G 0
#define DEFAULT_BACKGROUND_B 0
#define DEFAULT_BACKGROUND_A 0
#define DEFAULT_ALIGN_TYPE ALIGN_CENTER
#define DEFAULT_SPACING 1
#define DEFAULT_DURATION 5000000000ULL
#define DEFAULT_XALIGN_TYPE ALIGN_CENTER
#define DEFAULT_YALIGN_TYPE ALIGN_BOTTOM

struct hardsub_render_settings
{
	std::string font_name;
	uint32_t font_size;
	uint32_t halo_thickness;
	uint8_t foreground_r;
	uint8_t foreground_g;
	uint8_t foreground_b;
	uint8_t foreground_a;
	uint8_t halo_r;
	uint8_t halo_g;
	uint8_t halo_b;
	uint8_t halo_a;
	uint8_t background_r;
	uint8_t background_g;
	uint8_t background_b;
	uint8_t background_a;
	uint32_t align_type;
	uint32_t spacing;
	std::string text;
	image_frame_rgbx* operator()();
};

struct hardsub_settings
{
	hardsub_settings();
	void reset();
	struct hardsub_render_settings rsettings;
	uint64_t timecode;
	uint64_t duration;
	int xalign_type;
	int32_t xalign;
	int yalign_type;
	int32_t yalign;
	subtitle* operator()();
};

struct subtitle
{
	uint64_t timecode;
	uint64_t duration;
	int xalign_type;
	int32_t xalign;
	int yalign_type;
	int32_t yalign;
	struct hardsub_render_settings used_settings;
	image_frame_rgbx* subtitle_img;
	~subtitle();
};


void subtitle_set_resolution(uint32_t w, uint32_t h);
void render_subtitle(image_frame_rgbx& bottom, struct subtitle& sub);
std::list<subtitle*> parse_subtitle_option(struct hardsub_settings& settings, const std::string& option);
void print_hardsubs_help(const std::string& prefix);
std::list<subtitle*> process_hardsubs_options(struct hardsub_settings& settings, const std::string& prefix, int argc, char** argv);
void subtitle_update_parameter(std::list<subtitle*>& subs, unsigned char parameter, const std::string& value);
void subtitle_process_gameinfo(std::list<subtitle*>& subs, struct packet& p);

#endif
