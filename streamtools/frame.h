#ifndef _frame__h__included__
#define _frame__h__included__

#include <stdint.h>

struct frame
{
	uint64_t f_timeseq;
	uint32_t f_width;
	uint32_t f_height;
	uint32_t f_framedata[];
};

struct frame* frame_create(uint32_t w, uint32_t h);
void frame_release(struct frame* frame);

struct frame_input_stream;
struct frame_output_stream;

struct frame_input_stream* fis_open(const char* filename);
struct frame* fis_next_frame(struct frame_input_stream* fis);
void fis_close(struct frame_input_stream* fis);
struct frame_output_stream* fos_open(const char* filename);
void fos_save_frame(struct frame_output_stream* fos, struct frame* frame);
void fos_close(struct frame_output_stream* fos);

#endif
