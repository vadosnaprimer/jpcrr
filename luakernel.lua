--
-- Copyright 2009 Ilari Liusvaara
--
-- Licenced under GNU GPL v2.
--

-- Exported tables:
--	- args
--		Contains user-level arguments.
--
-- Exported functions:
--	- All lua standard functions in tables main, "coroutine", "string" and "table".
--	- modulus_split(number num, number...)
--		Returns quotents and quotent remainders of number divided by given dividers.
--		The number of results returned is equal to number of numbers passed. The
--		dividers must be in decreasing order and all dividers must be nonzero and
--		should be >1.
--	- assert(object ret, object err)
--		If ret is considered true, return ret. Otherwise raise err as error. Exception
--		if err is nil too, then ret is returned anyway.
--	- bit.none(number...)
--		Returns 48-bit number that has those bits set that are set in none of its
--		arguments.
--	- bit.any(number...)
--		Returns 48-bit number that has those bits set that are set in any of its
--		arguments.
--	- bit.parity(number...)
--		Returns 48-bit number that has those bits set that are set in even number
--		of its arguments.
--	- bit.any(number...)
--		Returns 48-bit number that has those bits set that are set in all of its
--		arguments.
--	- bit.lshift(number num, number shift)
--		Returns 48 rightmost bits of num shifted by shift places left. Going outside
--		shift range 0-47 produces unpredicatable results.
--	- bit.rshift(number num, number shift)
--		Returns 48 rightmost bits of num shifted by shift places right. Going outside
--		shift range 0-47 produces unpredicatable results.
--	- bit.arshift(number num, number shift)
--		Returns 48 rightmost bits of num shifted by shift places right and bit 47
--		value copied to all incoming bits. Going outside shift range 0-47 produces
--		unpredicatable results.
--	- bit.add(number...)
--		Returns sum of all passed numbers modulo 2^48.
--	- bit.addneg(number...)
--		Returns 0 minus sum of all passed numbers modulo 2^48.
--	- bit.addalt(number...)
--		Returns sum of odd arguments (first is odd) minus sum of even arguments modulo
--		 2^48.
--	- bit.tohex(number num)
--		Returns hexadecimal string representation of number.
--	- jpcrr.wait_message()
--		Waits for message (string) to come from message queue. Reutrns nil if interrupted.
--	- jpcrr.poll_message()
--		Checks if there is message from message queue. Reutrns message if there is one,
--		nil if none.
--	- jpcrr.wait_pc_stop()
--		Waits for PC execution to stop (but does not attempt to actually stop it).
--		If script is in frame hold, the frame hold is released.
--		Returns true if execution got stopped, false if interrupted.
--	- jpcrr.pc_running()
--		Returns true if PC is running.
--	- jpcrr.clock_time()
--		Returns current time or nil if no PC.
--	- jpcrr.pc_connected()
--		Returns true if PC is connected.
--	- jpcrr.wait_pc_attach()
--		Wait for PC to attach.
--	- jpcrr.next_frame()
--		Wait for next frame output hold.
--	- jpcrr.in_frame_hold()
--		Returns true if in frame hold, false otherwise.
--	- jpcrr.keypressed(number key)
--		Return true if key is pressed, else false.
--	- jpcrr.wait_vga()
--		Waits for VGA to enter frame hold mode. Frame hold happens once per frame.
--	- jpcrr.release_vga()
--		Allow VGA to exit frame hold mode. Wait for frame hold first.
--	- jpcrr.vga_resolution()
--		Return VGA x and y resolutions. -1x-1 or 0x0 is returned if no valid resolution.
--		Should only be called during frame hold.
--	- jpcrr.hud.left_gap(number flags, number gap)
--		Set left gap for HUD. If flags has bit 0 (1) set, draw on screen, if bit
--		1 (2) is set, dump to video dump.
--	- jpcrr.hud.right_gap(number flags, number gap)
--		Set right gap for HUD. If flags has bit 0 (1) set, draw on screen, if bit
--		1 (2) is set, dump to video dump.
--	- jpcrr.hud.top_gap(number flags, number gap)
--		Set top gap for HUD. If flags has bit 0 (1) set, draw on screen, if bit
--		1 (2) is set, dump to video dump.
--	- jpcrr.hud.bottom_gap(number flags, number gap)
--		Set bottom gap for HUD. If flags has bit 0 (1) set, draw on screen, if bit
--		1 (2) is set, dump to video dump.
--	- jpcrr.hud.white_solid_box(number flags, number x, number y, number w, number h)
--		Draw with solid opaque box.
--	- jpcrr.hud.box(number flags, number x, number y, number w, number h, number linethick,
--			number lineRed, number lineGreen, number lineBlue, number lineAlpha,
--			number fillRed, number fillGreen, number fillBlue, number fillAlpha)
--		Draw box with specified size, border line thickness, line color and fill color.
--	- jpcrr.hud.circle(number flags, number x, number y, number r, number linethick,
--			number lineRed, number lineGreen, number lineBlue, number lineAlpha,
--			number fillRed, number fillGreen, number fillBlue, number fillAlpha)
--		Draw circle with specified size, border line thickness, line color and fill color.
--	- jpcrr.hud.bitmap(number flags, number x, number y, string bmap,
--			number fgRed, number fgGreen, number fgBlue, number fgAlpha,
--			number bgRed, number bgGreen, number bgBlue, number bgAlpha)
--		Draw bitmap with specified foreground color and background color.
--	- jpcrr.hud.bitmap_binary(number flags, number x, number y, string bmap,
--			number fgRed, number fgGreen, number fgBlue, number fgAlpha,
--			number bgRed, number bgGreen, number bgBlue, number bgAlpha)
--		Draw binary bitmap with specified foreground color and background color.
--	- jpcrr.joystick_state()
--		Returns nil if no joystick. Otherwise returns hold times for all four axis (numeric)
--		followed by button states (boolean).
--	- jpcrr.keyboard_leds()
--		Returns nil if no keyboard, false if LED status is unknown. Otherwise returns three
--		booleans, first being state of num lock, second being state of caps lock and third
--		being state of scroll lock.
--	- jpcrr.component_encode(table components)
--		Return component encoding for specified components.
--	- jpcrr.component_decode(string line)
--		Return component decoding for specified line, nil/nil if it doesn't encode
--		anything, nil/string if parse error occurs (the error is the second return).
--	- jpcrr.save_state(string name)
--		Savestate into specified file. Returns name used.
--	- jpcrr.save_movie(string name)
--		Save movie into specified file. Returns name used.
--	- jpcrr.load_state_normal(string name)
--		Load specified savestate. Returns name used.
--	- jpcrr.load_state_preserve_events(string name)
--		Load specified savestate, preserving events. Returns name used.
--	- jpcrr.load_state_movie(string name)
--		Load specified savestate as movie. Returns name used.
--	- jpcrr.assemble()
--		Open system settings dialog.
--	- jpcrr.change_authors()
--		Open change authors dialog.
--	- jpcrr.exit = function()
--		Exit the Lua VM.
--	- jpcrr.ram_dump(string name, boolean binary)
--		Dump PC memory to specified file. If binary is true, dump is binary, otherwise
--		textual hexadecimal dump.
--	- jpcrr.write_byte(number addr, number value)
--		Write byte to specified physical address.
--	- jpcrr.write_word(number addr, number value)
--		Write word to specified physical address (little endian).
--	- jpcrr.write_dword(number addr, number value)
--		Write dword to specified physical address (little endian).
--	- jpcrr.read_byte(number addr)
--		Return byte from specified physical address.
--	- jpcrr.read_word(number addr)
--		Return word from specified physical address (little endian).
--	- jpcrr.read_dword(number addr)
--		Return dword from specified physical address (little endian).
--	- jpcrr.timed_trap(number nsecs)
--		Set trap after specified number of nanoseconds. Use nil as nsecs to disable.
--	- jpcrr.vretrace_start_trap(boolean is_on)
--		Set trap on vretrace start on/off.
--	- jpcrr.vretrace_end_trap(boolean is_on)
--		Set trap on vretrace end on/off.
--	- jpcrr.pc_start()
--		Start PC execution.
--	- jpcrr.pc_stop()
--		Stop PC execution.
--	- jpcrr.set_pccontrol_pos(number x, number y)
--		Set position of PCControl window.
--	- jpcrr.set_luaplugin_pos(number x, number y)
--		Set position of LuaPlugin window.
--	- jpcrr.set_pcmonitor_pos(number x, number y)
--		Set position of PCMonitor window.
--	- jpcrr.set_pcstartstoptest_pos(number x, number y)
--		Set position of PCStartStopTest window.
--	- jpcrr.set_virtualkeyboard_pos(number x, number y)
--		Set position of VirtualKeyboard window.
--	- jpcrr.stringlessthan(String x, String y)
--		Return true if x is before y in codepoint lexical order, otherwise false.
--	- jpcrr.screenshot(boolean include_hud)
--		Take screen shot (Requires monitor). If include_hud is true, include HUD
--		(as shown on screen). Note that this should only be called during frame
--		hold or results are pretty much undefined.
--
--	I/O functions have the following conventions. If function returns any real data, the first
--	return value returns this data or is nil. Otherwise first return value is true or false.
--	If first return value is nil or false, then the second return value gives textual error
--	message for failed operation, or is nil if EOF occured before anything was read.
--
--	Unlink, rename and mkdir don't follow this pattern. They just return true/false to signal
--	success or failure.
--
--	Specifying nil as name of file results random filename being used (it even works with unlink,
--	mkdir, rename and read-only access, but doesn't make any sense there).
--
--	Class: BinaryFile:
--		Binary file for RO or RW access. Methods are as follows:
--		- name()
--			Return name of file.
--		- length()
--			Return length of file.
--		- set_length(number length)
--			Truncate file to specified length
--		- read(number offset, number length)
--			Read up to length bytes from offset.
--		- write(number offset, string content)
--			Write content to specified offset.
--		- close()
--			Close the file.
--	Class: BinaryInput:
--		Binary file for sequential input. Methods are as follows:
--		- name()
--			Return name of file.
--		- four_to_five()
--			Return BinaryInput that is four to five decoding of this stream.
--		- text()
--			Return stream as TextInput.
--		- inflate()
--			Return BinaryInput that is inflate of this stream.
--		- read(number bytes)
--			Read up to bytes bytes from file.
--		- read()
--			Read the entiere file at once.
--		- close()
--			Close the file.
--		Character set for binary files is Latin-1.
--
--	Class: BinaryOutput:
--		Binary file for sequential output. Methods are as follows:
--		- name()
--			Return name of file.
--		- four_to_five()
--			Return BinaryOutput that writes four to five encoded output to this stream.
--		- text()
--			Return stream as TextOutput.
--		- deflate()
--			Return BinaryOutput that writes deflate output to this stream.
--		- write(string content)
--			Write string to file.
--		- close()
--			Close the file.
--		Character set for binary files is Latin-1.
--
--	Class: TextInput:
--		- name()
--			Return name of file.
--		- read()
--			Read line from file.
--		- read_component()
--			Read next componented line into array.
--		- lines()
--			Line iterator function.
--		- close()
--			Close the file.
--	Character set for text files is UTF-8.
--
--	Class: TextOutput:
--		- name()
--			Return name of file.
--		- write(string line)
--			Write line line to file.
--		- write_component(table components)
--			Write componented line.
--		- close()
--			Close the file.
--		Character set for text files is UTF-8.
--
--	Class ArchiveIn:
--		- member(string name)
--			Open substream for member name. The methods are the same as for io.opentextin.
--		- member_binary(string name)
--			Open binary (four to five) substream for member name. The methods are the same as
--			for io.openbinaryin.
--		- close()
--			Close the file. Any opened substreams are invalidated.
--		- member_list()
--			Return table listing all members of archive (sorted by name).
--	Class ArchiveOut:
--		- member(string name)
--			Open substream for member name. The methods are the same as for io.opentextout. Note that
--			previous member must be closed before next can open.
--		- member_binary(string name)
--			Open binary (four to five) substream for member name. The methods are the same as
--			for io.openbinaryout. Note that previous substream must be closed before next can open.
--		- commit()
--			Commit the file. No substream may be open. Closes the file.
--		- rollback()
--			Rollback the file. No substream may be open. Closes the file.
--
--	- io.open(string name, string mode) -> BinaryFile
--		Open file named @name in specified mode. The mode can be 'r' (read only) or 'rw' (read and
--		write).
--	- io.open_read(string name) -> BinaryInput
--		Open file named @name as binary input stream.
--	- io.open_write(string name) -> BinaryOutput
--		Open file named @name as binary input stream.
--	- io.open_arch_read(string name) -> ArchiveIn
--		Open file named @name as input archive.
--	- io.open_arch_write(string name) -> ArchiveOut
--		Open file named @name as output archive.
--	- io.mkdir(string name)
--		Create directory name. Returns name created on success, nil on failure.
--	- io.unlink(string name)
--		Delete file/directory name. Returns name deleted on success, nil on failure.
--	- io.rename(string old, string new)
--		Rename file old -> new. Returns old, new on success, nil on failure.
--	- io.transform.text()
--		Returns function that calls text() method of its parameter.
--	- io.transform.four_to_five()
--		Returns function that calls four_to_five() method of its parameter.
--	- io.transform.deflate()
--		Returns function that calls deflate() method of its parameter.
--	- io.transform.inflate()
--		Returns function that calls inflate() method of its parameter.
--	- io.dotransform(object obj, function...)
--		Call specified functions on specified object. If any function fails (first argument nil
--		or false), call close method on preceeding object. Otherwise call specified functions
--		chained left to right and return the result.
--	- io.dotransform2(object obj, string err, function...)
--		Similar to dotransform except if obj is nil or false, returns obj, err.
--




local handle, err, chunk, indication, k, v;

local loadmod = loadmodule;
loadmodule = nil;

local export_module_in = function(tab, modname, prefix)
	local fun = loadmod(modname);
	for k, v in pairs(fun) do
		tab[(prefix or "") .. k] = v;
	end
end

jpcrr = {};
jpcrr.hud = {};
bit = {};
io = {};

export_module_in(jpcrr, "org.jpc.luaextensions.Base");
export_module_in(jpcrr, "org.jpc.luaextensions.InputDevices");
export_module_in(jpcrr, "org.jpc.luaextensions.ComponentCoding", "component_");
export_module_in(bit, "org.jpc.luaextensions.Bitops");

-- Few misc functions.
assert = function(val, err)
	if (not val) and err then
		error(err);
	end
	return val;
end

modulus_split = function(number, ...)
	local dividers = {...};
	local results = {};
	local rem;

	for k, v in ipairs(dividers) do
		rem = number % v;
		table.insert(results, (number - rem) / v);
		number = rem;
	end

	table.insert(results, number);
	return unpack(results);
end

local getmtable = getmetatable;
local toString = tostring;
local inject_binary_file;
local inject_binary_input;
local inject_binary_output;
local inject_text_input;
local inject_text_output;
local inject_archive_input;
local inject_archive_output;

-- Class member injectors.
inject_binary_file = function(obj, name)
	local _name = name;
	getmtable(obj).name = function(obj)
		return _name;
	end
	getmtable(obj).__index = function(tab, name)
		local x = getmtable(obj)[name];
		if x then
			return x;
		end
		error("Invalid method " .. name .. " for BinaryFile");
	end
	return obj;
end

inject_binary_input = function(obj, underlying, name)
	local _name = name;
	local old_four_to_five = getmtable(obj).four_to_five;
	local old_inflate = getmtable(obj).inflate;
	local old_text = getmtable(obj).text;
	local old_read = getmtable(obj).read;
	local old_close = getmtable(obj).close;
	local underlying_object = underlying;

	getmtable(obj).name = function(obj)
		return _name;
	end
	getmtable(obj).four_to_five = function(obj)
		local res, err;
		res, err = old_four_to_five(obj);
		if not res then
			return res, err;
		end
		return inject_binary_input(res, obj, "four-to-five<" .. _name .. ">");
	end
	getmtable(obj).inflate = function(obj)
		local res, err;
		res, err = old_inflate(obj);
		if not res then
			return res, err;
		end
		return inject_binary_input(res, obj, "inflate<" .. _name .. ">");
	end
	getmtable(obj).text = function(obj)
		local res, err;
		res, err = old_text(obj);
		if not res then
			return res, err;
		end
		return inject_text_input(res, obj, "text<" .. _name .. ">");
	end
	getmtable(obj).read = function(obj, toread)
		if toread then
			return old_read(obj, toread);
		else
			local res = "";
			local ret, err;
			while true do
				ret, err = old_read(obj, 16384);
				if not ret then
					if not err then
						return res;
					end
					return nil, err;
				end
				res = res .. ret;
			end
		end
	end
	getmtable(obj).close = function(obj)
		local ret, err, ret2, err2;
		ret, err = old_close(obj);
		if underlying_object then ret2, err2 = underlying_object:close(); end
		if ret and not ret2 then
			err = err2;
			ret = ret2;
		end
		return ret, err;
	end
	getmtable(obj).__index = function(tab, name)
		local x = getmtable(obj)[name];
		if x then
			return x;
		end
		error("Invalid method " .. name .. " for BinaryInput");
	end
	return obj;
end

inject_binary_output = function(obj, underlying, name)
	local _name = name;
	local old_four_to_five = getmtable(obj).four_to_five;
	local old_deflate = getmtable(obj).deflate;
	local old_text = getmtable(obj).text;
	local old_close = getmtable(obj).close;
	local underlying_object = underlying;

	getmtable(obj).name = function(obj)
		return _name;
	end
	getmtable(obj).four_to_five = function(obj)
		local res, err;
		res, err = old_four_to_five(obj);
		if not res then
			return res, err;
		end
		return inject_binary_output(res, obj, "four-to-five<" .. _name .. ">");
	end
	getmtable(obj).deflate = function(obj)
		local res, err;
		res, err = old_deflate(obj);
		if not res then
			return res, err;
		end
		return inject_binary_output(res, obj, "deflate<" .. _name .. ">");
	end
	getmtable(obj).text = function(obj)
		local res, err;
		res, err = old_text(obj);
		if not res then
			return res, err;
		end
		return inject_text_output(res, obj, "text<" .. _name .. ">");
	end
	getmtable(obj).close = function(obj)
		local ret, err, ret2, err2;
		ret, err = old_close(obj);
		if underlying_object then ret2, err2 = underlying_object:close(); end
		if ret and not ret2 then
			err = err2;
			ret = ret2;
		end
		return ret, err;
	end
	getmtable(obj).__index = function(tab, name)
		local x = getmtable(obj)[name];
		if x then
			return x;
		end
		error("Invalid method " .. name .. " for BinaryOutput");
	end
	return obj;
end

inject_text_input = function(obj, underlying, name)
	local _name = name;
	local old_close = getmtable(obj).close;
	local underlying_object = underlying;

	getmtable(obj).lines = function(obj)
		return function(state, prevline)
			return state:read();
		end, obj, nil;
	end
	getmtable(obj).name = function(obj)
		return _name;
	end
	getmtable(obj).close = function(obj)
		local ret, err, ret2, err2;
		ret, err = old_close(obj);
		if underlying_object then ret2, err2 = underlying_object:close(); end
		if ret and not ret2 then
			err = err2;
			ret = ret2;
		end
		return ret, err;
	end
	getmtable(obj).__index = function(tab, name)
		local x = getmtable(obj)[name];
		if x then
			return x;
		end
		error("Invalid method " .. name .. " for TextInput");
	end
	return obj;
end

inject_text_output = function(obj, underlying, name)
	local _name = name;
	local old_close = getmtable(obj).close;
	local underlying_object = underlying;

	getmtable(obj).name = function(obj)
		return _name;
	end
	getmtable(obj).close = function(obj)
		local ret, err, ret2, err2;
		ret, err = old_close(obj);
		if underlying_object then ret2, err2 = underlying_object:close(); end
		if ret and underlying_object then
			err = err2;
			ret = ret2;
		end
		return ret, err;
	end
	getmtable(obj).__index = function(tab, name)
		local x = getmtable(obj)[name];
		if x then
			return x;
		end
		error("Invalid method " .. name .. " for TextOutput");
	end
	return obj;
end

inject_archive_input = function(obj, name)
	local _name = name;
	local old_member = getmtable(obj).member;
        local old_member_list = getmtable(obj).member_list;
	getmtable(obj).member = function(obj, member)
		local res, err;
		res, err = old_member(obj, member);
		if not res then
			return res, err;
		end
		return inject_binary_input(res, nil, _name .. "[" .. member .. "]");
	end
	getmtable(obj).name = function(obj)
		return _name;
	end
	getmtable(obj).member_list = function(obj)
		local tab = old_member_list(obj);
		if tab then table.sort(tab, jpcrr.stringlessthan); end
		return tab;
	end
	getmtable(obj).__index = function(tab, name)
		local x = getmtable(obj)[name];
		if x then
			return x;
		end
		error("Invalid method " .. name .. " for ArchiveInput");
	end
	return obj;
end

inject_archive_output = function(obj, name)
	local _name = name;
	local old_member = getmtable(obj).member;
	getmtable(obj).member = function(obj, member)
		local res, err;
		res, err = old_member(obj, member);
		if not res then
			return res, err;
		end
		return inject_binary_output(res, nil, _name .. "[" .. member .. "]");
	end
	getmtable(obj).name = function(obj)
		return _name;
	end
	getmtable(obj).__index = function(tab, name)
		local x = getmtable(obj)[name];
		if x then
			return x;
		end
		error("Invalid method " .. name .. " for ArchiveOutput");
	end
	return obj;
end


-- Redefined print.
do
	local rprint = print_console_msg;
	print_console_msg = nil;
	print = function(...)
		local x = "";
		local y = {...};
		local i;
		for i = 1,#y do
			if i > 1 then
				x = x .. "\t" .. toString(y[i]);
			else
				x = toString(y[i]);
			end
		end
		rprint(x);
	end
	print_console_msg = nil;
end

-- I/O routines.
local stringfind = string.find;
local randname = loadmod("org.jpc.luaextensions.DelayedDelete").random_temp_name;
local path = args["luapath"] or ".";
local toresourcename = function(resname)
	if not resname then
		return randname(path .. "/", "luatemp-");
	end

	if not stringfind(resname, "[%d%l%u_%-]") then
		error("Bad resource name (case 1): " .. resname);
	end
	if stringfind(resname, "^/") then
		error("Bad resource name (case 2): " .. resname);
	end
	if stringfind(resname, "%.%.") then
		error("Bad resource name (case 3): " .. resname);
	end
	if stringfind(resname, "\\") then
		error("Bad resource name (case 4): " .. resname);
	end

	return resname, path .. "/" .. resname;
end


do
	local openbinin = loadmod("org.jpc.luaextensions.BinaryInFile").open;
	local openbinout = loadmod("org.jpc.luaextensions.BinaryOutFile").open;
	local openarchin = loadmod("org.jpc.luaextensions.ArchiveIn").open;
	local openarchout = loadmod("org.jpc.luaextensions.ArchiveOut").open;
	local openbinary = loadmod("org.jpc.luaextensions.BinaryFile").open;

	local baseFS = loadmod("org.jpc.luaextensions.BaseFSOps");
	local mkdir = baseFS.mkdir;
	local unlink = baseFS.unlink;
	local rename = baseFS.rename;

	local getmtable = getmetatable;

	loadfile = function(_script)
		local file, file2, err, content;
		local x, y;
		x, y = toresourcename(_script);
		file, err = openbinin(y, "r");
		if not file then
			return nil, "Can't open " .. _script .. ": " .. err;
		end
		file2, err = file:text();
		if not file2 then
			return nil, "Can't transform " .. _script .. ": " .. err;
		end
		content = "";
		line = file2:read();
		while line do
			content = content .. line .. "\n";
			line = file2:read();
		end
		file2:close();
		file:close();
		return loadstring(content, _script);
	end

	io.open = function(name, mode)
		local _name;
		local res, err;
		local y;
		_name, y = toresourcename(name);
		res, err = openbinary(y, mode);
		if not res then
			return res, err;
		end
		return inject_binary_file(res, _name);
	end

	io.open_arch_read = function(name)
		local _name = name;
		local res, err;
		local y;
		_name, y = toresourcename(name);
		res, err = openarchin(y);
		if not res then
			return res, err;
		end
		return inject_archive_input(res, _name);
	end

	io.open_arch_write = function(name)
		local _name = name;
		local res, err;
		local y;
		_name, y = toresourcename(name);
		res, err = openarchout(y);
		if not res then
			return res, err;
		end
		return inject_archive_output(res, _name);
	end

	io.open_read = function(name)
		local _name = name;
		local res, err;
		local y;
		_name, y = toresourcename(name);
		res, err = openbinin(y);
		if not res then
			return res, err;
		end
		return inject_binary_input(res, nil, _name);
	end

	io.open_write = function(name)
		local _name = name;
		local res, err;
		local y;
		_name, y = toresourcename(name);
		res, err = openbinout(y);
		if not res then
			return res, err;
		end
		return inject_binary_output(res, nil, _name);
	end

	io.mkdir = function(name)
		local _name, y;
		_name, y = toresourcename(name);
		if mkdir(y) then
			return _name;
		else
			return nil;
		end
	end

	io.unlink = function(name)
		local _name, y;
		_name, y = toresourcename(name);
		if unlink(y) then
			return _name;
		else
			return nil;
		end
	end

	io.rename = function(name1, name2)
		local _name, y;
		local _name2, y2;
		_name, y = toresourcename(name1);
		_name2, y2 = toresourcename(name2);
		if rename(y, y2) then
			return _name, _name2;
		else
			return nil;
		end
	end

	io.transform = {};

	io.transform.text = function()
		return function(obj)
			return obj:text();
		end
	end

	io.transform.four_to_five = function()
		return function(obj)
			return obj:four_to_five();
		end
	end

	io.transform.inflate = function()
		return function(obj)
			return obj:inflate();
		end
	end

	io.transform.deflate = function()
		return function(obj)
			return obj:deflate();
		end
	end

	io.dotransform = function(obj, ...)
		local todo = {...};
		local k, v;
		local obj2, err;
		for k, v in ipairs(todo) do
			obj2, err = v(obj);
			if not obj2 then
				obj:close();
				return nil, err;
			end
			obj = obj2;
		end
		return obj;
	end

	io.dotransform2 = function(obj, err, ...)
		if not obj then
			return obj, err;
		end
		return io.dotransform(obj, err, ...);
	end

end

jpcrr.next_frame = function(name)
	while true do
		if not jpcrr.pc_connected() then
			jpcrr.wait_pc_attach();
		end
		if jpcrr.in_frame_hold() then
			jpcrr.release_vga();
		end
		if jpcrr.wait_vga() then
			return;
		end
	end
end


-- Various stuff built on top of ECI.
local invoke = jpcrr.invoke;
local invokecall = jpcrr.call;
local invokesync = jpcrr.invoke_synchronous;

jpcrr.save_state = function(name)
	local _name, _fname;
	_name, _fname = toresourcename(name);
	invokesync("state-save", {_fname});
	return _name;
end

jpcrr.save_movie = function(name)
	local _name, _fname;
	_name, _fname = toresourcename(name);
	invokesync("movie-save", {_name});
	return _name;
end

jpcrr.load_state_normal = function(name)
	local _name, _fname;
	_name, _fname = toresourcename(name);
	invokesync("state-load", {_name});
	return _name;
end

jpcrr.load_state_preserve_events = function(name)
	local _name, _fname;
	_name, _fname = toresourcename(name);
	invokesync("state-load-noevents", {_name});
	return _name;
end

jpcrr.load_state_movie = function(name)
	local _name, _fname;
	_name, _fname = toresourcename(name);
	invokesync("state-load-movie", {_name});
	return _name;
end

jpcrr.assemble = function()
	invokesync("pc-assemble");
end

jpcrr.change_authors = function()
	invokesync("change-authors");
end

jpcrr.ram_dump = function(name, binary)
	local _name, _fname;
	_name, _fname = toresourcename(name);
	if binary then
		invokesync("ram-dump-binary", {_name});
	else
		invokesync("ram-dump-text", {_name});
	end
	return _name;
end

jpcrr.hud.left_gap = function(f, g)
	invoke("hud-left-gap", {toString(f), toString(g)});
end

jpcrr.hud.right_gap = function(f, g)
	invoke("hud-right-gap", {toString(f), toString(g)});
end

jpcrr.hud.top_gap = function(f, g)
	invoke("hud-top-gap", {toString(f), toString(g)});
end

jpcrr.hud.bottom_gap = function(f, g)
	invoke("hud-bottom-gap", {toString(f), toString(g)});
end

jpcrr.hud.white_solid_box = function(f, x, y, w, h)
	invoke("hud-white-solid-box", {toString(f), toString(x), toString(y), toString(w), toString(h)});
end

jpcrr.hud.box = function(f, x, y, w, h, t, lr, lg, lb, la, fr, fg, fb, fa)
	invoke("hud-box", {toString(f), toString(x), toString(y), toString(w), toString(h),
		toString(t), toString(lr), toString(lg), toString(lb), toString(la), toString(fr),
		toString(fg), tostring(fb), toString(fa)});
end

jpcrr.hud.circle = function(f, x, y, r, t, lr, lg, lb, la, fr, fg, fb, fa)
	invoke("hud-circle", {toString(f), toString(x), toString(y), toString(r),
		toString(t), toString(lr), toString(lg), toString(lb), toString(la), toString(fr),
		toString(fg), tostring(fb), toString(fa)});
end

jpcrr.hud.bitmap = function(f, x, y, bmap, lr, lg, lb, la, fr, fg, fb, fa)
	invoke("hud-bitmap", {toString(f), toString(x), toString(y), bmap, toString(lr),
		toString(lg), toString(lb), toString(la), toString(fr), toString(fg), tostring(fb),
		toString(fa)});
end

jpcrr.hud.bitmap_binary = function(f, x, y, bmap, lr, lg, lb, la, fr, fg, fb, fa)
	invoke("hud-bitmap-binary", {toString(f), toString(x), toString(y), bmap, toString(lr),
		toString(lg), toString(lb), toString(la), toString(fr), toString(fg), tostring(fb),
		toString(fa)});
end

jpcrr.set_pccontrol_pos = function(x, y)
	invokesync("pccontrol-setwinpos", {toString(x), toString(y)});
end

jpcrr.set_luaplugin_pos = function(x, y)
	invokesync("luaplugin-setwinpos", {toString(x), toString(y)});
end

jpcrr.set_pcmonitor_pos = function(x, y)
	invokesync("pcmonitor-setwinpos", {toString(x), toString(y)});
end

jpcrr.set_pcstartstoptest_pos = function(x, y)
	invokesync("pcstartstoptest-setwinpos", {toString(x), toString(y)});
end

jpcrr.set_virtualkeyboard_pos = function(x, y)
	invokesync("virtualkeyboard-setwinpos", {toString(x), toString(y)});
end

jpcrr.exit = function()
	invokesync("luaplugin-terminate");
end

jpcrr.pc_start = function()
	invoke("pc-start");
end

jpcrr.pc_stop = function()
	invokesync("pc-stop");
end

jpcrr.screenshot = function(include_hud)
	if include_hud then
		invoke("screenshot-renderbuffer");
	else
		invoke("screenshot-vgabuffer");
	end
end


jpcrr.vretrace_start_trap = function(is_on)
	if is_on then
		invokesync("trap-vretrace-start-on");
	else
		invokesync("trap-vretrace-start-off");
	end
end

jpcrr.vretrace_end_trap = function(is_on)
	if is_on then
		invokesync("trap-vretrace-end-on");
	else
		invokesync("trap-vretrace-end-off");
	end
end

jpcrr.timed_trap = function(nsecs)
	if nsecs then
		invokesync("trap-timed", {toString(nsecs)});
	else
		invokesync("trap-timed-disable");
	end
end

jpcrr.write_byte = function(addr, value)
	invokesync("memory-write", {toString(addr), toString(value), "1"});
end

jpcrr.write_word = function(addr, value)
	invokesync("memory-write", {toString(addr), toString(value), "2"});
end

jpcrr.write_dword = function(addr, value)
	invokesync("memory-write", {toString(addr), toString(value), "4"});
end

jpcrr.read_byte = function(addr)
	local t = {toString(addr), "1"};
	t = invokecall("memory-read", t);
	return (t or {})[1];
end

jpcrr.read_word = function(addr)
	local t = {toString(addr), "2"};
	t = invokecall("memory-read", t);
	return (t or {})[1];
end

jpcrr.read_dword = function(addr)
	local t = {toString(addr), "4"};
	t = invokecall("memory-read", t);
	return (t or {})[1];
end

jpcrr.invoke = nil;
jpcrr.invoke_synchronous = nil;
jpcrr.call = null

-- Dofile.
dofile = function(_script)
	local chunk, err, indication
	chunk, err = loadfile(_script);
	if not chunk then
		error("Kernel: Can't load subscript " .. _script .. ": " .. err);
	end
	return chunk();
end

local args2 = args;
args = null;
args = {};
for k, v in pairs(args2) do
	if (#k > 2 and string.byte(k, 1) == 120 and string.byte(k, 2) == 45) then
		args[string.sub(k, 3)] = v;
	end
end
jpcrr_raw = null;

do
	chunk = null;
	loaded, err = pcall(function()
		chunk, err = loadfile(script);
		if not chunk then
			error(err);
		end
	end);
	if not loaded then
		print("Kernel: Can't load script " .. script .. ": " .. err);
		invoke("luaplugin-terminate");
		while true do end
	end

	script = null;
	indication, err = pcall(chunk);
	if not indication then
		print("Kernel: Unprotected error in script: " .. err);
		invoke("luaplugin-terminate");
		while true do end
	end
end
