#include "resize-linear-separable.hpp"

#define MAXCOEFFICIENTS 256

void resizer_linear_separable::operator()(uint8_t* target, uint32_t twidth, uint32_t theight,
	const uint8_t* source, uint32_t swidth, uint32_t sheight)
{
	float coeffs[MAXCOEFFICIENTS];
	unsigned count;
	unsigned base;
	float* interm;

	interm = new float[3 * twidth * sheight];

	for(unsigned x = 0; x < twidth; x++) {
		count = 0xDEADBEEF;
		base = 0xDEADBEEF;
		compute_coeffs(coeffs, (position_t)x * swidth, twidth, swidth, count, base);
		/* Normalize the coefficients. */
		float sum = 0;
		for(unsigned i = 0; i < count; i++)
			sum += coeffs[i];
		for(unsigned i = 0; i < count; i++)
			coeffs[i] /= sum;
		for(unsigned y = 0; y < sheight; y++) {
			float vr = 0, vg = 0, vb = 0;
			for(unsigned k = 0; k < count; k++) {
				uint32_t sampleaddr = 4 * y * swidth + 4 * (k + base);
				vr += coeffs[k] * source[sampleaddr + 0];
				vg += coeffs[k] * source[sampleaddr + 1];
				vb += coeffs[k] * source[sampleaddr + 2];
			}
			interm[y * 3 * twidth + 3 * x + 0] = vr;
			interm[y * 3 * twidth + 3 * x + 1] = vg;
			interm[y * 3 * twidth + 3 * x + 2] = vb;
		}
	}

	for(unsigned y = 0; y < theight; y++) {
		count = 0;
		base = 0;
		compute_coeffs(coeffs, (position_t)y * sheight, theight, sheight, count, base);
		/* Normalize the coefficients. */
		float sum = 0;
		for(unsigned i = 0; i < count; i++)
			sum += coeffs[i];
		for(unsigned i = 0; i < count; i++)
			coeffs[i] /= sum;
		for(unsigned x = 0; x < twidth; x++) {
			float vr = 0, vg = 0, vb = 0;
			for(unsigned k = 0; k < count; k++) {
				vr += coeffs[k] * interm[(base + k) * 3 * twidth + x * 3 + 0];
				vg += coeffs[k] * interm[(base + k) * 3 * twidth + x * 3 + 1];
				vb += coeffs[k] * interm[(base + k) * 3 * twidth + x * 3 + 2];
			}
			int wr = (int)vr;
			int wg = (int)vg;
			int wb = (int)vb;
			wr = (wr < 0) ? 0 : ((wr > 255) ? 255 : wr);
			wg = (wg < 0) ? 0 : ((wg > 255) ? 255 : wg);
			wb = (wb < 0) ? 0 : ((wb > 255) ? 255 : wb);

			target[y * 4 * twidth + 4 * x] = (unsigned char)wr;
			target[y * 4 * twidth + 4 * x + 1] = (unsigned char)wg;
			target[y * 4 * twidth + 4 * x + 2] = (unsigned char)wb;
			target[y * 4 * twidth + 4 * x + 3] = 0;
		}
	}
	delete[] interm;
}


namespace
{
	class simple_resizer_linear_separable_c : public resizer_linear_separable
	{
	public:
		simple_resizer_linear_separable_c(void(*_coeffs_fn)(float* coeffs, position_t newsize, position_t oldsize,
			position_t position, unsigned& base, unsigned& coeffcount))
		{
			coeffs_fn = _coeffs_fn;
		}

		void compute_coeffs(float* coeffs, position_t newsize, position_t oldsize, position_t position,
		unsigned& base, unsigned& coeffcount)
		{
			coeffs_fn(coeffs, newsize, oldsize, position, base, coeffcount);
		}

	private:
		void(*coeffs_fn)(float* coeffs, position_t newsize, position_t oldsize,
			position_t position, unsigned& base, unsigned& coeffcount);
	};

	class simple_resizer_linear_separable_c2 : public resizer_linear_separable
	{
	public:
		simple_resizer_linear_separable_c2(void(*_coeffs_fn)(float* coeffs, position_t newsize, position_t oldsize,
			position_t position, unsigned& base, unsigned& coeffcount, int algo), int _algo)
		{
			coeffs_fn = _coeffs_fn;
			algo = _algo;
		}

		void compute_coeffs(float* coeffs, position_t newsize, position_t oldsize, position_t position,
		unsigned& base, unsigned& coeffcount)
		{
			coeffs_fn(coeffs, newsize, oldsize, position, base, coeffcount, algo);
		}

	private:
		void(*coeffs_fn)(float* coeffs, position_t newsize, position_t oldsize,
			position_t position, unsigned& base, unsigned& coeffcount, int algo);
		int algo;
	};
}

simple_resizer_linear_separable::simple_resizer_linear_separable(const std::string& type,
	void(*_coeffs_fn)(float* coeffs, position_t newsize, position_t oldsize, position_t position, unsigned& base,
	unsigned& coeffcount))
	: resizer_factory(type)
{
	coeffs_fn = _coeffs_fn;
	coeffs_fn2 = NULL;
	algo = 0;
}

simple_resizer_linear_separable::simple_resizer_linear_separable(const std::string& type,
	void(*_coeffs_fn)(float* coeffs, position_t newsize, position_t oldsize, position_t position, unsigned& base,
	unsigned& coeffcount, int algo), int _algo)
	: resizer_factory(type)
{
	coeffs_fn = NULL;
	coeffs_fn2 = _coeffs_fn;
	algo = _algo;
}

resizer& simple_resizer_linear_separable::make(const std::string& type)
{
	if(coeffs_fn)
		return *new simple_resizer_linear_separable_c(coeffs_fn);
	else
		return *new simple_resizer_linear_separable_c2(coeffs_fn2, algo);
}
