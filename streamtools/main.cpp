#ifndef NO_SDLMAIN
#include "SDL_main.h"
#endif

int real_main(int argc, char** argv);

int main(int argc, char** argv)
{
	return real_main(argc, argv);
}
