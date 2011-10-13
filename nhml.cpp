#include "nhml.hpp"
#include <boost/lexical_cast.hpp>
#include <stack>
#include <cstdio>
#include "expat.h"
#include <iostream>
#include <stdexcept>
#include <string>
#include <fstream>
#include <sstream>
#include <vector>

namespace
{
	std::string convert_string(const XML_Char* str, int len)
	{
		if(sizeof(str[0]) > 1) {
			//Elements are wchar_t's!
			std::ostringstream tmp;
			for(size_t i = 0; i < len; i++) {
				unsigned char x1;
				unsigned char x2;
				unsigned char x3;
				unsigned char x4;
				if(str[i] < 0x80) {
					x1 = str[i];
					tmp << x1;
				} else if(str[i] < 0x800) {
					x1 = 0xC0 | ((str[i] >> 6) & 0x1F);
					x2 = 0x80 | (str[i] & 0x3F);
					tmp << x1 << x2;
				} else if(str[i] < 0x10000) {
					x1 = 0xE0 | ((str[i] >> 12) & 0x0F);
					x2 = 0x80 | ((str[i] >> 6) & 0x3F);
					x2 = 0x80 | (str[i] & 0x3F);
					tmp << x1 << x2 << x3;
				} else if(str[i] < 0x200000) {
					x1 = 0xF0 | ((str[i] >> 18) & 0x08);
					x1 = 0x80 | ((str[i] >> 12) & 0x3F);
					x2 = 0x80 | ((str[i] >> 6) & 0x3F);
					x2 = 0x80 | (str[i] & 0x3F);
					tmp << x1 << x2 << x3 << x4;
				}
			}
			return tmp.str();
		} else {
			return std::string(str, str + len);
		}
	}

	std::string convert_string(const XML_Char* str)
	{
		int len = 0;
		for(len = 0; str[len]; len++);
		return convert_string(str, len);
	}

	void fill_directory(dictionary& d, const XML_Char** attrs)
	{
		for(size_t i = 0; attrs[i]; i += 2) {
			std::string name = convert_string(attrs[i + 0]);
			std::string value = convert_string(attrs[i + 1]);
			refstring _name(name);
			d[_name] = value;
		}
	}

	void handle_element_start(void* user, const XML_Char* name, const XML_Char** attrs)
	{
		nhml_file& f = *reinterpret_cast<nhml_file*>(user);
		std::string _name = convert_string(name);
		if(_name == "NHNTStream") {
			fill_directory(f.header, attrs);
		} else if(_name == "NHNTSample") {
			f.samples.push_back(dictionary());
			fill_directory(f.samples[f.samples.size() - 1], attrs);
		} else
			std::cerr << "Warning: Unrecognized tag '" << _name << "'." << std::endl;
	}
	
	void handle_element_end(void* user, const XML_Char* name)
	{
		//We don't care about ending elements.
	}

	void handle_text(void *userData, const XML_Char *s, int len)
	{
		//No-op. We don't expect text.
	}
}

void read_nhml_file(std::istream& str, nhml_file& contents)
{
	XML_Parser parser;
	parser = XML_ParserCreate(NULL);
	if(!parser)
		throw std::runtime_error("Can't create XML parser!");
	XML_SetUserData(parser, &contents);
	XML_SetElementHandler(parser, handle_element_start, handle_element_end);
	XML_SetCharacterDataHandler(parser, handle_text);
	char buffer[8192];
	while(str) {
		str.read(buffer, 8192);
		size_t r = str.gcount();
		if(!XML_Parse(parser, buffer, r, 0)) {
			XML_ParserFree(parser);
			throw std::runtime_error("XML parse error!");
		}
	}
	if(!XML_Parse(parser, buffer, 0, 1)) {
		XML_ParserFree(parser);
		throw std::runtime_error("XML parse error (end of input)!");
	}
	XML_ParserFree(parser);
}

void write_nhml_file(std::ostream& str, nhml_file& contents)
{
	str << "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>" << std::endl;
	str << contents.header.serialize_xml("NHNTStream", false);
	for(auto i : contents.samples)
		str << i.serialize_xml("NHNTSample", true);
	str << "</NHNTStream>" << std::endl;
}

void read_nhml_file(const std::string& str, nhml_file& contents)
{
	std::cout << "Loading NHML '" << str << "'..." << std::flush;
	std::ifstream _str(str.c_str());
	if(!_str)
		throw std::runtime_error("Can't open '" + str + "'.");
	read_nhml_file(_str, contents);
	std::cout << "Done (" << contents.samples.size() << " samples)." << std::endl;
}

void write_nhml_file(const std::string& str, nhml_file& contents)
{
	std::cout << "Saving NHML '" << str << "'..." << std::flush;
	std::ofstream _str(str.c_str());
	if(!_str)
		throw std::runtime_error("Can't open '" + str + "'.");
	write_nhml_file(_str, contents);
	std::cout << "Done (" << contents.samples.size() << " samples)." << std::endl;
}

void backup_replace(const std::string& newf, const std::string& curf, const std::string& oldf)
{
	std::cout << "Renaming '" << curf << "' to '" << oldf << "'..." << std::flush;
	if(rename(curf.c_str(), oldf.c_str()) < 0) {
		if(remove(oldf.c_str()) < 0)
			throw std::runtime_error("Can't rename '" + curf + "' to '" + oldf + "'");
		if(rename(curf.c_str(), oldf.c_str()) < 0)
			throw std::runtime_error("Can't rename '" + curf + "' to '" + oldf + "'");
	}
	std::cout << "Done" << std::endl;
	std::cout << "Renaming '" << newf << "' to '" << curf << "'..." << std::flush;
	if(rename(newf.c_str(), curf.c_str()) < 0)
		throw std::runtime_error("Can't rename '" + newf + "' to '" + curf + "'");
	std::cout << "Done" << std::endl;
}

std::vector<double> read_tc_file(const std::string& filename)
{
	std::vector<double> ret;
	std::cout << "Loading TC file '" << filename << "'..." << std::flush;
	std::ifstream _str(filename.c_str());
	if(!_str)
		throw std::runtime_error("Can't open '" + filename + "'.");
	bool first = true;
	std::string line;
	while(std::getline(_str, line)) {
		if(line.length() > 0 && line[line.length() - 1] == '\r')
			line = line.substr(0, line.length() - 1);
		if(first && line != "# timecode format v2")
			throw std::runtime_error("Bad timecode file magic");
		if(first) {
			first = false;
			continue;
		}
		ret.push_back(boost::lexical_cast<double>(line));
	}
	if(first)
		throw std::runtime_error("TC file can't be empty");
	std::cout << "Done (" << ret.size() << " timecodes)." << std::endl;
	return ret;
}
