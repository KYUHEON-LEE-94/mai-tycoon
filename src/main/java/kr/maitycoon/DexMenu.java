package kr.maitycoon;

import java.io.IOException;
import java.util.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;

/** Collection book. Clicking an item submits one of it; the item is consumed. */
final class DexMenu implements Listener {
    private static final class Holder implements InventoryHolder {
        private final Map<Integer, String> tabs = new HashMap<>();
        private final Map<Integer, String> entries = new HashMap<>();
        private String page;
        private int subpage;
        private Inventory inventory;
        @Override public Inventory getInventory() { return inventory; }
    }
    private static final int INFO = 0, BODY_START = 9, BODY_SIZE = 36, PREV = 45, PROGRESS = 49, NEXT = 53;
    private static final int[] TAB_SLOTS = {1, 2, 3, 4, 5, 6, 7, 8, 46, 47, 48, 50, 51, 52};
    private final MaiTycoonPlugin plugin;
    private final Dex dex;
    private final ProfileStore store;
    private final Catalog catalog;
    DexMenu(MaiTycoonPlugin plugin, Dex dex, ProfileStore store, Catalog catalog) {
        this.plugin = plugin;
        this.dex = dex;
        this.store = store;
        this.catalog = catalog;
    }
    void open(Player player) {
        Holder holder = new Holder();
        holder.page = dex.pages().keySet().iterator().next();
        holder.inventory = plugin.getServer().createInventory(holder, Ui.SIZE, plugin.menuTitle(player, "수집 도감"));
        plugin.guarded(player, () -> render(holder, player));
        player.openInventory(holder.inventory);
    }
    private void render(Holder holder, Player player) throws IOException {
        Profile profile = store.load(player.getUniqueId());
        Inventory inventory = holder.inventory;
        plugin.frame(inventory, player);
        holder.tabs.clear();
        holder.entries.clear();
        int tabIndex = 0;
        for (var page : dex.pages().entrySet()) {
            if (tabIndex == TAB_SLOTS.length) throw new IllegalStateException("Too many dex pages");
            if (page.getValue().isEmpty()) continue;
            int slot = TAB_SLOTS[tabIndex++];
            long done = page.getValue().stream().filter(profile.dex()::contains).count();
            ItemStack tab = Ui.decorate(base(page.getValue().get(0)), Ui.line(page.getKey(), NamedTextColor.WHITE),
                List.of(Ui.line(done + " / " + page.getValue().size(), NamedTextColor.GRAY)));
            holder.tabs.put(slot, page.getKey());
            inventory.setItem(slot, page.getKey().equals(holder.page) ? Ui.glow(tab) : tab);
        }
        List<String> entries = dex.pages().get(holder.page);
        int pages = Math.max(1, (entries.size() + BODY_SIZE - 1) / BODY_SIZE);
        holder.subpage = Math.min(holder.subpage, pages - 1);
        int skip = holder.subpage * BODY_SIZE;
        for (int i = skip; i < entries.size() && i < skip + BODY_SIZE; i++) {
            String id = entries.get(i);
            holder.entries.put(BODY_START + i - skip, id);
            inventory.setItem(BODY_START + i - skip, entry(id, profile, player));
        }
        inventory.setItem(INFO, Ui.icon(Material.GOLD_INGOT, Ui.line(profile.coins() + " 골드", NamedTextColor.GOLD), List.of(Ui.line("즈코인 " + profile.zcoin() + "개", NamedTextColor.AQUA))));
        inventory.setItem(PROGRESS, progress(profile));
        if (holder.subpage > 0) inventory.setItem(PREV, Ui.icon(Material.ARROW, Ui.line("이전 페이지", NamedTextColor.WHITE), List.of()));
        if (holder.subpage < pages - 1) inventory.setItem(NEXT, Ui.icon(Material.ARROW, Ui.line("다음 페이지 " + (holder.subpage + 2) + "/" + pages, NamedTextColor.WHITE), List.of()));
    }
    private static boolean found(String id) { return id.startsWith(DexItems.MOB) || id.startsWith(DexItems.BIOME); }
    private ItemStack base(String id) { return found(id) ? new ItemStack(DexItems.icon(id)) : catalog.item(id, 1); }
    private Component title(String id, NamedTextColor color) { return (found(id) ? DexItems.name(id) : catalog.name(id)).color(color).decoration(TextDecoration.ITALIC, false); }
    private ItemStack entry(String id, Profile profile, Player player) {
        if (profile.dex().contains(id)) return Ui.glow(Ui.decorate(base(id), title(id, NamedTextColor.GREEN), List.of(Ui.line("등록 완료", NamedTextColor.GREEN))));
        if (found(id)) return Ui.icon(Material.GRAY_DYE, title(id, NamedTextColor.DARK_GRAY), List.of(Ui.line(id.startsWith(DexItems.MOB) ? "처치하면 등록됩니다." : "방문하면 등록됩니다.", NamedTextColor.GRAY)));
        long owned = catalog.count(player, id);
        return owned > 0
            ? Ui.decorate(catalog.item(id, 1), title(id, NamedTextColor.YELLOW), List.of(Ui.line("보유 " + owned + "개", NamedTextColor.GRAY), Ui.line("클릭하면 1개를 제출해 등록합니다. 제출한 아이템은 사라집니다.", NamedTextColor.GREEN)))
            : Ui.icon(Material.GRAY_DYE, title(id, NamedTextColor.DARK_GRAY), List.of(Ui.line("미등록", NamedTextColor.GRAY)));
    }
    private ItemStack progress(Profile profile) {
        List<Component> lore = new ArrayList<>();
        for (var reward : dex.rewards().entrySet()) {
            boolean claimed = profile.dexRewards().contains(reward.getKey());
            String text = reward.getKey() + "개: " + reward.getValue().describe();
            lore.add(Ui.line((claimed ? "✔ " : "· ") + text, claimed ? NamedTextColor.GREEN : NamedTextColor.GRAY));
        }
        return Ui.icon(Material.WRITABLE_BOOK, Ui.line("도감 " + profile.dex().size() + " / " + dex.total(), NamedTextColor.YELLOW), lore);
    }
    /** Registers a kill or a visit; nothing is handed in, and it works in any game mode. */
    void discover(Player player, String id) {
        if (!dex.has(id)) return;
        plugin.guarded(player, () -> {
            var uuid = player.getUniqueId();
            Profile before = store.load(uuid);
            if (before.dex().contains(id)) return;
            Profile after = dex.register(before, id);
            store.save(uuid, after);
            plugin.tell(player, Component.text("도감 등록: ").append(DexItems.name(id)).append(Component.text(" (" + after.dex().size() + "/" + dex.total() + ")")));
            announce(player, before, after);
            if (after.lamps() > before.lamps()) plugin.deliverLamps(player);
        });
    }
    private void announce(Player player, Profile before, Profile after) {
        for (int tier : after.dexRewards()) {
            if (before.dexRewards().contains(tier)) continue;
            plugin.tell(player, "도감 " + tier + "개 달성! " + dex.rewards().get(tier).describe());
        }
    }
    private final Map<UUID, String> lastBiome = new HashMap<>();
    void checkBiomes() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            String biome = player.getLocation().getBlock().getBiome().getKey().getKey();
            if (biome.equals(lastBiome.put(player.getUniqueId(), biome))) continue;
            discover(player, DexItems.BIOME + biome);
        }
    }
    @EventHandler
    public void onKill(org.bukkit.event.entity.EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer != null) discover(killer, DexItems.MOB + event.getEntityType().name());
    }
    @EventHandler
    public void onQuit(org.bukkit.event.player.PlayerQuitEvent event) { lastBiome.remove(event.getPlayer().getUniqueId()); }
    private int slotOf(Player player, String id) {
        ItemStack[] contents = player.getInventory().getStorageContents();
        for (int slot = 0; slot < contents.length; slot++) if (id.equals(catalog.idOf(contents[slot]))) return slot;
        return -1;
    }
    private void register(Player player, String id, int slot) throws IOException {
        if (!plugin.survival(player)) return;
        if (slot < 0 || id == null || !dex.has(id)) return;
        var uuid = player.getUniqueId();
        Profile before = store.load(uuid);
        if (before.dex().contains(id)) { plugin.tell(player, "이미 등록된 항목입니다."); return; }
        if (plugin.tradeHeld(player)) { plugin.tell(player, "거래를 완료하지 못했습니다. 아이템과 잔액을 확인해 주세요."); return; }
        Profile after = dex.register(before, id);
        plugin.transact(player, "dex", id, () -> {
            store.save(uuid, after);
            Ui.take(player, slot, 1);
        });
        plugin.tell(player, Component.text("도감 등록: ").append(catalog.name(id)).append(Component.text(" (" + after.dex().size() + "/" + dex.total() + ")")));
        announce(player, before, after);
    }
    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getClick() != ClickType.LEFT) return;
        int raw = event.getRawSlot();
        plugin.guarded(player, () -> {
            String tab = holder.tabs.get(raw), entry = holder.entries.get(raw);
            if (tab != null) { holder.page = tab; holder.subpage = 0; }
            else if (raw == PREV) holder.subpage = Math.max(0, holder.subpage - 1);
            else if (raw == NEXT) holder.subpage++;
            else if (entry != null) { if (found(entry)) return; register(player, entry, slotOf(player, entry)); }
            else if (raw >= Ui.SIZE) register(player, catalog.idOf(event.getCurrentItem()), event.getSlot());
            else return;
            render(holder, player);
        });
    }
    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Holder) event.setCancelled(true);
    }
}
