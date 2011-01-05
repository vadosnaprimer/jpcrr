#ifndef _rescalers__simple__hpp__included__
#define _rescalers__simple__hpp__included__

#include <stdint.h>
#include <string>
#include "bound-method.hpp"
#include "rescalers/factory.hpp"

//The parameters in order are target, twidth, theight, source, swidth and sheight.
typedef bound_method<void, uint8_t*, uint32_t, uint32_t, const uint8_t*, uint32_t, uint32_t> bound_scaler_t;

class simple_rescaler : public rescaler_factory
{
public:
	simple_rescaler(const std::string& type, bound_scaler_t _fn);
	rescaler& make(const std::string& type);
private:
	bound_scaler_t fn;
};

#endif
