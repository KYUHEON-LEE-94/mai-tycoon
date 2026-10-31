package kr.maitycoon;

import java.util.*;

/** coins is 골드, the trading currency. zcoin is a separate reward currency and never converts into 골드. */
public record Profile(long coins, long zcoin, long experience, boolean starterClaimed, Set<String> dex, Set<Integer> dexRewards, boolean landPermit, long lamps, long replants, boolean autoPlant, boolean tractor) {
    public Profile {
        if (coins < 0 || zcoin < 0 || experience < 0 || lamps < 0 || replants < 0) throw new IllegalArgumentException("Negative profile value");
        dex = Set.copyOf(dex);
        dexRewards = Set.copyOf(dexRewards);
    }
    public Profile(long coins, long experience, boolean starterClaimed) {
        this(coins, 0, experience, starterClaimed, Set.of(), Set.of(), false, 0, 0, false, false);
    }
    public static Profile empty() { return new Profile(0, 0, false); }
    public Profile sell(long income) {
        if (income <= 0) throw new IllegalArgumentException("Invalid sale");
        return new Profile(Math.addExact(coins, income), zcoin, experience, starterClaimed, dex, dexRewards, landPermit, lamps, replants, autoPlant, tractor);
    }
    public Profile spend(long cost) {
        if (cost <= 0) throw new IllegalArgumentException("Invalid purchase");
        return new Profile(coins - cost, zcoin, experience, starterClaimed, dex, dexRewards, landPermit, lamps, replants, autoPlant, tractor);
    }
    public Profile useLamp() { return new Profile(coins, zcoin, experience, starterClaimed, dex, dexRewards, landPermit, lamps - 1, replants, autoPlant, tractor); }
    public Profile addLamps(long count) { return new Profile(coins, zcoin, experience, starterClaimed, dex, dexRewards, landPermit, Math.addExact(lamps, count), replants, autoPlant, tractor); }
    public Profile useReplant() { return new Profile(coins, zcoin, experience, starterClaimed, dex, dexRewards, landPermit, lamps, replants - 1, autoPlant, tractor); }
    public Profile addReplants(long count) { return new Profile(coins, zcoin, experience, starterClaimed, dex, dexRewards, landPermit, lamps, Math.addExact(replants, count), autoPlant, tractor); }
    /** Buying turns the feature on, since nobody buys uses to leave them idle. */
    public Profile buyReplants(long zcoinCost, long count) {
        if (zcoinCost <= 0 || count <= 0) throw new IllegalArgumentException("Invalid purchase");
        return new Profile(coins, zcoin - zcoinCost, experience, starterClaimed, dex, dexRewards, landPermit, lamps, Math.addExact(replants, count), true, tractor);
    }
    public Profile toggleAutoPlant() { return new Profile(coins, zcoin, experience, starterClaimed, dex, dexRewards, landPermit, lamps, replants, !autoPlant, tractor); }
    public Profile buyTractor(long zcoinCost) {
        if (zcoinCost <= 0 || tractor) throw new IllegalArgumentException("Invalid purchase");
        return new Profile(coins, zcoin - zcoinCost, experience, starterClaimed, dex, dexRewards, landPermit, lamps, replants, autoPlant, true);
    }
    public Profile addZcoin(long count) { return new Profile(coins, Math.addExact(zcoin, count), experience, starterClaimed, dex, dexRewards, landPermit, lamps, replants, autoPlant, tractor); }
    public Profile claimStarter() { return new Profile(coins, zcoin, experience, true, dex, dexRewards, landPermit, lamps, replants, autoPlant, tractor); }
    public Profile collect(String id) {
        Set<String> next = new HashSet<>(dex);
        if (!next.add(id)) throw new IllegalArgumentException("Already registered");
        return new Profile(coins, zcoin, experience, starterClaimed, next, dexRewards, landPermit, lamps, replants, autoPlant, tractor);
    }
    public Profile claimReward(int tier, Dex.Reward reward) {
        Set<Integer> next = new HashSet<>(dexRewards);
        if (!next.add(tier)) throw new IllegalArgumentException("Reward already claimed");
        return new Profile(Math.addExact(coins, reward.coins()), Math.addExact(zcoin, reward.zcoin()), experience, starterClaimed, dex, next, landPermit || reward.landPermit(), Math.addExact(lamps, reward.lamps()), replants, autoPlant, tractor);
    }
}
