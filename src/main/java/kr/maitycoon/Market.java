package kr.maitycoon;

import java.util.*;
import java.util.random.RandomGenerator;

/** Independent uniform prices each round, with per-crop server-wide oversupply pressure. */
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
    private long revision;
    private final long volumeThreshold;
    private final int pressurePerBatch, maxPressure;
    private final Map<String, Long> sold = new HashMap<>();
    private final Map<String, Integer> lastPressure = new HashMap<>();
    private static final int TRAIL = 8;
    private final Map<String, Integer> percent = new HashMap<>(), previous = new HashMap<>();
    private final Map<String, List<Integer>> trail = new HashMap<>();
    public Market(Map<String, Rule> rules, int minPercent, int maxPercent) {
        this(rules, minPercent, maxPercent, 256, 10, 60);
    }
    public Market(Map<String, Rule> rules, int minPercent, int maxPercent, long volumeThreshold, int pressurePerBatch, int maxPressure) {
        if (volumeThreshold < 1 || pressurePerBatch < 1 || maxPressure < pressurePerBatch || maxPressure > 90)
            throw new IllegalArgumentException("Invalid supply pressure settings");
        this.volumeThreshold = volumeThreshold;
        this.pressurePerBatch = pressurePerBatch;
        this.maxPressure = maxPressure;
        if (minPercent < 1 || minPercent > 100 || maxPercent < 100 || maxPercent > 1000) throw new IllegalArgumentException("Invalid market bounds");
        this.rules = Collections.unmodifiableMap(new LinkedHashMap<>(rules));
        this.minPercent = minPercent;
        this.maxPercent = maxPercent;
        for (String id : rules.keySet()) { percent.put(id, 100); previous.put(id, 100); trail.put(id, new ArrayList<>(List.of(100))); }
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
    /** Recent percents, oldest first, for the price graph. */
    public List<Integer> trail(String id) { return List.copyOf(trail.get(id)); }
    public int minPercent() { return minPercent; }
    /** Changes on every price step, so a quote can tell it is out of date. */
    public long revision() { return revision; }
    public int maxPercent() { return maxPercent; }
    private long priceAt(String id, int atPercent) {
        return Math.max(1, Math.round(rules.get(id).base() * atPercent / 100.0));
    }
    public void restore(String id, int current, int before) {
        if (!has(id)) return;
        percent.put(id, clamp(current));
        previous.put(id, clamp(before));
    }
    public void restoreTrail(String id, List<Integer> values) {
        if (!has(id) || values.isEmpty()) return;
        List<Integer> restored = new ArrayList<>();
        for (int value : values.subList(Math.max(0, values.size() - TRAIL), values.size())) restored.add(clamp(value));
        trail.put(id, restored);
    }
    public long sold(String id) { return sold.getOrDefault(id, 0L); }
    public int lastPressure(String id) { return lastPressure.getOrDefault(id, 0); }
    public int pendingPressure(String id) {
        long batches = sold(id) / volumeThreshold;
        return (int) Math.min(maxPressure, Math.min(batches, maxPressure) * pressurePerBatch);
    }
    /** Further sales needed to raise the pressure, or 0 once it is at the maximum. */
    public long untilNextPressure(String id) { return pendingPressure(id) >= maxPressure ? 0 : volumeThreshold - sold(id) % volumeThreshold; }
    public void recordSales(Map<String, Long> counts) {
        for (var entry : counts.entrySet()) {
            if (!has(entry.getKey()) || entry.getValue() == null || entry.getValue() < 0)
                throw new IllegalArgumentException("Invalid sale volume");
        }
        counts.forEach((id, count) -> sold.put(id, count > Long.MAX_VALUE - sold(id) ? Long.MAX_VALUE : sold(id) + count));
    }
    public void restoreSupply(String id, long count, int pressure) {
        if (!has(id)) return;
        sold.put(id, Math.max(0, count));
        lastPressure.put(id, Math.max(0, Math.min(maxPressure, pressure)));
    }
    public List<Move> step(RandomGenerator random) {
        List<Move> moves = new ArrayList<>();
        revision++;
        for (var entry : rules.entrySet()) {
            String id = entry.getKey();
            int current = percent.get(id), pressure = pendingPressure(id);
            long oldPrice = price(id);
            // The full range is sampled afresh, independent of the previous price.
            int next = random.nextInt(minPercent, maxPercent + 1);
            if (pressure > 0) {
                next = clamp(next * (100 - pressure) / 100);
                // Heavy sales must actually lower the next 골드 price, unless already at the floor.
                long target = Math.max(floor(id), oldPrice - Math.max(1, Math.round(oldPrice * pressure / 100.0)));
                while (next > minPercent && priceAt(id, next) > target) next--;
            }
            previous.put(id, current);
            percent.put(id, next);
            lastPressure.put(id, pressure);
            sold.put(id, 0L);
            List<Integer> recent = trail.get(id);
            recent.add(percent.get(id));
            if (recent.size() > TRAIL) recent.remove(0);
            moves.add(new Move(id, price(id), change(id)));
        }
        return moves;
    }
    private int clamp(int value) { return Math.max(minPercent, Math.min(maxPercent, value)); }
}
