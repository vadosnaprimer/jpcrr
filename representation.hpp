#ifndef _represnetation__hpp__included__
#define _represnetation__hpp__included__

#include "nhml.hpp"

void nhml_to_internal(struct nhml_file& nhml, const std::string& kind);
void nhml_to_external(struct nhml_file& nhml, const std::string& kind);


#endif