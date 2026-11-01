package kr.maitycoon;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/** Read-only board of every crop's price and supply, busiest first. The next price is random, so it is never predicted. */
final class MarketMenu implements Listener {
    private static final class Holder implements InventoryHolder {
        private int page;
        private Inventory inventory;
        @Override public Inventory getInventory() { return inventory; }
    }
    private static final int INFO = 4, BODY_START = 9, BODY_SIZE = 36, PREV = 45, NEXT = 53;
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");
    private final MaiTycoonPlugin plugin;
    private final Market market;
    private final Catalog catalog;
    MarketMenu(MaiTycoonPlugin plugin, Market market, Catalog catalog) {
        this.plugin = plugin;
        this.market = market;
        this.catalog = catalog;
    }
    /** Shared with the sell screen so both explain supply in the same words. */
    static List<Component> supply(Market market, String id, long secondsLeft) {
        List<Component> lines = new ArrayList<>();
        lines.add(Ui.line("이번 회차 서버 판매 " + market.sold(id) + "개", NamedTextColor.GOLD));
        long more = market.untilNextPressure(id);
        lines.add(Ui.line("다음 시세 하락 압력 " + market.pendingPressure(id) + "%" + (more == 0 ? " (최대)" : " · " + more + "개 더 팔리면 올라감"), NamedTextColor.GOLD));
        if (market.lastPressure(id) > 0) lines.add(Ui.line("지난 회차 판매량 때문에 하락 압력 " + market.lastPressure(id) + "%가 적용된 시세입니다", NamedTextColor.RED));
        lines.add(Ui.line(LocalTime.now().plusSeconds(secondsLeft).format(CLOCK) + "에 바뀝니다 · 다음 가격은 무작위입니다", NamedTextColor.GRAY));
        return lines;
    }
    private List<String> busiestFirst() {
        List<String> ids = new ArrayList<>(market.ids());
        ids.sort(Comparator.comparingLong(market::sold).reversed());
        return ids;
    }
    void open(Player player) {
        Holder holder = new Holder();
        holder.inventory = plugin.getServer().createInventory(holder, Ui.SIZE, plugin.menuTitle(player, "시세 게시판"));
        render(holder, player);
        player.openInventory(holder.inventory);
    }
    private void render(Holder holder, Player player) {
        Inventory inventory = holder.inventory;
        plugin.frame(inventory, player);
        List<String> ids = busiestFirst();
        int pages = Math.max(1, (ids.size() + BODY_SIZE - 1) / BODY_SIZE);
        holder.page = Math.min(holder.page, pages - 1);
        long left = plugin.secondsToNextMarket();
        inventory.setItem(INFO, Ui.icon(Material.CLOCK, Ui.line("다음 시세 약 " + left / 60 + "분 뒤", NamedTextColor.YELLOW), List.of(
            Ui.line("품목별로 많이 팔릴수록 다음 시세가 내려갑니다.", NamedTextColor.GRAY),
            Ui.line("판매는 작물 상인에게 하세요.", NamedTextColor.GRAY))));
        int start = holder.page * BODY_SIZE;
        for (int i = start; i < ids.size() && i < start + BODY_SIZE; i++) {
            String id = ids.get(i);
            long change = market.change(id);
            List<Component> lore = new ArrayList<>(List.of(
                Ui.line(market.price(id) + " 골드" + (change > 0 ? " ▲" + change : change < 0 ? " ▼" + -change : ""), NamedTextColor.YELLOW),
                Ui.line("시세 " + Shop.graph(market, id), NamedTextColor.GREEN),
                Ui.line("범위 " + market.floor(id) + " ~ " + market.ceiling(id) + " 골드", NamedTextColor.GRAY),
                Ui.line("보유 " + catalog.count(player, id) + "개", NamedTextColor.GRAY)));
            lore.addAll(supply(market, id, left));
            inventory.setItem(BODY_START + i - start, Ui.decorate(catalog.item(id, 1), catalog.name(id).color(NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false), lore));
        }
        if (holder.page > 0) inventory.setItem(PREV, Ui.icon(Material.ARROW, Ui.line("이전", NamedTextColor.WHITE), List.of()));
        if (holder.page < pages - 1) inventory.setItem(NEXT, Ui.icon(Material.ARROW, Ui.line("다음", NamedTextColor.WHITE), List.of()));
    }
    void refreshOpen() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof Holder holder) render(holder, player);
        }
    }
    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getRawSlot() == PREV && holder.page > 0) holder.page--;
        else if (event.getRawSlot() == NEXT) holder.page++;
        else return;
        render(holder, player);
    }
    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Holder) event.setCancelled(true);
    }
}
