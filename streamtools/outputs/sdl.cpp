#include "outputs/internal.hpp"
#include <iostream>
#include <fstream>
#include <stdexcept>
#include <string>
#include "SDL.h"

namespace
{
	class output_driver_sdl : public output_driver
	{
	public:
		output_driver_sdl()
		{
			set_video_callback(make_bound_method(*this, &output_driver_sdl::video_callback));
		}

		~output_driver_sdl()
		{
		}

		void ready()
		{
			const video_settings& v = get_video_settings();
			width = v.get_width();
			height = v.get_height();
#if SDL_BYTEORDER == SDL_BIG_ENDIAN
			uint32_t rmask = 0xFF000000;
			uint32_t gmask = 0x00FF0000;
			uint32_t bmask = 0x0000FF00;
#else
			uint32_t rmask = 0x000000FF;
			uint32_t gmask = 0x0000FF00;
			uint32_t bmask = 0x00FF0000;
#endif

			if(SDL_Init(SDL_INIT_VIDEO) < 0)
				throw std::runtime_error("SDL: Can't initialize SDL");
			hwsurf = SDL_SetVideoMode(width, height, 0, SDL_SWSURFACE | SDL_DOUBLEBUF | SDL_ANYFORMAT);
			swsurf = SDL_CreateRGBSurface(SDL_SWSURFACE, width, height, 32, rmask, gmask, bmask, 0);
			if(!hwsurf || !swsurf)
				throw std::runtime_error("SDL: Can't initialize SDL");
		}

		void video_callback(uint64_t timestamp, const uint8_t* raw_rgbx_data)
		{
			SDL_LockSurface(swsurf);
			memcpy((unsigned char*)swsurf->pixels, raw_rgbx_data, 4 * width * height);
			SDL_UnlockSurface(swsurf);
			SDL_BlitSurface(swsurf, NULL, hwsurf, NULL);
			SDL_Flip(hwsurf);
			SDL_Event e;
			if(SDL_PollEvent(&e) == 1 && e.type == SDL_QUIT)
				throw std::runtime_error("SDL: Quit by user");
		}
	private:
		SDL_Surface* swsurf;
		SDL_Surface* hwsurf;
		size_t width;
		size_t height;
	};

	class output_driver_sdl_factory : output_driver_factory
	{
	public:
		output_driver_sdl_factory()
			: output_driver_factory("sdl")
		{
		}

		output_driver& make(const std::string& type, const std::string& name, const std::string& parameters)
		{
			if(parameters != "")
				throw std::runtime_error("sdl output does not take parameters");
			return *new output_driver_sdl();
		}
	} factory;
}
