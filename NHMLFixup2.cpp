#include "nhml.hpp"
#include "fixup.hpp"
#include <stdexcept>
#include <iostream>
#include <boost/lexical_cast.hpp>

namespace
{
	std::pair<int64_t, int64_t> parse_xy(const std::string& val)
	{
		std::string v = val;
		size_t s = v.find_first_of(":");
		if(s < v.length()) {
			std::string v1 = v.substr(0, s);
			std::string v2 = v.substr(s + 1);
			return std::make_pair(boost::lexical_cast<int64_t>(v1), boost::lexical_cast<int64_t>(v2));
		} else
			return std::make_pair(0, 0);
	}

	std::pair<int64_t, int64_t> parse_xy2(const std::string& val)
	{
		std::string v = val;
		size_t s = v.find_first_of("/");
		if(s < v.length()) {
			std::string v1 = v.substr(0, s);
			std::string v2 = v.substr(s + 1);
			return std::make_pair(boost::lexical_cast<int64_t>(v1), boost::lexical_cast<int64_t>(v2));
		} else
			return std::make_pair(boost::lexical_cast<int64_t>(v), 1);
	}
}

int main(int argc, char** argv)
{
	if(argc < 2) {
		std::cerr << "Syntax: NHMLFixup2 [<options>...] <nhml-file> <nhml-file>" << std::endl;
		std::cerr << "-s / --tvaspect : Use DAR of 4:3" << std::endl;
		std::cerr << "-w / --widescreen : Use DAR of 16:9" << std::endl;
		std::cerr << "-d <x>:<y> / --dar=<x>:<y> : Use DAR of x:y" << std::endl;
		std::cerr << "-r <x>:<y> / --resolution=<x>:<y> : Use resolution x:y for AR purposes" << std::endl;
		std::cerr << "-a <x>[/<y>] / --assume-fps=<x>[/<y>] : Use fps of <x>/<y>" << std::endl;
		std::cerr << "-t <file> / --timecodes=<file> : Use <file> for timecodes." << std::endl;
	}
	try {
		int64_t dar_x = 0;
		int64_t dar_y = 0;
		int64_t res_x = 0;
		int64_t res_y = 0;
		int64_t fps_n = 0;
		int64_t fps_d = 0;
		std::string nhml1;
		std::string nhml2;
		bool use_tc = false;
		std::string timecodes;

		for(size_t i = 1; i < argc; i++) {
			std::string arg = argv[i];
			if(arg == "--tvaspect" || arg == "-s") {
				dar_x = 4;
				dar_y = 3;
			} else if(arg == "--widescreen" || arg == "-w") {
				dar_x = 16;
				dar_y = 9;
			} else if(arg.length() >= 6 && arg.substr(0, 6) == "--dar=") {
				auto g = parse_xy(arg.substr(6));
				if(g.first <= 0 || g.second <= 0)
					throw std::runtime_error("Illegal DAR");
				dar_x = g.first;
				dar_y = g.second;
			} else if(arg == "-d") {
				if(!argv[i + 1])
					throw std::runtime_error("-d Needs an argument");
				auto g = parse_xy(argv[++i]);
				if(g.first <= 0 || g.second <= 0)
					throw std::runtime_error("Illegal DAR");
				dar_x = g.first;
				dar_y = g.second;
			} else if(arg.length() >= 13 && arg.substr(0, 13) == "--resolution=") {
				auto g = parse_xy(arg.substr(13));
				if(g.first <= 0 || g.second <= 0)
					throw std::runtime_error("Illegal resolution");
				res_x = g.first;
				res_y = g.second;
			} else if(arg == "-r") {
				if(!argv[i + 1])
					throw std::runtime_error("-r Needs an argument");
				auto g = parse_xy(argv[++i]);
				if(g.first <= 0 || g.second <= 0)
					throw std::runtime_error("Illegal resolution");
				res_x = g.first;
				res_y = g.second;
			} else if(arg.length() >= 12 && arg.substr(0, 12) == "--timecodes=") {
				timecodes = arg.substr(12);
				use_tc = true;
			} else if(arg == "-t") {
				if(!argv[i + 1])
					throw std::runtime_error("-t Needs an argument");
				timecodes = argv[++i];
				use_tc = true;
			} else if(arg.length() >= 13 && arg.substr(0, 13) == "--assume-fps=") {
				auto g = parse_xy2(arg.substr(13));
				if(g.first <= 0 || g.second <= 0)
					throw std::runtime_error("Illegal assumed fps");
				fps_n = g.first;
				fps_d = g.second;
			} else if(arg == "-a") {
				if(!argv[i + 1])
					throw std::runtime_error("-a Needs an argument");
				auto g = parse_xy2(argv[++i]);
				if(g.first <= 0 || g.second <= 0)
					throw std::runtime_error("Illegal assumed fps");
				fps_n = g.first;
				fps_d = g.second;
			} else if(arg != "" && arg[0] != '-') {
				if(nhml1 == "")
					nhml1 = arg;
				else if(nhml2 == "")
					nhml2 = arg;
				else
					throw std::runtime_error("2 NHML files neeeded");
			} else
				throw std::runtime_error("Invalid argument '" + arg + "'");
		}
		if(nhml2 == "")
			throw std::runtime_error("2 NHML files neeeded");

		if(!use_tc && (fps_n || fps_d))
			throw std::runtime_error("--assume-fps can only be used with --timecodes");
		nhml_file n1;
		nhml_file n2;
		read_nhml_file(nhml1, n1);
		read_nhml_file(nhml2, n2);
		if(use_tc) {
			auto tc = read_tc_file(timecodes);
			do_fixup(n1, n2, tc, fps_n, fps_d, dar_x, dar_y, res_x, res_y);
		} else
			do_fixup_cfr(n1, n2, dar_x, dar_y, res_x, res_y);
		write_nhml_file(nhml1 + ".tmp", n1);
		write_nhml_file(nhml2 + ".tmp", n2);
		backup_replace(nhml1 + ".tmp", nhml1, nhml1 + ".bak");
		backup_replace(nhml2 + ".tmp", nhml2, nhml2 + ".bak");
	} catch(std::exception& e) {
		std::cerr << "\n\nFatal: " << e.what() << std::endl;
		return 1;
	}
	std::cout << "All done." << std::endl;
	return 0;
}
