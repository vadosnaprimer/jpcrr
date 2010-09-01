#include "resize.hpp"
#include <stdint.h>
#include <cmath>
#include <stdexcept>

#define RMETHOD_AVERAGE 0
#define RMETHOD_LANCZOS1 1
#define RMETHOD_LANCZOS2 2
#define RMETHOD_LANCZOS3 3
#define RMETHOD_LANCZOS4 4
#define RMETHOD_LANCZOS5 5

namespace
{
	#define MAXCOEFFICIENTS 256

	typedef signed long long position_t;

	void compute_coefficients_lanczos(float* coeffs, position_t num, position_t denum, position_t width,
		unsigned* count, unsigned* base, unsigned a)
	{
		signed lowbound, highbound, scan;

		if(num % denum == 0) {
			coeffs[0] = 1;
			*count = 1;
			*base = num / denum;
			return;
		}

		if(a == 0)
			throw std::runtime_error("Parameter alpha must be positive in lanczos resizer");

		if(2 * a + 1 <= a)
			throw std::runtime_error("Parameter alpha way too large in lanczos resizer");

		if(2 * a + 1 > MAXCOEFFICIENTS)
			throw std::runtime_error("Parameter alpha value would require more coefficients than supported");

		lowbound = num - a * denum;
		highbound = num + a * denum;
		if(lowbound < 0)
			lowbound = 0;
		if(highbound > width * denum)
			highbound = width * denum - denum;

		scan = lowbound + (denum - lowbound % denum) % denum;
		*base = scan / denum;
		*count = 0;
		while(scan <= highbound) {
			float difference = (float)(num - scan) / denum;
			if(num == scan)
				coeffs[(*count)++] = 1;
			else
				coeffs[(*count)++] = a * sin(M_PI*difference) * sin(M_PI*difference/2) /
					(M_PI * M_PI * difference * difference);

			scan = scan + denum;
		}
	}

	void compute_coefficients_average(float* coeffs, position_t num, position_t denum, position_t width,
		unsigned* count, unsigned* base)
	{
		signed lowbound, highbound, scan;

		lowbound = num;
		highbound = num + width;
		scan = lowbound - lowbound % denum;

		if((width + denum - 1) / denum > MAXCOEFFICIENTS)
			throw std::runtime_error("Conversion would require more coefficients than supported");

		*base = scan / denum;
		*coeffs = (scan + denum) - lowbound;
		*count = 1;
		scan = scan + denum;
		while(scan < highbound) {
			if(scan + denum > highbound)
				coeffs[(*count)++] = highbound - scan;
			else
				coeffs[(*count)++] = denum;

			scan = scan + denum;
		}
	}

	// The coodinate space is such that range is [0, srclength] and is given as fraction
	// num / denum, where denumerator is destination length. Thus source pixel spacing
	// is unity.
	void compute_coefficients(float* coeffs, position_t num, position_t denum, position_t width,
		unsigned* count, unsigned* base, int algo)
	{
		float sum = 0;
		switch(algo) {
		case RMETHOD_AVERAGE:
			compute_coefficients_average(coeffs, num, denum, width, count, base);
			break;
		case RMETHOD_LANCZOS1:
			compute_coefficients_lanczos(coeffs, num, denum, width, count, base, 1);
			break;
		case RMETHOD_LANCZOS2:
			compute_coefficients_lanczos(coeffs, num, denum, width, count, base, 2);
			break;
		case RMETHOD_LANCZOS3:
			compute_coefficients_lanczos(coeffs, num, denum, width, count, base, 3);
			break;
		case RMETHOD_LANCZOS4:
			compute_coefficients_lanczos(coeffs, num, denum, width, count, base, 4);
			break;
		case RMETHOD_LANCZOS5:
			compute_coefficients_lanczos(coeffs, num, denum, width, count, base, 5);
			break;
		default:
			throw std::runtime_error("Unknown resize algorithm");
		}

		/* Normalize the coefficients. */
		for(unsigned i = 0; i < *count; i++)
			sum += coeffs[i];
		for(unsigned i = 0; i < *count; i++)
			coeffs[i] /= sum;
	}

