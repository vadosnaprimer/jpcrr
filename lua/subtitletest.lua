dofile("subtitles.lua");
print("Game:" .. (get_gamename() or "<not available>"));

print("Short authors:");
local k, v;
for k, v in ipairs(get_short_authors()) do
	print(v);
end

print("");
print("Long authors:");
for k, v in ipairs(get_long_authors()) do
	print(v);
end

headers = jpcrr.movie_headers();

decode_colorspecs = function(v)
	if #v <= 4 then
		return 255, 255, 255, 255, 0, 0, 0, 0;
	elseif #v == 5 then
		return 255, 255, 255, 255, 0, 0, 0, tonumber(v[5]);
	elseif #v == 6 then
		return 255, 255, 255, tonumber(v[5]), 0, 0, 0, tonumber(v[6]);
	elseif #v == 7 then
		return tonumber(v[5]), tonumber(v[6]), tonumber(v[7]), 255, 0, 0, 0, 0;
	elseif #v == 8 then
		return tonumber(v[5]), tonumber(v[6]), tonumber(v[7]), 255, 0, 0, 0, tonumber(v[8]);
	elseif #v == 9 then
		return tonumber(v[5]), tonumber(v[6]), tonumber(v[7]), tonumber(v[8]), 0, 0, 0, tonumber(v[9]);
	elseif #v == 10 then
		return tonumber(v[5]), tonumber(v[6]), tonumber(v[7]), 255, tonumber(v[8]), tonumber(v[9]), tonumber(v[10]), 255;
	elseif #v == 11 then
		return tonumber(v[5]), tonumber(v[6]), tonumber(v[7]), 255, tonumber(v[8]), tonumber(v[9]), tonumber(v[10]), tonumber(v[11]);
	else
		return tonumber(v[5]), tonumber(v[6]), tonumber(v[7]), tonumber(v[8]), tonumber(v[9]), tonumber(v[10]), tonumber(v[11]), tonumber(v[12]);
	end
end

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
				fr, fg, fb, fa, br, bg, bb, ba = decode_colorspecs(v);
				render_subtitle_text(info1, (v[4] == "TOP"), fr, fg, fb, fa, br, bg, bb, ba);
			end
		end
		if v[1] == "SUBTITLE2" then
			if time >= tonumber(v[2]) and time <= tonumber(v[3]) then
				fr, fg, fb, fa, br, bg, bb, ba = decode_colorspecs(v);
				render_subtitle_text(info2, (v[4] == "TOP"), fr, fg, fb, fa, br, bg, bb, ba);
			end
		end
	end
end
