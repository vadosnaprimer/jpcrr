#include "fixup.hpp"
#include "representation.hpp"
#include <cmath>
#include <iostream>
#include <stdexcept>

bool is_video_track(nhml_file& f)
{
	return (static_cast<std::string>(f.header["streamType"]) == "4");
}

bool is_audio_track(nhml_file& f)
{
	return (static_cast<std::string>(f.header["streamType"]) == "5");
}

void reassign_track_ids(nhml_file& video, nhml_file& audio)
{
	std::cout << "Changing track IDs if needed..." << std::flush;
	if(static_cast<int64_t>(video.header["trackID"]) == static_cast<int64_t>(audio.header["trackID"])) {
		int64_t oldid = static_cast<int64_t>(audio.header["trackID"]);
		int64_t newid = oldid + 1;
		audio.header["trackID"] = newid;
		std::cout << "Changed audio track ID from " << oldid << " to " << newid << std::endl;
	} else
		std::cout << "Not needed" << std::endl;
}

int64_t compute_max_causality_violation(nhml_file& f)
{
	refstring CTS("CTS");
	refstring DTS("DTS");
	int64_t max = 0;
	for(auto i : f.samples) {
		int64_t cts = i[CTS];
		int64_t dts = i[DTS];
		if(cts < dts && max < dts - cts)
			max = dts - cts;
	}
	return max;
}

void adjust_cts(nhml_file& f, int64_t adjustment)
{
	refstring CTS("CTS");
	for(auto& i : f.samples)
		i[CTS] = static_cast<int64_t>(i[CTS]) + adjustment;
}

void adjust_xts(nhml_file& f, int64_t adjustment)
{
	refstring CTS("CTS");
	refstring DTS("DTS");
	for(auto& i : f.samples) {
		i[CTS] = static_cast<int64_t>(i[CTS]) + adjustment;
		i[DTS] = static_cast<int64_t>(i[DTS]) + adjustment;
	}
}

int64_t minimum_cts(nhml_file& f)
{
	int64_t min = 9000000000000000000LL;
	refstring CTS("CTS");
	for(auto i : f.samples) {
		int64_t cts = i[CTS];
		if(cts < min)
			min = cts;
	}
	return min;
}

void adjust_timescale(nhml_file& f, double scale)
{
	refstring CTS("CTS");
	refstring DTS("DTS");
	for(auto& i : f.samples) {
		int64_t cts = i[CTS];
		int64_t dts = i[DTS];
		cts = floor(cts * scale + 0.5);
		dts = floor(dts * scale + 0.5);
		i[CTS] = cts;
		i[DTS] = dts;
	}
	f.header["timeScale"] = floor(static_cast<int64_t>(f.header["timeScale"]) * scale + 0.5);
}

int64_t scale_ts(int64_t ts, int64_t srcscale, int64_t dstscale)
{
	return (ts * dstscale) / srcscale;
}

void workaround_mp4box_bug(nhml_file& f)
{
	std::cout << "Working around MP4Box bug..." << std::flush;
	int64_t max = 0;
	int64_t div = 1;
	refstring CTS("CTS");
	refstring DTS("DTS");
	for(auto i : f.samples) {
		int64_t cts = i[CTS];
		int64_t dts = i[DTS];
		if(cts > max)
			max = cts;
		if(dts > max)
			max = dts;
	}
	while(max / div > 0x7FFFFFFF)
		div++;
	if(div == 1) {
		std::cout << "No workaround needed." << std::endl;
		return;
	}
	std::cout << "Dividing timecodes by " << div << "." << std::endl;
	adjust_timescale(f, static_cast<double>(1.0) / div);
}

void adjust_audio(nhml_file& video, nhml_file& audio)
{
	std::cout << "Adjusting audio track..." << std::flush;
	int64_t mincts = minimum_cts(video);
	mincts = scale_ts(mincts, video.header["timeScale"], audio.header["timeScale"]);
	mincts -= minimum_cts(audio);
	if(mincts < 0)
		mincts = 0;
	adjust_xts(audio, mincts);
	std::cerr << mincts << "TUs (at " << static_cast<int64_t>(audio.header["timeScale"]) << "TU/s)." << std::endl;
}



