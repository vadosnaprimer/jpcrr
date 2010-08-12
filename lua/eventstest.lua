count = jpcrr.events.count();
current = jpcrr.events.current_sequence();
print("Current event sequence: " .. current);

print("-------- Start of event dump --------");
for i = 0,count - 1 do
	if current == i then
		print("------------ You are here -----------");
	end
	s = "Event #" .. i .. ": ";
        ev = jpcrr.events.by_sequence(i);
        if #ev == 0 then
		s = s .. "(no such event)";
	else
		s = s .. ev[2] .. "@" .. tostring(ev[1]);
		for j = 3,#ev do
			s = s .. " " .. ev[j];
		end
	end
	print(s);
end
if current < 0 then
	print("------------ You are here -----------");
end
print("--------- End of event dump ---------");
