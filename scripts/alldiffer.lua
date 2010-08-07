#!/usr/bin/env lua

lines = {};
files = {};

for i = 1,#arg do
	files[i], err = io.open(arg[i], "r");
	if not files[i] then
		error("Can't open " .. arg[i] .. ": " .. err);
	end
end

firstline = true;
while true do
	remaining = false;
	for i = 1,#arg do
		lines[i] = files[i]:read();
		if lines[i] then
			remaining = true;
		end
	end
	if not remaining then
		os.exit(0);
	end

	alldiffer = true;
	for i = 1,#arg do
		for j = i + 1,#arg do
			if lines[i] and lines[j] and lines[i] == lines[j] then
				alldiffer = false;
			end
		end
	end

	if alldiffer then
		if not firstline then
			print("-------------------------------------------------------");
		end
		firstline = false;
		for i = 1,#arg do
			if lines[i] then
				print(lines[i]);
			end
		end
	end
end
