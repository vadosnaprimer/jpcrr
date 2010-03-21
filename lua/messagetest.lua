while true do
	message = jpcrr.wait_message();
	if message then
		print("Message: " .. message);
	else
		print("Interrupted!");
	end
end
