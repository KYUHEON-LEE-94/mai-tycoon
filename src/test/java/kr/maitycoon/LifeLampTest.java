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
            Option option = LAMP.roll(Tool.HOE, random);
            seen.add(option.kind());
            assertTrue(option.percent() >= (option.kind() == Kind.BONE_MEAL ? 10 : 20) && option.percent() <= 50);
            assertTrue(option.amount() >= 1 && option.amount() <= (option.kind() == Kind.BONE_MEAL ? 2 : 3));
        }
        assertEquals(EnumSet.of(Kind.CROP, Kind.SEED, Kind.BONE_MEAL), seen);
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
    private static final LifeLamp FULL;
    static {
        Map<Kind, Range> ranges = new EnumMap<>(Kind.class);
        for (Kind kind : Kind.values()) ranges.put(kind, new Range(10, 50, 2, 4));
        FULL = new LifeLamp(ranges);
    }
    @Test void aToolOnlyRollsItsOwnAndCommonOptions() {
        var random = new Random(3);
        for (Tool tool : Tool.values()) {
            Set<Kind> seen = EnumSet.noneOf(Kind.class);
            for (int i = 0; i < 5_000; i++) seen.add(FULL.roll(tool, random).kind());
            assertEquals(EnumSet.copyOf(FULL.kinds(tool)), seen);
            assertTrue(seen.stream().allMatch(kind -> kind.fits(tool)));
        }
        assertTrue(FULL.kinds(Tool.AXE).contains(Kind.TREE) && !FULL.kinds(Tool.AXE).contains(Kind.CROP));
        assertTrue(FULL.kinds(Tool.AXE).contains(Kind.MONSTER));
    }
    @Test void toolTypeComesFromTheMaterialName() {
        assertEquals(Tool.PICKAXE, Tool.of("IRON_PICKAXE"));
        assertEquals(Tool.AXE, Tool.of("NETHERITE_AXE"));
        assertEquals(Tool.SHOVEL, Tool.of("WOODEN_SHOVEL"));
        assertEquals(Tool.HOE, Tool.of("STONE_HOE"));
        assertNull(Tool.of("DIAMOND_SWORD"));
        assertNull(Tool.of("MACE"));
    }
    @Test void areaMiningHasOnlyItsTwoShapes() {
        var random = new Random(4);
        for (int i = 0; i < 5_000; i++) {
            Option option = FULL.roll(Tool.PICKAXE, random);
            if (option.kind() == Kind.AREA) assertTrue(option.amount() == 2 || option.amount() == 4);
        }
        assertEquals(2, LifeLamp.areaOffsets(2, 0, -1).size());
        var north = LifeLamp.areaOffsets(4, 0, -1);
        assertArrayEquals(new int[]{-1, 0, 0}, north.get(2));
        assertArrayEquals(new int[]{1, 0, 0}, north.get(3));
        var east = LifeLamp.areaOffsets(4, 1, 0);
        assertArrayEquals(new int[]{0, 0, -1}, east.get(2));
        assertArrayEquals(new int[]{0, 0, 1}, east.get(3));
    }
    @Test void textsFillInTheirNumbers() {
        assertEquals("작물 수확 시 30% 확률로 작물 2개 추가", new Option(Kind.CROP, 30, 2).describe());
        assertEquals("상·하 1칸을 함께 캡니다. 야생 전용", new Option(Kind.AREA, 100, 2).describe());
        assertEquals("상·하·좌·우 1칸을 함께 캡니다. 야생 전용", new Option(Kind.AREA, 100, 4).describe());
        assertEquals("작물 수확 시 10~50% 확률로 작물 2~4개 추가", FULL.range(Kind.CROP));
        for (Kind kind : Kind.values()) assertFalse(FULL.range(kind).contains("{"));
    }
    @Test void everyToolMustHaveAnOption() {
        assertThrows(IllegalArgumentException.class, () -> new LifeLamp(Map.of(Kind.CROP, new Range(20, 50, 1, 3))));
    }
    @Test void rejectsInvalidRanges() {
        assertThrows(IllegalArgumentException.class, () -> new Range(60, 50, 1, 3));
        assertThrows(IllegalArgumentException.class, () -> new Range(10, 101, 1, 3));
        assertThrows(IllegalArgumentException.class, () -> new Range(10, 50, 0, 3));
        assertThrows(IllegalArgumentException.class, () -> new LifeLamp(Map.of()));
    }
}
