#include "framerate-reducer.hpp"

framerate_reducer::framerate_reducer()
{
}

framerate_reducer::~framerate_reducer()
{
}

framerate_reducer_dropframes::framerate_reducer_dropframes()
{
	newest = NULL;
}

framerate_reducer_dropframes::~framerate_reducer_dropframes()
{
	if(newest)
		newest->put_ref();
}

void framerate_reducer_dropframes::push(uint64_t ts, image_frame_rgbx& f)
{
	if(newest)
		newest->put_ref();
	f.get_ref();
	newest = &f;
}

image_frame_rgbx& framerate_reducer_dropframes::pull(uint64_t ts)
{
	if(newest) {
		newest->get_ref();
		return *newest;
	} else
		return *new image_frame_rgbx(0, 0);
}
