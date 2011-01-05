#ifndef _rescalers__public__hpp__included__
#define _rescalers__public__hpp__included__

#include <stdint.h>
#include <string>
#include <list>
#include <map>

class rescaler
{
public:
	virtual ~rescaler();
	virtual void operator()(uint8_t* target, uint32_t twidth, uint32_t theight,
		const uint8_t* source, uint32_t swidth, uint32_t sheight) = 0;
	static rescaler& make(const std::string& type);
};

struct parsed_scaler
{
	bool is_special;
	uint32_t swidth;	//Meaningful only if is_special is set.
	uint32_t sheight;	//Meaningful only if is_special is set.
	rescaler* use_rescaler;
};

std::string get_rescaler_string();
std::list<std::string> get_rescaler_list();
rescaler& composite_rescaler(rescaler& s1, uint32_t iwidth, uint32_t iheight, rescaler& s2);
struct parsed_scaler parse_rescaler_expression(const std::string& expr);
rescaler& get_default_rescaler();

class rescaler_group
{
public:
	rescaler_group(rescaler& _default_rescaler);
	~rescaler_group();
	void set_default_rescaler(rescaler& _default_rescaler);
	void set_special_rescaler(uint32_t width, uint32_t height, rescaler& rescaler);
	void operator()(uint8_t* target, uint32_t twidth, uint32_t theight,
		const uint8_t* source, uint32_t swidth, uint32_t sheight);
private:
	rescaler* default_rescaler;
	std::map<std::pair<uint32_t, uint32_t>, rescaler*> special_rescalers;
};

#endif
