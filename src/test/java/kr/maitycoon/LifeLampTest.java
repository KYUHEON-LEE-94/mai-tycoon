package kr.maitycoon;
import java.util.*;
import kr.maitycoon.LifeLamp.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class LifeLampTest {
    private static final LifeLamp LAMP = new LifeLamp(Map.of(
        Kind.CROP, new Range(20, 50, 1, 3), Kind.SEED, new Range(20, 50, 1, 3), Kind.BONE_MEAL, new Range(10, 50, 1, 2)));
    @Test void rollsStayInsideTheConfiguredRanges() {
        var random = new Random(11);
        Set<Kind> seen = EnumSet.noneOf(Kind.class);
        for (int i = 0; i < 20_000; i++) {
            Option option = LAMP.roll(random);
            seen.add(option.kind());
            assertTrue(option.percent() >= (option.kind() == Kind.BONE_MEAL ? 10 : 20) && option.percent() <= 50);
            assertTrue(option.amount() >= 1 && option.amount() <= (option.kind() == Kind.BONE_MEAL ? 2 : 3));
        }
        assertEquals(EnumSet.allOf(Kind.class), seen);
    }
    @Test void probabilityBoundaries() {
        var random = new Random(5);
        for (int i = 0; i < 2_000; i++) {
            assertEquals(0, new Option(Kind.CROP, 0, 3).bonus(random));
            assertEquals(3, new Option(Kind.CROP, 100, 3).bonus(random));
        }
    }
    @Test void hitRateMatchesThePercent() {
        var random = new Random(9);
        int hits = 0;
        for (int i = 0; i < 100_000; i++) if (new Option(Kind.SEED, 35, 1).bonus(random) > 0) hits++;
        assertEquals(35_000, hits, 1_000);
    }
    @Test void encodingRoundTripsAndRejectsDamage() {
        Option option = new Option(Kind.SEED, 42, 2);
        assertEquals(option, Option.decode(option.encode()));
        for (String bad : new String[]{null, "", "1:SEED", "9:SEED:42:2", "1:NOPE:42:2", "1:SEED:x:2"}) assertNull(Option.decode(bad));
    }
    @Test void rejectsInvalidRanges() {
        assertThrows(IllegalArgumentException.class, () -> new Range(60, 50, 1, 3));
        assertThrows(IllegalArgumentException.class, () -> new Range(10, 101, 1, 3));
        assertThrows(IllegalArgumentException.class, () -> new Range(10, 50, 0, 3));
        assertThrows(IllegalArgumentException.class, () -> new LifeLamp(Map.of()));
    }
}
