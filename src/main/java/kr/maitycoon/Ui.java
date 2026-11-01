package kr.maitycoon;

import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/** Item-icon helpers shared by the menus. */
final class Ui {
    static final int SIZE = 54, ROW = 9;
    private Ui() {}
    static ItemStack icon(Material type, Component name, List<Component> lore) {
        ItemStack icon = new ItemStack(type);
        var meta = icon.getItemMeta();
        if (name != null) meta.displayName(name);
        meta.lore(lore);
        icon.setItemMeta(meta);
        return icon;
    }
    static ItemStack decorate(ItemStack icon, Component name, List<Component> lore) {
        var meta = icon.getItemMeta();
        if (name != null) meta.displayName(name);
        meta.lore(lore);
        icon.setItemMeta(meta);
        return icon;
    }
    static ItemStack glow(ItemStack icon) {
        var meta = icon.getItemMeta();
        meta.setEnchantmentGlintOverride(true);
        icon.setItemMeta(meta);
        return icon;
    }
    /** Keeps the tooltip and clicks but draws nothing, so a button painted on the background shows through. */
    static ItemStack blank(ItemStack icon) {
        var meta = icon.getItemMeta();
        meta.setItemModel(new org.bukkit.NamespacedKey("maitycoon", "blank"));
        icon.setItemMeta(meta);
        return icon;
    }
    /** Every slot of a wide button: painted with the pack, filled with the icon without it. */
    static void button(Inventory inventory, List<Integer> slots, boolean drawn, ItemStack icon) {
        ItemStack face = drawn ? blank(icon.clone()) : icon;
        for (int slot : slots) inventory.setItem(slot, face);
    }
    static Component line(String text, NamedTextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }
    static Component name(Material type, NamedTextColor color) {
        return Component.translatable(type).color(color).decoration(TextDecoration.ITALIC, false);
    }
    /** Dark header and footer rows around a lighter body. */
    static void frame(Inventory inventory) {
        ItemStack bar = icon(Material.BLACK_STAINED_GLASS_PANE, Component.empty(), List.of());
        ItemStack body = icon(Material.LIGHT_GRAY_STAINED_GLASS_PANE, Component.empty(), List.of());
        for (int slot = 0; slot < SIZE; slot++) inventory.setItem(slot, slot < ROW || slot >= SIZE - ROW ? bar : body);
    }
    static boolean plain(ItemStack item) { return item != null && !item.getType().isAir() && !item.hasItemMeta(); }
    static long count(Player player, Material type) {
        long count = 0;
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (plain(item) && item.getType() == type) count += item.getAmount();
        }
        return count;
    }
    /** First plain stack of the type in the storage slots, or -1. */
    static int firstPlain(Player player, Material type) {
        ItemStack[] contents = player.getInventory().getStorageContents();
        for (int slot = 0; slot < contents.length; slot++) {
            if (plain(contents[slot]) && contents[slot].getType() == type) return slot;
        }
        return -1;
    }
    static void take(Player player, int slot, int amount) {
        ItemStack item = player.getInventory().getItem(slot);
        if (item.getAmount() == amount) player.getInventory().setItem(slot, null);
        else item.setAmount(item.getAmount() - amount);
    }
}
