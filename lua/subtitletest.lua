dofile("subtitles.lua");
print("Game:" .. (get_gamename() or "<not available>"));

print("Short authors:");
for k, v in ipairs(get_short_authors()) do
	print(v);
end

print("");
print("Long authors:");
for k, v in ipairs(get_long_authors()) do
	print(v);
end

headers = jpcrr.movie_headers();

print("");
info1 = subtitle_runinfo();
info2 = tasvideos_subtitle_this_is_TAS();
print(info1);
print(info2);

while true do
        jpcrr.next_frame();
	time = jpcrr.clock_time();
	for k, v in ipairs(headers) do
		if v[1] == "SUBTITLE1" then
			if time >= tonumber(v[2]) and time <= tonumber(v[3]) then
				render_subtitle_text(info1, (v[4] == "TOP"), 255, 128, 0);
			end
		end
		if v[1] == "SUBTITLE2" then
			if time >= tonumber(v[2]) and time <= tonumber(v[3]) then
				render_subtitle_text(info2, (v[4] == "TOP"), 255, 128, 0);
			end
		end
	end
end
