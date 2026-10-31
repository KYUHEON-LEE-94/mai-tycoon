package kr.maitycoon;

import java.util.*;

/** Collection book: pages of item ids, plus rewards that are granted when a count is reached. */
public final class Dex {
    public record Reward(long coins, long zcoin, boolean landPermit) {
        public Reward { if (coins < 0 || zcoin < 0) throw new IllegalArgumentException("Invalid reward"); }
        public String describe() {
            List<String> parts = new ArrayList<>();
            if (coins > 0) parts.add(coins + " YD");
            if (zcoin > 0) parts.add("즈코인 " + zcoin + "개");
            if (landPermit) parts.add("땅 구매 이용권");
            return String.join(" + ", parts);
        }
    }
    private final Map<String, List<String>> pages;
    private final NavigableMap<Integer, Reward> rewards;
    private final Set<String> ids = new HashSet<>();
    public Dex(Map<String, List<String>> pages, Map<Integer, Reward> rewards) {
        this.pages = Collections.unmodifiableMap(new LinkedHashMap<>(pages));
        this.rewards = Collections.unmodifiableNavigableMap(new TreeMap<>(rewards));
        for (List<String> page : pages.values()) for (String id : page) {
            if (!ids.add(id)) throw new IllegalArgumentException("Duplicate dex entry " + id);
        }
    }
    public Map<String, List<String>> pages() { return pages; }
    public NavigableMap<Integer, Reward> rewards() { return rewards; }
    public int total() { return ids.size(); }
    public boolean has(String id) { return ids.contains(id); }
    public Profile register(Profile profile, String id) {
        if (!has(id)) throw new IllegalArgumentException("Not a dex entry");
        Profile next = profile.collect(id);
        for (var entry : rewards.headMap(next.dex().size(), true).entrySet()) {
            if (!next.dexRewards().contains(entry.getKey())) next = next.claimReward(entry.getKey(), entry.getValue());
        }
        return next;
    }
}
