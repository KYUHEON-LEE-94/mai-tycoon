package kr.maitycoon;

import java.io.IOException;
import java.util.*;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;

/** NPC shop GUIs. Main-thread only, and player data is saved before the inventory changes. */
final class Shop implements Listener {
    enum Kind {
        SELL("농산물 상점"), BUY("씨앗 상점");
        final String title;
        Kind(String title) { this.title = title; }
    }
    private static final class Holder implements InventoryHolder {
        private final Kind kind;
        private final Map<Integer, Material> items = new HashMap<>();
        private Inventory inventory;
        private Holder(Kind kind) { this.kind = kind; }
        @Override public Inventory getInventory() { return inventory; }
    }
    private static final int SIZE = 54, INFO_SLOT = 4, SELL_ALL_SLOT = 49, BULK = 16;
    private static final int[] SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43};
    private final MaiTycoonPlugin plugin;
    private final Market market;
    private final ProfileStore store;
    private final Map<Material, Long> seeds;
    Shop(MaiTycoonPlugin plugin, Market market, ProfileStore store, Map<Material, Long> seeds) {
        this.plugin = plugin;
        this.market = market;
        this.store = store;
        this.seeds = seeds;
    }
    void open(Player player, Kind kind) {
        Holder holder = new Holder(kind);
        holder.inventory = plugin.getServer().createInventory(holder, SIZE, Component.text(kind.title + " · YD"));
        guarded(player, () -> render(holder, player));
        player.openInventory(holder.inventory);
    }
    void refreshOpen() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof Holder holder) guarded(player, () -> render(holder, player));
        }
    }
    private void render(Holder holder, Player player) throws IOException {
        Inventory inventory = holder.inventory;
        holder.items.clear();
        ItemStack filler = icon(Material.GRAY_STAINED_GLASS_PANE, Component.empty(), List.of());
        for (int slot = 0; slot < SIZE; slot++) inventory.setItem(slot, filler);
        List<Material> listed = holder.kind == Kind.SELL ? market.ids().stream().map(Material::valueOf).toList() : List.copyOf(seeds.keySet());
        for (int i = 0; i < listed.size() && i < SLOTS.length; i++) {
            Material item = listed.get(i);
            holder.items.put(SLOTS[i], item);
            inventory.setItem(SLOTS[i], holder.kind == Kind.SELL ? sellIcon(item, player) : buyIcon(item));
        }
        long coins = store.load(player.getUniqueId()).coins();
        inventory.setItem(INFO_SLOT, icon(Material.GOLD_INGOT, line("보유 " + coins + " YD", NamedTextColor.GOLD),
            holder.kind == Kind.SELL ? List.of(line("다음 시세까지 약 " + plugin.secondsToNextMarket() + "초", NamedTextColor.GRAY)) : List.of()));
        if (holder.kind == Kind.SELL) inventory.setItem(SELL_ALL_SLOT, icon(Material.EMERALD, line("작물 모두 판매", NamedTextColor.GREEN),
            List.of(line("인벤토리 보관 칸의 일반 작물을 현재 시세로 판매합니다.", NamedTextColor.GRAY))));
    }
    private ItemStack sellIcon(Material item, Player player) {
        String id = item.name();
        long change = market.change(id);
        Component trend = change > 0 ? line("▲ " + change, NamedTextColor.RED) : change < 0 ? line("▼ " + -change, NamedTextColor.BLUE) : line("- 변동 없음", NamedTextColor.GRAY);
        return icon(item, null, List.of(
            line("현재 " + market.price(id) + " YD ", NamedTextColor.YELLOW).append(trend),
            line("기준 " + market.base(id) + " YD (" + market.floor(id) + "~" + market.ceiling(id) + ")", NamedTextColor.GRAY),
            line("보유 " + sellable(player, item) + "개", NamedTextColor.GRAY),
            line("좌클릭 1개 판매 / 우클릭 전부 판매", NamedTextColor.GREEN)));
    }
    private ItemStack buyIcon(Material item) {
        return icon(item, null, List.of(
            line("개당 " + seeds.get(item) + " YD", NamedTextColor.YELLOW),
            line("좌클릭 1개 구매 / 우클릭 " + Math.min(BULK, item.getMaxStackSize()) + "개 구매", NamedTextColor.GREEN)));
    }
    private static ItemStack icon(Material type, Component name, List<Component> lore) {
        ItemStack icon = new ItemStack(type);
        var meta = icon.getItemMeta();
        if (name != null) meta.displayName(name);
        meta.lore(lore);
        icon.setItemMeta(meta);
        return icon;
    }
    private static Component line(String text, NamedTextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }
    private static boolean plainCrop(ItemStack item) { return item != null && !item.getType().isAir() && !item.hasItemMeta(); }
    private long sellable(Player player, Material only) {
        long count = 0;
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (plainCrop(item) && item.getType() == only) count += item.getAmount();
        }
        return count;
    }
    /** only == null sells every listed crop. Off-hand and named items stay untouched. */
    void sell(Player player, Material only, long limit) throws IOException {
        if (!plugin.survival(player)) return;
        ItemStack[] contents = player.getInventory().getStorageContents();
        long income = 0, count = 0;
        Map<Integer, Integer> taken = new LinkedHashMap<>();
        for (int slot = 0; slot < contents.length && count < limit; slot++) {
            ItemStack item = contents[slot];
            if (!plainCrop(item) || !market.has(item.getType().name()) || (only != null && item.getType() != only)) continue;
            int amount = (int) Math.min(item.getAmount(), limit - count);
            income = Math.addExact(income, Math.multiplyExact(market.price(item.getType().name()), amount));
            count += amount;
            taken.put(slot, amount);
        }
        if (count == 0) { plugin.tell(player, "판매할 일반 작물이 없습니다."); return; }
        var id = player.getUniqueId();
        store.save(id, store.load(id).sell(income));
        taken.forEach((slot, amount) -> {
            ItemStack item = player.getInventory().getItem(slot);
            if (item.getAmount() == amount) player.getInventory().setItem(slot, null);
            else item.setAmount(item.getAmount() - amount);
        });
        plugin.tell(player, "작물 " + count + "개 판매! +" + income + " YD");
    }
    private void buy(Player player, Material item, int amount) throws IOException {
        if (!plugin.survival(player)) return;
        long cost = Math.multiplyExact(seeds.get(item), amount);
        var id = player.getUniqueId();
        Profile profile = store.load(id);
        if (profile.coins() < cost) { plugin.tell(player, "YD가 부족합니다. 필요 " + cost + " / 보유 " + profile.coins()); return; }
        if (freeSpace(player, item) < amount) { plugin.tell(player, "인벤토리 공간이 부족합니다."); return; }
        store.save(id, profile.spend(cost));
        player.getInventory().addItem(new ItemStack(item, amount));
        plugin.tell(player, Component.translatable(item).append(Component.text(" " + amount + "개 구매! -" + cost + " YD")));
    }
    private static int freeSpace(Player player, Material item) {
        int free = 0, stack = item.getMaxStackSize();
        ItemStack sample = new ItemStack(item);
        for (ItemStack slot : player.getInventory().getStorageContents()) {
            if (slot == null || slot.getType().isAir()) free += stack;
            else if (slot.isSimilar(sample)) free += Math.max(0, stack - slot.getAmount());
        }
        return free;
    }
    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || !player.hasPermission("maitycoon.play")) return;
        ClickType click = event.getClick();
        if (click != ClickType.LEFT && click != ClickType.RIGHT) return;
        int slot = event.getRawSlot();
        Material item = holder.items.get(slot);
        guarded(player, () -> {
            if (holder.kind == Kind.SELL && slot == SELL_ALL_SLOT) sell(player, null, Long.MAX_VALUE);
            else if (item == null) return;
            else if (holder.kind == Kind.SELL) sell(player, item, click == ClickType.LEFT ? 1 : Long.MAX_VALUE);
            else buy(player, item, click == ClickType.LEFT ? 1 : Math.min(BULK, item.getMaxStackSize()));
            render(holder, player);
        });
    }
    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Holder) event.setCancelled(true);
    }
    private interface Action { void run() throws IOException; }
    private void guarded(Player player, Action action) {
        try { action.run(); }
        catch (IOException | ArithmeticException | IllegalArgumentException e) {
            plugin.getLogger().log(Level.SEVERE, "Shop transaction failed: " + player.getUniqueId(), e);
            plugin.tell(player, "데이터를 처리하지 못했습니다. 관리자에게 문의하세요.");
        }
    }
}
