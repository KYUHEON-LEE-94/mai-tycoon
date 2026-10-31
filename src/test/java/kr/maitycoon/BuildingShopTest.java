package kr.maitycoon;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BuildingShopTest {
    @Test void everyDefaultBuildingItemHasAValidPriceAndDrawnName() throws Exception {
        try (var stream = getClass().getResourceAsStream("/config.yml")) {
            assertNotNull(stream);
            var config = YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
            var shop = config.getConfigurationSection("building-shop");
            assertNotNull(shop);
            assertEquals(149, shop.getKeys(false).size());
            var categories = new HashSet<String>();
            for (String id : shop.getKeys(false)) {
                var material = Material.valueOf(id);
                assertNotNull(material); // Registry-backed block/item validation runs during plugin startup.
                assertTrue(shop.getLong(id + ".price") > 0, id);
                assertTrue(ShopLayout.ITEM_IDS.contains(id), "Missing title glyph: " + id);
                categories.add(shop.getString(id + ".category"));
            }
            assertTrue(categories.size() + 1 <= ShopLayout.MAX_TABS);
            assertEquals(ShopLayout.ITEM_IDS.size(), ShopLayout.ITEM_NAMES.size());
            assertEquals(ShopLayout.ITEM_IDS.size(), new HashSet<>(ShopLayout.ITEM_IDS).size());
        }
    }
}
