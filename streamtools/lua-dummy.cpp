#include "lua.hpp"
#include <stdexcept>

Lua::Lua()
{
}

Lua::~Lua()
{
}

void Lua::load_script(const std::string& name)
{
	throw std::runtime_error("No support for Lua scripting compiled in");
}

void Lua::frame_callback(uint64_t ts, image_frame_rgbx* f)
{
	throw std::runtime_error("No support for Lua scripting compiled in");
}

bool Lua::enabled()
{
	return false;
}

void Lua::set_processor(packet_processor& proc)
{
}
