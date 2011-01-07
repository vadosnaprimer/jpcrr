#include "rescalers/simple.hpp"
#include <stdint.h>
#include <sstream>
#include <cmath>
#include <stdexcept>

namespace
{
	void do_rescale(uint8_t* target, uint32_t twidth, uint32_t theight,
		const uint8_t* source, uint32_t swidth, uint32_t sheight)
	{
		uint32_t* __restrict__ src = (uint32_t*)source;
		uint32_t* __restrict__ dest = (uint32_t*)target;

		if(twidth % 8)
			throw std::runtime_error("Expected target width to be divisible by 8");
		if(theight != sheight || swidth / 9 * 8 != twidth) {
			std::ostringstream str;
			str << "xdrop9: Expected source to have resolution of " << twidth / 8 * 9 << "x"
				<< theight << ", got " << swidth << "x" << sheight << ".";
			throw std::runtime_error(str.str());
		}

		for(uint32_t y = 0; y < theight; y++)
			for(uint32_t x = 0; x < twidth; x++) {
				uint32_t _x = x / 8 * 9 + x % 8;
				dest[y * twidth + x] = src[y * swidth + _x];
			}
	}

	simple_rescaler factory("xdrop9", make_bound_method(do_rescale));
}
