#include "outputs/internal.hpp"
#include "outputs/argexpand.hpp"
#include <cstdio>
#include <stdexcept>
#include <string>
#include <sstream>
#include <fcntl.h>

namespace
{
	class output_driver_opusenc : public output_driver
	{
	public:
		output_driver_opusenc(const std::string& _filename, const std::string& _options)
		{
			filename = _filename;
			options = _options;
			set_audio_callback(make_bound_method(*this, &output_driver_opusenc::audio_callback));
		}

		~output_driver_opusenc()
		{
			pclose(out);
		}

		void ready()
		{
			const audio_settings& a = get_audio_settings();

			std::stringstream commandline;
			std::string executable = "opusenc";
			std::string x = expand_arguments_common(options, "--", "=", executable);
			commandline << executable << " --raw --raw-rate " << a.get_rate() << " ";
			commandline << x << " - " << filename;
			std::string s = commandline.str();
			out = popen(s.c_str(), "w");
			if(!out) {
				std::stringstream str;
				str << "Can't run opusenc (" << s << ")";
				throw std::runtime_error(str.str());
			}
#if defined(_WIN32) || defined(_WIN64)
			setmode(fileno(out), O_BINARY);
#endif
		}

		void audio_callback(short left, short right)
		{
			uint8_t rawdata[4];
			rawdata[1] = ((unsigned short)left >> 8) & 0xFF;
			rawdata[0] = ((unsigned short)left) & 0xFF;
			rawdata[3] = ((unsigned short)right >> 8) & 0xFF;
			rawdata[2] = ((unsigned short)right) & 0xFF;
			if(fwrite(rawdata, 1, 4, out) < 4)
				throw std::runtime_error("Error writing sample to opusenc");
		}
	private:
		FILE* out;
		std::string filename;
		std::string options;
	};

	class output_driver_opusenc_factory : output_driver_factory
	{
	public:
		output_driver_opusenc_factory()
			: output_driver_factory("opusenc")
		{
		}

		output_driver& make(const std::string& type, const std::string& name, const std::string& parameters)
		{
			return *new output_driver_opusenc(name, parameters);
		}
	} factory;
}
