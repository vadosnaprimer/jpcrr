#include "output-drv.hpp"
#include <iostream>
#include <fstream>
#include <stdexcept>
#include <string>

namespace
{
	class output_driver_rawaudio : public output_driver
	{
	public:
		output_driver_rawaudio(const std::string& filename)
		{
			if(filename != "-")
				out = new std::ofstream(filename.c_str(), std::ios_base::binary);
			else
				out = &std::cout;
			if(!*out)
				throw std::runtime_error("Unable to open output file");
			set_audio_callback<output_driver_rawaudio>(*this, &output_driver_rawaudio::audio_callback);
		}

		~output_driver_rawaudio()
		{
			if(out != &std::cout)
				delete out;
		}

		void ready()
		{
		}

		void audio_callback(short left, short right)
		{
			uint8_t rawdata[4];
			rawdata[1] = ((unsigned short)left >> 8) & 0xFF;
			rawdata[0] = ((unsigned short)left) & 0xFF;
			rawdata[3] = ((unsigned short)right >> 8) & 0xFF;
			rawdata[2] = ((unsigned short)right) & 0xFF;
			out->write((const char*)rawdata, 4);
			if(!*out)
				throw std::runtime_error("Error writing frame to file");
		}
	private:
		std::ostream* out;
	};

	class output_driver_rawaudio_factory : output_driver_factory
	{
	public:
		output_driver_rawaudio_factory()
			: output_driver_factory("rawaudio")
		{
		}

		output_driver& make(const std::string& type, const std::string& name, const std::string& parameters)
		{
			if(parameters != "")
				throw std::runtime_error("rawaudio output does not take parameters");
			return *new output_driver_rawaudio(name);
		}
	} factory;
}
