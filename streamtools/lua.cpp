#include "lua.hpp"
#include <sstream>
extern "C"
{
#include <lua.h>
#include <lauxlib.h>
#include <lualib.h>
}
#include <stdexcept>
#include "resize.hpp"
#include <iostream>
#include "packet-processor.hpp"

namespace {
	char dummyaddr;

	void push_frame(lua_State* L, image_frame_rgbx* frame);

	struct internal {
		bool script_loaded;
		lua_State* L;
	};

	void raise_error(lua_State* L, const std::string& message)
	{
		lua_pushstring(L, message.c_str());
		lua_error(L);
	}

	template<typename T>
	T get_numeric_argument(lua_State* L, unsigned argument)
	{
		if(lua_type(L, argument) != LUA_TNUMBER) {
			std::ostringstream str;
			str << "Expected number as argument #" << argument << ".";
			raise_error(L, str.str());
		}
		return (T)lua_tonumber(L, argument);
	}

	void* get_userdata_argument(lua_State* L, unsigned argument)
	{
		if(lua_type(L, argument) != LUA_TUSERDATA) {
			std::ostringstream str;
			str << "Expected userdata as argument #" << argument << ".";
			raise_error(L, str.str());
		}
		return lua_touserdata(L, argument);
	}

	image_frame_rgbx* get_image_argument(lua_State* L, unsigned argument)
	{
		return *(image_frame_rgbx**)get_userdata_argument(L, argument);
	}

	int gc_frame(lua_State* L)
	{
		image_frame_rgbx* ptr = get_image_argument(L, 1);
		ptr->put_ref();
		return 0;
	}

	packet_processor* get_processor(lua_State* L)
	{
		packet_processor* p;
		lua_pushlightuserdata(L, &dummyaddr);
		lua_gettable(L, LUA_REGISTRYINDEX);
		p = (packet_processor*)lua_touserdata(L, -1);
		lua_pop(L, 1);
		return p;
	}

	int get_output_size(lua_State* L)
	{
		packet_processor* p = get_processor(L);
		lua_pushnumber(L, p->get_width());
		lua_pushnumber(L, p->get_height());
		return 2;
	}

	int get_audio_rate(lua_State* L)
	{
		packet_processor* p = get_processor(L);
		lua_pushnumber(L, p->get_rate());
		return 1;
	}

	int read_image(lua_State* L)
	{
		//TODO: Implement
		return 0;
	}

	int render_text(lua_State* L)
	{
		//TODO: Implement
		return 0;
	}

	int suspend_audio(lua_State* L)
	{
		//TODO: Implement
		return 0;
	}

	int emit_audio(lua_State* L)
	{
		//TODO: Implement
		return 0;
	}

	int skip_audio(lua_State* L)
	{
		//TODO: Implement
		return 0;
	}

	int blank_image(lua_State* L)
	{
		uint32_t width = get_numeric_argument<uint32_t>(L, 1);
		uint32_t height = get_numeric_argument<uint32_t>(L, 2);
		image_frame_rgbx* copy = new image_frame_rgbx(width, height);
		unsigned char* pixels = copy->get_pixels();
		for(size_t i = 0; i < width * height; i++)
			pixels[4 * i + 3] = 255;
		push_frame(L, copy);
		copy->put_ref();
		return 1;
	}

	int frame_copyrect(lua_State* L)
	{
		//TODO: Implement
		return 0;
	}

	int frame_composite(lua_State* L)
	{
		//TODO: Implement
		return 0;
	}

	int frame_scalealpha(lua_State* L)
	{
		image_frame_rgbx* ptr = get_image_argument(L, 1);
		double scale = get_numeric_argument<double>(L, 2);
		uint32_t width = ptr->get_width();
		uint32_t height = ptr->get_height();
		unsigned char* pixels = ptr->get_pixels();
		for(size_t i = 0; i < width * height; i++) {
			double newalpha = scale * pixels[4 * i + 3];
			newalpha = (newalpha > 255) ? 255 : (newalpha < 0) ? 0 : newalpha;
			pixels[4 * i + 3] = (uint8_t)newalpha;
		}
		return 0;
	}

	int frame_forcealpha(lua_State* L)
	{
		image_frame_rgbx* ptr = get_image_argument(L, 1);
		uint8_t alpha = get_numeric_argument<uint8_t>(L, 2);
		bool key_flag = false;
		uint8_t key_r = 0;
		uint8_t key_g = 0;
		uint8_t key_b = 0;
		if(lua_type(L, 3) != LUA_TNONE) {
			key_flag = true;
			key_r = get_numeric_argument<uint8_t>(L, 3);
			key_g = get_numeric_argument<uint8_t>(L, 4);
			key_b = get_numeric_argument<uint8_t>(L, 5);
		}
		uint32_t width = ptr->get_width();
		uint32_t height = ptr->get_height();
		unsigned char* pixels = ptr->get_pixels();
		for(size_t i = 0; i < width * height; i++)
			if(!key_flag || (pixels[4 * i + 0] == key_r && pixels[4 * i + 1] == key_g &&
				pixels[4 * i + 2] == key_b))
				pixels[4 * i + 3] = alpha;
		return 0;
	}

