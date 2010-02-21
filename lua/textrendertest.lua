dofile("textrender.lua");

font, err = io.open_arch_read("unifont.jrsr");
if not font then
        error("Can't open font: " .. err);
end

set_font_file(font);

w, h = text_metrics("012345\n6789", false);
print(w .. "x" .. h);

while true do
        if jpcrr.wait_vga() then
                render_text(3, 100, 100, "012345\n6789", false, 255, 255, 0);
                jpcrr.release_vga();
        end
end
