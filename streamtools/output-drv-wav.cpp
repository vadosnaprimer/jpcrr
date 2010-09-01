#define RIFF_MAGIC 0x46464952
#define WAVE_MAGIC 0x45564157
#define FMT_MAGIC 0x20746d66
#define DATA_MAGIC 0x61746164

#include "output-drv.hpp"
#include <cstdio>
#include <stdexcept>
#include <string>

namespace
{
	static void write_little32(unsigned char* to, uint32_t value)
	{
		to[0] = (value) & 0xFF;
		to[1] = (value >> 8) & 0xFF;
		to[2] = (value >> 16) & 0xFF;
		to[3] = (value >> 24) & 0xFF;
	}

	static void write_little16(unsigned char* to, uint16_t value)
	{
		to[0] = (value) & 0xFF;
		to[1] = (value >> 8) & 0xFF;
	}

	void write_wav_header(FILE* out, uint32_t rate, uint64_t samples)
	{
		unsigned char header[44];
		uint32_t size1;
		uint32_t size2;
		size2 = (uint32_t)(samples << 2);
		size1 = size2 + 36;
		write_little32(header + 0, RIFF_MAGIC);		//Main RIFF header magic
		write_little32(header + 4, size1);		//RIFF payload size.
		write_little32(header + 8, WAVE_MAGIC);		//This is WAVE data.
		write_little32(header + 12, FMT_MAGIC);		//Format data.
		write_little32(header + 16, 16);		//16 bytes of format data
		write_little16(header + 20, 1);			//PCM encoded.
		write_little16(header + 22, 2);			//Stereo
		write_little32(header + 24, rate);		//Sample rate.
		write_little32(header + 28, rate << 2);		//Data rate.
		write_little16(header + 32, 4);			//4 bytes per sample.
		write_little16(header + 34, 16);		//16 bits per sample and channel.
		write_little32(header + 36, DATA_MAGIC);	//Actual data.
		write_little32(header + 40, size2);		//Data size.
		if(fseek(out, 0, SEEK_SET) < 0 || fwrite(header, 1, 44, out) < 44)
			throw std::runtime_error("Failed to write WAV header");
	}

	class output_driver_wav : public output_driver
	{
	public:
		output_driver_wav(const std::string& filename)
		{
			if(filename != "-")
				out = fopen(filename.c_str(), "wb");
			else
				throw std::runtime_error("WAV driver does not support output to stdout");
			if(!out)
				throw std::runtime_error("Unable to open output file");
			write_wav_header(out, 0, 0);
			set_audio_callback<output_driver_wav>(*this, &output_driver_wav::audio_callback);
		}

		~output_driver_wav()
		{
			write_wav_header(out, rate, samples);
			fclose(out);
		}

		void ready()
		{
			rate = get_audio_settings().get_rate();
			samples = 0;
		}

		void audio_callback(short left, short right)
		{
			uint8_t rawdata[4];
			rawdata[1] = ((unsigned short)left >> 8) & 0xFF;
			rawdata[0] = ((unsigned short)left) & 0xFF;
			rawdata[3] = ((unsigned short)right >> 8) & 0xFF;
			rawdata[2] = ((unsigned short)right) & 0xFF;
			if(fwrite(rawdata, 1, 4, out) < 4)
				throw std::runtime_error("Error writing sample to file");
			samples++;
		}

	private:
		FILE* out;
		uint32_t rate;
		uint64_t samples;
	};

	class output_driver_wav_factory : output_driver_factory
	{
	public:
		output_driver_wav_factory()
			: output_driver_factory("wav")
		{
		}

		output_driver& make(const std::string& type, const std::string& name, const std::string& parameters)
		{
			if(parameters != "")
				throw std::runtime_error("wav output does not take parameters");
			return *new output_driver_wav(name);
		}
	} factory;
}