	int frame_resize_foo(lua_State* L)
	{
		image_frame_rgbx* ptr = get_image_argument(L, 1);
		uint32_t width = get_numeric_argument<uint32_t>(L, 2);
		uint32_t height = get_numeric_argument<uint32_t>(L, 3);
		image_frame_rgbx* target = new image_frame_rgbx(width, height);
		std::string resizer_name = lua_tostring(L, lua_upvalueindex(1));
		resizer& r = resizer_factory::make_by_type(resizer_name);
		r(target->get_pixels(), width, height, ptr->get_pixels(), ptr->get_width(), ptr->get_height());
		delete &r;
		push_frame(L, target);
		target->put_ref();
		return 1;
	}

	int frame_pset(lua_State* L)
	{
		image_frame_rgbx* ptr = get_image_argument(L, 1);
		uint32_t x = get_numeric_argument<uint32_t>(L, 2);
		uint32_t y = get_numeric_argument<uint32_t>(L, 3);
		uint8_t r = get_numeric_argument<uint8_t>(L, 4);
		uint8_t g = get_numeric_argument<uint8_t>(L, 5);
		uint8_t b = get_numeric_argument<uint8_t>(L, 6);
		uint8_t a = 255;
		if(lua_type(L, 7) != LUA_TNONE)
			a = (uint8_t)lua_tonumber(L, 7);
		uint32_t width = ptr->get_width();
		uint32_t height = ptr->get_height();
		if(x < 0 || x >= width || y < 0 || y >= height)
			raise_error(L, "Coordinates out of range in pset");
		uint8_t* data = ptr->get_pixels();
		data[4 * width * y + 4 * x + 0] = r;
		data[4 * width * y + 4 * x + 0] = g;
		data[4 * width * y + 4 * x + 0] = b;
		data[4 * width * y + 4 * x + 0] = a;
		return 0;
	}

	int frame_pget(lua_State* L)
	{
		image_frame_rgbx* ptr = get_image_argument(L, 1);
		uint32_t x = get_numeric_argument<uint32_t>(L, 2);
		uint32_t y = get_numeric_argument<uint32_t>(L, 3);
		uint32_t width = ptr->get_width();
		uint32_t height = ptr->get_height();
		if(x < 0 || x >= width || y < 0 || y >= height)
			raise_error(L, "Coordinates out of range in pget");
		uint8_t* data = ptr->get_pixels();
		lua_pushnumber(L, data[4 * width * y + 4 * x + 0]);
		lua_pushnumber(L, data[4 * width * y + 4 * x + 1]);
		lua_pushnumber(L, data[4 * width * y + 4 * x + 2]);
		lua_pushnumber(L, data[4 * width * y + 4 * x + 3]);
		return 4;
	}

	int push_image(lua_State* L)
	{
		packet_processor* p = get_processor(L);
		uint64_t ts = get_numeric_argument<uint64_t>(L, 1);
		image_frame_rgbx* ptr = get_image_argument(L, 2);
		ptr->get_ref();
		p->inject_frame(ts, ptr);
		return 0;
	}

	int frame_get_size(lua_State* L)
	{
		image_frame_rgbx* ptr = get_image_argument(L, 1);
		lua_pushnumber(L, ptr->get_width());
		lua_pushnumber(L, ptr->get_height());
		return 2;
	}

	int frame_copy(lua_State* L)
	{
		image_frame_rgbx* ptr = get_image_argument(L, 1);
		image_frame_rgbx* copy = new image_frame_rgbx(*ptr);
		push_frame(L, copy);
		copy->put_ref();
		return 1;
	}

#define FRAME_OP(X) do { lua_pushstring(L, #X ); lua_pushcfunction(L, frame_##X ); lua_settable(L, -3); \
	} while(0);
#define FRAME_OP_ARG(X, Y) do { lua_pushstring(L, #X ); lua_pushstring(L, Y); lua_pushcclosure(L, frame_##X , 1); \
	lua_settable(L, -3); } while(0);

	void perform_ops_load(lua_State* L)
	{
		//Now the table of ops is topmost in stack. Load some ops into it.
		FRAME_OP(get_size);
		FRAME_OP(copy);
		FRAME_OP(copyrect);
		FRAME_OP(composite);
		FRAME_OP(scalealpha);
		FRAME_OP(forcealpha);
		FRAME_OP(pget);
		FRAME_OP(pset);
		std::list<std::string> r = get_resizer_list2();
		for(std::list<std::string>::iterator i = r.begin(); i != r.end(); i++)
			FRAME_OP_ARG(resize_foo, i->c_str());
	}

