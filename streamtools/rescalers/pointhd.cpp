#include "rescalers/simple.hpp"
#include <stdint.h>
#include <cmath>
#include <stdexcept>
#include <map>

namespace
{
	struct rescale_key
	{
		uint32_t swidth;
		uint32_t sheight;
		uint32_t twidth;
		uint32_t theight;
		bool halfres;
		bool operator==(const rescale_key& k) const
		{
			if(swidth != k.swidth)
				return false;
			if(sheight != k.sheight)
				return false;
			if(twidth != k.twidth)
				return false;
			if(theight != k.theight)
				return false;
			if(halfres != k.halfres)
				return false;
			return true;
		}
		bool operator<(const rescale_key& k) const
		{
			if(swidth < k.swidth)
				return true;
			if(swidth > k.swidth)
				return false;
			if(sheight < k.sheight)
				return true;
			if(sheight > k.sheight)
				return false;
			if(twidth < k.twidth)
				return true;
			if(twidth > k.twidth)
				return false;
			if(theight < k.theight)
				return true;
			if(theight > k.theight)
				return false;
			if(!halfres && k.halfres)
				return true;
			if(halfres && !k.halfres)
				return false;
			return false;
		}
	};

	struct rescale_info
	{
		uint32_t* strip_widths;
		uint32_t* strip_heights;
	};

	std::map<rescale_key, rescale_info> scaleinfo;

	void do_stripscale(uint32_t* stripsizes, uint32_t strips, uint32_t tdim, uint32_t sratio)
	{
		tdim /= 2;
		uint32_t sdim = strips * sratio;
		for(uint32_t i = 0; i < strips; i++)
			stripsizes[i] = 0;
		for(uint32_t tpixel = 0; tpixel < tdim; tpixel++) {
			uint32_t spixel = (uint32_t)(((uint64_t)tpixel * sdim + tdim / 2) / tdim) / sratio;
			stripsizes[spixel] += 2;
		}
	}

	struct rescale_info get_info_for(uint32_t swidth, uint32_t sheight, uint32_t twidth, uint32_t theight,
		bool halfres)
	{
		rescale_key tag;
		tag.swidth = swidth;
		tag.sheight = sheight;
		tag.twidth = twidth;
		tag.theight = theight;
		tag.halfres = halfres;
		if(scaleinfo.count(tag))
			return scaleinfo[tag];

		//Output resolution must be even.
		if(twidth & 1 || theight & 1)
			throw std::runtime_error("pointhd: Output resolution must be even");
		if((swidth & 1 || sheight & 1) && halfres)
			throw std::runtime_error("pointhd: Input resolution must be even in halfres mode");
		if(halfres) {
			swidth /= 2;
			sheight /= 2;
		}

		uint32_t scaleratio;
		//At least one of ratios twidth / swidth or theight / sheight must be even integer, and said
		//ratio must be at least as small as the other.
		if(twidth % (2 * swidth) == 0)
			if(theight % (2 * sheight) == 0)
				//Both X and Y are even ratios. Pick half of smaller as scale ratio.
				if(twidth / swidth < theight / sheight)
					scaleratio = twidth / (2 * swidth);
				else
					scaleratio = theight / (2 * sheight);
			else {
				//X is even but Y is not. X ratio must be smaller or equal to Y ratio.
				if(twidth / swidth <= theight / sheight)
					scaleratio = twidth / (2 * swidth);
				else
					throw std::runtime_error("pointhd: Even multiple must be the smaller ratio");
			}
		else
			if(theight % (2 * sheight) == 0) {
				//Y is even but X is not. Y ratio must be smaller or equal to X ratio.
				if(twidth / swidth >= theight / sheight)
					scaleratio = theight / (2 * sheight);
				else
					throw std::runtime_error("pointhd: Even multiple must be the smaller ratio");
			} else
				throw std::runtime_error("pointhd: At least one dimension must be even multiple");

		//No info yet, compute and cache it. In halfres mode, strip array gets full size.
		struct rescale_info i;
		i.strip_widths = new uint32_t[swidth * (halfres ? 2 : 1)];
		i.strip_heights = new uint32_t[sheight * (halfres ? 2 : 1)];

		//Scaling.
		do_stripscale(i.strip_widths, swidth, twidth, scaleratio);
		do_stripscale(i.strip_heights, sheight, theight, scaleratio);

		//Split the halfs in halfres mode.
		if(halfres) {
			for(uint32_t j = swidth; j <= swidth; --j) {
				i.strip_widths[2 * j + 1] = i.strip_widths[j] / 2;
				i.strip_widths[2 * j] = (i.strip_widths[j] + 1) / 2;
			}
			for(uint32_t j = sheight; j <= sheight; --j) {
				i.strip_heights[2 * j + 1] = i.strip_heights[j] / 2;
				i.strip_heights[2 * j] = (i.strip_heights[j] + 1) / 2;
			}
		}

		scaleinfo[tag] = i;
		return i;
	}

	void _do_rescale(uint8_t* target, struct rescale_info& info, uint32_t twidth,
		const uint8_t* source, uint32_t swidth, uint32_t sheight)
	{
		uint32_t* __restrict__ src = (uint32_t*)source;
		uint32_t* __restrict__ dest = (uint32_t*)target;
		uint32_t* heights = info.strip_heights;
		uint32_t* widths = info.strip_widths;
		size_t sptr = 0;
		size_t tptr = 0;
		for(uint32_t y = 0; y < sheight; y++) {
			for(uint32_t x = 0; x < swidth; x++) {
				uint32_t stripw = widths[x];
				for(uint32_t i = 0; i < stripw; i++)
					dest[tptr++] = src[sptr];
				sptr++;
			}
			uint32_t stripd = twidth * (heights[y] - 1);
			for(uint32_t i = 0; i < stripd; i++, tptr++)
				dest[tptr] = dest[tptr - twidth];
		}
	}

	void do_rescale(uint8_t* target, uint32_t twidth, uint32_t theight,
		const uint8_t* source, uint32_t swidth, uint32_t sheight)
	{
		struct rescale_info i = get_info_for(swidth, sheight, twidth, theight, false);
		_do_rescale(target, i, twidth, source, swidth, sheight);
	}

	void do_rescale_h(uint8_t* target, uint32_t twidth, uint32_t theight,
		const uint8_t* source, uint32_t swidth, uint32_t sheight)
	{
		struct rescale_info i = get_info_for(swidth, sheight, twidth, theight, true);
		_do_rescale(target, i, twidth, source, swidth, sheight);
	}

	simple_rescaler factory("pointhd", make_bound_method(do_rescale));
	simple_rescaler factory2("pointhdh", make_bound_method(do_rescale_h));
}
