#include "representation.hpp"
#include <stdexcept>
#include <iostream>

void nhml_to_internal(struct nhml_file& nhml, const std::string& kind)
{
	std::cout << "Changing " << kind << " NHML to internal representation..." << std::flush;
	int64_t default_dtsinc = 0;
	int64_t prev_dts = 0;
	refstring DTS_increment("DTS_increment");
	refstring DTS("DTS");
	refstring CTS("CTS");
	refstring CTSOffset("CTSOffset");
	if(!nhml.header["timeScale"]) {
		if(!nhml.header["sampleRate"])
			nhml.header["timeScale"] = static_cast<int64_t>(nhml.header["sampleRate"]);
		else
			nhml.header["timeScale"] = "1000";
	}

	if(nhml.header[DTS_increment])
		default_dtsinc = nhml.header[DTS_increment];
	for(auto& i : nhml.samples) {
		if(!i[DTS])
			i[DTS] = (prev_dts + default_dtsinc);
		prev_dts = static_cast<int64_t>(i[DTS]);
		if(i[CTSOffset]) {
			if(static_cast<int64_t>(i[CTSOffset]) < 0) {
				//Try to fix this.
				int64_t x = i[CTSOffset];
				x += 4294967296LL;
				i[CTSOffset] = x;
				std::cout << "Warning: Non-causal file, trying to fix the error..." << std::endl;
			}
			i[CTS] = static_cast<int64_t>(i[DTS]) + static_cast<int64_t>(i[CTSOffset]);
		} else
			i[CTS] = static_cast<int64_t>(i[DTS]);
		i[CTSOffset].clear();
	}
	std::cout << "Done." << std::endl;
}

void nhml_to_external(struct nhml_file& nhml, const std::string& kind)
{
	std::cout << "Changing " << kind << " NHML to external representation..." << std::flush;
	refstring DTS("DTS");
	refstring CTS("CTS");
	refstring CTSOffset("CTSOffset");
	for(auto& i : nhml.samples) {
		i[CTSOffset] = static_cast<int64_t>(i[CTS]) - static_cast<int64_t>(i[DTS]);
		i[CTS].clear();
	}
	std::cout << "Done." << std::endl;
}