void replace_timestamps(nhml_file& f, std::vector<int64_t> replacements)
{
	std::cout << "Replacing video timecodes..." << std::flush;
	if(f.samples.size() != replacements.size())
		throw std::runtime_error("Number of frames in video NHML and TC file doesn't match");
	refstring CTS("CTS");
	refstring DTS("DTS");
	ordinal_map cts_map;
	ordinal_map dts_map;
	for(auto i : f.samples) {
		cts_map.insert(i[CTS]);
		dts_map.insert(i[DTS]);
	}
	for(auto& i : f.samples) {
		i[CTS] = replacements[cts_map.lookup(i[CTS])];
		i[DTS] = replacements[dts_map.lookup(i[DTS])];
	}
	std::cout << "Done." << std::endl;
}

namespace
{
	inline int64_t snap(double raw, int64_t fps_n, int64_t fps_d)
	{
		double secs = raw / 1000.0;
		
		return floor((secs * fps_n) / fps_d + 0.5) * fps_d;
	}
}

void snap_timecodes(const std::vector<double>& in, std::vector<int64_t>& out, int64_t fps_n, int64_t fps_d)
{
	std::cout << "Snapping raw timecodes to " << fps_n << "/" << fps_d << " fps..." << std::flush;
	std::set<int64_t> used;
	for(auto i : in) {
		int64_t tc = snap(i, fps_n, fps_d);
		if(used.count(tc))
			throw std::runtime_error("Framerate too small (--assume-fps?)");
		used.insert(tc);
		out.push_back(tc);
	}
	std::cout << "Done." << std::endl;
}

namespace
{
	int64_t gcd(int64_t x, int64_t y)
	{
		if(y == 0)
			return x;
		else
			return gcd(y, x % y);
	}
}

void _do_fixup(nhml_file& video, nhml_file& audio, const std::vector<double>& rawtc, int64_t fps_n, int64_t fps_d,
	int dar_x, int64_t dar_y, int64_t res_x, int64_t res_y, bool cfr)
{
	nhml_to_internal(video, "video");
	nhml_to_internal(audio, "audio");
	std::vector<int64_t> tc;
	if(!fps_n || !fps_d) {
		fps_n = video.header["timeScale"];
		fps_d = 1;
	}
	if(dar_x && dar_y) {
		int64_t par_x;
		int64_t par_y;
		std::cout << "Fixing up AR flag..." << std::endl;
		if(!res_x)
			res_x = video.header["width"];
		if(!res_y)
			res_y = video.header["height"];
		par_x = dar_x * res_y;
		par_y = dar_y * res_x;
		int64_t g = gcd(par_x, par_y);
		video.header["parNum"] = par_x / g;
		video.header["parDen"] = par_y / g;
		std::cout << "Done (PAR is " << par_x / g << ":" << par_y / g << ")." << std::endl;
	}
	reassign_track_ids(video, audio);
	if(!cfr) {
		snap_timecodes(rawtc, tc, fps_n, fps_d);
		video.header["timeScale"] = fps_n;
		replace_timestamps(video, tc);
	}
	std::cout << "Calculating max causality violation..." << std::flush;
	int64_t max_cv = compute_max_causality_violation(video);
	std::cout << "Done." << std::endl;
	std::cout << "Adjusting video timestamps by " << max_cv << "TUs..." << std::flush;
	adjust_cts(video, max_cv);
	std::cout << "Done." << std::endl;
	adjust_audio(video, audio);
	workaround_mp4box_bug(video);
	workaround_mp4box_bug(audio);
	nhml_to_external(video, "video");
	nhml_to_external(audio, "audio");
}

void do_fixup(nhml_file& f1, nhml_file& f2, const std::vector<double>& rawtc, int64_t fps_n, int64_t fps_d,
	int64_t dar_x, int64_t dar_y, int64_t res_x, int64_t res_y)
{
	if(is_video_track(f1) && is_audio_track(f2))
		_do_fixup(f1, f2, rawtc, fps_n, fps_d, dar_x, dar_y, res_x, res_y, false);
	else if(is_video_track(f2) && is_audio_track(f1))
		_do_fixup(f2, f1, rawtc, fps_n, fps_d, dar_x, dar_y, res_x, res_y, false);
	else
		throw std::runtime_error("Expected one video NHML and one audio NHML");
}

void do_fixup_cfr(nhml_file& f1, nhml_file& f2, int64_t dar_x, int64_t dar_y, int64_t res_x, int64_t res_y)
{
	std::vector<double> rawtc;
	if(is_video_track(f1) && is_audio_track(f2))
		_do_fixup(f1, f2, rawtc, 0, 0, dar_x, dar_y, res_x, res_y, true);
	else if(is_video_track(f2) && is_audio_track(f1))
		_do_fixup(f2, f1, rawtc, 0, 0, dar_x, dar_y, res_x, res_y, true);
	else
		throw std::runtime_error("Expected one video NHML and one audio NHML");
}
