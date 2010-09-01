#include "digital-filter.hpp"
#include <cmath>
#include <iostream>
#include <stdexcept>

template<> const short field_characteristics<short>::additive_identity = 0;
template<> const short field_characteristics<short>::multiplicative_identity = 1;
template<> const bool field_characteristics<short>::limited = true;
template<> const short field_characteristics<short>::clip_min = -32768;
template<> const short field_characteristics<short>::clip_max = 32767;

template<> const int64_t field_characteristics<int64_t>::additive_identity = 0;
template<> const int64_t field_characteristics<int64_t>::multiplicative_identity = 1;
template<> const bool field_characteristics<int64_t>::limited = true;
//Not exactly correct, but...
template<> const int64_t field_characteristics<int64_t>::clip_min = -9000000000000000000LL;
template<> const int64_t field_characteristics<int64_t>::clip_max = 9000000000000000000LL;

template<> const double field_characteristics<double>::additive_identity = 0;
template<> const double field_characteristics<double>::multiplicative_identity = 1;
template<> const bool field_characteristics<double>::limited = false;
template<> const double field_characteristics<double>::clip_min = 0;
template<> const double field_characteristics<double>::clip_max = 0;

filter::~filter()
{
}


silencer::silencer()
{
}

silencer::~silencer()
{
}

filter_number_t silencer::operator()(filter_number_t input)
{
	return 0;
}


amplifier::amplifier(filter_number_t::base_type amount, int type)
{
	switch(type) {
	case AMPLIFIER_GAIN_LINEAR:
		linear_gain = amount;
		break;
	case AMPLIFIER_GAIN_DB:
		linear_gain = pow(10, amount / 10);
		break;
	case AMPLIFIER_ATTENUATION_LINEAR:
		linear_gain = 1 / amount;
		break;
	case AMPLIFIER_ATTENUATION_DB:
		linear_gain = 1 / pow(10, amount / 10);
		break;
        default:
		throw std::runtime_error("Bad amplifier type");
	}
}

amplifier::~amplifier()
{
}

filter_number_t amplifier::operator()(filter_number_t input)
{
	return linear_gain * input;
}

digital_filter::~digital_filter()
{
}

digital_filter::digital_filter(const std::vector<filter_number_t>& num)
{
	init_numerator(num);
	filter_denumerator.push_back(1);
	old_output.push_back(0);
}

digital_filter::digital_filter(const std::vector<filter_number_t>& num, const std::vector<filter_number_t>& denum)
{
	init_numerator(num);
	init_denumerator(denum);
}

void digital_filter::init_numerator(const std::vector<filter_number_t>& num)
{
	filter_numerator = num;
	for(size_t i = 0; i < num.size(); i++)
		old_input.push_back(0);
}

void digital_filter::init_denumerator(const std::vector<filter_number_t>& denum)
{
	filter_denumerator = denum;
	for(size_t i = 0; i < denum.size(); i++)
		old_output.push_back(0);
}

inline size_t decmod(size_t num, size_t mod)
{
	return ((num == 0) ? mod : num) - 1;
}

filter_number_t digital_filter::operator()(filter_number_t input)
{
	//Save input.
	uint64_t currsamples = sample_count;
	size_t input_index = currsamples % old_input.size();
	size_t output_index = currsamples % old_output.size();
	filter_number_t output;
	filter_number_t partial_old_input = 0;
	filter_number_t partial_old_output = 0;
	size_t i, j;

	old_input[input_index] = input;

	//Calculate partial old input.
	i = input_index;
	j = 0;
	do {
		partial_old_input = partial_old_input + filter_numerator[j] * old_input[i];
		i = decmod(i, old_input.size());
		j++;
	} while(i != input_index);


	i = decmod(output_index, old_output.size());
	j = 1;
	while(i != output_index) {
		partial_old_output = filter_denumerator[j] * old_output[i];
		i = decmod(i, old_output.size());
		j++;
	}

	//a0*y0+a1*y1+...+an*yn = b0*x0+b1*x1+...+bm*xm
	//This can be simplfied to.
	//a0*y0+poo = poi = > a0*y0 = poi - poo => y0 = (poi - poo) / a0.
	output = (partial_old_input - partial_old_output) / filter_denumerator[0];

	old_output[output_index] = output;
	sample_count++;
	return output;
}

