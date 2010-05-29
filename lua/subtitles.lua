do
	local old_set_font_file = set_font_file;
	local old_text_metrics = text_metrics;
	local old_render_text = render_text;


	dofile("textrender.lua");

	local new_set_font_file = set_font_file;
	local new_text_metrics = text_metrics;
	local new_render_text = render_text;

	font, err = io.open_arch_read("subtitle-font");
	if not font then
	        error("Can't open font: " .. err);
	end
	new_set_font_file(font);


	set_font_file = old_set_font_file;
	text_metrics = old_text_metrics;
	render_text = old_render_text;

	tasvideos_subtitle_this_is_TAS = function()
		return "This is a tool-assisted recording.\nFor details, visit http://TASVideos.org/";
	end

	tasvideos_subtitle_this_is_TAS_fast = function()
		return "This was a tool-assisted recording.\nFor details, visit http://TASVideos.org/";
	end

	for_each_header = function(callback)
		local headers = jpcrr.movie_headers();
		local hdrnum, hdr, ret;

		for hdrnum, hdr in ipairs(headers) do
			ret = callback(hdr);
			if(ret ~= nil) then
				return ret;
			end
		end
	end

	get_gamename = function()
		return for_each_header(function(header)
			if header[1] == "GAMENAME" then
				return header[2];
			else
				return nil;
			end
		end);
	end

	get_authors = function(long_form)
		local ret = {};
		local i;

		for_each_header(function(header)
			if header[1] == "AUTHORS" or header[1] == "AUTHORNICKS" then
				for i = 2, #header do
					ret[#ret + 1] = header[i];
				end
			elseif header[1] == "AUTHORFULL" then
				if long_form then
					ret[#ret + 1] = header[2];
				else
					ret[#ret + 1] = header[3];
				end
			end
		end);
		return ret;
	end

	get_long_authors = function()
		return get_authors(true);
	end

	get_short_authors = function()
		return get_authors(false);
	end

	format_runtime = function()
		local length = jpcrr.movie_length();
		local subseconds = length % 1000000000;
		local seconds = (length - subseconds) / 1000000000;
		subseconds = math.ceil(subseconds / 1000000);
		local minutes = (seconds - seconds % 60) / 60;
		seconds = seconds % 60;
		local hours = (minutes - minutes % 60) / 60;
		minutes = minutes % 60;
		if hours > 0 then
			return tostring(hours) .. ":" .. tostring(minutes) .. ":" .. tostring(seconds) .. "." .. tostring(subseconds);
		else
			return tostring(minutes) .. ":" .. tostring(seconds) .. "." .. tostring(subseconds);
		end
	end

	subtitle_runinfo = function()

		local w, h, x, y, wr, hr, old_w;
		wr, hr = jpcrr.vga_resolution();

		ret = (get_gamename() or "<unknown>") .. " in " .. format_runtime() .. "\n";
		ret = ret .. "by ";
		local k, v, rettmp;
		for k, v in ipairs(get_short_authors()) do
			if k > 1 then
				ret = ret .. " & ";
			end
			rettmp = ret .. v;
			old_w = w;
			w, h = new_text_metrics(rettmp);
			if w > 550 and w > old_w then
				ret = ret .. "\n" .. v;
			else
				ret = rettmp;
			end
		end
		ret = ret .. "\nRerecord count: " .. tostring(jpcrr.movie_rerecords());
		return ret;
	end

	render_subtitle_text = function(text, to_top, fr, fg, fb, fa, br, bg, bb, ba)

		local w, h, x, y, wr, hr;

		w, h = new_text_metrics(text);
		wr, hr = jpcrr.vga_resolution();
		x = math.floor((wr - w) / 2);
		if to_top then
			y = 0;
		else
			y = hr - h;
		end

		new_render_text(3, x, y, text, false, fr, fg, fb, fa, br, bg, bb, ba);
	end

end


--- Title in hh:mm:ss.ss
---  by PlayerName
---  Rerecord count: number
