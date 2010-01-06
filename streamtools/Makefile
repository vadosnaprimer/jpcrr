all: dumpframes.exe frameshow.exe letterbox.exe streamcopy.exe rawtorgb.exe rawtoaudio.exe

%.exe: %.o frame.o
	gcc -g -o $@ $^ -lz `sdl-config --libs`

%.o: %.c frame.h
	gcc -g -std=c99 -c -o $@ $< `sdl-config --cflags`
