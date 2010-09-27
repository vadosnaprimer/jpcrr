#include <cstring>
#include "output-drv.hpp"
#include <iostream>
#include <fstream>
#include <stdexcept>
#include <vector>
#include <string>
#include <sstream>
#include "rgbtorgb.hh"

namespace
{
	class output_driver_rawi420 : public output_driver
	{
	public:
		output_driver_rawi420(const std::string& filename, bool _uvswap)
		{
			uvswap = _uvswap;
			if(filename != "-")
				out = new std::ofstream(filename.c_str(), std::ios_base::binary);
			else
				out = &std::cout;
			if(!*out)
				throw std::runtime_error("Unable to open output file");
			set_video_callback<output_driver_rawi420>(*this, &output_driver_rawi420::video_callback);
		}

		~output_driver_rawi420()
		{
			if(out != &std::cout)
				delete out;
		}

		void ready()
		{
			const video_settings& v = get_video_settings();
			framesize = 4 * v.get_width() * v.get_height();
			width = v.get_width();
		}

		void video_callback(uint64_t timestamp, const uint8_t* raw_rgbx_data)
		{
			std::vector<unsigned char> tmp(framesize * 3 / 8);
			size_t primarysize = framesize / 4;
			size_t offs1 = primarysize / 4;
			size_t offs2 = 0;
			if(uvswap)
				std::swap(offs1, offs2);
			Convert32To_I420Frame(raw_rgbx_data, &tmp[0], framesize / 4, width);
			size_t r;
			out->write((const char*)&tmp[0], primarysize);
			if(!*out) {
				std::stringstream str;
				str << "Error writing frame to file (requested " << primarysize << ", got " << r
					<< ")";
				throw std::runtime_error(str.str());
			}
			//Swap U and V.
			out->write((const char*)&tmp[primarysize + offs1], primarysize / 4);
			if(!*out) {
				std::stringstream str;
				str << "Error writing frame to file (requested " << primarysize / 4 << ", got "
					<< r << ")";
				throw std::runtime_error(str.str());
			}
			out->write((const char*)&tmp[primarysize + offs2], primarysize / 4);
			if(!*out) {
				std::stringstream str;
				str << "Error writing frame to file (requested " << primarysize / 4 << ", got "
					<< r << ")";
				throw std::runtime_error(str.str());
			}
		}
	private:
		std::ostream* out;
		size_t framesize;
		size_t width;
		bool uvswap;
	};

	class output_driver_rawi420_factory : output_driver_factory
	{
	public:
		output_driver_rawi420_factory(const std::string& name, bool _uvswap)
			: output_driver_factory(name)
		{
			uvswap = _uvswap;
		}

		output_driver& make(const std::string& type, const std::string& name, const std::string& parameters)
		{
			if(parameters != "")
				throw std::runtime_error("rawi420 output does not take parameters");
			return *new output_driver_rawi420(name, uvswap);
		}
	private:
		bool uvswap;
	};
	output_driver_rawi420_factory fact1("rawi420", false);
	output_driver_rawi420_factory fact2("rawi420-uvswap", true);
}
