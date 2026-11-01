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
    @Test void nearbyPlayersKeepTheirCell() {
        assertEquals(new MiniMapTitle.Marker(MiniMapTitle.RADIUS + 3, MiniMapTitle.RADIUS - 2, "kai"), MiniMapTitle.pin(3, -2, 40, "kai"));
    }
    @Test void farPlayersArePinnedAtTheEdgeWithTheirDistance() {
        var east = MiniMapTitle.pin(100, 0, 800, "kai");
        assertEquals(new MiniMapTitle.Marker(MiniMapTitle.CELLS - 1, MiniMapTitle.RADIUS, "kai 800m"), east);
        var corner = MiniMapTitle.pin(-500, 500, 5650, "kai");
        assertEquals(0, corner.column());
        assertEquals(MiniMapTitle.CELLS - 1, corner.row());
        var slope = MiniMapTitle.pin(100, 50, 890, "kai");
        assertEquals(MiniMapTitle.CELLS - 1, slope.column());
        assertEquals(MiniMapTitle.RADIUS + 6, slope.row());
    }
    @Test void namesDoNotMoveTheMap() {
        int[][] cells = filled(0x7FB238);
        var plain = MiniMapTitle.render(cells, 0).children();
        var named = MiniMapTitle.render(cells, 0, java.util.List.of(new MiniMapTitle.Marker(2, 5, "kai"), new MiniMapTitle.Marker(23, 5, "maizurzu_long_16"))).children();
        assertEquals(plain.size() + 6, named.size());
        int net = 0;
        for (var child : named.subList(plain.size(), named.size())) {
            String text = ((TextComponent) child).content();
            net += child.font().value().equals("space") ? advance(text) : text.length() * MiniMapTitle.NAME_ADVANCE;
        }
        assertEquals(0, net);
    }
    @Test void namesOnOneLineSeparate() {
        var named = MiniMapTitle.render(filled(0), 0, java.util.List.of(new MiniMapTitle.Marker(2, 5, "kai"), new MiniMapTitle.Marker(9, 5, "ari"))).children();
        assertEquals("mn5", named.get(named.size() - 5).font().value());
        assertEquals("mn8", named.get(named.size() - 2).font().value());
    }
    @Test void onlyDrawnCharactersReachTheFont() {
        var named = MiniMapTitle.render(filled(0), 0, java.util.List.of(new MiniMapTitle.Marker(2, 5, "가a"))).children();
        assertEquals("_a", ((TextComponent) named.get(named.size() - 2)).content());
    }
    private static int advance(String spaces) {
        int total = 0;
        for (char c : spaces.toCharArray()) total += c >= 0xE100 ? 1 << (c - 0xE100) : -(1 << (c - 0xE000));
        return total;
    }
}