composite_filter::composite_filter()
{
}

composite_filter::~composite_filter()
{
	for(std::list<filter*>::iterator i = filters.begin(); i != filters.end(); i++)
		delete *i;
}

void composite_filter::add(filter& filt)
{
	filters.push_back(&filt);
}

filter_number_t composite_filter::operator()(filter_number_t input)
{
	for(std::list<filter*>::iterator i = filters.begin(); i != filters.end(); i++)
		input = (**i)(input);
	return input;
}

trivial_filter::trivial_filter()
{
}

trivial_filter::~trivial_filter()
{
}

filter_number_t trivial_filter::operator()(filter_number_t input)
{
	return input;
}

sample_number_t downconvert(filter_number_t input, uint64_t& clipped)
{
	sample_number_t::base_type x, y;

	if(sample_number_t::characteristics::limited) {
		if(input.get_x() > sample_number_t::characteristics::clip_max) {
			clipped++;
			x = sample_number_t::characteristics::clip_max;
		} else if(input.get_x() < sample_number_t::characteristics::clip_min) {
			clipped++;
			x = sample_number_t::characteristics::clip_min;
		} else
			x = (sample_number_t::base_type)input.get_x();
	} else
		x = (sample_number_t::base_type)input.get_x();

	if(sample_number_t::characteristics::limited) {
		if(input.get_y() > sample_number_t::characteristics::clip_max) {
			clipped++;
			y = sample_number_t::characteristics::clip_max;
		} else if(input.get_y() < sample_number_t::characteristics::clip_min) {
			clipped++;
			y = sample_number_t::characteristics::clip_min;
		} else
			y = (sample_number_t::base_type)input.get_y();
	} else
		y = (sample_number_t::base_type)input.get_y();
	return sample_number_t(x, y);
}

filter_number_t upconvert(sample_number_t sample)
{
	return filter_number_t(sample.get_x(), sample.get_y());
}

trivial_filter trivial;


mixer::mixer()
{
	accumulator = 0;
	output_filter = &trivial;
	clip_count = 0;
}

mixer::~mixer()
{
	for(std::map<uint32_t, filter*>::iterator i = input_filters.begin(); i != input_filters.end(); ++i)
		if(i->second != &trivial)
			delete i->second;
	if(output_filter != &trivial)
		delete output_filter;
}

filter& mixer::get_input_filter(uint32_t permchan)
{
	if(!input_filters.count(permchan))
		return trivial;
	return *input_filters[permchan];
}


filter_number_t mixer::get_input_volume(uint32_t permchan)
{
	if(!input_volumes.count(permchan))
		return filter_number_t::one();
	return input_volumes[permchan];
}

void mixer::set_input_filter(uint32_t permchan, filter& f)
{
	if(input_filters.count(permchan) && input_filters[permchan] != &trivial)
		delete input_filters[permchan];
	input_filters[permchan] = &f;
}

void mixer::set_output_filter(filter& f)
{
	if(output_filter != &trivial)
		delete output_filter;
	output_filter = &f;
}

uint64_t mixer::get_clip_count()
{
	return clip_count;
}

void mixer::set_channel_volume(uint32_t permchan, filter_number_t volume)
{
	input_volumes[permchan] = volume;
}


void mixer::send_sample(uint32_t permchan, sample_number_t sample)
{
	filter_number_t _sample = upconvert(sample) * get_input_volume(permchan);
	accumulator = accumulator + get_input_filter(permchan)(_sample);
}

sample_number_t mixer::recv_sample()
{
	sample_number_t out = downconvert((*output_filter)(accumulator), clip_count);
	accumulator = filter_number_t::zero();
	return out;
}
