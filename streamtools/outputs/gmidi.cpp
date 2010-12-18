#include "outputs/internal.hpp"
#include <fstream>
#include <stdexcept>
#include <string>
#include <iostream>
#include <vector>

#define MIDI_BUFFER 256

namespace
{
	unsigned get_event_length(unsigned char ev)
	{
		if(ev < 128)
			return 0;
		if(ev < 192)
			return 3;
		if(ev < 224)
			return 2;
		if(ev < 240)
			return 3;
		switch(ev) {
		case 240:	case 244:	case 245:	case 247:
		case 249:	case 253:	case 255:
			return 0;
		case 246:	case 248:	case 250:	case 251:
		case 252:	case 254:
			return 1;
		case 241:	case 243:
			return 2;
		case 242:
			return 3;
		};
		return 0;	// Should not be here!
	}

	unsigned char midi_file_header[] = {
		'M', 'T', 'h', 'd', 0, 0, 0, 6, 0, 0, 0, 1,
		1, 0xF4, 'M', 'T', 'r', 'k', 0, 0, 0, 0
	};

	class output_driver_gmidi : public output_driver
	{
	public:
		output_driver_gmidi(const std::string& filename)
		{
			if(filename != "-")
				out = new std::ofstream(filename.c_str(), std::ios_base::binary);
			else
				out = &std::cout;
			if(!*out)
				throw std::runtime_error("Unable to open output file");

			set_gmidi_callback(make_bound_method(*this, &output_driver_gmidi::midi_data));
			set_audio_end_callback(make_bound_method(*this, &output_driver_gmidi::midi_end));

			out->write((const char*)midi_file_header, sizeof(midi_file_header));
			first = true;
			target_command_length = 0;
		}

		~output_driver_gmidi()
		{
			if(out != &std::cout)
				delete out;
		}

		void ready()
		{
		}

		void midi_data(uint64_t timestamp, uint8_t data)
		{
			//Is there SysEx in progress?
			if(command_buffer.size() > 0 && command_buffer[0] == 0xF0) {
				// Process SysEx transfer.
				if(data & 0x80) {
					// End the SysEx in progress.
					command_buffer.push_back(0xF7);
					do_sysex(timestamp, command_buffer);
					command_buffer.clear();
					target_command_length = 0;
				} else {
					// One more byte...
					command_buffer.push_back(data);
					return;
				}
			}
			// Ok, this not SysEx (or it just ended). If this is command, process it.
			if(data & 0x80) {
				command_buffer.clear();
				target_command_length = get_event_length(data);
			}
			// Process the bytes in command.
			if(target_command_length) {
				command_buffer.push_back(data);
				if(command_buffer.size() == target_command_length) {
					do_midi_event(timestamp, command_buffer);
					command_buffer.resize(1);	//Preserve status unless there is new one.
				}
			}
		}

		void midi_end()
		{
			// End the MIDI track.
			push_raw_byte(0);
			push_raw_byte(0xFF);
			push_raw_byte(0x2F);
			push_raw_byte(0);
			flush_midi_buffer();
			out->seekp(18, std::ios_base::beg);
			unsigned char tmp[4];
			tmp[0] = (midi_bytes >> 24) & 0xFF;
			tmp[1] = (midi_bytes >> 16) & 0xFF;
			tmp[2] = (midi_bytes >> 8) & 0xFF;
			tmp[3] = midi_bytes & 0xFF;
			out->write((const char*)tmp, 4);
		}
	private:
		void write_timestamp(uint64_t cur_ts)
		{
			if(first) {
				first = false;
				last_ts = cur_ts;
			}
			uint32_t delta = (cur_ts - last_ts) / 1000000;
			push_raw_number((uint32_t)delta);
			last_ts = cur_ts;
		}

		void do_sysex(uint64_t ts, std::vector<uint8_t> data)
		{
			write_timestamp(ts);
			push_raw_byte(0xF0);
			push_raw_number((uint32_t)data.size() - 1);
			for(auto i = data.begin() + 1; i != data.end(); ++i)
				push_raw_byte(*i);
		}

		void do_midi_event(uint64_t ts, std::vector<uint8_t> data)
		{
			write_timestamp(ts);
			for(auto i = data.begin(); i != data.end(); ++i)
				push_raw_byte(*i);
		}

		void push_raw_number(uint32_t number)
		{
			for(int i = 28; i > 0; i -= 7)
				if(number >> i)
					push_raw_byte(((number >> i) & 0x7F) | 0x80);
			push_raw_byte(number & 0x7F);
		}

		void push_raw_byte(unsigned char byte)
		{
			midi_buffer[midi_buffer_fill++] = byte;
			if(midi_buffer_fill == MIDI_BUFFER)
				flush_midi_buffer();
		}

		void flush_midi_buffer()
		{
			if(midi_buffer_fill > 0)
				out->write((const char*)midi_buffer, midi_buffer_fill);
			midi_bytes += midi_buffer_fill;
			midi_buffer_fill = 0;
		}

		std::ostream* out;
		unsigned midi_bytes;
		unsigned midi_buffer_fill;
		unsigned char midi_buffer[MIDI_BUFFER];
		uint64_t last_ts;
		bool first;
		std::vector<uint8_t> command_buffer;
		unsigned target_command_length;
	};

	class output_driver_gmidi_factory : output_driver_factory
	{
	public:
		output_driver_gmidi_factory()
			: output_driver_factory("gmidi")
		{
		}

		output_driver& make(const std::string& type, const std::string& name, const std::string& parameters)
		{
			if(parameters != "")
				throw std::runtime_error("gmidi output does not take parameters");
			return *new output_driver_gmidi(name);
		}
	} factory;
}
