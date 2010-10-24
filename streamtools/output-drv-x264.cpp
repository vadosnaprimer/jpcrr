#include <cstring>
#include "output-drv.hpp"
#include <cstdio>
#include <stdexcept>
#include <vector>
#include <string>
#include <sstream>
#include "rgbtorgb.hh"

namespace
{
	std::string expand_options(const std::string& opts, uint32_t rn, uint32_t rd)
	{
		std::ostringstream ret;
		if(rd)
			ret << "--fps " << rn << "/" << rd << " ";
		ret << expand_arguments_common(opts, "--", " ");
		return ret.str();
	}

	class output_driver_x264 : public output_driver
	{
	public:
		output_driver_x264(const std::string& _filename, const std::string& _options)
		{
			filename = _filename;
			options = _options;
			set_video_callback<output_driver_x264>(*this, &output_driver_x264::video_callback);
		}

		~output_driver_x264()
		{
			pclose(out);
		}

		void ready()
		{
			const video_settings& v = get_video_settings();
			framesize = 4 * v.get_width() * v.get_height();
			width = v.get_width();
			height = v.get_height();

			std::stringstream commandline;
			commandline << "x264 ";
			commandline << expand_options(options, v.get_rate_num(), v.get_rate_denum());
			commandline << " - -o " << filename << " " << v.get_width() << "x" << v.get_height();
			std::string s = commandline.str();
			out = popen(s.c_str(), "w");
			if(!out) {
				std::stringstream str;
				str << "Can't run x264 (" << s << ")";
				throw std::runtime_error(str.str());
			}
		}

		void video_callback(uint64_t timestamp, const uint8_t* raw_rgbx_data)
		{
			I420_convert_common(raw_rgbx_data, width, height, out, true);
		}
	private:
		FILE* out;
		std::string filename;
		std::string options;
		size_t framesize;
		uint32_t width;
		uint32_t height;
	};

	class output_driver_x264_factory : output_driver_factory
	{
	public:
		output_driver_x264_factory()
			: output_driver_factory("x264")
		{
		}

		output_driver& make(const std::string& type, const std::string& name, const std::string& parameters)
		{
			return *new output_driver_x264(name, parameters);
		}
	} factory;
}
