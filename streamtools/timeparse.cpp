#include "timeparse.hpp"
#include <string>
#include <sstream>
#include <stdexcept>

#define MAXTIME 0xFFFFFFFFFFFFFFFFULL

uint64_t parse_timespec(const std::string& str)
{
	uint64_t scale = 0;
	uint64_t value = 0;
	for(int i = 0; str[i]; i++) {
		if(isdigit((unsigned char)str[i])) {
			if(scale == 1)
				goto bad;
			else if(scale > 1)
				scale /= 10;
			uint64_t num = str[i] - '0';
			if((MAXTIME - num) / 10 < value)
				goto bad;
			value = 10 * value + num;
		} else if(str[i] == '.') {
			if(scale > 0)
				goto bad;
			scale = 1000000000L;
		} else
			goto bad;
	}

	if(scale) {
		if(MAXTIME / scale < value)
			goto bad;
		value *= scale;
	}

	return value;
bad:
	std::stringstream str_err;
	str_err <<  "Bad timespec: " << str;
	throw std::runtime_error(str_err.str());
}
