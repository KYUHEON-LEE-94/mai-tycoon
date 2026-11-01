package kr.maitycoon;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.function.ToLongFunction;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Last known custom crops per chunk, so a farm can be read without loading it. Growth runs on real time, so a stored
 * planting time stays correct while the chunk is unloaded. Chunk data remains the truth; this file can be rebuilt from it.
 */
public final class FarmIndex {
    public record Entry(long seenAt, List<CropField.Plot> plots) {}
    /** nextReadyAt is 0 when nothing is still growing. */
    public record Row(int planted, int ready, long nextReadyAt) {}
    private final Path file;
    private final Map<String, Entry> entries = new HashMap<>();
    private boolean dirty;
    public FarmIndex(Path file) { this.file = file; }
    /** A damaged file is reported to the caller and leaves the index empty, because every chunk rebuilds its entry on load. */
    public void load() throws IOException {
        if (!Files.exists(file)) return;
        var yaml = new YamlConfiguration();
        try {
            yaml.load(file.toFile());
            Map<String, Entry> read = new HashMap<>();
            for (String line : yaml.getStringList("chunks")) {
                String[] parts = line.split("\\|", 3);
                if (parts.length != 3) throw new IOException("Invalid farm index line " + line);
                read.put(parts[0], new Entry(Long.parseLong(parts[1]), List.copyOf(CropField.decode(parts[2]).plots())));
            }
            entries.putAll(read);
        } catch (InvalidConfigurationException | IllegalArgumentException e) { throw new IOException("Invalid farm index", e); }
    }
    public Entry entry(String key) { return entries.get(key); }
    public int size() { return entries.size(); }
    public void update(String key, Collection<CropField.Plot> plots, long now) {
        entries.put(key, new Entry(now, List.copyOf(plots)));
        dirty = true;
    }
    public void forget(String key) { if (entries.remove(key) != null) dirty = true; }
    public void saveIfChanged() throws IOException {
        if (!dirty) return;
        var yaml = new YamlConfiguration();
        List<String> lines = new ArrayList<>();
        entries.forEach((key, entry) -> {
            CropField field = new CropField();
            entry.plots().forEach(field::put);
            lines.add(key + "|" + entry.seenAt() + "|" + field.encode());
        });
        Collections.sort(lines);
        yaml.set("chunks", lines);
        Path temp = Files.createTempFile(file.getParent(), "farm-index-", ".tmp");
        try {
            Files.writeString(temp, yaml.saveToString());
            Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temp); }
        dirty = false;
    }
    /** Per crop id. growMillis answers below 1 for a crop no longer configured, which is left out. */
    public static Map<String, Row> summarize(Collection<Entry> entries, long now, ToLongFunction<String> growMillis) {
        Map<String, Row> rows = new TreeMap<>();
        for (Entry entry : entries) for (CropField.Plot plot : entry.plots()) {
            long grow = growMillis.applyAsLong(plot.id());
            if (grow < 1) continue;
            Row row = rows.getOrDefault(plot.id(), new Row(0, 0, 0));
            boolean ready = plot.ready(now, grow);
            long at = plot.plantedAt() + grow;
            long next = ready ? row.nextReadyAt() : row.nextReadyAt() == 0 ? at : Math.min(row.nextReadyAt(), at);
            rows.put(plot.id(), new Row(row.planted() + 1, row.ready() + (ready ? 1 : 0), next));
        }
        return rows;
    }
}
