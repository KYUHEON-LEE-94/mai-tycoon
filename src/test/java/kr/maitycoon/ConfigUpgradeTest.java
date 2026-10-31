package kr.maitycoon;

import java.io.*;
import java.nio.charset.StandardCharsets;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ConfigUpgradeTest {
    @Test void oldInstallGetsNewSectionsAndCropsWithoutOverwritingCustomPrices() throws Exception {
        try (var reader = new InputStreamReader(getClass().getResourceAsStream("/config.yml"), StandardCharsets.UTF_8)) {
            var defaults = YamlConfiguration.loadConfiguration(reader);
            var old = new YamlConfiguration();
            old.loadFromString("market:\n  min-percent: 70\ncustom-crops:\n  crops:\n    CABBAGE:\n      seed-price: 123\n");
            old.setDefaults(defaults);
            old.options().copyDefaults(true);
            assertEquals(12, old.getConfigurationSection("custom-crops.crops").getKeys(false).size());
            assertEquals(24, old.getConfigurationSection("building-shop").getKeys(false).size());
            assertFalse(old.getConfigurationSection("lamp").getKeys(false).isEmpty());
            assertEquals(123, old.getInt("custom-crops.crops.CABBAGE.seed-price"));
            assertEquals(70, old.getInt("market.min-percent"));
            var saved = new YamlConfiguration();
            saved.loadFromString(old.saveToString());
            assertEquals(12, saved.getConfigurationSection("custom-crops.crops").getKeys(false).size());
            assertEquals(256, saved.getInt("market.volume-threshold"));
            assertEquals(12, saved.getConfigurationSection("custom-crops.crops.ONION").getLong("grow-minutes", -1));
        }
    }
}
