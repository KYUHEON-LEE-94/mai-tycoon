package kr.maitycoon;

import java.io.*;
import java.util.zip.*;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ResourcePackGeometryTest {
    @Test void backgroundGlyphsFitClientAtlasAndCoverBothScreens() throws Exception {
        int tiles = 0;
        try (var stream = getClass().getResourceAsStream("/pack.zip")) {
            assertNotNull(stream);
            try (var zip = new ZipInputStream(stream)) {
                for (ZipEntry entry; (entry = zip.getNextEntry()) != null;) {
                    if (!entry.getName().endsWith(".png")) continue;
                    var image = ImageIO.read(new ByteArrayInputStream(zip.readAllBytes()));
                    assertNotNull(image, entry.getName());
                    // All current PNGs contain cells no larger than the entire texture.
                    assertTrue(image.getWidth() <= 256 && image.getHeight() <= 256,
                        "Oversized bitmap glyph: " + entry.getName());
                    if (entry.getName().matches(".*/(sell|buy|build|panel|furniture|lamp)_\\d_\\d.png")) {
                        tiles++;
                        assertEquals(ShopLayout.TILE_WIDTH * 4, image.getWidth());
                        assertNotEquals(0, image.getRGB(image.getWidth() - 1, image.getHeight() - 1) >>> 24,
                            "Right edge must preserve font advance");
                    }
                }
            }
        }
        assertEquals(6 * ShopLayout.TILE_COLUMNS * ShopLayout.TILE_ROWS, tiles);
    }
}
