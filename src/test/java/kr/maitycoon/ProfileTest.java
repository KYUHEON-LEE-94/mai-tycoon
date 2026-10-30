package kr.maitycoon;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ProfileTest {
    @Test void saleAddsCurrencyWithoutInventingExperience() {
        assertEquals(new Profile(180, 0, true), new Profile(100, 0, true).sell(80));
    }
    @Test void purchaseCannotOverdraw() {
        assertEquals(new Profile(30, 0, false), new Profile(50, 0, false).spend(20));
        assertThrows(IllegalArgumentException.class, () -> new Profile(10, 0, false).spend(11));
        assertThrows(IllegalArgumentException.class, () -> new Profile(10, 0, false).spend(0));
    }
    @Test void rejectsInvalidAndOverflowingTransactions() {
        assertThrows(IllegalArgumentException.class, () -> Profile.empty().sell(-1));
        assertThrows(IllegalArgumentException.class, () -> new Profile(-1, 0, false));
        assertThrows(ArithmeticException.class, () -> new Profile(Long.MAX_VALUE, 0, false).sell(1));
    }
}
