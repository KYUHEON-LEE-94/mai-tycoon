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
        SELL("농산물 상점"), BUY("씨앗 상점");
        final String title;
        Kind(String title) { this.title = title; }
    }
    record Listing(long price, String category) {}
    /** What a sale would do right now; two quotes are equal only if prices and contents both match. */
    private record Quote(long revision, Map<Material, Long> counts, long total) {
        long count() { return counts.values().stream().mapToLong(Long::longValue).sum(); }
    }
    private static final class Holder implements InventoryHolder {
        private final Kind kind;
        private List<Material> page = List.of();
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
        private final Material only;
        private Quote quote;
        private boolean changed;
        private Inventory inventory;
        private Confirm(Holder shop, Material only, Quote quote) {
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
    private final Map<Material, String> crops;
    private final Map<Material, Listing> seeds;
    private final Set<UUID> packReady = new HashSet<>();
    Shop(MaiTycoonPlugin plugin, Market market, ProfileStore store, Map<Material, String> crops, Map<Material, Listing> seeds) {
        this.plugin = plugin;
        this.market = market;
        this.store = store;
        this.crops = crops;
        this.seeds = seeds;
        for (Kind kind : Kind.values()) {
            if (categories(kind).size() > ShopLayout.MAX_TABS) throw new IllegalArgumentException("Too many shop categories for " + kind);
        }
    }
    void setPackReady(Player player, boolean ready) {
        if (ready) packReady.add(player.getUniqueId());
        else packReady.remove(player.getUniqueId());
        if (player.getOpenInventory().getTopInventory().getHolder() instanceof Holder holder) plugin.guarded(player, () -> { render(holder, player); show(holder, player); });
    }
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
    private List<String> categories(Kind kind) {
        List<String> tabs = new ArrayList<>(List.of(ALL));
        Collection<String> all = kind == Kind.SELL ? crops.values() : seeds.values().stream().map(Listing::category).toList();
        for (String category : all) if (!tabs.contains(category)) tabs.add(category);
        return tabs;
    }
    private String category(Kind kind, Material item) { return kind == Kind.SELL ? crops.get(item) : seeds.get(item).category(); }
    private List<Material> listed(Holder holder) {
        return (holder.kind == Kind.SELL ? crops.keySet() : seeds.keySet()).stream()
            .filter(item -> holder.tab.equals(ALL) || holder.tab.equals(category(holder.kind, item))).toList();
    }
    private void render(Holder holder, Player player) throws IOException {
        List<Material> listed = listed(holder);
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
            Material item = holder.page.get(i);
            inventory.setItem(ShopLayout.itemSlot(i), holder.kind == Kind.SELL ? sellIcon(item, player, holder) : buyIcon(item, holder));
            if (!drawn) inventory.setItem(ShopLayout.priceSlot(i), priceIcon(holder.kind, item));
        }
        if (drawn) holder.title = drawnTitle(holder, coins);
        else {
            plainControls(holder, pages);
            holder.title = Component.text(holder.kind.title + " · " + String.format("%,d", coins) + " YD");
        }
    }
    private Component drawnTitle(Holder holder, long coins) {
        ShopTitle title = new ShopTitle().background(holder.kind);
        int selected = holder.tabs.indexOf(holder.tab);
        if (selected >= 0) title.selectedTab(selected);
        for (int i = 0; i < holder.page.size(); i++) {
            Material item = holder.page.get(i);
            if (holder.kind == Kind.SELL) title.price(ShopLayout.row(i), ShopLayout.itemColumn(i) + 1, market.price(item.name()), market.change(item.name()));
            else title.price(ShopLayout.row(i), ShopLayout.itemColumn(i) + 1, seeds.get(item).price(), 0);
        }
        return title.coins(coins).amount(AMOUNTS[holder.amountIndex]).build();
    }
    private void plainControls(Holder holder, int pages) {
        Inventory inventory = holder.inventory;
        for (int i = 0; i < holder.tabs.size(); i++) {
            String tab = holder.tabs.get(i);
            ItemStack icon = Ui.icon(i == 0 ? Material.CHEST : tabIcon(holder.kind, tab), Ui.line(tab, NamedTextColor.WHITE), List.of());
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
    private Material tabIcon(Kind kind, String tab) {
        return (kind == Kind.SELL ? crops.keySet() : seeds.keySet()).stream()
            .filter(item -> tab.equals(category(kind, item))).findFirst().orElse(Material.CHEST);
    }
    private ItemStack priceIcon(Kind kind, Material item) {
        if (kind == Kind.BUY) return Ui.icon(Material.GOLD_NUGGET, Ui.line(seeds.get(item).price() + " YD", NamedTextColor.YELLOW), List.of());
        long change = market.change(item.name());
        String trend = change > 0 ? " ▲" + change : change < 0 ? " ▼" + -change : "";
        return Ui.icon(Material.GOLD_NUGGET, Ui.line(market.price(item.name()) + " YD" + trend, NamedTextColor.YELLOW), List.of());
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
            Ui.line("다음 시세까지 약 " + plugin.secondsToNextMarket() / 60 + "분", NamedTextColor.GRAY),
            Ui.line("보유 " + Ui.count(player, item) + "개", NamedTextColor.GRAY),
            Ui.line("좌클릭 " + AMOUNTS[holder.amountIndex] + "개 판매 / 우클릭 전부 판매", NamedTextColor.GREEN)));
    }
    private ItemStack buyIcon(Material item, Holder holder) {
        return Ui.icon(item, Ui.name(item, NamedTextColor.WHITE), List.of(
            Ui.line("개당 " + seeds.get(item).price() + " YD", NamedTextColor.YELLOW),
            Ui.line("좌클릭 " + buyAmount(item, AMOUNTS[holder.amountIndex]) + "개 구매 / 우클릭 " + item.getMaxStackSize() + "개 구매", NamedTextColor.GREEN)));
    }
    private static int buyAmount(Material item, int wanted) { return Math.min(wanted, item.getMaxStackSize()); }
    /** only == null covers every listed crop. Off-hand and named items stay untouched. */
    private Quote quote(Player player, Material only, long limit) {
        Map<Material, Long> counts = new LinkedHashMap<>();
        long total = 0, count = 0;
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (count >= limit) break;
            if (!Ui.plain(item) || !market.has(item.getType().name()) || (only != null && item.getType() != only)) continue;
            long amount = Math.min(item.getAmount(), limit - count);
            total = Math.addExact(total, Math.multiplyExact(market.price(item.getType().name()), amount));
            counts.merge(item.getType(), amount, Long::sum);
            count += amount;
        }
        return new Quote(market.revision(), counts, total);
    }
    private void execute(Player player, Quote quote) throws IOException {
        var id = player.getUniqueId();
        plugin.transact(player, "sell", quote.counts() + " +" + quote.total(), () -> {
            store.save(id, store.load(id).sell(quote.total()));
            take(player, quote.counts());
        });
        Component what = quote.counts().size() == 1 ? Component.translatable(quote.counts().keySet().iterator().next()) : Component.text("작물");
        plugin.tell(player, what.append(Component.text(" " + quote.count() + "개를 판매했습니다. +" + money(quote.total()) + " YD · 잔액 " + money(store.load(id).coins()) + " YD")));
    }
    private static void take(Player player, Map<Material, Long> counts) {
        Map<Material, Long> left = new HashMap<>(counts);
        ItemStack[] contents = player.getInventory().getStorageContents();
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack item = contents[slot];
            if (!Ui.plain(item)) continue;
            long wanted = left.getOrDefault(item.getType(), 0L);
            if (wanted == 0) continue;
            int amount = (int) Math.min(wanted, item.getAmount());
            left.put(item.getType(), wanted - amount);
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
    private void sellNow(Player player, Material item, long limit) throws IOException {
        if (!mayTrade(player)) return;
        Quote quote = quote(player, item, limit);
        if (quote.count() == 0) plugin.tell(player, "판매할 일반 작물이 없습니다.");
        else execute(player, quote);
    }
    private void openConfirm(Holder shop, Player player, Material only) throws IOException {
        if (!mayTrade(player)) return;
        Quote quote = quote(player, only, Long.MAX_VALUE);
        if (quote.count() == 0) { plugin.tell(player, "판매할 일반 작물이 없습니다."); return; }
        Confirm confirm = new Confirm(shop, only, quote);
        confirm.inventory = plugin.getServer().createInventory(confirm, ShopLayout.SIZE, Component.text("판매 확인"));
        renderConfirm(confirm, player);
        plugin.getServer().getScheduler().runTask(plugin, () -> player.openInventory(confirm.inventory));
    }
    private void renderConfirm(Confirm confirm, Player player) throws IOException {
        Inventory inventory = confirm.inventory;
        Ui.frame(inventory);
        long coins = store.load(player.getUniqueId()).coins();
        Quote quote = confirm.quote;
        List<Component> lore = new ArrayList<>(List.of(Ui.line("현재 잔액 " + money(coins) + " YD", NamedTextColor.GRAY), Ui.line("판매 후 잔액 " + money(coins + quote.total()) + " YD", NamedTextColor.GOLD)));
        if (confirm.changed) lore.add(Ui.line("시세나 인벤토리가 바뀌어 다시 계산했습니다.", NamedTextColor.RED));
        inventory.setItem(CONFIRM_TARGET, Ui.icon(Material.GOLD_INGOT, Ui.line("합계 " + money(quote.total()) + " YD · " + quote.count() + "개", NamedTextColor.YELLOW), lore));
        List<Material> items = List.copyOf(quote.counts().keySet());
        for (int i = 0; i < items.size() && i < CONFIRM_LINES.size(); i++) {
            Material item = items.get(i);
            long count = quote.counts().get(item);
            inventory.setItem(CONFIRM_LINES.get(i), Ui.icon(item, Ui.name(item, NamedTextColor.WHITE),
                List.of(Ui.line(count + "개 × " + market.price(item.name()) + " YD = " + money(count * market.price(item.name())) + " YD", NamedTextColor.GRAY))));
        }
        if (items.size() > CONFIRM_LINES.size()) {
            List<Component> rest = new ArrayList<>();
            for (Material item : items.subList(CONFIRM_LINES.size() - 1, items.size())) {
                rest.add(Ui.name(item, NamedTextColor.GRAY).append(Ui.line(" " + quote.counts().get(item) + "개", NamedTextColor.GRAY)));
            }
            inventory.setItem(CONFIRM_LINES.get(CONFIRM_LINES.size() - 1), Ui.icon(Material.CHEST, Ui.line("그 외 " + rest.size() + "종", NamedTextColor.WHITE), rest));
        }
        ItemStack yes = Ui.icon(Material.LIME_CONCRETE, Ui.line("판매", NamedTextColor.GREEN), List.of(Ui.line(money(quote.total()) + " YD를 받습니다.", NamedTextColor.GRAY)));
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
    private void buy(Player player, Material item, int amount) throws IOException {
        if (!mayTrade(player)) return;
        long cost = Math.multiplyExact(seeds.get(item).price(), amount);
        var id = player.getUniqueId();
        Profile profile = store.load(id);
        if (profile.coins() < cost) { plugin.tell(player, "YD가 부족합니다. 필요 " + money(cost) + " / 보유 " + money(profile.coins())); return; }
        if (freeSpace(player, item) < amount) {
            plugin.tell(player, Component.translatable(item).append(Component.text(" " + amount + "개를 넣을 공간이 부족합니다.")));
            return;
        }
        plugin.transact(player, "buy", item + " x" + amount + " -" + cost, () -> {
            store.save(id, profile.spend(cost));
            player.getInventory().addItem(new ItemStack(item, amount));
        });
        plugin.tell(player, Component.translatable(item).append(Component.text(" " + amount + "개를 구매했습니다. -" + money(cost) + " YD · 잔액 " + money(profile.coins() - cost) + " YD")));
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
            Material item = index >= 0 && index < holder.page.size() ? holder.page.get(index) : null;
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
            else if (holder.kind == Kind.BUY) buy(player, item, click == ClickType.LEFT ? buyAmount(item, AMOUNTS[holder.amountIndex]) : item.getMaxStackSize());
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
