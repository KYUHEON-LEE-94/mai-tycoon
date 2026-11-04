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
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

/** Balances, recent changes and sending 골드 to an online player. Money only exists on the account, never as an item. */
final class WalletMenu implements Listener {
    private enum Screen { MAIN, PLAYERS, AMOUNT }
    private static final class Holder implements InventoryHolder {
        private final Screen screen;
        private final UUID target;
        private final List<UUID> listed = new ArrayList<>();
        private long amount;
        private Inventory inventory;
        private Holder(Screen screen, UUID target) {
            this.screen = screen;
            this.target = target;
        }
        @Override public Inventory getInventory() { return inventory; }
    }
    private static final int GOLD = 10, ZCOIN = 12, LAMPS = 14, REPLANTS = 16, LOG_START = 27, LOG_SIZE = 9, SEND = 49, CLOSE = 53;
    private static final int TARGET = 4, ALL = 24, CLEAR = 25, CONFIRM = 48, BACK = 50, BODY_START = 9, BODY_SIZE = 36;
    private static final long[] STEPS = {1, 100, 1_000, 10_000};
    private static final int ADD_START = 19, TAKE_START = 28;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("MM-dd HH:mm").withZone(ZoneId.systemDefault());
    private static final Map<String, String> TYPES = Map.ofEntries(Map.entry("sell", "작물 판매"), Map.entry("buy", "상점 구매"), Map.entry("lamp-buy", "램프 구매"), Map.entry("land", "땅 구매"),
        Map.entry("dex", "도감 보상"), Map.entry("replant", "자동 심기 구매"), Map.entry("tractor", "트랙터 구매"), Map.entry("transfer-out", "송금"), Map.entry("admin", "관리자 조정"), Map.entry("transfer-in", "받은 돈"));
    private final MaiTycoonPlugin plugin;
    private final ProfileStore store;
    private final TransactionJournal journal;
    private final WalletLog log;
    WalletMenu(MaiTycoonPlugin plugin, ProfileStore store, TransactionJournal journal, WalletLog log) {
        this.plugin = plugin;
        this.store = store;
        this.journal = journal;
        this.log = log;
    }
    private static String money(long value) { return String.format("%,d", value); }
    private Holder holder(Player player, Screen screen, UUID target, String title) {
        Holder holder = new Holder(screen, target);
        holder.inventory = plugin.getServer().createInventory(holder, Ui.SIZE, plugin.menuTitle(player, title));
        plugin.frame(holder.inventory, player);
        return holder;
    }
    void open(Player player) throws IOException {
        Holder holder = holder(player, Screen.MAIN, null, "내 지갑");
        Inventory inventory = holder.inventory;
        var id = player.getUniqueId();
        Profile profile = store.load(id);
        inventory.setItem(GOLD, Ui.icon(Material.GOLD_INGOT, Ui.line(money(profile.coins()) + " 골드", NamedTextColor.GOLD), List.of(Ui.line("상품을 사고팔고 송금할 때 쓰는 돈입니다.", NamedTextColor.GRAY))));
        inventory.setItem(ZCOIN, Ui.icon(Material.SUNFLOWER, Ui.line(money(profile.zcoin()) + " 즈코인", NamedTextColor.YELLOW), List.of(Ui.line("도감 보상으로 받습니다. 골드로 바꿀 수 없습니다.", NamedTextColor.GRAY))));
        inventory.setItem(LAMPS, Ui.icon(Material.GLOWSTONE_DUST, Ui.line("생활 램프 " + money(plugin.carriedLamps(player)) + "개", NamedTextColor.GOLD), List.of()));
        inventory.setItem(REPLANTS, Ui.icon(Material.WHEAT_SEEDS, Ui.line("자동 심기 " + money(profile.replants()) + "회", NamedTextColor.GREEN), List.of()));
        int slot = LOG_START;
        for (WalletLog.Line line : log.recent(id, LOG_SIZE)) {
            long change = line.change();
            inventory.setItem(slot++, Ui.icon(change > 0 ? Material.LIME_DYE : Material.RED_DYE, Ui.line(TYPES.getOrDefault(line.type(), "거래"), NamedTextColor.WHITE), List.of(
                Ui.line((change > 0 ? "+" : "") + money(change) + " " + line.currency(), change > 0 ? NamedTextColor.GREEN : NamedTextColor.RED),
                Ui.line("잔액 " + money(line.after()) + " " + line.currency(), NamedTextColor.GRAY),
                Ui.line(TIME.format(Instant.ofEpochMilli(line.at())), NamedTextColor.DARK_GRAY))));
        }
        inventory.setItem(SEND, Ui.icon(Material.EMERALD, Ui.line("골드 보내기", NamedTextColor.GREEN), List.of(Ui.line("지금 접속한 플레이어에게 보냅니다.", NamedTextColor.GRAY), Ui.line("수수료는 없습니다.", NamedTextColor.GRAY))));
        inventory.setItem(CLOSE, Ui.icon(Material.BARRIER, Ui.line("닫기", NamedTextColor.RED), List.of()));
        player.openInventory(inventory);
    }
    private void openPlayers(Player player) {
        Holder holder = holder(player, Screen.PLAYERS, null, "골드 보내기");
        for (Player other : plugin.getServer().getOnlinePlayers()) {
            if (other.getUniqueId().equals(player.getUniqueId()) || holder.listed.size() == BODY_SIZE) continue;
            holder.inventory.setItem(BODY_START + holder.listed.size(), head(other, List.of(Ui.line("누르면 금액을 정합니다.", NamedTextColor.GRAY))));
            holder.listed.add(other.getUniqueId());
        }
        if (holder.listed.isEmpty()) holder.inventory.setItem(22, Ui.icon(Material.PAPER, Ui.line("접속한 다른 플레이어가 없습니다.", NamedTextColor.GRAY), List.of()));
        holder.inventory.setItem(BACK, Ui.icon(Material.ARROW, Ui.line("지갑으로", NamedTextColor.WHITE), List.of()));
        player.openInventory(holder.inventory);
    }
    private static ItemStack head(Player owner, List<Component> lore) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        if (head.getItemMeta() instanceof SkullMeta meta) {
            meta.setOwningPlayer(owner);
            head.setItemMeta(meta);
        }
        return Ui.decorate(head, Ui.line(owner.getName(), NamedTextColor.WHITE), lore);
    }
    private void openAmount(Player player, UUID target, long amount) throws IOException {
        Player receiver = plugin.getServer().getPlayer(target);
        if (receiver == null) { plugin.tell(player, "상대가 접속을 종료했습니다."); open(player); return; }
        Holder holder = holder(player, Screen.AMOUNT, target, "골드 보내기");
        holder.amount = amount;
        render(holder, player, receiver);
        player.openInventory(holder.inventory);
    }
    private void render(Holder holder, Player player, Player receiver) throws IOException {
        Inventory inventory = holder.inventory;
        plugin.frame(inventory, player);
        long coins = store.load(player.getUniqueId()).coins();
        inventory.setItem(TARGET, head(receiver, List.of(Ui.line("받는 사람", NamedTextColor.GRAY))));
        for (int i = 0; i < STEPS.length; i++) {
            inventory.setItem(ADD_START + i, Ui.icon(Material.LIME_STAINED_GLASS_PANE, Ui.line("+" + money(STEPS[i]), NamedTextColor.GREEN), List.of()));
            inventory.setItem(TAKE_START + i, Ui.icon(Material.RED_STAINED_GLASS_PANE, Ui.line("-" + money(STEPS[i]), NamedTextColor.RED), List.of()));
        }
        inventory.setItem(ALL, Ui.icon(Material.GOLD_BLOCK, Ui.line("전액", NamedTextColor.GOLD), List.of(Ui.line(money(coins) + " 골드", NamedTextColor.GRAY))));
        inventory.setItem(CLEAR, Ui.icon(Material.BUCKET, Ui.line("0으로", NamedTextColor.WHITE), List.of()));
        inventory.setItem(CONFIRM, Ui.icon(Material.LIME_CONCRETE, Ui.line("보내기", NamedTextColor.GREEN), List.of(
            Ui.line("받는 사람 " + receiver.getName(), NamedTextColor.WHITE),
            Ui.line("보내는 금액 " + money(holder.amount) + " 골드", NamedTextColor.YELLOW),
            Ui.line("받는 금액 " + money(holder.amount) + " 골드 · 수수료 0", NamedTextColor.GRAY),
            Ui.line("보낸 뒤 잔액 " + money(coins - holder.amount) + " 골드", NamedTextColor.GRAY),
            Ui.line("돈만 보냅니다. 물건을 받는 것은 보장되지 않습니다.", NamedTextColor.RED))));
        inventory.setItem(BACK, Ui.icon(Material.RED_CONCRETE, Ui.line("취소", NamedTextColor.RED), List.of()));
    }
    private void send(Player player, Holder holder) throws IOException {
        Player receiver = plugin.getServer().getPlayer(holder.target);
        if (receiver == null) { plugin.tell(player, "상대가 접속을 종료해 보내지 않았습니다."); open(player); return; }
        var from = player.getUniqueId();
        Profile sender = store.load(from), other = store.load(holder.target);
        switch (Transfer.run(journal, store, store::save, from, holder.target, holder.amount)) {
            case DONE -> {
                plugin.logWallet(from, "transfer-out", sender, store.load(from));
                plugin.logWallet(holder.target, "transfer-in", other, store.load(holder.target));
                plugin.tell(player, receiver.getName() + "에게 " + money(holder.amount) + " 골드를 보냈습니다. 잔액 " + money(sender.coins() - holder.amount) + " 골드");
                plugin.tell(receiver, player.getName() + "에게서 " + money(holder.amount) + " 골드를 받았습니다. 잔액 " + money(other.coins() + holder.amount) + " 골드");
                open(player);
            }
            case AMOUNT -> plugin.tell(player, "보낼 금액을 정해 주세요.");
            case FUNDS -> plugin.tell(player, "골드가 부족합니다. 보유 " + money(sender.coins()) + " 골드");
            case OVERFLOW -> plugin.tell(player, "상대가 더 받을 수 없는 금액입니다.");
            default -> plugin.tell(player, "거래를 완료하지 못했습니다. 아이템과 잔액을 확인해 주세요.");
        }
    }
    private void amountClick(Player player, Holder holder, int slot) throws IOException {
        long coins = store.load(player.getUniqueId()).coins(), next = holder.amount;
        if (slot >= ADD_START && slot < ADD_START + STEPS.length) next += STEPS[slot - ADD_START];
        else if (slot >= TAKE_START && slot < TAKE_START + STEPS.length) next -= STEPS[slot - TAKE_START];
        else if (slot == ALL) next = coins;
        else if (slot == CLEAR) next = 0;
        else if (slot == CONFIRM) { send(player, holder); return; }
        else if (slot == BACK) { open(player); return; }
        else return;
        holder.amount = Math.clamp(next, 0L, coins);
        Player receiver = plugin.getServer().getPlayer(holder.target);
        if (receiver == null) { plugin.tell(player, "상대가 접속을 종료했습니다."); open(player); return; }
        render(holder, player, receiver);
    }
    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || !player.hasPermission("maitycoon.play")) return;
        int slot = event.getRawSlot();
        // Screens change on the next tick, because a click cannot replace the window it happened in.
        plugin.getServer().getScheduler().runTask(plugin, () -> plugin.guarded(player, () -> {
            if (player.getOpenInventory().getTopInventory().getHolder() != holder) return;
            switch (holder.screen) {
                case MAIN -> {
                    if (slot == SEND) openPlayers(player);
                    else if (slot == CLOSE) player.closeInventory();
                }
                case PLAYERS -> {
                    if (slot == BACK) open(player);
                    else if (slot >= BODY_START && slot - BODY_START < holder.listed.size()) openAmount(player, holder.listed.get(slot - BODY_START), 0);
                }
                case AMOUNT -> amountClick(player, holder, slot);
            }
        }));
    }
    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Holder) event.setCancelled(true);
    }
}
