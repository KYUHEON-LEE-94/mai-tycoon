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
    @Test void pullsBackTowardBasePrice() {
        Market market = market();
        var random = new Random(7);
        long total = 0;
        for (int i = 0; i < 20_000; i++) { market.step(random); total += market.percent("WHEAT"); }
        assertEquals(100, total / 20_000.0, 5);
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
    @Test void rejectsInvalidRules() {
        assertThrows(IllegalArgumentException.class, () -> new Market.Rule(0, 5));
        assertThrows(IllegalArgumentException.class, () -> new Market.Rule(5, 0));
        assertThrows(IllegalArgumentException.class, () -> new Market(Map.of(), 120, 180));
    }
}
