win = jpcrr.window.create("test from Lua");
win:create_component({gridx = 0, gridy = 0, gridwidth = 1, gridheight = 1, name = "label1", type = "label", text = "foo"});
win:create_component({gridx = 1, gridy = 0, gridwidth = 1, gridheight = 1, name = "text1", type = "textfield", text = "", columns = 40});
win:create_component({gridx = 0, gridy = 1, gridwidth = 1, gridheight = 1, name = "label2", type = "label", text = "bar"});
win:create_component({gridx = 1, gridy = 1, gridwidth = 1, gridheight = 1, name = "text2", type = "textfield", text = "", columns = 40});
win:create_component({gridx = 0, gridy = 2, gridwidth = 1, gridheight = 1, name = "label3", type = "label", text = "baz"});
win:create_component({gridx = 1, gridy = 2, gridwidth = 1, gridheight = 1, name = "text3", type = "textfield", text = "", columns = 40});
win:create_component({gridx = 0, gridy = 3, gridwidth = 2, gridheight = 1, name = "button1", type = "button", text = "zot"});
win:create_component({gridx = 0, gridy = 4, gridwidth = 2, gridheight = 1, name = "checkbox1", type = "checkbox", text = "foobar"});
win:show();
win:disable("text2");
win:select("checkbox1");

while true do
	mtype, message = jpcrr.wait_event();
	if mtype == "lock" then
		jpcrr.release_vga();
	else
		if message then
			print("Got '" .. mtype .. "'['" .. message .. "'].")
		else
			print("Got '" .. mtype .. "'[<no message>].")
		end
	end
end
