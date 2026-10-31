package kr.maitycoon;

import java.io.IOException;
import java.util.*;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * Experimental area harvest, off by default. Each crop goes through a normal break event, so protection and
 * auto planting behave as usual. Work is spread over ticks and stops as soon as its conditions no longer hold.
 */
final class Tractors implements Listener {
    private static final class Job {
        private final Deque<Block> queue;
        private final Location origin;
        private final int slot;
        private final ItemStack tool;
        private int harvested;
        private Job(Deque<Block> queue, Location origin, int slot, ItemStack tool) {
            this.queue = queue;
            this.origin = origin;
            this.slot = slot;
            this.tool = tool;
        }
    }
    private static final Set<Material> CROPS = EnumSet.of(Material.WHEAT, Material.CARROTS, Material.POTATOES, Material.BEETROOTS);
    private final MaiTycoonPlugin plugin;
    private final ProfileStore store;
    private final Village village;
    private final Lands lands;
    private final int radius, perTick;
    private final Map<UUID, Job> jobs = new HashMap<>();
    private boolean harvesting;
    Tractors(MaiTycoonPlugin plugin, ProfileStore store, Village village, Lands lands, int radius, int perTick) {
        this.plugin = plugin;
        this.store = store;
        this.village = village;
        this.lands = lands;
        this.radius = radius;
        this.perTick = perTick;
    }
    /** True while a tractor break is being processed, so per-harvest bonuses can leave it out. */
    boolean harvesting() { return harvesting; }
    /** Nearest first, so stopping early leaves the far edge rather than holes. */
    static List<int[]> offsets(int radius) {
        List<int[]> all = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) all.add(new int[]{dx, dz});
        all.sort(Comparator.comparingInt(a -> a[0] * a[0] + a[1] * a[1]));
        return all;
    }
    private boolean usable(Player player, Block block) { return lands.mayTractor(player, block) || village.isFarm(block); }
    void start(Player player, Block center) {
        UUID id = player.getUniqueId();
        if (jobs.containsKey(id)) return;
        Deque<Block> queue = new ArrayDeque<>();
        for (int[] offset : offsets(radius)) {
            Block block = center.getRelative(offset[0], 0, offset[1]);
            if (block.getWorld().isChunkLoaded(block.getX() >> 4, block.getZ() >> 4)) queue.add(block);
        }
        ItemStack tool = player.getInventory().getItemInMainHand().clone();
        Job job = new Job(queue, player.getLocation(), player.getInventory().getHeldItemSlot(), tool);
        jobs.put(id, job);
        plugin.getServer().getScheduler().runTaskTimer(plugin, task -> {
            if (jobs.get(id) != job || !step(player, job)) {
                if (jobs.get(id) == job) jobs.remove(id);
                task.cancel();
                if (player.isOnline()) player.sendActionBar(Component.text("트랙터 수확 " + job.harvested + "칸", NamedTextColor.GREEN));
            }
        }, 1L, 1L);
    }
    /** One tick of work; false ends the job. Only blocks actually harvested count, so nothing is charged for the rest. */
    private boolean step(Player player, Job job) {
        if (!player.isOnline() || player.getGameMode() != GameMode.SURVIVAL) return false;
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (player.getInventory().getHeldItemSlot() != job.slot || !job.tool.isSimilar(hand)) return false;
        Location at = player.getLocation();
        if (!job.origin.getWorld().equals(at.getWorld()) || at.distanceSquared(job.origin) > (radius + 4) * (radius + 4)) return false;
        int done = 0;
        while (done < perTick && !job.queue.isEmpty()) {
            Block block = job.queue.poll();
            if (!block.getWorld().isChunkLoaded(block.getX() >> 4, block.getZ() >> 4)) return false;
            if (!CROPS.contains(block.getType()) || Lands.immature(block) || !usable(player, block)) continue;
            BlockBreakEvent event = new BlockBreakEvent(block, player);
            harvesting = true;
            try { plugin.getServer().getPluginManager().callEvent(event); }
            finally { harvesting = false; }
            if (event.isCancelled()) continue;
            block.breakNaturally(hand);
            job.harvested++;
            done++;
        }
        return !job.queue.isEmpty();
    }
    @EventHandler
    public void onUse(PlayerInteractEvent event) {
        Block clicked = event.getClickedBlock();
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND || clicked == null) return;
        if (!player.isSneaking() || item == null || !item.getType().name().endsWith("_HOE") || player.getGameMode() != GameMode.SURVIVAL) return;
        Block crop = clicked.getType() == Material.FARMLAND ? clicked.getRelative(BlockFace.UP) : clicked;
        if (!CROPS.contains(crop.getType())) return;
        try {
            if (!store.load(player.getUniqueId()).tractor()) return;
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Cannot read tractor state: " + player.getUniqueId(), e);
            return;
        }
        event.setCancelled(true);
        start(player, crop);
    }
    @EventHandler
    public void onQuit(PlayerQuitEvent event) { jobs.remove(event.getPlayer().getUniqueId()); }
}
