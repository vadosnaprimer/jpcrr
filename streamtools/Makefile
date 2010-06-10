all: dumpframes.exe frameshow.exe rawtorgb.exe rawtoaudio2.exe mkdump.exe

rawtoaudio2.exe: rawtoaudio2.o opl.o audioconvert.o
	g++ -g -o $@ $^ -lz `sdl-config --libs`

%.exe: %.o frame.o
	gcc -g -o $@ $^ -lz `sdl-config --libs`

%.o: %.c frame.h
	gcc -g -std=c99 -c -o $@ $< `sdl-config --cflags`

rawtoaudio2.o: rawtoaudio2.cpp
	g++ -g -c -o $@ $< -DOPLTYPE_IS_OPL3 `sdl-config --cflags`

audioconvert.o: audioconvert.cpp
	g++ -g -c -o $@ $< -DOPLTYPE_IS_OPL3 `sdl-config --cflags`

opl.o: opl.cpp opl.h
	g++ -g -DINLINE=inline -DOPLTYPE_IS_OPL3 -DOPL_CPP -c -o $@ $<
