#include "frame.h"
#include <zlib.h>
#include <stdio.h>
#include <stdlib.h>

/*
struct frame
{
	uint64_t f_timeseq;
	uint32_t f_width;
	uint32_t f_height;
	uint32_t f_framedata[];
};
*/

static voidpf internal_alloc(voidpf opaque, uInt items, uInt size)
{
	size_t _items = items;
	size_t _size = size;
	voidpf ret;

	if((_items * _size) / _size != _items) {
		fprintf(stderr, "internal_alloc: %lu*%lu bytes overflows VM space.\n",
			(unsigned long)items, (unsigned long)size);
		exit(1);
	}
	if(!(ret = calloc(items, size))) {
		fprintf(stderr, "internal_alloc: Can't allocate %zu bytes.\n",
			_items * _size);
		exit(1);
	}
	return ret;
}

static void internal_free(voidpf opaque, voidpf address)
{
	free(address);
}

struct frame* frame_create(uint32_t w, uint32_t h)
{
	size_t size = (size_t)w * (size_t)h * sizeof(uint32_t) + sizeof(struct frame);
	if((size - sizeof(struct frame)) / h / sizeof(uint32_t) != w) {
		fprintf(stderr, "frame_create: %lu*%lu pixel image overflows VM space.\n",
			(unsigned long)w, (unsigned long)h);
		exit(1);
	}
	if(w == 0 || h == 0) {
		fprintf(stderr, "frame_create: Width and height must be positive.\n");
		exit(1);
	}

	struct frame* f = malloc(size);
	if(!f) {
		fprintf(stderr, "Can't allocate %zu bytes for frame.\n", size);
		exit(1);
	}
	f->f_timeseq = 0;
	f->f_width = w;
	f->f_height = h;
	return f;
}

void frame_release(struct frame* frame)
{
	free(frame);
}

#define INPUTBUFSZ 1024

struct frame_input_stream
{
	FILE* fis_filp;
	z_stream fis_zlib;
	uint64_t fis_current_seq;
	int fis_new_flag;
	int fis_eof_flag;
	uint8_t fis_buffer[INPUTBUFSZ];
};

struct frame_output_stream
{
	FILE* fos_filp;
	z_stream fos_zlib;
	uint64_t fos_current_seq;
	uint8_t fos_buffer[INPUTBUFSZ];
};

struct frame_input_stream* fis_open(const char* filename)
{
	struct frame_input_stream* fis = malloc(sizeof(struct frame_input_stream));
	if(!fis) {
		fprintf(stderr, "fis_open: Can't allocate %zu bytes for input stream.\n",
			sizeof(struct frame_input_stream));
		exit(1);
	}
	fis->fis_filp = fopen(filename, "rb");
	if(!fis->fis_filp) {
		fprintf(stderr, "fis_open: Can't open input file %s.\n", filename);
		exit(1);
	}
	fis->fis_zlib.zalloc = internal_alloc;
	fis->fis_zlib.zfree = internal_free;
	fis->fis_zlib.opaque = NULL;
	int r = inflateInit(&fis->fis_zlib);
	if(r != Z_OK) {
		fprintf(stderr, "fis_open: Can't initialize zlib: %s\n", fis->fis_zlib.msg);
		exit(1);
	}
	fis->fis_new_flag = 1;
	fis->fis_eof_flag = 0;
	return fis;
}

static void reinitialize_inflate(struct frame_input_stream* fis)
{
	z_stream* zlib = &fis->fis_zlib;

	Bytef* nin = zlib->next_in;
	Bytef* nout = zlib->next_out;
	uInt ain = zlib->avail_in;
	uInt aout = zlib->avail_out;
	if(inflateEnd(zlib) != Z_OK) {
		fprintf(stderr, "reinitialize_inflate: Can't finalize zlib: %s\n", zlib->msg);
		exit(1);
	}
	fis->fis_zlib.zalloc = internal_alloc;
	fis->fis_zlib.zfree = internal_free;
	fis->fis_zlib.opaque = NULL;
	int r = inflateInit(&fis->fis_zlib);
	if(r != Z_OK) {
		fprintf(stderr, "reinitialize_inflate: Can't initialize zlib: %s\n", fis->fis_zlib.msg);
		exit(1);
	}
	zlib->next_in = nin;
	zlib->next_out = nout;
	zlib->avail_in = ain;
	zlib->avail_out = aout;
}

