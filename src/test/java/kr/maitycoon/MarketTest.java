package kr.maitycoon;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class MarketTest {
    private static Market market() {
        var rules = new LinkedHashMap<String, Market.Rule>();
        rules.put("WHEAT", new Market.Rule(8, 5));
        rules.put("MELON_SLICE", new Market.Rule(3, 12));
        return new Market(rules, 50, 180);
    }
    @Test void pricesStayInsideBoundsAndKeepMoving() {
        Market market = market();
        var random = new Random(42);
        Set<Long> seen = new HashSet<>();
        for (int i = 0; i < 20_000; i++) {
            market.step(random);
            long price = market.price("WHEAT");
            assertTrue(price >= market.floor("WHEAT") && price <= market.ceiling("WHEAT"));
            assertTrue(market.price("MELON_SLICE") >= 1);
            seen.add(price);
        }
        assertTrue(seen.size() > 3, "시세가 실제로 변동해야 합니다");
    }
    @Test void samplesWholeRangeIndependently() {
        Market market = market();
        var random = new Random(7);
        long total = 0;
        for (int i = 0; i < 20_000; i++) { market.step(random); total += market.percent("WHEAT"); }
        assertEquals(115, total / 20_000.0, 2);
    }
    @Test void restoreClampsAndReportsChange() {
        Market market = market();
        market.restore("WHEAT", 500, 100);
        assertEquals(180, market.percent("WHEAT"));
        assertEquals(14, market.price("WHEAT"));
        assertEquals(6, market.change("WHEAT"));
        market.restore("UNKNOWN", 100, 100);
        assertFalse(market.has("UNKNOWN"));
    }
    @Test void reportsSalesLeftUntilTheNextPressureStep() {
        Market m = market();
        assertEquals(256, m.untilNextPressure("WHEAT"));
        m.recordSales(Map.of("WHEAT", 250L));
        assertEquals(6, m.untilNextPressure("WHEAT"));
        m.recordSales(Map.of("WHEAT", 6L));
        assertEquals(256, m.untilNextPressure("WHEAT"));
        m.recordSales(Map.of("WHEAT", 256L * 10));
        assertEquals(0, m.untilNextPressure("WHEAT"));
    }
    @Test void trailKeepsRecentPricesInOrder() {
        Market market = market();
        var random = new Random(3);
        for (int i = 0; i < 20; i++) market.step(random);
        List<Integer> trail = market.trail("WHEAT");
        assertEquals(8, trail.size());
        assertEquals(market.percent("WHEAT"), trail.get(trail.size() - 1));
        market.restoreTrail("WHEAT", List.of(999, 100));
        assertEquals(List.of(180, 100), market.trail("WHEAT"));
    }
    @Test void previousPriceDoesNotAffectAnUnsoldCrop() {
        Market low = market(), high = market();
        low.restore("WHEAT", 50, 50);
        high.restore("WHEAT", 180, 180);
        low.step(new Random(42)); high.step(new Random(42));
        assertEquals(low.percent("WHEAT"), high.percent("WHEAT"));
    }
    @Test void salesAcrossPlayersAccumulateAndHeavySalesAlwaysLowerPrice() {
        for (int seed = 0; seed < 100; seed++) {
            Market m = market();
            m.restore("WHEAT", 150, 100);
            long before = m.price("WHEAT");
            m.recordSales(Map.of("WHEAT", 128L));
            assertEquals(0, m.pendingPressure("WHEAT"));
            m.recordSales(Map.of("WHEAT", 128L));
            assertEquals(10, m.pendingPressure("WHEAT"));
            m.step(new Random(seed));
            assertTrue(m.price("WHEAT") < before);
            assertTrue(m.price("WHEAT") >= m.floor("WHEAT"));
            assertEquals(0, m.sold("WHEAT"));
            assertEquals(10, m.lastPressure("WHEAT"));
        }
    }
    @Test void pressureIsPerCropAndExpiresAfterOneRound() {
        Market m = market(), reference = market();
        m.recordSales(Map.of("WHEAT", Long.MAX_VALUE));
        m.recordSales(Map.of("WHEAT", 64L));
        assertEquals(60, m.pendingPressure("WHEAT"));
        m.step(new Random(42)); reference.step(new Random(42));
        assertEquals(reference.percent("MELON_SLICE"), m.percent("MELON_SLICE"));
        m.step(new Random(99)); reference.step(new Random(99));
        assertEquals(reference.percent("WHEAT"), m.percent("WHEAT"));
    }
    @Test void supplyCanBeRestoredAndFloorIsRespected() {
        Market m = market();
        m.restoreSupply("WHEAT", 512, 10);
        assertEquals(20, m.pendingPressure("WHEAT"));
        m.restore("WHEAT", 50, 50);
        m.step(new Random(1));
        assertEquals(m.floor("WHEAT"), m.price("WHEAT"));
        assertThrows(IllegalArgumentException.class, () -> m.recordSales(Map.of("WHEAT", -1L)));
        assertThrows(IllegalArgumentException.class, () -> m.recordSales(Map.of("UNKNOWN", 1L)));
    }
    @Test void rejectsInvalidRules() {
        assertThrows(IllegalArgumentException.class, () -> new Market.Rule(0, 5));
        assertThrows(IllegalArgumentException.class, () -> new Market.Rule(5, 0));
        assertThrows(IllegalArgumentException.class, () -> new Market(Map.of(), 120, 180));
    }
}
