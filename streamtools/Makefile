all:  screenshot.exe dumppackets.exe picturestodump.exe audiotodump.exe demuxdump.exe muxdump.exe mknulldump.exe cutdump.exe fmtopcm.exe playdump.exe dumpconvert.exe guessresolution.exe

CXXFLAGS2=-g -O2 -Wall -fopenmp
CXXFLAGS=$(CXXFLAGS2)
RESIZE_DRIVERS=resize.o resizer-lanczos.o resizer-nearest.o resizer-test.o
I420_DRIVER_FILES=output-drv-rawi420.o rgbtorgb.o
X264_DRIVER_FILES=output-drv-x264.o rgbtorgb.o
OUTPUT_DRIVERS=$(RESIZE_DRIVERS) dedup.o output-drv.o output-drv-rawrgbx.o output-drv-timecodev2.o output-drv-rawaudio.o output-drv-wav.o output-drv-faac.o $(I420_DRIVER_FILES) $(X264_DRIVER_FILES) output-drv-oggenc.o
ifdef WITH_HQX
RESIZE_DRIVERS += resizer-hqx.o
LIBS=-lhqx
endif

picturestodump.exe: picturestodump.o newpacket.o png-out.o timecounter.o
	g++ $(CXXFLAGS2) -o $@ $^ -lz `sdl-config --libs` -lSDL_image $(LIBS)

audiotodump.exe: audiotodump.o newpacket.o timecounter.o
	g++ $(CXXFLAGS2) -o $@ $^ -lz `sdl-config --libs` $(LIBS)

demuxdump.exe: demuxdump.o newpacket.o
	g++ $(CXXFLAGS2) -o $@ $^ -lz $(LIBS)

muxdump.exe: muxdump.o newpacket.o
	g++ $(CXXFLAGS2) -o $@ $^ -lz $(LIBS)

fmtopcm.exe: fmtopcm.o newpacket.o resampler.o timecounter.o digital-filter.o opl.o misc.o
	g++ $(CXXFLAGS2) -o $@ $^ -lz $(LIBS)

screenshot.exe: screenshot.o newpacket.o png-out.o timeparse.o
	g++ $(CXXFLAGS2) -o $@ $^ -lz $(LIBS)

dumppackets.exe: dumppackets.o newpacket.o
	g++ $(CXXFLAGS2) -o $@ $^ -lz $(LIBS)

mknulldump.exe: mknulldump.o newpacket.o timeparse.o
	g++ $(CXXFLAGS2) -o $@ $^ -lz $(LIBS)

cutdump.exe: cutdump.o newpacket.o timeparse.o
	g++ $(CXXFLAGS2) -o $@ $^ -lz $(LIBS)

guessresolution.exe: guessresolution.o newpacket.o
	g++ $(CXXFLAGS2) -o $@ $^ -lz $(LIBS)

playdump.exe: playdump.o newpacket.o timecounter.o resize.o resampler.o digital-filter.o opl.o misc.o hardsubs.o timeparse.o
	g++ $(CXXFLAGS2) -o $@ $^ -lz `sdl-config --libs` -lSDL_ttf $(LIBS)

rawtoaudio2.exe: rawtoaudio2.o opl.o audioconvert.o
	g++ $(CXXFLAGS2) -o $@ $^ -lz `sdl-config --libs` $(LIBS)

dumpconvert.exe: dumpconvert.o $(OUTPUT_DRIVERS) packet-processor.o resampler.o digital-filter.o misc.o opl.o timecounter.o hardsubs.o newpacket.o timeparse.o
	g++ $(CXXFLAGS2) -o $@ $^ -lz `sdl-config --libs` -lSDL_ttf $(LIBS)

%.o: %.cpp newpacket.hpp
	g++ $(CXXFLAGS2) -c -o $@ $< `sdl-config --cflags`

%.o: %.cc lanczos.hh rgbtorgb.hh quatize.hh simd.hh
	g++ $(CXXFLAGS) -c -o $@ $<

opl.o: opl.cpp opl.h
	g++ $(CXXFLAGS2) -DINLINE=inline -DOPLTYPE_IS_OPL3 -DOPL_CPP -c -o $@ $<
