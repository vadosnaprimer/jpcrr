#include "outputs/internal.hpp"
#include <iostream>
#include <fstream>
#include <stdexcept>
#include <string>

namespace
{
	class output_driver_rawrgbx : public output_driver
	{
	public:
		output_driver_rawrgbx(const std::string& filename)
		{
			if(filename != "-")
				out = new std::ofstream(filename.c_str(), std::ios_base::binary);
			else
				out = &std::cout;
			if(!*out)
				throw std::runtime_error("Unable to open output file");
			set_video_callback(make_bound_method(*this, &output_driver_rawrgbx::video_callback));
		}

		~output_driver_rawrgbx()
		{
			if(out != &std::cout)
				delete out;
		}

		void ready()
		{
			const video_settings& v = get_video_settings();
			framesize = 4 * v.get_width() * v.get_height();
		}

		void video_callback(uint64_t timestamp, const uint8_t* raw_rgbx_data)
		{
			out->write((const char*)raw_rgbx_data, framesize);
			if(!*out)
				throw std::runtime_error("Error writing frame to file");
		}
	private:
		std::ostream* out;
		size_t framesize;
	};

	class output_driver_rawrgbx_factory : output_driver_factory
	{
	public:
		output_driver_rawrgbx_factory()
			: output_driver_factory("rawrgbx")
		{
		}

		output_driver& make(const std::string& type, const std::string& name, const std::string& parameters)
		{
			if(parameters != "")
				throw std::runtime_error("rawrgbx output does not take parameters");
			return *new output_driver_rawrgbx(name);
		}
	} factory;
}
