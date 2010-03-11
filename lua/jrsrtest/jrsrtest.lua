opentext = function(name)
	local obj, err;
	obj, err = io.dotransform(io.open_write(name), io.transform.text());
	if not obj then
		error(err);
	end
	return obj;
end

linefeeds = {string.char(10), string.char(13, 10), string.char(13), string.char(133)};
spaces = {string.char(9), string.char(32), string.char(tonumber("0x1680")), string.char(tonumber("0x180E")),
	string.char(tonumber("0x2000")), string.char(tonumber("0x2001")), string.char(tonumber("0x2002")),
	string.char(tonumber("0x2003")), string.char(tonumber("0x2004")), string.char(tonumber("0x2005")),
	string.char(tonumber("0x2006")), string.char(tonumber("0x2007")), string.char(tonumber("0x2008")),
	string.char(tonumber("0x2009")), string.char(tonumber("0x200A")), string.char(tonumber("0x2028")),
	string.char(tonumber("0x205F")), string.char(tonumber("0x3000"))};
testfile = "jrsrtest/test-63627373337.jrsr";
EOL = string.char(10);

randomelement = function(tab)
	return tab[math.random(#tab)];
end

randomspace = function() return randomelement(spaces); end
randomlinefeed = function() return randomelement(linefeeds); end

testname = {};
test = {};

withfile = function(f, file, err)
	local success, ret, err;
	if not file then
		error("Withfile: No file given");
	end
	success, err = pcall(f, file);
	file:close();
	if success then
		return;
	else
		error("Withfile call errored: " .. err);
	end
end

simpleopentest = function(sep1, sep2, sep3)
	return function()
		local file, err;
		local member;
		withfile(function(file)
			file:write("JRSR" .. sep1 .. "!BEGIN dummy" .. sep2 .. "!END" .. sep3);
		end, opentext(testfile));

		withfile(function(file)
			member, err = file:member_list();
			if not member then error(err); end
		end, io.open_arch_read(testfile));

		io.unlink(testfile);

		if not member then return nil, err; end
		if #member ~= 1 or member[1] ~= "dummy" then return nil, "Unexpected members"; end
		return true;
	end
end

readline = function(file)
	local line, err;
	line, err = file:read();
	if not line and err then error("Read error: " .. err); end
	return line;
end

simpleopenreadtest = function(sep1)
	return function()
		local file, ret, err;
		local member, line;
		withfile(function(file)
			file:write("JRSR" .. EOL .. "!BEGIN dummy" .. EOL .. "+text1" .. sep1 .. "+text2" .. EOL .. "!END" .. EOL);
		end, opentext(testfile));

		withfile(function(file)
			withfile(function(member)
				line = readline(member);
				if line ~= "text1" then error("Unexpected line #1 (" .. line .. ")"); end
				line = readline(member);
				if line ~= "text2" then error("Unexpected line #2 (" .. line .. ")"); end
				line = readline(member);
				if line3 then error("Unexpected line #3 (" .. line .. ")"); end
			end, io.dotransform2(file:member("dummy"), io.transform.text()));
		end, io.open_arch_read(testfile));

		io.unlink(testfile);

		return true;
	end
end

ftfdecodetest = function(sep1)
	return function()
		local file, ret, err;
		local member, line;
		withfile(function(file)
			file:write("P])" .. sep1 .. "YT!" .. EOL);
		end, opentext(testfile));


		withfile(function(file)
			line, err = file:read();
			if not line then error("Read error: " .. err); end
			if line ~= "AAAA" then error("Unexpected output (" .. line .. ")"); end
		end, io.dotransform2(io.open_read(testfile), io.transform.four_to_five()));

		io.unlink(testfile);

		return true;
	end
end

testname[1] = "JRSR header with LF termination";
test[1] = simpleopentest(linefeeds[1], linefeeds[1], linefeeds[1]);
testname[2] = "JRSR header with CRLF termination";
test[2] = simpleopentest(linefeeds[2], linefeeds[1], linefeeds[1]);
testname[3] = "JRSR header with CR termination";
test[3] = simpleopentest(linefeeds[3], linefeeds[1], linefeeds[1]);
testname[4] = "JRSR header with NL termination";
test[4] = simpleopentest(linefeeds[4], linefeeds[1], linefeeds[1]);

testname[5] = "JRSR !BEGIN with LF termination";
test[5] = simpleopentest(linefeeds[1], linefeeds[1], linefeeds[1]);
testname[6] = "JRSR !BEGIN with CRLF termination";
test[6] = simpleopentest(linefeeds[1], linefeeds[2], linefeeds[1]);
testname[7] = "JRSR !BEGIN with CR termination";
test[7] = simpleopentest(linefeeds[1], linefeeds[3], linefeeds[1]);
testname[8] = "JRSR !BEGIN with NL termination";
test[8] = simpleopentest(linefeeds[1], linefeeds[4], linefeeds[1]);

testname[9] = "JRSR !END with LF termination";
test[9] = simpleopentest(linefeeds[1], linefeeds[1], linefeeds[1]);
testname[10] = "JRSR !END with CRLF termination";
test[10] = simpleopentest(linefeeds[1], linefeeds[1], linefeeds[2]);
testname[11] = "JRSR !END with CR termination";
test[11] = simpleopentest(linefeeds[1], linefeeds[1], linefeeds[3]);
testname[12] = "JRSR !END with NL termination";
test[12] = simpleopentest(linefeeds[1], linefeeds[1], linefeeds[4]);

testname[13] = "JRSR memberline with LF termination";
test[13] = simpleopenreadtest(linefeeds[1]);
testname[14] = "JRSR memberline with CRLF termination";
test[14] = simpleopenreadtest(linefeeds[2]);
testname[15] = "JRSR memberline with CR termination";
test[15] = simpleopenreadtest(linefeeds[3]);
testname[16] = "JRSR memberline with NL termination";
test[16] = simpleopenreadtest(linefeeds[4]);

table.insert(testname, "Four-to-five base test");
table.insert(test, ftfdecodetest(""));

for k, v in ipairs(spaces) do
	table.insert(testname, "Four-to-five space #" .. k);
	table.insert(test, ftfdecodetest(v));
end

for k, v in ipairs(linefeeds) do
	table.insert(testname, "Four-to-five linefeed #" .. k);
	table.insert(test, ftfdecodetest(v));
end

for k, v in ipairs(test) do
	success, ret, err = pcall(v);
	if success and ret then
		print("Test #" .. k .. ": " .. testname[k] .. "...OK");
	elseif success then
		print("Test #" .. k .. ": " .. testname[k] .. "...ERROR: " .. err);
	elseif success and not err then
		print("Test #" .. k .. ": " .. testname[k] .. "...ERROR: <No error available>");
	elseif ret then
		print("Test #" .. k .. ": " .. testname[k] .. "...CRASHED: " .. ret);
	else
		print("Test #" .. k .. ": " .. testname[k] .. "...CRASHED: <No error available>");
	end
end
