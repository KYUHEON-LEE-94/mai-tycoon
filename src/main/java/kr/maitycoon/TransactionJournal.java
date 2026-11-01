package kr.maitycoon;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.stream.Stream;

/**
 * Write-ahead record of every trade. An entry is durable before balances or items change and is removed afterwards,
 * so an entry found at startup means the server stopped mid-trade. Which side landed cannot be proven, so nothing is
 * replayed: that player's trading stops until an admin has checked the record and resolved it.
 */
public final class TransactionJournal {
    public record Entry(String id, UUID player, String type, String detail, String createdAt) {}
    private final Path directory;
    private final Map<String, Entry> interrupted = new LinkedHashMap<>();
    public TransactionJournal(Path directory) throws IOException {
        this.directory = directory;
        Files.createDirectories(directory);
        try (Stream<Path> files = Files.list(directory)) {
            for (Path path : files.sorted().toList()) {
                if (path.getFileName().toString().endsWith(".tx")) hold(path.getFileName().toString().replace(".tx", ""));
            }
        }
    }
    /** Treats a trade as interrupted right away, for a failure the server survives. */
    public void hold(String id) throws IOException {
        Path path = directory.resolve(id + ".tx");
        List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        if (lines.size() != 4) throw new IOException("Invalid journal entry " + path);
        interrupted.put(id, new Entry(id, UUID.fromString(lines.get(0)), lines.get(1), lines.get(2), lines.get(3)));
    }
    public Collection<Entry> interrupted() { return Collections.unmodifiableCollection(interrupted.values()); }
    public boolean held(UUID player) { return interrupted.values().stream().anyMatch(entry -> entry.player().equals(player)); }
    public String begin(UUID player, String type, String detail) throws IOException {
        String id = System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 8);
        String body = String.join("\n", player.toString(), type, detail.replace('\n', ' '), Instant.now().toString());
        Path temp = directory.resolve(id + ".tmp");
        Files.writeString(temp, body, StandardCharsets.UTF_8);
        try (FileChannel channel = FileChannel.open(temp, StandardOpenOption.WRITE)) { channel.force(true); }
        Files.move(temp, directory.resolve(id + ".tx"), StandardCopyOption.ATOMIC_MOVE);
        return id;
    }
    /** Also used for a trade that failed before changing anything. */
    public void finish(String id) throws IOException { Files.deleteIfExists(directory.resolve(id + ".tx")); }
    public boolean resolve(String id) throws IOException {
        if (interrupted.remove(id) == null) return false;
        finish(id);
        return true;
    }
}
