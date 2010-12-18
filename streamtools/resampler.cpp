#include "resampler.hpp"
#include <iostream>
#include <sstream>
#include <stdexcept>
#include "misc.hpp"
#include "opl.h"

uint64_t next_sample_time(uint64_t time, uint32_t rate)
{
	uint64_t seconds = time / 1000000000;
	uint64_t subseconds = time % 1000000000;
	uint64_t subsamples = (rate * subseconds + 999999999) / 1000000000;
	subseconds = 1000000000 * subsamples / rate;
	return seconds * 1000000000 + subseconds;
}

npair<int64_t> widen(npair<short> x)
{
	return npair<int64_t>(x.get_x(), x.get_y());
}

sample_number_t narrow(npair<int64_t> x)
{
	return sample_number_t((sample_number_t::base_type)(x.get_x()), (sample_number_t::base_type)(x.get_y()));
}

resampler::~resampler()
{
}

resampler_pcm::resampler_pcm(uint32_t rate)
{
	written = false;
	output_rate = rate;
}

sample_number_t resampler_pcm::nextsample()
{
	if(!written)
		return sample_number_t(0, 0);

	uint64_t now = next_sample_time(last_read_time + 1, output_rate);
	if(last_write_time <= last_read_time) {
		last_read_time = now;
		last_write_time = now;
		accumulator = npair<int64_t>::zero();
		return current_levels;
	}

	int64_t cover = now - last_read_time;
	last_read_time = now;

	if(!cover) {
		accumulator = npair<int64_t>::zero();
		return current_levels;
	}

	uint64_t wcover = now - last_write_time;
	accumulator = accumulator + widen(current_levels) * wcover;
	last_write_time = now;
	npair<int64_t> avg = accumulator / cover;
	accumulator = npair<int64_t>::zero();
	return narrow(avg);
}

void resampler_pcm::sendpacket(struct packet& p)
{
	if(p.rp_major != 1 || p.rp_minor != 1 || p.rp_payload.size() < 4)
		return;		//Wrong type.
	int64_t cover = 0;
	if(written)
		cover = p.rp_timestamp - last_write_time;
	else
		last_read_time = p.rp_timestamp;
	written = true;
	accumulator = accumulator + cover * widen(current_levels);
	unsigned char* pl = &p.rp_payload[0];
	current_levels = npair<short>((unsigned short)pl[0] * 256 + (unsigned short)pl[1],
		(unsigned short)pl[2] * 256 + (unsigned short)pl[3]);
	last_write_time = p.rp_timestamp;
}

resampler_fm::resampler_fm(uint32_t rate)
{
	adlib_init(rate);
}

sample_number_t resampler_fm::nextsample()
{
	short samples[2];
	adlib_getsample(samples, 1);
	return sample_number_t(samples[0], samples[1]);
}

void resampler_fm::sendpacket(struct packet& p)
{
	if(p.rp_major != 2)
		return;		//Wrong type.
	if(p.rp_minor == 0 || p.rp_minor > 3)
		return;		//Wrong type.
	if((p.rp_minor == 1 || p.rp_minor == 2) && p.rp_payload.size() < 2)
		return;		//Malformed.

	if(p.rp_minor == 3) {
		//RESET.
		for(int i = 0; i < 512; i++)
			adlib_write(i, 0);
		return;
	}

	unsigned short reg = p.rp_payload[0];
	unsigned char val = p.rp_payload[1];
	if(p.rp_minor == 2)
		reg += 256;	//Second set.
	adlib_write(reg, val);
}


packet_demux::packet_demux(mixer& mix, uint32_t rate)
	: use_mixer(mix)
{
	used_rate = rate;
	output_filter = new composite_filter();
	use_mixer.set_output_filter(*output_filter);
}

packet_demux::~packet_demux()
{
	for(std::map<uint32_t, resampler*>::iterator i = resamplers.begin(); i != resamplers.end(); ++i)
		delete i->second;
	delete &use_mixer;
}

sample_number_t packet_demux::nextsample()
{
	for(std::map<uint32_t, resampler*>::iterator i = resamplers.begin(); i != resamplers.end(); ++i)
		use_mixer.send_sample(i->first, i->second->nextsample());
	return use_mixer.recv_sample();
}

void packet_demux::do_volume_change(struct packet& p)
{
	filter_number_t::base_type lv, rv;
	uint32_t ln = 0, ld = 0, rn = 0, rd = 0;
	if(p.rp_payload.size() < 16)
		return;		//Malformed.

	ln |= (uint32_t)p.rp_payload[0] << 24;
	ln |= (uint32_t)p.rp_payload[1] << 16;
	ln |= (uint32_t)p.rp_payload[2] << 8;
	ln |= (uint32_t)p.rp_payload[3];
	ld |= (uint32_t)p.rp_payload[4] << 24;
	ld |= (uint32_t)p.rp_payload[5] << 16;
	ld |= (uint32_t)p.rp_payload[6] << 8;
	ld |= (uint32_t)p.rp_payload[7];
	rn |= (uint32_t)p.rp_payload[8] << 24;
	rn |= (uint32_t)p.rp_payload[9] << 16;
	rn |= (uint32_t)p.rp_payload[10] << 8;
	rn |= (uint32_t)p.rp_payload[11];
	rd |= (uint32_t)p.rp_payload[12] << 24;
	rd |= (uint32_t)p.rp_payload[13] << 16;
	rd |= (uint32_t)p.rp_payload[14] << 8;
	rd |= (uint32_t)p.rp_payload[15];

	if(!ld || !rd)
		return;		//Malformed.

	lv = (filter_number_t::base_type)ln / ld;
	rv = (filter_number_t::base_type)rn / rd;
	use_mixer.set_channel_volume(p.rp_channel_perm, filter_number_t(lv, rv));
}

