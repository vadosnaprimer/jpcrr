#include "rescalers/linear-separable.hpp"
#include <stdint.h>
#include <cmath>
#include <stdexcept>

namespace
{
	void compute_coefficients_bilinear(float* coeffs, position_t num, position_t denum, position_t width,
		position_t twidth, unsigned& count, unsigned& base)
	{
		base = num / denum;
		if(base < width - 1) {
			float fpos = (num % denum) / (float)denum;
			coeffs[0] = 1 - fpos;
			coeffs[1] = fpos;
			count = 2;
		} else {
			coeffs[0] = 1;
			count = 1;
		}
	}

	simple_rescaler_linear_separable r_bilinear("bilinear", make_bound_method(compute_coefficients_bilinear));
}
