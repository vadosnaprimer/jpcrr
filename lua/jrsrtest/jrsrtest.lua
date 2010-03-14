opentext = function(name)
	local obj, err;
	obj, err = io.dotransform(io.open_write(name), io.transform.text());
	if not obj then
		error(err);
	end
	return obj;
end

linefeeds = {string.char(10), string.char(13, 10), string.char(13), string.char(28), string.char(29),
	string.char(30), string.char(133), string.char(tonumber("0x2029"))};
linefeedtypes = {"LF", "CRLF", "CR", "IS4", "IS3", "IS2", "NL", "PS"};
spaces = {string.char(9), string.char(12), string.char(32), string.char(tonumber("0x1680")),
	string.char(tonumber("0x180E")),
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

newtest = function(name, testfun)
	table.insert(testname, name);
	table.insert(test, testfun);
end

succeed = 0;
fail = 0;

for k, v in ipairs(linefeedtypes) do
	newtest("JRSR header with " .. v .. " termination", simpleopentest(linefeeds[k], linefeeds[1], linefeeds[1]));
	newtest("JRSR !BEGIN with " .. v .. " termination", simpleopentest(linefeeds[1], linefeeds[k], linefeeds[1]));
	newtest("JRSR !END with " .. v .. " termination", simpleopentest(linefeeds[1], linefeeds[1], linefeeds[k]));
	newtest("JRSR memberline with " .. v .. " termination", simpleopenreadtest(linefeeds[k]));
	newtest("JRSR memberline with double-" .. v .. " termination", simpleopenreadtest(linefeeds[k] .. linefeeds[k]));
end

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
		succeed = succeed + 1;
	elseif success then
		print("Test #" .. k .. ": " .. testname[k] .. "...ERROR: " .. err);
		fail = fail + 1;
	elseif success and not err then
		print("Test #" .. k .. ": " .. testname[k] .. "...ERROR: <No error available>");
		fail = fail + 1;
	elseif ret then
		print("Test #" .. k .. ": " .. testname[k] .. "...CRASHED: " .. ret);
		fail = fail + 1;
	else
		print("Test #" .. k .. ": " .. testname[k] .. "...CRASHED: <No error available>");
		fail = fail + 1;
	end
end
print(succeed .. " OK, " .. fail .. " FAILED.");