	//Read the frame data in src (swidth x sheight) and resize it to dest (dwidth x dheight).
	void resize_frame(unsigned char* dest, unsigned dwidth, unsigned dheight, const unsigned char* src,
		unsigned swidth, unsigned sheight, int algo)
	{
		float coeffs[MAXCOEFFICIENTS];
		unsigned count;
		unsigned base;
		float* interm;

		interm = new float[3 * dwidth * sheight];

		for(unsigned x = 0; x < dwidth; x++) {
			count = 0xDEADBEEF;
			base = 0xDEADBEEF;
			compute_coefficients(coeffs, (position_t)x * swidth, dwidth, swidth, &count, &base, algo);
			for(unsigned y = 0; y < sheight; y++) {
				float vr = 0, vg = 0, vb = 0;
				for(unsigned k = 0; k < count; k++) {
					uint32_t sampleaddr = 4 * y * swidth + 4 * (k + base);
					vr += coeffs[k] * src[sampleaddr + 0];
					vg += coeffs[k] * src[sampleaddr + 1];
					vb += coeffs[k] * src[sampleaddr + 2];
				}
				interm[y * 3 * dwidth + 3 * x + 0] = vr;
				interm[y * 3 * dwidth + 3 * x + 1] = vg;
				interm[y * 3 * dwidth + 3 * x + 2] = vb;
			}
		}

		for(unsigned y = 0; y < dheight; y++) {
			count = 0;
			base = 0;
			compute_coefficients(coeffs, (position_t)y * sheight, dheight, sheight, &count, &base, algo);
			for(unsigned x = 0; x < dwidth; x++) {
				float vr = 0, vg = 0, vb = 0;
				for(unsigned k = 0; k < count; k++) {
					vr += coeffs[k] * interm[(base + k) * 3 * dwidth + x * 3 + 0];
					vg += coeffs[k] * interm[(base + k) * 3 * dwidth + x * 3 + 1];
					vb += coeffs[k] * interm[(base + k) * 3 * dwidth + x * 3 + 2];
				}
				int wr = (int)vr;
				int wg = (int)vg;
				int wb = (int)vb;
				wr = (wr < 0) ? 0 : ((wr > 255) ? 255 : wr);
				wg = (wg < 0) ? 0 : ((wg > 255) ? 255 : wg);
				wb = (wb < 0) ? 0 : ((wb > 255) ? 255 : wb);

				dest[y * 4 * dwidth + 4 * x] = (unsigned char)wr;
				dest[y * 4 * dwidth + 4 * x + 1] = (unsigned char)wg;
				dest[y * 4 * dwidth + 4 * x + 2] = (unsigned char)wb;
				dest[y * 4 * dwidth + 4 * x + 3] = 0;
			}
		}

		delete[] interm;
	}

	class resizer_local : public resizer
	{
	public:
		resizer_local(int _algo)
		{
			algo = _algo;
		}

		void operator()(uint8_t* target, uint32_t twidth, uint32_t theight,
			const uint8_t* source, uint32_t swidth, uint32_t sheight)
		{
			resize_frame(target, twidth, theight, source, swidth, sheight, algo);
		}
	private:
		int algo;
	};

	class resizer_local_factory : public resizer_factory
	{
	public:
		resizer_local_factory(const std::string& name, int _algo)
			: resizer_factory(name)
		{
			algo = _algo;
		}

		resizer& make(const std::string& type)
		{
			return *new resizer_local(algo);
		}
	private:
		int algo;
	};

	resizer_local_factory r_average("average", RMETHOD_AVERAGE);
	resizer_local_factory r_lanczos1("lanczos1", RMETHOD_LANCZOS1);
	resizer_local_factory r_lanczos2("lanczos2", RMETHOD_LANCZOS2);
	resizer_local_factory r_lanczos3("lanczos3", RMETHOD_LANCZOS3);
	resizer_local_factory r_lanczos4("lanczos4", RMETHOD_LANCZOS4);
	resizer_local_factory r_lanczos5("lanczos5", RMETHOD_LANCZOS5);
}
