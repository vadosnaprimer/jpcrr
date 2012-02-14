#include <cstring>
#include "outputs/internal.hpp"
#include "outputs/I420.hpp"
#include "outputs/argexpand.hpp"
#include <cstdio>
#include <stdexcept>
#include <vector>
#include <string>
#include <sstream>

namespace
{
	class output_driver_ivfenc : public output_driver
	{
	public:
		output_driver_ivfenc(const std::string& _filename, const std::string& _options)
		{
			filename = _filename;
			options = _options;
			set_video_callback(make_bound_method(*this, &output_driver_ivfenc::video_callback));
		}

		~output_driver_ivfenc()
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
			std::string executable = "ivfenc";
			std::string x = expand_arguments_common(options, "--", "=", executable);
			commandline << executable << " --width=" << v.get_width() << " --height=" << v.get_height() << " ";
			if(v.get_rate_denum())
				commandline << "--timebase " << v.get_rate_denum() << "/" << v.get_rate_num() << " ";
			commandline << x << " - " << filename;
			std::string s = commandline.str();
			out = popen(s.c_str(), "w");
			if(!out) {
				std::stringstream str;
				str << "Can't run ivfenc (" << s << ")";
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

	class output_driver_ivfenc_factory : output_driver_factory
	{
	public:
		output_driver_ivfenc_factory()
			: output_driver_factory("ivfenc")
		{
		}

		output_driver& make(const std::string& type, const std::string& name, const std::string& parameters)
		{
			return *new output_driver_ivfenc(name, parameters);
		}
	} factory;
}
