package kr.maitycoon;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class MarketStatsTest {
    @TempDir Path directory;
    private final UUID alice = UUID.randomUUID(), bob = UUID.randomUUID();
    private final Map<String, Long> price = Map.of("WHEAT", 40L, "CARROT", 25L);
    private MarketStats stats() throws IOException { return stats(100); }
    private MarketStats stats(int keep) throws IOException { return new MarketStats(directory, keep, 1000); }
    private MarketStats.Round close(MarketStats stats) {
        return stats.close(Map.of("WHEAT", 40L, "CARROT", 12L), Map.of("WHEAT", 50L, "CARROT", 20L), Map.of("WHEAT", 10, "CARROT", 0), Map.of("WHEAT", 20L, "CARROT", 12L), 2000);
    }
    @Test void addsAccountsTogetherAndCountsEachOnce() throws IOException {
        var stats = stats();
        stats.record("a", alice, false, Map.of("WHEAT", 10L), price);
        stats.record("b", bob, false, Map.of("WHEAT", 5L), price);
        stats.record("c", alice, false, Map.of("WHEAT", 1L), price);
        assertEquals(16, stats.sold("WHEAT"));
        assertEquals(640, stats.paid("WHEAT"));
        assertEquals(2, stats.sellers("WHEAT"));
    }
    @Test void aRepeatedTradeChangesNothing() throws IOException {
        var stats = stats();
        assertTrue(stats.record("a", alice, false, Map.of("WHEAT", 10L), price));
        assertFalse(stats.record("a", alice, false, Map.of("WHEAT", 10L), price));
        assertEquals(10, stats.sold("WHEAT"));
    }
    @Test void adminSalesStayApartFromPlayers() throws IOException {
        var stats = stats();
        stats.record("a", alice, true, Map.of("WHEAT", 10L), price);
        assertEquals(0, stats.sold("WHEAT"));
        assertEquals(0, stats.sellers("WHEAT"));
        assertEquals(10, stats.adminSold("WHEAT"));
        assertEquals(400, stats.adminPaid("WHEAT"));
    }
    @Test void theOpenRoundSurvivesRestartIncludingTradeIds() throws Exception {
        var stats = stats();
        close(stats);
        stats.record("a", alice, false, Map.of("WHEAT", 10L), price);
        stats.record("b", bob, true, Map.of("CARROT", 4L), price);
        var yaml = new YamlConfiguration();
        stats.save(yaml.createSection("round"));
        var text = yaml.saveToString();
        var loaded = new YamlConfiguration();
        loaded.loadFromString(text);
        var restarted = stats();
        restarted.restore(loaded.getConfigurationSection("round"));
        assertEquals(2, restarted.round());
        assertEquals(10, restarted.sold("WHEAT"));
        assertEquals(1, restarted.sellers("WHEAT"));
        assertEquals(4, restarted.adminSold("CARROT"));
        assertTrue(restarted.counted("a"));
        assertFalse(restarted.record("a", alice, false, Map.of("WHEAT", 10L), price));
    }
    @Test void savingAfterARoundLeavesNoOldItems() throws Exception {
        var stats = stats();
        stats.record("a", alice, false, Map.of("WHEAT", 10L), price);
        var yaml = new YamlConfiguration();
        stats.save(yaml.createSection("round"));
        close(stats);
        yaml.set("round", null);
        stats.save(yaml.createSection("round"));
        assertFalse(yaml.isSet("round.items.WHEAT"));
        assertEquals(List.of(), yaml.getStringList("round.trades"));
    }
    @Test void closingStartsAFreshRoundAndKeepsTheFigures() throws IOException {
        var stats = stats();
        stats.record("a", alice, false, Map.of("WHEAT", 10L), price);
        var closed = close(stats);
        assertEquals(1, closed.id());
        assertEquals(new MarketStats.Line(40, 50, 10, false, 10, 400, 1, 0, 0), closed.lines().get("WHEAT"));
        assertTrue(closed.lines().get("CARROT").floor());
        assertEquals(2, stats.round());
        assertEquals(0, stats.sold("WHEAT"));
        assertFalse(stats.counted("a"));
    }
    @Test void closedRoundsReadBackAndWritingTwiceKeepsOneFile() throws IOException {
        var stats = stats();
        stats.record("a", alice, false, Map.of("WHEAT", 10L), price);
        var closed = close(stats);
        stats.write(closed);
        stats.write(closed);
        assertEquals(List.of(closed), stats.recent(10));
        try (var files = Files.list(directory)) { assertEquals(1, files.count()); }
    }
    @Test void retentionRemovesOnlyOldRoundFiles() throws IOException {
        var stats = stats(2);
        Path other = Files.writeString(directory.resolve("notes.txt"), "keep");
        for (int i = 0; i < 5; i++) stats.write(close(stats));
        assertEquals(List.of(4L, 5L), stats.recent(10).stream().map(MarketStats.Round::id).toList());
        assertTrue(Files.exists(other));
    }
    @Test void aDamagedRoundFailsTheReportInsteadOfVanishing() throws IOException {
        var stats = stats();
        stats.write(close(stats));
        Files.writeString(directory.resolve("round-1.yml"), ": : [");
        assertThrows(IOException.class, () -> stats.recent(10));
    }
    @Test void summaryRanksItemsAndTotalsSides() {
        var line = new MarketStats.Line(40, 40, 0, false, 10, 400, 1, 2, 80);
        var cheap = new MarketStats.Line(12, 12, 0, true, 30, 360, 2, 0, 0);
        var summary = MarketStats.summarize(List.of(new MarketStats.Round(1, 0, 1, Map.of("WHEAT", line, "CARROT", cheap)), new MarketStats.Round(2, 1, 2, Map.of("WHEAT", line, "CARROT", cheap))));
        assertEquals(80, summary.sold());
        assertEquals(1520, summary.paid());
        assertEquals(4, summary.adminSold());
        assertEquals(100, summary.topThreePercent());
        assertEquals("CARROT", summary.items().get(0).id());
        assertEquals(2, summary.items().get(0).floorRounds());
    }
    @Test void anEmptySummaryIsSafe() { assertEquals(0, MarketStats.summarize(List.of()).topThreePercent()); }
}
