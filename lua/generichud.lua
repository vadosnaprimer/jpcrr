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
	jpcrr.next_frame();
	jpcrr.hud.top_gap(3, 40);
	render_text(3, 0, 0, "Timestamp: " .. tostring(jpcrr.clock_time()), false, 255, 255, 0);
	index = 0;
	for k, v in pairs(keys) do
		if jpcrr.keypressed(v) then
			render_text(3, index * 8, 20, k, false, 255, 255, 0);
		end
		index = index + #k + 1;
	end
end
