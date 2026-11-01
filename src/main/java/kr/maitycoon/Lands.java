package kr.maitycoon;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Directional;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.world.StructureGrowEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;

/** Chunk land: purchase map, role-based protection and the footprints left by taking crops from a claim. */
final class Lands implements Listener {
    private static final class BuyHolder implements InventoryHolder {
        private final int chunkX, chunkZ;
        private final long cost;
        private final boolean bought;
        private boolean done;
        private Inventory inventory;
        private BuyHolder(int chunkX, int chunkZ, long cost, boolean bought) {
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.cost = cost;
            this.bought = bought;
        }
        @Override public Inventory getInventory() { return inventory; }
    }
    private static final class Holder implements InventoryHolder {
        private final Map<Integer, int[]> chunks = new HashMap<>();
        private final int centerX, centerZ;
        private int chosenX, chosenZ;
        private boolean reopening;
        private Inventory inventory;
        private Holder(int centerX, int centerZ, int chosenX, int chosenZ) {
            this.centerX = centerX;
            this.centerZ = centerZ;
            this.chosenX = chosenX;
            this.chosenZ = chosenZ;
        }
        @Override public Inventory getInventory() { return inventory; }
    }
    private enum Status { VILLAGE, MINE, TAKEN, FREE }
    private final MaiTycoonPlugin plugin;
    private final ProfileStore store;
    private final Village village;
    private final LandRegistry registry = new LandRegistry();
    private final FootprintLog footprints;
    private final Map<UUID, net.kyori.adventure.bossbar.BossBar> banners = new HashMap<>();
    private final Map<UUID, String> shown = new HashMap<>();
    private final Map<UUID, Long> linkSent = new HashMap<>();
    private final FarmMenu farmMenu;
    private final File file, footprintFile;
    private final long price, step;
    private final int max;
    Lands(MaiTycoonPlugin plugin, ProfileStore store, Village village, long price, long step, int max, long reportWindow) throws IOException {
        this.plugin = plugin;
        this.store = store;
        this.village = village;
        this.price = price;
        this.step = step;
        this.max = max;
        this.footprints = new FootprintLog(reportWindow);
        this.farmMenu = new FarmMenu(plugin, this);
        this.file = new File(plugin.getDataFolder(), "lands.yml");
        this.footprintFile = new File(plugin.getDataFolder(), "footprints.yml");
        plugin.getServer().getPluginManager().registerEvents(farmMenu, plugin);
        loadLands();
        loadFootprints();
    }
    private YamlConfiguration read(File source) throws IOException {
        var yaml = new YamlConfiguration();
        try { yaml.load(source); }
        catch (InvalidConfigurationException e) { throw new IOException("Invalid " + source.getName(), e); }
        return yaml;
    }
    private void loadLands() throws IOException {
        if (!file.exists()) return;
        var yaml = read(file);
        boolean migrated = false;
        for (String entry : yaml.getStringList("lands")) {
            String[] parts = entry.split("=");
            if (parts.length < 2) throw new IOException("Invalid land entry " + entry);
            String key = worldIdKey(parts[0]);
            migrated |= !key.equals(parts[0]);
            registry.put(key, UUID.fromString(parts[1]), parts.length > 2 ? Long.parseLong(parts[2]) : 0);
        }
        var workers = yaml.getConfigurationSection("workers");
        if (workers != null) for (String owner : workers.getKeys(false)) {
            for (String worker : workers.getStringList(owner)) registry.addWorker(UUID.fromString(owner), UUID.fromString(worker));
        }
        if (migrated) save();
    }
    /** Land saved by older versions used the world name, which changes if a world is renamed. */
    private String worldIdKey(String key) {
        String[] parts = key.split(":");
        World world = plugin.getServer().getWorld(parts[0]);
        return world == null ? key : LandRegistry.key(world.getUID().toString(), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
    }
    private void save() throws IOException {
        var yaml = new YamlConfiguration();
        yaml.set("version", 2);
        yaml.set("lands", registry.parcels().entrySet().stream().map(e -> e.getKey() + "=" + e.getValue().owner() + "=" + e.getValue().createdAt()).sorted().toList());
        for (var parcel : registry.parcels().values()) {
            var workers = registry.workers(parcel.owner());
            if (!workers.isEmpty()) yaml.set("workers." + parcel.owner(), workers.stream().map(UUID::toString).toList());
        }
        yaml.save(file);
    }
    private void loadFootprints() throws IOException {
        if (!footprintFile.exists()) return;
        var yaml = read(footprintFile);
        for (String id : yaml.getKeys(false)) {
            var f = Objects.requireNonNull(yaml.getConfigurationSection(id));
            String reporter = f.getString("reporter");
            footprints.restore(new FootprintLog.Footprint(id, UUID.fromString(f.getString("actor", "")), UUID.fromString(f.getString("owner", "")), f.getString("world"),
                f.getInt("x"), f.getInt("y"), f.getInt("z"), f.getString("crop"), f.getString("cause"), f.getLong("created-at"), f.getLong("reported-at"),
                reporter == null ? null : UUID.fromString(reporter)));
        }
    }
    private void saveFootprints() throws IOException {
        var yaml = new YamlConfiguration();
        for (var f : footprints.all()) {
            yaml.set(f.id() + ".actor", f.actor().toString());
            yaml.set(f.id() + ".owner", f.owner().toString());
            yaml.set(f.id() + ".world", f.world());
            yaml.set(f.id() + ".x", f.x());
            yaml.set(f.id() + ".y", f.y());
            yaml.set(f.id() + ".z", f.z());
            yaml.set(f.id() + ".crop", f.crop());
            yaml.set(f.id() + ".cause", f.cause());
            yaml.set(f.id() + ".created-at", f.createdAt());
            yaml.set(f.id() + ".reported-at", f.reportedAt());
            if (f.reporter() != null) yaml.set(f.id() + ".reporter", f.reporter().toString());
        }
        yaml.save(footprintFile);
    }
    private static String key(World world, int chunkX, int chunkZ) { return LandRegistry.key(world.getUID().toString(), chunkX, chunkZ); }
    private static String key(Block block) { return key(block.getWorld(), block.getX() >> 4, block.getZ() >> 4); }
    private static String key(Location at) { return key(at.getWorld(), at.getBlockX() >> 4, at.getBlockZ() >> 4); }
    private UUID owner(Block block) { return registry.owner(key(block)); }
    private String ownerName(UUID id) {
        String name = plugin.getServer().getOfflinePlayer(id).getName();
        return name == null ? "알 수 없음" : name;
    }
    void open(Player player) {
        World world = village.world();
        if (world == null || !player.getWorld().equals(world)) { plugin.tell(player, "땅은 마을이 있는 월드에서만 살 수 있습니다."); return; }
        Chunk here = player.getLocation().getChunk();
        plugin.guarded(player, () -> show(new Holder(here.getX(), here.getZ(), here.getX(), here.getZ()), player));
    }
    /** A title cannot change on an open window, so a new selection opens the screen again. */
    private void show(Holder holder, Player player) throws IOException {
        Profile profile = store.load(player.getUniqueId());
        boolean drawn = plugin.packDrawn(player);
        holder.inventory = plugin.getServer().createInventory(holder, Ui.SIZE, drawn ? mapTitle(holder, player, profile) : Component.text("토지"));
        render(holder, player, profile);
        player.openInventory(holder.inventory);
    }
    private void showLater(Holder holder, Player player) {
        if (holder.reopening) return;
        holder.reopening = true;
        plugin.getServer().getScheduler().runTask(plugin, () -> plugin.guarded(player, () -> {
            holder.reopening = false;
            if (player.getOpenInventory().getTopInventory().getHolder() == holder) show(holder, player);
        }));
    }
    private Status status(Player player, int cx, int cz) {
        World world = player.getWorld();
        UUID owner = registry.owner(key(world, cx, cz));
        if (village.overlaps(world, cx, cz)) return Status.VILLAGE;
        if (player.getUniqueId().equals(owner)) return Status.MINE;
        return owner != null ? Status.TAKEN : Status.FREE;
    }
    private static String label(Status status) {
        return switch (status) {
            case VILLAGE -> "마을 구역";
            case MINE -> "내 땅";
            case TAKEN -> "타인 소유";
            case FREE -> "구매 가능";
        };
    }
    private static String money(long value) { return String.format("%,d", value); }
    private int limit(Player player) { return player.hasPermission("maitycoon.admin") ? Integer.MAX_VALUE : max; }
    private boolean permitted(Player player, Profile profile) { return profile.landPermit() || player.hasPermission("maitycoon.admin"); }
    private long nextPrice(Player player) { return LandRegistry.price(price, step, registry.count(player.getUniqueId())); }
    private Component mapTitle(Holder holder, Player player, Profile profile) {
        int owned = registry.count(player.getUniqueId());
        long next = nextPrice(player);
        Status status = status(player, holder.chosenX, holder.chosenZ);
        var title = new ShopTitle().screen(0xEA00).value(7, 144, owned + "/" + max);
        title.selected(holder.chosenX - holder.centerX + MenuLayout.MAP_COLUMNS / 2, holder.chosenZ - holder.centerZ + MenuLayout.MAP_ROWS / 2);
        title.value(32, 103, holder.chosenX + "," + holder.chosenZ).label(42, 103, label(status));
        if (status == Status.FREE) {
            title.value(62, 103, money(next));
            if (profile.coins() < next) title.label(82, 103, "골드 부족");
            else title.value(82, 103, money(profile.coins() - next));
            title.label(97, 130, permitted(player, profile) ? "보유" : "필요");
        }
        if (!purchasable(player, profile, holder.chosenX, holder.chosenZ)) title.dimmed(MenuLayout.slotX(MenuLayout.LAND_CONFIRM.get(0)) - 1, false);
        return title.build();
    }
    private boolean purchasable(Player player, Profile profile, int cx, int cz) {
        return status(player, cx, cz) == Status.FREE && permitted(player, profile) && registry.count(player.getUniqueId()) < limit(player) && profile.coins() >= nextPrice(player);
    }
    private void render(Holder holder, Player player, Profile profile) {
        Inventory inventory = holder.inventory;
        boolean drawn = plugin.packDrawn(player);
        plugin.frame(inventory, player);
        holder.chunks.clear();
        long next = nextPrice(player);
        for (int row = 0; row < MenuLayout.MAP_ROWS; row++) for (int column = 0; column < MenuLayout.MAP_COLUMNS; column++) {
            int cx = holder.centerX + column - MenuLayout.MAP_COLUMNS / 2, cz = holder.centerZ + row - MenuLayout.MAP_ROWS / 2;
            int slot = MenuLayout.mapSlot(column, row);
            holder.chunks.put(slot, new int[]{cx, cz});
            inventory.setItem(slot, cell(player, cx, cz, cx == holder.centerX && cz == holder.centerZ, cx == holder.chosenX && cz == holder.chosenZ, next));
        }
        List<Component> reasons = refusal(player, profile, holder.chosenX, holder.chosenZ);
        List<Component> summary = new ArrayList<>(List.of(Ui.line(label(status(player, holder.chosenX, holder.chosenZ)), NamedTextColor.YELLOW),
            Ui.line("가격 " + money(next) + " 골드", NamedTextColor.GOLD), Ui.line("잔액 " + money(profile.coins()) + " 골드", NamedTextColor.GRAY),
            Ui.line("이용권 " + (permitted(player, profile) ? "보유" : "필요"), NamedTextColor.GRAY), Ui.line("보유 땅 " + registry.count(player.getUniqueId()) + " / " + max, NamedTextColor.GRAY)));
        if (!drawn) inventory.setItem(MenuLayout.LAND_SUMMARY, Ui.icon(Material.PAPER, Ui.line("선택한 땅: 청크 " + holder.chosenX + ", " + holder.chosenZ, NamedTextColor.WHITE), summary));
        Ui.button(inventory, MenuLayout.LAND_BORDER, drawn, Ui.icon(Material.SPYGLASS, Ui.line("경계 확인", NamedTextColor.AQUA), List.of(Ui.line("선택한 땅의 경계를 잠시 표시합니다.", NamedTextColor.GRAY))));
        Ui.button(inventory, MenuLayout.LAND_HOME, drawn, Ui.icon(Material.COMPASS, Ui.line("내 위치", NamedTextColor.WHITE), List.of(Ui.line("서 있는 땅을 선택합니다.", NamedTextColor.GRAY))));
        Ui.button(inventory, MenuLayout.LAND_FARM, drawn, Ui.icon(Material.WRITABLE_BOOK, Ui.line("농장 관리", NamedTextColor.YELLOW), List.of(Ui.line("내 땅 목록, 작업자, 자동 심기, 발자국", NamedTextColor.GRAY))));
        Ui.button(inventory, MenuLayout.LAND_CONFIRM, drawn, Ui.icon(Material.LIME_CONCRETE, Ui.line("구매 내용 확인", NamedTextColor.GREEN), reasons.isEmpty() ? List.of(Ui.line("아직 결제되지 않습니다.", NamedTextColor.GRAY)) : reasons));
    }
    private List<Component> refusal(Player player, Profile profile, int cx, int cz) {
        Status status = status(player, cx, cz);
        if (status != Status.FREE) return List.of(Ui.line(label(status) + "라 살 수 없습니다.", NamedTextColor.RED));
        if (!permitted(player, profile)) return List.of(Ui.line("땅 구매 이용권이 필요합니다.", NamedTextColor.RED));
        if (registry.count(player.getUniqueId()) >= limit(player)) return List.of(Ui.line("땅은 최대 " + max + "개까지 살 수 있습니다.", NamedTextColor.RED));
        if (profile.coins() < nextPrice(player)) return List.of(Ui.line("골드가 부족합니다.", NamedTextColor.RED));
        return List.of();
    }
    private ItemStack cell(Player player, int cx, int cz, boolean here, boolean chosen, long next) {
        Status status = status(player, cx, cz);
        UUID owner = registry.owner(key(player.getWorld(), cx, cz));
        Material type = switch (status) {
            case VILLAGE -> Material.YELLOW_CONCRETE;
            case MINE -> Material.EMERALD_BLOCK;
            case TAKEN -> Material.RED_CONCRETE;
            case FREE -> Material.LIGHT_GRAY_CONCRETE;
        };
        Component name = switch (status) {
            case VILLAGE -> Ui.line("마을 구역", NamedTextColor.YELLOW);
            case MINE -> Ui.line("내 땅", NamedTextColor.GREEN);
            case TAKEN -> Ui.line(ownerName(owner) + "의 땅", NamedTextColor.RED);
            case FREE -> Ui.line("빈 땅", NamedTextColor.WHITE);
        };
        List<Component> lore = new ArrayList<>(List.of(Ui.line("청크 " + cx + ", " + cz + (here ? " · 현재 위치" : ""), NamedTextColor.GRAY)));
        if (status == Status.FREE) lore.add(Ui.line(money(next) + " 골드", NamedTextColor.GOLD));
        lore.add(Ui.line(chosen ? "선택됨" : "누르면 선택합니다. 바로 결제되지 않습니다.", chosen ? NamedTextColor.YELLOW : NamedTextColor.DARK_GRAY));
        ItemStack icon = Ui.icon(here ? Material.COMPASS : type, name, lore);
        return chosen ? Ui.glow(icon) : icon;
    }
    /** True once the land is paid for. A changed price is not paid: the caller shows the new total first. */
    private boolean buy(Player player, int cx, int cz, long expected) throws IOException {
        if (!plugin.survival(player)) return false;
        if (plugin.tradeHeld(player)) { plugin.tell(player, "거래를 완료하지 못했습니다. 아이템과 잔액을 확인해 주세요."); return false; }
        World world = player.getWorld();
        var id = player.getUniqueId();
        Profile profile = store.load(id);
        boolean admin = player.hasPermission("maitycoon.admin");
        if (!profile.landPermit() && !admin) { plugin.tell(player, "땅 구매 이용권이 필요합니다. 수집 도감을 채워 얻으세요."); return false; }
        if (village.overlaps(world, cx, cz)) { plugin.tell(player, "마을 구역은 살 수 없습니다."); return false; }
        String key = key(world, cx, cz);
        switch (registry.check(key, id, admin ? Integer.MAX_VALUE : max)) {
            case TAKEN -> { plugin.tell(player, "이미 주인이 있는 땅입니다."); return false; }
            case LIMIT -> { plugin.tell(player, "땅은 최대 " + max + "개까지 살 수 있습니다."); return false; }
            case OK -> {}
        }
        long cost = LandRegistry.price(price, step, registry.count(id));
        if (cost != expected) { plugin.tell(player, "땅값이 바뀌었습니다. 금액을 다시 확인해 주세요."); return false; }
        if (profile.coins() < cost) { plugin.tell(player, "골드가 부족합니다. 필요 " + money(cost) + " / 보유 " + money(profile.coins())); return false; }
        registry.put(key, id);
        try {
            plugin.transact(player, "land", key + " -" + cost, () -> {
                save();
                store.save(id, profile.spend(cost));
            });
        } catch (IOException e) {
            registry.remove(key);
            try { save(); } catch (IOException rollback) { plugin.getLogger().log(Level.SEVERE, "Cannot roll back land " + key, rollback); }
            throw e;
        }
        plugin.tell(player, "땅을 구매했습니다! 청크 " + cx + ", " + cz + " (-" + money(cost) + " 골드)");
        return true;
    }
    /** The banner text for where the player stands, or null when there is nothing to buy here. */
    String bannerText(Player player) {
        World world = village.world();
        Location at = player.getLocation();
        if (world == null || !world.equals(at.getWorld())) return null;
        int cx = LandRegistry.chunkOf(at.getBlockX()), cz = LandRegistry.chunkOf(at.getBlockZ());
        if (village.overlaps(world, cx, cz) || registry.owner(key(world, cx, cz)) != null) return null;
        int owned = registry.count(player.getUniqueId());
        if (owned >= max) return null;
        try {
            if (!store.load(player.getUniqueId()).landPermit()) return "구매 가능한 땅 · 땅 구매 이용권 필요";
        } catch (IOException e) { return null; }
        return String.format(MaiTycoonPlugin.BUY_PROMPT + " 이 땅 구매 · %,d 골드", LandRegistry.price(price, step, owned));
    }
    void refreshBanners() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            UUID id = player.getUniqueId();
            String text = bannerText(player);
            if (Objects.equals(text, shown.get(id))) continue;
            var old = banners.remove(id);
            if (old != null) player.hideBossBar(old);
            shown.remove(id);
            if (text == null) continue;
            var banner = net.kyori.adventure.bossbar.BossBar.bossBar(plugin.bannerTitle(player, text), 1f, net.kyori.adventure.bossbar.BossBar.Color.WHITE, net.kyori.adventure.bossbar.BossBar.Overlay.PROGRESS);
            player.showBossBar(banner);
            banners.put(id, banner);
            shown.put(id, text);
            offerLink(player, text);
        }
    }
    /** The clickable alternative to the hotkey; rate limited so walking across land does not fill the chat. */
    private void offerLink(Player player, String text) {
        long now = System.currentTimeMillis();
        if (!text.startsWith("[") || now - linkSent.getOrDefault(player.getUniqueId(), 0L) < 60_000) return;
        linkSent.put(player.getUniqueId(), now);
        player.sendMessage(Component.text("[이 땅 구매하기]", NamedTextColor.GREEN).clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand("/tycoon land buy")));
    }
    void openBuy(Player player) {
        World world = village.world();
        Location at = player.getLocation();
        if (world == null || !world.equals(at.getWorld())) { plugin.tell(player, "땅은 마을이 있는 월드에서만 살 수 있습니다."); return; }
        int cx = LandRegistry.chunkOf(at.getBlockX()), cz = LandRegistry.chunkOf(at.getBlockZ());
        if (village.overlaps(world, cx, cz)) { plugin.tell(player, "마을 구역은 살 수 없습니다."); return; }
        plugin.guarded(player, () -> showBuy(player, cx, cz, false));
    }
    private void showBuy(Player player, int cx, int cz, boolean bought) throws IOException {
        BuyHolder holder = new BuyHolder(cx, cz, nextPrice(player), bought);
        Profile profile = store.load(player.getUniqueId());
        int owned = registry.count(player.getUniqueId()), x = LandRegistry.firstBlock(cx), z = LandRegistry.firstBlock(cz), size = LandRegistry.CHUNK_SIZE;
        long after = profile.coins() - holder.cost;
        String blocksX = x + "~" + (x + size - 1), blocksZ = z + "~" + (z + size - 1);
        boolean drawn = plugin.packDrawn(player);
        String yes = bought ? "경계 보기" : "구매 확정", no = bought ? "농장 관리" : "돌아가기";
        Component title = Component.text(bought ? "구매 완료" : "땅 구매 확인");
        if (drawn) {
            var card = new ShopTitle().screen(0xEB00).value(22, 72, cx + "," + cz).value(32, 72, blocksX).value(42, 72, blocksZ);
            if (bought) card.label(52, 72, "구매 완료").value(62, 72, money(profile.coins())).value(82, 72, owned + "/" + max);
            else {
                card.value(52, 72, money(holder.cost)).value(62, 72, money(profile.coins())).value(82, 72, (owned + 1) + "/" + max);
                if (after < 0) card.label(72, 72, "골드 부족");
                else card.value(72, 72, money(after));
            }
            card.label(92, 72, permitted(player, profile) ? "소모 없음" : "필요").onBlue(true).label(112, 34, yes).onBlue(false).label(112, 124, no);
            title = card.build();
        }
        holder.inventory = plugin.getServer().createInventory(holder, Ui.SIZE, title);
        plugin.frame(holder.inventory, player);
        if (!drawn) holder.inventory.setItem(MenuLayout.BUY_SUMMARY, Ui.icon(Material.GRASS_BLOCK, Ui.line("청크 " + cx + ", " + cz, NamedTextColor.YELLOW), List.of(
            Ui.line("범위 X " + x + " ~ " + (x + size - 1) + ", Z " + z + " ~ " + (z + size - 1) + " (" + size + "×" + size + ")", NamedTextColor.GRAY),
            Ui.line("가격 " + money(holder.cost) + " 골드", NamedTextColor.GOLD),
            Ui.line("현재 잔액 " + money(profile.coins()) + " 골드", NamedTextColor.GRAY),
            Ui.line(after < 0 ? "골드가 부족합니다." : "구매 후 잔액 " + money(after) + " 골드", after < 0 ? NamedTextColor.RED : NamedTextColor.GRAY),
            Ui.line("구매 후 보유 " + (owned + 1) + " / " + max, NamedTextColor.GRAY),
            Ui.line("이용권 " + (permitted(player, profile) ? "보유 · 소모 없음" : "필요"), NamedTextColor.GRAY))));
        Ui.button(holder.inventory, MenuLayout.BUY_YES, drawn, Ui.icon(Material.LIME_CONCRETE, Ui.line(yes, NamedTextColor.GREEN), List.of()));
        Ui.button(holder.inventory, MenuLayout.BUY_NO, drawn, Ui.icon(bought ? Material.WRITABLE_BOOK : Material.ARROW, Ui.line(no, NamedTextColor.WHITE), List.of()));
        player.openInventory(holder.inventory);
    }
    @EventHandler(ignoreCancelled = true)
    public void onHotkey(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        // Sneaking keeps the vanilla hand swap available for anyone who needs it.
        if (player.isSneaking()) return;
        event.setCancelled(true);
        hotkey(player);
    }
    private void hotkey(Player player) {
        String text = bannerText(player);
        boolean buy = text != null && text.startsWith("[");
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (buy) openBuy(player);
            else plugin.openDex(player);
        });
    }
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        banners.remove(id);
        shown.remove(id);
    }
    private void showBorder(Player player, int chunkX, int chunkZ) {
        int x = chunkX << 4, z = chunkZ << 4;
        plugin.tell(player, "청크 경계를 표시합니다.");
        int[] left = {8};
        plugin.getServer().getScheduler().runTaskTimer(plugin, task -> {
            if (!player.isOnline() || left[0]-- <= 0) { task.cancel(); return; }
            double y = player.getY() + 1;
            for (int i = 0; i <= 16; i += 2) for (double[] at : new double[][]{{x + i, z}, {x + i, z + 16}, {x, z + i}, {x + 16, z + i}}) {
                player.spawnParticle(Particle.HAPPY_VILLAGER, at[0], y, at[1], 1);
            }
        }, 0L, 10L);
    }
    private void later(Player player, MaiTycoonPlugin.Action action) {
        plugin.getServer().getScheduler().runTask(plugin, () -> plugin.guarded(player, action));
    }
    private void buyClick(InventoryClickEvent event, BuyHolder holder) {
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getClick() != ClickType.LEFT) return;
        int slot = event.getRawSlot();
        if (holder.bought) {
            if (MenuLayout.BUY_YES.contains(slot)) later(player, () -> { player.closeInventory(); showBorder(player, holder.chunkX, holder.chunkZ); });
            else if (MenuLayout.BUY_NO.contains(slot)) later(player, () -> farmMenu.open(player));
            return;
        }
        if (MenuLayout.BUY_NO.contains(slot)) { later(player, () -> open(player)); return; }
        if (!MenuLayout.BUY_YES.contains(slot) || holder.done) return;
        holder.done = true;
        plugin.guarded(player, () -> {
            boolean paid = buy(player, holder.chunkX, holder.chunkZ, holder.cost);
            later(player, () -> {
                if (paid || nextPrice(player) != holder.cost) showBuy(player, holder.chunkX, holder.chunkZ, paid);
                else open(player);
            });
        });
    }
    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof BuyHolder buying) { buyClick(event, buying); return; }
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || (event.getClick() != ClickType.LEFT && event.getClick() != ClickType.RIGHT)) return;
        int raw = event.getRawSlot();
        if (MenuLayout.LAND_BORDER.contains(raw)) { later(player, () -> { player.closeInventory(); showBorder(player, holder.chosenX, holder.chosenZ); }); return; }
        if (MenuLayout.LAND_FARM.contains(raw)) { later(player, () -> farmMenu.open(player)); return; }
        if (MenuLayout.LAND_CONFIRM.contains(raw)) {
            plugin.guarded(player, () -> {
                List<Component> reasons = refusal(player, store.load(player.getUniqueId()), holder.chosenX, holder.chosenZ);
                if (reasons.isEmpty()) later(player, () -> showBuy(player, holder.chosenX, holder.chosenZ, false));
                else player.sendMessage(reasons.get(0));
            });
            return;
        }
        int[] chunk = MenuLayout.LAND_HOME.contains(raw) ? new int[]{holder.centerX, holder.centerZ} : holder.chunks.get(raw);
        if (chunk == null) return;
        holder.chosenX = chunk[0];
        holder.chosenZ = chunk[1];
        plugin.guarded(player, () -> { render(holder, player, store.load(player.getUniqueId())); showLater(holder, player); });
    }
    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        Object top = event.getView().getTopInventory().getHolder();
        if (top instanceof Holder || top instanceof BuyHolder) event.setCancelled(true);
    }
    List<String> ownedKeys(UUID owner) { return registry.parcels().entrySet().stream().filter(entry -> entry.getValue().owner().equals(owner)).map(Map.Entry::getKey).toList(); }
    /** Neither bought land nor anywhere the village keeps for itself. */
    boolean wild(Block block) { return owner(block) == null && !village.overlaps(block.getWorld(), block.getX() >> 4, block.getZ() >> 4); }
    /** Keys are world:x:z, so the coordinates can be read without loading the chunk. */
    List<String> parcelNames(Player player) {
        return ownedKeys(player.getUniqueId()).stream().map(key -> key.split(":")).map(parts -> "청크 " + parts[1] + ", " + parts[2]).sorted().toList();
    }
    boolean owned(org.bukkit.Chunk chunk) { return registry.owner(key(chunk.getWorld(), chunk.getX(), chunk.getZ())) != null; }
    void openFarm(Player player) { farmMenu.open(player); }
    List<FootprintLog.Footprint> reportable(Player player) { return footprints.reportable(player.getUniqueId(), System.currentTimeMillis()); }
    List<String> workerNames(Player player) { return registry.workers(player.getUniqueId()).stream().map(this::ownerName).sorted().toList(); }
    void report(Player player, String id) throws IOException {
        var footprint = footprints.get(id);
        UUID current = footprint == null ? null : registry.owner(LandRegistry.key(footprint.world(), footprint.x() >> 4, footprint.z() >> 4));
        var result = footprints.report(id, player.getUniqueId(), current, System.currentTimeMillis());
        switch (result) {
            case OK -> {
                saveFootprints();
                plugin.tell(player, ownerName(footprint.actor()) + "의 발자국을 신고했습니다.");
                Player actor = plugin.getServer().getPlayer(footprint.actor());
                if (actor != null) plugin.tell(actor, "당신의 발자국이 신고되었습니다.");
            }
            case ALREADY -> plugin.tell(player, "이미 신고한 발자국입니다.");
            case EXPIRED -> plugin.tell(player, "신고 기간이 지난 발자국입니다.");
            case MISSING, NOT_OWNER, OWN_ACT -> plugin.tell(player, "신고할 수 없는 발자국입니다.");
        }
    }
    void memberCommand(Player player, String[] args) throws IOException {
        UUID owner = player.getUniqueId();
        if (args.length == 3 && args[1].equalsIgnoreCase("member") && !args[2].equalsIgnoreCase("list")) { plugin.tell(player, "/tycoon land member add|remove <이름>"); return; }
        if (args.length < 4) { plugin.tell(player, "작업자: " + String.join(", ", workerNames(player))); return; }
        var target = plugin.getServer().getOfflinePlayerIfCached(args[3]);
        if (target == null) { plugin.tell(player, "서버에 접속한 적이 있는 플레이어만 지정할 수 있습니다."); return; }
        if (target.getUniqueId().equals(owner)) { plugin.tell(player, "자신은 작업자로 지정할 수 없습니다."); return; }
        boolean add = args[2].equalsIgnoreCase("add");
        boolean changed = add ? registry.addWorker(owner, target.getUniqueId()) : registry.removeWorker(owner, target.getUniqueId());
        if (changed) save();
        plugin.tell(player, !changed ? "변경된 내용이 없습니다." : args[3] + (add ? "을(를) 작업자로 추가했습니다." : "을(를) 작업자에서 제외했습니다."));
    }
    /** Own or worker land, or unclaimed ground. Never someone else's land, even for admins. */
    boolean mayReplant(Player player, Block block) {
        var role = registry.role(key(block), player.getUniqueId());
        return role != LandRegistry.Role.VISITOR || player.hasPermission("maitycoon.admin");
    }
    /** Claimed land the player works on. Tractors never run on open ground or anyone else's land. */
    boolean mayTractor(Player player, Block block) {
        var role = registry.role(key(block), player.getUniqueId());
        return role == LandRegistry.Role.OWNER || role == LandRegistry.Role.WORKER || (role != null && player.hasPermission("maitycoon.admin"));
    }
    boolean allowsPlanting(Player player, Block block) { return !refused(player, block, LandRegistry.Action.PLANT); }
    private boolean refused(Player player, Block block, LandRegistry.Action action) {
        if (registry.allows(key(block), player.getUniqueId(), action) || player.hasPermission("maitycoon.admin")) return false;
        player.sendActionBar(Component.text(ownerName(owner(block)) + "의 땅은 보호되어 있습니다.", NamedTextColor.RED));
        return true;
    }
    private static final Set<Material> STAGED_CROPS = EnumSet.of(Material.WHEAT, Material.CARROTS, Material.POTATOES, Material.BEETROOTS);
    /** Only these four have a reliable growth stage; age on cane or cactus means something else. */
    static boolean immature(Block block) {
        return STAGED_CROPS.contains(block.getType()) && block.getBlockData() instanceof org.bukkit.block.data.Ageable crop && crop.getAge() < crop.getMaximumAge();
    }
    private static boolean crop(Material type) { return Village.FARMABLE.contains(type) && type != Material.FARMLAND && type != Material.SHORT_GRASS; }
    private void footprint(Player player, Block block, String cause) {
        if (!crop(block.getType()) || !registry.isTheft(key(block), player.getUniqueId()) || player.hasPermission("maitycoon.admin")) return;
        var left = footprints.add(player.getUniqueId(), owner(block), block.getWorld().getUID().toString(), block.getX(), block.getY(), block.getZ(),
            block.getType().name(), immature(block) ? cause + "-immature" : cause, System.currentTimeMillis(), plugin.getServer().getCurrentTick());
        if (left == null) return;
        try { saveFootprints(); }
        catch (IOException e) { plugin.getLogger().log(Level.SEVERE, "Cannot save footprints", e); }
        plugin.tell(player, "다른 사람의 농작물을 수확했습니다. 발자국이 남았습니다.");
    }
    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Material type = event.getBlock().getType();
        var action = crop(type) ? LandRegistry.Action.HARVEST : type == Material.FARMLAND ? LandRegistry.Action.TILL : LandRegistry.Action.BUILD;
        if (refused(event.getPlayer(), event.getBlock(), action)) event.setCancelled(true);
    }
    /** Runs last, so a break cancelled by any protection leaves no footprint. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBroken(BlockBreakEvent event) { footprint(event.getPlayer(), event.getBlock(), "break"); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPicked(PlayerHarvestBlockEvent event) { footprint(event.getPlayer(), event.getHarvestedBlock(), "right-click"); }
    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        var action = crop(event.getBlock().getType()) ? LandRegistry.Action.PLANT : LandRegistry.Action.BUILD;
        if (refused(event.getPlayer(), event.getBlock(), action)) event.setCancelled(true);
    }
    @EventHandler(ignoreCancelled = true)
    public void onBucket(PlayerBucketEmptyEvent event) { if (refused(event.getPlayer(), event.getBlock(), LandRegistry.Action.BUILD)) event.setCancelled(true); }
    @EventHandler(ignoreCancelled = true)
    public void onBucket(PlayerBucketFillEvent event) { if (refused(event.getPlayer(), event.getBlock(), LandRegistry.Action.BUILD)) event.setCancelled(true); }
    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (block == null) return;
        boolean trample = event.getAction() == org.bukkit.event.block.Action.PHYSICAL && block.getType() == Material.FARMLAND;
        boolean rightClick = event.getAction() == org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK;
        LandRegistry.Action action = null;
        if (trample) action = LandRegistry.Action.TILL;
        else if (rightClick && tool(event.getItem())) action = event.getItem().getType().name().endsWith("_HOE") ? LandRegistry.Action.TILL : LandRegistry.Action.BUILD;
        // Picking berries is a harvest, recorded by onPicked, not the use of a container.
        else if (rightClick && block.getType().isInteractable() && !crop(block.getType())) action = LandRegistry.Action.USE;
        if (action != null && refused(event.getPlayer(), block, action)) event.setCancelled(true);
    }
    private static boolean tool(ItemStack item) {
        if (item == null) return false;
        String name = item.getType().name();
        return name.endsWith("_HOE") || name.endsWith("_SHOVEL") || name.endsWith("_AXE");
    }
    @EventHandler(ignoreCancelled = true)
    public void onEntityUse(PlayerInteractEntityEvent event) {
        Entity entity = event.getRightClicked();
        if ((entity instanceof Hanging || entity instanceof ArmorStand) && refused(event.getPlayer(), entity.getLocation().getBlock(), LandRegistry.Action.USE)) event.setCancelled(true);
    }
    @EventHandler(ignoreCancelled = true)
    public void onEntityUse(PlayerInteractAtEntityEvent event) {
        if (event.getRightClicked() instanceof ArmorStand && refused(event.getPlayer(), event.getRightClicked().getLocation().getBlock(), LandRegistry.Action.USE)) event.setCancelled(true);
    }
    @EventHandler(ignoreCancelled = true)
    public void onEntityHit(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Hanging || event.getEntity() instanceof ArmorStand)) return;
        Block at = event.getEntity().getLocation().getBlock();
        if (owner(at) == null) return;
        Entity damager = event.getDamager();
        if (damager instanceof org.bukkit.entity.Projectile projectile && projectile.getShooter() instanceof Player shooter) damager = shooter;
        if (!(damager instanceof Player player) || refused(player, at, LandRegistry.Action.BUILD)) event.setCancelled(true);
    }
    @EventHandler(ignoreCancelled = true)
    public void onHanging(HangingBreakByEntityEvent event) {
        Block at = event.getEntity().getLocation().getBlock();
        if (owner(at) == null) return;
        if (!(event.getRemover() instanceof Player player) || refused(player, at, LandRegistry.Action.BUILD)) event.setCancelled(true);
    }
    private boolean crosses(Block from, Block to) { return registry.crossesInto(key(from), key(to)); }
    private boolean movesAcross(Block piston, List<Block> moved, BlockFace direction) {
        for (Block block : moved) {
            if (crosses(piston, block) || crosses(piston, block.getRelative(direction))) return true;
        }
        return crosses(piston, piston.getRelative(direction));
    }
    @EventHandler(ignoreCancelled = true)
    public void onPiston(BlockPistonExtendEvent event) { if (movesAcross(event.getBlock(), event.getBlocks(), event.getDirection())) event.setCancelled(true); }
    @EventHandler(ignoreCancelled = true)
    public void onPiston(BlockPistonRetractEvent event) { if (movesAcross(event.getBlock(), event.getBlocks(), event.getDirection())) event.setCancelled(true); }
    @EventHandler(ignoreCancelled = true)
    public void onFlow(BlockFromToEvent event) { if (crosses(event.getBlock(), event.getToBlock())) event.setCancelled(true); }
    @EventHandler(ignoreCancelled = true)
    public void onDispense(BlockDispenseEvent event) {
        if (event.getBlock().getBlockData() instanceof Directional facing && crosses(event.getBlock(), event.getBlock().getRelative(facing.getFacing()))) event.setCancelled(true);
    }
    @EventHandler(ignoreCancelled = true)
    public void onHopper(InventoryMoveItemEvent event) {
        Location source = event.getSource().getLocation(), target = event.getDestination().getLocation();
        if (source == null || target == null) return;
        String from = key(source), to = key(target);
        if (registry.crossesInto(from, to) || registry.crossesInto(to, from)) event.setCancelled(true);
    }
    @EventHandler(ignoreCancelled = true)
    public void onGrow(StructureGrowEvent event) {
        event.getBlocks().removeIf(state -> registry.crossesInto(key(event.getLocation()), key(state.getLocation())));
    }
    @EventHandler(ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent event) { event.blockList().removeIf(block -> owner(block) != null); }
    @EventHandler(ignoreCancelled = true)
    public void onExplode(BlockExplodeEvent event) { event.blockList().removeIf(block -> owner(block) != null); }
    @EventHandler(ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) { if (owner(event.getBlock()) != null) event.setCancelled(true); }
    @EventHandler(ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent event) {
        if (owner(event.getBlock()) == null) return;
        if (event.getPlayer() == null || refused(event.getPlayer(), event.getBlock(), LandRegistry.Action.BUILD)) event.setCancelled(true);
    }
    @EventHandler(ignoreCancelled = true)
    public void onEntityChange(EntityChangeBlockEvent event) {
        if (!(event.getEntity() instanceof Player) && !(event.getEntity() instanceof org.bukkit.entity.FallingBlock) && owner(event.getBlock()) != null) event.setCancelled(true);
    }
    @EventHandler(ignoreCancelled = true)
    public void onEntityTrample(EntityInteractEvent event) {
        if (event.getBlock().getType() == Material.FARMLAND && owner(event.getBlock()) != null) event.setCancelled(true);
    }
    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location from = event.getFrom(), to = event.getTo();
        if (from.getBlockX() >> 4 == to.getBlockX() >> 4 && from.getBlockZ() >> 4 == to.getBlockZ() >> 4) return;
        UUID before = registry.owner(key(from)), after = registry.owner(key(to));
        if (Objects.equals(before, after)) return;
        event.getPlayer().sendActionBar(after == null ? Component.text("주인 없는 땅", NamedTextColor.GRAY) : Component.text(ownerName(after) + "의 땅", NamedTextColor.GOLD));
    }
}
