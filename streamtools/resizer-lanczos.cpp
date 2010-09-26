#include "resize-linear-separable.hpp"
#include <stdint.h>
#include <cmath>
#include <stdexcept>

namespace
{
	void compute_coefficients_lanczos(float* coeffs, position_t num, position_t denum, position_t width,
		unsigned& count, unsigned& base, int a)
	{
		signed lowbound, highbound, scan;

		if(num % denum == 0) {
			coeffs[0] = 1;
			count = 1;
			base = num / denum;
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
		base = scan / denum;
		count = 0;
		while(scan <= highbound) {
			float difference = (float)(num - scan) / denum;
			if(num == scan)
				coeffs[count++] = 1;
			else
				coeffs[count++] = a * sin(M_PI*difference) * sin(M_PI*difference/2) /
					(M_PI * M_PI * difference * difference);

			scan = scan + denum;
		}
	}

	void compute_coefficients_average(float* coeffs, position_t num, position_t denum, position_t width,
		unsigned& count, unsigned& base)
	{
		signed lowbound, highbound, scan;

		lowbound = num;
		highbound = num + width;
		scan = lowbound - lowbound % denum;

		if((width + denum - 1) / denum > MAXCOEFFICIENTS)
			throw std::runtime_error("Conversion would require more coefficients than supported");

		base = scan / denum;
		*coeffs = (scan + denum) - lowbound;
		count = 1;
		scan = scan + denum;
		while(scan < highbound) {
			if(scan + denum > highbound)
				coeffs[count++] = highbound - scan;
			else
				coeffs[count++] = denum;

			scan = scan + denum;
		}
	}

	simple_resizer_linear_separable r_average("average", compute_coefficients_average);
	simple_resizer_linear_separable r_lanczos1("lanczos1", compute_coefficients_lanczos, 1);
	simple_resizer_linear_separable r_lanczos2("lanczos2", compute_coefficients_lanczos, 2);
	simple_resizer_linear_separable r_lanczos3("lanczos3", compute_coefficients_lanczos, 3);
	simple_resizer_linear_separable r_lanczos4("lanczos4", compute_coefficients_lanczos, 4);
	simple_resizer_linear_separable r_lanczos5("lanczos5", compute_coefficients_lanczos, 5);
}
