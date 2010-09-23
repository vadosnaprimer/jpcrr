#include "newpacket.hpp"
#include "misc.hpp"
#include "output-drv.hpp"
#include "packet-processor.hpp"
#include <string>
#include <stdexcept>
#include "timeparse.hpp"
#include <iostream>

#define DEFAULT_RESIZE_TYPE "lanczos2"

int main(int argc, char** argv)
{
	int64_t _audio_delay = 0;
	int64_t _subtitle_delay = 0;
	uint32_t _audio_rate = 44100;
	uint32_t _width = 0;
	uint32_t _height = 0;
	uint32_t _rate_num = 0;
	uint32_t _rate_denum = 0;
	uint32_t _dedup_max = 0;
	std::string resize_type = DEFAULT_RESIZE_TYPE;
	std::map<std::pair<uint32_t, uint32_t>, std::string> special_resizers;
	bool sep = false;

	for(int i = 1; i < argc; i++) {
		std::string arg = argv[i];
		if(arg == "--") {
			sep = true;
			break;
		}
		if(!isstringprefix(arg, "--"))
			continue;
		try {
			if(isstringprefix(arg, "--video-width=")) {
				std::string value = settingvalue(arg);
				char* x;
				_width = strtoul(value.c_str(), &x, 10);
				if(*x || !_width) {
					std::cerr << "--video-width: Bad width" << std::endl;
					return 1;
				}
			} else if(isstringprefix(arg, "--video-height=")) {
				std::string value = settingvalue(arg);
				char* x;
				_height = strtoul(value.c_str(), &x, 10);
				if(*x || !_height) {
					std::cerr << "--video-height: Bad height" << std::endl;
					return 1;
				}
			} else if(isstringprefix(arg, "--video-scale-algo=")) {
				std::string tmptype = settingvalue(arg);
				uint32_t width = 0;
				uint32_t height = 0;
				size_t p = tmptype.find_first_of(" ");
				if(p > tmptype.length())
					resize_type = settingvalue(arg);
				else {
					std::string tmptype_tail = tmptype.substr(p + 1);
					tmptype = tmptype.substr(0, p);
					const char* x = tmptype_tail.c_str();
					char* end;
					width = (uint32_t)strtoul(x, &end, 10);
					if(*end != ' ')
						throw std::runtime_error("Bad resolution for resolution-dependent scaler (width).");
					x = end + 1;
					height = (uint32_t)strtoul(x, &end, 10);
					if(*end != '\0')
						throw std::runtime_error("Bad resolution for resolution-dependent scaler (height).");
					special_resizers[std::make_pair(width, height)] = tmptype;
				}
			} else if(isstringprefix(arg, "--video-scale-algo-")) {
				std::string tmptype = settingvalue(arg);
				uint32_t width = 0;
				uint32_t height = 0;

				special_resizers[std::make_pair(width, height)] = tmptype;
			} else if(arg == "--video-framerate=auto") {
				if(_rate_denum) {
					std::cerr << "Conflicts with earlier explicit fps: " << arg << "." << std::endl;
					return 1;
				}
				_rate_num = 1;
			} else if(isstringprefix(arg, "--video-framerate=")) {
				std::string value = settingvalue(arg);
				char* x;
				if(_rate_num || _rate_denum) {
					std::cerr << "Conflicts with earlier fps: " << arg << "." << std::endl;
					return 1;
				}
				_rate_num = strtoul(value.c_str(), &x, 10);
				if((*x != '\0' && *x != '/') || !_rate_num) {
					std::cerr << "--video-framerate: Bad value (n)" << std::endl;
					return 1;
				}
				if(*x) {
					x++;
					_rate_denum = strtoul(x, &x, 10);
					if(*x || !_rate_denum) {
						std::cerr << "--video-framerate: Bad value (d)" << std::endl;
						return 1;
					}
				} else
					_rate_denum = 1;

			} else if(isstringprefix(arg, "--video-max-dedup=")) {
				std::string value = settingvalue(arg);
				char* x;
				_dedup_max = strtoul(value.c_str(), &x, 10);
				if(*x) {
					std::cerr << "--video-dedup-max: Bad value" << std::endl;
					return 1;
				}
				if(_rate_denum) {
					std::cerr << "Conflicts with earlier explicit fps: " << arg << "." << std::endl;
					return 1;
				}
				_rate_num = 1;
			} else if(isstringprefix(arg, "--audio-delay=")) {
				std::string value = settingvalue(arg);
				if(value.length() && value[0] == '-')
					_audio_delay = -(int64_t)parse_timespec(value.substr(1));
				else
					_audio_delay = -(int64_t)parse_timespec(value);
			} else if(isstringprefix(arg, "--subtitle-delay=")) {
				std::string value = settingvalue(arg);
				if(value.length() && value[0] == '-')
					_subtitle_delay = -(int64_t)parse_timespec(value.substr(1));
				else
					_subtitle_delay = -(int64_t)parse_timespec(value);
			} else if(isstringprefix(arg, "--audio-mixer-")) {
				//We process these later.
			} else if(isstringprefix(arg, "--video-hardsub-")) {
				//We process these later.
			} else if(isstringprefix(arg, "--output-")) {
				//We process these later.
			} else {
				std::cerr << "Bad option: " << arg << "." << std::endl;
				return 1;
			}
		} catch(std::exception& e) {
			std::cerr << "Error processing option: " << arg << ":" << e.what() << std::endl;
			return 1;
		}

	}

	if(!_width || !_height) {
		std::cout << "usage: " << argv[0] << " [<options>] [--] <filename>..." << std::endl;
		std::cout << "Convert <filename> to variety of raw formats." << std::endl;
		std::cout << "--output-<type>=<file>[,<parameters>]" << std::endl;
		std::cout << "\tSend <type> output to <file>." << std::endl;
		std::cout << "\tSupported types: " << get_output_driver_list() << std::endl;
		std::cout << "--audio-delay=<delay>" << std::endl;
		std::cout << "\tSet audio delay to <delay> (may be negative). Default 0." << std::endl;
		std::cout << "--subtitle-delay=<delay>" << std::endl;
		std::cout << "\tSet subtitle delay to <delay> (may be negative). Default 0." << std::endl;
		std::cout << "--video-width=<width>" << std::endl;
		std::cout << "\tSet video width to <width>." << std::endl;
		std::cout << "--video-height=<height>" << std::endl;
		std::cout << "\tSet video width to <height>." << std::endl;
		std::cout << "--video-scale-algo=<algo>" << std::endl;
		std::cout << "\tSet video scaling algo to <algo>." << std::endl;
		std::cout << "--video-scale-algo=<algo> <w> <h>" << std::endl;
		std::cout << "\tSet video scaling algo for <w>x<h> frames to <algo>." << std::endl;
		std::cout << "\tSupported algorithms: " << get_resizer_list() << std::endl;
		std::cout << "--video-scale-framerate=<n>[/<d>]" << std::endl;
		std::cout << "\tSet video framerate to <n>/<d>." << std::endl;
		std::cout << "--video-scale-framerate=auto" << std::endl;
		std::cout << "\tSet video framerate to variable." << std::endl;
		std::cout << "--video-max-dedup=<frames>" << std::endl;
		std::cout << "\tSet maximum consequtive frames to elide to <frames>." << std::endl;
		print_hardsubs_help("--video-hardsub-");
		print_audio_resampler_help("--audio-mixer-");
		return 1;

	}

	if(!_rate_num && !_rate_denum) {
		_rate_num = 60;
		_rate_denum = 1;
	}

	audio_settings asettings(_audio_rate);
	video_settings vsettings(_width, _height, _rate_num, _rate_denum);
	subtitle_settings ssettings;
	set_audio_parameters(asettings);
	set_video_parameters(vsettings);
	set_subtitle_parameters(ssettings);


	sep = false;
	for(int i = 1; i < argc; i++) {
		std::string arg = argv[i];
		if(arg == "--") {
			sep = true;
			break;
		}
		if(!isstringprefix(arg, "--"))
			continue;
		if(isstringprefix(arg, "--output-")) {
			std::string type;
			std::string file;
			std::string parameters;
			size_t x = arg.find_first_of("=");
			if(x > arg.length()) {
				std::cerr << "Bad output specification: " << arg << "." << std::endl;
				return 1;
			}
			type = arg.substr(9, x - 9);
			file = arg.substr(x + 1);
			x = file.find_first_of(",");
			if(x < file.length()) {
				parameters = file.substr(x + 1);
				file = file.substr(0, x);
			}
			add_output_driver(type, file, parameters);
		}
	}

	packet_processor& p = create_packet_processor(_audio_delay, _subtitle_delay, _audio_rate, _width, _height,
		_rate_num, _rate_denum, _dedup_max, resize_type, special_resizers, argc, argv);
	sep = false;
	uint64_t timebase = 0;
	for(int i = 1; i < argc; i++) {
		std::string arg = argv[i];
		if(arg == "--") {
			sep = true;
			continue;
		}
		if(sep || !isstringprefix(arg, "--")) {
			read_channel rc(arg);
			timebase = send_stream(p, rc, timebase);
		}
	}
	p.send_end_of_stream();
	close_output_drivers();
}
