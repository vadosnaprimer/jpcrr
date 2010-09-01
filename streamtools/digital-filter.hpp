#ifndef _digital_filter__hpp__included__
#define _digital_filter__hpp__included__

#include <iostream>
#include <stdint.h>
#include <vector>
#include <map>
#include <list>

#define AMPLIFIER_GAIN_LINEAR 0
#define AMPLIFIER_GAIN_DB 1
#define AMPLIFIER_ATTENUATION_LINEAR 2
#define AMPLIFIER_ATTENUATION_DB 3

template<typename T>
struct field_characteristics
{
	typedef field_characteristics<T> self;
	static const T additive_identity;
	static const T multiplicative_identity;
	static const bool limited;
	static const T clip_min;
	static const T clip_max;
};

template<typename T>
struct npair
{
	typedef typename field_characteristics<T>::self characteristics;
	typedef T base_type;

	npair()
	{
		x = field_characteristics<T>::additive_identity;
		y = field_characteristics<T>::additive_identity;
	}

	npair(T n)
	{
		x = n;
		y = n;
	}

	npair(T _x, T _y)
	{
		x = _x;
		y = _y;
	}

	~npair()
	{
	}

	static npair<T> zero()
	{
		return npair(field_characteristics<T>::additive_identity);
	}

	static npair<T> one()
	{
		return npair(field_characteristics<T>::multiplicative_identity);
	}

	T get_x() const
	{
		return x;
	}

	T get_y() const
	{
		return y;
	}

	npair<T> operator+(npair<T> another)
	{
		return npair(x + another.x, y + another.y);
	}

	npair<T> operator-(npair<T> another)
	{
		return npair(x - another.x, y - another.y);
	}

	npair<T> operator-()
	{
		return npair(-x, -y);
	}

	npair<T> operator*(npair<T> another)
	{
		return npair(x * another.x, y * another.y);
	}

	npair<T> operator*(base_type another)
	{
		return npair(x * another, y * another);
	}

	npair<T> operator/(npair<T> another)
	{
		return npair(x / another.x, y / another.y);
	}

private:
	T x;
	T y;
};

template<typename T>
std::ostream& operator<< (std::ostream& os, const npair<T>& pair)
{
	return os << "(" << pair.get_x() << "," << pair.get_y() << ")";
}

template<typename T>
npair<T> operator*(T another, npair<T> _this)
{
	return npair<T>(another * _this.get_x(), another * _this.get_y());
}


typedef npair<double> filter_number_t;
typedef npair<short> sample_number_t;

struct filter
{
	virtual ~filter();
	virtual filter_number_t operator()(filter_number_t input) = 0;
};


//a0*y0+a1*y1+...+an*yn = b0*x0+b1*x1+...+bm*xm
struct digital_filter : public filter
{
	digital_filter(const std::vector<filter_number_t>& num);
	digital_filter(const std::vector<filter_number_t>& num, const std::vector<filter_number_t>& denum);
	~digital_filter();
	filter_number_t operator()(filter_number_t input);
private:
	void init_numerator(const std::vector<filter_number_t>& num);
	void init_denumerator(const std::vector<filter_number_t>& num);
	std::vector<filter_number_t> filter_numerator;
	std::vector<filter_number_t> filter_denumerator;
	std::vector<filter_number_t> old_input;
	std::vector<filter_number_t> old_output;
	uint64_t sample_count;
};

struct amplifier : public filter
{
	amplifier(filter_number_t::base_type amount, int type);
	~amplifier();
	filter_number_t operator()(filter_number_t input);
private:
	filter_number_t::base_type linear_gain;
};

struct silencer : public filter
{
	silencer();
	~silencer();
	filter_number_t operator()(filter_number_t input);
};

struct composite_filter : public filter
{
	composite_filter();
	~composite_filter();
	//Filt is deleted when composite_filter is destroyed.
	void add(filter& filt);
	filter_number_t operator()(filter_number_t input);
private:
	std::list<filter*> filters;
};

struct trivial_filter : public filter
{
	trivial_filter();
	~trivial_filter();
	filter_number_t operator()(filter_number_t input);
};

extern trivial_filter trivial;

sample_number_t downconvert(filter_number_t input, uint64_t& clipped);
filter_number_t upconvert(sample_number_t input);

struct mixer
{
	mixer();
	~mixer();

	//These release filter on destroy (except for trivial filter).
	void set_input_filter(uint32_t permchan, filter& f);
	void set_output_filter(filter& f);

	void set_channel_volume(uint32_t permchan, filter_number_t volume);
	void send_sample(uint32_t permchan, sample_number_t sample);
	sample_number_t recv_sample();

	uint64_t get_clip_count();
private:
	filter& get_input_filter(uint32_t permchan);
	filter_number_t get_input_volume(uint32_t permchan);
	filter_number_t accumulator;
	std::map<uint32_t, filter_number_t> input_volumes;
	std::map<uint32_t, filter*> input_filters;
	filter* output_filter;
	uint64_t clip_count;
};

#endif
