#ifndef _resampler__hpp__included__
#define _resampler__hpp__included__

#include "digital-filter.hpp"
#include "newpacket.hpp"

uint64_t next_sample_time(uint64_t time, uint32_t rate);

struct resampler
{
	virtual ~resampler();
	virtual sample_number_t nextsample() = 0;
	virtual void sendpacket(struct packet& p) = 0;
};

struct resampler_pcm : public resampler
{
	resampler_pcm(uint32_t rate);
	sample_number_t nextsample();
	void sendpacket(struct packet& p);
private:
	bool written;
	uint32_t output_rate;
	uint64_t last_read_time;
	uint64_t last_write_time;
	npair<int64_t> accumulator;
	npair<short> current_levels;
};

struct resampler_fm : public resampler
{
	resampler_fm(uint32_t rate);
	sample_number_t nextsample();
	void sendpacket(struct packet& p);
};

struct packet_demux
{
	packet_demux(mixer& mix, uint32_t rate);
	~packet_demux();
	sample_number_t nextsample();
	void sendpacket(struct packet& p);
	void sendoption(const std::string& option);
private:
	void do_volume_change(struct packet& p);
	mixer& use_mixer;
	uint32_t used_rate;
	std::map<uint32_t, resampler*> resamplers;
	composite_filter* output_filter;
	std::map<std::string, composite_filter*> input_filters;
};

void print_audio_resampler_help(const std::string& prefix);
void process_audio_resampler_options(packet_demux& d, const std::string& prefix, int argc, char** argv);

#endif
