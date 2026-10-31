package kr.maitycoon;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;

/** The starter town: saved position, shop NPCs and block protection. */
final class Village implements Listener {
    record Zone(int x1, int z1, int x2, int z2) {
        boolean contains(int dx, int dz) { return dx >= x1 && dx <= x2 && dz >= z1 && dz <= z2; }
    }
    private record Npc(String id, int dx, int dz, float yaw, String name, Villager.Profession profession) {}
    private static final List<Npc> NPCS = List.of(
        new Npc("market", VillageBuilder.MARKET_STALL_X, VillageBuilder.NPC_Z, 0f, "작물 상인", Villager.Profession.FARMER),
        new Npc("seeds", VillageBuilder.SEED_STALL_X, VillageBuilder.NPC_Z, 0f, "씨앗 상인", Villager.Profession.FLETCHER),
        new Npc("dex", -VillageBuilder.PLAZA_NPC_X, 0, -90f, "도감 사서", Villager.Profession.LIBRARIAN),
        // Not a cartographer: saving a villager can generate its trades, and map trades search for
        // structures, which fails during chunk unload and loses the entity.
        new Npc("land", VillageBuilder.PLAZA_NPC_X, 0, 90f, "토지 관리인", Villager.Profession.MASON));
    static final Set<Material> FARMABLE = EnumSet.of(Material.WHEAT, Material.CARROTS, Material.POTATOES, Material.BEETROOTS,
        Material.MELON, Material.PUMPKIN, Material.MELON_STEM, Material.PUMPKIN_STEM, Material.ATTACHED_MELON_STEM, Material.ATTACHED_PUMPKIN_STEM,
        Material.SUGAR_CANE, Material.CACTUS, Material.BAMBOO, Material.BAMBOO_SAPLING, Material.COCOA, Material.SWEET_BERRY_BUSH,
        Material.CAVE_VINES, Material.CAVE_VINES_PLANT, Material.NETHER_WART, Material.KELP, Material.KELP_PLANT,
        Material.TORCHFLOWER_CROP, Material.TORCHFLOWER, Material.PITCHER_CROP, Material.PITCHER_PLANT,
        Material.BROWN_MUSHROOM, Material.RED_MUSHROOM, Material.SHORT_GRASS, Material.FARMLAND);
    private final MaiTycoonPlugin plugin;
    private final File file;
    private final NamespacedKey npcKey;
    private final Map<String, Consumer<Player>> npcActions;
    private String worldName;
    private int x, y, z;
    private boolean built, building;
    /** npcActions is keyed by NPC id and may be filled after construction. */
    Village(MaiTycoonPlugin plugin, Map<String, Consumer<Player>> npcActions) throws IOException {
        this.plugin = plugin;
        this.npcActions = npcActions;
        this.file = new File(plugin.getDataFolder(), "village.yml");
        this.npcKey = new NamespacedKey(plugin, "npc");
        if (!file.exists()) return;
        var yaml = new YamlConfiguration();
        try { yaml.load(file); }
        catch (org.bukkit.configuration.InvalidConfigurationException e) { throw new IOException("Invalid village.yml", e); }
        worldName = yaml.getString("world");
        x = yaml.getInt("x");
        y = yaml.getInt("y");
        z = yaml.getInt("z");
        built = yaml.getBoolean("built");
    }
    boolean built() { return built; }
    World world() { return worldName == null ? null : plugin.getServer().getWorld(worldName); }
    /** True when the chunk touches the town or its terrain slope. */
    boolean overlaps(World world, int chunkX, int chunkZ) {
        if (!built || !world.getName().equals(worldName)) return false;
        int reach = VillageBuilder.RADIUS + VillageBuilder.BLEND;
        return (chunkX << 4) + 15 >= x - reach && (chunkX << 4) <= x + reach && (chunkZ << 4) + 15 >= z - reach && (chunkZ << 4) <= z + reach;
    }
    Location spawn() {
        World world = world();
        return world == null ? null : new Location(world, x + 0.5, y + 1, z + VillageBuilder.SPAWN_DZ + 0.5, 180f, 0f);
    }
    /** Runs later over several ticks; `done` is called on the main thread when the town stands. */
    void build(Runnable done) {
        if (building) { done.run(); return; }
        building = true;
        World world = built && world() != null ? world() : plugin.getServer().getWorlds().get(0);
        Location center = built ? new Location(world, x, y, z) : world.getSpawnLocation();
        var builder = new VillageBuilder(world, center.getBlockX(), center.getBlockZ(), built ? y : VillageBuilder.surfaceY(world, center.getBlockX(), center.getBlockZ()));
        worldName = world.getName();
        x = center.getBlockX();
        z = center.getBlockZ();
        y = builder.y();
        Set<Long> chunks = new HashSet<>();
        int reach = VillageBuilder.RADIUS + VillageBuilder.BLEND;
        for (int cx = (x - reach) >> 4; cx <= (x + reach) >> 4; cx++) for (int cz = (z - reach) >> 4; cz <= (z + reach) >> 4; cz++) chunks.add(Chunk.getChunkKey(cx, cz));
        plugin.getLogger().info("Building village at " + x + "," + y + "," + z + " (" + chunks.size() + " chunks)");
        var loading = chunks.stream().map(key -> world.getChunkAtAsync((int) (long) key, (int) (key >> 32))).toArray(CompletableFuture[]::new);
        CompletableFuture.allOf(loading).whenComplete((ignored, error) -> plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (error != null) {
                building = false;
                plugin.getLogger().log(Level.SEVERE, "Cannot load village chunks", error);
                done.run();
                return;
            }
            Iterator<int[]> columns = builder.columns();
            plugin.getServer().getScheduler().runTaskTimer(plugin, task -> {
                for (int i = 0; i < 400 && columns.hasNext(); i++) builder.terrain(columns.next());
                if (columns.hasNext()) return;
                task.cancel();
                builder.structures();
                finish(world);
                done.run();
            }, 1L, 1L);
        }));
    }
    private void finish(World world) {
        Location spawn = spawn();
        world.setSpawnLocation(spawn);
        world.setGameRule(GameRule.SPAWN_RADIUS, 0);
        // Vanilla spawn protection would block non-ops from the shared farm; zones below replace it.
        plugin.getServer().setSpawnRadius(0);
        built = true;
        building = false;
        var yaml = new YamlConfiguration();
        yaml.set("world", worldName);
        yaml.set("x", x);
        yaml.set("y", y);
        yaml.set("z", z);
        yaml.set("built", true);
        try { yaml.save(file); }
        catch (IOException e) { plugin.getLogger().log(Level.SEVERE, "Cannot save village.yml", e); }
        ensureNpcs();
        for (Player player : world.getPlayers()) if (inside(player.getLocation())) player.teleport(spawn);
        plugin.getLogger().info("Village ready");
    }
    /** Replaces every NPC so exactly one of each exists, whatever an older version or a lost save left behind. */
    void ensureNpcs() {
        World world = world();
        if (world == null) return;
        for (Npc npc : NPCS) {
            Location at = new Location(world, x + npc.dx() + 0.5, y + 1, z + npc.dz() + 0.5, npc.yaw(), 0f);
            for (Entity entity : at.getChunk().getEntities()) {
                if (npc.id().equals(entity.getPersistentDataContainer().get(npcKey, PersistentDataType.STRING))) entity.remove();
            }
            world.spawn(at, Villager.class, villager -> {
                villager.setProfession(npc.profession());
                villager.setVillagerLevel(5);
                villager.customName(Component.text(npc.name(), NamedTextColor.GREEN));
                villager.setCustomNameVisible(true);
                villager.setAI(false);
                villager.setInvulnerable(true);
                villager.setSilent(true);
                villager.setCollidable(false);
                villager.setPersistent(true);
                villager.setRemoveWhenFarAway(false);
                villager.getPersistentDataContainer().set(npcKey, PersistentDataType.STRING, npc.id());
            });
        }
    }
    private boolean npc(Entity entity) { return entity.getPersistentDataContainer().has(npcKey, PersistentDataType.STRING); }
    private boolean inside(Location at) {
        return built && at.getWorld().getName().equals(worldName)
            && Math.abs(at.getBlockX() - x) <= VillageBuilder.RADIUS && Math.abs(at.getBlockZ() - z) <= VillageBuilder.RADIUS;
    }
    private boolean farm(Block block) {
        int dx = block.getX() - x, dz = block.getZ() - z;
        return VillageBuilder.FARMS.stream().anyMatch(zone -> zone.contains(dx, dz));
    }
    private boolean guarded(Player player, Block block) { return inside(block.getLocation()) && !player.hasPermission("maitycoon.admin"); }
    private void refuse(Player player) { player.sendActionBar(Component.text("마을은 보호 구역입니다. 농장에서만 작물을 심고 거둘 수 있습니다.", NamedTextColor.RED)); }

    @EventHandler
    public void onNpcClick(PlayerInteractEntityEvent event) {
        String id = event.getRightClicked().getPersistentDataContainer().get(npcKey, PersistentDataType.STRING);
        if (id == null) return;
        event.setCancelled(true);
        if (event.getHand() != EquipmentSlot.HAND || !event.getPlayer().hasPermission("maitycoon.play")) return;
        var action = npcActions.get(id);
        if (action != null) action.accept(event.getPlayer());
    }
    @EventHandler public void onNpcDamage(EntityDamageEvent event) { if (npc(event.getEntity())) event.setCancelled(true); }
    @EventHandler public void onNpcTransform(EntityTransformEvent event) { if (npc(event.getEntity())) event.setCancelled(true); }
    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (!guarded(event.getPlayer(), event.getBlock())) return;
        if (farm(event.getBlock()) && FARMABLE.contains(event.getBlock().getType()) && event.getBlock().getType() != Material.FARMLAND) return;
        event.setCancelled(true);
        refuse(event.getPlayer());
    }
    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (!guarded(event.getPlayer(), event.getBlock())) return;
        if (farm(event.getBlock()) && FARMABLE.contains(event.getBlock().getType())) return;
        event.setCancelled(true);
        refuse(event.getPlayer());
    }
    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (block == null || !inside(block.getLocation())) return;
        if (event.getAction() == org.bukkit.event.block.Action.PHYSICAL && block.getType() == Material.FARMLAND) { event.setCancelled(true); return; }
        if (event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK || event.getItem() == null || !guarded(event.getPlayer(), block)) return;
        String tool = event.getItem().getType().name();
        boolean hoe = tool.endsWith("_HOE");
        if ((hoe && !farm(block)) || tool.endsWith("_SHOVEL") || tool.endsWith("_AXE")) {
            event.setCancelled(true);
            refuse(event.getPlayer());
        }
    }
    @EventHandler(ignoreCancelled = true)
    public void onBucket(PlayerBucketEmptyEvent event) {
        if (!guarded(event.getPlayer(), event.getBlock())) return;
        event.setCancelled(true);
        refuse(event.getPlayer());
    }
    @EventHandler(ignoreCancelled = true)
    public void onBucket(PlayerBucketFillEvent event) {
        if (!guarded(event.getPlayer(), event.getBlock())) return;
        event.setCancelled(true);
        refuse(event.getPlayer());
    }
    @EventHandler(ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent event) { event.blockList().removeIf(block -> inside(block.getLocation())); }
    @EventHandler(ignoreCancelled = true)
    public void onExplode(BlockExplodeEvent event) { event.blockList().removeIf(block -> inside(block.getLocation())); }
    @EventHandler(ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) { if (inside(event.getBlock().getLocation())) event.setCancelled(true); }
    @EventHandler(ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent event) { if (inside(event.getBlock().getLocation())) event.setCancelled(true); }
    @EventHandler(ignoreCancelled = true)
    public void onMonster(CreatureSpawnEvent event) {
        if (event.getEntity() instanceof Monster && event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.NATURAL && inside(event.getLocation())) event.setCancelled(true);
    }
}
