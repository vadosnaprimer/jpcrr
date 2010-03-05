all: dumpframes.exe frameshow.exe letterbox.exe streamcopy.exe rawtorgb.exe rawtoaudio.exe audiomix.exe audiofilter.exe

rawtoaudio.exe: rawtoaudio.o opl.o
	g++ -g -o $@ $^ -lz `sdl-config --libs`

%.exe: %.o frame.o
	gcc -g -o $@ $^ -lz `sdl-config --libs`

%.o: %.c frame.h
	gcc -g -std=c99 -c -o $@ $< `sdl-config --cflags`

rawtoaudio.o: rawtoaudio.cpp
	g++ -g -c -o $@ $< -DOPLTYPE_IS_OPL3 `sdl-config --cflags`

opl.o: opl.cpp opl.h
	g++ -g -DINLINE=inline -DOPLTYPE_IS_OPL3 -DOPL_CPP -c -o $@ $<
