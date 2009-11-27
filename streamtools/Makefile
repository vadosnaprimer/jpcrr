all: dumpframes.exe frameshow.exe letterbox.exe streamcopy.exe

%.exe: %.o frame.o
	gcc -o $@ $^ -lz `sdl-config --libs`

%.o: %.c frame.h
	gcc -std=c99 -c -o $@ $< `sdl-config --cflags`
