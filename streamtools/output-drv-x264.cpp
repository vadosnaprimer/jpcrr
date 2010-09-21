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
		bool insert = true;
		std::ostringstream ret;
		if(rd)
			ret << "--fps " << rn << "/" << rd << " ";
		for(size_t i = 0; i < opts.length(); i++) {
			if(insert)
				ret << " --";
			insert = false;
			switch(opts[i]) {
			case ',':
				insert = true;
				break;
			case '=':
				ret << " ";
				break;
			default:
				ret << opts[i];
			};
		}
		ret << " ";
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

			std::stringstream commandline;
			commandline << "x264 ";
			commandline << expand_options(options, v.get_rate_num(), v.get_rate_denum());
			commandline << "- -o " << filename << " " << v.get_width() << "x" << v.get_height();
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
			std::vector<unsigned char> tmp(framesize * 3 / 8);
			size_t primarysize = framesize / 4;
			size_t offs1 = primarysize / 4;
			size_t offs2 = 0;
			Convert32To_I420Frame(raw_rgbx_data, &tmp[0], framesize / 4, width);
			size_t r;
			if((r = fwrite(&tmp[0], 1, primarysize, out)) < primarysize) {
				std::stringstream str;
				str << "Error writing frame to x264 (requested " << primarysize << ", got " << r << ")";
				throw std::runtime_error(str.str());
			}
			//Swap U and V.
			if((r = fwrite(&tmp[primarysize + offs1], 1, primarysize / 4, out)) < primarysize / 4) {
				std::stringstream str;
				str << "Error writing frame to x264 (requested " << primarysize / 4 << ", got " << r << ")";
				throw std::runtime_error(str.str());
			}
			if((r = fwrite(&tmp[primarysize + offs2], 1, primarysize / 4, out)) < primarysize / 4) {
				std::stringstream str;
				str << "Error writing frame to x264 (requested " << primarysize / 4 << ", got " << r << ")";
				throw std::runtime_error(str.str());
			}
		}
	private:
		FILE* out;
		std::string filename;
		std::string options;
		size_t framesize;
		size_t width;
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
