#ifndef _outputs__I420__hpp__included__
#define _outputs__I420__hpp__included__

#include <cstdint>
#include <cstdio>
#include <iostream>

void I420_convert_common(const uint8_t* raw_rgbx_data, uint32_t width, uint32_t height, FILE* out, bool uvswap);
void I420_convert_common(const uint8_t* raw_rgbx_data, uint32_t width, uint32_t height, std::ostream& out,
	bool uvswap);

#endif
