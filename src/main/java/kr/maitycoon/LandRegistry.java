package kr.maitycoon;

import java.util.*;

/** Who owns which chunk. Keys are "world:chunkX:chunkZ". */
public final class LandRegistry {
    public enum Claim { OK, TAKEN, LIMIT }
    private final Map<String, UUID> owners = new HashMap<>();
    public static String key(String world, int chunkX, int chunkZ) { return world + ":" + chunkX + ":" + chunkZ; }
    public static long price(long base, long step, int owned) { return Math.addExact(base, Math.multiplyExact(step, owned)); }
    public UUID owner(String key) { return owners.get(key); }
    public Map<String, UUID> owners() { return Collections.unmodifiableMap(owners); }
    public int count(UUID owner) { return (int) owners.values().stream().filter(owner::equals).count(); }
    public Claim check(String key, UUID owner, int limit) {
        if (owners.containsKey(key)) return Claim.TAKEN;
        return count(owner) >= limit ? Claim.LIMIT : Claim.OK;
    }
    public void put(String key, UUID owner) { owners.put(key, owner); }
    public void remove(String key) { owners.remove(key); }
}
