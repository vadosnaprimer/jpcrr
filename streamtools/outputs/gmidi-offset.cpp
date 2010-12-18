#include "outputs/internal.hpp"
#include <fstream>
#include <stdexcept>
#include <string>
#include <iostream>

namespace
{
	class output_driver_gmidi_offset : public output_driver
	{
	public:
		output_driver_gmidi_offset(const std::string& filename)
		{
			if(filename != "-")
				out = new std::ofstream(filename.c_str(), std::ios_base::binary);
			else
				out = &std::cout;
			if(!*out)
				throw std::runtime_error("Unable to open output file");

			set_gmidi_callback(make_bound_method(*this, &output_driver_gmidi_offset::midi_data));
			first = true;
		}

		~output_driver_gmidi_offset()
		{
			if(out != &std::cout)
				delete out;
		}

		void ready()
		{
		}

		void midi_data(uint64_t timestamp, uint8_t data)
		{
			if(first)
				(*out) << timestamp << std::endl;
			first = false;
		}

	private:
		std::ostream* out;
		bool first;
	};

	class output_driver_gmidi_offset_factory : output_driver_factory
	{
	public:
		output_driver_gmidi_offset_factory()
			: output_driver_factory("gmidi-offset")
		{
		}

		output_driver& make(const std::string& type, const std::string& name, const std::string& parameters)
		{
			if(parameters != "")
				throw std::runtime_error("gmidi-offset output does not take parameters");
			return *new output_driver_gmidi_offset(name);
		}
	} factory;
}
