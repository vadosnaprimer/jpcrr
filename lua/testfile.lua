file, err = io.open_read("testfile");
if not file then
	error("Error opening file: " .. err);
end

content, err = file:read();
file:close();

if not content then
	error("Error reading file: " .. err);
end

print("Aount read: " .. #content);
