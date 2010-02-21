while true do
	if jpcrr.wait_vga() then
		x, y = jpcrr.vga_resolution();
		print("Frame: Resolution: " .. x .. "x" .. y .. ".");
		jpcrr.release_vga();
	end
end
