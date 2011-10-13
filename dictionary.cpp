#include "dictionary.hpp"
#include <stdexcept>
#include <boost/lexical_cast.hpp>
#include <sstream>

struct refstring_instance
{
	std::string s;
	bool operator<(const refstring_instance& r) const;
	bool operator==(const refstring_instance& r) const;
};

bool refstring_instance::operator<(const refstring_instance& r) const
{
	return s < r.s;
}

bool refstring_instance::operator==(const refstring_instance& r) const
{
	return s == r.s;
}

namespace
{
	std::set<refstring_instance> refstrings;

	std::string escape_xml_string(const std::string& v)
	{
		std::ostringstream ss;
		for(size_t i = 0; i < v.length(); i++) {
			if(v[i] == '\'')
				ss << "&apos;";
			else if(v[i] == '"')
				ss << "&quot;";
			else if(v[i] == '&')
				ss << "&amp;";
			else if(v[i] == '<')
				ss << "&lt;";
			else if(v[i] == '>')
				ss << "&gt;";
			else
				ss << v[i];
		}
		return ss.str();
	}
}

refstring::refstring()
{
	refstring_instance i;
	i.s = "";
	auto g = refstrings.insert(i);
	instance = &*g.first;
}

refstring::refstring(const std::string& value)
{
	refstring_instance i;
	i.s = value;
	auto g = refstrings.insert(i);
	instance = &*g.first;
}

refstring::refstring(const char* value)
{
	refstring_instance i;
	i.s = value;
	auto g = refstrings.insert(i);
	instance = &*g.first;
}

bool refstring::operator<(const refstring& r) const
{
	return *instance < *(r.instance);
}

bool refstring::operator==(const refstring& r) const
{
	return *instance == *(r.instance);
}

refstring::operator std::string() const
{
	return instance->s;
}

valuetype::type valuetype::get_type()
{
	return _type;
}

std::string valuetype::get_string()
{
	switch(_type) {
	case V_NONE:
		throw std::runtime_error("Attempted to convert none to string");
	case V_STRING:
		break;
	case V_INTEGER:
		s = boost::lexical_cast<std::string>(id.i);
		_type = V_STRING;
		break;
	case V_FLOATING:
		s = boost::lexical_cast<std::string>(id.d);
		_type = V_STRING;
		break;
	};
	return s;
}

int64_t valuetype::get_integer()
{
	switch(_type) {
	case V_NONE:
		throw std::runtime_error("Attempted to convert none to integer");
	case V_STRING:
		id.i = boost::lexical_cast<int64_t>(s);
		_type = V_INTEGER;
		break;
	case V_INTEGER:
		break;
	case V_FLOATING:
		id.i = boost::lexical_cast<int64_t>(id.d);
		_type = V_INTEGER;
		break;
	};
	return id.i;
}

double valuetype::get_floating()
{
	switch(_type) {
	case V_NONE:
		throw std::runtime_error("Attempted to convert none to floating");
	case V_STRING:
		id.d = boost::lexical_cast<double>(s);
		_type = V_FLOATING;
		break;
	case V_INTEGER:
		id.d = boost::lexical_cast<double>(id.i);
		_type = V_FLOATING;
		break;
	case V_FLOATING:
		break;
	};
	return id.d;
}

void valuetype::set_none()
{
	_type = V_NONE;
}

void valuetype::set_string(const std::string& value)
{
	s = value;
	_type = V_STRING;
}

void valuetype::set_integer(int64_t v)
{
	_type = V_INTEGER;
	id.i = v;
}

void valuetype::set_floating(double v)
{
	_type = V_FLOATING;
	id.d = v;
}

valueholder::valueholder(dictionary& _dict, refstring _field)
	: dict(_dict), field(_field)
{
}

valueholder& valueholder::operator=(const std::string& v)
{
	dict.values[field].set_string(v);
}

valueholder& valueholder::operator=(int64_t v)
{
	dict.values[field].set_integer(v);
}

valueholder& valueholder::operator=(double v)
{
	dict.values[field].set_floating(v);
}

valueholder::operator std::string()
{
	if(!dict.values.count(field))
		throw std::runtime_error("Trying to read nonexistent field '" + std::string(field) + "'");
	try {
		return dict.values[field].get_string();
	} catch(std::exception& e) {
		throw std::runtime_error("Can't read field '" + std::string(field) + "': " + e.what());
	}
}

valueholder::operator int64_t()
{
	if(!dict.values.count(field))
		throw std::runtime_error("Trying to read nonexistent field '" + std::string(field) + "'");
	try {
		return dict.values[field].get_integer();
	} catch(std::exception& e) {
		throw std::runtime_error("Can't read field '" + std::string(field) + "': " + e.what());
	}
}

valueholder::operator bool()
{
	return (dict.values.count(field) != 0);
}

valueholder::operator double()
{
	if(!dict.values.count(field))
		throw std::runtime_error("Trying to read nonexistent field '" + std::string(field) + "'");
	try {
		return dict.values[field].get_floating();
	} catch(std::exception& e) {
		throw std::runtime_error("Can't read field '" + std::string(field) + "': " + e.what());
	}
}

valuetype::type valueholder::type()
{
	if(!dict.values.count(field))
		return valuetype::V_NONE;
	return dict.values[field].get_type();
}

void valueholder::clear()
{
	dict.values.erase(field);
}

valueholder dictionary::operator[](refstring key)
{
	return valueholder(*this, key);
}

ordinal_map::ordinal_map()
{
	dirty = false;
}

void ordinal_map::insert(int64_t v)
{
	dirty = true;
	values.insert(v);
}

int64_t ordinal_map::lookup(int64_t v)
{
	if(dirty) {
		std::map<int64_t, int64_t> _ordinals;
		int64_t o = 0;
		for(auto i : values)
			_ordinals[i] = o++;
		ordinals = _ordinals;
		dirty = false;
	}
	if(!ordinals.count(v))
		throw std::runtime_error("Trying to look up nonexistent ordinal");
	return ordinals[v];
};

std::string dictionary::serialize_xml(const std::string& nodetype, bool end)
{
	std::ostringstream ss;
	ss << "<" << nodetype << " ";
	for(auto i : values)
		ss << std::string(i.first) << "=\"" << escape_xml_string(i.second.get_string()) << "\" ";
	if(end)
		ss << "/";
	ss << ">\n";
	return ss.str();
}
