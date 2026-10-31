package kr.maitycoon;
import kr.maitycoon.CropField.Plot;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class CropFieldTest {
    private static final long GROW = 900_000;
    @Test void growsThroughStagesAndStopsWhenReady() {
        var plot = new Plot(1, 64, 2, "CABBAGE", 1000);
        assertEquals(0, plot.stage(1000, GROW));
        assertEquals(1, plot.stage(1000 + GROW / 2, GROW));
        assertFalse(plot.ready(1000 + GROW - 1, GROW));
        assertTrue(plot.ready(1000 + GROW, GROW));
        assertEquals(2, plot.stage(1000 + GROW * 5, GROW));
    }
    @Test void boneMealAdvancesOneStage() {
        var plot = new Plot(1, 64, 2, "CABBAGE", 1000);
        assertEquals(1, plot.advanced(GROW).stage(1000, GROW));
        assertEquals(2, plot.advanced(GROW).advanced(GROW).stage(1000, GROW));
    }
    @Test void encodingRoundTripsNegativeCoordinates() {
        var field = new CropField();
        field.put(new Plot(-16, -3, 31, "CABBAGE", 5));
        field.put(new Plot(0, 64, 0, "GRAPE", 6));
        var back = CropField.decode(field.encode());
        assertEquals(2, back.size());
        assertEquals("CABBAGE", back.get(-16, -3, 31).id());
        assertEquals(6, back.get(0, 64, 0).plantedAt());
    }
    @Test void replacesAndRemovesByPosition() {
        var field = new CropField();
        field.put(new Plot(1, 2, 3, "A", 1));
        field.put(new Plot(1, 2, 3, "B", 2));
        assertEquals(1, field.size());
        field.remove(field.get(1, 2, 3));
        assertEquals(0, field.size());
        assertEquals(0, CropField.decode("").size());
        assertThrows(IllegalArgumentException.class, () -> CropField.decode("broken"));
    }
}
