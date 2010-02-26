infile, err = io.open(arg[1], "r");
if not infile then
	error("Can't open input file: " .. err);
end

outfile, err = io.open(arg[2], "w");
if not outfile then
	error("Can't open output file: " .. err);
end

is_high = false;
last_trans_time = 0;
samplenum = 0;

emit_transition = function(time, tohigh)
	x = time - last_trans_time;
	s = "";
	while x >= 4294967295 do
		print("Skipping time ahead");
		s = s .. string.char(255, 255, 255, 255);
		x = x - 4294967295;
		last_trans_time = last_trans_time + 4294967295;
	end

	x4 = x % 256;
	x = (x - x4) / 256;
	x3 = x % 256;
	x = (x - x3) / 256;
	x2 = x % 256;
	x = (x - x2) / 256;
	x1 = x % 256;


	if is_high ~= tohigh then
		s = s .. string.char(x1, x2, x3, x4);
		if is_high then
			s = s .. string.char(255, 255, 255, 255);
		else
			s = s .. string.char(0, 0, 0, 0);
		end
		if tohigh then
			s = s .. string.char(0, 0, 0, 0, 255, 255, 255, 255);
		else
			s = s .. string.char(0, 0, 0, 0, 0, 0, 0, 0);
		end
		last_trans_time = time;
	end
	outfile:write(s);
	is_high = tohigh;
end

emit_transition(0, false);

while true do
	inb = infile:read(1);
	if not inb then
		break;
	end
	scaled = math.floor(50 * string.byte(inb) / 255 + 0.5);
	if scaled == 0 and is_high then
		emit_transition(50000 * samplenum, false);
	elseif scaled == 0 then
		-- Do nothing.
		x = 6;
	else
		if not is_high then
			emit_transition(50000 * samplenum, true);
		end
		if scaled < 50 then
			emit_transition(50000 * samplenum + 1000 * scaled, false);
		end
	end

	samplenum = samplenum + 1;
end
