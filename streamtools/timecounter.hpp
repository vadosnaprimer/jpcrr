#ifndef _timecounter__hpp__included__
#define _timecounter__hpp__included__

#include <stdint.h>
#include <string>

class timecounter
{
public:
	timecounter(const std::string& spec);
	timecounter(uint32_t spec);
	timecounter(uint32_t n, uint32_t d);
	operator uint64_t();
	timecounter& operator++();
	timecounter operator++(int);
private:
	uint64_t current_w;
	uint64_t current_n;
	uint64_t step_w;
	uint64_t step_n;
	uint64_t step_d;
};

#endif