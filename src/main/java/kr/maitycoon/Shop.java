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

/** NPC shop GUIs: category tabs on top, item grid in the middle, amount and paging at the bottom. Saves before touching the inventory. */
final class Shop implements Listener {
    enum Kind {
        SELL("농산물 상점"), BUY("씨앗 상점");
        final String title;
        Kind(String title) { this.title = title; }
    }
    record Listing(long price, String category) {}
    private static final class Holder implements InventoryHolder {
        private final Kind kind;
        private final Map<Integer, Material> items = new HashMap<>();
        private final Map<Integer, String> tabs = new HashMap<>();
        private String tab = ALL;
        private int page, amountIndex;
        private Inventory inventory;
        private Holder(Kind kind) { this.kind = kind; }
        @Override public Inventory getInventory() { return inventory; }
    }
    private static final String ALL = "전체";
    private static final int INFO = 0, CLOCK = 8, FIRST_TAB = 2, PREV = 45, AMOUNT = 47, SELL_ALL = 49, NEXT = 53, BODY_START = 9, BODY_SIZE = 36;
    private static final int[] AMOUNTS = {1, 8, 16, 32, 64};
    private static final char[] BARS = {'▁', '▂', '▃', '▄', '▅', '▆', '▇', '█'};
    private final MaiTycoonPlugin plugin;
    private final Market market;
    private final ProfileStore store;
    private final Map<Material, String> crops;
    private final Map<Material, Listing> seeds;
    Shop(MaiTycoonPlugin plugin, Market market, ProfileStore store, Map<Material, String> crops, Map<Material, Listing> seeds) {
        this.plugin = plugin;
        this.market = market;
        this.store = store;
        this.crops = crops;
        this.seeds = seeds;
    }
    void open(Player player, Kind kind) {
        Holder holder = new Holder(kind);
        holder.inventory = plugin.getServer().createInventory(holder, Ui.SIZE, Component.text(kind.title));
        plugin.guarded(player, () -> render(holder, player));
        player.openInventory(holder.inventory);
    }
    void refreshOpen() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof Holder holder) plugin.guarded(player, () -> render(holder, player));
        }
    }
    private List<String> categories(Kind kind) {
        List<String> tabs = new ArrayList<>(List.of(ALL));
        Collection<String> all = kind == Kind.SELL ? crops.values() : seeds.values().stream().map(Listing::category).toList();
        for (String category : all) if (!tabs.contains(category)) tabs.add(category);
        return tabs;
    }
    private List<Material> listed(Holder holder) {
        return (holder.kind == Kind.SELL ? crops.keySet() : seeds.keySet()).stream()
            .filter(item -> holder.tab.equals(ALL) || holder.tab.equals(holder.kind == Kind.SELL ? crops.get(item) : seeds.get(item).category())).toList();
    }
    private void render(Holder holder, Player player) throws IOException {
        Inventory inventory = holder.inventory;
        Ui.frame(inventory);
        holder.items.clear();
        holder.tabs.clear();
        List<String> tabs = categories(holder.kind);
        for (int i = 0; i < tabs.size() && FIRST_TAB + i < CLOCK; i++) {
            String tab = tabs.get(i);
            ItemStack icon = Ui.icon(i == 0 ? Material.CHEST : tabIcon(holder, tab), Ui.line(tab, NamedTextColor.WHITE), List.of());
            holder.tabs.put(FIRST_TAB + i, tab);
            inventory.setItem(FIRST_TAB + i, tab.equals(holder.tab) ? Ui.glow(icon) : icon);
        }
        List<Material> listed = listed(holder);
        int pages = Math.max(1, (listed.size() + BODY_SIZE - 1) / BODY_SIZE);
        holder.page = Math.min(holder.page, pages - 1);
        int skip = holder.page * BODY_SIZE;
        for (int i = skip; i < listed.size() && i < skip + BODY_SIZE; i++) {
            Material item = listed.get(i);
            int slot = BODY_START + i - skip;
            holder.items.put(slot, item);
            inventory.setItem(slot, holder.kind == Kind.SELL ? sellIcon(item, player, holder) : buyIcon(item, holder));
        }
        long coins = store.load(player.getUniqueId()).coins();
        inventory.setItem(INFO, Ui.icon(Material.GOLD_INGOT, Ui.line(coins + " YD", NamedTextColor.GOLD), List.of(Ui.line("보유 금액", NamedTextColor.GRAY))));
        if (holder.kind == Kind.SELL) inventory.setItem(CLOCK, Ui.icon(Material.CLOCK, Ui.line("다음 시세까지", NamedTextColor.YELLOW),
            List.of(Ui.line("약 " + plugin.secondsToNextMarket() + "초", NamedTextColor.GRAY))));
        inventory.setItem(AMOUNT, Ui.icon(Material.HOPPER, Ui.line("거래 수량: " + AMOUNTS[holder.amountIndex] + "개", NamedTextColor.AQUA),
            List.of(Ui.line("클릭하면 수량이 바뀝니다.", NamedTextColor.GRAY))));
        if (holder.kind == Kind.SELL) inventory.setItem(SELL_ALL, Ui.icon(Material.EMERALD, Ui.line("작물 모두 판매", NamedTextColor.GREEN),
            List.of(Ui.line("인벤토리의 일반 작물을 현재 시세로 판매합니다.", NamedTextColor.GRAY))));
        if (holder.page > 0) inventory.setItem(PREV, Ui.icon(Material.ARROW, Ui.line("이전 페이지", NamedTextColor.WHITE), List.of()));
        if (holder.page < pages - 1) inventory.setItem(NEXT, Ui.icon(Material.ARROW, Ui.line("다음 페이지", NamedTextColor.WHITE), List.of()));
    }
    private Material tabIcon(Holder holder, String tab) {
        return (holder.kind == Kind.SELL ? crops.keySet() : seeds.keySet()).stream()
            .filter(item -> tab.equals(holder.kind == Kind.SELL ? crops.get(item) : seeds.get(item).category())).findFirst().orElse(Material.CHEST);
    }
    private String graph(String id) {
        StringBuilder graph = new StringBuilder();
        int range = Math.max(1, market.maxPercent() - market.minPercent());
        for (int percent : market.trail(id)) graph.append(BARS[(percent - market.minPercent()) * (BARS.length - 1) / range]);
        return graph.toString();
    }
    private ItemStack sellIcon(Material item, Player player, Holder holder) {
        String id = item.name();
        long change = market.change(id);
        Component trend = change > 0 ? Ui.line("▲ " + change, NamedTextColor.RED) : change < 0 ? Ui.line("▼ " + -change, NamedTextColor.BLUE) : Ui.line("-", NamedTextColor.GRAY);
        return Ui.icon(item, Ui.name(item, NamedTextColor.WHITE), List.of(
            Ui.line("현재 " + market.price(id) + " YD  ", NamedTextColor.YELLOW).append(trend),
            Ui.line("시세 " + graph(id), NamedTextColor.GREEN),
            Ui.line("범위 " + market.floor(id) + " ~ " + market.ceiling(id) + " YD", NamedTextColor.GRAY),
            Ui.line("보유 " + Ui.count(player, item) + "개", NamedTextColor.GRAY),
            Ui.line("좌클릭 " + AMOUNTS[holder.amountIndex] + "개 판매 / 우클릭 전부 판매", NamedTextColor.GREEN)));
    }
    private ItemStack buyIcon(Material item, Holder holder) {
        return Ui.icon(item, Ui.name(item, NamedTextColor.WHITE), List.of(
            Ui.line("개당 " + seeds.get(item).price() + " YD", NamedTextColor.YELLOW),
            Ui.line("좌클릭 " + buyAmount(item, AMOUNTS[holder.amountIndex]) + "개 구매 / 우클릭 " + item.getMaxStackSize() + "개 구매", NamedTextColor.GREEN)));
    }
    private static int buyAmount(Material item, int wanted) { return Math.min(wanted, item.getMaxStackSize()); }
    /** only == null sells every listed crop. Off-hand and named items stay untouched. */
    private void sell(Player player, Material only, long limit) throws IOException {
        if (!plugin.survival(player)) return;
        ItemStack[] contents = player.getInventory().getStorageContents();
        long income = 0, count = 0;
        Map<Integer, Integer> taken = new LinkedHashMap<>();
        for (int slot = 0; slot < contents.length && count < limit; slot++) {
            ItemStack item = contents[slot];
            if (!Ui.plain(item) || !market.has(item.getType().name()) || (only != null && item.getType() != only)) continue;
            int amount = (int) Math.min(item.getAmount(), limit - count);
            income = Math.addExact(income, Math.multiplyExact(market.price(item.getType().name()), amount));
            count += amount;
            taken.put(slot, amount);
        }
        if (count == 0) { plugin.tell(player, "판매할 일반 작물이 없습니다."); return; }
        var id = player.getUniqueId();
        store.save(id, store.load(id).sell(income));
        taken.forEach((slot, amount) -> Ui.take(player, slot, amount));
        plugin.tell(player, "작물 " + count + "개 판매! +" + income + " YD");
    }
    private void buy(Player player, Material item, int amount) throws IOException {
        if (!plugin.survival(player)) return;
        long cost = Math.multiplyExact(seeds.get(item).price(), amount);
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
        plugin.guarded(player, () -> {
            Material item = holder.items.get(slot);
            String tab = holder.tabs.get(slot);
            if (tab != null) { holder.tab = tab; holder.page = 0; }
            else if (slot == PREV) holder.page = Math.max(0, holder.page - 1);
            else if (slot == NEXT) holder.page++;
            else if (slot == AMOUNT) holder.amountIndex = (holder.amountIndex + 1) % AMOUNTS.length;
            else if (slot == SELL_ALL && holder.kind == Kind.SELL) sell(player, null, Long.MAX_VALUE);
            else if (item == null) return;
            else if (holder.kind == Kind.SELL) sell(player, item, click == ClickType.LEFT ? AMOUNTS[holder.amountIndex] : Long.MAX_VALUE);
            else buy(player, item, click == ClickType.LEFT ? buyAmount(item, AMOUNTS[holder.amountIndex]) : item.getMaxStackSize());
            render(holder, player);
        });
    }
    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Holder) event.setCancelled(true);
    }
}
