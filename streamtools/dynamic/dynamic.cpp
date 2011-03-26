#include "dynamic/dynamic.hpp"
#include <iostream>
#include <cstring>
#define _USE_BSD
#include <sys/mman.h>

void* commit_machine_code(const std::vector<uint8_t>& code)
{
	uint8_t* cb = NULL;
	if(code.empty())
		return NULL;
#ifdef __linux__
	uint32_t toalloc = (code.size() + getpagesize() - 1) / getpagesize() * getpagesize();
	cb = (uint8_t*)mmap(NULL, toalloc, PROT_READ | PROT_WRITE, MAP_PRIVATE | MAP_ANONYMOUS, -1, 0);
	if(cb == MAP_FAILED)
		return NULL;
	memcpy(cb, &code[0], code.size());
	if(mprotect(cb, toalloc, PROT_READ | PROT_EXEC) < 0) {
		munmap(cb, toalloc);
		return NULL;
	}
#endif
	if(cb)
		std::cerr << "Rescaler mapped to address 0x" << std::hex << (unsigned long)cb << std::dec << std::endl;
	return cb;
}

void write_trailer_bytes_64(std::vector<uint8_t>& code, uint8_t byte, bool enable)
{
	if(!enable)
		return;
	code.push_back(byte);
	code.push_back(byte);
	code.push_back(byte);
	code.push_back(byte);
}

void write32_le(std::vector<uint8_t>& code, uint32_t value)
{
	code.push_back(value);
	code.push_back(value >> 8);
	code.push_back(value >> 16);
	code.push_back(value >> 24);
}

//Trashes [ER]CX, [ER]SI and [ER]DI. Pops stuff from stack.
void write_line_intel(std::vector<uint8_t>& code, uint32_t* strip_widths, uint32_t swidth, uint32_t twidth,
	bool bits64)
{
	uint32_t ntwidth = (uint32_t)(-twidth);
	std::cerr << "ntwidth=" << std::hex << ntwidth << std::dec << "." << std::endl;
	for(uint32_t i = 0; i < swidth; i++)
	{
		code.push_back(0xAD);	//LODSD
		for(uint32_t j = 0; j < strip_widths[i]; j++)
			code.push_back(0xAB);	//STOSD
	}
	code.push_back(0x59);	//POP RCX / POP ECX
	code.push_back(0x56);	//PUSH RSI / PUSH ESI
	if(bits64)
		code.push_back(0x48);	//Make next instruction 64-bit.
	code.push_back(0x89);		//MOV RSI, RDI / MOV ESI, EDI
	code.push_back(0xFE);
	if(bits64)
		code.push_back(0x48);	//Make next instruction 64-bit.
	code.push_back(0x81);	//SUB RSI, x / SUB ESI, x
	code.push_back(0xEE);
	write32_le(code, 4 * twidth);
	code.push_back(0xF3);	//REP MOVSD
	code.push_back(0xA5);
	code.push_back(0x5E);	//POP RSI
}

void write_loop_intel(std::vector<uint8_t>& code, uint32_t* strip_widths, uint32_t swidth, uint32_t twidth,
	uint32_t* strip_heights, uint32_t lines, bool bits64)
{
	code.push_back(0x9C);		//PUSHF
	code.push_back(0xFC);		//CLD

	for(uint32_t i = lines - 1; i < lines; i--) {
		if(bits64)
			code.push_back(0x48);	//Make next instruction 64-bit.
		code.push_back(0xB8);		//MOV EAX, imm / MOV RAX, imm.
		write32_le(code, (strip_heights[i] - 1) * twidth);
		write_trailer_bytes_64(code, 0, bits64);
		code.push_back(0x50);	//PUSH EAX.
	}

	if(bits64)
		code.push_back(0x48);	//Make next instruction 64-bit.
	code.push_back(0xBA);	//MOV RDX, imm / MOV EDX, imm.
	write32_le(code, lines);
	write_trailer_bytes_64(code, 0, bits64);

	uint32_t osize = code.size();
	write_line_intel(code, strip_widths, swidth, twidth, bits64);
	if(bits64)
		code.push_back(0x48);	//Make next instruction 64-bit.
	code.push_back(0x83);	//SUB RDX, 1 / SUB EDX, 1.
	code.push_back(0xEA);
	code.push_back(0x01);
	code.push_back(0x0F);	//JNZ NEAR.
	code.push_back(0x85);
	uint32_t jumpoffset = osize - (code.size() + 4);	//Yes, this is negative.
	write32_le(code, jumpoffset);

	code.push_back(0x9D);		//POPF
}

void prologue_linux_intel(std::vector<uint8_t>& code, bool bits64)
{
	code.push_back(0x55);		//PUSH RBP / PUSH EBP
	if(bits64)
		code.push_back(0x48);	//Make next instruction 64-bit.
	code.push_back(0x89);		//MOV EBP, ESP / MOV RBP, RSP
	code.push_back(0xE5);

	if(!bits64) {
		code.push_back(0x8B);	//MOV EDI, [EBP + 8]
		code.push_back(0x7D);
		code.push_back(0x08);
		code.push_back(0x8B);	//MOV EDI, [EBP + 12]
		code.push_back(0x75);
		code.push_back(0x0C);
	}
}

void postlogue_linux_intel(std::vector<uint8_t>& code)
{
	code.push_back(0xC9);		//LEAVE
	code.push_back(0xC3);		//RET
}



void generate_hdscaler(std::vector<uint8_t>& code, uint32_t* strip_widths, uint32_t* strip_heights, uint32_t swidth,
	uint32_t sheight, uint32_t twidth)
{
#if defined(__x86_64__)
#if defined(__linux__)
	prologue_linux_intel(code, true);
	write_loop_intel(code, strip_widths, swidth, twidth, strip_heights, sheight, true);
	postlogue_linux_intel(code);
#else
		return;
#endif
#else
#if defined(__i386__)
#if defined(__linux__)
	prologue_linux_intel(code, false);
	write_loop_intel(code, strip_widths, swidth, twidth, strip_heights, sheight, false);
	postlogue_linux_intel(code);
#else
	return;
#endif
#else
	return;
#endif
#endif
	std::cerr << "Generated scaler, " << code.size() << " bytes." << std::endl;
}
