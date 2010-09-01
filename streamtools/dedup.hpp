#ifndef _dedup__hpp__included__
#define _dedup__hpp__included__

#include <vector>
#include <stdint.h>
#include "resize.hpp"

class dedup
{
public:
	dedup(uint32_t max, uint32_t width, uint32_t height);
	bool operator()(const uint8_t* buffer);
private:
	std::vector<uint8_t> framebuffer;
	uint32_t bound;
	uint32_t current;
};

#endif