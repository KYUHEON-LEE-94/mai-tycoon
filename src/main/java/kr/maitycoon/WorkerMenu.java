package kr.maitycoon;

import java.io.IOException;
import java.util.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.SkullMeta;

/** Choosing who may work on a player's land and what each of them may do there. */
final class WorkerMenu implements Listener {
    private static final class Holder implements InventoryHolder {
        private final UUID worker;
        private final List<UUID> listed = new ArrayList<>();
        private Inventory inventory;
        private Holder(UUID worker) { this.worker = worker; }
        @Override public Inventory getInventory() { return inventory; }
    }
    private record Right(LandRegistry.Action action, String name, String detail, Material icon) {}
    private static final List<Right> RIGHTS = List.of(
        new Right(LandRegistry.Action.HARVEST, "수확", "작물을 거둡니다.", Material.WHEAT),
        new Right(LandRegistry.Action.PLANT, "파종", "씨앗을 심습니다.", Material.WHEAT_SEEDS),
        new Right(LandRegistry.Action.TILL, "경작", "괭이로 밭을 일굽니다.", Material.IRON_HOE),
        new Right(LandRegistry.Action.BUILD, "건축", "블록을 놓고 부숩니다.", Material.BRICKS),
        new Right(LandRegistry.Action.USE, "상자와 문", "상자, 문, 작업대 같은 블록을 씁니다.", Material.CHEST));
    private static final int TARGET = 4, ICON_START = 20, STATE_START = 29, REMOVE = 48, BACK = 50, BODY_START = 9, BODY_SIZE = 36;
    private final MaiTycoonPlugin plugin;
    private final Lands lands;
    WorkerMenu(MaiTycoonPlugin plugin, Lands lands) {
        this.plugin = plugin;
        this.lands = lands;
    }
    static String rights(Set<LandRegistry.Action> allowed) {
        StringJoiner names = new StringJoiner(", ");
        for (Right right : RIGHTS) if (allowed.contains(right.action())) names.add(right.name());
        return names.length() == 0 ? "권한 없음" : names.toString();
    }
    static ItemStack head(OfflinePlayer owner, String name, List<Component> lore) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        if (head.getItemMeta() instanceof SkullMeta meta) {
            meta.setOwningPlayer(owner);
            head.setItemMeta(meta);
        }
        return Ui.decorate(head, Ui.line(name, NamedTextColor.WHITE), lore);
    }
    private Holder holder(Player player, UUID worker, String title) {
        Holder holder = new Holder(worker);
        holder.inventory = plugin.getServer().createInventory(holder, Ui.SIZE, plugin.menuTitle(player, title));
        plugin.frame(holder.inventory, player);
        return holder;
    }
    void openAdd(Player player) {
        Holder holder = holder(player, null, "작업자 추가");
        Set<UUID> current = lands.workers(player);
        for (Player other : plugin.getServer().getOnlinePlayers()) {
            UUID id = other.getUniqueId();
            if (id.equals(player.getUniqueId()) || current.contains(id) || holder.listed.size() == BODY_SIZE) continue;
            holder.inventory.setItem(BODY_START + holder.listed.size(), head(other, other.getName(), List.of(Ui.line("누르면 작업자로 지정합니다.", NamedTextColor.GRAY))));
            holder.listed.add(id);
        }
        if (holder.listed.isEmpty()) holder.inventory.setItem(22, Ui.icon(Material.PAPER, Ui.line("지정할 수 있는 플레이어가 없습니다.", NamedTextColor.GRAY), List.of(Ui.line("접속해 있지 않다면 /tycoon land member add <이름>", NamedTextColor.DARK_GRAY))));
        holder.inventory.setItem(BACK, Ui.icon(Material.ARROW, Ui.line("토지 관리로", NamedTextColor.WHITE), List.of()));
        player.openInventory(holder.inventory);
    }
    void openRights(Player player, UUID worker) {
        if (!lands.workers(player).contains(worker)) { lands.openFarm(player); return; }
        Holder holder = holder(player, worker, "작업자 권한");
        render(holder, player);
        player.openInventory(holder.inventory);
    }
    private void render(Holder holder, Player player) {
        Inventory inventory = holder.inventory;
        Set<LandRegistry.Action> allowed = lands.rights(player, holder.worker);
        inventory.setItem(TARGET, head(plugin.getServer().getOfflinePlayer(holder.worker), lands.name(holder.worker), List.of(Ui.line("내 땅 전체에 적용됩니다.", NamedTextColor.GRAY))));
        for (int i = 0; i < RIGHTS.size(); i++) {
            Right right = RIGHTS.get(i);
            boolean on = allowed.contains(right.action());
            List<Component> lore = List.of(Ui.line(right.detail(), NamedTextColor.GRAY), Ui.line(on ? "허용됨 · 누르면 끕니다." : "금지됨 · 누르면 켭니다.", on ? NamedTextColor.GREEN : NamedTextColor.RED));
            inventory.setItem(ICON_START + i, Ui.icon(right.icon(), Ui.line(right.name(), NamedTextColor.YELLOW), lore));
            inventory.setItem(STATE_START + i, Ui.icon(on ? Material.LIME_DYE : Material.GRAY_DYE, Ui.line(on ? "허용" : "금지", on ? NamedTextColor.GREEN : NamedTextColor.GRAY), lore));
        }
        inventory.setItem(REMOVE, Ui.icon(Material.BARRIER, Ui.line("작업자에서 제외", NamedTextColor.RED), List.of()));
        inventory.setItem(BACK, Ui.icon(Material.ARROW, Ui.line("토지 관리로", NamedTextColor.WHITE), List.of()));
    }
    private void later(Player player, MaiTycoonPlugin.Action action) {
        plugin.getServer().getScheduler().runTask(plugin, () -> plugin.guarded(player, action));
    }
    private static int right(int slot) {
        for (int start : new int[]{ICON_START, STATE_START}) if (slot >= start && slot < start + RIGHTS.size()) return slot - start;
        return -1;
    }
    private void clickRights(Holder holder, Player player, int slot) throws IOException {
        if (slot == REMOVE) {
            lands.removeWorker(player, holder.worker);
            later(player, () -> lands.openFarm(player));
            return;
        }
        int index = right(slot);
        if (index < 0) return;
        lands.toggleRight(player, holder.worker, RIGHTS.get(index).action());
        render(holder, player);
    }
    private void clickAdd(Holder holder, Player player, int slot) throws IOException {
        int index = slot - BODY_START;
        if (index < 0 || index >= holder.listed.size()) return;
        UUID picked = holder.listed.get(index);
        lands.addWorker(player, picked);
        later(player, () -> openRights(player, picked));
    }
    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getClick() != ClickType.LEFT) return;
        int slot = event.getRawSlot();
        if (slot == BACK) { later(player, () -> lands.openFarm(player)); return; }
        plugin.guarded(player, () -> {
            if (holder.worker == null) clickAdd(holder, player, slot);
            else clickRights(holder, player, slot);
        });
    }
    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Holder) event.setCancelled(true);
    }
}
