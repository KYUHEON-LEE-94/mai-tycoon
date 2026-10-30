package kr.maitycoon;

import java.util.*;
import java.util.random.RandomGenerator;

/** Bounded random walk per item: swings by up to `swing` percent per step and drifts back toward 100%. */
public final class Market {
    public record Rule(long base, int swing) {
        public Rule {
            if (base < 1 || base > 1_000_000) throw new IllegalArgumentException("Invalid base price");
            if (swing < 1 || swing > 50) throw new IllegalArgumentException("Invalid swing");
        }
    }
    public record Move(String id, long price, long change) {}
    private final Map<String, Rule> rules;
    private final int minPercent, maxPercent;
    private final Map<String, Integer> percent = new HashMap<>(), previous = new HashMap<>();
    public Market(Map<String, Rule> rules, int minPercent, int maxPercent) {
        if (minPercent < 1 || minPercent > 100 || maxPercent < 100 || maxPercent > 1000) throw new IllegalArgumentException("Invalid market bounds");
        this.rules = Collections.unmodifiableMap(new LinkedHashMap<>(rules));
        this.minPercent = minPercent;
        this.maxPercent = maxPercent;
        for (String id : rules.keySet()) { percent.put(id, 100); previous.put(id, 100); }
    }
    public Set<String> ids() { return rules.keySet(); }
    public boolean has(String id) { return rules.containsKey(id); }
    public long price(String id) { return priceAt(id, percent.get(id)); }
    public long base(String id) { return rules.get(id).base(); }
    public long ceiling(String id) { return priceAt(id, maxPercent); }
    public long floor(String id) { return priceAt(id, minPercent); }
    public long change(String id) { return price(id) - priceAt(id, previous.get(id)); }
    public int percent(String id) { return percent.get(id); }
    public int previousPercent(String id) { return previous.get(id); }
    private long priceAt(String id, int atPercent) {
        return Math.max(1, Math.round(rules.get(id).base() * atPercent / 100.0));
    }
    public void restore(String id, int current, int before) {
        if (!has(id)) return;
        percent.put(id, clamp(current));
        previous.put(id, clamp(before));
    }
    public List<Move> step(RandomGenerator random) {
        List<Move> moves = new ArrayList<>();
        for (var entry : rules.entrySet()) {
            String id = entry.getKey();
            int swing = entry.getValue().swing(), current = percent.get(id);
            int pull = (100 - current) / 8;
            previous.put(id, current);
            percent.put(id, clamp(current + pull + random.nextInt(-swing, swing + 1)));
            moves.add(new Move(id, price(id), change(id)));
        }
        return moves;
    }
    private int clamp(int value) { return Math.max(minPercent, Math.min(maxPercent, value)); }
}
