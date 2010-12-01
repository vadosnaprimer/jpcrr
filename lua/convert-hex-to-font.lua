invalid_data = "007E7E7E4E367A7A76766E6E7E6E7E00";
nonexistent_data = "00540240024002400240024002402A00";

local hexes = {};
hexes[48] = 0;
hexes[49] = 8;
hexes[50] = 4;
hexes[51] = 12;
hexes[52] = 2;
hexes[53] = 10;
hexes[54] = 6;
hexes[55] = 14;
hexes[56] = 1;
hexes[57] = 9;
hexes[65] = 5;
hexes[66] = 11;
hexes[67] = 3;
hexes[68] = 13;
hexes[69] = 7;
hexes[70] = 15;
hexes[97] = 5;
hexes[98] = 13;
hexes[99] = 3;
hexes[100] = 11;
hexes[101] = 7;
hexes[102] = 15;

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
		s = s .. "+\"*"		-- width 8
	elseif #hex == 64 then
		s = s .. "+\"2"		-- width 16
	else
		error("Unknown font format " .. (#hex) .. ".");
	end
	local i;
	for i = 0,(#hex - 1),8 do
		local c1, c2, c3, c4, c5, c6, c7, c8;
		c1 = hexes[string.byte(hex, i + 1)];
		c2 = hexes[string.byte(hex, i + 2)];
		c3 = hexes[string.byte(hex, i + 3)];
		c4 = hexes[string.byte(hex, i + 4)];
		c5 = hexes[string.byte(hex, i + 5)];
		c6 = hexes[string.byte(hex, i + 6)];
		c7 = hexes[string.byte(hex, i + 7)];
		c8 = hexes[string.byte(hex, i + 8)];
		local val;
		val = c2 * 268435456 + c1 * 16777216 + c4 * 1048576 + c3 * 65536 +
			c6 * 4096 + c5 * 256 + c8 * 16 + c7;
		local x1, x2, x3, x4, x5;
		x5 = val % 93;
		val = (val - x5) / 93;
		x4 = val % 93;
		val = (val - x4) / 93;
		x3 = val % 93;
		val = (val - x3) / 93;
		x2 = val % 93;
		val = (val - x2) / 93;
		x1 = val % 93;
		s = s .. string.char(x1 + 66, x2 + 34, x3 + 34, x4 + 34, x5 + 34);
	end
	return s .. "\n";
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
