package kr.maitycoon;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ShopLayoutTest {
    @Test void everyItemOwnsItsIconAndPriceCell() {
        Set<Integer> used = new HashSet<>();
        for (int i = 0; i < ShopLayout.PAGE_SIZE; i++) {
            assertEquals(i, ShopLayout.itemAt(ShopLayout.itemSlot(i)));
            assertEquals(i, ShopLayout.itemAt(ShopLayout.priceSlot(i)));
            assertTrue(used.add(ShopLayout.itemSlot(i)));
            assertTrue(used.add(ShopLayout.priceSlot(i)));
        }
        assertEquals(16, used.size());
    }
    @Test void regionsDoNotOverlap() {
        for (int slot = 0; slot < ShopLayout.SIZE; slot++) {
            int regions = (ShopLayout.tabAt(slot) >= 0 ? 1 : 0) + (ShopLayout.itemAt(slot) >= 0 ? 1 : 0)
                + (ShopLayout.AMOUNT.contains(slot) ? 1 : 0) + (ShopLayout.ACTION.contains(slot) ? 1 : 0)
                + (slot == ShopLayout.PREV || slot == ShopLayout.NEXT ? 1 : 0);
            assertTrue(regions <= 1, "Overlapping actions at slot " + slot);
        }
    }
    @Test void menuButtonsNeverShareASlot() {
        for (var screen : List.of(
            List.of(List.of(MenuLayout.SHOP_ICON), List.of(MenuLayout.SHOP_CLOSE), MenuLayout.SHOP_BUY, MenuLayout.SHOP_USE),
            List.of(List.of(MenuLayout.USE_TARGET), List.of(MenuLayout.USE_BACK), MenuLayout.USE_LIST, MenuLayout.USE_APPLY),
            List.of(MenuLayout.LAND_BORDER, MenuLayout.LAND_HOME, MenuLayout.LAND_FARM, MenuLayout.LAND_CONFIRM),
            List.of(MenuLayout.FARM_WORKERS, MenuLayout.FARM_PARCELS, MenuLayout.FARM_BACK, MenuLayout.FARM_STATUS, MenuLayout.FARM_AUTO),
            List.of(MenuLayout.BUY_YES, MenuLayout.BUY_NO))) {
            Set<Integer> used = new HashSet<>();
            for (List<Integer> button : screen) for (int slot : button) assertTrue(slot >= 0 && slot < MenuLayout.SIZE && used.add(slot), "Slot " + slot);
        }
        for (int row = 0; row < MenuLayout.MAP_ROWS; row++) for (int column = 0; column < MenuLayout.MAP_COLUMNS; column++) {
            assertTrue(MenuLayout.mapSlot(column, row) < 45, "The map stays above the buttons");
            assertNotEquals(MenuLayout.LAND_SUMMARY, MenuLayout.mapSlot(column, row));
        }
    }
    @Test void everyToolAndOptionHasALabel() {
        for (var kind : LifeLamp.Kind.values()) assertTrue(MenuLayout.LABEL_IDS.contains("KIND_" + kind));
        for (String material : List.of("WOODEN", "STONE", "COPPER", "IRON", "GOLDEN", "DIAMOND", "NETHERITE")) for (var tool : LifeLamp.Tool.values()) assertTrue(MenuLayout.LABEL_IDS.contains(material + "_" + tool));
        assertEquals(new HashSet<>(MenuLayout.LABEL_IDS).size(), MenuLayout.LABEL_IDS.size());
        assertEquals(MenuLayout.LABEL_IDS.size(), MenuLayout.LABEL_TEXTS.size());
    }
    @Test void textHeightsRoundTrip() {
        for (int line = 0; line < MenuLayout.TEXT_LINES; line++) assertEquals(line, MenuLayout.textLine(MenuLayout.textY(line)));
        assertThrows(IllegalArgumentException.class, () -> MenuLayout.textLine(500));
    }
    @Test void coversOutOfRangeSlots() {
        assertEquals(-1, ShopLayout.itemAt(-999));
        assertEquals(-1, ShopLayout.itemAt(54 + 3));
        assertEquals(-1, ShopLayout.tabAt(45));
    }
}
