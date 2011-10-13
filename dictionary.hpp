#ifndef _dictionary__hpp__included__
#define _dictionary__hpp__included__

#include <cstdint>
#include <string>
#include <map>
#include <set>

struct refstring_instance;

class refstring
{
public:
	refstring();
	refstring(const std::string& value);
	refstring(const char* str);
	bool operator<(const refstring& r) const;
	bool operator==(const refstring& r) const;
	operator std::string() const;
private:
	const refstring_instance* instance;
};

class valuetype
{
public:
	enum type
	{
		V_NONE,
		V_STRING,
		V_INTEGER,
		V_FLOATING
	};
	type get_type();
	std::string get_string();
	int64_t get_integer();
	double get_floating();
	void set_none();
	void set_string(const std::string& value);
	void set_integer(int64_t v);
	void set_floating(double v);
private:
	type _type;
	std::string s;
	union iord {
		int64_t i;
		double d;
	} id;
};

class dictionary;

class valueholder
{
public:
	valueholder(dictionary& _dict, refstring _field);
	valueholder& operator=(const std::string& v);
	valueholder& operator=(int64_t v);
	valueholder& operator=(double v);
	operator std::string();
	operator int64_t();
	operator double();
	operator bool();
	valuetype::type type();
	void clear();
private:
	dictionary& dict;
	refstring field;
};

class dictionary
{
public:
	friend class valueholder;
	valueholder operator[](refstring key);
	std::string serialize_xml(const std::string& nodetype, bool end);
private:
	std::map<refstring, valuetype> values;
};

class ordinal_map
{
public:
	ordinal_map();
	void insert(int64_t v);
	int64_t lookup(int64_t v);
private:
	bool dirty;
	std::set<int64_t> values;
	std::map<int64_t, int64_t> ordinals;
};

#endif