void packet_demux::sendpacket(struct packet& p)
{
	if(p.rp_major != 1 && p.rp_major != 2 && p.rp_major != 6)
		return;		//Not interested.
	if(p.rp_minor == 0 && p.rp_major != 6) {
		do_volume_change(p);
		return;		//Volume change.
	}
	if(!resamplers.count(p.rp_channel_perm)) {
		std::cerr << "Create channel of type " << p.rp_major << "." << std::endl;
		if(p.rp_major == 1)
			resamplers[p.rp_channel_perm] = new resampler_pcm(used_rate);
		else if(p.rp_major == 2)
			resamplers[p.rp_channel_perm] = new resampler_fm(used_rate);
		if(input_filters.count(p.rp_channel_name))
			use_mixer.set_input_filter(p.rp_channel_perm, *input_filters[p.rp_channel_name]);
	}
	resamplers[p.rp_channel_perm]->sendpacket(p);
}

std::vector<filter_number_t> process_filter(std::string spec)
{
	std::vector<filter_number_t> f;
	while(1) {
		if(spec == "")
			throw std::runtime_error("Bad filter specification");
		char* x;
		const char* o = spec.c_str();
		double val = strtod(o, &x);
		if(*x != ',' && *x)
			throw std::runtime_error("Bad filter specification");
		f.push_back(filter_number_t(val, val));
		if(!*x)
			break;
		spec = spec.substr(x + 1 - o);
	}
	return f;
}

void packet_demux::sendoption(const std::string& option)
{
	std::string _option = option;
	std::string optionname;
	bool on_output = false;
	std::string optionchan;
	std::string optionvalue;
	struct filter* f = NULL;
	if(option == "silence") {
		on_output = true;
		f = new silencer();
	} else if(isstringprefix(option, "silence=")) {
		optionchan = settingvalue(option);
		f = new silencer();
	} else {
		optionvalue = settingvalue(option);
		size_t spos = optionvalue.find_first_of(":");
		if(spos > optionvalue.length())
			on_output = true;
		else {
			optionchan = optionvalue.substr(0, spos);
			optionvalue = optionvalue.substr(spos + 1);
		}
		if(isstringprefix(option, "gain=")) {
			char* x;
			double gdb = strtod(optionvalue.c_str(), &x);
			if(*x)
				throw std::runtime_error("Bad value");
			f = new amplifier(gdb, AMPLIFIER_GAIN_DB);
		} else if(isstringprefix(option, "attenuate=")) {
			char* x;
			double gdb = strtod(optionvalue.c_str(), &x);
			if(*x)
				throw std::runtime_error("Bad value");
			f = new amplifier(gdb, AMPLIFIER_ATTENUATION_DB);
		} else if(isstringprefix(option, "filter=")) {
			size_t spos = optionvalue.find_first_of(";");
			if(spos > optionvalue.length())
				f = new digital_filter(process_filter(optionvalue));
			else
				f = new digital_filter(process_filter(optionvalue.substr(0, spos)),
					process_filter(optionvalue.substr(spos + 1)));
		} else
			throw std::runtime_error("Unknown audio processing option");
	}

	if(on_output) {
		output_filter->add(*f);
	} else {
		if(!input_filters.count(optionchan))
			input_filters[optionchan] = new composite_filter();
		input_filters[optionchan]->add(*f);
	}
}

	composite_filter* output_filter;
	std::map<std::string, composite_filter*> input_filters;

/*

--audio-fir=[<channel>:]<a0>,<a1>,...
		Perform FIR filitering (y0 = a0*x0 + a1*x1 + ... + an*xn).
--audio-iir=[<channel>:]<a0>,<a1>,...;<b0>,<b1>,...
		Perform FIR filitering (b0*y0 + b1*y1 + ... bm*ym = a0*x0 + a1*x1 + ... + an*xn).
--audio-attenuate=[<channel>:]<amount>
		Attenuate sound by <amount> decibels.
--audio-gain=[<channel>:]<amount>
		Amplify sound by <amount> decibels.
*/

void print_audio_resampler_help(const std::string& prefix)
{
	std::cout << prefix << "filter=[<channel>:]<a0>,<a1>,..." << std::endl;
	std::cout << "\tPreform FIR filtering (y0 = a0*x0 + a1*x1 + ... + an*xn)." << std::endl;
	std::cout << prefix << "filter=[<channel>:]<a0>,<a1>,...;<b0>,<b1>,..." << std::endl;
	std::cout << "\tPreform IIR filtering (b0*y0 + b1*y1 + ... bm*ym = a0*x0 + a1*x1 + ... + an*xn)." << std::endl;
	std::cout << prefix << "gain=[<channel>:]<decibels>" << std::endl;
	std::cout << "\tAmplify signal by <decibels> dB." << std::endl;
	std::cout << prefix << "attenuate=[<channel>:]<decibels>" << std::endl;
	std::cout << "\tAttenuate signal by <decibels> dB." << std::endl;
	std::cout << prefix << "silence" << std::endl;
	std::cout << "\tCompletely silence audio output." << std::endl;
	std::cout << prefix << "silence=<channel>" << std::endl;
	std::cout << "\tCompletely silence audio output on channel." << std::endl;
}

void process_audio_resampler_options(packet_demux& d, const std::string& prefix, int argc, char** argv)
{
	for(int i = 1; i < argc; i++) {
		std::string arg = argv[i];
		if(arg == "--")
			break;
		if(!isstringprefix(arg, prefix))
			continue;
		try {
			d.sendoption(arg.substr(prefix.length()));
		} catch(std::exception& e) {
			std::stringstream str;
			str << "Error processing option '" << arg << "': " << e.what();
			throw std::runtime_error(str.str());
		}
	}
}
