#include "rescalers/simple.hpp"
#include <stdint.h>
#include <cmath>
#include <stdexcept>

namespace
{
	void do_changestack(uint8_t* target, uint32_t twidth, uint32_t theight,
		const uint8_t* source, uint32_t swidth, uint32_t sheight)
	{
		uint32_t* __restrict__ src = (uint32_t*)source;
		uint32_t* __restrict__ dest = (uint32_t*)target;
		bool horizontal_to_vertical;
		uint32_t ratio;

		if(swidth < twidth) {
			//Vertical to horizontal.
			horizontal_to_vertical = false;
			if(twidth % swidth || sheight % theight)
				throw std::runtime_error("Bad size change for stacking change");
			ratio = twidth / swidth;
			if(theight * ratio != sheight)
				throw std::runtime_error("Bad size change for stacking change");
		} else {
			//Horizontal to vertical.
			horizontal_to_vertical = true;
			if(swidth % twidth || theight % sheight)
				throw std::runtime_error("Bad size change for stacking change");
			ratio = swidth / twidth;
			if(sheight * ratio != theight)
				throw std::runtime_error("Bad size change for stacking change");
		}

		for(uint32_t y = 0; y < sheight; y++)
			for(uint32_t x = 0; x < swidth; x++) {
				uint32_t _x, _y;
				if(horizontal_to_vertical) {
					_x = x % twidth;
					_y = y + x / twidth * sheight;
				} else {
					_x = x + y / theight * swidth;
					_y = y % theight;
				}
				dest[_y * twidth + _x] = src[y * swidth + x];
			}
	}

	simple_rescaler factory("changestacking", make_bound_method(do_changestack));
}
