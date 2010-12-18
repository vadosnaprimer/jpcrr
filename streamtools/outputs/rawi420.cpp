#include <cstring>
#include "outputs/internal.hpp"
#include "outputs/I420.hpp"
#include <iostream>
#include <fstream>
#include <stdexcept>
#include <vector>
#include <string>

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
			set_video_callback(make_bound_method(*this, &output_driver_rawi420::video_callback));
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
			height = v.get_height();
		}

		void video_callback(uint64_t timestamp, const uint8_t* raw_rgbx_data)
		{
			I420_convert_common(raw_rgbx_data, width, height, *out, !uvswap);
		}
	private:
		std::ostream* out;
		size_t framesize;
		uint32_t width;
		uint32_t height;
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
