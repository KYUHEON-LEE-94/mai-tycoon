package kr.maitycoon;

import java.util.*;

/** coins is YD, the trading currency. zcoin is a separate reward currency and never converts into YD. */
public record Profile(long coins, long zcoin, long experience, boolean starterClaimed, Set<String> dex, Set<Integer> dexRewards, boolean landPermit) {
    public Profile {
        if (coins < 0 || zcoin < 0 || experience < 0) throw new IllegalArgumentException("Negative profile value");
        dex = Set.copyOf(dex);
        dexRewards = Set.copyOf(dexRewards);
    }
    public Profile(long coins, long experience, boolean starterClaimed) {
        this(coins, 0, experience, starterClaimed, Set.of(), Set.of(), false);
    }
    public static Profile empty() { return new Profile(0, 0, false); }
    public Profile sell(long income) {
        if (income <= 0) throw new IllegalArgumentException("Invalid sale");
        return new Profile(Math.addExact(coins, income), zcoin, experience, starterClaimed, dex, dexRewards, landPermit);
    }
    public Profile spend(long cost) {
        if (cost <= 0) throw new IllegalArgumentException("Invalid purchase");
        return new Profile(coins - cost, zcoin, experience, starterClaimed, dex, dexRewards, landPermit);
    }
    public Profile claimStarter() { return new Profile(coins, zcoin, experience, true, dex, dexRewards, landPermit); }
    public Profile collect(String id) {
        Set<String> next = new HashSet<>(dex);
        if (!next.add(id)) throw new IllegalArgumentException("Already registered");
        return new Profile(coins, zcoin, experience, starterClaimed, next, dexRewards, landPermit);
    }
    public Profile claimReward(int tier, Dex.Reward reward) {
        Set<Integer> next = new HashSet<>(dexRewards);
        if (!next.add(tier)) throw new IllegalArgumentException("Reward already claimed");
        return new Profile(Math.addExact(coins, reward.coins()), Math.addExact(zcoin, reward.zcoin()), experience, starterClaimed, dex, next, landPermit || reward.landPermit());
    }
}
