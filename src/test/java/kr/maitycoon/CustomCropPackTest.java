package kr.maitycoon;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;
import javax.imageio.ImageIO;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CustomCropPackTest {
    @Test void allConfiguredCropsHaveNamesSeedsAndThreeDistinctGrowthTextures() throws Exception {
        Map<String, byte[]> assets = new HashMap<>();
        try (var zip = new ZipInputStream(getClass().getResourceAsStream("/pack.zip"))) {
            for (ZipEntry entry; (entry = zip.getNextEntry()) != null;) assets.put(entry.getName(), zip.readAllBytes());
        }
        try (var reader = new InputStreamReader(getClass().getResourceAsStream("/config.yml"), StandardCharsets.UTF_8)) {
            var crops = YamlConfiguration.loadConfiguration(reader).getConfigurationSection("custom-crops.crops");
            assertNotNull(crops);
            assertEquals(12, crops.getKeys(false).size());
            String items = new String(assets.get("assets/minecraft/items/paper.json"), StandardCharsets.UTF_8);
            String stages = new String(assets.get("assets/minecraft/items/short_grass.json"), StandardCharsets.UTF_8);
            for (String id : crops.getKeys(false)) {
                assertTrue(ShopLayout.ITEM_IDS.contains(id));
                assertTrue(ShopLayout.ITEM_IDS.contains(id + "_SEEDS"));
                String key = id.toLowerCase(Locale.ROOT);
                for (String suffix : List.of("", "_seeds", "_0", "_1", "_2")) {
                    byte[] data = assets.get("assets/maitycoon/textures/item/" + key + suffix + ".png");
                    assertNotNull(data, key + suffix);
                    var image = ImageIO.read(new ByteArrayInputStream(data));
                    assertEquals(32, image.getWidth()); assertEquals(32, image.getHeight());
                    assertNotNull(assets.get("assets/maitycoon/models/item/" + key + suffix + ".json"));
                    String selector = suffix.matches("_[012]") ? stages : items;
                    assertTrue(selector.contains("\"when\":\"" + key + suffix + "\""), key + suffix);
                }
                assertFalse(Arrays.equals(assets.get("assets/maitycoon/textures/item/" + key + "_0.png"), assets.get("assets/maitycoon/textures/item/" + key + "_2.png")));
            }
        }
    }
}
