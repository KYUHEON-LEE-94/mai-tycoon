package kr.maitycoon;

import java.util.*;

/** Who owns which chunk and who may do what there. Keys are "worldUuid:chunkX:chunkZ"; workers belong to an owner, across all their land. */
public final class LandRegistry {
    public enum Claim { OK, TAKEN, LIMIT }
    public enum Role { OWNER, WORKER, VISITOR }
    public enum Action { BUILD, USE, TILL, PLANT, HARVEST }
    public record Parcel(UUID owner, long createdAt) {}
    private static final Set<Action> WORKER_ACTIONS = EnumSet.of(Action.TILL, Action.PLANT, Action.HARVEST);
    private final Map<String, Parcel> parcels = new HashMap<>();
    private final Map<UUID, Set<UUID>> workers = new HashMap<>();
    /** A chunk is always 16 blocks wide. The shift rounds down, so block -1 is in chunk -1, not chunk 0. */
    public static final int CHUNK_SIZE = 16;
    public static int chunkOf(int block) { return block >> 4; }
    public static int firstBlock(int chunk) { return chunk * CHUNK_SIZE; }
    public static String key(String world, int chunkX, int chunkZ) { return world + ":" + chunkX + ":" + chunkZ; }
    public static long price(long base, long step, int owned) { return Math.addExact(base, Math.multiplyExact(step, owned)); }
    public UUID owner(String key) {
        Parcel parcel = parcels.get(key);
        return parcel == null ? null : parcel.owner();
    }
    public Map<String, Parcel> parcels() { return Collections.unmodifiableMap(parcels); }
    public int count(UUID owner) { return (int) parcels.values().stream().filter(parcel -> parcel.owner().equals(owner)).count(); }
    public Claim check(String key, UUID owner, int limit) {
        if (parcels.containsKey(key)) return Claim.TAKEN;
        return count(owner) >= limit ? Claim.LIMIT : Claim.OK;
    }
    public void put(String key, UUID owner, long createdAt) { parcels.put(key, new Parcel(owner, createdAt)); }
    public void put(String key, UUID owner) { put(key, owner, System.currentTimeMillis()); }
    public void remove(String key) { parcels.remove(key); }
    public Set<UUID> workers(UUID owner) { return Collections.unmodifiableSet(workers.getOrDefault(owner, Set.of())); }
    public boolean addWorker(UUID owner, UUID worker) {
        if (owner.equals(worker)) throw new IllegalArgumentException("Owner cannot be a worker");
        return workers.computeIfAbsent(owner, id -> new TreeSet<>()).add(worker);
    }
    public boolean removeWorker(UUID owner, UUID worker) {
        Set<UUID> current = workers.get(owner);
        return current != null && current.remove(worker);
    }
    /** null when the chunk has no owner. */
    public Role role(String key, UUID actor) {
        UUID owner = owner(key);
        if (owner == null) return null;
        if (owner.equals(actor)) return Role.OWNER;
        return workers(owner).contains(actor) ? Role.WORKER : Role.VISITOR;
    }
    /** Visitors may take crops, which is the theft the footprint log records; everything else stays protected. */
    public boolean allows(String key, UUID actor, Action action) {
        Role role = role(key, actor);
        if (role == null || role == Role.OWNER) return true;
        return role == Role.WORKER ? WORKER_ACTIONS.contains(action) : action == Action.HARVEST;
    }
    public boolean isTheft(String key, UUID actor) { return role(key, actor) == Role.VISITOR; }
    /** True when something crossing between these chunks would reach land its source does not belong to. */
    public boolean crossesInto(String from, String to) {
        UUID target = owner(to);
        return target != null && !target.equals(owner(from));
    }
}
