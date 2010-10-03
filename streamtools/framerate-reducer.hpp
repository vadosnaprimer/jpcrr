#ifndef _framerate_reducer__hpp__included__
#define _framerate_reducer__hpp__included__

#include "resize.hpp"

class framerate_reducer
{
public:
	framerate_reducer();
	virtual ~framerate_reducer();
	virtual void push(uint64_t ts, image_frame_rgbx& f) = 0;
	virtual image_frame_rgbx& pull(uint64_t ts) = 0;
};

class framerate_reducer_dropframes : public framerate_reducer
{
public:
	framerate_reducer_dropframes();
	void push(uint64_t ts, image_frame_rgbx& f);
	image_frame_rgbx& pull(uint64_t ts);
private:
	image_frame_rgbx* newest;
};

#endif
