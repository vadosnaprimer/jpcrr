invalid_data = "007E7E7E4E367A7A76766E6E7E6E7E00";
nonexistent_data = "00540240024002400240024002402A00";

local hexes = {};
hexes[48] = "....";
hexes[49] = "...X";
hexes[50] = "..X.";
hexes[51] = "..XX";
hexes[52] = ".X..";
hexes[53] = ".X.X";
hexes[54] = ".XX.";
hexes[55] = ".XXX";
hexes[56] = "X...";
hexes[57] = "X..X";
hexes[65] = "X.X.";
hexes[66] = "X.XX";
hexes[67] = "XX..";
hexes[68] = "XX.X";
hexes[69] = "XXX.";
hexes[70] = "XXXX";
hexes[97] = "X.X.";
hexes[98] = "X.XX";
hexes[99] = "XX..";
hexes[100] = "XX.X";
hexes[101] = "XXX.";
hexes[102] = "XXXX";

known_args = {};
known_args["input"] = true;
known_args["output"] = true;
known_args["spacehack"] = true;

args = args or {};

spacehack=false;

for k, v in ipairs(arg) do
	name, value = string.match(v, "([%w_-]+)=(.*)");
	if not name or not value then
		error("Bad argument: '" .. v .. "'.");
	end
	args[name] = value;
end

for k, v in pairs(args) do
	if not known_args[k] then
		print("Warning: Unknown option '" .. k .. "'.");
	end
end

if jpcrr then
	-- This is JPC-RR embedded Selfstanding Lua interpretter.
	print("Detected environment: JPC-RR embedded Lua interpretter");
	if not args["input"] or not args["output"] then
		error("Syntax: input=<input> and output=<output> required");
	end
	tmpfile = io.open_read(args["input"]);
	infile = tmpfile:text();
	outfile = io.open_write(args["output"]);
else
	-- Assume reference implementation or compatible.
	print("Detected environment: Reference Lua interpretter or compatible");
	if not args["input"] or not args["output"] then
		error("Syntax: input=<input> and output=<output> required");
	end
	infile = io.open(args["input"], "r");
	outfile = io.open(args["output"], "w");
end

if args["spacehack"] then
	spacehack = true;
end

write_data_to_file = function(data)
	outfile:write(data);
end

read_data_from_file = function()
	return infile:read();
end

parsenumber = function(hexnumber)
	local n = tonumber("0x" .. hexnumber);
	if not n then
		error("Bad number " .. hexnumber);
	end
	return n;
end

hex_to_member_data = function(codepoint, hex)
	local s = "!BEGIN " .. tostring(codepoint) .. "\n";
	if #hex == 32 then
		local i;
		for i = 0,15 do
			s = s .. "+"
			local c1, c2;
			c1 = hexes[string.byte(hex, 2 * i + 1)];
			c2 = hexes[string.byte(hex, 2 * i + 2)];
			if not c1 or not c2 then
				error("Bad character in data (" .. hex .. ")");
			end
			s = s .. c1 .. c2 .. "\n";
		end
	elseif #hex == 64 then
		local i;
		for i = 0,15 do
			s = s .. "+"
			local c1, c2, c3, c4;
			c1 = hexes[string.byte(hex, 4 * i + 1)];
			c2 = hexes[string.byte(hex, 4 * i + 2)];
			c3 = hexes[string.byte(hex, 4 * i + 3)];
			c4 = hexes[string.byte(hex, 4 * i + 4)];
			if not c1 or not c2 or not c3 or not c4 then
				error("Bad character in data (" .. hex .. ")");
			end
			s = s .. c1 .. c2 .. c3 .. c4 .. "\n";
		end
	else
		error("Unknown font format " .. (#hex) .. ".");
	end
	return s;
end

local codepoints = 0;

write_codepoint = function(codepoint, hex)
	write_data_to_file(hex_to_member_data(codepoint, hex));
	codepoints = codepoints + 1;
	if codepoints % 1000 == 0 then
		print("Wrote " .. codepoints .. " codepoints");
	end
end


write_data_to_file("JRSR\n");
write_codepoint("invalid", invalid_data);
write_codepoint("nonexistent", nonexistent_data);
if spacehack then
	write_codepoint(32, "00000000000000000000000000000000");
end

local line = read_data_from_file();
while line do
	if #line > 0 then
		sep = string.find(line, ":");
		if not sep then
			error("Badly formatted line (" .. line .. ")");
		end
		local code, hexes;
		code = parsenumber(string.sub(line, 1, sep - 1));
		hexes = string.sub(line, sep + 1);
		if code ~= 32 or not spacehack then
			write_codepoint(code, hexes);
		end
	end
	line = read_data_from_file();
end


write_data_to_file("!END\n");
print("Converted " .. (codepoints - 2) .. " codepoints");
