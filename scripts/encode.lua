#!/usr/bin/env lua
rate = 44100;
aspect = "5:6";
speaker = false;
pcm = false;
fm = false;
outputs = 0;
crf = nil;
finalmode = false;
finalmodee = false;

for i, value in ipairs(arg) do
	if string.sub(value, 1, 6) == "width=" then
		width = tonumber(string.sub(value, 7, #value));
	end
	if string.sub(value, 1, 7) == "height=" then
		height = tonumber(string.sub(value, 8, #value));
	end
	if string.sub(value, 1, 5) == "rate=" then
		rate = tonumber(string.sub(value, 6, #value));
	end
	if string.sub(value, 1, 4) == "crf=" then
		crf = tonumber(string.sub(value, 5, #value));
	end
	if string.sub(value, 1, 7) == "aspect=" then
		aspect = string.sub(value, 8, #value);
	end
	if value == "final" then
		finalmode = true;
	end
	if value == "finale" then
		finalmodee = true;
	end
end
if not width or not height or not rate then
	error("Valid width, height and rate needed (rate has default)");
end

if not crf then
	if finalmode or finalmodee then
		crf = 20
	else
		crf = 25
	end
end

print("Width:  " .. width);
print("Height: " .. height);
print("Rate:   " .. rate);

io.stdout:write("Creating src video pipe...");
os.execute("rm -f video.src");
os.execute("mkfifo video.src");
print("Done.");

io.stdout:write("Creating src audio pipe...");
os.execute("rm -f audio.src");
os.execute("mkfifo audio.src");
print("Done.");

io.stdout:write("Creating RGB video pipe...");
os.execute("rm -f video.rgb");
os.execute("mkfifo video.rgb");
print("Done.");

io.stdout:write("Creating YUV video pipe...");
os.execute("rm -f video.yuv");
os.execute("mkfifo video.yuv");
print("Done.");

io.stdout:write("Checking if PC speaker output is present...");
file = io.open("speaker.dump", "r");
if file then
	print("Yes.");
	file:close();
	speaker = true;
	outputs = outputs + 1;
else
	print("No.");
end


io.stdout:write("Checking if PCM output is present...");
file = io.open("pcm.dump", "r");
if file then
	print("Yes.");
	file:close();
	pcm = true;
	outputs = outputs + 1;
else
	print("No.");
end

io.stdout:write("Checking if FM output is present...");
file = io.open("fm.dump", "r");
if file then
	print("Yes.");
	file:close();
	fm = true;
	outputs = outputs + 1;
else
	print("No.");
end

if speaker then
	print("Converting PC speaker audio...");
	if finalmode then
		os.execute("xterm -e sh -c \"cat logoaudio.dump speaker.dump >audio.src \" &");
		filename = "audio.src";
	else
		filename = "speaker.dump";
	end

	os.execute("rawtoaudio2.exe --input-file=" .. filename .. " --input-format=pcm --output-file=speaker.wav --output-format=wav --output-attenuation=20 --output-rate=" .. rate);
	print("Done.");
end

if pcm then
	print("Converting PCM audio...");
	if finalmode then
		os.execute("xterm -e sh -c \"cat logoaudio.dump pcm.dump >audio.src \" &");
		filename = "audio.src";
	else
		filename = "pcm.dump";
	end

	os.execute("rawtoaudio2.exe --input-file=" .. filename .. " --input-format=pcm --output-file=pcm.wav --output-format=wav --output-rate=" .. rate);
	print("Done.");
end

if fm then
	print("Converting FM audio...");
	if finalmode then
		os.execute("xterm -e sh -c \"cat logoaudio.dump fm.dump >audio.src \" &");
		filename = "audio.src";
	else
		filename = "fm.dump";
	end

	os.execute("rawtoaudio2.exe --input-file=" .. filename .. " --input-format=fm --output-file=fm.wav --output-format=wav --output-rate=" .. rate);
	print("Done.");
end

if outputs > 0 then
	command = "sox ";
	if outputs > 1 then	command = command .. "-m ";		end
	if speaker then		command = command .. "speaker.wav ";	end
	if pcm then		command = command .. "pcm.wav ";	end
	if fm then		command = command .. "fm.wav ";		end
	command = command .. "soundtrack.ogg";
	io.stdout:write("Mixing soundtrack...");
	os.execute(command);
	print("done.");
end

io.stdout:write("Launching logo insertion...");
if finalmode then
	os.execute("xterm -e sh -c \"cat logovideo.dump video.dump >video.src \" &");
else
	os.execute("xterm -e sh -c \"cat video.dump >video.src \" &");
end
print("done.");

io.stdout:write("Launching video conversion...");
os.execute("xterm -e rawtorgb.exe video.src video.rgb lanczos2 " .. width .. " " .. height .. " 16666667 &");
print("Done.");

io.stdout:write("Launching RGB->YUV conversion...");
os.execute("xterm -e mencoder -nosound -vf format=i420 -ovc raw -of rawvideo -o video.yuv -demuxer rawvideo -rawvideo w=" .. width .. ":h=" .. height .. ":fps=60:format=rgb32 video.rgb &");
print("Done.");

for i=1,50000000 do end

print("Launching encoder process...");
if finalmode or finalmodee then
	os.execute("x264 --ssim --crf " .. crf .. " --keyint 600 --ref 16 --mixed-refs --no-fast-pskip --bframes 16 --b-adapt 2 --mbtree --weightb --direct auto --subme 10 --trellis 2 --partitions all --me esa --merange 128 --rc-lookahead 250 --fullrange on --threads 6 --8x8dct --no-dct-decimate --sar " .. aspect .. " --fps 60 -o videoonly.mkv video.yuv " .. width .. "x" .. height);
else
	os.execute("x264 --preset slow --threads 8 --crf " .. crf .. " --merange 96 --me umh --sar " .. aspect .. " --fps 60 -o videoonly.mkv video.yuv " .. width .. "x" .. height);
end
print("Done.");


print("Muxing the MKV...");
if finalmode then
	os.execute("mkvmerge -o final.mkv videoonly.mkv soundtrack.ogg");
else
	os.execute("mkvmerge -o final-wip.mkv videoonly.mkv soundtrack.ogg");
end
print("Done.");
