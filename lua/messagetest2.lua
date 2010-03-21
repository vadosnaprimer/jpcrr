while true do
	message = jpcrr.poll_message();
	if message then
		print("Message: " .. message);
	end
end
