#include "output-drv.hpp"
#include <cstdio>
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
				out = fopen(filename.c_str(), "wb");
			else
				out = stdout;
			if(!out)
				throw std::runtime_error("Unable to open output file");
			set_video_callback<output_driver_rawrgbx>(*this, &output_driver_rawrgbx::video_callback);
		}

		~output_driver_rawrgbx()
		{
			fclose(out);
		}

		void ready()
		{
			const video_settings& v = get_video_settings();
			framesize = 4 * v.get_width() * v.get_height();
		}

		void video_callback(uint64_t timestamp, const uint8_t* raw_rgbx_data)
		{
			if(fwrite(raw_rgbx_data, 1, framesize, out) < framesize)
				throw std::runtime_error("Error writing frame to file");
		}
	private:
		FILE* out;
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
