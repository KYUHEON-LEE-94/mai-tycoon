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
    private static final class Holder implements InventoryHolder {
        private final Map<Integer, int[]> chunks = new HashMap<>();
        private Inventory inventory;
        @Override public Inventory getInventory() { return inventory; }
    }
    private static final int COLUMNS = 9, ROWS = 5, INFO = 45, MINE = 47, PERMIT = 49, FARM = 51, SHOW = 53;
    private final MaiTycoonPlugin plugin;
    private final ProfileStore store;
    private final Village village;
    private final LandRegistry registry = new LandRegistry();
    private final FootprintLog footprints;
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
        Holder holder = new Holder();
        holder.inventory = plugin.getServer().createInventory(holder, Ui.SIZE, Component.text("땅 지도"));
        plugin.guarded(player, () -> render(holder, player));
        player.openInventory(holder.inventory);
    }
    private void render(Holder holder, Player player) throws IOException {
        Inventory inventory = holder.inventory;
        Ui.frame(inventory);
        holder.chunks.clear();
        Profile profile = store.load(player.getUniqueId());
        int owned = registry.count(player.getUniqueId());
        long next = LandRegistry.price(price, step, owned);
        Chunk here = player.getLocation().getChunk();
        for (int row = 0; row < ROWS; row++) for (int column = 0; column < COLUMNS; column++) {
            int cx = here.getX() + column - COLUMNS / 2, cz = here.getZ() + row - ROWS / 2;
            int slot = row * COLUMNS + column;
            holder.chunks.put(slot, new int[]{cx, cz});
            inventory.setItem(slot, cell(player, cx, cz, cx == here.getX() && cz == here.getZ(), next));
        }
        inventory.setItem(INFO, Ui.icon(Material.GOLD_INGOT, Ui.line(profile.coins() + " YD", NamedTextColor.GOLD), List.of(Ui.line("보유 금액", NamedTextColor.GRAY))));
        inventory.setItem(MINE, Ui.icon(Material.EMERALD_BLOCK, Ui.line("내 땅 " + owned + " / " + max, NamedTextColor.GREEN), List.of(Ui.line("다음 땅 가격 " + next + " YD", NamedTextColor.GRAY))));
        inventory.setItem(PERMIT, Ui.icon(profile.landPermit() ? Material.PAPER : Material.BARRIER,
            Ui.line(profile.landPermit() ? "땅 구매 이용권 보유" : "땅 구매 이용권 없음", profile.landPermit() ? NamedTextColor.GREEN : NamedTextColor.RED),
            List.of(Ui.line("수집 도감 보상으로 얻습니다.", NamedTextColor.GRAY))));
        inventory.setItem(FARM, Ui.icon(Material.WRITABLE_BOOK, Ui.line("농장 관리", NamedTextColor.YELLOW), List.of(
            Ui.line("신고할 수 있는 발자국 " + reportable(player).size() + "건", NamedTextColor.GRAY), Ui.line("작업자 " + registry.workers(player.getUniqueId()).size() + "명", NamedTextColor.GRAY))));
        inventory.setItem(SHOW, Ui.icon(Material.SPYGLASS, Ui.line("청크 경계 보기", NamedTextColor.AQUA), List.of(Ui.line("지금 서 있는 청크의 경계를 잠시 표시합니다.", NamedTextColor.GRAY))));
    }
    private ItemStack cell(Player player, int cx, int cz, boolean here, long next) {
        World world = player.getWorld();
        UUID owner = registry.owner(key(world, cx, cz));
        String position = "청크 " + cx + ", " + cz + (here ? " (현재 위치)" : "");
        Material type;
        Component name;
        List<Component> lore = new ArrayList<>(List.of(Ui.line(position, NamedTextColor.GRAY)));
        if (village.overlaps(world, cx, cz)) { type = Material.YELLOW_CONCRETE; name = Ui.line("마을 구역", NamedTextColor.YELLOW); }
        else if (player.getUniqueId().equals(owner)) { type = Material.EMERALD_BLOCK; name = Ui.line("내 땅", NamedTextColor.GREEN); }
        else if (owner != null) { type = Material.RED_CONCRETE; name = Ui.line(ownerName(owner) + "의 땅", NamedTextColor.RED); }
        else {
            type = Material.LIME_STAINED_GLASS;
            name = Ui.line("빈 땅", NamedTextColor.WHITE);
            lore.add(Ui.line("우클릭으로 " + next + " YD에 구매", NamedTextColor.GREEN));
        }
        if (here) type = type == Material.LIME_STAINED_GLASS ? Material.COMPASS : type;
        ItemStack icon = Ui.icon(type, name, lore);
        return here ? Ui.glow(icon) : icon;
    }
    private void buy(Player player, int cx, int cz) throws IOException {
        if (!plugin.survival(player)) return;
        if (plugin.tradeHeld(player)) { plugin.tell(player, "거래를 완료하지 못했습니다. 아이템과 잔액을 확인해 주세요."); return; }
        World world = player.getWorld();
        var id = player.getUniqueId();
        Profile profile = store.load(id);
        if (!profile.landPermit()) { plugin.tell(player, "땅 구매 이용권이 필요합니다. 수집 도감을 채워 얻으세요."); return; }
        if (village.overlaps(world, cx, cz)) { plugin.tell(player, "마을 구역은 살 수 없습니다."); return; }
        String key = key(world, cx, cz);
        switch (registry.check(key, id, max)) {
            case TAKEN -> { plugin.tell(player, "이미 주인이 있는 땅입니다."); return; }
            case LIMIT -> { plugin.tell(player, "땅은 최대 " + max + "개까지 살 수 있습니다."); return; }
            case OK -> {}
        }
        long cost = LandRegistry.price(price, step, registry.count(id));
        if (profile.coins() < cost) { plugin.tell(player, "YD가 부족합니다. 필요 " + cost + " / 보유 " + profile.coins()); return; }
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
        plugin.tell(player, "땅을 구매했습니다! 청크 " + cx + ", " + cz + " (-" + cost + " YD)");
    }
    private void showBorder(Player player) {
        Chunk chunk = player.getLocation().getChunk();
        int x = chunk.getX() << 4, z = chunk.getZ() << 4;
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
    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        int raw = event.getRawSlot();
        if (raw == SHOW) { player.closeInventory(); showBorder(player); return; }
        if (raw == FARM) { plugin.getServer().getScheduler().runTask(plugin, () -> farmMenu.open(player)); return; }
        int[] chunk = holder.chunks.get(raw);
        if (chunk == null || event.getClick() != ClickType.RIGHT) return;
        plugin.guarded(player, () -> { buy(player, chunk[0], chunk[1]); render(holder, player); });
    }
    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Holder) event.setCancelled(true);
    }
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
