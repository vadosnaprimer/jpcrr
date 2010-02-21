file, err = io.open_read("XBITMAP");
if not file then
	Xbitmap = "";
else
	Xbitmap = file:read();
	file:close();
end

while true do
	if jpcrr.wait_vga() then
		x, y = jpcrr.vga_resolution();
		print("Frame: Resolution: " .. x .. "x" .. y .. ".");
		jpcrr.hud.left_gap(3, 20);
		jpcrr.hud.right_gap(3, 25);
		jpcrr.hud.top_gap(3, 15);
		jpcrr.hud.bottom_gap(3, 30);
		jpcrr.hud.white_solid_box(3, 0, 0, 20, 15);
		jpcrr.hud.white_solid_box(3, 640, 0, 200, 800);

		jpcrr.hud.box(3, 100, 100, 100, 100, 10, 255, 128, 0, 255, 255, 0, 0, 128);

		jpcrr.hud.bitmap(3, 250, 50, Xbitmap, 255, 0, 0, 255, 0, 0, 255, 128);

		jpcrr.release_vga();
	end
end
