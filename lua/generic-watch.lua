win = jpcrr.window.create("Generic watch");

y = 0;

fieldpair = function(fname, label)
	win:create_component({gridx = 0, gridy = y, gridwidth = 1, gridheight = 1, name = fname .. "_LABEL", type = "label", text = label,
		fill = win:HORIZONTAL(), weightx = 0, weighty = 1});
	win:create_component({gridx = 1, gridy = y, gridwidth = 1, gridheight = 1, name = fname, type = "label", text = "N/A",
		fill = win:HORIZONTAL(), weightx = 1, weighty = 1, insets_left = 16});
	y = y + 1;
end

update_field = function(fname, newval)
	win:set_text(fname, tostring(newval));
end


fieldnames = {};
fieldupdates = {};
keys = {};
keys.LEFT = 128 + 75;
keys.RIGHT = 128 + 77;
keys.UP = 128 + 72;
keys.DOWN = 128 + 80;
keys.SPACE = 57;
keys.LCTRL = 29;
keys.LALT = 56;
keys.LSHIFT = 42;
keys.RCTRL = 128 + 29;
keys.RALT = 128 + 56;
keys.RSHIFT = 54;
keys.ENTER = 28;
keys.ESC = 1;

add_field = function(name, update_fn)
	fieldpair(name, name);
	table.insert(fieldnames, name);
	fieldupdates[name] = update_fn;
end

do_update = function()
	for k, v in ipairs(fieldnames) do
		update_field(v, fieldupdates[v]());
	end
end


add_field("System time: ", function()
	return tostring(jpcrr.clock_time());
end);

add_field("Frame number: ", function()
	return tostring(jpcrr.frame_number());
end);

add_field("Keyboard queue: ", function()
                nextchar = jpcrr.read_word(0x41A) - 30;
                lastchar = jpcrr.read_word(0x41C) - 30;
                if lastchar < nextchar then
                        lastchar = lastchar + 32;
                end
                return tostring((lastchar-nextchar)/2);
end);

for k, v in pairs(keys) do
	local v2 = v;
	add_field("Keyboard " .. k .. ":", function()
		if jpcrr.keypressed(v) then
			return "Pressed";
		else
			return "Released";
		end
	end);
end

fmt_jaxis = function(val)
	if not val then
		return "<not present>";
	else
		return tostring(val);
	end
end

fmt_jbutton = function(val)
	if val == nil then
		return "<not present>";
	elseif val then
		return "held";
	else
		return "released";
	end
end

add_field("Joystick axis X1: ", function() return fmt_jaxis(hA); end);
add_field("Joystick axis Y1: ", function() return fmt_jaxis(hB); end);
add_field("Joystick axis X2: ", function() return fmt_jaxis(hC); end);
add_field("Joystick axis Y2: ", function() return fmt_jaxis(hD); end);
add_field("Joystick button 1-1: ", function() return fmt_jbutton(bA); end);
add_field("Joystick button 1-2: ", function() return fmt_jbutton(bB); end);
add_field("Joystick button 2-1: ", function() return fmt_jbutton(bC); end);
add_field("Joystick button 2-2: ", function() return fmt_jbutton(bD); end);
win:show();

while true do
	a, b = jpcrr.wait_event();
	if a == "lock" then
		hA, hB, hC, hD, bA, bB, bC, bD = jpcrr.joystick_state();
		do_update();
		jpcrr.release_vga();
	elseif a == "message" then
		print("Message: " .. b);
	end
end
