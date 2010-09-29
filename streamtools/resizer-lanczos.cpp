#include "resize-linear-separable.hpp"
#include <stdint.h>
#include <cmath>
#include <stdexcept>

namespace
{
	double pi = 3.1415926535897932384626;

	//num / denum = x * swidth / twidth
	// swidth = n * twidth => x * n
	// twdith = n * swidth => x / n

	void compute_coefficients_lanczos(float* coeffs, position_t num, position_t denum, position_t width,
		position_t twidth, unsigned& count, unsigned& base, int a)
	{
		if(a == 0)
			throw std::runtime_error("Parameter alpha must be positive in lanczos resizer");

		if(2 * a + 1 <= a)
			throw std::runtime_error("Parameter alpha way too large in lanczos resizer");

		if(2 * a + 1 > MAXCOEFFICIENTS)
			throw std::runtime_error("Parameter alpha value would require more coefficients than "
				"supported");

		count = 0;
		base = 0;
		bool base_set = false;
		position_t centralpoint = num / denum;
		for(int i = -a + 1; i <= a; i++) {
			position_t point = centralpoint + i;

			if(point < 0 || point >= width)
				continue;	//Out of range.
			if(!base_set) {
				base_set = true;
				base = (unsigned)point;
			}
			if(num != centralpoint * denum) {
				double x = (double)num / denum - centralpoint;
				coeffs[count++] = a * sin(x * pi) * sin(x * pi / a) / ((x * pi) * (x * pi));
			} else {
				coeffs[count++] = 1;
			}
		}
	}

	void compute_coefficients_average(float* coeffs, position_t num, position_t denum, position_t width,
		position_t twidth, unsigned& count, unsigned& base)
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
