package kr.maitycoon;

import java.util.*;

/** Records of crops taken from someone else's land, and the once-only reports against them. */
public final class FootprintLog {
    public record Footprint(String id, UUID actor, UUID owner, String world, int x, int y, int z, String crop, String cause,
                            long createdAt, long reportedAt, UUID reporter) {
        public boolean reported() { return reportedAt != 0; }
        Footprint reportedBy(UUID who, long at) { return new Footprint(id, actor, owner, world, x, y, z, crop, cause, createdAt, at, who); }
    }
    public enum Report { OK, MISSING, NOT_OWNER, OWN_ACT, ALREADY, EXPIRED }
    private final Map<String, Footprint> footprints = new LinkedHashMap<>();
    private final Set<String> thisTick = new HashSet<>();
    private final long reportWindow;
    private long tick = -1;
    public FootprintLog(long reportWindow) {
        if (reportWindow < 1) throw new IllegalArgumentException("Invalid report window");
        this.reportWindow = reportWindow;
    }
    /** Returns null when the same actor, block and cause were already recorded this tick. */
    public Footprint add(UUID actor, UUID owner, String world, int x, int y, int z, String crop, String cause, long now, long currentTick) {
        if (currentTick != tick) { tick = currentTick; thisTick.clear(); }
        if (!thisTick.add(actor + ":" + world + ":" + x + ":" + y + ":" + z + ":" + cause)) return null;
        Footprint footprint = new Footprint(UUID.randomUUID().toString(), actor, owner, world, x, y, z, crop, cause, now, 0, null);
        footprints.put(footprint.id(), footprint);
        return footprint;
    }
    public void restore(Footprint footprint) { footprints.put(footprint.id(), footprint); }
    public Collection<Footprint> all() { return Collections.unmodifiableCollection(footprints.values()); }
    public Footprint get(String id) { return footprints.get(id); }
    private boolean expired(Footprint footprint, long now) { return now - footprint.createdAt() > reportWindow; }
    /** Newest first. */
    public List<Footprint> reportable(UUID owner, long now) {
        List<Footprint> open = new ArrayList<>(footprints.values().stream()
            .filter(f -> f.owner().equals(owner) && !f.reported() && !expired(f, now)).toList());
        Collections.reverse(open);
        return open;
    }
    /** currentOwner is who owns the chunk now, since land can change hands after the footprint was left. */
    public Report report(String id, UUID reporter, UUID currentOwner, long now) {
        Footprint footprint = footprints.get(id);
        if (footprint == null) return Report.MISSING;
        if (!reporter.equals(footprint.owner()) || !reporter.equals(currentOwner)) return Report.NOT_OWNER;
        if (footprint.actor().equals(reporter)) return Report.OWN_ACT;
        if (footprint.reported()) return Report.ALREADY;
        if (expired(footprint, now)) return Report.EXPIRED;
        footprints.put(id, footprint.reportedBy(reporter, now));
        return Report.OK;
    }
}
