package kr.maitycoon;

import java.util.*;

/** Custom crops planted in one chunk. Encoded into the chunk's own data so they travel with the world. */
public final class CropField {
    public record Plot(int x, int y, int z, String id, long plantedAt) {
        /** 0 sprout, 1 growing, 2 ready; growth runs on real time and is split evenly across the stages. */
        public int stage(long now, long growMillis) {
            if (now - plantedAt >= growMillis) return STAGES - 1;
            return (int) Math.max(0, (now - plantedAt) * (STAGES - 1) / growMillis);
        }
        public boolean ready(long now, long growMillis) { return stage(now, growMillis) == STAGES - 1; }
        /** Moves growth forward by one stage's worth of time. */
        public Plot advanced(long growMillis) { return new Plot(x, y, z, id, plantedAt - growMillis / (STAGES - 1)); }
    }
    public static final int STAGES = 3;
    private final Map<String, Plot> plots = new LinkedHashMap<>();
    private static String at(int x, int y, int z) { return x + "," + y + "," + z; }
    public Collection<Plot> plots() { return List.copyOf(plots.values()); }
    public int size() { return plots.size(); }
    public Plot get(int x, int y, int z) { return plots.get(at(x, y, z)); }
    public void put(Plot plot) { plots.put(at(plot.x(), plot.y(), plot.z()), plot); }
    public void remove(Plot plot) { plots.remove(at(plot.x(), plot.y(), plot.z())); }
    public String encode() {
        StringJoiner out = new StringJoiner("|");
        for (Plot plot : plots.values()) out.add(plot.x() + "," + plot.y() + "," + plot.z() + "," + plot.id() + "," + plot.plantedAt());
        return out.toString();
    }
    public static CropField decode(String encoded) {
        CropField field = new CropField();
        if (encoded == null || encoded.isEmpty()) return field;
        for (String part : encoded.split("\\|")) {
            String[] values = part.split(",");
            if (values.length != 5) throw new IllegalArgumentException("Invalid crop entry " + part);
            field.put(new Plot(Integer.parseInt(values[0]), Integer.parseInt(values[1]), Integer.parseInt(values[2]), values[3], Long.parseLong(values[4])));
        }
        return field;
    }
}