static size_t read_inflate(struct frame_input_stream* fis, uint8_t* buf, size_t toread)
{
	fis->fis_zlib.next_out = (Bytef*)buf;
	fis->fis_zlib.avail_out = (uInt)toread;

	while(fis->fis_zlib.avail_out > 0) {
		int r = inflate(&fis->fis_zlib, 0);
		if(r == Z_STREAM_END) {
			/* This is special. We got to renitialize the stream. */
			reinitialize_inflate(fis);
			fis->fis_new_flag = 1;
		} else if(r != Z_OK && r != Z_BUF_ERROR) {
			fprintf(stderr, "read_inflate: Can't uncompress data: %s\n", fis->fis_zlib.msg);
			exit(1);
		}
		if(fis->fis_zlib.avail_in == 0 && !fis->fis_eof_flag) {
			/* Refill the buffer. */
			fis->fis_zlib.next_in = (Bytef*)fis->fis_buffer;
			fis->fis_zlib.avail_in = (uInt)fread(fis->fis_buffer, 1, INPUTBUFSZ, fis->fis_filp);
			if(ferror(fis->fis_filp)) {
				fprintf(stderr, "read_inflate: Input file read error.\n");
				exit(1);
			}
			if(feof(fis->fis_filp)) {
				fis->fis_eof_flag = 1;
				if(fis->fis_new_flag)
					return toread - fis->fis_zlib.avail_out;
			}
		} else if(fis->fis_zlib.avail_in == 0) {
			if(fis->fis_new_flag)
				return toread - fis->fis_zlib.avail_out;
			else {
				fprintf(stderr, "read_inflate: Unexpected end of input file.\n");
				exit(1);
			}
		}
		fis->fis_new_flag = 0;
	}
	return toread;
}

static void decode_pixeldata(uint32_t* pixels, uint8_t* data, size_t count)
{
	for(size_t i = 0; i < count; i++) {
		pixels[i] = 0;
		pixels[i] += ((uint32_t)data[4 * i + 1] << 16);
		pixels[i] += ((uint32_t)data[4 * i + 2] << 8);
		pixels[i] += ((uint32_t)data[4 * i + 3]);
	}
}

static void encodepixels(uint8_t* targ, uint32_t* pixels, size_t count)
{
	for(size_t i = 0; i < count; i++) {
		targ[4 * i] = 0;
		targ[4 * i + 1] = (uint8_t)(pixels[i] >> 16);
		targ[4 * i + 2] = (uint8_t)(pixels[i] >> 8);
		targ[4 * i + 3] = (uint8_t)(pixels[i]);
	}
}

#define BLOCKBUF_PIXELS 1024
#define BYTES_PER_PIXEL 4

struct frame* fis_next_frame(struct frame_input_stream* fis)
{
	uint8_t frameheaders[8];
	uint8_t blockbuffer[BYTES_PER_PIXEL * BLOCKBUF_PIXELS];
	uint64_t timeseq = fis->fis_current_seq;
	uint32_t width = 0;
	uint32_t height = 0;
	size_t pixelsleft;
	size_t pixelptr = 0;
	size_t r;

back:
	r = read_inflate(fis, frameheaders, 4);
	if(r == 0)
		return NULL;	/* End of stream. */
	if(r < 4) {
		fprintf(stderr, "fis_next_frame: Unexpected end of input.\n");
		exit(1);
	}
	if(frameheaders[0] == 255 && frameheaders[1] == 255 && frameheaders[3] == 255 && frameheaders[4] == 255) {
		timeseq += 0xFFFFFFFFUL;
		goto back;
	}
	r = read_inflate(fis, frameheaders + 4, 4);
	if(r < 4) {
		fprintf(stderr, "fis_next_frame: Unexpected end of input.\n");
		exit(1);
	}

