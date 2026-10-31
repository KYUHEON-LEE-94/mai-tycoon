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
    @Test void coversOutOfRangeSlots() {
        assertEquals(-1, ShopLayout.itemAt(-999));
        assertEquals(-1, ShopLayout.itemAt(54 + 3));
        assertEquals(-1, ShopLayout.tabAt(45));
    }
}
