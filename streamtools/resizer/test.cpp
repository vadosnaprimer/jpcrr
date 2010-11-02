#include <stdint.h>
#include <cmath>
#include <stdexcept>
#include "resize.hpp"

namespace
{
	inline uint32_t nnscale(uint32_t value, uint32_t trange, uint32_t srange)
	{
		//x / trange is as good approximation for value / srange as possible.
		//=> x is as good approximation for value * trange / srange as possible.
		return (uint32_t)(((uint64_t)value * trange + srange / 2) / srange);
	}

	void do_resize(uint8_t* target, uint32_t twidth, uint32_t theight,
		const uint8_t* source, uint32_t swidth, uint32_t sheight)
	{
		uint32_t* __restrict__ src = (uint32_t*)source;
		uint32_t* __restrict__ dest = (uint32_t*)target;

		for(uint32_t y = 0; y < sheight; y++)
			for(uint32_t x = 0; x < swidth; x++) {
				uint32_t _x = nnscale(x, twidth, swidth);
				uint32_t _y = nnscale(y, theight, sheight);
				dest[_y * twidth + _x] = src[y * swidth + x];
			}
	}

	simple_resizer factory("test", do_resize);
}
