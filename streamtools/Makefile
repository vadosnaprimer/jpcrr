all:  screenshot.exe dumppackets.exe picturestodump.exe audiotodump.exe demuxdump.exe muxdump.exe mknulldump.exe cutdump.exe fmtopcm.exe playdump.exe dumpconvert.exe guessresolution.exe testresizer.exe

COMPILER=g++
CXXFLAGS2=-g -O2 -Wall
CXXFLAGS=$(CXXFLAGS2)
RESIZE_DRIVERS=resize.o resizer-lanczos.o resizer-nearest.o resizer-test.o resize-linear-separable.o resizer-bilinear.o resizer-xdrop9.o
I420_DRIVER_FILES=output-drv-rawi420.o rgbtorgb.o
X264_DRIVER_FILES=output-drv-x264.o rgbtorgb.o
OUTPUT_DRIVERS=$(RESIZE_DRIVERS) dedup.o output-drv.o output-drv-rawrgbx.o output-drv-timecodev2.o output-drv-rawaudio.o output-drv-wav.o output-drv-faac.o $(I420_DRIVER_FILES) $(X264_DRIVER_FILES) output-drv-oggenc.o
ifdef WITH_HQX
RESIZE_DRIVERS += resizer-hqx.o
LIBS=-lhqx
endif
ifdef MINGW
MINGW_LIBS=-lmingw32
else
MINGW_LIBS=
endif
ifdef LIBS_PATH
LIBS_PATH2=-L$(LIBS_PATH)
else
LIBS_PATH2=
endif
LIBS=$(LIBS_PATH2) $(SDLMAIN_FILE) -lz -lSDL -lSDL_image -lSDL_ttf $(MINGW_LIBS) -lSDLmain -lSDL

picturestodump.exe: picturestodump.o newpacket.o png-out.o timecounter.o main.o
	$(COMPILER) $(CXXFLAGS2) -o $@ $^ $(LIBS)

audiotodump.exe: audiotodump.o newpacket.o timecounter.o main.o
	$(COMPILER) $(CXXFLAGS2) -o $@ $^ $(LIBS)

demuxdump.exe: demuxdump.o newpacket.o main.o
	$(COMPILER) $(CXXFLAGS2) -o $@ $^ $(LIBS)

muxdump.exe: muxdump.o newpacket.o main.o
	$(COMPILER) $(CXXFLAGS2) -o $@ $^ $(LIBS)

fmtopcm.exe: fmtopcm.o newpacket.o resampler.o timecounter.o digital-filter.o opl.o misc.o main.o
	$(COMPILER) $(CXXFLAGS2) -o $@ $^ $(LIBS)

screenshot.exe: screenshot.o newpacket.o png-out.o timeparse.o main.o
	$(COMPILER) $(CXXFLAGS2) -o $@ $^ $(LIBS)

dumppackets.exe: dumppackets.o newpacket.o main.o
	$(COMPILER) $(CXXFLAGS2) -o $@ $^ $(LIBS)

mknulldump.exe: mknulldump.o newpacket.o timeparse.o main.o
	$(COMPILER) $(CXXFLAGS2) -o $@ $^ $(LIBS)

cutdump.exe: cutdump.o newpacket.o timeparse.o main.o
	$(COMPILER) $(CXXFLAGS2) -o $@ $^ $(LIBS)

guessresolution.exe: guessresolution.o newpacket.o main.o
	$(COMPILER) $(CXXFLAGS2) -o $@ $^ $(LIBS)

playdump.exe: playdump.o newpacket.o timecounter.o resize.o resampler.o digital-filter.o opl.o misc.o hardsubs.o timeparse.o main.o
	$(COMPILER) $(CXXFLAGS2) -o $@ $^ $(LIBS)

dumpconvert.exe: dumpconvert.o $(OUTPUT_DRIVERS) packet-processor.o resampler.o digital-filter.o misc.o opl.o timecounter.o hardsubs.o newpacket.o timeparse.o main.o framerate-reducer.o
	$(COMPILER) $(CXXFLAGS2) -o $@ $^ $(LIBS)

rgbtorgb.o: rgbtorgb.cc lanczos.hh rgbtorgb.hh quantize.hh simd.hh
	$(COMPILER) $(CXXFLAGS) -c -o $@ $<

%.o: %.cpp newpacket.hpp
	$(COMPILER) $(CXXFLAGS2) -Werror -c -o $@ $< `sdl-config --cflags`

opl.o: opl.cpp opl.h
	$(COMPILER) $(CXXFLAGS2) -DINLINE=inline -DOPLTYPE_IS_OPL3 -DOPL_CPP -c -o $@ $<

testresizer.exe: testresizer.o $(RESIZE_DRIVERS)  main.o
	$(COMPILER) $(CXXFLAGS2) -o $@ $^ $(LIBS)
