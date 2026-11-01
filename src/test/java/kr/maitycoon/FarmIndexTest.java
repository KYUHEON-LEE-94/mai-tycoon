package kr.maitycoon;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import kr.maitycoon.CropField.Plot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class FarmIndexTest {
    private static final long GROW = 1000;
    @TempDir Path directory;
    private FarmIndex index() { return new FarmIndex(directory.resolve("farm-index.yml")); }
    private static long grow(String id) { return id.equals("GONE") ? 0 : GROW; }
    private static Plot plot(int x, String id, long plantedAt) { return new Plot(x, 64, 0, id, plantedAt); }
    @Test void countsPlantedAndReadyAcrossChunksAtTheGrowthBoundary() {
        var index = index();
        index.update("a", List.of(plot(0, "CABBAGE", 0), plot(1, "CABBAGE", 500)), 1);
        index.update("b", List.of(plot(0, "CABBAGE", 400), plot(1, "ONION", 999)), 1);
        var rows = FarmIndex.summarize(List.of(index.entry("a"), index.entry("b")), 1000, FarmIndexTest::grow);
        assertEquals(new FarmIndex.Row(3, 1, 1400), rows.get("CABBAGE"));
        assertEquals(new FarmIndex.Row(1, 0, 1999), rows.get("ONION"));
    }
    @Test void nothingGrowingHasNoNextHarvest() {
        var row = FarmIndex.summarize(List.of(new FarmIndex.Entry(1, List.of(plot(0, "CABBAGE", 0)))), 5000, FarmIndexTest::grow);
        assertEquals(new FarmIndex.Row(1, 1, 0), row.get("CABBAGE"));
    }
    @Test void aCropNoLongerConfiguredIsLeftOut() {
        assertTrue(FarmIndex.summarize(List.of(new FarmIndex.Entry(1, List.of(plot(0, "GONE", 0)))), 5000, FarmIndexTest::grow).isEmpty());
    }
    @Test void updatingAChunkReplacesItInsteadOfAdding() {
        var index = index();
        index.update("a", List.of(plot(0, "CABBAGE", 0), plot(1, "CABBAGE", 0)), 1);
        index.update("a", List.of(plot(0, "CABBAGE", 0)), 2);
        assertEquals(1, index.entry("a").plots().size());
        assertEquals(2, index.entry("a").seenAt());
        assertEquals(1, index.size());
    }
    @Test void survivesRestartIncludingEmptyChunks() throws IOException {
        var index = index();
        index.update("uuid:-1:2", List.of(plot(-16, "CABBAGE", 7)), 3);
        index.update("uuid:0:0", List.of(), 4);
        index.saveIfChanged();
        var back = index();
        back.load();
        assertEquals(index.entry("uuid:-1:2"), back.entry("uuid:-1:2"));
        assertEquals(new FarmIndex.Entry(4, List.of()), back.entry("uuid:0:0"));
    }
    @Test void aSaveWithoutChangesWritesNothing() throws IOException {
        var index = index();
        index.saveIfChanged();
        assertFalse(Files.exists(directory.resolve("farm-index.yml")));
    }
    @Test void forgetRemovesTheChunkFromTheFile() throws IOException {
        var index = index();
        index.update("a", List.of(), 1);
        index.saveIfChanged();
        index.forget("a");
        index.saveIfChanged();
        var back = index();
        back.load();
        assertNull(back.entry("a"));
    }
    @Test void aDamagedFileIsReportedAndLeavesTheIndexEmpty() throws IOException {
        Files.writeString(directory.resolve("farm-index.yml"), "chunks:\n- a|notanumber|\n");
        var index = index();
        assertThrows(IOException.class, index::load);
        assertEquals(0, index.size());
    }
}