	timeseq += ((uint32_t)frameheaders[0] << 24);
	timeseq += ((uint32_t)frameheaders[1] << 16);
	timeseq += ((uint32_t)frameheaders[2] << 8);
	timeseq += ((uint32_t)frameheaders[3]);
	width += ((uint32_t)frameheaders[4] << 8);
	width += ((uint32_t)frameheaders[5]);
	height += ((uint32_t)frameheaders[6] << 8);
	height += ((uint32_t)frameheaders[7]);
	pixelsleft = width * height;
	struct frame* f = frame_create(width, height);
	f->f_timeseq = timeseq;

	while(pixelsleft > 0) {
		if(pixelsleft > BLOCKBUF_PIXELS) {
			if(read_inflate(fis, blockbuffer, sizeof(blockbuffer)) < sizeof(blockbuffer)) {
				fprintf(stderr, "fis_next_frame: Unexpected end of input.\n");
				exit(1);
			}
			decode_pixeldata(f->f_framedata + pixelptr, blockbuffer, BLOCKBUF_PIXELS);
			pixelptr += BLOCKBUF_PIXELS;
			pixelsleft -= BLOCKBUF_PIXELS;
		} else {
			if(read_inflate(fis, blockbuffer, BYTES_PER_PIXEL * pixelsleft) <
				BYTES_PER_PIXEL * pixelsleft) {

				fprintf(stderr, "fis_next_frame: Unexpected end of input.\n");
				exit(1);
			}
			decode_pixeldata(f->f_framedata + pixelptr, blockbuffer, pixelsleft);
			pixelptr += pixelsleft;
			pixelsleft = 0;
		}
	}
	fis->fis_current_seq = f->f_timeseq;
	return f;
}

void fis_close(struct frame_input_stream* fis)
{
	inflateEnd(&fis->fis_zlib);
	fclose(fis->fis_filp);
	free(fis);
}

