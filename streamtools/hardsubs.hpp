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
	//Filename of font to use for subtitle.
	std::string font_name;
	//Size of font to use for subititle.
	uint32_t font_size;
	//Thickness of halo region around characters.
	uint32_t halo_thickness;
	//Character foreground color.
	uint8_t foreground_r;
	uint8_t foreground_g;
	uint8_t foreground_b;
	uint8_t foreground_a;
	//Character halo color.
	uint8_t halo_r;
	uint8_t halo_g;
	uint8_t halo_b;
	uint8_t halo_a;
	//Character background color.
	uint8_t background_r;
	uint8_t background_g;
	uint8_t background_b;
	uint8_t background_a;
	//Alignment of text rows w.r.t. one another, one of:
	//ALIGN_LEFT: Left-justify text
	//ALIGN_RIGHT: Right-justify text
	//ALIGN_CENTER: Center text
	uint32_t align_type;
	//Number of extra pixels between rows.
	uint32_t spacing;
	//The actual text itself (UTF-8).
	std::string text;
	//Render the subtitle into image.
	image_frame_rgbx* operator()();
};

struct hardsub_settings
{
	hardsub_settings();
	//Reset settings to defaults.
	void reset();
	//Various settings for rendering image itself.
	struct hardsub_render_settings rsettings;
	//Starting timecode (nanoseconds)
	uint64_t timecode;
	//Duration (nanoseconds)
	uint64_t duration;
	//Subtitle horizontal alignment. One of:
	//ALIGN_LEFT: Align subtitle to left.
	//ALIGN_RIGHT: Align subtitle to right.
	//ALIGN_CENTER: Center the subtitle.
	//ALIGN_CUSTOM: custom alignment.
	int xalign_type;
	//If xalign_type == ALIGN_CUSTOM, then this field gives offset of subtitle left edge from image left edge.
	int32_t xalign;
	//Subtitle vertical alignment. One of:
	//ALIGN_TOP: Align subtitle to top.
	//ALIGN_BOTTOM: Align subtitle to bottom.
	//ALIGN_CENTER: Center the subtitle.
	//ALIGN_CUSTOM: custom alignment.
	int yalign_type;
	//If yalign_type == ALIGN_CUSTOM, then this field gives offset of subtitle top edge from image top edge.
	int32_t yalign;
	//Render the subtitle and copy settings.
	subtitle* operator()();
};

struct subtitle
{
	~subtitle();
	//These fields have the same meaning as in struct hardsub_settings.
	uint64_t timecode;
	uint64_t duration;
	int xalign_type;
	int32_t xalign;
	int yalign_type;
	int32_t yalign;
	//The used settings (for re-rendering when gameinfo is updated).
	struct hardsub_render_settings used_settings;
	//The actual graphical rendering of subtitle.
	image_frame_rgbx* subtitle_img;
	//If true, render settings are not valid and subtitle will not be updated.
	bool disable_updates;
};

void render_subtitle(image_frame_rgbx& bottom, struct subtitle& sub);
std::list<subtitle*> parse_subtitle_option(struct hardsub_settings& settings, const std::string& option);
void print_hardsubs_help(const std::string& prefix);
std::list<subtitle*> process_hardsubs_options(struct hardsub_settings& settings, const std::string& prefix, int argc, char** argv);
void subtitle_update_parameter(std::list<subtitle*>& subs, unsigned char parameter, const std::string& value);
void subtitle_process_gameinfo(std::list<subtitle*>& subs, struct packet& p);

#endif