#define GLOBAL_OP(X) lua_pushstring(L, #X ); lua_pushcfunction(L, X ); lua_settable(L, -3);

	void perform_global_ops_load(lua_State* L)
	{
		//Now the table of ops is topmost in stack. Load some ops into it.
		GLOBAL_OP(get_output_size);
		GLOBAL_OP(push_image);
		GLOBAL_OP(get_audio_rate);
		GLOBAL_OP(read_image);
		GLOBAL_OP(render_text);
		GLOBAL_OP(suspend_audio);
		GLOBAL_OP(emit_audio);
		GLOBAL_OP(skip_audio);
	}

	void push_frame(lua_State* L, image_frame_rgbx* frame)
	{
		image_frame_rgbx** ptr = (image_frame_rgbx**)lua_newuserdata(L, sizeof(image_frame_rgbx*));
		frame->get_ref();
		*ptr = frame;
		lua_newtable(L);
		lua_pushstring(L, "__gc");
		lua_pushcfunction(L, gc_frame);
		lua_settable(L, -3);
		lua_pushstring(L, "__index");
		lua_newtable(L);
		perform_ops_load(L);
		lua_settable(L, -3);
		//Load the metatable.
		lua_setmetatable(L, -2);
	}
}

Lua::Lua()
{
	internal* i = new internal;
	internal_state = i;
	i->script_loaded = false;
	i->L = luaL_newstate();
	if(!i->L)
		throw std::bad_alloc();
	luaL_openlibs(i->L);
	//Load table for operations.
	lua_pushstring(i->L, "streamtools");
	lua_newtable(i->L);
	perform_global_ops_load(i->L);
	lua_settable(i->L, LUA_GLOBALSINDEX);
}

Lua::~Lua()
{
	internal* i = (internal*)internal_state;
	lua_close(i->L);
	delete i;
}

namespace
{
	int lua_fault_handler(lua_State* L)
	{
		std::cerr << "Lua runtime error: " << lua_tostring(L, 1) << std::endl;
		int i = 0;
		lua_Debug stackframe;
		std::cerr << "Stack traceback:" << std::endl;
		while(lua_getstack(L, i, &stackframe)) {
			lua_getinfo(L, "nSl", &stackframe);
			std::cerr << "Frame #" << i << ": " << stackframe.short_src << ":" << stackframe.currentline
				<< " " << (stackframe.name ? stackframe.name : "?") <<  std::endl;
			i++;
		}
		return 1;
	}
}

void Lua::load_script(const std::string& name)
{
	internal* i = (internal*)internal_state;
	lua_pushcfunction(i->L, lua_fault_handler);
	int r = luaL_loadfile(i->L, name.c_str());
	switch(r) {
	case LUA_ERRRUN:
		throw std::runtime_error("Can't load script: Runtime fault");
	case LUA_ERRSYNTAX:
		throw std::runtime_error("Can't load script: Syntax error");
	case LUA_ERRMEM:
		throw std::runtime_error("Can't load script: Out of memory");
	case LUA_ERRERR:
		throw std::runtime_error("Can't load script: Double fault");
	case LUA_ERRFILE:
		throw std::runtime_error("Can't load script: Can't read file");
	}
	r = lua_pcall(i->L, 0, 0, -2);
	switch(r) {
	case LUA_ERRRUN:
		throw std::runtime_error("Can't initialize script: Runtime fault");
	case LUA_ERRSYNTAX:
		throw std::runtime_error("Can't initialize script: Syntax error");
	case LUA_ERRMEM:
		throw std::runtime_error("Can't initialize script: Out of memory");
	case LUA_ERRERR:
		throw std::runtime_error("Can't initialize script: Double fault");
	case LUA_ERRFILE:
		throw std::runtime_error("Can't initialize script: Can't read file");
	}
	lua_pop(i->L, 1);
	i->script_loaded = true;
}



void Lua::frame_callback(uint64_t ts, image_frame_rgbx* f)
{
	internal* i = (internal*)internal_state;
	lua_pushcfunction(i->L, lua_fault_handler);
	lua_getglobal(i->L, "process_frame");
	lua_pushnumber(i->L, ts);
	push_frame(i->L, f);
	int r = lua_pcall(i->L, 2, 0, -4);
	switch(r) {
	case LUA_ERRRUN:
		throw std::runtime_error("Can't run frame handler: Runtime fault");
	case LUA_ERRSYNTAX:
		throw std::runtime_error("Can't run frame handler: Syntax error");
	case LUA_ERRMEM:
		throw std::runtime_error("Can't run frame handler: Out of memory");
	case LUA_ERRERR:
		throw std::runtime_error("Can't run frame handler: Double fault");
	case LUA_ERRFILE:
		throw std::runtime_error("Can't run frame handler: Can't read file");
	}
	lua_pop(i->L, 1);
	lua_gc(i->L, LUA_GCCOLLECT, 0);
}

bool Lua::enabled()
{
	internal* i = (internal*)internal_state;
	return i->script_loaded;
}

void Lua::set_processor(packet_processor& proc)
{
	processor = &proc;
	internal* i = (internal*)internal_state;
	lua_pushlightuserdata(i->L, &dummyaddr);
	lua_pushlightuserdata(i->L, processor);
	lua_settable(i->L, LUA_REGISTRYINDEX);
}
