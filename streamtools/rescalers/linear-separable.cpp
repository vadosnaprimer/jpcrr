#include "rescalers/linear-separable.hpp"

#define MAXCOEFFICIENTS 256

void rescaler_linear_separable::operator()(uint8_t* target, uint32_t twidth, uint32_t theight,
	const uint8_t* source, uint32_t swidth, uint32_t sheight)
{
	float coeffs[MAXCOEFFICIENTS];
	unsigned count;
	unsigned base;
	float* interm;

	interm = new float[4 * twidth * sheight];

	for(unsigned x = 0; x < twidth; x++) {
		count = 0xDEADBEEF;
		base = 0xDEADBEEF;
		compute_coeffs(coeffs, (position_t)x * swidth, twidth, swidth, twidth, count, base);
		/* Normalize the coefficients. */
		float sum = 0;
		for(unsigned i = 0; i < count; i++)
			sum += coeffs[i];
		for(unsigned i = 0; i < count; i++)
			coeffs[i] /= sum;
		for(unsigned y = 0; y < sheight; y++) {
			float vr = 0, vg = 0, vb = 0, va = 0;
			for(unsigned k = 0; k < count; k++) {
				uint32_t sampleaddr = 4 * y * swidth + 4 * (k + base);
				vr += coeffs[k] * source[sampleaddr + 0];
				vg += coeffs[k] * source[sampleaddr + 1];
				vb += coeffs[k] * source[sampleaddr + 2];
				va += coeffs[k] * source[sampleaddr + 3];
			}
			interm[y * 4 * twidth + 4 * x + 0] = vr;
			interm[y * 4 * twidth + 4 * x + 1] = vg;
			interm[y * 4 * twidth + 4 * x + 2] = vb;
			interm[y * 4 * twidth + 4 * x + 3] = va;
		}
	}

	for(unsigned y = 0; y < theight; y++) {
		count = 0;
		base = 0;
		compute_coeffs(coeffs, (position_t)y * sheight, theight, sheight, theight, count, base);
		/* Normalize the coefficients. */
		float sum = 0;
		for(unsigned i = 0; i < count; i++)
			sum += coeffs[i];
		for(unsigned i = 0; i < count; i++)
			coeffs[i] /= sum;
		for(unsigned x = 0; x < twidth; x++) {
			float vr = 0, vg = 0, vb = 0, va = 0;
			for(unsigned k = 0; k < count; k++) {
				vr += coeffs[k] * interm[(base + k) * 4 * twidth + x * 4 + 0];
				vg += coeffs[k] * interm[(base + k) * 4 * twidth + x * 4 + 1];
				vb += coeffs[k] * interm[(base + k) * 4 * twidth + x * 4 + 2];
				va += coeffs[k] * interm[(base + k) * 4 * twidth + x * 4 + 3];
			}
			int wr = (int)vr;
			int wg = (int)vg;
			int wb = (int)vb;
			int wa = (int)va;
			wr = (wr < 0) ? 0 : ((wr > 255) ? 255 : wr);
			wg = (wg < 0) ? 0 : ((wg > 255) ? 255 : wg);
			wb = (wb < 0) ? 0 : ((wb > 255) ? 255 : wb);
			wa = (wa < 0) ? 0 : ((wa > 255) ? 255 : wa);

			target[y * 4 * twidth + 4 * x] = (unsigned char)wr;
			target[y * 4 * twidth + 4 * x + 1] = (unsigned char)wg;
			target[y * 4 * twidth + 4 * x + 2] = (unsigned char)wb;
			target[y * 4 * twidth + 4 * x + 3] = (unsigned char)wa;
		}
	}
	delete[] interm;
}


namespace
{
	class simple_rescaler_linear_separable_c : public rescaler_linear_separable
	{
	public:
		simple_rescaler_linear_separable_c(bound_coeff_function_t _coeffs_fn)
		{
			coeffs_fn = _coeffs_fn;
		}

		void compute_coeffs(float* coeffs, position_t num, position_t denum, position_t osize,
			position_t nsize, unsigned& base, unsigned& coeffcount)
		{
			coeffs_fn(coeffs, num, denum, osize, nsize, base, coeffcount);
		}

	private:
		bound_coeff_function_t coeffs_fn;
	};
}

simple_rescaler_linear_separable::simple_rescaler_linear_separable(const std::string& type,
	bound_coeff_function_t _coeffs_fn)
	: rescaler_factory(type)
{
	coeffs_fn = _coeffs_fn;
}

rescaler& simple_rescaler_linear_separable::make(const std::string& type)
{
	return *new simple_rescaler_linear_separable_c(coeffs_fn);
}
