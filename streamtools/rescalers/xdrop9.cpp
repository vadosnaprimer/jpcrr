#include "rescalers/simple.hpp"
#include <stdint.h>
#include <cmath>
#include <stdexcept>

namespace
{
	void do_rescale(uint8_t* target, uint32_t twidth, uint32_t theight,
		const uint8_t* source, uint32_t swidth, uint32_t sheight)
	{
		uint32_t* __restrict__ src = (uint32_t*)source;
		uint32_t* __restrict__ dest = (uint32_t*)target;

		if(theight != sheight || twidth % 8 || swidth % 9 || swidth / 9 * 8 != twidth)
			throw std::runtime_error("xdrop9: Incorrect scale factor");

		for(uint32_t y = 0; y < theight; y++)
			for(uint32_t x = 0; x < twidth; x++) {
				uint32_t _x = x / 8 * 9 + x % 8;
				dest[y * twidth + x] = src[y * swidth + _x];
			}
	}

	simple_rescaler factory("xdrop9", make_bound_method(do_rescale));
}
