package kr.maitycoon;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;

/**
 * Paints a grid of colours as boss bar text using one-pixel glyphs from the resource pack. Each row has its own font
 * because a font fixes the height. Boss bar text is centred, so leading space moves the map to the right of the screen.
 */
public final class MiniMapTitle {
    public static final int CELLS = 25, CELL_SIZE = 3, MAP_SIZE = CELLS * CELL_SIZE, FRAME_MARGIN = 2;
    private static final int LEFT = 332;
    private static final String CELL = "", FRAME = "";
    private static final Key SPACE = Key.key("maitycoon", "space"), GUI = Key.key("maitycoon", "gui");
    private MiniMapTitle() {}
    private static Component space(int delta) {
        StringBuilder spaces = new StringBuilder();
        int base = delta < 0 ? 0xE000 : 0xE100, left = Math.abs(delta);
        for (int bit = 0; left != 0; bit++, left >>= 1) if ((left & 1) != 0) spaces.append((char) (base + bit));
        return Component.text(spaces.toString()).font(SPACE);
    }
    /** One component per run of equal colour, which keeps the packet small over grass or water. */
    public static Component render(int[][] cells) {
        TextComponent.Builder title = Component.text().color(NamedTextColor.WHITE);
        title.append(space(LEFT - FRAME_MARGIN));
        title.append(Component.text(FRAME).font(GUI));
        title.append(space(FRAME_MARGIN - (MAP_SIZE + FRAME_MARGIN * 2 + 1)));
        for (int row = 0; row < cells.length; row++) {
            Key font = Key.key("maitycoon", "mm" + row);
            int[] line = cells[row];
            for (int start = 0; start < line.length; ) {
                int end = start;
                while (end < line.length && line[end] == line[start]) end++;
                title.append(Component.text(CELL.repeat(end - start)).font(font).color(TextColor.color(line[start])));
                start = end;
            }
            title.append(space(row == cells.length - 1 ? 0 : -line.length * CELL_SIZE));
        }
        return title.build();
    }
}
