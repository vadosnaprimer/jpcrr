dofile("textrender.lua");

local LEFT = 128 + 75;
local RIGHT = 128 + 77;
local UP = 128 + 72;
local DOWN = 128 + 80;
local SPACE = 57;
local LCTRL = 29;
local LALT = 56;
local LSHIFT = 42;
local RCTRL = 128 + 29;
local RALT = 128 + 56;
local RSHIFT = 54;
local ENTER = 28;
local ESC = 1;

font, err = io.open_arch_read("unifont.jrsr");
if not font then
        error("Can't open font: " .. err);
end

set_font_file(font);

w, h = text_metrics("012345\n6789", false);
print(w .. "x" .. h);

while true do
        if jpcrr.wait_vga() then
		jpcrr.hud.top_gap(3, 40);
                render_text(3, 0, 0, "Timestamp: " .. tostring(jpcrr.clock_time()), false, 255, 255, 0);
		if jpcrr.keypressed(LEFT) then
			render_text(3, 0 * 8, 20, "LEFT", false, 255, 255, 0);
		end
		if jpcrr.keypressed(RIGHT) then
			render_text(3, 5 * 8, 20, "RIGHT", false, 255, 255, 0);
		end
		if jpcrr.keypressed(UP) then
			render_text(3, 11 * 8, 20, "UP", false, 255, 255, 0);
		end
		if jpcrr.keypressed(DOWN) then
			render_text(3, 14 * 8, 20, "DOWN", false, 255, 255, 0);
		end
		if jpcrr.keypressed(SPACE) then
			render_text(3, 19 * 8, 20, "SPACE", false, 255, 255, 0);
		end
		if jpcrr.keypressed(LCTRL) then
			render_text(3, 25 * 8, 20, "LCTRL", false, 255, 255, 0);
		end
		if jpcrr.keypressed(LALT) then
			render_text(3, 31 * 8, 20, "LALT", false, 255, 255, 0);
		end
		if jpcrr.keypressed(LSHIFT) then
			render_text(3, 36 * 8, 20, "LSHIFT", false, 255, 255, 0);
		end
		if jpcrr.keypressed(RCTRL) then
			render_text(3, 43 * 8, 20, "RCTRL", false, 255, 255, 0);
		end
		if jpcrr.keypressed(RALT) then
			render_text(3, 49 * 8, 20, "RALT", false, 255, 255, 0);
		end
		if jpcrr.keypressed(RSHIFT) then
			render_text(3, 54 * 8, 20, "RSHIFT", false, 255, 255, 0);
		end
		if jpcrr.keypressed(ESC) then
			render_text(3, 61 * 8, 20, "ESC", false, 255, 255, 0);
		end
		if jpcrr.keypressed(ENTER) then
			render_text(3, 65 * 8, 20, "ENTER", false, 255, 255, 0);
		end
                jpcrr.release_vga();
        end
end
