#ifndef _fixup__hpp__included__
#define _fixup__hpp__included__

#include <vector>
#include <iostream>
#include "nhml.hpp"

bool is_video_track(nhml_file& f);
bool is_audio_track(nhml_file& f);
void reassign_track_ids(nhml_file& video, nhml_file& audio);
int64_t compute_max_causality_violation(nhml_file& f);
void adjust_cts(nhml_file& f, int64_t adjustment);
void adjust_xts(nhml_file& f, int64_t adjustment);
int64_t minimum_cts(nhml_file& f);
void adjust_timescale(nhml_file& samples, double scale);
int64_t scale_ts(int64_t ts, int64_t srcscale, int64_t dstscale);
void workaround_mp4box_bug(nhml_file& f);
void adjust_audio(nhml_file& video, nhml_file& audio);
void replace_timestamps(nhml_file& f, std::vector<int64_t> replacements);
void snap_timecodes(const std::vector<double>& in, std::vector<int64_t>& out, int64_t fps_n, int64_t fps_d);
void do_fixup(nhml_file& f1, nhml_file& f2, const std::vector<double>& rawtc, int64_t fps_n, int64_t fps_d,
	int64_t dar_x, int64_t dar_y, int64_t res_x, int64_t res_y);
void do_fixup_cfr(nhml_file& f1, nhml_file& f2, int64_t dar_x, int64_t dar_y, int64_t res_x, int64_t res_y);

#endif
