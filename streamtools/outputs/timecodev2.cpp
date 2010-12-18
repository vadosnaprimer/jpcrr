#include "outputs/internal.hpp"
#include <fstream>
#include <stdexcept>
#include <string>
#include <iostream>

namespace
{
	class output_driver_timecodev2 : public output_driver
	{
	public:
		output_driver_timecodev2(const std::string& filename)
		{
			if(filename != "-")
				out = new std::ofstream(filename.c_str(), std::ios_base::binary);
			else
				out = &std::cout;
			if(!*out)
				throw std::runtime_error("Unable to open output file");
			*out << "# timecode format v2" << std::endl;
			set_video_callback(make_bound_method(*this, &output_driver_timecodev2::video_callback));
		}

		~output_driver_timecodev2()
		{
			if(out != &std::cout)
				delete out;
		}

		void ready()
		{
		}

		void video_callback(uint64_t timestamp, const uint8_t* raw_rgbx_data)
		{
			*out << (unsigned long long)timestamp / 1000000 << std::endl;
		}
	private:
		std::ostream* out;
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
