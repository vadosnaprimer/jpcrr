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


print("");
info = subtitle_runinfo();
print(info);

while true do
        jpcrr.next_frame();
	render_subtitle_text(info, true, 255, 255, 255);
end
