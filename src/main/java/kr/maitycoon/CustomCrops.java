package kr.maitycoon;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/**
 * Crops that do not exist in vanilla. A tripwire block marks the spot (no collision, breaks by hand) and an item display
 * shows the plant. Fields live in chunk data and are held in memory only while the chunk is loaded.
 */
final class CustomCrops implements Listener {
    private static final long CHECK_TICKS = 40L;
    private final MaiTycoonPlugin plugin;
    private final Catalog catalog;
    private final Village village;
    private final Lands lands;
    private final FarmIndex index;
    private final int chunkLimit;
    private final boolean boneMeal;
    private final NamespacedKey fieldKey, displayKey;
    private final Map<Chunk, CropField> fields = new HashMap<>();
    CustomCrops(MaiTycoonPlugin plugin, Catalog catalog, Village village, Lands lands, FarmIndex index, int chunkLimit, boolean boneMeal) {
        this.index = index;
        this.boneMeal = boneMeal;
        this.plugin = plugin;
        this.catalog = catalog;
        this.village = village;
        this.lands = lands;
        this.chunkLimit = chunkLimit;
        this.fieldKey = new NamespacedKey(plugin, "crops");
        this.displayKey = new NamespacedKey(plugin, "crop_display");
    }
    void start() {
        for (World world : plugin.getServer().getWorlds()) for (Chunk chunk : world.getLoadedChunks()) load(chunk);
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, CHECK_TICKS, CHECK_TICKS);
    }
    /** Fully grown crops on empty soil, cycling through every kind, so the shared farm shows what can be grown. */
    void plantShowcase(List<Block> soil) {
        List<Catalog.Crop> kinds = List.copyOf(catalog.crops());
        if (kinds.isEmpty()) return;
        long now = System.currentTimeMillis();
        int next = 0;
        for (Block ground : soil) {
            Block spot = ground.getRelative(BlockFace.UP);
            Catalog.Crop crop = kinds.get(next % kinds.size());
            CropField field = fields.computeIfAbsent(spot.getChunk(), ignored -> new CropField());
            if (ground.getType() != Material.FARMLAND || !spot.getType().isAir() || field.size() >= chunkLimit) continue;
            spot.setType(Material.TRIPWIRE, false);
            field.put(new CropField.Plot(spot.getX(), spot.getY(), spot.getZ(), crop.id(), now - crop.growMillis()));
            save(spot.getChunk(), field);
            show(spot, crop.id(), CropField.STAGES - 1);
            next++;
        }
    }
    private void load(Chunk chunk) {
        String stored = chunk.getPersistentDataContainer().get(fieldKey, PersistentDataType.STRING);
        if (stored == null) {
            if (lands.owned(chunk)) index.update(key(chunk), List.of(), System.currentTimeMillis());
            else index.forget(key(chunk));
            return;
        }
        try {
            CropField field = CropField.decode(stored);
            fields.put(chunk, field);
            index.update(key(chunk), field.plots(), System.currentTimeMillis());
        }
        catch (IllegalArgumentException e) { plugin.getLogger().warning("Ignoring damaged crop data in chunk " + chunk.getX() + "," + chunk.getZ()); }
    }
    @EventHandler public void onChunkLoad(ChunkLoadEvent event) { load(event.getChunk()); }
    /** Nothing can change while unloaded, so the moment of unloading is what the record is good for. */
    @EventHandler public void onChunkUnload(ChunkUnloadEvent event) {
        Chunk chunk = event.getChunk();
        CropField field = fields.remove(chunk);
        if (index.entry(key(chunk)) != null) index.update(key(chunk), field == null ? List.of() : field.plots(), System.currentTimeMillis());
    }
    private static String key(Chunk chunk) { return LandRegistry.key(chunk.getWorld().getUID().toString(), chunk.getX(), chunk.getZ()); }
    private void save(Chunk chunk, CropField field) {
        if (field.size() == 0) { chunk.getPersistentDataContainer().remove(fieldKey); fields.remove(chunk); }
        else chunk.getPersistentDataContainer().set(fieldKey, PersistentDataType.STRING, field.encode());
        index.update(key(chunk), field.plots(), System.currentTimeMillis());
    }
    private CropField field(Block block) { return fields.get(block.getChunk()); }
    private CropField.Plot plot(Block block) {
        CropField field = field(block);
        return field == null ? null : field.get(block.getX(), block.getY(), block.getZ());
    }
    boolean isCrop(Block block) { return block.getType() == Material.TRIPWIRE && plot(block) != null; }
    private ItemStack stageItem(String id, int stage) {
        ItemStack item = new ItemStack(Material.SHORT_GRASS);
        var meta = item.getItemMeta();
        var model = meta.getCustomModelDataComponent();
        model.setStrings(List.of(id.toLowerCase(Locale.ROOT) + "_" + stage));
        meta.setCustomModelDataComponent(model);
        item.setItemMeta(meta);
        return item;
    }
    private Optional<ItemDisplay> display(Block block) {
        return block.getWorld().getNearbyEntities(block.getLocation().add(0.5, 0.5, 0.5), 0.3, 0.3, 0.3, this::tagged).stream().map(ItemDisplay.class::cast).findFirst();
    }
    private boolean tagged(Entity entity) { return entity instanceof ItemDisplay && entity.getPersistentDataContainer().has(displayKey, PersistentDataType.BYTE); }
    private void show(Block block, String id, int stage) {
        ItemStack item = stageItem(id, stage);
        var shown = display(block);
        if (shown.isPresent()) { shown.get().setItemStack(item); return; }
        block.getWorld().spawn(block.getLocation().add(0.5, 0.5, 0.5), ItemDisplay.class, entity -> {
            entity.setItemStack(item);
            entity.setPersistent(true);
            entity.getPersistentDataContainer().set(displayKey, PersistentDataType.BYTE, (byte) 1);
        });
    }
    private void tick() {
        long now = System.currentTimeMillis();
        for (var entry : List.copyOf(fields.entrySet())) {
            Chunk chunk = entry.getKey();
            CropField field = entry.getValue();
            boolean changed = false;
            for (var plot : field.plots()) {
                Block block = chunk.getWorld().getBlockAt(plot.x(), plot.y(), plot.z());
                Catalog.Crop crop = catalog.crop(plot.id());
                if (crop == null) continue;
                if (block.getType() != Material.TRIPWIRE || block.getRelative(BlockFace.DOWN).getType() != Material.FARMLAND) {
                    // Water, a piston or lost farmland removed the plant, so give the seed back.
                    field.remove(plot);
                    changed = true;
                    display(block).ifPresent(Entity::remove);
                    if (block.getType() == Material.TRIPWIRE) block.setType(Material.AIR);
                    block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5), catalog.item(crop.seedId(), 1));
                    continue;
                }
                show(block, plot.id(), plot.stage(now, crop.growMillis()));
            }
            if (changed) save(chunk, field);
        }
    }
    private void plant(PlayerInteractEvent event, Catalog.Crop crop) {
        Player player = event.getPlayer();
        Block soil = Objects.requireNonNull(event.getClickedBlock());
        event.setCancelled(true);
        Block spot = soil.getRelative(BlockFace.UP);
        if (soil.getType() != Material.FARMLAND || event.getBlockFace() != BlockFace.UP || !spot.getType().isAir()) return;
        if (!village.allowsPlanting(player, spot) || !lands.allowsPlanting(player, spot)) return;
        Chunk chunk = spot.getChunk();
        CropField field = fields.computeIfAbsent(chunk, ignored -> new CropField());
        if (field.size() >= chunkLimit) {
            plugin.tell(player, "한 청크에는 특산 작물을 " + chunkLimit + "개까지 심을 수 있습니다.");
            return;
        }
        spot.setType(Material.TRIPWIRE, false);
        field.put(new CropField.Plot(spot.getX(), spot.getY(), spot.getZ(), crop.id(), System.currentTimeMillis()));
        save(chunk, field);
        ItemStack hand = Objects.requireNonNull(event.getItem());
        hand.setAmount(hand.getAmount() - 1);
        show(spot, crop.id(), 0);
    }
    private void boneMeal(PlayerInteractEvent event, CropField.Plot plot) {
        Block block = Objects.requireNonNull(event.getClickedBlock());
        Catalog.Crop crop = catalog.crop(plot.id());
        event.setCancelled(true);
        if (crop == null || plot.ready(System.currentTimeMillis(), crop.growMillis())) return;
        CropField field = field(block);
        CropField.Plot grown = plot.advanced(crop.growMillis());
        field.put(grown);
        save(block.getChunk(), field);
        ItemStack hand = Objects.requireNonNull(event.getItem());
        hand.setAmount(hand.getAmount() - 1);
        block.getWorld().playEffect(block.getLocation(), Effect.BONE_MEAL_USE, 8);
        show(block, plot.id(), grown.stage(System.currentTimeMillis(), crop.growMillis()));
    }
    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND || event.getClickedBlock() == null) return;
        ItemStack item = event.getItem();
        String id = catalog.idOf(item);
        Catalog.Crop crop = id == null ? null : catalog.bySeed(id);
        if (crop != null) { plant(event, crop); return; }
        CropField.Plot plot = plot(event.getClickedBlock());
        if (plot == null || item == null || item.getType() != Material.BONE_MEAL) return;
        if (boneMeal) boneMeal(event, plot);
        else {
            event.setCancelled(true);
            event.getPlayer().sendActionBar(Component.text("이 작물에는 뼛가루를 쓸 수 없습니다.", NamedTextColor.GRAY));
        }
    }
    /** Protection has already decided by MONITOR; a cancelled break keeps the plant. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        CropField.Plot plot = plot(block);
        if (plot == null || block.getType() != Material.TRIPWIRE) return;
        CropField field = field(block);
        field.remove(plot);
        save(block.getChunk(), field);
        display(block).ifPresent(Entity::remove);
        event.setDropItems(false);
        Catalog.Crop crop = catalog.crop(plot.id());
        if (crop == null || event.getPlayer().getGameMode() == GameMode.CREATIVE) return;
        Location at = block.getLocation().add(0.5, 0.5, 0.5);
        if (plot.ready(System.currentTimeMillis(), crop.growMillis())) {
            int yield = ThreadLocalRandom.current().nextInt(crop.minYield(), crop.maxYield() + 1);
            block.getWorld().dropItemNaturally(at, catalog.item(crop.id(), yield));
            block.getWorld().dropItemNaturally(at, catalog.item(crop.seedId(), ThreadLocalRandom.current().nextInt(1, 3)));
        } else {
            block.getWorld().dropItemNaturally(at, catalog.item(crop.seedId(), 1));
            event.getPlayer().sendActionBar(Component.text("덜 자라서 씨앗만 돌려받았습니다.", NamedTextColor.GRAY));
        }
    }
}
