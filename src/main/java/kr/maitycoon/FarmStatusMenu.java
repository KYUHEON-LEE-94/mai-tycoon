package kr.maitycoon;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/** Custom crops on the player's own land only. Vanilla crops grow on random ticks, so no harvest time can be promised for them. */
final class FarmStatusMenu implements Listener {
    private static final class Holder implements InventoryHolder {
        private Inventory inventory;
        @Override public Inventory getInventory() { return inventory; }
    }
    private static final int INFO = 4, BODY_START = 9, BODY_SIZE = 36, BACK = 45;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("MM-dd HH:mm").withZone(ZoneId.systemDefault());
    private final MaiTycoonPlugin plugin;
    private final Lands lands;
    private final FarmIndex index;
    private final Catalog catalog;
    FarmStatusMenu(MaiTycoonPlugin plugin, Lands lands, FarmIndex index, Catalog catalog) {
        this.plugin = plugin;
        this.lands = lands;
        this.index = index;
        this.catalog = catalog;
    }
    private boolean loaded(String key) {
        String[] parts = key.split(":");
        World world = plugin.getServer().getWorld(UUID.fromString(parts[0]));
        return world != null && world.isChunkLoaded(Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
    }
    private long grow(String id) {
        Catalog.Crop crop = catalog.crop(id);
        return crop == null ? 0 : crop.growMillis();
    }
    void open(Player player) {
        Holder holder = new Holder();
        holder.inventory = plugin.getServer().createInventory(holder, Ui.SIZE, plugin.menuTitle(player, "작물 현황"));
        Inventory inventory = holder.inventory;
        plugin.frame(inventory, player);
        List<String> owned = lands.ownedKeys(player.getUniqueId());
        List<FarmIndex.Entry> known = new ArrayList<>();
        int unknown = 0;
        long oldest = 0;
        for (String key : owned) {
            FarmIndex.Entry entry = index.entry(key);
            boolean live = loaded(key);
            if (entry == null) { if (!live) unknown++; continue; }
            known.add(entry);
            if (!live && (oldest == 0 || entry.seenAt() < oldest)) oldest = entry.seenAt();
        }
        long now = System.currentTimeMillis();
        Map<String, FarmIndex.Row> rows = FarmIndex.summarize(known, now, this::grow);
        List<Component> notes = new ArrayList<>();
        if (owned.isEmpty()) notes.add(Ui.line("아직 땅이 없습니다. 마을 공용 농장은 누구나 쓸 수 있습니다.", NamedTextColor.GRAY));
        else {
            notes.add(Ui.line("재배 " + rows.values().stream().mapToInt(FarmIndex.Row::planted).sum() + "칸 · 수확 가능 " + rows.values().stream().mapToInt(FarmIndex.Row::ready).sum() + "칸", NamedTextColor.YELLOW));
            if (oldest > 0) notes.add(Ui.line("멀리 있는 땅은 " + TIME.format(Instant.ofEpochMilli(oldest)) + " 이후 기록입니다.", NamedTextColor.GRAY));
            if (unknown > 0) notes.add(Ui.line("기록이 없는 청크 " + unknown + "개는 그 땅에 가면 집계됩니다.", NamedTextColor.RED));
        }
        notes.add(Ui.line("특산 작물만 표시합니다. 밀 같은 일반 작물은 자라는 시간이 일정하지 않습니다.", NamedTextColor.DARK_GRAY));
        inventory.setItem(INFO, Ui.icon(Material.CLOCK, Ui.line("내 땅 " + owned.size() + "청크", NamedTextColor.GREEN), notes));
        int slot = BODY_START;
        List<Map.Entry<String, FarmIndex.Row>> sorted = new ArrayList<>(rows.entrySet());
        sorted.sort(Comparator.comparingInt((Map.Entry<String, FarmIndex.Row> row) -> row.getValue().ready()).reversed());
        for (var row : sorted) {
            if (slot == BODY_START + BODY_SIZE) break;
            FarmIndex.Row value = row.getValue();
            List<Component> lore = new ArrayList<>(List.of(Ui.line("재배 " + value.planted() + "칸", NamedTextColor.GRAY), Ui.line("수확 가능 " + value.ready() + "칸", value.ready() > 0 ? NamedTextColor.GREEN : NamedTextColor.GRAY)));
            if (value.nextReadyAt() > 0) lore.add(Ui.line("다음 수확 약 " + Math.max(1, (value.nextReadyAt() - now + 59_999) / 60_000) + "분 뒤", NamedTextColor.YELLOW));
            inventory.setItem(slot++, Ui.decorate(catalog.item(row.getKey(), 1), catalog.name(row.getKey()).color(NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false), lore));
        }
        inventory.setItem(BACK, Ui.icon(Material.ARROW, Ui.line("농장 관리로", NamedTextColor.WHITE), List.of()));
        player.openInventory(inventory);
    }
    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder)) return;
        event.setCancelled(true);
        if (event.getRawSlot() == BACK && event.getWhoClicked() instanceof Player player) plugin.getServer().getScheduler().runTask(plugin, () -> lands.openFarm(player));
    }
    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Holder) event.setCancelled(true);
    }
}
