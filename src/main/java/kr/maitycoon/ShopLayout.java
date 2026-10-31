package kr.maitycoon;

import java.util.Set;

/**
 * The 54-slot shop contract shared by both renderers and tools/GeneratePack.java.
 * Column 0 of rows 0-4 holds tabs; columns 1/3/5/7 hold items and the column to the right is that item's price cell.
 */
public final class ShopLayout {
    public static final int SIZE = 54, COLUMNS = 9, ROWS = 5, PAGE_SIZE = ROWS * 4, MAX_TABS = ROWS, PREV = 45, NEXT = 53;
    public static final int GUI_WIDTH = 176, GUI_HEIGHT = 128, SLOT = 18, LEFT = 8, TOP = 18, FOOTER_ROW = 5;
    public static final Set<Integer> AMOUNT = Set.of(46, 47, 48), ACTION = Set.of(49, 50, 51, 52);
    private ShopLayout() {}
    public static int tabSlot(int tab) { return tab * COLUMNS; }
    public static int tabAt(int slot) { return slot >= 0 && slot < ROWS * COLUMNS && slot % COLUMNS == 0 ? slot / COLUMNS : -1; }
    public static int row(int index) { return index / 4; }
    public static int itemColumn(int index) { return 1 + index % 4 * 2; }
    public static int itemSlot(int index) { return row(index) * COLUMNS + itemColumn(index); }
    public static int priceSlot(int index) { return itemSlot(index) + 1; }
    /** Page index of the item whose icon or price cell is at this slot, or -1. */
    public static int itemAt(int slot) {
        if (slot < 0 || slot >= ROWS * COLUMNS || slot % COLUMNS == 0) return -1;
        return slot / COLUMNS * 4 + (slot % COLUMNS - 1) / 2;
    }
    public static int slotX(int column) { return LEFT + column * SLOT; }
    public static int slotY(int row) { return TOP + row * SLOT; }
}
