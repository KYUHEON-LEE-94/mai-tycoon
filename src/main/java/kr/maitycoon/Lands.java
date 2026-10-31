package kr.maitycoon;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;

/** Chunk land: purchase map and owner protection. Inside a claim everything except crops is protected. */
final class Lands implements Listener {
    private static final class Holder implements InventoryHolder {
        private final Map<Integer, int[]> chunks = new HashMap<>();
        private Inventory inventory;
        @Override public Inventory getInventory() { return inventory; }
    }
    private static final int COLUMNS = 9, ROWS = 5, INFO = 45, MINE = 47, PERMIT = 49, SHOW = 53;
    private final MaiTycoonPlugin plugin;
    private final ProfileStore store;
    private final Village village;
    private final LandRegistry registry = new LandRegistry();
    private final File file;
    private final long price, step;
    private final int max;
    Lands(MaiTycoonPlugin plugin, ProfileStore store, Village village, long price, long step, int max) throws IOException {
        this.plugin = plugin;
        this.store = store;
        this.village = village;
        this.price = price;
        this.step = step;
        this.max = max;
        this.file = new File(plugin.getDataFolder(), "lands.yml");
        if (!file.exists()) return;
        var yaml = new YamlConfiguration();
        try { yaml.load(file); }
        catch (InvalidConfigurationException e) { throw new IOException("Invalid lands.yml", e); }
        for (String entry : yaml.getStringList("lands")) {
            int split = entry.lastIndexOf('=');
            if (split < 0) throw new IOException("Invalid land entry " + entry);
            registry.put(entry.substring(0, split), UUID.fromString(entry.substring(split + 1)));
        }
    }
    private void save() throws IOException {
        var yaml = new YamlConfiguration();
        yaml.set("lands", registry.owners().entrySet().stream().map(e -> e.getKey() + "=" + e.getValue()).sorted().toList());
        yaml.save(file);
    }
    private static String key(Chunk chunk) { return LandRegistry.key(chunk.getWorld().getName(), chunk.getX(), chunk.getZ()); }
    private UUID owner(Block block) { return registry.owner(key(block.getChunk())); }
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
        inventory.setItem(SHOW, Ui.icon(Material.SPYGLASS, Ui.line("청크 경계 보기", NamedTextColor.AQUA), List.of(Ui.line("지금 서 있는 청크의 경계를 잠시 표시합니다.", NamedTextColor.GRAY))));
    }
    private ItemStack cell(Player player, int cx, int cz, boolean here, long next) {
        World world = player.getWorld();
        UUID owner = registry.owner(LandRegistry.key(world.getName(), cx, cz));
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
        String key = LandRegistry.key(world.getName(), cx, cz);
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
        int[] chunk = holder.chunks.get(raw);
        if (chunk == null || event.getClick() != ClickType.RIGHT) return;
        plugin.guarded(player, () -> { buy(player, chunk[0], chunk[1]); render(holder, player); });
    }
    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Holder) event.setCancelled(true);
    }
    private boolean refused(Player player, Block block) {
        UUID owner = owner(block);
        if (owner == null || owner.equals(player.getUniqueId()) || player.hasPermission("maitycoon.admin")) return false;
        player.sendActionBar(Component.text(ownerName(owner) + "의 땅은 보호되어 있습니다.", NamedTextColor.RED));
        return true;
    }
    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Material type = event.getBlock().getType();
        if (Village.FARMABLE.contains(type) && type != Material.FARMLAND) return;
        if (refused(event.getPlayer(), event.getBlock())) event.setCancelled(true);
    }
    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) { if (refused(event.getPlayer(), event.getBlock())) event.setCancelled(true); }
    @EventHandler(ignoreCancelled = true)
    public void onBucket(PlayerBucketEmptyEvent event) { if (refused(event.getPlayer(), event.getBlock())) event.setCancelled(true); }
    @EventHandler(ignoreCancelled = true)
    public void onBucket(PlayerBucketFillEvent event) { if (refused(event.getPlayer(), event.getBlock())) event.setCancelled(true); }
    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (block == null) return;
        boolean trample = event.getAction() == org.bukkit.event.block.Action.PHYSICAL && block.getType() == Material.FARMLAND;
        boolean use = event.getAction() == org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK && (block.getType().isInteractable() || tool(event.getItem()));
        if ((trample || use) && refused(event.getPlayer(), block)) event.setCancelled(true);
    }
    private static boolean tool(ItemStack item) {
        if (item == null) return false;
        String name = item.getType().name();
        return name.endsWith("_HOE") || name.endsWith("_SHOVEL") || name.endsWith("_AXE");
    }
    @EventHandler(ignoreCancelled = true)
    public void onHanging(HangingBreakByEntityEvent event) {
        if (event.getRemover() instanceof Player player && refused(player, event.getEntity().getLocation().getBlock())) event.setCancelled(true);
    }
    @EventHandler(ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent event) { event.blockList().removeIf(block -> owner(block) != null); }
    @EventHandler(ignoreCancelled = true)
    public void onExplode(BlockExplodeEvent event) { event.blockList().removeIf(block -> owner(block) != null); }
    @EventHandler(ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) { if (owner(event.getBlock()) != null) event.setCancelled(true); }
    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location from = event.getFrom(), to = event.getTo();
        if (from.getBlockX() >> 4 == to.getBlockX() >> 4 && from.getBlockZ() >> 4 == to.getBlockZ() >> 4) return;
        UUID before = registry.owner(LandRegistry.key(from.getWorld().getName(), from.getBlockX() >> 4, from.getBlockZ() >> 4));
        UUID after = registry.owner(LandRegistry.key(to.getWorld().getName(), to.getBlockX() >> 4, to.getBlockZ() >> 4));
        if (Objects.equals(before, after)) return;
        event.getPlayer().sendActionBar(after == null ? Component.text("주인 없는 땅", NamedTextColor.GRAY) : Component.text(ownerName(after) + "의 땅", NamedTextColor.GOLD));
    }
}
