#include "output-drv.hpp"
#include <cstdio>
#include <stdexcept>
#include <string>

namespace
{
	class output_driver_timecodev2 : public output_driver
	{
	public:
		output_driver_timecodev2(const std::string& filename)
		{
			if(filename != "-")
				out = fopen(filename.c_str(), "wb");
			else
				out = stdout;
			if(!out)
				throw std::runtime_error("Unable to open output file");
			fprintf(out, "# timecode format v2\n");
			set_video_callback<output_driver_timecodev2>(*this, &output_driver_timecodev2::video_callback);
		}

		~output_driver_timecodev2()
		{
			fclose(out);
		}

		void ready()
		{
		}

		void video_callback(uint64_t timestamp, const uint8_t* raw_rgbx_data)
		{
			fprintf(out, "%llu\n", (unsigned long long)timestamp / 1000000);
		}
	private:
		FILE* out;
	};

	class output_driver_timecodev2_factory : output_driver_factory
	{
	public:
		output_driver_timecodev2_factory()
			: output_driver_factory("timecodev2")
		{
		}

		output_driver& make(const std::string& type, const std::string& name, const std::string& parameters)
		{
			if(parameters != "")
				throw std::runtime_error("timecodev2 output does not take parameters");
			return *new output_driver_timecodev2(name);
		}
	} factory;
}
