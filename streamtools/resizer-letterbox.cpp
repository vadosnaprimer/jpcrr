#include "resize-linear-separable.hpp"
#include <stdint.h>
#include <cmath>
#include <stdexcept>

namespace
{
	void compute_coefficients_letterbox(float* coeffs, position_t num, position_t denum, position_t width,
		position_t twidth, unsigned& count, unsigned& base)
	{
		position_t relpos = num / width - (twidth - width) / 2;
		*coeffs = 0;
		count = 1;
		base = 0;
		if(relpos >= 0 && relpos < width) {
			*coeffs = 1;
			base = (unsigned)relpos;
		}
	}

	simple_resizer_linear_separable r_letterbox("letterbox", compute_coefficients_letterbox);
}
