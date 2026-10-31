package kr.maitycoon;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;

/**
 * Builds a container title that paints the shop screen: the client draws title text at x=8, so
 * negative-space glyphs from the resource pack can place images and numbers anywhere on the GUI.
 * Glyph sizes here must match tools/GeneratePack.java.
 */
final class ShopTitle {
    private static final Key GUI = Key.key("maitycoon", "gui"), SPACE = Key.key("maitycoon", "space");
    private static final Key HEADER = Key.key("maitycoon", "header"), FOOTER = Key.key("maitycoon", "footer");
    private static final int TITLE_X = 8, BACKGROUND_ADVANCE = 177, TAB_ADVANCE = 19, SMALL_ADVANCE = 4, LARGE_ADVANCE = 5;
    private final TextComponent.Builder title = Component.text().color(NamedTextColor.WHITE);
    private int cursor = TITLE_X;
    private void moveTo(int x) {
        int delta = x - cursor;
        cursor = x;
        StringBuilder spaces = new StringBuilder();
        int base = delta < 0 ? 0xE000 : 0xE100, left = Math.abs(delta);
        for (int bit = 0; left != 0; bit++, left >>= 1) if ((left & 1) != 0) spaces.append((char) (base + bit));
        if (!spaces.isEmpty()) title.append(Component.text(spaces.toString()).font(SPACE));
    }
    private void put(Key font, String text, TextColor color, int advance) {
        title.append(Component.text(text).font(font).color(color));
        cursor += advance;
    }
    ShopTitle background(Shop.Kind kind) {
        moveTo(0);
        put(GUI, kind == Shop.Kind.SELL ? "" : "", NamedTextColor.WHITE, BACKGROUND_ADVANCE);
        return this;
    }
    ShopTitle selectedTab(int row) {
        moveTo(4);
        put(GUI, String.valueOf((char) (0xE210 + row)), NamedTextColor.WHITE, TAB_ADVANCE);
        return this;
    }
    /** Right-aligned coin balance in the header. */
    ShopTitle coins(long coins) {
        String text = String.format("%,d", coins);
        moveTo(152 - text.length() * LARGE_ADVANCE);
        put(HEADER, text, TextColor.color(0xFFD860), text.length() * LARGE_ADVANCE);
        return this;
    }
    ShopTitle amount(int amount) {
        String text = String.valueOf(amount);
        moveTo(50);
        put(FOOTER, text, TextColor.color(0xFFF2D0), text.length() * LARGE_ADVANCE);
        return this;
    }
    /** Price beside the icon at the given grid row and column of the 9-wide slot layout. */
    ShopTitle price(int row, int column, long price, long change) {
        String number = String.valueOf(price);
        moveTo(TITLE_X + column * 18 - 1);
        Key font = Key.key("maitycoon", "row" + row);
        put(font, number, TextColor.color(0x3A2818), number.length() * SMALL_ADVANCE);
        if (change > 0) put(font, "▲", NamedTextColor.RED, SMALL_ADVANCE);
        else if (change < 0) put(font, "▼", NamedTextColor.BLUE, SMALL_ADVANCE);
        return this;
    }
    Component build() { return title.build(); }
}
