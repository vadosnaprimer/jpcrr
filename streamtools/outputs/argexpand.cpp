#include "outputs/argexpand.hpp"
#include <sstream>

std::string expand_arguments_common(std::string opts, std::string commaexpand, std::string equalsexpand)
{
	bool insert = true;
	bool first = true;
	std::ostringstream ret;
	for(size_t i = 0; i < opts.length(); i++) {
		if(insert) {
			if(first)
				ret << commaexpand;
			else
				ret << " " << commaexpand;
		}
		first = false;
		insert = false;
		switch(opts[i]) {
		case ',':
			insert = true;
			break;
		case '=':
			ret << equalsexpand;
			break;
		default:
			ret << opts[i];
		};
	}
	ret << " ";
	return ret.str();
}
