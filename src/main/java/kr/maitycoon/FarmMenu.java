package kr.maitycoon;

import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;

/** Farm management: workers and, while theft is on, the footprints that can still be reported. */
final class FarmMenu implements Listener {
    private static final class Holder implements InventoryHolder {
        private final Map<Integer, String> footprints = new HashMap<>();
        private final Map<Integer, UUID> workers = new HashMap<>();
        private Inventory inventory;
        @Override public Inventory getInventory() { return inventory; }
    }
    private static final int BODY_START = 9, BODY_SIZE = 36;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("MM-dd HH:mm").withZone(ZoneId.systemDefault());
    private final MaiTycoonPlugin plugin;
    private final Lands lands;
    FarmMenu(MaiTycoonPlugin plugin, Lands lands) {
        this.plugin = plugin;
        this.lands = lands;
    }
    void open(Player player) {
        Holder holder = new Holder();
        Component title = plugin.menuTitle(player, "토지 관리");
        if (plugin.packDrawn(player)) {
            var panel = new ShopTitle().screen(0xEC00).value(7, 143, Integer.toString(lands.parcelNames(player).size())).label(7, -1, "개");
            if (lands.workers(player).isEmpty()) panel.label(67, 55, "지정한 작업자 없음");
            title = panel.build();
        }
        holder.inventory = plugin.getServer().createInventory(holder, Ui.SIZE, title);
        render(holder, player);
        player.openInventory(holder.inventory);
    }
    private void render(Holder holder, Player player) {
        Inventory inventory = holder.inventory;
        boolean drawn = plugin.packDrawn(player);
        plugin.frame(inventory, player);
        holder.footprints.clear();
        holder.workers.clear();
        int slot = BODY_START;
        for (UUID worker : lands.workers(player)) {
            if (slot == BODY_START + BODY_SIZE) break;
            holder.workers.put(slot, worker);
            inventory.setItem(slot++, WorkerMenu.head(plugin.getServer().getOfflinePlayer(worker), lands.name(worker), List.of(
                Ui.line(WorkerMenu.rights(lands.rights(player, worker)), NamedTextColor.GRAY),
                Ui.line("누르면 권한을 바꿉니다.", NamedTextColor.GREEN))));
        }
        for (FootprintLog.Footprint footprint : lands.reportable(player)) {
            if (slot == BODY_START + BODY_SIZE) break;
            holder.footprints.put(slot, footprint.id());
            inventory.setItem(slot++, Ui.icon(Material.COARSE_DIRT, Ui.line("어둠의 발자국", NamedTextColor.RED), List.of(
                Ui.line("위치 " + footprint.x() + ", " + footprint.y() + ", " + footprint.z(), NamedTextColor.GRAY),
                Ui.line("시각 " + TIME.format(Instant.ofEpochMilli(footprint.createdAt())), NamedTextColor.GRAY),
                Ui.line(footprint.cause().endsWith("-immature") ? "덜 자란 작물이 훼손되었습니다." : "작물을 가져갔습니다.", NamedTextColor.GRAY),
                Ui.line("우클릭으로 신고", NamedTextColor.GREEN))));
        }
        Ui.button(inventory, MenuLayout.FARM_WORKERS, drawn, Ui.icon(Material.PLAYER_HEAD, Ui.line("작업자 추가", NamedTextColor.GREEN), List.of(
            Ui.line("지금 " + lands.workers(player).size() + "명이 지정되어 있습니다.", NamedTextColor.GRAY),
            Ui.line("내 땅에서 활동할 사람을 고릅니다.", NamedTextColor.GRAY))));
        Ui.button(inventory, MenuLayout.FARM_STATUS, drawn, Ui.icon(Material.WHEAT, Ui.line("작물 현황", NamedTextColor.YELLOW), List.of(Ui.line("내 땅의 특산 작물과 수확 시각을 봅니다.", NamedTextColor.GRAY))));
        Ui.button(inventory, MenuLayout.FARM_AUTO, drawn, Ui.icon(Material.WHEAT_SEEDS, Ui.line("자동 심기", NamedTextColor.YELLOW), List.of(Ui.line("켜고 끄거나 횟수를 삽니다.", NamedTextColor.GRAY))));
        List<String> parcels = lands.parcelNames(player);
        List<Component> lines = new ArrayList<>();
        for (String parcel : parcels) lines.add(Ui.line(parcel, NamedTextColor.GRAY));
        Ui.button(inventory, MenuLayout.FARM_PARCELS, drawn, Ui.icon(Material.GRASS_BLOCK, Ui.line("내 땅 " + parcels.size() + "개", NamedTextColor.GREEN), lines));
        Ui.button(inventory, MenuLayout.FARM_BACK, drawn, Ui.icon(Material.ARROW, Ui.line("토지로", NamedTextColor.WHITE), List.of()));
    }
    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        int slot = event.getRawSlot();
        if (MenuLayout.FARM_WORKERS.contains(slot)) { plugin.getServer().getScheduler().runTask(plugin, () -> lands.openWorkerAdd(player)); return; }
        UUID worker = holder.workers.get(slot);
        if (worker != null) { plugin.getServer().getScheduler().runTask(plugin, () -> lands.openWorkerRights(player, worker)); return; }
        if (MenuLayout.FARM_BACK.contains(slot)) { plugin.getServer().getScheduler().runTask(plugin, () -> lands.open(player)); return; }
        if (MenuLayout.FARM_STATUS.contains(slot)) { plugin.getServer().getScheduler().runTask(plugin, () -> plugin.openFarmStatus(player)); return; }
        if (MenuLayout.FARM_AUTO.contains(slot)) { plugin.getServer().getScheduler().runTask(plugin, () -> plugin.guarded(player, () -> plugin.openAutoPlant(player))); return; }
        String id = holder.footprints.get(slot);
        if (id == null || event.getClick() != ClickType.RIGHT) return;
        plugin.guarded(player, () -> { lands.report(player, id); plugin.getServer().getScheduler().runTask(plugin, () -> { if (player.getOpenInventory().getTopInventory().getHolder() == holder) open(player); }); });
    }
    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Holder) event.setCancelled(true);
    }
}
