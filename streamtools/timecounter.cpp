#include "timecounter.hpp"
#include <stdexcept>
#include <sstream>

timecounter::timecounter(const std::string& spec)
{
	uint64_t base = 1000000000;
	bool decimal = false;
	uint64_t readfpu = 0;

	if(!spec.length())
		throw std::runtime_error("Empty fps spec is not legal");

	for(size_t i = 0; i < spec.length(); i++) {
		if(readfpu > 1844674407370955160ULL)
			throw std::runtime_error("Overflow reading number");
		if(!decimal)
			if(spec[i] >= '0' && spec[i] <= '9')
				readfpu = 10 * readfpu + (spec[i] - '0');
			else if(spec[i] == '.')
				decimal = true;
			else {
				std::stringstream str;
				str << "Expected number or '.', got '" << spec[i] << "'";
				throw std::runtime_error(str.str());
			}
		else
			if(spec[i] >= '0' && spec[i] <= '9') {
				if(base == 10000000000000000000ULL) {
					std::stringstream str;
					str << "fps number has more than 10 decimal digits";
					throw std::runtime_error(str.str());
				}
				base *= 10;
				readfpu = 10 * readfpu + (spec[i] - '0');
			} else {
				std::stringstream str;
				str << "Expected number, got '" << spec[i] << "'";
				throw std::runtime_error(str.str());
			}
	}

	if(!readfpu)
		throw std::runtime_error("0 is not valid fps value");

	step_w = base / readfpu;
	step_n = base % readfpu;
	step_d = readfpu;
	current_w = 0;
	current_n = 0;
}

timecounter::timecounter(uint32_t spec)
{
	if(spec > 0) {
		step_w = 1000000000 / spec;
		step_n = 1000000000 % spec;
		step_d = spec;
	} else {
		step_w = step_n = 0;
		step_d = 1;
	}
	current_w = 0;
	current_n = 0;
}

timecounter::timecounter(uint32_t n, uint32_t d)
{
	step_w = (1000000000ULL * d) / n;
	step_n = (1000000000ULL * d) % n;
	step_d = n;
	current_w = 0;
	current_n = 0;
}

timecounter::operator uint64_t()
{
	return current_w;
}

timecounter& timecounter::operator++()
{
	current_w += step_w;
	current_n += step_n;
	while(current_n >= step_d) {
		current_n -= step_d;
		current_w++;
	}
	return *this;
}

timecounter timecounter::operator++(int)
{
	timecounter scratch = *this;
	++*this;
	return scratch;
}
