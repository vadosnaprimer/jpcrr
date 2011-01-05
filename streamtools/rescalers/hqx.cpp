#ifdef WITH_HQX
#include "rescalers/simple.hpp"
#include <stdint.h>
extern "C" {
#include <hqx.h>
}
#include <cstring>
#include <stdexcept>


#define RMETHOD_HQX2 0
#define RMETHOD_HQX3 1
#define RMETHOD_HQX4 2
#define RMETHOD_HQX22 3
#define RMETHOD_HQX32 4
#define RMETHOD_HQX42 5
#define RMETHOD_HQX2d 6
#define RMETHOD_HQX3d 7
#define RMETHOD_HQX4d 8

namespace
{
	bool initflag = false;

	void psize_dummy(uint32_t* src, uint32_t* dest, int width, int height)
	{
		memcpy(dest, src, 4 * width * height);
	}

	void psize_double(uint32_t* src, uint32_t* dest, int width, int height)
	{
		for(size_t y = 0; (int)y < height; y++)
			for(size_t x = 0; (int)x < width; x++) {
				size_t dbaseindex = 4 * y * width + 2 * x;
				size_t sbaseindex = y * width + x;
				dest[dbaseindex] = dest[dbaseindex + 1] = dest[dbaseindex + 2 * width] =
					dest[dbaseindex + 2 * width + 1] = src[sbaseindex];
			}
	}

	typedef void(*rescale_t)(uint32_t*, uint32_t*, int, int);
	int factors[] = {2, 3, 4, 4, 6, 8, 4, 6, 8};
	int pfactors[] = {1, 1, 1, 2, 2, 2, 2, 2, 2};
	rescale_t hqxfun[] = {hq2x_32, hq3x_32, hq4x_32, hq2x_32, hq3x_32, hq4x_32,
		hq2x_32, hq3x_32, hq4x_32};
	rescale_t psizefun[] = {psize_dummy, psize_dummy, psize_dummy, hq2x_32, hq2x_32,
		hq2x_32, psize_double, psize_double, psize_double};

	//Read the frame data in src (swidth x sheight) and rescale it to dest (dwidth x dheight).
	void rescale_frame(unsigned char* dest, unsigned dwidth, unsigned dheight, const unsigned char* src,
		unsigned swidth, unsigned sheight, int algo)
	{
		unsigned char magic[4] = {255, 255, 255, 0};
		uint32_t* magic2 = (uint32_t*)magic;
		uint32_t magic3 = ~*magic2;
		uint32_t shiftscale = 0;
		if(*magic2 > 0xFFFFFF)
			shiftscale = 8;		//Big-endian.

		if(!initflag)
			hqxInit();
		initflag = true;

		if(dwidth % factors[algo] || dheight % factors[algo])
			throw std::runtime_error("hqx: Target image size must be multiple of scale factor");
		if(dwidth / factors[algo] != swidth || dheight / factors[algo] != sheight)
			throw std::runtime_error("hqx: Ratio of source and destination sizes must be the scale factor");

		uint32_t buffer1_width = dwidth / factors[algo];
		uint32_t buffer2_width = dwidth / pfactors[algo];
		uint32_t buffer3_width = dwidth;
		uint32_t buffer1_height = dheight / factors[algo];
		uint32_t buffer2_height = dheight / pfactors[algo];
		uint32_t buffer3_height = dheight;
		uint32_t* buffer1 = new uint32_t[buffer1_width * buffer1_height];
		uint32_t* buffer2 = new uint32_t[buffer2_width * buffer2_height];
		uint32_t* buffer3 = new uint32_t[buffer3_width * buffer3_height];
		uint32_t* __restrict__ _src = (uint32_t*)src;

		for(uint32_t y = 0; y < buffer1_height; y++)
			for(uint32_t x = 0; x < buffer1_width; x++)
				//Mask off high bits because HQX will crash otherwise.
				buffer1[y * buffer1_width + x] = (_src[y * swidth + x] >> shiftscale) & 0xFFFFFF;

		//Do the rescale steps.
		hqxfun[algo](buffer1, buffer2, buffer1_width, buffer1_height);
		psizefun[algo](buffer2, buffer3, buffer2_width, buffer2_height);

		//Final copy out of buffer3 to destination.
		for(size_t i = 0; i < buffer3_width * buffer3_height; i++)
			buffer3[i] = (buffer3[i] << shiftscale) | magic3;
		memcpy(dest, buffer3, 4 * dwidth * dheight);

		delete buffer1;
		delete buffer2;
		delete buffer3;
	}

	simple_rescaler r_hqx2("hqx2", bind_last<void, int, uint8_t*, uint32_t, uint32_t, const uint8_t*,
		uint32_t, uint32_t>(make_bound_method(rescale_frame), RMETHOD_HQX2));
	simple_rescaler r_hqx3("hqx3", bind_last<void, int, uint8_t*, uint32_t, uint32_t, const uint8_t*,
		uint32_t, uint32_t>(make_bound_method(rescale_frame), RMETHOD_HQX3));
	simple_rescaler r_hqx4("hqx4", bind_last<void, int, uint8_t*, uint32_t, uint32_t, const uint8_t*,
		uint32_t, uint32_t>(make_bound_method(rescale_frame), RMETHOD_HQX4));
	simple_rescaler r_hqx22("hqx22", bind_last<void, int, uint8_t*, uint32_t, uint32_t, const uint8_t*,
		uint32_t, uint32_t>(make_bound_method(rescale_frame), RMETHOD_HQX22));
	simple_rescaler r_hqx32("hqx32", bind_last<void, int, uint8_t*, uint32_t, uint32_t, const uint8_t*,
		uint32_t, uint32_t>(make_bound_method(rescale_frame), RMETHOD_HQX32));
	simple_rescaler r_hqx42("hqx42", bind_last<void, int, uint8_t*, uint32_t, uint32_t, const uint8_t*,
		uint32_t, uint32_t>(make_bound_method(rescale_frame), RMETHOD_HQX42));
	simple_rescaler r_hqx2d("hqx2d", bind_last<void, int, uint8_t*, uint32_t, uint32_t, const uint8_t*,
		uint32_t, uint32_t>(make_bound_method(rescale_frame), RMETHOD_HQX2d));
	simple_rescaler r_hqx3d("hqx3d", bind_last<void, int, uint8_t*, uint32_t, uint32_t, const uint8_t*,
		uint32_t, uint32_t>(make_bound_method(rescale_frame), RMETHOD_HQX3d));
	simple_rescaler r_hqx4d("hqx4d", bind_last<void, int, uint8_t*, uint32_t, uint32_t, const uint8_t*,
		uint32_t, uint32_t>(make_bound_method(rescale_frame), RMETHOD_HQX4d));
}
#endif
