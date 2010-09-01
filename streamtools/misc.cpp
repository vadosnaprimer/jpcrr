#include "misc.hpp"
#include <stdexcept>

bool isstringprefix(const std::string& full, const std::string& prefix)
{
	if(prefix.length() > full.length())
		return false;
	for(size_t i = 0; i < prefix.length(); i++)
		if(full[i] != prefix[i])
			return false;
	return true;
}

std::string settingvalue(const std::string& setting)
{
	size_t x;
	std::string _setting = setting;
	x = _setting.find_first_of("=");
	if(x > _setting.length())
		throw std::runtime_error("Invalid setting syntax");
	else
		return _setting.substr(x + 1);
}
