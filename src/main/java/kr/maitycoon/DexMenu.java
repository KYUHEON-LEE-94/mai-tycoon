package kr.maitycoon;

import java.io.IOException;
import java.util.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
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
        private final Map<Integer, Material> entries = new HashMap<>();
        private String page;
        private Inventory inventory;
        @Override public Inventory getInventory() { return inventory; }
    }
    private static final int INFO = 0, FIRST_TAB = 2, BODY_START = 9, BODY_SIZE = 36, PROGRESS = 49;
    private final MaiTycoonPlugin plugin;
    private final Dex dex;
    private final ProfileStore store;
    DexMenu(MaiTycoonPlugin plugin, Dex dex, ProfileStore store) {
        this.plugin = plugin;
        this.dex = dex;
        this.store = store;
    }
    void open(Player player) {
        Holder holder = new Holder();
        holder.page = dex.pages().keySet().iterator().next();
        holder.inventory = plugin.getServer().createInventory(holder, Ui.SIZE, Component.text("수집 도감"));
        plugin.guarded(player, () -> render(holder, player));
        player.openInventory(holder.inventory);
    }
    private void render(Holder holder, Player player) throws IOException {
        Profile profile = store.load(player.getUniqueId());
        Inventory inventory = holder.inventory;
        Ui.frame(inventory);
        holder.tabs.clear();
        holder.entries.clear();
        int slot = FIRST_TAB;
        for (var page : dex.pages().entrySet()) {
            long done = page.getValue().stream().filter(profile.dex()::contains).count();
            ItemStack tab = Ui.icon(Material.valueOf(page.getValue().get(0)), Ui.line(page.getKey(), NamedTextColor.WHITE),
                List.of(Ui.line(done + " / " + page.getValue().size(), NamedTextColor.GRAY)));
            holder.tabs.put(slot, page.getKey());
            inventory.setItem(slot++, page.getKey().equals(holder.page) ? Ui.glow(tab) : tab);
        }
        List<String> entries = dex.pages().get(holder.page);
        for (int i = 0; i < entries.size() && i < BODY_SIZE; i++) {
            Material item = Material.valueOf(entries.get(i));
            holder.entries.put(BODY_START + i, item);
            inventory.setItem(BODY_START + i, entry(item, profile, player));
        }
        inventory.setItem(INFO, Ui.icon(Material.GOLD_INGOT, Ui.line(profile.coins() + " YD", NamedTextColor.GOLD), List.of(Ui.line("즈코인 " + profile.zcoin() + "개", NamedTextColor.AQUA))));
        inventory.setItem(PROGRESS, progress(profile));
    }
    private ItemStack entry(Material item, Profile profile, Player player) {
        if (profile.dex().contains(item.name())) {
            return Ui.glow(Ui.icon(item, Ui.name(item, NamedTextColor.GREEN), List.of(Ui.line("등록 완료", NamedTextColor.GREEN))));
        }
        long owned = Ui.count(player, item);
        return owned > 0
            ? Ui.icon(item, Ui.name(item, NamedTextColor.YELLOW), List.of(Ui.line("보유 " + owned + "개", NamedTextColor.GRAY), Ui.line("클릭하면 1개를 제출해 등록합니다. 제출한 아이템은 사라집니다.", NamedTextColor.GREEN)))
            : Ui.icon(Material.GRAY_DYE, Ui.name(item, NamedTextColor.DARK_GRAY), List.of(Ui.line("미등록", NamedTextColor.GRAY)));
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
    private void register(Player player, Material item, int slot) throws IOException {
        if (!plugin.survival(player)) return;
        if (slot < 0 || !dex.has(item.name())) return;
        var id = player.getUniqueId();
        Profile before = store.load(id);
        if (before.dex().contains(item.name())) { plugin.tell(player, "이미 등록된 항목입니다."); return; }
        if (plugin.tradeHeld(player)) { plugin.tell(player, "거래를 완료하지 못했습니다. 아이템과 잔액을 확인해 주세요."); return; }
        Profile after = dex.register(before, item.name());
        plugin.transact(player, "dex", item.name(), () -> {
            store.save(id, after);
            Ui.take(player, slot, 1);
        });
        plugin.tell(player, Component.text("도감 등록: ").append(Component.translatable(item)).append(Component.text(" (" + after.dex().size() + "/" + dex.total() + ")")));
        for (int tier : after.dexRewards()) {
            if (before.dexRewards().contains(tier)) continue;
            Dex.Reward reward = dex.rewards().get(tier);
            plugin.tell(player, "도감 " + tier + "개 달성! " + reward.describe());
        }
    }
    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getClick() != ClickType.LEFT) return;
        int raw = event.getRawSlot();
        plugin.guarded(player, () -> {
            String tab = holder.tabs.get(raw);
            Material entry = holder.entries.get(raw);
            if (tab != null) holder.page = tab;
            else if (entry != null) register(player, entry, Ui.firstPlain(player, entry));
            else if (raw >= Ui.SIZE && Ui.plain(event.getCurrentItem())) register(player, event.getCurrentItem().getType(), event.getSlot());
            else return;
            render(holder, player);
        });
    }
    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Holder) event.setCancelled(true);
    }
}
