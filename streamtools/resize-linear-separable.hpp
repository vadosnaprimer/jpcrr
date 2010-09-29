#ifndef _resize_linear_separable__hpp__included__
#define _resize_linear_separable__hpp__included__

#include "resize.hpp"

#define MAXCOEFFICIENTS 256

typedef signed long long position_t;



class resizer_linear_separable : public resizer
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

class simple_resizer_linear_separable : public resizer_factory
{
public:
	simple_resizer_linear_separable(const std::string& type, void(*coeffs_fn)(float* coeffs, position_t num,
			position_t denum, position_t width, position_t twidth, unsigned& base, unsigned& coeffcount));
	simple_resizer_linear_separable(const std::string& type, void(*coeffs_fn)(float* coeffs, position_t num,
			position_t denum, position_t width, position_t twidth, unsigned& base, unsigned& coeffcount,
			int algo), int algo);
	resizer& make(const std::string& type);
private:
	void(*coeffs_fn)(float* coeffs, position_t newsize, position_t oldsize, position_t width, position_t twidth,
		unsigned& base, unsigned& coeffcount);
	void(*coeffs_fn2)(float* coeffs, position_t newsize, position_t oldsize, position_t width,
		position_t twidth, unsigned& base, unsigned& coeffcount, int algo);
	int algo;
};

#endif
