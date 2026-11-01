package kr.maitycoon;
import net.kyori.adventure.text.TextComponent;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class MiniMapTitleTest {
    private static int components(int[][] cells) { return MiniMapTitle.render(cells, 0).children().size(); }
    private static int[][] filled(int color) {
        int[][] cells = new int[MiniMapTitle.CELLS][MiniMapTitle.CELLS];
        for (int[] row : cells) java.util.Arrays.fill(row, color);
        return cells;
    }
    @Test void uniformRowsCollapseIntoOneRun() {
        assertEquals(7 + MiniMapTitle.CELLS * 2, components(filled(0x7FB238)));
    }
    @Test void everyCellIsDrawnExactlyOnce() {
        int[][] cells = filled(0x7FB238);
        cells[3][4] = 0xFFFFFF;
        long glyphs = MiniMapTitle.render(cells, 0).children().stream().map(c -> ((TextComponent) c).content()).mapToLong(s -> s.chars().filter(ch -> ch == 0xE230).count()).sum();
        assertEquals((long) MiniMapTitle.CELLS * MiniMapTitle.CELLS, glyphs);
        assertEquals(7 + MiniMapTitle.CELLS * 2 + 2, components(cells));
    }
    @Test void theBalanceDoesNotMoveTheMap() {
        int[][] cells = new int[MiniMapTitle.CELLS][MiniMapTitle.CELLS];
        var small = MiniMapTitle.render(cells, 7).children();
        var large = MiniMapTitle.render(cells, 1_234_567_890L).children();
        assertEquals("7", ((TextComponent) small.get(2)).content());
        assertEquals("1,234,567,890", ((TextComponent) large.get(2)).content());
        assertEquals(small.size(), large.size());
        assertTrue(advance(((TextComponent) small.get(4)).content()) - advance(((TextComponent) large.get(4)).content()) == 12 * 4);
    }
    private static int advance(String spaces) {
        int total = 0;
        for (char c : spaces.toCharArray()) total += c >= 0xE100 ? 1 << (c - 0xE100) : -(1 << (c - 0xE000));
        return total;
    }
}
