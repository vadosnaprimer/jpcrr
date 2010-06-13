lines = {};
linelen = nil;
linecount = 0;

component_encode = function(str)
	mult = 1;
	value = 0;
	for i = 1,#str do
		if i % 8 == 1 then
			mult = 1;
			value = 256 * value;
		end
		if string.sub(str, i, i) == "1" then
			value = value + mult;
		end
		mult = mult * 2;
	end
	if #str < 9 then
		c2 = value % 93;
		value = (value - c2) / 93;
		c1 = value % 93;
		return string.char(34 + c1, 34 + c2);
	elseif #str < 17 then
		c3 = value % 93;
		value = (value - c3) / 93;
		c2 = value % 93;
		value = (value - c2) / 93;
		c1 = value % 93;
		return string.char(37 + c1, 34 + c2, 34 + c3);
	elseif #str < 25 then
		c4 = value % 93;
		value = (value - c4) / 93;
		c3 = value % 93;
		value = (value - c3) / 93;
		c2 = value % 93;
		value = (value - c2) / 93;
		c1 = value % 93;
		return string.char(45 + c1, 34 + c2, 34 + c3, 34 + c4);
	elseif #str < 32 then
		c5 = value % 93;
		value = (value - c5) / 93;
		c4 = value % 93;
		value = (value - c4) / 93;
		c3 = value % 93;
		value = (value - c3) / 93;
		c2 = value % 93;
		value = (value - c2) / 93;
		c1 = value % 93;
		return string.char(66 + c1, 34 + c2, 34 + c3, 34 + c4, 34 + c5);
	end
end

encode_letter = function(x, y, name)
	letter = prefix;
	for i = 1,cellheight do
		letter = letter .. component_encode(string.sub(lines[y + i - 1], x, x + cellwidth));
	end
	print("!BEGIN " .. name);
	print("+" .. letter);
end

for line in io.stdin:lines() do
	table.insert(lines, line);
	if linelen and #line ~= linelen then
		error("Inconsistent line length");
	end
	linelen = #line;
	linecount = linecount + 1;
end

if linecount == 0 or linecount % 6 ~= 0 then
	error("Line count must be positive and divisible by 6");
end

if (linelen or 0) % 16 ~= 0 then
	error("Line length must be positive and divisible by 16");
end

cellwidth = linelen / 16;
cellheight = linecount / 6;

if cellwidth > 31 then
	error("Font too wide (max 31 pels)");
end

prefix = string.char(34, 34 + cellwidth);

print("JRSR");
for j=0,95 do
	x = j % 16;
	y = (j - x) / 16;
	encode_letter(cellwidth * x + 1, cellheight * y + 1, j + 32);
end
print("!END");
