#include "rescalers/linear-separable.hpp"
#include <stdint.h>
#include <cmath>
#include <stdexcept>

namespace
{
	void compute_coefficients_bicubic(float* coeffs, position_t num, position_t denum, position_t width,
		position_t twidth, unsigned& count, unsigned& base)
	{
		base = num / denum;
		float t1 = (num % denum) / (float)denum;
		float t2 = t1 * t1;
		float t3 = t2 * t1;
		count = 0;
		/*
		In matrix form, the equation is:
		                             / 0,  2,  0,  0 \ /a_-1\
		                             |-1,  0,  1,  0 | |a_0 |
		 p(t) = 0.5 * [1, t, t², t³] | 2, -5,  4, -1 | |a_1 |
		                             \-1,  3, -3,  1 / \a_2 /
		Due to implicit muliplications, only the first part needs to be computed.
		*/
		float c1 = t2 - 0.5 * (t1 + t3);
		float c2 = 1 - 2.5 * t2 + 1.5 * t3;
		float c3 = 0.5 * t1 + 2 * t2 - 1.5 * t3;
		float c4 = 0.5 * (t3 - t2);
		if(base == 0) {
			coeffs[count++] = c2;
			if(base < width - 1)	coeffs[count++] = c3;
			if(base < width - 2)	coeffs[count++] = c4;
		} else {
			base--;
			coeffs[count++] = c1;
			coeffs[count++] = c2;
			if(base < width - 2)	coeffs[count++] = c3;
			if(base < width - 3)	coeffs[count++] = c4;
		}
	}

	simple_rescaler_linear_separable r_bicubic("bicubic", make_bound_method(compute_coefficients_bicubic));
}
