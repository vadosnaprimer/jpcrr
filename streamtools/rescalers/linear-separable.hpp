#ifndef _rescalers__linear_separable__hpp__included__
#define _rescalers__linear_separable__hpp__included__

#include "rescalers/factory.hpp"
#include "bound-method.hpp"

#define MAXCOEFFICIENTS 256

typedef signed long long position_t;

typedef bound_method<void, float*, position_t, position_t, position_t, position_t, unsigned&, unsigned&>
	bound_coeff_function_t;


class rescaler_linear_separable : public rescaler
{
public:
	void operator()(uint8_t* target, uint32_t twidth, uint32_t theight,
		const uint8_t* source, uint32_t swidth, uint32_t sheight);
protected:
	//Fill coeffs, base and coeffcount. Coeffs is set of floating-point coefficients for pixels
	//starting from pixel number base (zero-based) and numbering coeffcount. Take care not to refer
	//to pixel outside range [0,width). Twidth is target width.
	//num / denum gives position of pixel being approximated. The coordinate range is [0, width),
	//width giving the width of original data.
	virtual void compute_coeffs(float* coeffs, position_t num, position_t denum, position_t width,
		position_t twidth, unsigned& base, unsigned& coeffcount) = 0;
};

class simple_rescaler_linear_separable : public rescaler_factory
{
public:
	simple_rescaler_linear_separable(const std::string& type, bound_coeff_function_t _coeffs_fn);
	rescaler& make(const std::string& type);
private:
	bound_coeff_function_t coeffs_fn;
};

#endif
