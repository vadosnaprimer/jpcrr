#include "SDL_image.h"
#include "SDL.h"
#include "resize.hpp"
#include "rescalers/public.hpp"
#include <zlib.h>
#include <cstdlib>
#include <stdint.h>
#include <string>
#include <iostream>
#include <vector>
#include "newpacket.hpp"
#include <stdexcept>

namespace
{
	bool do_quit_on(SDL_Event* e)
	{
		if(e->type == SDL_QUIT)
			return true;
		if(e->type == SDL_KEYUP && e->key.keysym.sym == 'q')
			return true;
		return false;
	}
}

int real_main(int argc, char** argv)
{
	if(argc < 5) {
		std::cerr << "Syntax: testresizer.exe <picture> <algo> <width> <height>" << std::endl;
		exit(1);
	}

	char* ptr1;
	char* ptr2;
	unsigned twidth = strtoul(argv[3], &ptr1, 10);
	unsigned theight = strtoul(argv[4], &ptr2, 10);
	if(!twidth || !theight || *ptr1 || *ptr2) {
		std::cerr << "Error: Bad target size." << std::endl;
		exit(1);
	}


	//Load the image.
	SDL_Surface* img = IMG_Load(argv[1]);
	if(!img) {
		std::cerr << "Can't load image '" << argv[1] << "':" << IMG_GetError() << std::endl;
		exit(2);
	}

	//Convert the SDL surface into raw image.
	unsigned width = img->w;
	unsigned height = img->h;
	image_frame_rgbx src(width, height);
	unsigned char* pixels = src.get_pixels();
	SDL_LockSurface(img);
	for(uint32_t y = 0; y < height; y++)
		for(uint32_t x = 0; x < width; x++) {
			Uint8 r, g, b;
			size_t addr = y * img->pitch + x * img->format->BytesPerPixel;
			SDL_GetRGB(*(Uint32*)((unsigned char*)img->pixels + addr), img->format, &r, &g, &b);
			pixels[4 * width * y + 4 * x + 0] = r;
			pixels[4 * width * y + 4 * x + 1] = g;
			pixels[4 * width * y + 4 * x + 2] = b;
			pixels[4 * width * y + 4 * x + 3] = 0;
		}
	SDL_UnlockSurface(img);
	SDL_FreeSurface(img);

	rescaler_group grp(*(parse_rescaler_expression(argv[2]).use_rescaler));
	image_frame_rgbx& dest = src.resize(twidth, theight, grp);

	//Now, display the image.
	SDL_Surface* swsurf = NULL;
	SDL_Surface* hwsurf = NULL;

	if(SDL_Init(SDL_INIT_VIDEO) < 0) {
		std::cerr << "Can't Initialize SDL." << std::endl;
		exit(2);
	}

#if SDL_BYTEORDER == SDL_BIG_ENDIAN
	uint32_t rmask = 0xFF000000;
	uint32_t gmask = 0x00FF0000;
	uint32_t bmask = 0x0000FF00;
#else
	uint32_t rmask = 0x000000FF;
	uint32_t gmask = 0x0000FF00;
	uint32_t bmask = 0x00FF0000;
#endif

	hwsurf = SDL_SetVideoMode(dest.get_width(), dest.get_height(), 0, SDL_SWSURFACE |
		SDL_DOUBLEBUF | SDL_ANYFORMAT);
	swsurf = SDL_CreateRGBSurface(SDL_SWSURFACE, dest.get_width(), dest.get_height(), 32,
		rmask, gmask, bmask, 0);
	if(!swsurf || !hwsurf) {
		std::cerr << "Can't Set video mode." << std::endl;
		exit(2);
	}

	//Copy the picture to surface.
	SDL_LockSurface(swsurf);
	memcpy((unsigned char*)swsurf->pixels, dest.get_pixels(), 4 * twidth * theight);
	SDL_UnlockSurface(swsurf);

	//Render and wait.
	SDL_BlitSurface(swsurf, NULL, hwsurf, NULL);
	SDL_Flip(hwsurf);
	SDL_Event e;
	while(SDL_PollEvent(&e) != 1 || !do_quit_on(&e));

	if(&dest != &src)
		delete &dest;
	return 0;
}
