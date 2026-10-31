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

/** Farm management: workers and the footprints that can still be reported. */
final class FarmMenu implements Listener {
    private static final class Holder implements InventoryHolder {
        private final Map<Integer, String> footprints = new HashMap<>();
        private Inventory inventory;
        @Override public Inventory getInventory() { return inventory; }
    }
    private static final int BODY_START = 9, BODY_SIZE = 36, BACK = 45, WORKERS = 49;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("MM-dd HH:mm").withZone(ZoneId.systemDefault());
    private final MaiTycoonPlugin plugin;
    private final Lands lands;
    FarmMenu(MaiTycoonPlugin plugin, Lands lands) {
        this.plugin = plugin;
        this.lands = lands;
    }
    void open(Player player) {
        Holder holder = new Holder();
        holder.inventory = plugin.getServer().createInventory(holder, Ui.SIZE, Component.text("농장 관리"));
        render(holder, player);
        player.openInventory(holder.inventory);
    }
    private void render(Holder holder, Player player) {
        Inventory inventory = holder.inventory;
        Ui.frame(inventory);
        holder.footprints.clear();
        List<FootprintLog.Footprint> open = lands.reportable(player);
        for (int i = 0; i < open.size() && i < BODY_SIZE; i++) {
            var footprint = open.get(i);
            holder.footprints.put(BODY_START + i, footprint.id());
            inventory.setItem(BODY_START + i, Ui.icon(Material.COARSE_DIRT, Ui.line("어둠의 발자국", NamedTextColor.RED), List.of(
                Ui.line("위치 " + footprint.x() + ", " + footprint.y() + ", " + footprint.z(), NamedTextColor.GRAY),
                Ui.line("시각 " + TIME.format(Instant.ofEpochMilli(footprint.createdAt())), NamedTextColor.GRAY),
                Ui.line(footprint.cause().endsWith("-immature") ? "덜 자란 작물이 훼손되었습니다." : "작물을 가져갔습니다.", NamedTextColor.GRAY),
                Ui.line("우클릭으로 신고", NamedTextColor.GREEN))));
        }
        List<Component> names = new ArrayList<>();
        for (String name : lands.workerNames(player)) names.add(Ui.line(name, NamedTextColor.GRAY));
        names.add(Ui.line("/tycoon land member add <이름> 으로 추가합니다.", NamedTextColor.DARK_GRAY));
        names.add(Ui.line("작업자는 수확, 파종, 경작만 할 수 있습니다.", NamedTextColor.DARK_GRAY));
        inventory.setItem(WORKERS, Ui.icon(Material.PLAYER_HEAD, Ui.line("작업자 " + (names.size() - 2) + "명", NamedTextColor.GREEN), names));
        inventory.setItem(BACK, Ui.icon(Material.ARROW, Ui.line("땅 지도로", NamedTextColor.WHITE), List.of()));
    }
    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        int slot = event.getRawSlot();
        if (slot == BACK) { plugin.getServer().getScheduler().runTask(plugin, () -> lands.open(player)); return; }
        String id = holder.footprints.get(slot);
        if (id == null || event.getClick() != ClickType.RIGHT) return;
        plugin.guarded(player, () -> { lands.report(player, id); render(holder, player); });
    }
    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Holder) event.setCancelled(true);
    }
}
