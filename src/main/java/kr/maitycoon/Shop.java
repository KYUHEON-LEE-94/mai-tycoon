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

/**
 * NPC shop screens. Slots follow ShopLayout: players with the resource pack see the drawn screen, everyone else the same
 * slots filled with plain items. Bulk sales go through a quote that is rechecked on confirm.
 */
final class Shop implements Listener {
    enum Kind {
        SELL("농산물 상점"), BUY("씨앗 상점"), BUILD("건축 자재 상점"), FURNITURE("가구 상점");
        final String title;
        Kind(String title) { this.title = title; }
    }
    record Listing(long price, String category) {}
    /** What a sale would do right now; two quotes are equal only if prices and contents both match. */
    private record Quote(long revision, Map<String, Long> counts, long total) {
        long count() { return counts.values().stream().mapToLong(Long::longValue).sum(); }
    }
    private static final class Holder implements InventoryHolder {
        private final Kind kind;
        private List<String> page = List.of();
        private List<String> tabs = List.of();
        private String tab = ALL;
        private int pageIndex, amountIndex;
        private Inventory inventory;
        private Component title = Component.empty();
        private boolean reopening;
        private Holder(Kind kind) { this.kind = kind; }
        @Override public Inventory getInventory() { return inventory; }
    }
    private static final class Confirm implements InventoryHolder {
        private final Holder shop;
        private final String only;
        private Quote quote;
        private boolean changed;
        private Inventory inventory;
        private Confirm(Holder shop, String only, Quote quote) {
            this.shop = shop;
            this.only = only;
            this.quote = quote;
        }
        @Override public Inventory getInventory() { return inventory; }
    }
    private static final String ALL = "전체";
    private static final int[] AMOUNTS = {1, 8, 16, 32, 64};
    private static final char[] BARS = {'▁', '▂', '▃', '▄', '▅', '▆', '▇', '█'};
    private static final int CONFIRM_TARGET = 13;
    private static final List<Integer> CONFIRM_LINES = List.of(20, 21, 22, 23, 24), CONFIRM_YES = List.of(30, 31, 32), CONFIRM_NO = List.of(39, 40, 41);
    private final MaiTycoonPlugin plugin;
    private final Market market;
    private final ProfileStore store;
    private final Map<String, String> crops;
    private final Map<String, Listing> seeds;
    private final Map<String, Listing> building, furniture;
    private final Catalog catalog;
    private final Set<UUID> packReady = new HashSet<>();
    Shop(MaiTycoonPlugin plugin, Market market, ProfileStore store, Catalog catalog, Map<String, String> crops, Map<String, Listing> seeds, Map<String, Listing> building, Map<String, Listing> furniture) {
        this.plugin = plugin;
        this.market = market;
        this.store = store;
        this.catalog = catalog;
        this.crops = crops;
        this.seeds = seeds;
        this.building = building;
        this.furniture = furniture;
        for (Kind kind : Kind.values()) {
            if (categories(kind).size() > ShopLayout.MAX_TABS) throw new IllegalArgumentException("Too many shop categories for " + kind);
        }
    }
    void setPackReady(Player player, boolean ready) {
        if (ready) packReady.add(player.getUniqueId());
        else packReady.remove(player.getUniqueId());
        if (player.getOpenInventory().getTopInventory().getHolder() instanceof Holder holder) plugin.guarded(player, () -> { render(holder, player); show(holder, player); });
    }
    boolean drawn(Player player) { return packReady.contains(player.getUniqueId()); }
    void forget(Player player) { packReady.remove(player.getUniqueId()); }
    void open(Player player, Kind kind) {
        Holder holder = new Holder(kind);
        holder.inventory = plugin.getServer().createInventory(holder, ShopLayout.SIZE, Component.text(kind.title));
        plugin.guarded(player, () -> { render(holder, player); show(holder, player); });
    }
    /** A title cannot change on an open window, so the screen is opened again with the new one. */
    private void show(Holder holder, Player player) {
        Inventory replacement = plugin.getServer().createInventory(holder, ShopLayout.SIZE, holder.title);
        replacement.setContents(holder.inventory.getContents());
        holder.inventory = replacement;
        player.openInventory(replacement);
    }
    private void showLater(Holder holder, Player player) {
        if (holder.reopening) return;
        holder.reopening = true;
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            holder.reopening = false;
            if (player.getOpenInventory().getTopInventory().getHolder() == holder) show(holder, player);
        });
    }
    void refreshOpen() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            Object top = player.getOpenInventory().getTopInventory().getHolder();
            if (top instanceof Holder holder) plugin.guarded(player, () -> { render(holder, player); showLater(holder, player); });
            else if (top instanceof Confirm confirm) plugin.guarded(player, () -> requote(confirm, player));
        }
    }
    private Map<String, Listing> stock(Kind kind) { return kind == Kind.BUILD ? building : kind == Kind.FURNITURE ? furniture : seeds; }
    private List<String> categories(Kind kind) {
        List<String> tabs = new ArrayList<>(List.of(ALL));
        Collection<String> all = kind == Kind.SELL ? crops.values() : stock(kind).values().stream().map(Listing::category).toList();
        for (String category : all) if (!tabs.contains(category)) tabs.add(category);
        return tabs;
    }
    private String category(Kind kind, String item) { return kind == Kind.SELL ? crops.get(item) : stock(kind).get(item).category(); }
    private List<String> listed(Holder holder) {
        return (holder.kind == Kind.SELL ? crops.keySet() : stock(holder.kind).keySet()).stream()
            .filter(item -> holder.tab.equals(ALL) || holder.tab.equals(category(holder.kind, item))).toList();
    }
    private void render(Holder holder, Player player) throws IOException {
        List<String> listed = listed(holder);
        int pages = Math.max(1, (listed.size() + ShopLayout.PAGE_SIZE - 1) / ShopLayout.PAGE_SIZE);
        holder.pageIndex = Math.min(holder.pageIndex, pages - 1);
        int skip = holder.pageIndex * ShopLayout.PAGE_SIZE;
        holder.page = listed.subList(skip, Math.min(listed.size(), skip + ShopLayout.PAGE_SIZE));
        holder.tabs = categories(holder.kind);
        long coins = store.load(player.getUniqueId()).coins();
        boolean drawn = packReady.contains(player.getUniqueId());
        Inventory inventory = holder.inventory;
        inventory.clear();
        if (!drawn) Ui.frame(inventory);
        for (int i = 0; i < holder.page.size(); i++) {
            String item = holder.page.get(i);
            inventory.setItem(ShopLayout.itemSlot(i), holder.kind == Kind.SELL ? sellIcon(item, player, holder) : buyIcon(item, holder));
            if (!drawn) inventory.setItem(ShopLayout.priceSlot(i), priceIcon(holder.kind, item));
        }
        if (drawn) holder.title = drawnTitle(holder, coins, player, pages);
        else {
            plainControls(holder, pages);
            holder.title = Component.text(holder.kind.title + " · " + String.format("%,d", coins) + " 골드");
        }
    }
    private Component drawnTitle(Holder holder, long coins, Player player, int pages) {
        ShopTitle title = new ShopTitle().background(holder.kind);
        int selected = holder.tabs.indexOf(holder.tab);
        if (selected >= 0) title.selectedTab(selected);
        for (int i = 0; i < holder.page.size(); i++) {
            String item = holder.page.get(i);
            title.product(i, item, holder.kind == Kind.SELL ? market.price(item) : stock(holder.kind).get(item).price(), catalog.count(player, item));
        }
        return title.coins(coins).amount(AMOUNTS[holder.amountIndex]).page(holder.pageIndex + 1, pages).timer(java.time.LocalTime.now().plusSeconds(plugin.secondsToNextMarket()), holder.kind == Kind.SELL).build();
    }
    private void plainControls(Holder holder, int pages) {
        Inventory inventory = holder.inventory;
        for (int i = 0; i < holder.tabs.size(); i++) {
            String tab = holder.tabs.get(i);
            ItemStack icon = Ui.decorate(i == 0 ? new ItemStack(Material.CHEST) : tabIcon(holder.kind, tab), Ui.line(tab, NamedTextColor.WHITE), List.of());
            inventory.setItem(ShopLayout.tabSlot(i), tab.equals(holder.tab) ? Ui.glow(icon) : icon);
        }
        ItemStack amount = Ui.icon(Material.HOPPER, Ui.line("거래 수량: " + AMOUNTS[holder.amountIndex] + "개", NamedTextColor.AQUA), List.of(Ui.line("클릭하면 수량이 바뀝니다.", NamedTextColor.GRAY)));
        for (int slot : ShopLayout.AMOUNT) inventory.setItem(slot, amount);
        if (holder.kind == Kind.SELL) {
            ItemStack sellAll = Ui.icon(Material.EMERALD, Ui.line("모두 판매", NamedTextColor.GREEN), List.of(Ui.line("합계를 확인한 뒤 판매합니다.", NamedTextColor.GRAY)));
            for (int slot : ShopLayout.ACTION) inventory.setItem(slot, sellAll);
        }
        if (holder.pageIndex > 0) inventory.setItem(ShopLayout.PREV, Ui.icon(Material.ARROW, Ui.line("이전 페이지", NamedTextColor.WHITE), List.of()));
        if (holder.pageIndex < pages - 1) inventory.setItem(ShopLayout.NEXT, Ui.icon(Material.ARROW, Ui.line("다음 페이지", NamedTextColor.WHITE), List.of()));
    }
    private ItemStack tabIcon(Kind kind, String tab) {
        return (kind == Kind.SELL ? crops.keySet() : stock(kind).keySet()).stream()
            .filter(item -> tab.equals(category(kind, item))).findFirst().map(item -> catalog.item(item, 1)).orElse(new ItemStack(Material.CHEST));
    }
    private ItemStack priceIcon(Kind kind, String item) {
        if (kind != Kind.SELL) return Ui.icon(Material.GOLD_NUGGET, Ui.line(stock(kind).get(item).price() + " 골드", NamedTextColor.YELLOW), List.of());
        long change = market.change(item);
        String trend = change > 0 ? " ▲" + change : change < 0 ? " ▼" + -change : "";
        return Ui.icon(Material.GOLD_NUGGET, Ui.line(market.price(item) + " 골드" + trend, NamedTextColor.YELLOW), List.of());
    }
    private String graph(String id) {
        StringBuilder graph = new StringBuilder();
        int range = Math.max(1, market.maxPercent() - market.minPercent());
        for (int percent : market.trail(id)) graph.append(BARS[(percent - market.minPercent()) * (BARS.length - 1) / range]);
        return graph.toString();
    }
    private ItemStack sellIcon(String id, Player player, Holder holder) {
        long change = market.change(id);
        Component trend = change > 0 ? Ui.line("▲ " + change, NamedTextColor.RED) : change < 0 ? Ui.line("▼ " + -change, NamedTextColor.BLUE) : Ui.line("-", NamedTextColor.GRAY);
        return Ui.decorate(catalog.item(id, 1), catalog.name(id).color(NamedTextColor.WHITE).decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false), List.of(
            Ui.line("현재 " + market.price(id) + " 골드  ", NamedTextColor.YELLOW).append(trend),
            Ui.line("시세 " + graph(id), NamedTextColor.GREEN),
            Ui.line("범위 " + market.floor(id) + " ~ " + market.ceiling(id) + " 골드", NamedTextColor.GRAY),
            Ui.line("다음 시세까지 약 " + plugin.secondsToNextMarket() / 60 + "분", NamedTextColor.GRAY),
            Ui.line("보유 " + catalog.count(player, id) + "개", NamedTextColor.GRAY),
            Ui.line("이번 회차 서버 판매 " + market.sold(id) + "개 · 다음 하락 압력 " + market.pendingPressure(id) + "%", NamedTextColor.GOLD),
            Ui.line("좌클릭 " + AMOUNTS[holder.amountIndex] + "개 판매 / 우클릭 전부 판매", NamedTextColor.GREEN)));
    }
    private ItemStack buyIcon(String item, Holder holder) {
        return Ui.decorate(catalog.item(item, 1), catalog.name(item).color(NamedTextColor.WHITE).decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false), List.of(
            Ui.line("개당 " + stock(holder.kind).get(item).price() + " 골드", NamedTextColor.YELLOW),
            Ui.line("좌클릭 " + buyAmount(item, AMOUNTS[holder.amountIndex]) + "개 구매 / 우클릭 " + catalog.maxStack(item) + "개 구매", NamedTextColor.GREEN)));
    }
    private int buyAmount(String item, int wanted) { return Math.min(wanted, catalog.maxStack(item)); }
    /** only == null covers every listed crop. Off-hand and named items stay untouched. */
    private Quote quote(Player player, String only, long limit) {
        Map<String, Long> counts = new LinkedHashMap<>();
        long total = 0, count = 0;
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (count >= limit) break;
            String id = catalog.idOf(item);
            if (id == null || !market.has(id) || (only != null && !id.equals(only))) continue;
            long amount = Math.min(item.getAmount(), limit - count);
            total = Math.addExact(total, Math.multiplyExact(market.price(id), amount));
            counts.merge(id, amount, Long::sum);
            count += amount;
        }
        return new Quote(market.revision(), counts, total);
    }
    private void execute(Player player, Quote quote) throws IOException {
        var id = player.getUniqueId();
        plugin.transact(player, "sell", quote.counts() + " +" + quote.total(), () -> {
            store.save(id, store.load(id).sell(quote.total()));
            take(player, quote.counts());
            plugin.recordMarketSales(quote.counts());
        });
        Component what = quote.counts().size() == 1 ? catalog.name(quote.counts().keySet().iterator().next()) : Component.text("작물");
        plugin.tell(player, what.append(Component.text(" " + quote.count() + "개를 판매했습니다. +" + money(quote.total()) + " 골드 · 잔액 " + money(store.load(id).coins()) + " 골드")));
    }
    private void take(Player player, Map<String, Long> counts) {
        Map<String, Long> left = new HashMap<>(counts);
        ItemStack[] contents = player.getInventory().getStorageContents();
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack item = contents[slot];
            String id = catalog.idOf(item);
            long wanted = id == null ? 0 : left.getOrDefault(id, 0L);
            if (wanted == 0) continue;
            int amount = (int) Math.min(wanted, item.getAmount());
            left.put(id, wanted - amount);
            Ui.take(player, slot, amount);
        }
    }
    private static String money(long value) { return String.format("%,d", value); }
    private boolean mayTrade(Player player) {
        if (!plugin.survival(player)) return false;
        if (!plugin.tradeHeld(player)) return true;
        plugin.tell(player, "거래를 완료하지 못했습니다. 아이템과 잔액을 확인해 주세요.");
        return false;
    }
    private void sellNow(Player player, String item, long limit) throws IOException {
        if (!mayTrade(player)) return;
        Quote quote = quote(player, item, limit);
        if (quote.count() == 0) plugin.tell(player, "판매할 일반 작물이 없습니다.");
        else execute(player, quote);
    }
    private void openConfirm(Holder shop, Player player, String only) throws IOException {
        if (!mayTrade(player)) return;
        Quote quote = quote(player, only, Long.MAX_VALUE);
        if (quote.count() == 0) { plugin.tell(player, "판매할 일반 작물이 없습니다."); return; }
        Confirm confirm = new Confirm(shop, only, quote);
        confirm.inventory = plugin.getServer().createInventory(confirm, ShopLayout.SIZE, plugin.menuTitle(player, "판매 확인"));
        renderConfirm(confirm, player);
        plugin.getServer().getScheduler().runTask(plugin, () -> player.openInventory(confirm.inventory));
    }
    private void renderConfirm(Confirm confirm, Player player) throws IOException {
        Inventory inventory = confirm.inventory;
        plugin.frame(inventory, player);
        long coins = store.load(player.getUniqueId()).coins();
        Quote quote = confirm.quote;
        List<Component> lore = new ArrayList<>(List.of(Ui.line("현재 잔액 " + money(coins) + " 골드", NamedTextColor.GRAY), Ui.line("판매 후 잔액 " + money(coins + quote.total()) + " 골드", NamedTextColor.GOLD)));
        if (confirm.changed) lore.add(Ui.line("시세나 인벤토리가 바뀌어 다시 계산했습니다.", NamedTextColor.RED));
        inventory.setItem(CONFIRM_TARGET, Ui.icon(Material.GOLD_INGOT, Ui.line("합계 " + money(quote.total()) + " 골드 · " + quote.count() + "개", NamedTextColor.YELLOW), lore));
        List<String> items = List.copyOf(quote.counts().keySet());
        for (int i = 0; i < items.size() && i < CONFIRM_LINES.size(); i++) {
            String item = items.get(i);
            long count = quote.counts().get(item);
            inventory.setItem(CONFIRM_LINES.get(i), Ui.decorate(catalog.item(item, 1), catalog.name(item).color(NamedTextColor.WHITE).decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false),
                List.of(Ui.line(count + "개 × " + market.price(item) + " 골드 = " + money(count * market.price(item)) + " 골드", NamedTextColor.GRAY))));
        }
        if (items.size() > CONFIRM_LINES.size()) {
            List<Component> rest = new ArrayList<>();
            for (String item : items.subList(CONFIRM_LINES.size() - 1, items.size())) {
                rest.add(catalog.name(item).color(NamedTextColor.GRAY).decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false).append(Ui.line(" " + quote.counts().get(item) + "개", NamedTextColor.GRAY)));
            }
            inventory.setItem(CONFIRM_LINES.get(CONFIRM_LINES.size() - 1), Ui.icon(Material.CHEST, Ui.line("그 외 " + rest.size() + "종", NamedTextColor.WHITE), rest));
        }
        ItemStack yes = Ui.icon(Material.LIME_CONCRETE, Ui.line("판매", NamedTextColor.GREEN), List.of(Ui.line(money(quote.total()) + " 골드를 받습니다.", NamedTextColor.GRAY)));
        ItemStack no = Ui.icon(Material.RED_CONCRETE, Ui.line("취소", NamedTextColor.RED), List.of());
        for (int slot : CONFIRM_YES) inventory.setItem(slot, yes);
        for (int slot : CONFIRM_NO) inventory.setItem(slot, no);
    }
    private void backToShop(Confirm confirm, Player player) {
        plugin.getServer().getScheduler().runTask(plugin, () -> plugin.guarded(player, () -> { render(confirm.shop, player); show(confirm.shop, player); }));
    }
    /** Refreshes the screen when the quote went stale. Returns true if it is still valid. */
    private boolean requote(Confirm confirm, Player player) throws IOException {
        if (confirm.quote == null) return false;
        Quote fresh = quote(player, confirm.only, Long.MAX_VALUE);
        if (fresh.equals(confirm.quote)) return true;
        confirm.quote = fresh;
        confirm.changed = true;
        renderConfirm(confirm, player);
        plugin.tell(player, "시세가 바뀌어 합계를 다시 계산했습니다. 금액을 확인해 주세요.");
        return false;
    }
    private void buy(Player player, Kind kind, String item, int amount) throws IOException {
        if (!mayTrade(player)) return;
        long cost = Math.multiplyExact(stock(kind).get(item).price(), amount);
        var id = player.getUniqueId();
        Profile profile = store.load(id);
        if (profile.coins() < cost) { plugin.tell(player, "골드가 부족합니다. 필요 " + money(cost) + " / 보유 " + money(profile.coins())); return; }
        if (freeSpace(player, item) < amount) {
            plugin.tell(player, catalog.name(item).append(Component.text(" " + amount + "개를 넣을 공간이 부족합니다.")));
            return;
        }
        plugin.transact(player, "buy", item + " x" + amount + " -" + cost, () -> {
            store.save(id, profile.spend(cost));
            player.getInventory().addItem(catalog.item(item, amount));
        });
        plugin.tell(player, catalog.name(item).append(Component.text(" " + amount + "개를 구매했습니다. -" + money(cost) + " 골드 · 잔액 " + money(profile.coins() - cost) + " 골드")));
    }
    private int freeSpace(Player player, String item) {
        int free = 0, stack = catalog.maxStack(item);
        ItemStack sample = catalog.item(item, 1);
        for (ItemStack slot : player.getInventory().getStorageContents()) {
            if (slot == null || slot.getType().isAir()) free += stack;
            else if (slot.isSimilar(sample)) free += Math.max(0, stack - slot.getAmount());
        }
        return free;
    }
    @EventHandler
    public void onClick(InventoryClickEvent event) {
        Object top = event.getView().getTopInventory().getHolder();
        if (top instanceof Confirm confirm) { confirmClick(event, confirm); return; }
        if (!(top instanceof Holder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || !player.hasPermission("maitycoon.play")) return;
        ClickType click = event.getClick();
        if (click != ClickType.LEFT && click != ClickType.RIGHT) return;
        int slot = event.getRawSlot();
        if (slot >= ShopLayout.SIZE) return;
        plugin.guarded(player, () -> {
            int tab = ShopLayout.tabAt(slot), index = ShopLayout.itemAt(slot);
            String item = index >= 0 && index < holder.page.size() ? holder.page.get(index) : null;
            if (tab >= 0) {
                if (tab >= holder.tabs.size()) return;
                holder.tab = holder.tabs.get(tab);
                holder.pageIndex = 0;
            }
            else if (slot == ShopLayout.PREV) holder.pageIndex = Math.max(0, holder.pageIndex - 1);
            else if (slot == ShopLayout.NEXT) holder.pageIndex++;
            else if (ShopLayout.AMOUNT.contains(slot)) holder.amountIndex = (holder.amountIndex + 1) % AMOUNTS.length;
            else if (ShopLayout.ACTION.contains(slot)) {
                if (holder.kind == Kind.SELL) openConfirm(holder, player, null);
                return;
            }
            else if (item == null) return;
            else if (holder.kind != Kind.SELL) buy(player, holder.kind, item, click == ClickType.LEFT ? buyAmount(item, AMOUNTS[holder.amountIndex]) : catalog.maxStack(item));
            else if (click == ClickType.LEFT) sellNow(player, item, AMOUNTS[holder.amountIndex]);
            else { openConfirm(holder, player, item); return; }
            render(holder, player);
            showLater(holder, player);
        });
    }
    private void confirmClick(InventoryClickEvent event, Confirm confirm) {
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getClick() != ClickType.LEFT) return;
        int slot = event.getRawSlot();
        if (CONFIRM_NO.contains(slot)) { confirm.quote = null; backToShop(confirm, player); return; }
        if (!CONFIRM_YES.contains(slot)) return;
        plugin.guarded(player, () -> {
            if (!requote(confirm, player)) return;
            Quote quote = confirm.quote;
            confirm.quote = null;
            if (mayTrade(player)) execute(player, quote);
            backToShop(confirm, player);
        });
    }
    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Confirm confirm) confirm.quote = null;
    }
    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        Object top = event.getView().getTopInventory().getHolder();
        if (top instanceof Holder || top instanceof Confirm) event.setCancelled(true);
    }
}
