all:  screenshot.exe dumppackets.exe picturestodump.exe audiotodump.exe demuxdump.exe muxdump.exe mknulldump.exe cutdump.exe fmtopcm.exe playdump.exe dumpconvert.exe

CXXFLAGS=-g -O2 -Werror -Wall
RESIZE_DRIVERS=resize.o resizer-lanczos.o
OUTPUT_DRIVERS=$(RESIZE_DRIVERS) dedup.o output-drv.o output-drv-rawrgbx.o output-drv-timecodev2.o output-drv-rawaudio.o output-drv-wav.o

picturestodump.exe: picturestodump.o newpacket.o png-out.o timecounter.o
	g++ $(CXXFLAGS) -o $@ $^ -lz `sdl-config --libs` -lSDL_image

audiotodump.exe: audiotodump.o newpacket.o timecounter.o
	g++ $(CXXFLAGS) -o $@ $^ -lz `sdl-config --libs`

demuxdump.exe: demuxdump.o newpacket.o
	g++ $(CXXFLAGS) -o $@ $^ -lz

muxdump.exe: muxdump.o newpacket.o
	g++ $(CXXFLAGS) -o $@ $^ -lz

fmtopcm.exe: fmtopcm.o newpacket.o resampler.o timecounter.o digital-filter.o opl.o misc.o
	g++ $(CXXFLAGS) -o $@ $^ -lz

screenshot.exe: screenshot.o newpacket.o png-out.o timeparse.o
	g++ $(CXXFLAGS) -o $@ $^ -lz

dumppackets.exe: dumppackets.o newpacket.o
	g++ $(CXXFLAGS) -o $@ $^ -lz

mknulldump.exe: mknulldump.o newpacket.o timeparse.o
	g++ $(CXXFLAGS) -o $@ $^ -lz

cutdump.exe: cutdump.o newpacket.o timeparse.o
	g++ $(CXXFLAGS) -o $@ $^ -lz

playdump.exe: playdump.o newpacket.o timecounter.o resize.o resampler.o digital-filter.o opl.o misc.o hardsubs.o timeparse.o
	g++ $(CXXFLAGS) -o $@ $^ -lz `sdl-config --libs` -lSDL_ttf

rawtoaudio2.exe: rawtoaudio2.o opl.o audioconvert.o
	g++ $(CXXFLAGS) -o $@ $^ -lz `sdl-config --libs`

dumpconvert.exe: dumpconvert.o $(OUTPUT_DRIVERS) packet-processor.o resampler.o digital-filter.o misc.o opl.o timecounter.o hardsubs.o newpacket.o timeparse.o
	g++ $(CXXFLAGS) -o $@ $^ -lz `sdl-config --libs` -lSDL_ttf

%.o: %.cpp newpacket.hpp
	g++ $(CXXFLAGS) -c -o $@ $< `sdl-config --cflags`

opl.o: opl.cpp opl.h
	g++ $(CXXFLAGS) -DINLINE=inline -DOPLTYPE_IS_OPL3 -DOPL_CPP -c -o $@ $<
