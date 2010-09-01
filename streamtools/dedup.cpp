#include "dedup.hpp"
#include <cstring>

dedup::dedup(uint32_t max, uint32_t width, uint32_t height)
{
	if(max)
		framebuffer.resize(4 * width * height);
	bound = max;
	current = 0;	//First can't be dup.
}

bool dedup::operator()(const uint8_t* buffer)
{
	if(!bound)
		return false;		//Dup detection disabled.

	//Update buffer if not identical.
	bool is_dup = (current && !memcmp(&framebuffer[0], buffer, framebuffer.size()));
	if(!is_dup)
		memcpy(&framebuffer[0], buffer, framebuffer.size());

	//Decrement current if match, otherwise reset to bound
	current = is_dup ? (current - 1) : bound;

	return is_dup;
}
