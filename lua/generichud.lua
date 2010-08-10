dofile("textrender.lua");

keys = {}
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

print("Loading font...");
font, err = io.open_arch_read("unifont.jrsr");
if not font then
	error("Can't open font: " .. err);
end
set_font_file(font);
print("Font loaded.");

while true do
	mtype, message = jpcrr.wait_event();
	if mtype == "lock" then
		jpcrr.hud.top_gap(3, 60);
		clocktime = jpcrr.clock_time();
		frame = jpcrr.frame_number();
		render_text(3, 0, 0, "Timestamp: " .. tostring(clocktime) .. "(" .. frame .. ")", false, 255, 255, 0);
		nextchar = jpcrr.read_word(0x41A) - 30;
		lastchar = jpcrr.read_word(0x41C) - 30;
		if lastchar < nextchar then
			lastchar = lastchar + 32;
		end
		render_text(3, 320, 0, "KEYQ: " .. tostring((lastchar-nextchar)/2), false, 255, 255, 0);

		index = 0;
		for k, v in pairs(keys) do
			if jpcrr.keypressed(v) then
				render_text(3, index * 8, 20, k, false, 255, 255, 0);
			end
			index = index + #k + 1;
		end

		if jpcrr.joystick_state then
			hA, hB, hC, hD, bA, bB, bC, bD = jpcrr.joystick_state();
		else
			render_text(3, 0 * 8, 40, false, "<Joystick status not available>", false, 255, 255, 0);
		end

		if bA then
			render_text(3, 0 * 8, 40, "X", false, 255, 255, 0);
		end
		if hA then
			render_text(3, 2 * 8, 40, tostring(hA), false, 255, 255, 0);
		end

		if bB then
			render_text(3, 16 * 8, 40, "X", false, 255, 255, 0);
		end
			if hB then
			render_text(3, 18 * 8, 40, tostring(hB), false, 255, 255, 0);
		end

		if bC then
			render_text(3, 32 * 8, 40, "X", false, 255, 255, 0);
		end
		if hC then
			render_text(3, 34 * 8, 40, tostring(hC), false, 255, 255, 0);
		end

		if bD then
			render_text(3, 48 * 8, 40, "X", false, 255, 255, 0);
		end
		if hD then
			render_text(3, 50 * 8, 40, tostring(hD), false, 255, 255, 0);
		end
		jpcrr.release_vga();
	else
		if message then
			print("Got '" .. mtype .. "'['" .. message .. "'].")
		else
			print("Got '" .. mtype .. "'[<no message>].")
		end
	end
end