struct frame_output_stream* fos_open(const char* filename)
{
	struct frame_output_stream* fos = malloc(sizeof(struct frame_output_stream));
	if(!fos) {
		fprintf(stderr, "fos_open: Can't allocate %zu bytes for output stream.\n",
			sizeof(struct frame_output_stream));
		exit(1);
	}
	fos->fos_filp = fopen(filename, "wb");
	if(!fos->fos_filp) {
		fprintf(stderr, "fos_open: Can't open output file %s.\n", filename);
		exit(1);
	}
	fos->fos_zlib.zalloc = internal_alloc;
	fos->fos_zlib.zfree = internal_free;
	fos->fos_zlib.opaque = NULL;
	fos->fos_current_seq = 0;
	int r = deflateInit(&fos->fos_zlib, Z_BEST_COMPRESSION);
	if(r != Z_OK) {
		fprintf(stderr, "fos_open: Can't initialize zlib: %s\n", fos->fos_zlib.msg);
		exit(1);
	}
	fos->fos_zlib.avail_out = INPUTBUFSZ;
	fos->fos_zlib.next_out = fos->fos_buffer;
	return fos;
}
void fos_save_frame(struct frame_output_stream* fos, struct frame* frame)
{
	if(frame->f_timeseq < fos->fos_current_seq) {
		fprintf(stderr, "fos_save_frame: Timecodes jump backwards: %llu < %llu.\n",
			frame->f_timeseq, fos->fos_current_seq);
		exit(1);
	}
	int time_saved = 0;
	int header_saved = 0;
	uint8_t tmp[INPUTBUFSZ];
	struct frame* f = frame;
	uint32_t pixelsleft = f->f_width * f->f_height;
	uint32_t pixelptr = 0;

	while(pixelsleft > 0 || fos->fos_zlib.avail_in > 0 || !header_saved) {
		if(fos->fos_zlib.avail_out == 0) {
			if(fwrite(fos->fos_buffer, 1, INPUTBUFSZ, fos->fos_filp) < INPUTBUFSZ) {
				fprintf(stderr, "fos_close: Error flushing output stream.\n");
				exit(1);
			}
			fos->fos_zlib.next_out = (Bytef*)fos->fos_buffer;
			fos->fos_zlib.avail_out = (uInt)INPUTBUFSZ;
		}
		if(fos->fos_zlib.avail_in > 0) {
		} else if(frame->f_timeseq - fos->fos_current_seq >= 0xFFFFFFFFU) {
			tmp[0] = 255;
			tmp[1] = 255;
			tmp[2] = 255;
			tmp[3] = 255;
			fos->fos_zlib.next_in = (Bytef*)tmp;
			fos->fos_zlib.avail_in = 4;
			fos->fos_current_seq += 0xFFFFFFFFU;
			continue;
		} else if(!time_saved) {
			uint64_t diff = frame->f_timeseq - fos->fos_current_seq;
			tmp[0] = (uint8_t)(diff >> 24);
			tmp[1] = (uint8_t)(diff >> 16);
			tmp[2] = (uint8_t)(diff >> 8);
			tmp[3] = (uint8_t)(diff);
			fos->fos_zlib.next_in = (Bytef*)tmp;
			fos->fos_zlib.avail_in = 4;
			fos->fos_current_seq = frame->f_timeseq;
			time_saved = 1;
			continue;
		} else if(!header_saved) {
			tmp[0] = (uint8_t)(f->f_width >> 8);
			tmp[1] = (uint8_t)(f->f_width);
			tmp[2] = (uint8_t)(f->f_height >> 8);
			tmp[3] = (uint8_t)(f->f_height);
			fos->fos_zlib.next_in = (Bytef*)tmp;
			fos->fos_zlib.avail_in = 4;
			header_saved = 1;
			continue;
		} else {
			if(pixelsleft > INPUTBUFSZ / BYTES_PER_PIXEL) {
				encodepixels(tmp, f->f_framedata + pixelptr, INPUTBUFSZ / BYTES_PER_PIXEL);
				pixelptr += INPUTBUFSZ / BYTES_PER_PIXEL;
				pixelsleft -= INPUTBUFSZ / BYTES_PER_PIXEL;
				fos->fos_zlib.avail_in = INPUTBUFSZ;
			} else {
				encodepixels(tmp, f->f_framedata + pixelptr, pixelsleft);
				pixelptr += pixelsleft;
				fos->fos_zlib.avail_in = 4 * pixelsleft;
				pixelsleft = 0;
			}
			fos->fos_zlib.next_in = (Bytef*)tmp;
			continue;
		}
		int r = deflate(&fos->fos_zlib, 0);
		if(r != Z_OK) {
			fprintf(stderr, "fos_save_frame: Can't compress data: %s\n", fos->fos_zlib.msg);
			exit(1);
		}
	}

	if(fos->fos_zlib.avail_out < INPUTBUFSZ) {
		if(fwrite(fos->fos_buffer, 1, INPUTBUFSZ - fos->fos_zlib.avail_out, fos->fos_filp) < INPUTBUFSZ -
			fos->fos_zlib.avail_out) {

			fprintf(stderr, "fos_save_frame: Error flushing tail of frame.\n");
			exit(1);
		}
		fos->fos_zlib.avail_out = INPUTBUFSZ;
		fos->fos_zlib.next_out = fos->fos_buffer;
	}

}

void fos_close(struct frame_output_stream* fos)
{
	int r = deflate(&fos->fos_zlib, Z_FINISH);
	do {
		if(fwrite(fos->fos_buffer, 1, INPUTBUFSZ - fos->fos_zlib.avail_out, fos->fos_filp) < INPUTBUFSZ -
			fos->fos_zlib.avail_out) {

			fprintf(stderr, "fos_save_frame: Error flushing tail of stream.\n");
			exit(1);
		}
		fos->fos_zlib.avail_out = INPUTBUFSZ;
		fos->fos_zlib.next_out = fos->fos_buffer;

		if(r == Z_OK)
			r = deflate(&fos->fos_zlib, Z_FINISH);
	} while(r == Z_OK || fos->fos_zlib.avail_out < INPUTBUFSZ);
	if(r != Z_STREAM_END)
		fprintf(stderr, "Error flushing tail\n");

	deflateEnd(&fos->fos_zlib);
	fclose(fos->fos_filp);
	free(fos);
}
