#ifndef _rescalers__factory__hpp__included__
#define _rescalers__factory__hpp__included__

#include <stdint.h>
#include <string>
#include "rescalers/public.hpp"

class rescaler_factory
{
public:
	rescaler_factory(const std::string& type);
	virtual ~rescaler_factory();
	virtual rescaler& make(const std::string& type) = 0;
};

#endif
