local cached_fonts = {};
local font_file = {};

local xD800 = 55296;
local xDC00 = 56320;
local xDFFF = 57343;

set_font_file = function(file)
	font_file = file;
	cached_fonts = {};	-- Flush cache.
end

local check_codepoint = function(codepoint)
	if type(codepoint) == "string" then
		if codepoint == "nonexistent" then
			return true;
		elseif codepoint == "invalid" then
			return true;
		else
			return false;
		end
	elseif type(codepoint) == "number" then
		local planechar = codepoint % 65536;
		local plane = (codepoint - planechar) / 65536;

		-- Negative codepoints are not valid, planes have 65534 characters
		-- and there are 17 planes.
		if codepoint < 0 or planechar > 65533 or plane > 16 then
			return false;
		end
		-- Surrogate range not valid.
		if codepoint >= xD800 and codepoint <= xDFFF then
			return false;
		end
		return true;	-- Ok.
	else
		return false;
	end
end

local load_codepoint = function(codepoint)
	local bitmap = "";
	local metric_w = 0;
	local metric_h = 0;

	if not font_file or not font_file.member then
		cached_fonts[codepoint] = false;
		return;
	end

	local file, err;
	file, err = font_file:member(tostring(codepoint));
	if not file then
		cached_fonts[codepoint] = false;
		return;
	end
	local file2 = file:four_to_five();
	bitmap = "";
	local line;
	local raw = file2:read();
	if #raw < 2 then
		metric_h = 0;
		metric_w = 0;
		bitmap = "";
	else
		local w1 = string.byte(raw, 1);
		local w2 = string.byte(raw, 2);
		local w, h, l;
		if w1 > 127 then
			metric_w = (w1 - 128) + 128 * w2;
			l = #raw - 2;
		else
			metric_w = w1;
			l = #raw - 1;
		end
		metric_h = (l - l % metric_w) / metric_w;
		bitmap = raw;
	end
	file2:close();

	cached_fonts[codepoint] = {bitmap = bitmap, metric_w = metric_w, metric_h = metric_h};
end

local get_character = function(codepoint)
	if not check_codepoint(codepoint) then
		codepoint = "invalid";
	end
	if cached_fonts[codepoint] == nil then
		load_codepoint(codepoint);
	end
	if not cached_fonts[codepoint] then
		-- No such codepoint in font. Fall back.
		codepoint = "nonexistent";
	end
	if cached_fonts[codepoint] == nil then
		load_codepoint(codepoint);
	end
	if not cached_fonts[codepoint] then
		-- Fine, doesn't exist and no fallback.
		return {bitmap = "", metric_w = 0, metric_h = 0};
	end
	return cached_fonts[codepoint];
end

local next_character = function(str, index)
	local c1 = string.byte(str, index);
	local c2 = nil;
	if index < #str then
		c2 = string.byte(str, index);
	end
	local codelen = 0;
	local point = 0;

	if c1 < xD800 or c1 > xDFFF then
	point = c1;
		codelen = 1;
	elseif c1 >= xDC00 then
		point = "invalid";
		codelen = 1;
	elseif not c2 then
		point = "invalid";
		codelen = 1;
	elseif c2 < xDC00 or c2 > xDFFF then
		point = "invalid";
		codelen = 2;
	else
		c1 = c1 - xD800;
		c2 = c2 - xDC00;
		point = 65536 + c1 * 1024 + c2;
		codelen = 2;
	end
	if index + codelen <= #str then
		return point, index + codelen;
	else
		return point, nil;
	end
end

text_metrics = function(str, singleline)
	local metric_w = 0;
	local metric_h = 0;
	local metric_w_curline = 0;
	local metric_h_curline = 0;
	local index = 1;

	while index do
		local codepoint;
		local fontdata;
		codepoint, index = next_character(str, index);
		if singleline or (codepoint ~= 10 and codepoint ~= 13) then
			fontdata = get_character(codepoint);
			if fontdata.metric_h > metric_h_curline then
				metric_h_curline = fontdata.metric_h;
			end
			metric_w_curline = metric_w_curline + fontdata.metric_w;
		else
			if metric_w_curline > metric_w then
				metric_w = metric_w_curline;
			end
			metric_h = metric_h + metric_h_curline;
			metric_w_curline = 0;
			metric_h_curline = 0;
		end
	end
	if metric_w_curline > metric_w then
		metric_w = metric_w_curline;
	end
	metric_h = metric_h + metric_h_curline;
	metric_w_curline = 0;
	metric_h_curline = 0;

	return metric_w, metric_h;
end

render_text = function(flags, x, y, str, singleline, fgr, fgg, fgb, fga, bgr, bgg, bgb, bga)
	local metric_w = 0;
	local metric_h = 0;
	local metric_w_curline = 0;
	local metric_h_curline = 0;
	local index = 1;

	fga = fga or 255;
	bgr = bgr or 0;
	bgg = bgg or 0;
	bgb = bgb or 0;
	bga = bga or 0;

	while index do
		local codepoint;
		local fontdata;
		codepoint, index = next_character(str, index);
		if singleline or (codepoint ~= 10 and codepoint ~= 13) then
			fontdata = get_character(codepoint);
			jpcrr.hud.bitmap_binary(flags, x + metric_w_curline, y + metric_h, fontdata.bitmap, fgr, fgg,
				fgb, fga, bgr, bgg, bgb, bga);
			if fontdata.metric_h > metric_h_curline then
				metric_h_curline = fontdata.metric_h;
			end
			metric_w_curline = metric_w_curline + fontdata.metric_w;
		else
			if metric_w_curline > metric_w then
				metric_w = metric_w_curline;
			end
			metric_h = metric_h + metric_h_curline;
			metric_w_curline = 0;
			metric_h_curline = 0;
		end
	end
end
