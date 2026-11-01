package kr.maitycoon;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Recent balance changes per account, for the wallet screen. Display only: the profile stays the one true balance. */
public final class WalletLog {
    public record Line(long at, String type, String currency, long before, long after) {
        long change() { return after - before; }
    }
    private static final int KEEP = 100, TRIM_AT = 200;
    private final Path directory;
    public WalletLog(Path directory) throws IOException {
        this.directory = directory;
        Files.createDirectories(directory);
    }
    private Path path(UUID id) { return directory.resolve(id + ".log"); }
    public void add(UUID id, Line line) throws IOException {
        String text = line.at() + "|" + line.type() + "|" + line.currency() + "|" + line.before() + "|" + line.after() + "\n";
        Files.writeString(path(id), text, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        List<String> lines = Files.readAllLines(path(id), StandardCharsets.UTF_8);
        if (lines.size() >= TRIM_AT) Files.write(path(id), lines.subList(lines.size() - KEEP, lines.size()), StandardCharsets.UTF_8);
    }
    /** Newest first. A damaged line is skipped, because this file decides nothing. */
    public List<Line> recent(UUID id, int limit) throws IOException {
        if (!Files.exists(path(id))) return List.of();
        List<Line> lines = new ArrayList<>();
        for (String text : Files.readAllLines(path(id), StandardCharsets.UTF_8)) {
            String[] parts = text.split("\\|");
            if (parts.length != 5) continue;
            try { lines.add(new Line(Long.parseLong(parts[0]), parts[1], parts[2], Long.parseLong(parts[3]), Long.parseLong(parts[4]))); }
            catch (NumberFormatException e) { continue; }
        }
        Collections.reverse(lines);
        return List.copyOf(lines.subList(0, Math.min(limit, lines.size())));
    }
}
