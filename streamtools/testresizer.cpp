#include "SDL_image.h"
#include "SDL.h"
#include "resize.hpp"
#include <zlib.h>
#include <cstdlib>
#include <stdint.h>
#include <string>
#include <iostream>
#include <vector>
#include "newpacket.hpp"
#include <stdexcept>

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
	image_frame_rgbx& src = *new image_frame_rgbx(img);
	resizer& r = resizer_factory::make_by_type(argv[2]);
	image_frame_rgbx& dest = src.resize(twidth, theight, r);

	//Now, display the image.
	SDL_Surface* swsurf = NULL;
	SDL_Surface* hwsurf = NULL;

	if(SDL_Init(SDL_INIT_VIDEO) < 0) {
		std::cerr << "Can't Initialize SDL." << std::endl;
		exit(2);
	}

	hwsurf = SDL_SetVideoMode(dest.get_width(), dest.get_height(), 0, SDL_SWSURFACE |
		SDL_DOUBLEBUF | SDL_ANYFORMAT);
	swsurf = dest.get_surface();
	if(!swsurf || !hwsurf) {
		std::cerr << "Can't Set video mode." << std::endl;
		exit(2);
	}

	//Render and wait.
	SDL_BlitSurface(swsurf, NULL, hwsurf, NULL);
	SDL_Flip(hwsurf);
	SDL_Event e;
	while(SDL_WaitEvent(&e) != 1 || e.type != SDL_QUIT);

	src.put_ref();
	dest.put_ref();
	return 0;
}
