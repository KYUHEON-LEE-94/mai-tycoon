package kr.maitycoon;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
    private static final Key SPACE = Key.key("maitycoon", "space"), GUI = Key.key("maitycoon", "gui"), HUD = Key.key("maitycoon", "hud");
    private static final String PURSE = "\uE260", GOLD_LABEL = "\uE261";
    public static final int PURSE_WIDTH = 76;
    private static final int DIGIT_ADVANCE = 4, LABEL_ADVANCE = 14, PURSE_PADDING = 5;
    private static final TextColor GOLD = TextColor.color(0xF2DDA4);
    public static final int OTHER = 0xFFE040, RADIUS = CELLS / 2, NAME_WIDTH = 5, NAME_ADVANCE = NAME_WIDTH + 1, NAME_HEIGHT = 7, NAME_COLUMNS = 16, NAME_ROW_STEP = 3;
    /** Every character a Minecraft name can hold, plus the space and the metres after a distance. */
    public static final String NAME_CHARS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz_ ";
    /** Where a player's dot goes and the words beside it. */
    public record Marker(int column, int row, String text) {}
    private MiniMapTitle() {}
    /** Someone past the edge stays on the map, pinned in their direction with the distance beside the name. */
    public static Marker pin(int cellsEast, int cellsSouth, int metres, String name) {
        int far = Math.max(Math.abs(cellsEast), Math.abs(cellsSouth));
        if (far <= RADIUS) return new Marker(RADIUS + cellsEast, RADIUS + cellsSouth, name);
        return new Marker(RADIUS + Math.round(cellsEast * (float) RADIUS / far), RADIUS + Math.round(cellsSouth * (float) RADIUS / far), name + " " + metres + "m");
    }
    private static Component space(int delta) {
        StringBuilder spaces = new StringBuilder();
        int base = delta < 0 ? 0xE000 : 0xE100, left = Math.abs(delta);
        for (int bit = 0; left != 0; bit++, left >>= 1) if ((left & 1) != 0) spaces.append((char) (base + bit));
        return Component.text(spaces.toString()).font(SPACE);
    }
    /**
     * One component per run of equal colour, which keeps the packet small over grass or water. The balance shares this
     * bar instead of having its own, because a second bar would push the map down whenever the land banner appears.
     */
    public static Component render(int[][] cells, long coins) { return render(cells, coins, List.of()); }
    public static Component render(int[][] cells, long coins, List<Marker> markers) {
        TextComponent.Builder title = Component.text().color(NamedTextColor.WHITE);
        String value = String.format("%,d", coins);
        int used = PURSE_PADDING + value.length() * DIGIT_ADVANCE + LABEL_ADVANCE;
        title.append(Component.text(PURSE).font(HUD));
        title.append(space(PURSE_PADDING - (PURSE_WIDTH + 1)));
        title.append(Component.text(value).font(HUD).color(GOLD));
        title.append(Component.text(GOLD_LABEL).font(HUD));
        title.append(space(LEFT - FRAME_MARGIN - used));
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
        Set<Integer> taken = new HashSet<>();
        for (Marker marker : markers) label(title, marker, freeRow(marker.row(), taken));
        return title.build();
    }
    /** Two names on one line would print over each other, so the later one moves to the nearest free line. */
    private static int freeRow(int row, Set<Integer> taken) {
        for (int step = 0; step < CELLS; step++) for (int side : new int[]{1, -1}) {
            int line = row + side * step * NAME_ROW_STEP;
            if (line >= 0 && line < CELLS && taken.add(line)) return line;
        }
        return row;
    }
    /** Each name starts and ends at the map's right edge, so names never change the width that centres the bar. */
    private static void label(TextComponent.Builder title, Marker marker, int row) {
        StringBuilder text = new StringBuilder();
        for (char c : marker.text().toCharArray()) text.append(NAME_CHARS.indexOf(c) >= 0 ? c : '_');
        int width = text.length() * NAME_ADVANCE, x = (marker.column() + 1) * CELL_SIZE + 1;
        if (x + width > MAP_SIZE) x = marker.column() * CELL_SIZE - 1 - width;
        title.append(space(x - MAP_SIZE));
        title.append(Component.text(text.toString()).font(Key.key("maitycoon", "mn" + row)).color(TextColor.color(OTHER)));
        title.append(space(MAP_SIZE - x - width));
    }
}
