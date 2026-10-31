package kr.maitycoon;

import java.util.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/** Every tradeable item by id: vanilla ones by Material name, custom ones through a tag on a plain base item. */
final class Catalog {
    record Crop(String id, String name, String seedId, String seedName, long base, int swing, long seedPrice, long growMillis, int minYield, int maxYield) {}
    static final Material ITEM_BASE = Material.PAPER;
    private final NamespacedKey key;
    private final Map<String, Crop> crops = new LinkedHashMap<>();
    private final Map<String, String> names = new HashMap<>();
    private final Map<String, Crop> seeds = new HashMap<>();
    Catalog(Plugin plugin, ConfigurationSection section) {
        key = new NamespacedKey(plugin, "item");
        if (section == null) return;
        for (String id : section.getKeys(false)) {
            var entry = Objects.requireNonNull(section.getConfigurationSection(id));
            List<Integer> yield = entry.getIntegerList("yield");
            if (Material.matchMaterial(id) != null || yield.size() != 2 || yield.get(0) < 1 || yield.get(1) < yield.get(0)) throw new IllegalArgumentException("Invalid custom crop " + id);
            long grow = entry.getLong("grow-minutes", -1) * 60_000L;
            if (grow <= 0) throw new IllegalArgumentException("Invalid growth time for " + id);
            Crop crop = new Crop(id, entry.getString("name", id), id + "_SEEDS", entry.getString("seed-name", id), entry.getLong("base", -1), entry.getInt("swing", -1),
                entry.getLong("seed-price", -1), grow, yield.get(0), yield.get(1));
            crops.put(id, crop);
            seeds.put(crop.seedId(), crop);
            names.put(id, crop.name());
            names.put(crop.seedId(), crop.seedName());
        }
    }
    Collection<Crop> crops() { return crops.values(); }
    Crop crop(String id) { return crops.get(id); }
    Crop bySeed(String seedId) { return seeds.get(seedId); }
    boolean custom(String id) { return names.containsKey(id); }
    private static String modelOf(String id) { return id.toLowerCase(Locale.ROOT); }
    ItemStack item(String id, int amount) {
        if (!custom(id)) return new ItemStack(Material.valueOf(id), amount);
        ItemStack item = new ItemStack(ITEM_BASE, amount);
        var meta = item.getItemMeta();
        meta.displayName(Component.text(names.get(id), NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false));
        var model = meta.getCustomModelDataComponent();
        model.setStrings(List.of(modelOf(id)));
        meta.setCustomModelDataComponent(model);
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, id);
        item.setItemMeta(meta);
        return item;
    }
    /** The product id of a stack, or null for anything that cannot be traded (renamed or otherwise customised items). */
    String idOf(ItemStack item) {
        if (item == null || item.getType().isAir()) return null;
        if (!item.hasItemMeta()) return item.getType().name();
        String id = item.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
        return id != null && custom(id) ? id : null;
    }
    Component name(String id) { return custom(id) ? Component.text(names.get(id)) : Component.translatable(Material.valueOf(id)); }
    int maxStack(String id) { return custom(id) ? ITEM_BASE.getMaxStackSize() : Material.valueOf(id).getMaxStackSize(); }
    long count(Player player, String id) {
        long count = 0;
        for (ItemStack item : player.getInventory().getStorageContents()) if (id.equals(idOf(item))) count += item.getAmount();
        return count;
    }
}
