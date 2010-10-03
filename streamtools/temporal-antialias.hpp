#ifndef _temporal_antialias__hpp__included__
#define _temporal_antialias__hpp__included__

#include "framerate-reducer.hpp"
#include <list>

class framerate_reducer_temporalantialias : public framerate_reducer
{
public:
	framerate_reducer_temporalantialias(double alpha, uint32_t n, uint32_t d);
	~framerate_reducer_temporalantialias();
	void push(uint64_t ts, image_frame_rgbx& f);
	image_frame_rgbx& pull(uint64_t ts);
private:
	void compute_frame_weights(uint64_t ts, float* weights);
	image_frame_rgbx* newest;
	std::list<std::pair<uint64_t, image_frame_rgbx*> > queue;
	uint64_t last_ts;
	double factor;
	double tdiv;
};

#endif
