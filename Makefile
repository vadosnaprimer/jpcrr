CC=g++
EXE=.exe

NHMLFixup2$(EXE): dictionary.o fixup.o nhml.o NHMLFixup2.o representation.o
	$(CC) -o $@ $^ -lexpat

%.o: %.cpp
	$(CC) -g --std=c++0x -c -o $@ $<
