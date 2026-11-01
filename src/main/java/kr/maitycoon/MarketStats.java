package kr.maitycoon;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Per-round economy figures. The open round is saved inside market.yml so it can never disagree with the supply count
 * saved beside it. A closed round gets a file named by its id, which makes closing it again after a crash harmless.
 * Sales by admins are kept apart from player figures, because admins can trade outside survival.
 */
public final class MarketStats {
    /** price held during the round; nextPrice and pressure are what its close produced. */
    public record Line(long price, long nextPrice, int pressure, boolean floor, long sold, long paid, int sellers, long adminSold, long adminPaid) {}
    public record Round(long id, long startedAt, long endedAt, Map<String, Line> lines) {}
    public record Total(String id, long sold, long paid, long adminSold, long adminPaid, int floorRounds) {}
    public record Summary(int rounds, long sold, long paid, long adminSold, long adminPaid, int topThreePercent, List<Total> items) {}
    private static final class Tally {
        private long sold, paid, adminSold, adminPaid;
        private final Set<UUID> sellers = new TreeSet<>();
    }
    private static final Pattern FILE = Pattern.compile("round-(\\d+)\\.yml");
    private final Path directory;
    private final int keepRounds;
    private final Map<String, Tally> tallies = new TreeMap<>();
    private final Set<String> trades = new LinkedHashSet<>();
    private long round = 1, startedAt;
    public MarketStats(Path directory, int keepRounds, long now) throws IOException {
        if (keepRounds < 1) throw new IllegalArgumentException("Invalid stats retention");
        this.directory = directory;
        this.keepRounds = keepRounds;
        this.startedAt = now;
        Files.createDirectories(directory);
    }
    public long round() { return round; }
    /** True if this trade is part of the open round, which tells an interrupted trade that was counted from one that was not. */
    public boolean counted(String trade) { return trades.contains(trade); }
    public long sold(String id) { return tally(id).sold; }
    public long paid(String id) { return tally(id).paid; }
    public long adminSold(String id) { return tally(id).adminSold; }
    public long adminPaid(String id) { return tally(id).adminPaid; }
    public int sellers(String id) { return tally(id).sellers.size(); }
    private Tally tally(String id) { return tallies.getOrDefault(id, new Tally()); }
    /** False if this trade was already recorded, and nothing changes. */
    public boolean record(String trade, UUID player, boolean admin, Map<String, Long> counts, Map<String, Long> prices) {
        if (!trades.add(trade)) return false;
        counts.forEach((id, count) -> {
            Tally tally = tallies.computeIfAbsent(id, key -> new Tally());
            long paid = Math.multiplyExact(count, prices.get(id));
            if (admin) {
                tally.adminSold = Math.addExact(tally.adminSold, count);
                tally.adminPaid = Math.addExact(tally.adminPaid, paid);
            } else {
                tally.sold = Math.addExact(tally.sold, count);
                tally.paid = Math.addExact(tally.paid, paid);
                tally.sellers.add(player);
            }
        });
        return true;
    }
    public void save(ConfigurationSection section) {
        section.set("id", round);
        section.set("started-at", startedAt);
        section.set("trades", List.copyOf(trades));
        tallies.forEach((id, tally) -> {
            ConfigurationSection item = section.createSection("items." + id);
            item.set("sold", tally.sold);
            item.set("paid", tally.paid);
            item.set("admin-sold", tally.adminSold);
            item.set("admin-paid", tally.adminPaid);
            item.set("sellers", tally.sellers.stream().map(UUID::toString).toList());
        });
    }
    public void restore(ConfigurationSection section) {
        round = section.getLong("id", 1);
        startedAt = section.getLong("started-at", startedAt);
        trades.clear();
        trades.addAll(section.getStringList("trades"));
        tallies.clear();
        ConfigurationSection items = section.getConfigurationSection("items");
        if (items == null) return;
        for (String id : items.getKeys(false)) {
            Tally tally = new Tally();
            tally.sold = items.getLong(id + ".sold");
            tally.paid = items.getLong(id + ".paid");
            tally.adminSold = items.getLong(id + ".admin-sold");
            tally.adminPaid = items.getLong(id + ".admin-paid");
            for (String seller : items.getStringList(id + ".sellers")) tally.sellers.add(UUID.fromString(seller));
            tallies.put(id, tally);
        }
    }
    /** Builds the closed round from prices either side of the step. The open round restarts even if writing the file fails. */
    public Round close(Map<String, Long> prices, Map<String, Long> nextPrices, Map<String, Integer> pressures, Map<String, Long> floors, long now) {
        Map<String, Line> lines = new TreeMap<>();
        prices.forEach((id, price) -> {
            Tally tally = tally(id);
            lines.put(id, new Line(price, nextPrices.get(id), pressures.get(id), price <= floors.get(id), tally.sold, tally.paid, tally.sellers.size(), tally.adminSold, tally.adminPaid));
        });
        Round closed = new Round(round, startedAt, now, lines);
        round++;
        startedAt = now;
        tallies.clear();
        trades.clear();
        return closed;
    }
    /** Writes the round, then removes those past the retention window. */
    public void write(Round closed) throws IOException {
        var yaml = new YamlConfiguration();
        yaml.set("round", closed.id());
        yaml.set("started-at", closed.startedAt());
        yaml.set("ended-at", closed.endedAt());
        closed.lines().forEach((id, line) -> {
            ConfigurationSection item = yaml.createSection("items." + id);
            item.set("price", line.price());
            item.set("next-price", line.nextPrice());
            item.set("pressure", line.pressure());
            item.set("floor", line.floor());
            item.set("sold", line.sold());
            item.set("paid", line.paid());
            item.set("sellers", line.sellers());
            item.set("admin-sold", line.adminSold());
            item.set("admin-paid", line.adminPaid());
        });
        Path temp = Files.createTempFile(directory, "round-", ".tmp");
        try {
            Files.writeString(temp, yaml.saveToString());
            Files.move(temp, directory.resolve("round-" + closed.id() + ".yml"), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temp); }
        for (long id : ids()) if (id <= closed.id() - keepRounds) Files.deleteIfExists(directory.resolve("round-" + id + ".yml"));
    }
    private List<Long> ids() throws IOException {
        try (Stream<Path> files = Files.list(directory)) {
            return files.map(path -> FILE.matcher(path.getFileName().toString())).filter(Matcher::matches).map(match -> Long.valueOf(match.group(1))).sorted().toList();
        }
    }
    /** Newest rounds up to the limit, oldest first. A damaged file fails the read so a report never silently leaves a round out. */
    public List<Round> recent(int limit) throws IOException {
        List<Long> ids = ids();
        List<Round> rounds = new ArrayList<>();
        for (long id : ids.subList(Math.max(0, ids.size() - limit), ids.size())) {
            var yaml = new YamlConfiguration();
            try { yaml.load(directory.resolve("round-" + id + ".yml").toFile()); }
            catch (InvalidConfigurationException e) { throw new IOException("Invalid stats round " + id, e); }
            Map<String, Line> lines = new TreeMap<>();
            ConfigurationSection items = yaml.getConfigurationSection("items");
            if (items != null) for (String item : items.getKeys(false)) {
                ConfigurationSection line = Objects.requireNonNull(items.getConfigurationSection(item));
                lines.put(item, new Line(line.getLong("price"), line.getLong("next-price"), line.getInt("pressure"), line.getBoolean("floor"),
                    line.getLong("sold"), line.getLong("paid"), line.getInt("sellers"), line.getLong("admin-sold"), line.getLong("admin-paid")));
            }
            rounds.add(new Round(yaml.getLong("round"), yaml.getLong("started-at"), yaml.getLong("ended-at"), lines));
        }
        return rounds;
    }
    /** Items are ordered by player sales, then admin sales. topThreePercent covers player sales only. */
    public static Summary summarize(List<Round> rounds) {
        Map<String, long[]> totals = new TreeMap<>();
        for (Round round : rounds) round.lines().forEach((id, line) -> {
            long[] total = totals.computeIfAbsent(id, key -> new long[5]);
            total[0] += line.sold();
            total[1] += line.paid();
            total[2] += line.adminSold();
            total[3] += line.adminPaid();
            if (line.floor()) total[4]++;
        });
        List<Total> items = new ArrayList<>();
        totals.forEach((id, total) -> items.add(new Total(id, total[0], total[1], total[2], total[3], (int) total[4])));
        items.sort(Comparator.comparingLong(Total::sold).thenComparingLong(Total::adminSold).reversed());
        long sold = items.stream().mapToLong(Total::sold).sum();
        long top = items.stream().limit(3).mapToLong(Total::sold).sum();
        return new Summary(rounds.size(), sold, items.stream().mapToLong(Total::paid).sum(), items.stream().mapToLong(Total::adminSold).sum(),
            items.stream().mapToLong(Total::adminPaid).sum(), sold == 0 ? 0 : (int) Math.round(top * 100.0 / sold), List.copyOf(items));
    }
}
