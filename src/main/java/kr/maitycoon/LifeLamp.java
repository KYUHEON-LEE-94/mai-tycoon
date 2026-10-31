package kr.maitycoon;

import java.util.*;
import java.util.random.RandomGenerator;

/** Life lamp options for hoes. Ranges come from config; the option itself is stored on the tool. */
public final class LifeLamp {
    public enum Kind {
        CROP("추가 작물"), SEED("추가 씨앗"), BONE_MEAL("추가 뼛가루");
        public final String label;
        Kind(String label) { this.label = label; }
    }
    public record Range(int minPercent, int maxPercent, int minAmount, int maxAmount) {
        public Range {
            if (minPercent < 0 || maxPercent > 100 || minPercent > maxPercent || minAmount < 1 || minAmount > maxAmount) throw new IllegalArgumentException("Invalid lamp range");
        }
    }
    public record Option(Kind kind, int percent, int amount) {
        private static final int SCHEMA = 1;
        public String describe() { return kind.label + " " + percent + "% 확률로 " + amount + "개"; }
        public String encode() { return SCHEMA + ":" + kind + ":" + percent + ":" + amount; }
        /** null for anything unreadable, so a damaged tag behaves like a hoe without an option. */
        public static Option decode(String value) {
            if (value == null) return null;
            String[] parts = value.split(":");
            try {
                if (parts.length != 4 || Integer.parseInt(parts[0]) != SCHEMA) return null;
                return new Option(Kind.valueOf(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
            } catch (IllegalArgumentException e) { return null; }
        }
        /** Extra items for one harvest. */
        public int bonus(RandomGenerator random) { return random.nextInt(100) < percent ? amount : 0; }
    }
    private final Map<Kind, Range> ranges;
    public LifeLamp(Map<Kind, Range> ranges) {
        if (ranges.isEmpty()) throw new IllegalArgumentException("No lamp options");
        this.ranges = new EnumMap<>(ranges);
    }
    public Option roll(RandomGenerator random) {
        List<Kind> kinds = List.copyOf(ranges.keySet());
        Kind kind = kinds.get(random.nextInt(kinds.size()));
        Range range = ranges.get(kind);
        return new Option(kind, random.nextInt(range.minPercent(), range.maxPercent() + 1), random.nextInt(range.minAmount(), range.maxAmount() + 1));
    }
}
