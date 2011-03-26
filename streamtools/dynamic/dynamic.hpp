#ifndef _dynamic__hpp__included__
#define _dynamic__hpp__included__

#include <vector>
#include <stdint.h>

void* commit_machine_code(const std::vector<uint8_t>& code);
void generate_hdscaler(std::vector<uint8_t>& code, uint32_t* strip_widths, uint32_t* strip_heights, uint32_t swidth,
	uint32_t sheight, uint32_t twidth);

#endif
