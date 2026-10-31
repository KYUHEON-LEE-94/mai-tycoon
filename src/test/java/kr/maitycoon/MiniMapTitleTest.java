package kr.maitycoon;
import net.kyori.adventure.text.TextComponent;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class MiniMapTitleTest {
    private static int components(int[][] cells) { return MiniMapTitle.render(cells).children().size(); }
    private static int[][] filled(int color) {
        int[][] cells = new int[MiniMapTitle.CELLS][MiniMapTitle.CELLS];
        for (int[] row : cells) java.util.Arrays.fill(row, color);
        return cells;
    }
    @Test void uniformRowsCollapseIntoOneRun() {
        assertEquals(3 + MiniMapTitle.CELLS * 2, components(filled(0x7FB238)));
    }
    @Test void everyCellIsDrawnExactlyOnce() {
        int[][] cells = filled(0x7FB238);
        cells[3][4] = 0xFFFFFF;
        long glyphs = MiniMapTitle.render(cells).children().stream().map(c -> ((TextComponent) c).content()).mapToLong(s -> s.chars().filter(ch -> ch == 0xE230).count()).sum();
        assertEquals((long) MiniMapTitle.CELLS * MiniMapTitle.CELLS, glyphs);
        assertEquals(3 + MiniMapTitle.CELLS * 2 + 2, components(cells));
    }
}
