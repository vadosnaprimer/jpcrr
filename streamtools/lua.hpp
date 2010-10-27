#ifndef _lua__hpp__included__
#define _lua__hpp__included__

#include "resize.hpp"

class packet_processor;

class Lua
{
public:
	Lua();
	~Lua();
	void load_script(const std::string& name);
	void frame_callback(uint64_t ts, image_frame_rgbx* f);
	bool enabled();
	void set_processor(packet_processor& proc);
private:
	packet_processor* processor;
	void* internal_state;
};

#endif
