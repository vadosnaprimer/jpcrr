#include "outputs/I420.hpp"
#include "outputs/rgbtorgb.hpp"
#include <vector>
#include <sstream>
#include <stdexcept>

namespace
{
	template <class T>
	void I420_convert_common(const uint8_t* raw_rgbx_data, uint32_t width, uint32_t height, bool uvswap,
		void (*writer)(T target, const uint8_t* buffer, size_t bufsize), T target)
	{
		size_t framesize = 4 * (size_t)width * height;
		std::vector<unsigned char> tmp(framesize * 3 / 8);
		size_t primarysize = framesize / 4;
		size_t offs1 = 0;
		size_t offs2 = primarysize / 4;
		if(uvswap)
			std::swap(offs1, offs2);
		Convert32To_I420Frame(raw_rgbx_data, &tmp[0], framesize / 4, width);
		writer(target, &tmp[0], primarysize);
		writer(target, &tmp[primarysize + offs1], primarysize / 4);
		writer(target, &tmp[primarysize + offs2], primarysize / 4);
	}

	void writer_stdio(FILE* target, const uint8_t* buffer, size_t bufsize)
	{
		size_t r;
		if((r = fwrite(buffer, 1, bufsize, target)) < bufsize) {
			std::stringstream str;
			str << "Error writing frame to output (requested " << bufsize << ", got " << r << ")";
			throw std::runtime_error(str.str());
		}
	}

	void writer_iostream(std::ostream* target, const uint8_t* buffer, size_t bufsize)
	{
		target->write((const char*)buffer, bufsize);
		if(!*target) {
			std::stringstream str;
			str << "Error writing frame to output (requested " << bufsize << ")";
			throw std::runtime_error(str.str());
		}
	}
}

void I420_convert_common(const uint8_t* raw_rgbx_data, uint32_t width, uint32_t height, FILE* out, bool uvswap)
{
	I420_convert_common(raw_rgbx_data, width, height, uvswap, writer_stdio, out);
}

void I420_convert_common(const uint8_t* raw_rgbx_data, uint32_t width, uint32_t height, std::ostream& out,
	bool uvswap)
{
	I420_convert_common(raw_rgbx_data, width, height, uvswap, writer_iostream, &out);
}
