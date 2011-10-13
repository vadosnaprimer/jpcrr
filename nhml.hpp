#ifndef _nhml__hpp__included__
#define _nhml__hpp__included__

#include "dictionary.hpp"
#include <vector>

struct nhml_file
{
	dictionary header;
	std::vector<dictionary> samples;
};

void read_nhml_file(std::istream& str, nhml_file& contents);
void write_nhml_file(std::ostream& str, nhml_file& contents);
void read_nhml_file(const std::string& str, nhml_file& contents);
void write_nhml_file(const std::string& str, nhml_file& contents);
void backup_replace(const std::string& newf, const std::string& curf, const std::string& oldf);
std::vector<double> read_tc_file(const std::string& filename);

#endif
